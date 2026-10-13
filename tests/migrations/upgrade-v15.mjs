/**
 * Exact V1–V10 SQL upgrade in isolated in-memory PostgreSQL (PGlite).
 * Preserves 1.4 data, including historical MXN/USD and CARD/voided payments.
 * No service process, installation database, port or external email is used.
 *
 * SIABUC_PGLITE_MODULE=/tmp/siabuc-pglite/node_modules/@electric-sql/pglite/dist/index.js node tests/migrations/upgrade-v15.mjs
 * Dependency and PowerShell setup are documented in upgrade-v14.mjs.
 * This tests SQL migration/data/constraints, not HTTP permissions, accounting services,
 * concurrent database connections, Docker startup or Flyway's schema-history table.
 */
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const folder=resolve(root,'backend/src/main/resources/db/migration');
const { PGlite }=await import(process.env.SIABUC_PGLITE_MODULE ? pathToFileURL(resolve(process.env.SIABUC_PGLITE_MODULE)).href : '@electric-sql/pglite');
const db=new PGlite();
const names=(await readdir(folder)).filter(n=>/^V(?:[1-9]|10)__.+\.sql$/.test(n)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]));
assert.deepEqual(names.map(n=>Number(n.match(/^V(\d+)/)[1])),[1,2,3,4,5,6,7,8,9,10]);
const migration=async name=>{await db.exec(await readFile(resolve(folder,name),'utf8'));console.log('Applied '+name);};
const tables=['institutions','app_users','support_cases','case_entries','audit_log','payments','system_lock',
  'notification_outbox','notification_settings','support_settings','case_profiles','case_attachments',
  'portal_notifications','balance_alert_settings','work_timers','remote_sessions'];
const primary=table=>table==='case_profiles'?'case_id':table==='balance_alert_settings'?'institution_id':'id';
const rows=async table=>(await db.query(`SELECT * FROM ${table} ORDER BY ${primary(table)}`)).rows;
let checks=0;
const check=(actual,expected,label)=>{assert.deepEqual(actual,expected,label);checks++;};
const reject=async(sql,state,label)=>{await assert.rejects(db.exec(sql),error=>error.code===state,label);checks++;};

