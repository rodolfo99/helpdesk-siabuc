"""Deterministic worker tests. pg_dump/psql boundaries are simulated, not a Docker restore test."""
from __future__ import annotations
import datetime as dt
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location('backup_worker_under_test',ROOT/'backup/worker.py')
worker=importlib.util.module_from_spec(SPEC);sys.modules[SPEC.name]=worker;SPEC.loader.exec_module(worker)
NOW=dt.datetime(2026,10,13,12,tzinfo=dt.timezone.utc)
SETTINGS={'enabled':False,'destination_confirmed':False,'daily_time':'03:00:00','time_zone':'America/Mexico_City',
          'retention_days':30,'keep_last':7,'updated_at':'2026-10-01T00:00:00+00:00'}
PAYLOAD=b'PGDMP binary fixture, no real database\x00\xff'

class FakeDatabase:
    def __init__(self):
        self.config=dict(SETTINGS);self.records=[];self.scheduled=[];self.owner=None;self.lock=threading.Lock();self.failures=[];self.successes=[];self.purged_ids=[];self.heartbeats=[]
    def settings(self):return self.config
    def heartbeat(self,label,writable,error=None,owner=None):self.heartbeats.append((label,writable,error,owner))
    def enqueue(self,day,version):
        if day not in self.scheduled:self.scheduled.append(day)
    def claim(self,owner):
        with self.lock:
            if self.owner or not self.config['destination_confirmed']:return None
            for record in self.records:
                if record['status']=='QUEUED' and (record['kind']=='MANUAL' or self.config['enabled']):
                    self.owner=owner;record.update(status='RUNNING',owner=owner,attempts=record.get('attempts',0)+1);return record
    def release(self,owner):
        with self.lock:
            if self.owner==owner:self.owner=None
    def success(self,record,filename,digest,size):
        record.update(status='SUCCEEDED',filename=filename,sha256=digest,size_bytes=size,finished_at=NOW.isoformat());self.successes.append(dict(record))
    def failure(self,record,message):record.update(status=worker.retry_state(record['attempts'])[0]);self.failures.append(message)
    def successful(self):return [r for r in self.records if r['status']=='SUCCEEDED']
    def purged(self,run_id):self.purged_ids.append(run_id)
    def manual(self):self.records.append({'id':len(self.records)+1,'kind':'MANUAL','status':'QUEUED','attempts':0})

