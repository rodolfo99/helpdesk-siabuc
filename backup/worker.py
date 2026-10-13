#!/usr/bin/env python3
"""One daily PostgreSQL custom dump. No Docker API, shell expansion or external Python packages."""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid
from zoneinfo import ZoneInfo

UTC=dt.timezone.utc
OWNED_NAME=re.compile(r"siabuc-auto-[1-9][0-9]*-[0-9a-f]{32}\.dump\Z")
MAX_ATTEMPTS=3
RETRY_SECONDS=300
POLL_SECONDS=20
HEALTH_FILE=Path('/tmp/siabuc-backup-heartbeat')

class BackupError(RuntimeError):
    """Only a safe, actionable message; never embed process stderr or credentials."""

def quote(value):
    if value is None:return 'NULL'
    if isinstance(value,bool):return 'TRUE' if value else 'FALSE'
    if isinstance(value,int):return str(value)
    return "'"+str(value).replace("'","''")+"'"

def utcnow():return dt.datetime.now(UTC)

def due_date(settings,now):
    """At most one slot for today's local date; no historical catch-up storm."""
    if not settings['enabled'] or not settings['destination_confirmed']:return None
    zone=ZoneInfo(settings['time_zone'])
    local=now.astimezone(zone)
    scheduled=dt.datetime.combine(local.date(),dt.time.fromisoformat(settings['daily_time']),zone)
    if settings.get('updated_at'):
        activated=dt.datetime.fromisoformat(settings['updated_at'].replace('Z','+00:00'))
        if activated>scheduled.astimezone(UTC):return None
    # Comparing UTC handles an ambiguous hour once (fold=0), and normalizes a skipped hour forward.
    return local.date().isoformat() if now.astimezone(UTC)>=scheduled.astimezone(UTC) else None

def retry_state(attempt):
    return ('RETRYING',RETRY_SECONDS) if attempt<MAX_ATTEMPTS else ('FAILED',None)

def digest_file(path):
    digest=hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda:handle.read(1024*1024),b''):digest.update(block)
    return digest.hexdigest()

def safe_destination(directory):
    directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    if not directory.is_dir():raise BackupError('El destino de respaldo no es un directorio.')
    descriptor,name=tempfile.mkstemp(prefix='.write-check-',dir=directory)
    os.close(descriptor);Path(name).unlink()

def retain(directory,records,retention_days,keep_last,now,mark_purged):
    """Only DB-owned scheduled successes, expired AND outside the minimum number of copies."""
    cutoff=now-dt.timedelta(days=retention_days)
    successes=sorted((r for r in records if r['status']=='SUCCEEDED' and r['kind']=='SCHEDULED' and not r.get('purged_at')),
                     key=lambda r:r['finished_at'],reverse=True)
    verified=[]
    for record in successes:
        name=record.get('filename','')
        if not OWNED_NAME.fullmatch(name):continue
        target=directory/name;checksum=directory/(name+'.sha256')
        # Refuse links and anything not exactly represented by the successful DB record + matching checksum.
        if target.is_symlink() or checksum.is_symlink() or not target.is_file() or not checksum.is_file():continue
        expected=record.get('sha256','')
        if not re.fullmatch(r'[0-9a-f]{64}',expected):continue
        if checksum.read_text(encoding='utf-8').strip()!=f'{expected}  {name}' or digest_file(target)!=expected:continue
        verified.append((record,target,checksum))
    # The floor counts intact files present here, not historical rows whose files disappeared.
    for record,target,checksum in verified[keep_last:]:
        finished=dt.datetime.fromisoformat(record['finished_at'].replace('Z','+00:00'))
        if finished>=cutoff:continue
        target.unlink();checksum.unlink();mark_purged(record['id'])

