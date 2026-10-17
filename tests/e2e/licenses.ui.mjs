// Angular UI against a simulated API. LicenseIntegrationIT covers real PostgreSQL accounting.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {dirname,extname,resolve,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {expect} from 'playwright/test';

const build=resolve(dirname(fileURLToPath(import.meta.url)),'../../frontend/dist/helpdesk/browser');
const server=createServer(async(req,res)=>{
  try {
    const name=new URL(req.url,'http://localhost').pathname;
    const file=resolve(build,'.'+(name==='/'?'/index.html':decodeURIComponent(name)));
    if(!file.startsWith(build+sep))throw Error('Invalid path');
    res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'application/javascript','.css':'text/css'})[extname(file)]??'application/octet-stream'});
    res.end(await readFile(file));
  }catch{res.writeHead(404);res.end();}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const browser=await chromium.launch({headless:true,...(process.env.E2E_CHROMIUM_PATH?{executablePath:process.env.E2E_CHROMIUM_PATH}:{}),
  ...(process.env.E2E_CHROMIUM_ARGS?{args:JSON.parse(process.env.E2E_CHROMIUM_ARGS)}:{})});
const errors=[],writes=[];
const now='2026-10-16T18:00:00Z';
let defaults={emailMinutes:0,remoteMinutes:0,configured:false,revision:0},conflict=false;
const institution={id:1,name:'Biblioteca de prueba',active:true,emailRemainingMinutes:0,remoteRemainingMinutes:0,emailAllocatedMinutes:0,remoteAllocatedMinutes:0,emailUsedMinutes:0,remoteUsedMinutes:0};
const licenses=[],history=[],cases=[];
let passed=0;
async function check(name,fn){await fn();passed++;console.log('PASS '+name);}
async function setup(role='ADMIN',viewport={width:1366,height:1000}){
  const context=await browser.newContext({viewport});const page=await context.newPage();
  page.on('pageerror',error=>errors.push(error.message));
  await context.route('**/api/**',async route=>{
    const request=route.request(),path=new URL(request.url()).pathname,method=request.method();
    const reply=(body,status=200)=>route.fulfill({status,json:body});
    if(method==='PUT'||method==='POST'){
      const body=request.postDataJSON();writes.push({path,body});
      if(path==='/api/license-support-defaults'){
        if(conflict)return reply({message:'La configuración cambió. Vuelva a cargarla antes de guardar.'},409);
        defaults={...body,configured:true,revision:defaults.revision+1};return reply(defaults);
      }
      if(path==='/api/licenses'){
        const license={id:licenses.length+1,...body,institutionName:institution.name,initialEmailMinutes:defaults.emailMinutes,initialRemoteMinutes:defaults.remoteMinutes,
          emailAllocatedMinutes:defaults.emailMinutes,remoteAllocatedMinutes:defaults.remoteMinutes,emailUsedMinutes:0,remoteUsedMinutes:0,
          emailRemainingMinutes:defaults.emailMinutes,remoteRemainingMinutes:defaults.remoteMinutes};
        licenses.push(license);institution.emailRemainingMinutes+=defaults.emailMinutes;institution.remoteRemainingMinutes+=defaults.remoteMinutes;
        history.push({id:1,kind:'INITIAL',emailMinutes:defaults.emailMinutes,remoteMinutes:defaults.remoteMinutes,reason:'Horas incluidas',createdBy:'admin',createdAt:now});
        return reply(license,201);
      }
      if(path==='/api/licenses/1/hours'){
        licenses[0].emailAllocatedMinutes+=body.emailMinutes;licenses[0].emailRemainingMinutes+=body.emailMinutes;
        licenses[0].remoteAllocatedMinutes+=body.remoteMinutes;licenses[0].remoteRemainingMinutes+=body.remoteMinutes;
        history.push({id:2,kind:'TOPUP',...body,createdBy:'admin',createdAt:now});return reply(licenses[0]);
      }
      if(path==='/api/cases'){
        const value={id:1,...body,licenseNumber:licenses[0].number,institutionName:institution.name,status:'OPEN',entries:[],createdBy:'admin',createdAt:now,updatedAt:now,totalMinutes:0,assignedAdminId:1,assignedAdminName:'Soporte'};
        cases.push(value);return reply(value,201);
      }
      errors.push('Unexpected mutation '+path);return reply({},400);
    }
    if(path==='/api/auth/csrf')return reply({token:'qa'});
    if(path==='/api/auth/me')return reply({id:1,username:'qa',displayName:'Soporte',role,institutionId:role==='USER'?1:null,active:true});
    if(path==='/api/license-support-defaults')return reply(defaults);
    if(path==='/api/licenses')return reply(licenses);
    if(path==='/api/licenses/1/history')return reply(history);
    if(path==='/api/institutions')return reply([institution]);
    if(path==='/api/cases')return reply(cases);
    if(path==='/api/cases/1')return reply(cases[0]);
    if(path==='/api/cases/1/timer')return reply(null);
    if(path==='/api/users')return reply([{id:1,displayName:'Soporte',role:'ADMIN',active:true}]);
    if(path==='/api/support-settings')return reply({timezone:'America/Mexico_City',workDays:[1,2,3,4,5],startTime:'09:00',endTime:'17:00',holidays:[],goals:Object.fromEntries(['LOW','NORMAL','HIGH','URGENT'].map(p=>[p,{firstResponseMinutes:60,resolutionMinutes:120}]))});
    if(path==='/api/sla-alerts/settings')return reply({enabled:false,leadMinutes:30});
    if(path==='/api/inbox')return reply({items:[],unreadCount:0,pendingReplies:[]});
    if(path==='/api/backups/status')return reply({settings:{enabled:false},worker:{online:false},runs:[],lastFailure:null});
    if(['/api/client-finance-access','/api/balance-alert-settings','/api/case-profiles','/api/saved-views','/api/reply-templates'].includes(path)||/\/(incidents|knowledge-links|attachments)$/.test(path))return reply([]);
    errors.push('Unexpected GET '+path);return reply({},404);
  });
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await expect(page.getByRole('navigation')).toBeVisible();return {context,page};
}
try{
  const {context,page}=await setup();
  const navigate=name=>page.getByRole('navigation').getByRole('button',{name,exact:true}).click();
  await check('Defaults use separate integer hours/minutes and persist after navigation',async()=>{
    await navigate('Configuración de soporte');
    await page.getByLabel('Horas predeterminadas de correo',{exact:true}).fill('5');
    await page.getByLabel('Minutos adicionales de correo',{exact:true}).fill('30');
    await page.getByLabel('Horas predeterminadas de escritorio remoto',{exact:true}).fill('2');
    await page.getByLabel('Minutos adicionales de escritorio remoto',{exact:true}).fill('15');
    await page.getByRole('button',{name:'Guardar horas predeterminadas',exact:true}).click();
    await expect(page.getByRole('status')).toContainText('Horas predeterminadas guardadas');
    assert.deepEqual(writes.at(-1),{path:'/api/license-support-defaults',body:{emailMinutes:330,remoteMinutes:135,revision:0}});
    await navigate('Licencias');await navigate('Configuración de soporte');
    await expect(page.getByLabel('Horas predeterminadas de correo',{exact:true})).toHaveValue('5');
    await expect(page.getByLabel('Minutos adicionales de escritorio remoto',{exact:true})).toHaveValue('15');
    if(process.env.E2E_SCREENSHOT_DIR)await page.screenshot({path:resolve(process.env.E2E_SCREENSHOT_DIR,'siabuc-horas.png'),fullPage:true});
  });
  await check('Invalid minutes cannot be saved and stale settings show a recoverable conflict',async()=>{
    const save=page.getByRole('button',{name:'Guardar horas predeterminadas',exact:true});
    await page.getByLabel('Minutos adicionales de correo',{exact:true}).fill('60');await expect(save).toBeDisabled();
    await page.getByLabel('Minutos adicionales de correo',{exact:true}).fill('30');
    conflict=true;await save.click();await expect(page.getByRole('alert')).toContainText('La configuración cambió');
    conflict=false;await page.getByRole('button',{name:'Volver a cargar valores'}).click();
    await expect(page.getByLabel('Horas predeterminadas de correo',{exact:true})).toHaveValue('5');
  });
  await check('A new license displays and applies the saved defaults once',async()=>{
    await navigate('Licencias');await page.getByRole('button',{name:'Nueva licencia',exact:true}).click();
    await page.getByLabel('Institución titular').selectOption({label:institution.name});
    await page.getByLabel('Número de licencia',{exact:true}).fill('LIC-PRUEBA-1');
    await expect(page.getByText('Horas incluidas de correo:')).toContainText('5 h 30 min');
    await page.getByRole('button',{name:'Guardar licencia y acreditar horas'}).click();
    await expect(page.getByRole('status')).toContainText('Licencia registrada');
    assert.equal(licenses.length,1);assert.equal(licenses[0].emailRemainingMinutes,330);
    assert.equal(writes.find(w=>w.path==='/api/licenses').body.defaultsRevision,1);
  });
  await check('Additional hours preserve the initial grant and send an idempotency key',async()=>{
    await page.getByRole('button',{name:'Agregar horas',exact:true}).click();
    await page.getByLabel('Horas de correo adicionales',{exact:true}).fill('1');
    await page.getByLabel('Minutos de remoto adicionales',{exact:true}).fill('20');
    await page.getByLabel('Motivo de la asignación').fill('Ampliación autorizada de soporte');
    await page.getByRole('button',{name:'Confirmar horas adicionales'}).click();
    await expect(page.getByRole('status')).toContainText('Horas adicionales acreditadas');
    const body=writes.find(w=>w.path.endsWith('/hours')).body;
    assert.match(body.requestId,/^[0-9a-f-]{36}$/);assert.equal(body.emailMinutes,60);assert.equal(body.remoteMinutes,20);
    assert.equal(licenses[0].initialEmailMinutes,330);assert.equal(licenses[0].emailRemainingMinutes,390);
  });
  await check('New cases carry the selected license',async()=>{
    await navigate('Casos de soporte');await page.getByRole('button',{name:'Nuevo caso'}).click();
    await page.locator('select[name="institutionId"]').selectOption({label:institution.name});
    await page.getByLabel('Licencia para esta solicitud').selectOption({index:1});
    await page.getByLabel('Título del caso',{exact:true}).fill('Ayuda de importación');
    await page.locator('textarea[name="description"]').fill('Necesitamos ayuda para importar nuestros registros bibliográficos.');
    await page.getByRole('button',{name:'Revisar solicitud'}).click();await page.getByRole('button',{name:'Confirmar y crear caso'}).click();
    await expect(page.getByRole('heading',{name:'Saldo de la licencia'})).toBeVisible();
    assert.equal(writes.find(w=>w.path==='/api/cases').body.licenseId,1);
  });
  await context.close();
  await check('Clients can see their balances but cannot manage defaults or topups',async()=>{
    const {context,page}=await setup('USER',{width:390,height:844});
    await page.getByRole('button',{name:'Abrir navegación'}).click();
    await page.getByRole('navigation').getByRole('button',{name:'Licencias',exact:true}).click();
    await expect(page.getByRole('heading',{name:'Licencias',exact:true})).toBeVisible();
    await expect(page.getByRole('button',{name:'Nueva licencia',exact:true})).toHaveCount(0);
    await page.getByRole('button',{name:'Ver licencia LIC-PRUEBA-1'}).click();
    await expect(page.getByRole('button',{name:'Agregar horas',exact:true})).toHaveCount(0);
    await context.close();
  });
  assert.deepEqual(errors,[]);console.log(`${passed} browser scenarios passed (simulated API).`);
}finally{await browser.close();await new Promise(done=>server.close(done));}