class AutomaticBackupTests(unittest.TestCase):
    def setUp(self):self.temp=tempfile.TemporaryDirectory();self.directory=Path(self.temp.name);self.db=FakeDatabase();self.engine=worker.Worker(self.db,self.directory)
    def tearDown(self):self.temp.cleanup()
    def dump(self,path):path.write_bytes(PAYLOAD)
    def test_default_disabled_creates_no_copy_and_does_not_run_retention(self):
        manual=self.directory/'respaldo-manual.dump';manual.write_bytes(b'preservar')
        with mock.patch.object(self.engine,'dump') as dump,mock.patch.object(worker,'retain') as retain:self.engine.tick(NOW)
        dump.assert_not_called();retain.assert_not_called();self.assertEqual(self.db.scheduled,[]);self.assertEqual(manual.read_bytes(),b'preservar')
    def test_calendar_uses_local_zone_and_does_not_catch_up_missed_slots_after_resume(self):
        settings={**SETTINGS,'enabled':True,'destination_confirmed':True}
        self.assertIsNone(worker.due_date(settings,dt.datetime(2026,10,13,8,59,tzinfo=worker.UTC)))
        self.assertEqual(worker.due_date(settings,dt.datetime(2026,10,13,9,0,tzinfo=worker.UTC)),'2026-10-13')
        resumed={**settings,'updated_at':'2026-10-13T10:00:00+00:00'}
        self.assertIsNone(worker.due_date(resumed,NOW))
        self.assertEqual(worker.due_date(resumed,NOW+dt.timedelta(days=1)),'2026-10-14')
        self.assertIsNone(worker.due_date({**resumed,'enabled':False},NOW+dt.timedelta(days=1)))
    def test_dst_hour_is_evaluated_once_per_local_date_and_skipped_hour_moves_forward(self):
        settings={**SETTINGS,'enabled':True,'destination_confirmed':True,'time_zone':'America/New_York','daily_time':'02:30:00','updated_at':'2026-01-01T00:00:00+00:00'}
        self.assertIsNone(worker.due_date(settings,dt.datetime(2026,3,8,7,0,tzinfo=worker.UTC)))
        self.assertEqual(worker.due_date(settings,dt.datetime(2026,3,8,7,30,tzinfo=worker.UTC)),'2026-03-08')
        settings['daily_time']='01:30:00'
        self.assertEqual(worker.due_date(settings,dt.datetime(2026,11,1,5,30,tzinfo=worker.UTC)),worker.due_date(settings,dt.datetime(2026,11,1,6,30,tzinfo=worker.UTC)))
    def test_manual_runs_while_schedule_off_and_publishes_complete_hash_pair(self):
        self.db.config['destination_confirmed']=True;self.db.manual()
        original=self.db.success
        def complete(record,name,digest,size):
            self.assertEqual((self.directory/name).read_bytes(),PAYLOAD)
            self.assertEqual((self.directory/(name+'.sha256')).read_text(),f'{digest}  {name}\n')
            original(record,name,digest,size)
        with mock.patch.object(self.engine,'dump',side_effect=self.dump),mock.patch.object(self.db,'success',side_effect=complete),mock.patch.object(worker,'retain') as retain:self.engine.tick(NOW)
        retain.assert_not_called()
        self.assertEqual(len(self.db.successes),1);self.assertIsNone(self.db.owner);self.assertEqual(self.db.scheduled,[])
        self.assertEqual(list(self.directory.glob('.incomplete-*')),[])
        for path in self.directory.iterdir():self.assertEqual(path.stat().st_mode&0o777,0o600)
    def test_failed_dump_removes_temporary_file_keeps_manual_files_and_never_applies_retention(self):
        self.db.config['destination_confirmed']=True;self.db.manual();existing=self.directory/'manual.dump';existing.write_bytes(b'original')
        def failed(path):path.write_bytes(b'partial');raise worker.BackupError('Fallo seguro de prueba')
        with mock.patch.object(self.engine,'dump',side_effect=failed),mock.patch.object(worker,'retain') as retain:self.engine.tick(NOW)
        retain.assert_not_called();self.assertEqual(self.db.failures,['Fallo seguro de prueba']);self.assertEqual(self.db.records[0]['status'],'RETRYING')
        self.assertEqual(list(self.directory.iterdir()),[existing]);self.assertIsNone(self.db.owner)
    def test_two_worker_instances_cannot_dump_the_same_run_in_parallel(self):
        self.db.config['destination_confirmed']=True;self.db.manual();second=worker.Worker(self.db,self.directory)
        started=threading.Event();resume=threading.Event()
        def blocked(path):started.set();self.assertTrue(resume.wait(5));path.write_bytes(PAYLOAD)
        with mock.patch.object(self.engine,'dump',side_effect=blocked),mock.patch.object(second,'dump') as duplicate:
            first=threading.Thread(target=self.engine.tick,args=(NOW,));first.start();self.assertTrue(started.wait(5));second.tick(NOW);duplicate.assert_not_called();resume.set();first.join(5)
        self.assertFalse(first.is_alive());self.assertEqual(len(self.db.successes),1)
    def test_sql_lease_rejects_a_second_owner_without_claiming_or_dumping(self):
        database=worker.Database({'DB_PASSWORD':'unsecreto'});database.sql=mock.Mock(return_value='')
        self.assertIsNone(database.claim('00000000-0000-0000-0000-000000000001'))
        self.assertEqual(database.sql.call_count,1)
    def test_retries_are_delayed_and_bounded_to_three_attempts(self):
        self.assertEqual(worker.retry_state(1),('RETRYING',300));self.assertEqual(worker.retry_state(2),('RETRYING',300));self.assertEqual(worker.retry_state(3),('FAILED',None))
    def fixture(self,run_id,age,kind='SCHEDULED',status='SUCCEEDED',valid=True):
        name=f'siabuc-auto-{run_id}-'+str(run_id).zfill(32)+'.dump';path=self.directory/name;path.write_bytes(PAYLOAD)
        digest=hashlib.sha256(PAYLOAD).hexdigest();(self.directory/(name+'.sha256')).write_text(f'{digest}  {name}\n' if valid else 'alterado')
        return dict(id=run_id,kind=kind,status=status,filename=name,sha256=digest,finished_at=(NOW-dt.timedelta(days=age)).isoformat(),purged_at=None)
    def test_retention_keeps_minimum_and_never_touches_manual_failed_unregistered_or_modified_files(self):
        recent=self.fixture(1,1);protected=self.fixture(2,40);expired=self.fixture(3,60);manual=self.fixture(4,70,'MANUAL');failed=self.fixture(5,80,status='FAILED');modified=self.fixture(6,90,valid=False)
        unknown=self.directory/'desconocido.dump';unknown.write_bytes(b'original')
        worker.retain(self.directory,[recent,protected,expired,manual,failed,modified],30,2,NOW,self.db.purged)
        self.assertEqual(self.db.purged_ids,[3]);self.assertFalse((self.directory/expired['filename']).exists())
        for record in [recent,protected,manual,failed,modified]:self.assertTrue((self.directory/record['filename']).exists())
        self.assertEqual(unknown.read_bytes(),b'original')
    def test_retention_refuses_symlinks_and_path_traversal_records(self):
        target=self.directory/'private.dump';target.write_bytes(b'keep')
        old=self.fixture(1,100);(self.directory/old['filename']).unlink();(self.directory/old['filename']).symlink_to(target)
        traversal={**old,'id':2,'filename':'../outside.dump'}
        worker.retain(self.directory,[self.fixture(3,1),old,traversal],30,1,NOW,self.db.purged)
        self.assertEqual(target.read_bytes(),b'keep');self.assertEqual(self.db.purged_ids,[])
    def test_retention_floor_counts_only_recoverable_files_not_missing_or_corrupt_recent_rows(self):
        missing=self.fixture(1,1);(self.directory/missing['filename']).unlink()
        corrupt=self.fixture(2,2,valid=False);old_valid=self.fixture(3,90);oldest_valid=self.fixture(4,100)
        worker.retain(self.directory,[missing,corrupt,old_valid,oldest_valid],30,2,NOW,self.db.purged)
        self.assertEqual(self.db.purged_ids,[])
        self.assertTrue((self.directory/old_valid['filename']).exists());self.assertTrue((self.directory/oldest_valid['filename']).exists())
    def test_disabling_calendar_during_scheduled_dump_preserves_all_previous_files(self):
        self.db.config.update(enabled=True,destination_confirmed=True)
        self.db.records.append({'id':1,'kind':'SCHEDULED','status':'QUEUED','attempts':0})
        def disable_while_dumping(path):self.db.config['enabled']=False;path.write_bytes(PAYLOAD)
        with mock.patch.object(self.engine,'dump',side_effect=disable_while_dumping),mock.patch.object(worker,'retain') as retain:self.engine.tick(NOW)
        self.assertEqual(len(self.db.successes),1);retain.assert_not_called()
    def test_process_errors_never_expose_password_or_raw_stderr(self):
        database=worker.Database({'DB_PASSWORD':'ClaveMuyPrivada123'})
        with mock.patch.object(worker.subprocess,'run',side_effect=subprocess.CalledProcessError(1,['psql'],stderr='password=ClaveMuyPrivada123')):
            with self.assertRaises(worker.BackupError) as caught:database.sql('SELECT 1')
        self.assertNotIn('ClaveMuyPrivada123',str(caught.exception));self.assertNotIn('password=',str(caught.exception))
    def test_failed_destination_is_reported_and_does_not_claim_a_run(self):
        with mock.patch.object(worker,'safe_destination',side_effect=PermissionError('secret host path')),mock.patch.object(self.db,'claim') as claim:self.engine.tick(NOW)
        claim.assert_not_called();self.assertFalse(self.db.heartbeats[0][1]);self.assertNotIn('secret host path',self.db.heartbeats[0][2])

if __name__=='__main__':unittest.main()