class Database:
    def __init__(self,environment=None):
        self.env=dict(os.environ if environment is None else environment)
        self.env.update(PGHOST=self.env.get('DB_HOST','db'),PGPORT=self.env.get('DB_PORT','5432'),
            PGDATABASE=self.env.get('DB_NAME','siabuc_helpdesk'),PGUSER=self.env.get('DB_USER','siabuc'),
            PGPASSWORD=self.env.get('DB_PASSWORD',''),PGCONNECT_TIMEOUT='10',PGAPPNAME='siabuc-backup-worker')
    def sql(self,sql):
        try:
            result=subprocess.run(['psql','-X','-A','-t','-q','-v','ON_ERROR_STOP=1','-c',sql],env=self.env,
                capture_output=True,text=True,timeout=30,check=True)
            return result.stdout.strip()
        except (subprocess.SubprocessError,OSError) as error:
            raise BackupError('No se pudo consultar PostgreSQL; revise conexión y credenciales del servicio de respaldos.') from error
    def rows(self,sql):return json.loads(self.sql("SELECT COALESCE(json_agg(q),'[]'::json) FROM ("+sql+") q") or '[]')
    def settings(self):return self.rows('SELECT * FROM backup_settings WHERE id=1')[0]
    def heartbeat(self,label,writable,error=None,owner=None):
        label=label[:1000]
        changed=f"destination_label<>'' AND destination_label<>{quote(label)}"
        detail=quote(error) if error is not None else 'last_error'
        self.sql(f"""BEGIN; SELECT id FROM backup_settings WHERE id=1 FOR UPDATE;
          UPDATE backup_settings SET enabled=FALSE,destination_confirmed=FALSE,updated_at=NOW()
          WHERE id=1 AND EXISTS(SELECT 1 FROM backup_runtime WHERE id=1 AND {changed});
          UPDATE backup_runs SET status='CANCELLED',finished_at=NOW(),retry_at=NULL,last_error='Se cambió el destino; confirme la nueva carpeta.'
          WHERE kind='SCHEDULED' AND status IN ('QUEUED','RETRYING') AND EXISTS(SELECT 1 FROM backup_runtime WHERE id=1 AND {changed});
          UPDATE backup_runtime SET last_seen_at=NOW(),writable={quote(writable)},
            last_error=CASE WHEN {changed} THEN 'El destino cambió. Confirme la nueva carpeta y active el calendario si corresponde.' ELSE {detail} END,
            destination_label={quote(label)} WHERE id=1; COMMIT;""")
        if owner:
            held=self.sql(f"UPDATE backup_runtime SET lock_until=NOW()+INTERVAL '5 minutes' WHERE id=1 AND lock_owner={quote(owner)}::uuid RETURNING id")
            if held!='1':raise BackupError('El servicio perdió la exclusividad del respaldo; no se publicó una copia de esa ejecución.')
        HEALTH_FILE.write_text(str(time.time()),encoding='ascii')
    def enqueue(self,day,version):
        # Serialize with ADMIN schedule changes, so disabling the calendar cannot race a new scheduled insert.
        self.sql(f"""BEGIN; SELECT id FROM backup_settings WHERE id=1 FOR UPDATE;
          INSERT INTO backup_runs(kind,scheduled_key) SELECT 'SCHEDULED',{quote(day)}::date FROM backup_settings
          WHERE id=1 AND enabled AND destination_confirmed AND updated_at={quote(version)}::timestamptz ON CONFLICT(scheduled_key) DO NOTHING; COMMIT;""")
    def claim(self,owner):
        owner_id=owner;owner=quote(owner)+'::uuid'
        # A single UPDATE owns the cross-container lease. A second worker gets no row and cannot claim a run.
        locked=self.sql(f"UPDATE backup_runtime SET lock_owner={owner},lock_until=NOW()+INTERVAL '5 minutes' WHERE id=1 AND (lock_until IS NULL OR lock_until<NOW()) RETURNING id")
        if locked!='1':return None
        self.sql("""UPDATE backup_attempts SET status='FAILED',finished_at=NOW(),error='El proceso anterior se interrumpió; se revisará el respaldo.'
          WHERE status='RUNNING' AND run_id IN (SELECT id FROM backup_runs WHERE status='RUNNING');
          UPDATE backup_runs r SET status='CANCELLED',finished_at=NOW(),retry_at=NULL,
            last_error='Horario cancelado o perdido; no se recupera al reactivar.' FROM backup_settings s
          WHERE s.id=1 AND r.kind='SCHEDULED' AND r.status IN ('QUEUED','RETRYING','RUNNING')
            AND (NOT s.enabled OR NOT s.destination_confirmed OR r.created_at<s.updated_at OR r.scheduled_key<>(NOW() AT TIME ZONE s.time_zone)::date);
          UPDATE backup_runs SET status=CASE WHEN attempts<3 THEN 'RETRYING' ELSE 'FAILED' END,
            retry_at=CASE WHEN attempts<3 THEN NOW() ELSE NULL END,finished_at=NOW(),
            last_error='El proceso anterior se interrumpió; el archivo incompleto no se considera un respaldo.' WHERE status='RUNNING';""")
        rows=json.loads(self.sql(f"""WITH cfg AS (SELECT * FROM backup_settings WHERE id=1 FOR UPDATE), candidate AS (
          SELECT r.id FROM backup_runs r CROSS JOIN cfg s
          WHERE s.id=1 AND s.destination_confirmed AND (r.kind='MANUAL' OR s.enabled)
            AND (r.status='QUEUED' OR (r.status='RETRYING' AND r.retry_at<=NOW())) AND r.attempts<3
          ORDER BY r.created_at,r.id FOR UPDATE OF r SKIP LOCKED LIMIT 1
        ), claimed AS (UPDATE backup_runs SET status='RUNNING',started_at=NOW(),finished_at=NULL,retry_at=NULL,
          attempts=attempts+1,owner={owner} WHERE id IN (SELECT id FROM candidate) RETURNING *) SELECT COALESCE(json_agg(claimed),'[]'::json) FROM claimed""") or '[]')
        if not rows:self.release(owner_id);return None
        record=rows[0]
        self.sql(f"INSERT INTO backup_attempts(run_id,attempt,status) VALUES ({record['id']},{record['attempts']},'RUNNING')")
        return record
    def release(self,owner):
        self.sql(f"UPDATE backup_runtime SET lock_owner=NULL,lock_until=NULL WHERE id=1 AND lock_owner={quote(owner)}::uuid")
    def success(self,record,filename,digest,size):
        self.sql(f"""BEGIN; UPDATE backup_runs SET status='SUCCEEDED',finished_at=NOW(),last_error=NULL,filename={quote(filename)},sha256={quote(digest)},size_bytes={size}
          WHERE id={record['id']} AND owner={quote(record['owner'])}::uuid AND status='RUNNING';
          UPDATE backup_attempts SET status='SUCCEEDED',finished_at=NOW() WHERE run_id={record['id']} AND attempt={record['attempts']};
          UPDATE backup_runtime SET last_error=NULL WHERE id=1 AND lock_owner={quote(record['owner'])}::uuid; COMMIT;""")
    def failure(self,record,message):
        status,delay=retry_state(record['attempts']);retry="NOW()+INTERVAL '5 minutes'" if delay else 'NULL'
        self.sql(f"""BEGIN; UPDATE backup_runs SET status={quote(status)},finished_at=NOW(),retry_at={retry},last_error={quote(message[:2000])}
          WHERE id={record['id']} AND owner={quote(record['owner'])}::uuid AND status='RUNNING';
          UPDATE backup_attempts SET status='FAILED',finished_at=NOW(),error={quote(message[:2000])} WHERE run_id={record['id']} AND attempt={record['attempts']}; COMMIT;""")
    def successful(self):return self.rows("SELECT * FROM backup_runs WHERE status='SUCCEEDED' AND kind='SCHEDULED' AND purged_at IS NULL ORDER BY finished_at DESC,id DESC")
    def purged(self,run_id):self.sql(f"UPDATE backup_runs SET purged_at=NOW() WHERE id={int(run_id)} AND status='SUCCEEDED' AND kind='SCHEDULED'")

