// Angular browser regression with a simulated API; Java ITs verify persistence and permissions.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {dirname,extname,resolve,sep} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const build=resolve(root,'frontend/dist/helpdesk/browser');
const moduleRoot=process.env.E2E_PLAYWRIGHT_PATH;
const {chromium}=await import(moduleRoot?pathToFileURL(resolve(moduleRoot,'index.mjs')).href:'playwright');
const {expect}=await import(moduleRoot?pathToFileURL(resolve(moduleRoot,'test.mjs')).href:'playwright/test');
const server=createServer(async(req,res)=>{
  try {
    const pathname=new URL(req.url,'http://localhost').pathname;
    const file=resolve(build,'.'+(pathname==='/'?'/index.html':decodeURIComponent(pathname)));
    if(!file.startsWith(build+sep))throw Error('Invalid path');
    res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'application/javascript','.css':'text/css'})[extname(file)]??'application/octet-stream'});
    res.end(await readFile(file));
  }catch{res.writeHead(404);res.end();}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const launch={headless:true};
if(process.env.E2E_CHROMIUM_PATH)launch.executablePath=process.env.E2E_CHROMIUM_PATH;
if(process.env.E2E_CHROMIUM_ARGS)launch.args=JSON.parse(process.env.E2E_CHROMIUM_ARGS);
let browser,passed=0;
const errors=[];
const states=['OPEN','IN_PROGRESS','WAITING_CLIENT','RESOLVED','CLOSED'];
const labels=['Recibido','En atención','Requiere respuesta del cliente','Resuelto','Cerrado'];
const now='2026-10-15T18:00:00Z';
const institution={id:1,name:'Biblioteca prueba',active:true,emailRemainingMinutes:60,remoteRemainingMinutes:60,emailAllocatedMinutes:60,remoteAllocatedMinutes:60,emailUsedMinutes:0,remoteUsedMinutes:0};

async function open(role='ADMIN') {
  const context=await browser.newContext({viewport:{width:1366,height:900}});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  const items=states.map((status,i)=>({id:i+1,institutionId:1,institutionName:institution.name,title:`Solicitud ${i+1}`,description:'Diagnóstico de soporte',channel:'EMAIL',status,createdBy:'cliente',createdAt:now,updatedAt:now,totalMinutes:0,assignedAdminId:1,assignedAdminName:'Soporte'}));
  const entries=new Map(items.map(c=>[c.id,[]]));const writes=[];let saved=[];
  await context.route('**/api/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname,method=req.method();
    const reply=(body,status=200)=>route.fulfill({status,json:body});
    const match=path.match(/^\/api\/cases\/(\d+)(?:\/(.*))?$/);
    const item=match?items.find(c=>c.id===Number(match[1])):null;
    if(method==='POST') {
      const body=req.postDataJSON();writes.push({path,body});
      if(item&&match[2]==='status') {
        item.status=body.status;
        entries.get(item.id).push({id:writes.length,caseId:item.id,description:body.reason,minutes:0,channel:null,internal:false,createdBy:'soporte',createdAt:now});
        return reply({...item,entries:entries.get(item.id)});
      }
      if(item&&match[2]==='entries') {
        if(role==='USER'&&item.status==='WAITING_CLIENT')item.status='IN_PROGRESS';
        entries.get(item.id).push({...body,id:writes.length,caseId:item.id,createdBy:'cliente',createdAt:now});
        return reply({...item,entries:entries.get(item.id)});
      }
      if(path==='/api/saved-views') {const view={id:1,...body,revision:1};saved=[view];return reply(view);}
      errors.push('Unexpected write '+path);return reply({},400);
    }
    if(path==='/api/auth/csrf')return reply({token:'qa'});
    if(path==='/api/auth/me')return reply({id:1,username:'qa',displayName:'Soporte',role,institutionId:role==='USER'?1:null,active:true});
    if(path==='/api/institutions')return reply([institution]);
    if(path==='/api/cases')return reply(items);
    if(path==='/api/case-profiles')return reply(items.map(c=>({caseId:c.id,category:'OTHER',priority:'NORMAL'})));
    if(path==='/api/backups/status')return reply({settings:{enabled:false,dailyTime:'02:00',timeZone:'America/Mexico_City',retentionDays:30,keepLast:3,destinationConfirmed:false,updatedAt:now},worker:{online:false,writable:false,destinationLabel:'',lastError:'',lastSeenAt:null},runs:[],lastFailure:null});
    if(path==='/api/inbox')return reply({items:[],unreadCount:0,pendingReplies:[]});
    if(path==='/api/users')return reply([{id:1,displayName:'Soporte',role:'ADMIN',active:true}]);
    if(path==='/api/saved-views')return reply(saved);
    if(item&&!match[2])return reply({...item,entries:entries.get(item.id)});
    if(item&&match[2]==='timer')return reply(null);
    if(/^\/api\/satisfaction\/cases\//.test(path))return reply({caseId:5,caseStatus:'CLOSED',survey:null,canRespond:false});
    if(path==='/api/client-finance-access'||path==='/api/policies'||path==='/api/reply-templates'||/\/(incidents|knowledge-links|attachments)$/.test(path))return reply([]);
    errors.push('Unexpected GET '+path);return reply({},404);
  });
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.getByRole('navigation').getByRole('button',{name:'Casos de soporte',exact:true}).click();
  await expect(page.locator('.case-title-button')).toHaveCount(5);
  return {context,page,writes};
}
async function scenario(name,fn){browser=await chromium.launch(launch);try{await fn();passed++;console.log('PASS '+name);}finally{await browser.close();}}

