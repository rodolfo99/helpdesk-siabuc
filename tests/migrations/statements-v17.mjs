/** Isolated exact-SQL verification of V24. No HTTP server, Docker or external databases.
 * SIABUC_PGLITE_MODULE=/path/to/@electric-sql/pglite/dist/index.js node tests/migrations/statements-v17.mjs
 */
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {dirname,resolve} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const folder=resolve(root,'backend/src/main/resources/db/migration');
const {PGlite}=await import(process.env.SIABUC_PGLITE_MODULE?pathToFileURL(resolve(process.env.SIABUC_PGLITE_MODULE)).href:'@electric-sql/pglite');
const db=new PGlite();let checks=0;
const equal=(a,b,label)=>{assert.deepEqual(a,b,label);checks++;};
const rows=async sql=>(await db.query(sql)).rows;
try{
 await db.waitReady;
 const names=(await readdir(folder)).filter(n=>/^V\d+__.*\.sql$/.test(n)&&Number(n.match(/^V(\d+)/)[1])<=16).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]));
 for(const name of names)await db.exec(await readFile(resolve(folder,name),'utf8'));
 await db.exec(`INSERT INTO institutions(id,name,notes,email_allocated_minutes,email_used_minutes,remote_allocated_minutes,remote_used_minutes) VALUES(901,'Institución anterior','SECRETO',180,40,60,20);
 INSERT INTO app_users(id,username,display_name,password_hash,role) VALUES(901,'staff_privado','Staff privado','hash-no-publicar','ADMIN');
 INSERT INTO support_policies(id,institution_id,folio,description,valid_from,valid_until,agreed_amount,email_minutes,remote_minutes,created_by) VALUES(901,901,'POL-ANTERIOR','SECRETO','2020-01-01','2020-12-31',100,60,30,901);
 INSERT INTO payments(id,request_id,institution_id,reference,amount,currency,paid_on,method,email_minutes,remote_minutes,notes,created_by,policy_id) VALUES(901,'00000000-0000-4000-8000-000000000901',901,'ABONO-ANTERIOR',40,'MXN','2020-01-01','CARD',10,5,'SECRETO',901,901),(902,'00000000-0000-4000-8000-000000000902',901,'USD-ANTERIOR',250.75,'USD','2020-01-02','CARD',30,10,'SECRETO',901,NULL);`);
 const before=await rows('SELECT to_jsonb(i) row FROM institutions i WHERE id=901');
 await db.exec(await readFile(resolve(folder,'V24__institution_statements.sql'),'utf8'));
 equal(await rows('SELECT to_jsonb(i) row FROM institutions i WHERE id=901'),before,'original institution preserved');
 let versions=await rows('SELECT * FROM statement_versions ORDER BY id');equal(versions.length,4,'all opening entities captured');equal(versions.every(v=>v.opening),true,'opening explicit');
 equal(new Set(versions.map(v=>v.recorded_at.toISOString())).size,1,'same migration origin');equal(JSON.stringify(versions).includes('SECRETO'),false,'private descriptions absent');equal(JSON.stringify(versions).includes('staff_privado'),false,'staff identities absent');equal(JSON.stringify(versions).includes('hash-no-publicar'),false,'credentials absent');
 equal(versions.find(v=>v.kind==='institutions').data.emailUsed,40,'opening consumption preserved');equal(versions.find(v=>v.entity_id===902).data.currency,'USD','legacy currency preserved');equal(versions.find(v=>v.entity_id===902).data.amount,250.75,'exact legacy amount preserved');
 await db.exec("UPDATE institutions SET notes='Otro secreto' WHERE id=901");equal((await rows('SELECT * FROM statement_versions')).length,4,'private-only change has no financial version');
 const cutoff=(await rows('SELECT clock_timestamp()::text stamp'))[0].stamp;
 await db.query('SELECT pg_sleep(0.03)');
 await db.exec("UPDATE institutions SET email_allocated_minutes=200,email_used_minutes=45 WHERE id=901; UPDATE support_policies SET agreed_amount=120 WHERE id=901; UPDATE payments SET voided=TRUE,void_reason='SECRETO',voided_by=901,voided_at=NOW() WHERE id=901");
 versions=await rows('SELECT * FROM statement_versions ORDER BY id');equal(versions.length,7,'later public changes captured');equal(versions.filter(v=>v.kind==='institutions').map(v=>v.data.emailAllocated),[180,200],'old counters not overwritten');equal(versions.filter(v=>v.kind==='support_policies').map(v=>v.data.amount),[100,120],'old contract not overwritten');equal(versions.filter(v=>v.kind==='payments'&&v.entity_id===901).map(v=>v.data.voided),[false,true],'payment void is a later version');equal(JSON.stringify(versions).includes('SECRETO'),false,'void reason private');
 const old=(await db.query("SELECT DISTINCT ON(kind,entity_id) kind,entity_id,data FROM statement_versions WHERE recorded_at<=$1 ORDER BY kind,entity_id,recorded_at DESC,id DESC",[cutoff])).rows;
 equal(old.find(v=>v.kind==='support_policies').data.amount,100,'older cutoff contract stable');equal(old.find(v=>v.kind==='payments'&&v.entity_id===901).data.voided,false,'older cutoff payment active');
 await db.exec('BEGIN; UPDATE institutions SET email_allocated_minutes=220 WHERE id=901; ROLLBACK');equal((await rows('SELECT * FROM statement_versions')).length,7,'transaction rollback includes its history');
 for(const sql of ['UPDATE statement_versions SET data=\'{}\'::jsonb','DELETE FROM statement_versions','UPDATE statement_history_origin SET opened_at=NOW()']){await assert.rejects(db.exec(sql),/inmutables/);checks++;}
 await db.exec(`INSERT INTO institution_statements(institution_id,request_id,cutoff,created_by,snapshot) VALUES(901,'00000000-0000-4000-8000-000000000903',CURRENT_DATE,901,'{"institutionId":901,"public":true}');`);
 await assert.rejects(db.exec("UPDATE institution_statements SET snapshot='{}'::jsonb"),/inmutables/);checks++;await assert.rejects(db.exec('DELETE FROM institution_statements'),/inmutables/);checks++;
 equal((await rows('SELECT COUNT(*)::int count FROM institution_statements'))[0].count,1,'issued snapshot retained');
 await db.exec("BEGIN; UPDATE institutions SET email_allocated_minutes=210 WHERE id=901; SELECT pg_sleep(0.02); UPDATE payments SET reference='USD-CORREGIDO' WHERE id=902; COMMIT");
 const stamps=await rows("SELECT recorded_at FROM statement_versions ORDER BY id DESC LIMIT 2");equal(stamps[0].recorded_at.toISOString(),stamps[1].recorded_at.toISOString(),'one transaction cannot split money and minute versions across a cutoff');
 console.log(`PASS ${checks} statement SQL checks (PGlite; exact V1–V16 and V24)`);
}finally{await db.close();}
