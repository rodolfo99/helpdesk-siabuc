"""Execute production worker SQL against isolated PGlite. No pg_dump, Docker, user database or restoration.
Requires Node and SIABUC_PGLITE_MODULE; see docs/RESPALDOS-AUTOMATICOS.md.
"""
import sys,importlib.util,json,subprocess,uuid,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('backupworker',root/'backup/worker.py');w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
engine=subprocess.Popen(['node',str(root/'tests/migrations/backup-worker-engine.mjs')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
assert engine.stdout.readline().strip()=='READY'
temporary=tempfile.TemporaryDirectory();w.HEALTH_FILE=Path(temporary.name)/'heartbeat'
class SQL(w.Database):
 def sql(self,sql):
  engine.stdin.write(json.dumps(sql)+'\n');engine.stdin.flush();result=json.loads(engine.stdout.readline())
  assert 'error' not in result,result
  return result['out']
db=SQL({});owner=str(uuid.uuid4());other=str(uuid.uuid4());checks=0
def check(value):
 global checks
 assert value;checks+=1
try:
 check(db.settings()['enabled']==False)
 db.sql("UPDATE backup_settings SET destination_confirmed=TRUE WHERE id=1")
 db.sql("INSERT INTO backup_runs(kind) VALUES ('MANUAL')")
 record=db.claim(owner);check(record['kind']=='MANUAL' and record['attempts']==1)
 check(db.claim(other) is None)
 db.heartbeat('Prueba aislada',True,owner=owner)
 db.failure(record,'Fallo de prueba');db.release(owner)
 check(db.claim(owner) is None)
 db.sql("UPDATE backup_runs SET retry_at=NOW()-INTERVAL '1 second'")
 record=db.claim(owner);check(record['attempts']==2)
 db.success(record,'siabuc-auto-1-'+'a'*32+'.dump','a'*64,100);db.release(owner)
 check(db.rows('SELECT status FROM backup_runs WHERE id=1')[0]['status']=='SUCCEEDED')
 # Crash after disabling an already-running scheduled job must cancel it, preserve manual queue.
 db.sql("UPDATE backup_settings SET enabled=TRUE,updated_at=NOW()-INTERVAL '1 day' WHERE id=1")
 db.sql("INSERT INTO backup_runs(kind,scheduled_key) SELECT 'SCHEDULED',(NOW() AT TIME ZONE time_zone)::date FROM backup_settings")
 record=db.claim(owner);check(record['kind']=='SCHEDULED')
 db.sql("UPDATE backup_settings SET enabled=FALSE,updated_at=NOW() WHERE id=1; UPDATE backup_runtime SET lock_until=NOW()-INTERVAL '1 second' WHERE id=1; INSERT INTO backup_runs(kind) VALUES ('MANUAL')")
 manual=db.claim(other);check(manual['kind']=='MANUAL')
 check(db.rows("SELECT status FROM backup_runs WHERE kind='SCHEDULED'")[0]['status']=='CANCELLED')
 check(db.rows("SELECT status FROM backup_attempts WHERE run_id=2")[0]['status']=='FAILED')
 db.success(manual,'siabuc-auto-3-'+'b'*32+'.dump','b'*64,100);db.release(other)
 db.sql("UPDATE backup_settings SET enabled=TRUE,updated_at=NOW() WHERE id=1")
 check(db.claim(owner) is None)
 # Exercise scheduled enqueue via a new date and schedule revision check.
 cfg=db.settings();db.enqueue('2099-01-01',cfg['updated_at']);db.enqueue('2099-01-01',cfg['updated_at'])
 check(len(db.rows("SELECT id FROM backup_runs WHERE scheduled_key='2099-01-01'"))==1)
 check(db.claim(owner) is None) # future/missed queued slot never recovered on resume.
 db.heartbeat('Nuevo destino aislado',True)
 check(db.settings()['enabled']==False)
 check(db.settings()['destination_confirmed']==False)
 print(f'{checks} PostgreSQL worker SQL checks passed (PGlite, all migrations). No actual pg_dump/restore.')
finally:
 engine.stdin.close();engine.wait(timeout=20);temporary.cleanup()
