// Integration of the Angular v1.6 knowledge screens using a simulated API.
// No real email, credentials or database are used; backend tests enforce permissions separately.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {dirname,extname,resolve,sep} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const project=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const build=resolve(project,'frontend/dist/helpdesk/browser');
await readFile(resolve(build,'index.html'));
const moduleRoot=process.env.E2E_PLAYWRIGHT_PATH;
const {chromium}=await import(moduleRoot?pathToFileURL(resolve(moduleRoot,'index.mjs')).href:'playwright');
const {expect:baseExpect}=await import(moduleRoot?pathToFileURL(resolve(moduleRoot,'test.mjs')).href:'playwright/test');
const expect=baseExpect.configure({timeout:10000});
const server=createServer(async(req,res)=>{try{const path=new URL(req.url,'http://localhost').pathname;const file=resolve(build,'.'+(path==='/'?'/index.html':decodeURIComponent(path)));if(!file.startsWith(build+sep))throw new Error('Invalid path');res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'application/javascript','.css':'text/css'})[extname(file)]||'application/octet-stream'});res.end(await readFile(file));}catch{res.writeHead(404);res.end();}});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const baseURL='http://127.0.0.1:'+server.address().port;
const launch={headless:true};if(process.env.E2E_CHROMIUM_PATH)launch.executablePath=process.env.E2E_CHROMIUM_PATH;if(process.env.E2E_CHROMIUM_ARGS)launch.args=JSON.parse(process.env.E2E_CHROMIUM_ARGS);
const failures=[];let browser,passed=0;
const now='2026-10-13T18:00:00Z';
const user={id:1,username:'admin',displayName:'Administrativo de pruebas',role:'ADMIN',email:'admin@example.test',institutionId:null,active:true};
const institution={id:1,name:'Biblioteca de pruebas',active:true,emailAllocatedMinutes:120,emailUsedMinutes:0,emailRemainingMinutes:120,remoteAllocatedMinutes:60,remoteUsedMinutes:0,remoteRemainingMinutes:60};
const supportCase={id:10,institutionId:1,institutionName:institution.name,title:'Error de catalogación',description:'Consulta de prueba',channel:'EMAIL',status:'OPEN',createdBy:'cliente',createdAt:now,updatedAt:now,totalMinutes:0,assignedAdminId:1,assignedAdminName:user.displayName};
const article=(overrides={})=>({id:20,title:'Resolver error de catálogo',category:'CATALOGING',siabucVersion:'SIABUC 9',summary:'Pasos para revisar el catálogo',body:'1. Consultar el diagnóstico.\n2. Verificar los datos.',status:'PUBLISHED',revision:2,createdAt:now,updatedAt:now,publishedAt:now,...overrides});
async function scenario(name,action){await action();passed++;console.log('PASS '+name);}
async function screenshot(page,name){if(process.env.E2E_REPORT_DIR){await mkdir(process.env.E2E_REPORT_DIR,{recursive:true});await page.screenshot({path:resolve(process.env.E2E_REPORT_DIR,name+'.png'),fullPage:true});}}
async function open({role='ADMIN',articles=[article()],linked=[],width=1440,conflict=false}={}){
 const context=await browser.newContext({viewport:{width,height:1000}});const page=await context.newPage();page.on('pageerror',error=>failures.push(error.message));
 const state={articles:structuredClone(articles),links:linked.slice(),history:[]};const gets=[],writes=[];
 const summarize=article=>{const {body,createdAt,...summary}=article;return summary;};
 const links=()=>state.links.map(id=>state.articles.find(article=>article.id===id)).filter(article=>article&&(role==='ADMIN'||article.status==='PUBLISHED')).map(article=>({caseId:10,article:summarize(article),linkedAt:now}));
 const snapshot=(article,action,changeNote='')=>state.history.unshift({...article,id:state.history.length+1,articleId:article.id,actor:'admin',action,changeNote});
 state.articles.forEach(article=>snapshot(article,'CREATE'));
 await context.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname,method=req.method(),reply=(body,status=200)=>route.fulfill({status,json:body});
  if(method==='GET')gets.push(path+url.search);else{
   const body=req.postDataJSON();writes.push({path,method,body});
   if(path==='/api/knowledge'){const created=article({...body,id:21,status:'DRAFT',revision:1,publishedAt:null});state.articles.push(created);snapshot(created,'CREATE',body.changeNote);return reply(created,201);}
   if(path.match(/^\/api\/knowledge\/\d+$/)){if(conflict)return reply({message:'Otro administrativo modificó el artículo. Recargue la versión actual antes de guardar.'},409);const current=state.articles.find(a=>a.id===Number(path.split('/').at(-1)));Object.assign(current,body,{revision:current.revision+1});snapshot(current,'EDIT',body.changeNote);return reply(current);}
   if(path.match(/^\/api\/knowledge\/\d+\/status$/)){const current=state.articles.find(a=>a.id===Number(path.split('/')[3]));Object.assign(current,{status:body.status,revision:current.revision+1});snapshot(current,body.status==='PUBLISHED'?'PUBLISH':body.status==='ARCHIVED'?'ARCHIVE':'UNPUBLISH',body.changeNote);return reply(current);}
   if(path==='/api/cases/10/knowledge-links'){if(!state.links.includes(body.articleId))state.links.push(body.articleId);return reply(links());}
   if(path.match(/^\/api\/cases\/10\/knowledge-links\/\d+$/)){state.links=state.links.filter(id=>id!==Number(path.split('/').at(-1)));return reply(links());}
   failures.push('Unexpected mutation '+method+' '+path);return reply({message:'Unexpected mutation'},400);
  }
  if(path==='/api/auth/csrf')return reply({token:'mock'});if(path==='/api/auth/me')return reply({...user,role,institutionId:role==='USER'?1:null});
  if(path==='/api/inbox')return reply({items:[],unreadCount:0,pendingReplies:[]});if(path==='/api/institutions')return reply([institution]);if(path==='/api/users')return reply([user]);
  if(path==='/api/cases')return reply([supportCase]);if(path==='/api/cases/10')return reply({...supportCase,entries:[]});if(path==='/api/cases/10/timer')return reply(null);if(path==='/api/case-profiles')return reply([{caseId:10,category:'CATALOGING',priority:'NORMAL'}]);
  if(path==='/api/payments'||path==='/api/policies')return reply([]);
  if(path==='/api/backups/status')return reply({settings:{enabled:false},worker:{online:false},runs:[],lastFailure:null});
  if(path==='/api/knowledge'){
   const q=(url.searchParams.get('q')||'').toLowerCase(),category=url.searchParams.get('category'),version=url.searchParams.get('siabucVersion')||'',status=url.searchParams.get('status'),page=Number(url.searchParams.get('page')||1),pageSize=Number(url.searchParams.get('pageSize')||20);
   const items=state.articles.filter(a=>(role!=='USER'||a.status==='PUBLISHED')&&(!status||a.status===status)&&(!category||a.category===category)&&a.siabucVersion.includes(version)&&[a.title,a.summary,a.body].some(t=>t.toLowerCase().includes(q)));
   return reply({items:items.slice((page-1)*pageSize,page*pageSize).map(summarize),total:items.length,page,pageSize});
  }
  if(path.match(/^\/api\/knowledge\/\d+\/history$/))return reply(state.history.filter(h=>h.articleId===Number(path.split('/')[3])));
  if(path.match(/^\/api\/knowledge\/\d+$/)){const a=state.articles.find(a=>a.id===Number(path.split('/').at(-1)));return a?reply(a):reply({message:'No encontrado'},404);}
  if(path==='/api/cases/10/knowledge-links')return reply(links());
  failures.push('Unexpected GET '+path);return reply({message:'Unexpected GET'},404);
 });
 await page.goto(baseURL);await expect(page.getByRole('button',{name:'Notificaciones y respuestas pendientes',exact:true})).toBeVisible();return {page,context,state,gets,writes};
}
async function navigate(page,name){const toggle=page.getByRole('button',{name:'Abrir navegación',exact:true});if(await toggle.isVisible()&&await toggle.getAttribute('aria-expanded')==='false')await toggle.click();await page.getByRole('navigation',{name:'Navegación principal'}).getByRole('button',{name,exact:true}).click();}
async function knowledge(page){await navigate(page,'Base de conocimientos');await expect(page.getByRole('heading',{name:'Base de conocimientos',exact:true})).toBeVisible();}
async function openArticle(page,title='Resolver error de catálogo'){await knowledge(page);await page.getByRole('button',{name:title,exact:true}).click();await expect(page.getByRole('button',{name:'← Volver al listado de artículos',exact:true})).toBeVisible();}
async function caseView(page){await navigate(page,'Casos de soporte');await page.getByRole('button',{name:'Ver caso 10',exact:true}).click();await expect(page.getByRole('heading',{name:'Soluciones vinculadas al caso',exact:true})).toBeVisible();}
try{
 browser=await chromium.launch(launch);
 await scenario('búsqueda transmite texto categoría y versión y conserva paginación',async()=>{
  const {page,context,gets}=await open({articles:Array.from({length:22},(_,index)=>article({id:20+index,title:'Resolver catálogo '+index}))});await knowledge(page);await expect(page.getByText('Página 1 de 2',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Siguiente',exact:true}).click();await expect(page.getByText('Página 2 de 2',{exact:true})).toBeVisible();await page.locator('[name=knowledgeQuery]').fill('Resolver');await page.locator('[name=knowledgeFilterCategory]').selectOption('CATALOGING');await page.locator('[name=knowledgeFilterVersion]').fill('9');await page.getByRole('button',{name:'Buscar',exact:true}).click();await expect(page.getByText('Página 1 de 2',{exact:true})).toBeVisible();assert(gets.some(url=>url.includes('q=Resolver')&&url.includes('category=CATALOGING')&&url.includes('siabucVersion=9')));await context.close();
 });
 await scenario('alta guarda borrador y publicar exige confirmación con motivo',async()=>{
  const {page,context,writes}=await open({articles:[]});await knowledge(page);await page.getByRole('button',{name:'Nuevo artículo',exact:true}).click();await page.locator('[name=knowledgeTitle]').fill('Instalación inicial');await page.locator('[name=knowledgeCategory]').selectOption('INSTALLATION');await page.locator('[name=knowledgeVersion]').fill('SIABUC 9');await page.locator('[name=knowledgeSummary]').fill('Preparar un equipo');await page.locator('[name=knowledgeBody]').fill('1. Revisar requisitos.\n2. Abrir el instalador.');await page.locator('[name=knowledgeChangeNote]').fill('Procedimiento revisado');await page.getByRole('button',{name:'Guardar borrador',exact:true}).click();await expect(page.getByText('Borrador guardado. Todavía no está disponible para clientes.',{exact:true})).toBeVisible();assert.equal(writes.length,1);assert.equal(writes[0].path,'/api/knowledge');await page.getByRole('button',{name:'Publicar artículo',exact:true}).click();await expect(page.getByRole('button',{name:'Confirmar cambio editorial',exact:true})).toBeDisabled();await page.locator('[name=knowledgeStatusNote]').fill('Validado por soporte');await page.getByRole('button',{name:'Confirmar cambio editorial',exact:true}).click();await expect(page.getByText('Artículo publicado para consulta de las instituciones.',{exact:true})).toBeVisible();assert.deepEqual(writes[1].body,{status:'PUBLISHED',expectedRevision:1,changeNote:'Validado por soporte'});await screenshot(page,'01-editorial');await context.close();
 });
 await scenario('publicado requiere volver a borrador antes de editar con revisión actual',async()=>{
  const {page,context,writes}=await open();await openArticle(page);await expect(page.getByRole('button',{name:'Editar borrador',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Pasar a borrador',exact:true}).click();await page.locator('[name=knowledgeStatusNote]').fill('Corregir instrucciones');await page.getByRole('button',{name:'Confirmar cambio editorial',exact:true}).click();await page.getByRole('button',{name:'Editar borrador',exact:true}).click();await page.locator('[name=knowledgeBody]').fill('Solución corregida');await page.getByRole('button',{name:'Guardar borrador',exact:true}).click();await expect(page.getByText('Solución corregida',{exact:true}).first()).toBeVisible();assert.equal(writes[1].method,'PUT');assert.equal(writes[1].body.expectedRevision,3);await context.close();
 });
 await scenario('conflicto de revisión conserva texto local y no informa éxito',async()=>{
  const {page,context,writes}=await open({articles:[article({status:'DRAFT'})],conflict:true});await openArticle(page);await page.getByRole('button',{name:'Editar borrador',exact:true}).click();await page.locator('[name=knowledgeBody]').fill('Texto local aún sin guardar');await page.getByRole('button',{name:'Guardar borrador',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Otro administrativo modificó');await expect(page.locator('[name=knowledgeBody]')).toHaveValue('Texto local aún sin guardar');await expect(page.getByText('Borrador guardado. Todavía no está disponible para clientes.',{exact:true})).toHaveCount(0);assert.equal(writes.length,1);await context.close();
 });
 await scenario('cliente consulta texto literal sin HTML activo ni controles editoriales',async()=>{
  const body='<script>window.injectionExecuted=true</script>\n<img src=x onerror="window.injectionExecuted=true">';const {page,context,gets}=await open({role:'USER',articles:[article({body})]});await openArticle(page);await expect(page.locator('.knowledge-text')).toHaveText(body);assert.equal(await page.locator('.knowledge-text script,.knowledge-text img').count(),0);assert.equal(await page.evaluate(()=>window.injectionExecuted),undefined);await expect(page.getByRole('button',{name:'Nuevo artículo',exact:true})).toHaveCount(0);await expect(page.getByRole('heading',{name:'Historial editorial',exact:true})).toHaveCount(0);assert(!gets.some(path=>path.endsWith('/history')));await page.getByRole('button',{name:'← Volver al listado de artículos',exact:true}).click();await expect(page.getByRole('button',{name:'Resolver error de catálogo',exact:true})).toBeVisible();await screenshot(page,'02-consulta-cliente');await context.close();
 });
 await scenario('archivar es explícito y restaura como borrador antes de republicar',async()=>{
  const {page,context,writes}=await open();await openArticle(page);await page.getByRole('button',{name:'Archivar artículo',exact:true}).click();await page.getByRole('button',{name:'Conservar estado',exact:true}).click();assert.equal(writes.length,0);await page.getByRole('button',{name:'Archivar artículo',exact:true}).click();await page.locator('[name=knowledgeStatusNote]').fill('Procedimiento obsoleto');await page.getByRole('button',{name:'Confirmar cambio editorial',exact:true}).click();await expect(page.getByRole('button',{name:'Recuperar como borrador',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Publicar artículo',exact:true})).toHaveCount(0);assert.equal(writes[0].body.status,'ARCHIVED');await context.close();
 });
 await scenario('administrativo vincula consulta y retira solución sin consumo ni cierre',async()=>{
  const {page,context,writes,state}=await open();await caseView(page);await page.getByRole('button',{name:'Buscar soluciones',exact:true}).click();await page.getByRole('button',{name:'Vincular solución',exact:true}).click();await expect(page.getByText('Solución vinculada. El saldo de soporte no cambió.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Ya vinculado',exact:true})).toBeDisabled();await page.getByRole('button',{name:'Resolver error de catálogo',exact:true}).click();await expect(page.locator('.knowledge-text')).toContainText('Consultar el diagnóstico');await page.getByRole('button',{name:'← Volver al listado de soluciones',exact:true}).click();await screenshot(page,'03-solucion-caso');await page.getByRole('button',{name:'Retirar vínculo',exact:true}).click();await expect(page.getByText('Vínculo retirado del caso. El artículo y la auditoría se conservan.',{exact:true})).toBeVisible();assert.deepEqual(writes.map(w=>w.method),['POST','DELETE']);assert(writes.every(w=>w.path.includes('/knowledge-links')));assert.equal(state.links.length,0);assert.equal(institution.emailRemainingMinutes,120);await context.close();
 });
 await scenario('cliente abre soluciones del caso sin controles para vincular o retirar',async()=>{
  const {page,context,writes}=await open({role:'USER',linked:[20]});await caseView(page);await expect(page.getByRole('button',{name:'Buscar soluciones',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Retirar vínculo',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Resolver error de catálogo',exact:true}).click();await expect(page.locator('.knowledge-text')).toBeVisible();assert.equal(writes.length,0);await context.close();
 });
 await scenario('cobranza no tiene navegación hacia artículos ni soporte',async()=>{
  const {page,context,gets}=await open({role:'BILLING'});const nav=page.getByRole('navigation',{name:'Navegación principal'});await expect(nav.getByRole('button',{name:'Base de conocimientos',exact:true})).toHaveCount(0);assert(!gets.some(path=>path.startsWith('/api/knowledge')));await context.close();
 });
 await scenario('artículo y soluciones legibles a 390 píxeles sin desbordamiento',async()=>{
  const {page,context}=await open({role:'USER',width:390,linked:[20],articles:[article({body:'Paso de diagnóstico '+ 'x'.repeat(180)})]});await openArticle(page);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await screenshot(page,'04-conocimiento-movil');await caseView(page);await page.getByRole('button',{name:'Resolver error de catálogo',exact:true}).click();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await context.close();
 });
 assert.deepEqual(failures,[]);console.log('RESULT '+passed+' passed, 0 failed (simulated API; no SMTP or database)');
}catch(error){console.error(error);if(failures.length)console.error(failures);process.exitCode=1;}
finally{await browser?.close();await new Promise(done=>server.close(done));}