class Worker:
    def __init__(self,db,directory,label=None):
        self.db=db;self.directory=Path(directory);self.label=label or str(directory);self.owner=str(uuid.uuid4());self.claimed=False
    def heartbeat(self):self.db.heartbeat(self.label,True,owner=self.owner if self.claimed else None)
    def dump(self,path):
        try:
            with tempfile.TemporaryFile() as diagnostic:
                process=subprocess.Popen(['pg_dump','--format=custom','--no-owner','--no-privileges','--file',str(path)],
                    env=self.db.env,stdout=subprocess.DEVNULL,stderr=diagnostic)
                deadline=time.monotonic()+3600
                try:
                    while True:
                        try:
                            code=process.wait(timeout=20)
                            if code:raise BackupError('pg_dump falló. Revise la conexión, permisos y espacio del destino; no se publicó una copia completa.')
                            break
                        except subprocess.TimeoutExpired:
                            if time.monotonic()>deadline:raise BackupError('El respaldo excedió el límite de una hora. Revise el tamaño y rendimiento del almacenamiento.')
                            self.heartbeat()
                finally:
                    if process.poll() is None:process.kill();process.wait()
                subprocess.run(['pg_restore','--list',str(path)],env=self.db.env,stdout=subprocess.DEVNULL,stderr=diagnostic,timeout=60,check=True)
        except BackupError:raise
        except (subprocess.SubprocessError,OSError) as error:raise BackupError('No se pudo crear o verificar el archivo PostgreSQL. Revise las herramientas y los permisos del destino.') from error
    def create(self,record):
        filename=f"siabuc-auto-{record['id']}-{uuid.uuid4().hex}.dump"
        descriptor,temp=tempfile.mkstemp(prefix='.incomplete-',suffix='.dump',dir=self.directory);os.close(descriptor)
        temporary=Path(temp);final=self.directory/filename;checktemp=None
        try:
            self.dump(temporary)
            if temporary.stat().st_size==0:raise BackupError('pg_dump produjo un archivo vacío; el respaldo no se considera válido.')
            with temporary.open('rb') as handle:os.fsync(handle.fileno())
            digest=digest_file(temporary);size=temporary.stat().st_size
            descriptor,name=tempfile.mkstemp(prefix='.checksum-',dir=self.directory);checktemp=Path(name)
            with os.fdopen(descriptor,'w',encoding='utf-8') as handle:
                handle.write(f'{digest}  {filename}\n');handle.flush();os.fsync(handle.fileno())
            self.heartbeat()
            if final.exists():raise BackupError('El archivo de destino ya existe; no se reemplazó ninguna copia.')
            os.replace(temporary,final);os.replace(checktemp,self.directory/(filename+'.sha256'))
            self.db.success(record,filename,digest,size)
        finally:
            temporary.unlink(missing_ok=True)
            if checktemp:checktemp.unlink(missing_ok=True)
    def tick(self,now=None):
        now=now or utcnow()
        try:safe_destination(self.directory)
        except (OSError,BackupError) as error:
            self.db.heartbeat(self.label,False,'No se puede escribir en el destino. Revise la ruta y sus permisos.');return
        self.heartbeat();settings=self.db.settings();day=due_date(settings,now)
        if day:self.db.enqueue(day,settings['updated_at'])
        record=self.db.claim(self.owner)
        if not record:return
        self.claimed=True
        try:
            try:self.create(record)
            except (BackupError,OSError) as error:
                message=str(error) if isinstance(error,BackupError) else 'No se pudo escribir el respaldo; revise permisos y espacio libre del destino.'
                self.db.failure(record,message);self.db.heartbeat(self.label,True,message,owner=self.owner);return
            # A manual copy never prunes anything, and switching the calendar off preserves existing copies.
            current=self.db.settings()
            if record['kind']!='SCHEDULED' or not current['enabled']:return
            try:retain(self.directory,self.db.successful(),current['retention_days'],current['keep_last'],now,self.db.purged)
            except (OSError,ValueError,UnicodeError):self.db.heartbeat(self.label,True,'El respaldo finalizó, pero la limpieza de copias antiguas requiere revisar permisos o archivos.',owner=self.owner)
        finally:self.db.release(self.owner);self.claimed=False

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--health',action='store_true');parser.add_argument('--once',action='store_true');args=parser.parse_args()
    if args.health:
        try:healthy=time.time()-float(HEALTH_FILE.read_text(encoding='ascii'))<120
        except (OSError,ValueError):healthy=False
        raise SystemExit(0 if healthy else 1)
    os.umask(0o077)
    worker=Worker(Database(),os.environ.get('BACKUP_TARGET','/backups'),os.environ.get('BACKUP_DIRECTORY_LABEL','Destino privado de respaldos'))
    while True:
        try:worker.tick()
        except (BackupError,OSError,ValueError,KeyError):
            # No raw subprocess diagnostic, environment variables or credentials in logs.
            print('Respaldo: no se completó la revisión. Revise estado de PostgreSQL, migraciones y permisos.',flush=True)
        if args.once:return
        time.sleep(POLL_SECONDS)

if __name__=='__main__':main()
