/**
 * Upgrade smoke/regression: exact repository SQL V1–V8, isolated in-memory PostgreSQL (PGlite).
 * Does not connect to the installation, start a server, use a port or send messages.
 *
 * Install the test-only dependency outside the project if necessary:
 *   npm install --prefix /tmp/siabuc-pglite @electric-sql/pglite@0.5.9
 *   SIABUC_PGLITE_MODULE=/tmp/siabuc-pglite/node_modules/@electric-sql/pglite/dist/index.js node tests/migrations/upgrade-v14.mjs
 * PowerShell:
 *   $env:SIABUC_PGLITE_MODULE='C:\path\node_modules\@electric-sql\pglite\dist\index.js'
 *   node tests/migrations/upgrade-v14.mjs
 *
 * If installed in the project tooling environment, the package import can resolve without the variable.
 * This verifies PostgreSQL SQL/data behavior, not Docker startup or Flyway's schema-history metadata.
 */
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const projectRoot=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const migrationRoot=resolve(projectRoot,'backend/src/main/resources/db/migration');
const modulePath=process.env.SIABUC_PGLITE_MODULE;
const { PGlite }=await import(modulePath ? pathToFileURL(resolve(modulePath)).href : '@electric-sql/pglite');
const db=new PGlite();
const names=(await readdir(migrationRoot)).filter(name=>/^V[1-8]__.+\.sql$/.test(name)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]));
assert.deepEqual(names.map(name=>Number(name.match(/^V(\d+)/)[1])),[1,2,3,4,5,6,7,8],'Expected exactly migrations V1–V8');
const migration=async name=>{await db.exec(await readFile(resolve(migrationRoot,name),'utf8'));console.log('Applied '+name);};
const rows=async(table,order='id')=>(await db.query(`SELECT * FROM ${table} ORDER BY ${order}`)).rows;
const stamp=value=>value===null?null:new Date(value).toISOString();