try {
  await scenario('five labels, active group and saved waiting filter',async()=>{
    const {page,context,writes}=await open();
    const filter=page.getByLabel('Estado',{exact:true});
    for(const label of labels)await expect(page.locator('td .badge').getByText(label,{exact:true})).toBeVisible();
    await filter.selectOption('active');await expect(page.locator('.case-title-button')).toHaveCount(3);
    await filter.selectOption('WAITING_CLIENT');await expect(page.locator('.case-title-button')).toHaveCount(1);
    await page.locator('[name=savedViewName]').fill('Esperando información');
    await page.getByRole('button',{name:'Guardar filtros como nueva vista',exact:true}).click();
    await expect(page.getByText('Vista personal guardada.',{exact:true})).toBeVisible();
    assert.equal(writes.at(-1).body.filters.status,'WAITING_CLIENT');
    await context.close();
  });
  await scenario('state selector submits reason and resolved/closed require reopening',async()=>{
    const {page,context,writes}=await open();await page.locator('.case-title-button').first().click();
    const reason='<img src=x onerror="window.workflowXss=true"> Motivo de soporte';
    async function change(state){
      await page.getByRole('button',{name:'Cambiar estado',exact:true}).click();
      await page.locator('[name=statusTarget]').selectOption(state);
      await page.locator('[name=statusReason]').fill(reason);
      await page.getByRole('button',{name:'Confirmar cambio',exact:true}).click();
      await expect(page.locator('.case-heading .badge')).toHaveText(labels[states.indexOf(state)]);
      assert.equal(writes.at(-1).body.status,state);assert.equal(writes.at(-1).body.reason,reason);
    }
    for(const state of ['IN_PROGRESS','WAITING_CLIENT','RESOLVED'])await change(state);
    await expect(page.locator('.entry-form')).toHaveCount(0);
    await expect(page.getByRole('button',{name:'Iniciar cronómetro',exact:true})).toBeDisabled();
    await page.getByRole('button',{name:'Cambiar estado',exact:true}).click();
    assert.deepEqual(await page.locator('[name=statusTarget] option').evaluateAll(rows=>rows.map(r=>r.value)),['IN_PROGRESS','CLOSED']);
    await page.getByRole('button',{name:'Cancelar',exact:true}).click();
    await change('CLOSED');await page.getByRole('button',{name:'Cambiar estado',exact:true}).click();
    assert.deepEqual(await page.locator('[name=statusTarget] option').evaluateAll(rows=>rows.map(r=>r.value)),['IN_PROGRESS']);
    await page.getByRole('button',{name:'Cancelar',exact:true}).click();await change('IN_PROGRESS');
    await expect(page.locator('.entry-form')).toBeVisible();
    assert.equal(await page.evaluate(()=>window.workflowXss),undefined);
    await expect(page.locator('main img[src=x]')).toHaveCount(0);
    await context.close();
  });
  await scenario('client reply in waiting case returns to attention without state control',async()=>{
    const {page,context,writes}=await open('USER');
    await page.getByLabel('Estado',{exact:true}).selectOption('WAITING_CLIENT');
    await page.locator('.case-title-button').click();
    await expect(page.getByRole('button',{name:'Cambiar estado',exact:true})).toHaveCount(0);
    await page.locator('[name=entryDescription]').fill('Aquí está la información solicitada');
    await page.getByRole('button',{name:'Agregar al historial',exact:true}).click();
    await expect(page.locator('.case-heading .badge')).toHaveText('En atención');
    assert.equal(writes.at(-1).body.minutes,0);
    await context.close();
  });
  await scenario('waiting label and selector remain usable at 390 pixels',async()=>{
    const {page,context}=await open();await page.getByLabel('Estado',{exact:true}).selectOption('WAITING_CLIENT');
    await page.locator('.case-title-button').click();await page.setViewportSize({width:390,height:844});
    await page.getByRole('button',{name:'Cambiar estado',exact:true}).click();
    await expect(page.locator('[name=statusTarget]')).toBeVisible();
    const width=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert.ok(width.content<=width.viewport+1,JSON.stringify(width));await context.close();
  });
  assert.deepEqual(errors,[]);
  console.log(`${passed} browser scenarios passed (simulated API)`);
}finally{await browser?.close();await new Promise(done=>server.close(done));}
