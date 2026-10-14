/**
 * Exact V1–V24 SQL upgrade in isolated in-memory PostgreSQL (PGlite).
 * Preserves 1.4 and 1.5 data, including historical MXN/USD and CARD/voided payments.
 * No service process, installation database, port or external email is used.
 *
 * SIABUC_PGLITE_MODULE=/tmp/siabuc-pglite/node_modules/@electric-sql/pglite/dist/index.js node tests/migrations/upgrade-v17.mjs
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
  const v15Tables=[...preV10Tables,'payment_attachments'];
  const v15Rows=new Map(), v15Sequences=new Map();
  for(const table of v15Tables){
    v15Rows.set(table,await rows(table));
    const sequence=(await db.query('SELECT pg_get_serial_sequence($1,$2) AS name',[table,primary(table)])).rows[0].name;
    if(sequence)v15Sequences.set(sequence,(await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0]);
  }
  const additions=(await readdir(folder)).filter(n=>/^V1[1-6]__.+\.sql$/.test(n)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]));
  check(additions.map(n=>Number(n.match(/^V(\d+)/)[1])),[11,12,13,14,15,16],'Every v1.6 migration is present once');
  for(const name of additions){
    await migration(name);
    for(const table of v15Tables){
      const previous=v15Rows.get(table),current=await rows(table);
      check(current.length,previous.length,`${name}: row count of ${table}`);
      const oldColumns=previous.length?Object.keys(previous[0]):[];
      const selectOld=row=>Object.fromEntries(oldColumns.map(k=>[k,row[k]]));
      check(current.map(selectOld),previous.map(selectOld),`${name}: existing values of ${table}`);
    }
    for(const [sequence,value] of v15Sequences)check((await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0],value,`${name}: preserve ${sequence}`);
  }
  // Add representative v1.6 data before applying ANY v1.7 migration.
  await db.exec(`
    INSERT INTO support_policies(id,institution_id,folio,description,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,created_by)
      VALUES(2,1,'POL-RENOVADA-001','Renovación histórica','2027-10-01','2028-09-30',1500.00,600,300,2);
    INSERT INTO support_quotes(institution_id,folio,description,expires_on,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,
      status,previous_policy_id,converted_policy_id,conversion_folio,created_by,sent_at,sent_evidence,decision_contact,decision_evidence,decided_on,decision_recorded_by,decision_recorded_at)
      VALUES(1,'COT-ANTERIOR','Cotización convertida preservada','2026-11-01','2027-10-01','2028-09-30',1500,600,300,
      'CONVERTED',1,2,'POL-RENOVADA-001',2,NOW(),'Emitida antes de actualizar','Contacto anterior','Aceptación anterior','2026-10-13',2,NOW());
    INSERT INTO quote_history(quote_id,action,description,created_by) VALUES(1,'CONVERT_QUOTE','Conversión ya realizada',2);
    INSERT INTO policy_renewals(institution_id,previous_policy_id,new_policy_id,quote_id,created_by) VALUES(1,1,2,1,2);
    INSERT INTO renewal_notices(policy_id,institution_id,institution_name,policy_folio,expiration_date) VALUES(1,1,'Biblioteca con pagos previos','POL-2026-001','2027-09-30');
    INSERT INTO renewal_notice_reads(notice_id,user_id) VALUES(1,2);
    INSERT INTO client_finance_access(institution_id,enabled,updated_by) VALUES(1,TRUE,1);
    INSERT INTO payment_requests(submission_id,payment_request_id,institution_id,reference,amount,paid_on,method,email_minutes,remote_minutes,description,submitted_by)
      VALUES(gen_random_uuid(),gen_random_uuid(),1,'Por revisar antes de actualizar',200,'2026-10-13','TRANSFER',60,0,'Comprobante pendiente, no acreditar al migrar',3);
    INSERT INTO payment_request_attachments(request_id,uploaded_by,submission_id,filename,content_type,size_bytes,sha256,file_bytes)
      VALUES(1,3,gen_random_uuid(),'solicitud.png','image/png',8,repeat('c',64),decode('89504e470d0a1a0a','hex'));
    INSERT INTO payment_receipts(payment_id,folio,snapshot,issued_by) VALUES(1,'SIABUC-REC-0000000001','{"amount":1234.56,"currency":"MXN","reference":"Recibo anterior","privateNote":"Snapshot intacto"}',2);
    INSERT INTO knowledge_articles(title,category,summary,body,status,created_by,updated_by,published_at)
      VALUES('Artículo público anterior','CATALOGING','Resumen anterior','Texto de solución anterior','PUBLISHED',1,1,NOW());
    INSERT INTO knowledge_article_history(article_id,revision,action,title,category,siabuc_version,summary,body,status,actor)
      VALUES(1,1,'PUBLISH','Artículo público anterior','CATALOGING','','Resumen anterior','Texto de solución anterior','PUBLISHED',1);
    INSERT INTO knowledge_case_links(case_id,article_id,linked_by) VALUES(1,1,1);
    INSERT INTO knowledge_link_events(case_id,article_id,action,actor) VALUES(1,1,'LINK',1);
    INSERT INTO satisfaction_surveys(request_id,case_id,institution_id,institution_name,case_title,respondent_id,respondent_username,respondent_name,administrator_id,administrator_username,administrator_name,score,comment)
      VALUES(gen_random_uuid(),2,1,'Biblioteca con pagos previos','Caso remoto cerrado',3,'cliente_anterior','Cliente anterior',1,'admin_anterior','Administración anterior',5,'Encuesta anterior intacta');
    INSERT INTO backup_runs(kind,status,requested_by,attempts,filename,sha256,size_bytes,started_at,finished_at)
      VALUES('MANUAL','SUCCEEDED',1,1,'siabuc-manual-historico.dump',repeat('d',64),128,NOW(),NOW());
    INSERT INTO backup_attempts(run_id,attempt,started_at,finished_at,status) VALUES(1,1,NOW(),NOW(),'SUCCEEDED');
  `);
  // JSONB rows give a deterministic representation for composite PK and BYTEA tables.
  const v16Tables=(await db.query("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")).rows.map(r=>r.tablename);
  const allRows=async table=>(await db.query(`SELECT to_jsonb(t) AS data FROM ${table} t ORDER BY to_jsonb(t)::text`)).rows.map(r=>r.data);
  const oldRows=new Map(),oldSequences=new Map();
  for(const table of v16Tables)oldRows.set(table,await allRows(table));
  for(const row of (await db.query("SELECT sequencename FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename")).rows)
    oldSequences.set(row.sequencename,(await db.query(`SELECT last_value,is_called FROM ${row.sequencename}`)).rows[0]);
  check((await db.query('SELECT enabled,destination_confirmed FROM backup_settings')).rows,[{enabled:false,destination_confirmed:false}],'V1.6 backup default remains off before upgrade');
  const v17Additions=(await readdir(folder)).filter(n=>/^V(?:1[7-9]|2[0-4])__.+\.sql$/.test(n)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]));
  check(v17Additions.map(n=>Number(n.match(/^V(\d+)/)[1])),[17,18,19,20,21,22,23,24],'All eight v1.7 migrations exist exactly once');
  for(const name of v17Additions){
    await migration(name);
    for(const table of v16Tables){
      const previous=oldRows.get(table),current=await allRows(table),columns=previous.length?Object.keys(previous[0]):[];
      const project=rows=>rows.map(row=>Object.fromEntries(columns.map(key=>[key,row[key]]))).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)));
      check(current.length,previous.length,`${name}: preserve row count of ${table}`);
      check(project(current),project(previous),`${name}: preserve every old field of ${table}`);
    }
    for(const [sequence,old] of oldSequences)check((await db.query(`SELECT last_value,is_called FROM ${sequence}`)).rows[0],old,`${name}: preserve old sequence ${sequence}`);
    check((await db.query('SELECT enabled,destination_confirmed FROM backup_settings')).rows,[{enabled:false,destination_confirmed:false}],`${name}: backups remain OFF without destination confirmation`);
  }
  console.log(`PASS: all ${v16Tables.length} existing tables and ${oldSequences.size} sequences preserved across V17–V24; v1.6 quotes, renewals, pending payments/proofs, receipts, knowledge, surveys and manual backup records retained.`);

  // Scope constraints, not a substitute for HTTP/service authorization tests.
  await db.exec("INSERT INTO app_users(username,display_name,password_hash,role) VALUES('tecnico_nuevo','Técnico nuevo','hash-tech','TECH')");checks++;
  await reject("INSERT INTO app_users(username,display_name,password_hash,role,institution_id) VALUES('tech_invalid','Inválido','hash','TECH',1)",'23514','TECH has no institution-scoped customer identity');
  await reject("INSERT INTO app_users(username,display_name,password_hash,role) VALUES('user_invalid','Inválido','hash','USER')",'23514','USER still requires an institution');
  await db.exec(`INSERT INTO saved_case_views(owner_id,name,filters) VALUES(1,'Mis pendientes','{"status":"OPEN","assignment":"mine","priority":"URGENT"}'),(3,'Mis pendientes','{"status":"OPEN","assignment":"all","institutionId":1}')`);checks++;
  await reject(`INSERT INTO saved_case_views(owner_id,name,filters) VALUES(1,'mis pendientes','{}')`,'23505','Saved view name uniqueness is per owner and case-insensitive');
  await reject(`INSERT INTO saved_case_views(owner_id,name,filters) VALUES(1,'No objeto','[]')`,'23514','Saved filters must be a JSON object');
  check((await db.query("SELECT filters->>'assignment' AS assignment FROM saved_case_views WHERE owner_id=3")).rows,[{assignment:'all'}],'Owner-filtered saved view query isolates user data');
  await db.exec("INSERT INTO sla_alert_deliveries(case_id,goal,due_at,recipient_id) VALUES(1,'RESOLUTION','2027-01-01T16:00:00Z',1)");checks++;
  await reject("INSERT INTO sla_alert_deliveries(case_id,goal,due_at,recipient_id) VALUES(1,'RESOLUTION','2027-01-01T16:00:00Z',1)",'23505','SLA notification target deduplication');
  await reject("UPDATE sla_alert_settings SET lead_minutes=0 WHERE id=1",'23514','SLA lead must be positive');
  await db.exec("INSERT INTO reply_templates(title,category,body,created_by) VALUES('Diagnóstico','INSTALLATION','Revise versión y sistema operativo.',1)");checks++;
  await reject("UPDATE reply_templates SET category='UNKNOWN' WHERE id=1",'23514','Template category is constrained');
  await db.exec(`INSERT INTO reply_template_history(template_id,revision,actor,action,snapshot) VALUES(1,1,'admin_anterior','CREATE','{"body":"Revise versión y sistema operativo."}')`);checks++;
  await reject(`INSERT INTO reply_template_history(template_id,revision,actor,action,snapshot) VALUES(1,1,'admin_anterior','EDIT','{}')`,'23505','Template snapshot revision unique');

  const budgetsBefore=await allRows('institutions'),paymentsBefore=await allRows('payments'),entriesBefore=await allRows('case_entries');
  await db.exec("INSERT INTO support_incidents(title,description,created_by,updated_by) VALUES('Diagnóstico privado','Otra institución tiene una falla privada',1,1)");checks++;
  await reject("UPDATE support_incidents SET published=TRUE WHERE id=1",'23514','Incident publishing requires separate nonempty public fields and timestamp');
  await db.exec("INSERT INTO incident_case_links(incident_id,case_id,linked_by) VALUES(1,1,1),(1,2,1)");checks++;
  const customerIncidentSql="SELECT s.public_title,s.public_summary,s.public_status FROM support_incidents s JOIN incident_case_links l ON l.incident_id=s.id JOIN support_cases c ON c.id=l.case_id WHERE c.id=$1 AND c.institution_id=$2 AND l.active AND s.published";
  check((await db.query(customerIncidentSql,[1,1])).rows,[],'Unpublished incidents return no customer rows');
  await db.exec("UPDATE support_incidents SET public_title='Aviso general',public_summary='El equipo revisa la falla.',public_status='OPEN',published=TRUE,published_at=NOW() WHERE id=1");checks++;
  check((await db.query(customerIncidentSql,[1,2])).rows,[],'Customer query checks institution before exposing publication');
  check((await db.query(customerIncidentSql,[1,1])).rows,[{public_title:'Aviso general',public_summary:'El equipo revisa la falla.',public_status:'OPEN'}],'Customer projection contains only reviewed public fields');
  await db.exec("UPDATE support_incidents SET status='RESOLVED',description='Nueva nota interna' WHERE id=1");checks++;
  check((await db.query(customerIncidentSql,[1,1])).rows[0].public_status,'OPEN','Changing private incident state preserves explicit public snapshot');

  await db.exec("INSERT INTO support_packages(code,created_by) VALUES('PAQUETE-PRUEBA',2)");
  await db.exec("INSERT INTO support_package_versions(package_id,revision,name,description,amount,email_minutes,remote_minutes,created_by) VALUES(1,1,'Paquete inicial','Precio inicial',250,60,30,2),(1,2,'Paquete actualizado','Precio nuevo',300,90,45,2)");checks+=2;
  await reject("UPDATE support_package_versions SET currency='USD' WHERE id=1",'23514','Package tariffs are MXN');
  await db.exec("UPDATE support_quotes SET package_version_id=1 WHERE id=1;UPDATE support_packages SET current_revision=2 WHERE id=1");checks++;
  check((await db.query("SELECT q.agreed_amount::text AS amount,v.amount::text AS package_price,v.revision FROM support_quotes q JOIN support_package_versions v ON v.id=q.package_version_id WHERE q.id=1")).rows,[{amount:'1500.00',package_price:'250.00',revision:1}],'Quote contractual terms and selected package version survive later tariff revisions');
  await db.exec("INSERT INTO policy_installments(policy_id,request_id,due_on,amount,description,created_by) VALUES(1,gen_random_uuid(),'2027-01-15',100,'Primera parcialidad',2)");checks++;
  await reject("UPDATE policy_installments SET amount=0 WHERE id=1",'23514','Installment must have positive exact amount');
  await db.exec("INSERT INTO installment_allocations(installment_id,payment_id,amount,created_by) VALUES(1,1,25.50,2)");checks++;
  await reject("INSERT INTO installment_allocations(installment_id,payment_id,amount,created_by) VALUES(1,1,10,2)",'23505','No duplicate active payment allocation per installment');
  // Policy ownership/allocation totals are validated by services, not inferred from this low-level fixture.
  await db.exec("INSERT INTO installment_notices(installment_id,kind,due_on) VALUES(1,'UPCOMING','2027-01-15')");checks++;
  await reject("INSERT INTO installment_notices(installment_id,kind,due_on) VALUES(1,'UPCOMING','2027-01-15')",'23505','Upcoming installment notice deduplicates');
  await db.exec("INSERT INTO bank_imports(account_label,account_key,content_hash,filename,row_count,new_count,created_by) VALUES('Cuenta prueba','cuenta prueba',repeat('a',64),'banco.csv',2,2,2)");
  await db.exec("INSERT INTO bank_movements(first_import_id,account_label,account_key,movement_hash,booked_on,reference,reference_key,amount,currency) VALUES(1,'Cuenta prueba','cuenta prueba',repeat('b',64),'2026-10-13','Referencia uno','referencia uno',1234.56,'MXN'),(1,'Cuenta prueba','cuenta prueba',repeat('c',64),'2026-10-13','Referencia dos','referencia dos',1234.56,'MXN')");checks+=2;
  await reject("INSERT INTO bank_imports(account_label,account_key,content_hash,filename,row_count,new_count,created_by) VALUES('Cuenta prueba','cuenta prueba',repeat('a',64),'duplicado.csv',2,0,2)",'23505','Exact bank import duplicate hash rejected');
  await db.exec("UPDATE bank_movements SET matched_payment_id=1,matched_by=2,matched_at=NOW() WHERE id=1");checks++;
  await reject("UPDATE bank_movements SET matched_payment_id=1,matched_by=2,matched_at=NOW() WHERE id=2",'23505','One existing payment can match only one bank movement');
  check(await allRows('institutions'),budgetsBefore,'Coordination/catalog/installments/reconciliation SQL never modifies support balances');
  check(await allRows('payments'),paymentsBefore,'New non-crediting modules leave every original payment unchanged');
  check(await allRows('case_entries'),entriesBefore,'Incident linking/resolution does not create or alter case attention');

  const opening=(await db.query("SELECT kind,COUNT(*)::int AS n FROM statement_versions WHERE opening GROUP BY kind ORDER BY kind")).rows;
  check(opening,[{kind:'institutions',n:2},{kind:'payments',n:paymentsBefore.length},{kind:'support_policies',n:2}],'Statement origin uses honest snapshots of existing records');
  check((await db.query("SELECT COUNT(*)::int AS n FROM statement_versions WHERE data ? 'notes' OR data ? 'password_hash' OR data ? 'contact_name' OR data ? 'created_by' OR data ? 'void_reason'")).rows[0].n,0,'Statement public projection excludes internal notes/authentication/author details');
  await db.exec("UPDATE institutions SET notes='Sólo privada, no nueva versión' WHERE id=1");checks++;
  check((await db.query("SELECT COUNT(*)::int AS n FROM statement_versions WHERE kind='institutions' AND entity_id=1")).rows[0].n,1,'Private-only edits do not create statement history');
  await db.exec("UPDATE institutions SET email_allocated_minutes=email_allocated_minutes+60 WHERE id=1");checks++;
  const history=(await db.query("SELECT opening,(data->>'emailAllocated')::int AS allocated FROM statement_versions WHERE kind='institutions' AND entity_id=1 ORDER BY id")).rows;
  check(history,[{opening:true,allocated:1500},{opening:false,allocated:1560}],'Future budget movement records new version without rewriting opening');
  await reject("UPDATE statement_versions SET opening=FALSE WHERE id=1",'P0001','Statement versions are immutable');
  await reject("DELETE FROM statement_history_origin WHERE id=1",'P0001','Historical start cannot be rewritten');
  await db.exec(`INSERT INTO institution_statements(institution_id,request_id,cutoff,created_by,snapshot) VALUES(1,gen_random_uuid(),CURRENT_DATE,2,'{"origin":"opening","institutionId":1}')`);checks++;
  await reject(`UPDATE institution_statements SET snapshot='{}' WHERE id=1`,'P0001','Issued statement snapshot immutable');
  check((await db.query('SELECT enabled,destination_confirmed FROM backup_settings')).rows,[{enabled:false,destination_confirmed:false}],'Final automatic backup default OFF remains intact');
  console.log(`SUCCESS: exact V1–V24 SQL; ${v16Tables.length} v1.6 tables preserved, backup defaults OFF, TECH scope, saved filters, deduplication, publication/package snapshots, reconciliation uniqueness and temporal statement protections; ${checks} assertions/statements verified. No HTTP authorization, native PostgreSQL, Flyway or concurrent-connection claim.`);
} finally { await db.close(); }
