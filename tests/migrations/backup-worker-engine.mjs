// Isolated PostgreSQL SQL bridge for verify_backup_worker_sql.py; never connects to installation.
import {dirname,resolve} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const {PGlite}=await import(process.env.SIABUC_PGLITE_MODULE ? pathToFileURL(resolve(process.env.SIABUC_PGLITE_MODULE)).href : '@electric-sql/pglite');
import {readFile,readdir} from 'node:fs/promises';
import {createInterface} from 'node:readline';
const db=new PGlite(); await db.waitReady;
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../backend/src/main/resources/db/migration')+'/';
for(const file of (await readdir(root)).filter(f=>f.endsWith('.sql')).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]))) await db.exec(await readFile(root+file,'utf8'));
console.log('READY');
for await(const line of createInterface({input:process.stdin})) {
 try{const results=await db.exec(JSON.parse(line));const out=results.flatMap(r=>r.rows.map(row=>Object.values(row).map(v=>typeof v==='object'?JSON.stringify(v):String(v)).join('|'))).join('\n');console.log(JSON.stringify({out}));}
 catch(error){console.log(JSON.stringify({error:error.message}));}
}
await db.close();