try {
  await db.waitReady;
  for(const name of names.slice(0,4)) await migration(name);
  // Fixtures are inserted in the real 1.3.3 schema: there is no case_profiles table yet.
  await db.exec(`
    INSERT INTO institutions(id,name,contact_name,email,phone,notes,email_allocated_minutes,email_used_minutes,remote_allocated_minutes,remote_used_minutes,created_at,updated_at)
    VALUES(1,'Biblioteca heredada','Contacto conservado','cliente@example.test','3120000000','Nota administrativa conservada',1200,25,600,40,'2025-01-01T15:00:00Z','2026-10-10T15:00:00Z'),
          (2,'Otra institución','Segundo contacto','otra@example.test','','Sin consumos',60,0,30,0,'2026-09-01T15:00:00Z','2026-09-01T15:00:00Z');
    INSERT INTO app_users(id,username,display_name,password_hash,auth_version,role,institution_id,active,email,created_at)
    VALUES(1,'admin_prev','Administrativo anterior','hash-admin-conservado',3,'ADMIN',NULL,TRUE,'admin@example.test','2025-01-01T15:00:00Z'),
          (2,'admin_inactivo','Administrativo inactivo','hash-inactivo-conservado',5,'ADMIN',NULL,FALSE,'anterior@example.test','2025-01-01T15:00:00Z'),
          (3,'cliente_prev','Cliente anterior','hash-cliente-conservado',7,'USER',1,TRUE,'usuario@example.test','2025-01-01T15:00:00Z'),
          (4,'cobranza_prev','Cobranza anterior','hash-cobranza-conservado',2,'BILLING',NULL,TRUE,'cobranza@example.test','2025-01-01T15:00:00Z'),
          (5,'otro_cliente','Otra cuenta cliente','hash-otro-conservado',1,'USER',2,TRUE,'otro@example.test','2026-09-01T15:00:00Z');
    UPDATE institutions SET support_admin_id=1 WHERE id=1;
    INSERT INTO support_cases(id,institution_id,title,description,channel,status,created_by,assigned_admin_id,created_at,updated_at)
    VALUES(1,1,'Abierto con respuesta y consumo','Descripción vigente abierta','EMAIL','OPEN',3,1,'2026-10-01T15:00:00Z','2026-10-10T18:00:00Z'),
          (2,1,'Cerrado con evidencia','Descripción vigente cerrada','REMOTE','CLOSED',3,2,'2026-10-02T15:00:00Z','2026-10-10T18:00:00Z'),
          (3,1,'Reabierto dos veces','Descripción vigente reabierta','EMAIL','OPEN',3,1,'2026-10-01T15:00:00Z','2026-10-10T18:00:00Z'),
          (4,1,'Cerrado sin fechas demostrables','Importación antigua incompleta','EMAIL','CLOSED',1,NULL,'2025-04-01T15:00:00Z','2026-10-10T18:00:00Z'),
          (5,2,'Abierto sin respuesta pública','Solo existen comentario cliente y nota interna','REMOTE','OPEN',5,NULL,'2026-10-03T15:00:00Z','2026-10-10T18:00:00Z');
    INSERT INTO case_entries(id,case_id,description,channel,minutes,internal,created_by,created_at,voided,void_reason)
    VALUES(1,1,'Comentario inicial del cliente',NULL,0,FALSE,3,'2026-10-01T15:10:00Z',FALSE,NULL),
          (2,1,'Caso tomado por administrativo',NULL,0,FALSE,1,'2026-10-01T15:20:00Z',FALSE,NULL),
          (3,1,'Diagnóstico privado anterior a la respuesta',NULL,0,TRUE,1,'2026-10-01T15:30:00Z',FALSE,NULL),
          (4,1,'Primera respuesta real; cargo después anulado','EMAIL',10,FALSE,1,'2026-10-01T15:50:00Z',TRUE,'Corrección de consumo; respuesta sí enviada'),
          (5,1,'Atención pública vigente','EMAIL',25,FALSE,1,'2026-10-01T16:00:00Z',FALSE,NULL),
          (6,2,'Primera atención remota pública','REMOTE',40,FALSE,1,'2026-10-02T16:00:00Z',FALSE,NULL),
          (7,2,'Caso cerrado: solución confirmada',NULL,0,FALSE,1,'2026-10-02T17:00:00Z',FALSE,NULL),
          (8,3,'Respuesta pública inicial',NULL,0,FALSE,1,'2026-10-01T16:00:00Z',FALSE,NULL),
          (9,3,'Caso cerrado: primer cierre',NULL,0,FALSE,1,'2026-10-02T17:00:00Z',FALSE,NULL),
          (10,3,'Caso reabierto: primera reapertura',NULL,0,FALSE,1,'2026-10-05T15:00:00Z',FALSE,NULL),
          (11,3,'Caso cerrado: segundo cierre',NULL,0,FALSE,1,'2026-10-06T17:00:00Z',FALSE,NULL),
          (12,3,'Caso reabierto: segunda reapertura',NULL,0,FALSE,1,'2026-10-07T15:00:00Z',FALSE,NULL),
          (13,4,'Cambio importado sin auditoría CREATE_ENTRY',NULL,0,FALSE,1,'2025-04-01T16:00:00Z',FALSE,NULL),
          (14,4,'Nota interna de caso antiguo',NULL,0,TRUE,1,'2025-04-01T16:10:00Z',FALSE,NULL),
          (15,5,'Comentario de otro cliente',NULL,0,FALSE,5,'2026-10-03T16:00:00Z',FALSE,NULL),
          (16,5,'Nota interna no es primera respuesta',NULL,0,TRUE,1,'2026-10-03T16:30:00Z',FALSE,NULL);
    INSERT INTO audit_log(actor,action,entity_type,entity_id,details,created_at)
    VALUES('cliente_prev','CREATE_ENTRY','ENTRY',1,'Caso=1; comentario cliente','2026-10-01T15:10:00Z'),
          ('admin_prev','CLAIM_CASE','CASE',1,'Caso tomado por administrativo','2026-10-01T15:20:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',3,'Caso=1; interno=true','2026-10-01T15:30:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',4,'Caso=1; minutos=10; interno=false','2026-10-01T15:50:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',5,'Caso=1; minutos=25; interno=false','2026-10-01T16:00:00Z'),
          ('admin_prev','VOID_ENTRY','ENTRY',4,'Reintegro de 10 minutos, sin borrar comunicación','2026-10-01T16:05:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',6,'Caso=2; minutos=40; interno=false','2026-10-02T16:00:00Z'),
          ('admin_prev','CHANGE_CASE_STATUS','CASE',2,'Caso cerrado: solución confirmada','2026-10-02T17:00:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',8,'Caso=3; comentario público','2026-10-01T16:00:00Z'),
          ('admin_prev','CHANGE_CASE_STATUS','CASE',3,'Caso cerrado: primer cierre','2026-10-02T17:00:00Z'),
          ('admin_prev','CHANGE_CASE_STATUS','CASE',3,'Caso reabierto: primera reapertura','2026-10-05T15:00:00Z'),
          ('admin_prev','CHANGE_CASE_STATUS','CASE',3,'Caso cerrado: segundo cierre','2026-10-06T17:00:00Z'),
          ('admin_prev','CHANGE_CASE_STATUS','CASE',3,'Caso reabierto: segunda reapertura','2026-10-07T15:00:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',14,'Caso=4; interno=true','2025-04-01T16:10:00Z'),
          ('otro_cliente','CREATE_ENTRY','ENTRY',15,'Caso=5; comentario cliente','2026-10-03T16:00:00Z'),
          ('admin_prev','CREATE_ENTRY','ENTRY',16,'Caso=5; interno=true','2026-10-03T16:30:00Z');
    INSERT INTO payments(id,request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by,created_at,voided,void_reason,voided_by,voided_at)
    VALUES(1,'10000000-0000-4000-8000-000000000001',1,'Pago-vigente',1000.50,'MXN','2026-09-01','TRANSFER',600,300,'Pago vigente conservado',4,'2026-09-01T15:00:00Z',FALSE,NULL,NULL,NULL),
          (2,'10000000-0000-4000-8000-000000000002',1,'Pago-anulado',300.00,'MXN','2026-09-02','CASH',100,50,'Pago anulado conservado',4,'2026-09-02T15:00:00Z',TRUE,'Referencia corregida',1,'2026-09-03T15:00:00Z');
    INSERT INTO notification_outbox(id,event_key,case_id,event_type,recipient_type,recipient_entity_id,recipient,subject,body,status,attempts,last_error,created_at,sent_at)
    VALUES(1,'20000000-0000-4000-8000-000000000001',1,'CREATE_ENTRY','INSTITUTION',1,'cliente@example.test','Aviso enviado','Contenido enviado inmutable','SENT',1,NULL,'2026-10-01T16:00:00Z','2026-10-01T16:01:00Z'),
          (2,'20000000-0000-4000-8000-000000000002',2,'CHANGE_CASE_STATUS','INSTITUTION',1,'cliente@example.test','Aviso descartado durante pausa','Contenido suprimido conservado','SUPPRESSED',0,'Envío pausado','2026-10-02T17:00:00Z',NULL),
          (3,'20000000-0000-4000-8000-000000000003',3,'CHANGE_CASE_STATUS','INSTITUTION',1,'cliente@example.test','Aviso pendiente nuevo','Contenido pendiente conservado','PENDING',0,NULL,'2026-10-07T15:00:00Z',NULL);
    UPDATE notification_settings SET paused=FALSE,suppressed_through_id=2 WHERE id=1;
  `);

  const preserved=['institutions','app_users','support_cases','case_entries','audit_log','payments','notification_outbox','notification_settings'];
  // Explicit fixture IDs must leave serial sequences in the same state as real INSERTs.
  const serialTables=preserved.filter(table=>table!=='notification_settings');
  const sequences=new Map();
  for(const table of serialTables) {
    const sequence=(await db.query("SELECT pg_get_serial_sequence($1,'id') AS name",[table])).rows[0].name;
    await db.query(`SELECT setval($1,(SELECT MAX(id) FROM ${table}),TRUE)`,[sequence]);
    sequences.set(sequence,(await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0]);
  }
  const before=new Map();
  for(const table of preserved) before.set(table,await rows(table));
  assert.equal((await db.query("SELECT COUNT(*)::int AS n FROM information_schema.tables WHERE table_name='case_profiles'")).rows[0].n,0,'Fixture must predate V5');
  for(const name of names.slice(4)) await migration(name);

  for(const table of preserved) {
    const after=await rows(table),original=before.get(table);
    assert.equal(after.length,original.length,table+' row count preserved');
    for(let index=0;index<original.length;index++) for(const key of Object.keys(original[index]))
      assert.deepEqual(after[index][key],original[index][key],table+' row '+original[index].id+' column '+key+' preserved');
  }
  for(const [sequence,original] of sequences)
    assert.deepEqual((await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0],original,sequence+' position preserved');
  console.log('PASS: institutions, balances, credentials, case descriptions/status, history, audits, payments, serial sequences and mail queue/settings remain unchanged.');

  const cases=await rows('case_profiles','case_id');
  assert.equal(cases.length,5);
  const calendar=JSON.parse((await rows('support_settings'))[0].configuration);
  for(const row of cases) {
    assert.equal(row.category,'OTHER');assert.equal(row.priority,'NORMAL');assert.equal(row.legacy,true);
    for(const field of ['siabuc_version','operating_system','error_message','reproduction_steps']) assert.equal(row[field],'');
    assert.deepEqual(JSON.parse(row.calendar_snapshot),calendar);
    assert.equal(stamp(row.opened_at),stamp(before.get('support_cases').find(c=>c.id===row.case_id).created_at));
  }
  assert.equal(calendar.timezone,'America/Mexico_City');assert.deepEqual(calendar.workDays,[1,2,3,4,5]);
  assert.equal(calendar.startTime,'09:00');assert.equal(calendar.endTime,'17:00');assert.deepEqual(calendar.holidays,[]);
  assert.deepEqual(calendar.goals.NORMAL,{firstResponseMinutes:240,resolutionMinutes:1440});
  const byId=id=>cases.find(c=>c.case_id===id);
  assert.equal(stamp(byId(1).first_responded_at),'2026-10-01T15:50:00.000Z','Voiding a charge must preserve actual first communication');
  assert.equal(stamp(byId(1).resolution_started_at),'2026-10-01T15:00:00.000Z');assert.equal(byId(1).resolved_at,null);
  assert.equal(stamp(byId(2).first_responded_at),'2026-10-02T16:00:00.000Z');
  assert.equal(stamp(byId(2).resolved_at),'2026-10-02T17:00:00.000Z','Closure must use status audit, not later updated_at');
  assert.equal(stamp(byId(3).first_responded_at),'2026-10-01T16:00:00.000Z');
  assert.equal(stamp(byId(3).resolution_started_at),'2026-10-07T15:00:00.000Z','Latest real reopening starts resolution');assert.equal(byId(3).resolved_at,null);
  assert.equal(byId(4).first_responded_at,null);assert.equal(byId(4).resolved_at,null,'Missing closure evidence must stay unknown');
  assert.equal(byId(5).first_responded_at,null,'Client and internal comments are not first response');assert.equal(byId(5).resolved_at,null);
  console.log('PASS: all five legacy profiles preserve creation times, reconstruct only proven public responses/closure/reopening, and keep unknown dates null.');

  const waiting=(await db.query('SELECT id,status,waiting_for FROM support_cases ORDER BY id')).rows;
  for(const row of waiting) assert.equal(row.waiting_for,row.status==='CLOSED'?'NONE':'ADMIN');
  for(const table of ['case_attachments','portal_notifications','balance_alert_settings','work_timers','remote_sessions'])
    assert.equal((await db.query(`SELECT COUNT(*)::int AS n FROM ${table}`)).rows[0].n,0,table+' starts empty; no invented events or charges');
  const balance=(await db.query('SELECT email_allocated_minutes-email_used_minutes AS email,remote_allocated_minutes-remote_used_minutes AS remote FROM institutions WHERE id=1')).rows[0];
  assert.equal(Number(balance.email),1175);assert.equal(Number(balance.remote),560);
  assert.equal((await db.query("SELECT status FROM notification_outbox WHERE id=2")).rows[0].status,'SUPPRESSED','Upgrade cannot reactivate suppressed mail');
  console.log('PASS: V6–V8 initialize attachments, bell, timers and agenda without synthetic rows; closed cases wait for nobody; suppressed email stays suppressed.');
  console.log('SUCCESS: exact V1–V8 SQL upgrade, five realistic legacy cases, eight existing tables preserved.');
} finally {
  await db.close();
}