try {
  await db.waitReady;
  for(const name of names.slice(0,8)) await migration(name);
  await db.exec(`
    INSERT INTO institutions(id,name,email,email_allocated_minutes,email_used_minutes,remote_allocated_minutes,remote_used_minutes)
      VALUES(1,'Biblioteca con pagos previos','cliente@example.test',1500,120,900,80),(2,'Segunda institución','segunda@example.test',200,20,100,10);
    INSERT INTO app_users(id,username,display_name,password_hash,auth_version,role,institution_id,email)
      VALUES(1,'admin_anterior','Administración anterior','hash-admin-no-modificar',8,'ADMIN',NULL,'admin@example.test'),
            (2,'cobranza_anterior','Cobranza anterior','hash-billing-no-modificar',4,'BILLING',NULL,'cobranza@example.test'),
            (3,'cliente_anterior','Cliente anterior','hash-cliente-no-modificar',5,'USER',1,'usuario@example.test');
    UPDATE institutions SET support_admin_id=1 WHERE id=1;
    INSERT INTO support_cases(id,institution_id,title,description,channel,status,created_by,assigned_admin_id,waiting_for)
      VALUES(1,1,'Atención anterior abierta','Descripción e historial preservados','EMAIL','OPEN',3,1,'CLIENT'),
            (2,1,'Caso remoto cerrado','Resolución preservada','REMOTE','CLOSED',3,1,'NONE');
    INSERT INTO case_entries(id,case_id,description,channel,minutes,internal,created_by,voided,void_reason)
      VALUES(1,1,'Respuesta pública real','EMAIL',120,FALSE,1,FALSE,NULL),
            (2,2,'Atención remota y cierre','REMOTE',80,FALSE,1,FALSE,NULL),
            (3,1,'Diagnóstico interno reservado',NULL,0,TRUE,1,FALSE,NULL),
            (4,1,'Atención anulada','EMAIL',20,FALSE,1,TRUE,'Minutos reintegrados previamente');
    INSERT INTO payments(id,request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by,voided,void_reason,voided_by,voided_at)
      VALUES(1,'10000000-0000-4000-8000-000000000001',1,'Previo MXN CARD',1234.56,'MXN','2026-10-01','CARD',300,120,'Tarjeta histórica sin tipo conocido',2,FALSE,NULL,NULL,NULL),
            (2,'10000000-0000-4000-8000-000000000002',1,'Previo USD CARD',250.75,'USD','2026-10-02','CARD',120,60,'No convertir dólares ni inferir tipo tarjeta',2,FALSE,NULL,NULL,NULL),
            (3,'10000000-0000-4000-8000-000000000003',1,'MXN anulado',500.00,'MXN','2026-10-03','TRANSFER',60,30,'Registro anulado íntegro',2,TRUE,'Duplicidad corregida',1,'2026-10-04T18:00:00Z'),
            (4,'10000000-0000-4000-8000-000000000004',2,'USD anulado',100.25,'USD','2026-10-04','CASH',20,10,'Anulación de otra institución',2,TRUE,'Referencia cancelada',1,'2026-10-05T18:00:00Z');
    INSERT INTO audit_log(id,actor,action,entity_type,entity_id,details)
      VALUES(1,'cobranza_anterior','CREATE_PAYMENT','PAYMENT',1,'Importe y minutos originales'),
            (2,'admin_anterior','VOID_PAYMENT','PAYMENT',3,'Retiro anterior de acreditación'),
            (3,'admin_anterior','CREATE_ENTRY','ENTRY',1,'Primera respuesta real'),
            (4,'admin_anterior','CHANGE_CASE_STATUS','CASE',2,'Caso cerrado: resuelto');
    INSERT INTO case_profiles(case_id,category,priority,siabuc_version,operating_system,error_message,reproduction_steps,calendar_snapshot,opened_at,resolution_started_at,first_responded_at,resolved_at)
      SELECT c.id,'CATALOGING','HIGH','9.2','Windows 11','Mensaje conservado','Pasos conservados',s.configuration,c.created_at,c.created_at,c.created_at,
        CASE WHEN c.status='CLOSED' THEN c.updated_at ELSE NULL END FROM support_cases c CROSS JOIN support_settings s;
    INSERT INTO case_attachments(id,case_id,entry_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes)
      VALUES(1,1,3,1,'30000000-0000-4000-8000-000000000001','diagnostico.log','text/plain;charset=UTF-8',4,repeat('a',64),decode('686f6c61','hex'));
    INSERT INTO portal_notifications(id,recipient_id,institution_id,case_id,event_type,title,description,internal)
      VALUES(1,1,1,1,'CREATE_ENTRY','Aviso interno anterior','Contenido reservado preservado',TRUE),
            (2,3,1,1,'CREATE_ENTRY','Aviso cliente anterior','Contenido público preservado',FALSE);
    INSERT INTO balance_alert_settings(institution_id,email_threshold_minutes,remote_threshold_minutes,enabled,email_low,remote_low)
      VALUES(1,45,20,TRUE,FALSE,FALSE),(2,15,10,FALSE,FALSE,FALSE);
    INSERT INTO work_timers(id,case_id,admin_id,status,elapsed_millis,segment_started_at,entry_id,confirmed_minutes,adjustment_reason)
      VALUES(1,1,1,'COMMITTED',7200000,NULL,1,120,'Atención anterior confirmada');
    INSERT INTO remote_sessions(id,case_id,admin_id,starts_at,ends_at,note,status,response,responded_by,created_by)
      VALUES(1,2,1,'2026-10-01T16:00:00Z','2026-10-01T17:30:00Z','Sesión anterior concluida','COMPLETED','Validación con el cliente',1,1);
    INSERT INTO notification_outbox(id,event_key,payment_id,event_type,recipient_type,recipient_entity_id,recipient,subject,body,status,attempts,last_error)
      VALUES(1,'20000000-0000-4000-8000-000000000001',1,'CREATE_PAYMENT','INSTITUTION',1,'cliente@example.test','Pago anterior','Importe MXN anterior preservado','SENT',1,NULL),
            (2,'20000000-0000-4000-8000-000000000002',2,'CREATE_PAYMENT','INSTITUTION',1,'cliente@example.test','Pago USD descartado','No reactivar aviso anterior','SUPPRESSED',0,'Correo pausado al registrar');
    UPDATE notification_settings SET paused=TRUE,suppressed_through_id=2 WHERE id=1;
  `);
  const sequences=new Map(), before=new Map();
  for(const table of tables) {
    before.set(table,await rows(table));
    const sequence=(await db.query('SELECT pg_get_serial_sequence($1,$2) AS name',[table,primary(table)])).rows[0].name;
    if(sequence) {
      await db.query(`SELECT setval($1,(SELECT MAX(${primary(table)}) FROM ${table}),TRUE)`,[sequence]);
      sequences.set(sequence,(await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0]);
    }
  }
  const moneySql="SELECT currency,SUM(amount)::text AS amount,SUM(email_minutes)::text AS email,SUM(remote_minutes)::text AS remote FROM payments WHERE NOT voided GROUP BY currency ORDER BY currency";
  const moneyBefore=(await db.query(moneySql)).rows;
  await migration(names[8]);

  for(const table of tables) {
    const after=await rows(table),original=before.get(table);
    check(after.length,original.length,`${table}: same row count`);
    for(let i=0;i<original.length;i++)for(const key of Object.keys(original[i]))
      check(after[i][key],original[i][key],`${table}: preserve ${primary(table)}=${original[i][primary(table)]}.${key}`);
  }
  for(const [sequence,value] of sequences)check((await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0],value,'Preserve '+sequence);
  check((await db.query(moneySql)).rows,moneyBefore,'Historical currency totals and credited minutes stay separate and exact');
  check(moneyBefore,[{currency:'MXN',amount:'1234.56',email:'300',remote:'120'},{currency:'USD',amount:'250.75',email:'120',remote:'60'}],'Expected independent historical totals');
  check((await db.query('SELECT policy_id FROM payments ORDER BY id')).rows,[{policy_id:null},{policy_id:null},{policy_id:null},{policy_id:null}],'Legacy payments are unlinked, never assigned invented policies');
  for(const table of ['support_policies','policy_history'])check((await db.query(`SELECT COUNT(*)::int AS n FROM ${table}`)).rows[0].n,0,table+' starts empty');
  console.log('PASS: all 16 pre-existing tables, bytea attachments, 4 mixed-currency/card/voided payments, balances, sequence positions and paused/suppressed mail preserved.');

  await db.exec(`INSERT INTO support_policies(institution_id,folio,description,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,created_by)
    VALUES(1,'POL-2026-001','Contrato de prueba SQL','2026-10-01','2027-09-30',1000.00,600,300,2)`);
  check((await rows('support_policies'))[0].currency,'MXN','Policies are MXN');
  check(await rows('institutions'),before.get('institutions'),'Creating policy SQL does not mutate institution allocations');
  const payment=(reference,{policy='NULL',amount='1.00',email=1,remote=0,method='TRANSFER',institution=1,currency='MXN'}={})=>`INSERT INTO payments(request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by,policy_id) VALUES(gen_random_uuid(),${institution},'${reference}',${amount},'${currency}','2026-10-13','${method}',${email},${remote},'Fixture de restricciones SQL',2,${policy})`;
  for(const method of ['TRANSFER','DEPOSIT','CASH','CREDIT_CARD','DEBIT_CARD','CHECK','OTHER','CARD']) {
    await db.exec(payment('Nuevo método '+method,{method}));checks++;
  }
  await db.exec(payment('Abono monetario',{policy:'1',amount:'50.25',email:0,remote:0}));checks++;
  await db.exec(payment('Entrega de minutos',{policy:'1',amount:'0.00',email:60,remote:15}));checks++;
  // Full-payment/overpayment rules are service-level and are covered by HTTP integration tests,
  // not by these direct SQL rows. This isolates widening only the intended schema constraints.
  await reject(payment('Sin póliza importe cero',{amount:'0.00'}),'23514','Unlinked zero-money payment remains invalid');
  await reject(payment('Sin póliza sin minutos',{email:0,remote:0}),'23514','Unlinked money-only payment remains invalid');
  await reject(payment('Todo cero',{policy:'1',amount:'0.00',email:0,remote:0}),'23514','Linked all-zero payment invalid');
  await reject(payment('Negativo',{policy:'1',amount:'-0.01'}),'23514','Negative money invalid');
  await reject(payment('Póliza inexistente',{policy:'9999'}),'23503','Policy reference enforced');
  await reject(payment('Póliza de otra institución',{policy:'1',institution:2}),'23503','Composite foreign key protects the policy institution');
  await reject(payment('Póliza en otra moneda',{policy:'1',currency:'USD'}),'23514','Linked payments require MXN without modifying historical USD');
  await reject("INSERT INTO support_policies(institution_id,folio,description,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,created_by) VALUES(1,' pol-2026-001 ','Duplicada','2026-10-01','2027-09-30',1,1,0,2)",'23505','Case/space-insensitive policy folio uniqueness');
  await reject("UPDATE support_policies SET currency='USD' WHERE id=1",'23514','Only MXN policy currency');
  await reject("UPDATE support_policies SET status='CANCELLED',cancel_reason=NULL,cancelled_by=2,cancelled_at=NOW() WHERE id=1",'23514','A cancellation requires an explicit reason even under SQL NULL semantics');
  await reject("DELETE FROM support_policies WHERE id=1",'23503','Referenced policy cannot be physically removed');
  await db.exec("INSERT INTO policy_history(policy_id,action,description,payment_id,created_by) VALUES(1,'CREATE_POLICY','Instantánea inmutable de creación',NULL,2)");checks++;
  check(await rows('institutions'),before.get('institutions'),'Constraint fixtures never alter purchased/used balance fields');
  console.log('PASS: optional policy FK, distinct payment methods, legacy CARD, linked money-only/minutes-only shape, invalid values and history references.');
  const preV10Tables=[...tables,'support_policies','policy_history'];
  const preV10=new Map();for(const table of preV10Tables)preV10.set(table,await rows(table));
  const preV10Sequences=new Map();
  for(const table of preV10Tables){
    const sequence=(await db.query('SELECT pg_get_serial_sequence($1,$2) AS name',[table,primary(table)])).rows[0].name;
    if(sequence)preV10Sequences.set(sequence,(await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0]);
  }
  await migration(names[9]);
  for(const table of preV10Tables)check(await rows(table),preV10.get(table),'V10 preserves all fields of '+table);
  for(const [sequence,value] of preV10Sequences)check((await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0],value,'V10 preserves '+sequence);
  check((await db.query('SELECT COUNT(*)::int AS n FROM payment_attachments')).rows[0].n,0,'V10 invents no payment proofs');
  await db.exec("INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes) VALUES(1,2,gen_random_uuid(),'transferencia.png','image/png',8,repeat('b',64),decode('89504e470d0a1a0a','hex'))");checks++;
  check((await db.query("SELECT encode(file_bytes,'hex') AS bytes FROM payment_attachments")).rows[0].bytes,'89504e470d0a1a0a','Proof bytes persist exactly');
  await reject("INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes) VALUES(9999,2,gen_random_uuid(),'falta.png','image/png',8,repeat('b',64),decode('89504e470d0a1a0a','hex'))",'23503','Proof requires existing payment');
  await reject("INSERT INTO payment_attachments(payment_id,uploaded_by,request_id,filename,content_type,size_bytes,sha256,file_bytes) VALUES(1,2,gen_random_uuid(),'log.txt','text/plain',1,repeat('b',64),decode('78','hex'))",'23514','Payment evidence excludes diagnostics');
  await db.exec("INSERT INTO policy_history(policy_id,action,description,payment_id,created_by) SELECT 1,'ATTACH_PAYMENT_PROOF','Evidencia adjunta sin alterar cobro ni minutos',id,2 FROM payments WHERE reference='Abono monetario'");checks++;
  check(await rows('institutions'),before.get('institutions'),'Proof/history SQL does not alter support allocations or consumption');
  check(await rows('payments'),preV10.get('payments'),'Proof/history SQL leaves every payment value unchanged');
  console.log('PASS: V10 preserves 18 existing tables and sequence positions; payment proofs start empty, persist BYTEA and reference existing payments; policy proof history adds no money/minutes.');
  console.log(`SUCCESS: exact V1–V10 SQL; 16 legacy and 18 V9 tables preserved; 4 historical payments; ${checks} assertions/statements verified. No HTTP or parallel-connection claims.`);
} finally { await db.close(); }
