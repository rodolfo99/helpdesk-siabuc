import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Real Angular -> Spring -> PostgreSQL-compatible DB. No route mocking.
// This suite writes records; explicitly acknowledge a disposable database.
if (process.env.E2E_DISPOSABLE_DATABASE !== 'true') throw new Error('Esta prueba crea datos. Usa una base desechable y define E2E_DISPOSABLE_DATABASE=true.');
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:4200';
const adminUser = process.env.E2E_ADMIN_USER, adminPassword = process.env.E2E_ADMIN_PASSWORD;
if (!adminUser || !adminPassword) throw new Error('Define E2E_ADMIN_USER y E2E_ADMIN_PASSWORD de la instalación de pruebas.');
const moduleRoot = process.env.E2E_PLAYWRIGHT_PATH;
const { chromium } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'index.mjs')).href : 'playwright');
const { expect: baseExpect } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'test.mjs')).href : 'playwright/test');
const expect = baseExpect.configure({ timeout: 20000 });
const launch = { headless: process.env.E2E_HEADLESS !== 'false' };
if (process.env.E2E_CHROMIUM_PATH) launch.executablePath = process.env.E2E_CHROMIUM_PATH;
if (process.env.E2E_CHROMIUM_ARGS) launch.args = JSON.parse(process.env.E2E_CHROMIUM_ARGS);
const browser = await chromium.launch(launch);
const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1000 }, timezoneId: 'America/Mexico_City', acceptDownloads: true });
const page = await context.newPage(); page.setDefaultTimeout(20000);
const jsErrors = []; page.on('pageerror', error => jsErrors.push(error.message));
const runId = `${Date.now()}-${randomBytes(3).toString('hex')}`;
const institutionName = `E2E Póliza ${runId}`, folio = `POLIZA-${runId}`;
const billingUsername = `cobranza-${runId}`, clientUsername = `cliente-${runId}`;
const password = randomBytes(18).toString('base64url');
const caseTitle = `Diagnóstico de instalación ${runId}`;
const attachmentName = `diagnostico-${runId}.txt`, attachmentBody = `Diagnóstico SIABUC ${runId}\nError reproducible al abrir el catálogo.\n`;
const receiptName = `comprobante-sintetico-${runId}.pdf`;
const receiptBytes = syntheticPdf('Comprobante sintetico de pruebas. Sin valor fiscal. '+runId);
const steps = [];
let institutionId, otherCaseId, policyId, caseId, timerId, sessionId, attachmentId, paymentAttachmentId, firstPaymentBody;
const paymentIds = [];

async function step(name, task) {
  const start = Date.now();
  try { await task(); steps.push({ name, result: 'PASS', milliseconds: Date.now() - start }); console.log(`PASS ${name}`); }
  catch (error) { steps.push({ name, result: 'FAIL', milliseconds: Date.now() - start, error: error.message }); throw error; }
}
function ok(response) { assert.ok([200,201].includes(response.status), JSON.stringify(response)); return response.body; }
async function api(path, method='GET', body) {
  return page.evaluate(async ({path,method,body}) => {
    const csrf = document.cookie.split('; ').find(cookie => cookie.startsWith('XSRF-TOKEN='));
    const headers = {'Content-Type':'application/json'};
    if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf.slice('XSRF-TOKEN='.length));
    const response = await fetch('/api'+path,{method,headers,credentials:'same-origin',...(body===undefined?{}:{body:JSON.stringify(body)})});
    const raw = await response.text(); let result = raw; try { result = JSON.parse(raw); } catch {}
    return {status:response.status,body:result};
  },{path,method,body});
}
async function login(username, pass, role='ADMIN') {
  await page.goto('/'); await expect(page.getByRole('heading',{name:'Inicia sesión',exact:true})).toBeVisible();
  await page.getByLabel('Usuario',{exact:true}).fill(username); await page.getByLabel('Contraseña',{exact:true}).fill(pass);
  await page.getByRole('button',{name:/Entrar al centro de soporte/}).click();
  await expect(page.getByRole('heading',{name:role==='BILLING'?'Cobranza, al día.':'Tu soporte, al día.',exact:true})).toBeVisible();
  assert.equal(ok(await api('/auth/me')).role,role);
}
async function logout() { await page.getByRole('button',{name:/Cerrar sesión/}).click(); await expect(page.getByRole('heading',{name:'Inicia sesión',exact:true})).toBeVisible(); }
async function navigate(name) { await page.getByRole('navigation',{name:'Navegación principal'}).getByRole('button',{name,exact:true}).click(); }
async function mutate(method,path,action) {
  const pending = page.waitForResponse(response => response.request().method()===method && new URL(response.url()).pathname==='/api'+path);
  await action(); const response=await pending;
  assert.ok([200,201,204].includes(response.status()), `${method} ${path}: ${response.status()} ${await response.text()}`);
  return response;
}
async function institution() { const found=ok(await api('/institutions')).find(item=>item.id===institutionId); assert.ok(found); return found; }
async function assertBalances(email,remote,emailUsed=0,remoteUsed=0) {
  const current=await institution(); assert.equal(current.emailRemainingMinutes,email);assert.equal(current.remoteRemainingMinutes,remote);
  assert.equal(current.emailUsedMinutes,emailUsed);assert.equal(current.remoteUsedMinutes,remoteUsed);
}
async function policy() { const detail=ok(await api('/policies/'+policyId));assert.equal(detail.policy.id,policyId);return detail; }
async function snapshot(name) { if(process.env.E2E_REPORT_DIR){await mkdir(process.env.E2E_REPORT_DIR,{recursive:true});await page.screenshot({path:resolve(process.env.E2E_REPORT_DIR,name+'.png'),fullPage:true,animations:'disabled'});} }
async function noOverflow() { assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'La página desborda horizontalmente.'); }
async function downloaded(click, extension, prefix) {
  const pending=page.waitForEvent('download');await click();const download=await pending;
  assert.ok(download.suggestedFilename().toLowerCase().endsWith(extension),download.suggestedFilename());
  assert.equal(await download.failure(),null); const bytes=await readFile(await download.path());
  assert.ok(bytes.length>100, 'Archivo exportado vacío o incompleto.');assert.equal(bytes.subarray(0,prefix.length).toString('latin1'),prefix);
  if(process.env.E2E_REPORT_DIR)await download.saveAs(resolve(process.env.E2E_REPORT_DIR,download.suggestedFilename()));
}
async function openCase() {
  await navigate('Casos de soporte');await page.getByLabel('Buscar casos',{exact:true}).fill(caseTitle);
  await page.getByRole('row').filter({hasText:caseTitle}).getByRole('button',{name:'Ver caso '+caseId,exact:true}).click();
  await expect(page.getByRole('heading',{name:caseTitle,exact:true})).toBeVisible();
}
async function caseTab(name) { await page.getByRole('navigation',{name:'Secciones del caso'}).getByRole('button',{name,exact:true}).click(); }
function day(offset=0) {const local=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Mexico_City',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());const d=new Date(local+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10);}
function syntheticPdf(message) {
  const stream='BT /F1 10 Tf 40 750 Td ('+message.replace(/[\\()]/g,'\\$&')+') Tj ET';
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`];
  let content='%PDF-1.4\n';const offsets=[0];
  for(let index=0;index<objects.length;index++){offsets.push(Buffer.byteLength(content));content+=`${index+1} 0 obj\n${objects[index]}\nendobj\n`;}
  const xref=Buffer.byteLength(content);content+='xref\n0 6\n0000000000 65535 f \n';
  for(const offset of offsets.slice(1))content+=String(offset).padStart(10,'0')+' 00000 n \n';
  content+=`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;return Buffer.from(content,'ascii');
}

// Labels are intentionally user-facing; a UI/contract mismatch must fail here.
async function registerPolicyPayment({amount,email=0,remote=0,method='TRANSFER',suffix}) {
  await navigate('Pagos');await page.getByRole('button',{name:'＋ Registrar pago',exact:true}).click();
  await page.getByLabel('Institución del pago',{exact:false}).selectOption({label:institutionName});
  const policySelect=page.getByLabel('Póliza de soporte (opcional)',{exact:false});
  const policyOption=policySelect.locator('option').filter({hasText:folio});await expect(policyOption).toHaveCount(1);
  await policySelect.selectOption(await policyOption.getAttribute('value'));
  await page.getByLabel('Fecha del pago',{exact:true}).fill(day());
  await page.getByLabel('Importe recibido',{exact:false}).fill(String(amount));
  await page.getByLabel('Método de pago',{exact:false}).selectOption(method);
  await page.getByLabel('Referencia del pago',{exact:false}).fill(`PAGO-${runId}-${suffix}`);
  await page.getByLabel('Descripción del pago (se incluye en el aviso)',{exact:true}).fill(`Parcialidad de póliza ${suffix}; validación integral de cobranza.`);
  await page.getByLabel('Horas de correo',{exact:true}).fill(String(Math.floor(email/60)));
  await page.getByLabel('Minutos de correo',{exact:true}).fill(String(email%60));
  await page.getByLabel('Horas de remoto',{exact:true}).fill(String(Math.floor(remote/60)));
  await page.getByLabel('Minutos de remoto',{exact:true}).fill(String(remote%60));
  await expect(page.locator('input[name="paymentCurrency"]')).toHaveValue('MXN');await expect(page.locator('input[name="paymentCurrency"]')).toHaveAttribute('readonly','');
  const label=amount===0?'Registrar acreditación de horas':email+remote>0?'Registrar pago y acreditar horas':'Registrar parcialidad';
  const response=await mutate('POST','/payments',()=>page.getByRole('button',{name:label,exact:true}).click());
  await expect(page.getByRole('heading',{name:'Registrar pago recibido',exact:true})).toHaveCount(0);
  const payment=await response.json();paymentIds.push(payment.id);
  assert.equal(payment.policyId,policyId);assert.equal(payment.currency,'MXN');assert.equal(Number(payment.amount),amount);
  assert.equal(payment.emailMinutes,email);assert.equal(payment.remoteMinutes,remote);
  return {payment,body:response.request().postDataJSON()};
}

try {
  await step('Inicio real, correo externo desactivado y fixtures API de roles e instituciones',async()=>{
    await login(adminUser,adminPassword);
    const config=ok(await api('/notifications/config'));assert.equal(config.configuredEnabled,false,'Requiere MAIL_ENABLED=false, incluso si el correo está pausado.');
    ok(await api('/notifications/config','POST',{paused:true}));
    institutionId=ok(await api('/institutions','POST',{name:institutionName,contactName:'Contacto QA de póliza',email:`institucion-${runId}@example.test`,phone:'',notes:'Fixture desechable',emailAllocatedMinutes:0,remoteAllocatedMinutes:0})).id;
    const otherInstitution=ok(await api('/institutions','POST',{name:`Otra institución ${runId}`,contactName:'',email:'',phone:'',notes:'',emailAllocatedMinutes:5,remoteAllocatedMinutes:5})).id;
    otherCaseId=ok(await api('/cases','POST',{institutionId:otherInstitution,title:`Caso ajeno ${runId}`,description:'El cliente de otra institución no debe consultarlo.',channel:'EMAIL'})).id;
    ok(await api('/users','POST',{username:billingUsername,displayName:'Cobranza E2E pólizas',email:`cobranza-${runId}@example.test`,password,role:'BILLING',active:true}));
    ok(await api('/users','POST',{username:clientUsername,displayName:'Cliente E2E pólizas',email:`cliente-${runId}@example.test`,password,role:'USER',institutionId,active:true}));
    await assertBalances(0,0);await logout();await login(billingUsername,password,'BILLING');
  });
  await step('Cobranza crea póliza MXN desde interfaz sin acreditar minutos automáticamente',async()=>{
    await navigate('Pólizas');await page.getByRole('button',{name:/Nueva póliza/}).click();
    await page.getByLabel('Institución de la póliza',{exact:false}).selectOption({label:institutionName});
    await page.getByLabel('Folio de la póliza',{exact:false}).fill(folio);
    await page.getByLabel('Descripción de la póliza',{exact:true}).fill('Soporte SIABUC: dos horas de correo y una de escritorio remoto.');
    await page.getByLabel('Inicio de vigencia',{exact:true}).fill(day());await page.getByLabel('Fin de vigencia',{exact:true}).fill(day(365));
    await page.getByLabel('Importe acordado',{exact:false}).fill('2000.00');
    await page.getByLabel('Horas de correo',{exact:true}).fill('2');await page.getByLabel('Horas de remoto',{exact:true}).fill('1');
    const response=await mutate('POST','/policies',()=>page.getByRole('button',{name:'Guardar póliza',exact:true}).click());
    const detail=await response.json();policyId=detail.policy.id;
    assert.equal(detail.policy.currency,'MXN');assert.equal(Number(detail.policy.agreedAmount),2000);assert.equal(detail.policy.emailMinutes,120);assert.equal(detail.policy.remoteMinutes,60);
    assert.equal(detail.history.length,1);await assertBalances(0,0);await snapshot('01-poliza-creada');
  });
  await step('Primer abono monetario sin minutos conserva saldo institucional en cero',async()=>{
    const first=await registerPolicyPayment({amount:500,suffix:'abono',method:'DEPOSIT'});firstPaymentBody=first.body;
    await assertBalances(0,0);const detail=await policy();assert.equal(Number(detail.policy.paidAmount),500);assert.equal(Number(detail.policy.remainingAmount),1500);
    assert.equal(detail.policy.creditedEmailMinutes,0);assert.equal(detail.policy.creditedRemoteMinutes,0);
  });
  await step('Segundo abono acredita sólo los minutos indicados y conserva el pendiente contractual',async()=>{
    await registerPolicyPayment({amount:1000,email:60,remote:30,suffix:'parcial',method:'TRANSFER'});
    await assertBalances(60,30);const detail=await policy();assert.equal(Number(detail.policy.paidAmount),1500);assert.equal(Number(detail.policy.remainingAmount),500);
    assert.equal(detail.policy.remainingEmailMinutes,60);assert.equal(detail.policy.remainingRemoteMinutes,30);
  });
  await step('Liquidación seguida de liberación de minutos con importe cero, sin doble crédito',async()=>{
    await registerPolicyPayment({amount:500,suffix:'liquidacion',method:'CREDIT_CARD'});await assertBalances(60,30);
    await registerPolicyPayment({amount:0,email:60,remote:30,suffix:'liberacion',method:'OTHER'});await assertBalances(120,60);
    const detail=await policy();assert.equal(Number(detail.policy.paidAmount),2000);assert.equal(Number(detail.policy.remainingAmount),0);
    assert.equal(detail.policy.remainingEmailMinutes,0);assert.equal(detail.policy.remainingRemoteMinutes,0);assert.equal(detail.payments.length,4);assert.equal(detail.history.length,5);
    const retry=ok(await api('/payments','POST',firstPaymentBody));assert.equal(retry.id,paymentIds[0]);await assertBalances(120,60);
    assert.equal((await policy()).payments.length,4);
    const invalid={...firstPaymentBody,requestId:randomUUID(),reference:`EXCESO-${runId}`,amount:1};assert.equal((await api('/payments','POST',invalid)).status,409);
    assert.equal((await api('/payments','POST',{...invalid,reference:`USD-${runId}`,currency:'USD'})).status,400);
    assert.equal((await api('/payments/'+paymentIds[0]+'/void','POST',{reason:'Cobranza no anula.'})).status,403);
    assert.equal((await api('/policies/'+policyId+'/cancel','POST',{reason:'No puede cancelar con pagos vigentes.'})).status,409);
  });
  await step('Cobranza adjunta comprobante PDF al pago y descarga los mismos bytes sin cambiar saldos',async()=>{
    await navigate('Pagos');await page.getByRole('row').filter({hasText:firstPaymentBody.reference}).getByRole('button',{name:'Ver detalle',exact:true}).click();
    await expect(page.getByRole('heading',{name:'Comprobantes de pago',exact:true})).toBeVisible();
    await page.getByLabel('Seleccionar comprobante',{exact:true}).setInputFiles({name:receiptName,mimeType:'application/pdf',buffer:receiptBytes});
    const uploaded=await mutate('POST','/payments/'+paymentIds[0]+'/attachments',()=>page.getByRole('button',{name:'Adjuntar comprobante',exact:true}).click());
    const receipt=await uploaded.json();paymentAttachmentId=receipt.id;
    assert.equal(receipt.paymentId,paymentIds[0]);assert.equal(receipt.filename,receiptName);assert.equal(receipt.contentType,'application/pdf');assert.equal(receipt.sizeBytes,receiptBytes.length);
    await expect(page.getByText(receiptName,{exact:true})).toBeVisible();
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Descargar '+receiptName,exact:true}).click();const download=await pending;
    assert.equal(download.suggestedFilename(),receiptName);assert.deepEqual(await readFile(await download.path()),receiptBytes);
    await assertBalances(120,60);assert.equal((await policy()).payments.length,4);await snapshot('02-comprobante-pago');
  });
  await step('Historial de póliza UI y reporte financiero separado por MXN con descargas reales',async()=>{
    await navigate('Pólizas');await page.getByRole('row').filter({hasText:folio}).getByRole('button',{name:'Ver póliza '+folio,exact:true}).click();
    await expect(page.getByText(folio,{exact:false}).first()).toBeVisible();await expect(page.getByRole('heading',{name:/Historial de la póliza/})).toBeVisible();
    const report=ok(await api('/reports/billing?institutionId='+institutionId+'&policyId='+policyId+'&from='+day()+'&to='+day()));
    assert.equal(report.payments.length,4);assert.equal(report.totals.length,1);assert.equal(report.totals[0].currency,'MXN');assert.equal(Number(report.totals[0].activeAmount),2000);
    await navigate('Reportes de cobranza');
    await page.locator('select[name="billingReportInstitution"]').selectOption({label:institutionName});
    await page.getByLabel('Fecha del pago desde',{exact:true}).fill(day());await page.getByLabel('Fecha del pago hasta',{exact:true}).fill(day());
    await page.getByRole('button',{name:'Consultar reporte',exact:true}).click();
    await expect(page.getByRole('heading',{name:'MXN',exact:true})).toBeVisible();
    await downloaded(()=>page.getByRole('button',{name:'Exportar Excel',exact:true}).click(),'.xlsx','PK');
    await downloaded(()=>page.getByRole('button',{name:'Exportar PDF',exact:true}).click(),'.pdf','%PDF');
    await snapshot('02-reporte-cobranza');await page.setViewportSize({width:390,height:844});await noOverflow();await snapshot('03-reporte-movil');await page.setViewportSize({width:1440,height:1000});
  });
  await step('Cliente sin acceso financiero abre caso con categoría, prioridad y ficha técnica reales',async()=>{
    await logout();await login(clientUsername,password,'USER');
    for(const name of ['Pólizas','Pagos','Reportes de cobranza'])await expect(page.getByRole('navigation',{name:'Navegación principal'}).getByRole('button',{name,exact:true})).toHaveCount(0);
    for(const path of ['/policies','/policies/'+policyId,'/payments','/payments/'+paymentIds[0]+'/attachments','/payment-attachments/'+paymentAttachmentId+'/download','/reports/billing','/reports/billing.xlsx','/reports/billing.pdf'])assert.equal((await api(path)).status,403,path);
    assert.equal((await api('/cases/'+otherCaseId)).status,404);
    await navigate('Casos de soporte');await page.getByRole('button',{name:'＋ Nuevo caso',exact:true}).click();
    const createForm=page.locator('form').filter({has:page.locator('input[name="title"]')});
    await createForm.getByLabel('Categoría',{exact:false}).selectOption('INSTALLATION');await createForm.getByLabel('Prioridad',{exact:false}).selectOption('HIGH');
    await createForm.getByLabel('Título del caso',{exact:true}).fill(caseTitle);await createForm.getByLabel('Descripción',{exact:true}).fill('El catálogo no abre después de instalar SIABUC. Solicito revisar la configuración.');
    await createForm.getByText('Ficha técnica (opcional)',{exact:true}).click();
    await createForm.getByLabel('Versión de SIABUC',{exact:true}).fill('SIABUC 9');await createForm.getByLabel('Sistema operativo',{exact:true}).fill('Windows 11');
    await createForm.getByLabel('Mensaje de error',{exact:true}).fill('No se puede abrir el catálogo de prueba.');await createForm.getByLabel('Pasos para reproducirlo',{exact:true}).fill('1. Abrir SIABUC. 2. Elegir catálogo. 3. Aparece error.');
    const response=await mutate('POST','/cases',()=>page.getByRole('button',{name:'Crear caso',exact:true}).click());caseId=(await response.json()).id;
    await expect(page.getByRole('heading',{name:caseTitle,exact:true})).toBeVisible();
    await expect.poll(async()=>ok(await api('/cases/'+caseId+'/profile')).category).toBe('INSTALLATION');
    const profile=ok(await api('/cases/'+caseId+'/profile'));assert.equal(profile.priority,'HIGH');assert.equal(profile.siabucVersion,'SIABUC 9');assert.equal(profile.operatingSystem,'Windows 11');assert.ok(profile.sla.firstResponseDueAt);
    await assertBalances(120,60);
  });
  await step('Adjunto de diagnóstico sube por multipart, queda en historial y se descarga intacto',async()=>{
    await caseTab('Adjuntos del caso');await page.getByLabel('Agregar archivo',{exact:true}).setInputFiles({name:attachmentName,mimeType:'text/plain',buffer:Buffer.from(attachmentBody)});
    const response=await mutate('POST','/cases/'+caseId+'/attachments',()=>page.getByRole('button',{name:'Adjuntar archivo',exact:true}).click());attachmentId=(await response.json()).id;
    await expect(page.getByText(attachmentName,{exact:true})).toBeVisible();
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Descargar '+attachmentName,exact:true}).click();const download=await pending;
    assert.equal(await readFile(await download.path(),'utf8'),attachmentBody);
    assert.ok(ok(await api('/cases/'+caseId)).entries.some(entry=>entry.description.includes(attachmentName)));await snapshot('04-cliente-adjunto');
  });
  await step('Administrativo toma caso y cronómetro persiste sin cargo hasta confirmar minutos',async()=>{
    await logout();await login(adminUser,adminPassword);await openCase();
    await mutate('POST','/cases/'+caseId+'/claim',()=>page.getByRole('button',{name:'Tomar caso',exact:true}).click());
    ok(await api('/balance-alert-settings/'+institutionId,'PUT',{enabled:true,emailThresholdMinutes:0,remoteThresholdMinutes:59}));
    const started=await mutate('POST','/cases/'+caseId+'/timer/start',()=>page.getByRole('button',{name:'Iniciar cronómetro',exact:true}).click());timerId=(await started.json()).id;
    await mutate('POST','/timers/'+timerId+'/pause',()=>page.getByRole('button',{name:'Pausar',exact:true}).click());
    await navigate('Resumen');await openCase();await expect(page.getByText('Pausado',{exact:true})).toBeVisible();
    assert.equal(ok(await api('/cases/'+caseId+'/timer')).status,'PAUSED');await assertBalances(120,60);
    await mutate('POST','/timers/'+timerId+'/resume',()=>page.getByRole('button',{name:'Continuar',exact:true}).click());
    await mutate('POST','/timers/'+timerId+'/finish',()=>page.getByRole('button',{name:'Finalizar atención',exact:true}).click());await assertBalances(120,60);
    const timer=page.getByRole('region',{name:'Cronómetro de atención',exact:true});
    await timer.getByLabel('Minutos a descontar',{exact:true}).fill('2');await timer.getByLabel('Canal',{exact:false}).selectOption('REMOTE');
    await timer.getByLabel('Descripción de la atención',{exact:true}).fill('Se revisó la configuración de conexión del catálogo.');
    if(await timer.getByLabel('Motivo del ajuste de minutos',{exact:true}).count())await timer.getByLabel('Motivo del ajuste de minutos',{exact:true}).fill('Prueba de confirmación manual: se documentan dos minutos de trabajo técnico.');
    const committed=await mutate('POST','/timers/'+timerId+'/commit',()=>timer.getByRole('button',{name:'Confirmar y descontar 2 minutos',exact:true}).click());
    assert.equal((await committed.json()).status,'COMMITTED');await assertBalances(120,58,0,2);
    assert.ok(ok(await api('/cases/'+caseId+'/profile')).sla.firstRespondedAt);await snapshot('05-cronometro-cobrado');
  });
  await step('Administrativo propone sesión futura sin descontar minutos',async()=>{
    await caseTab('Agenda remota');const scheduledDay=day(2);
    await page.getByLabel('Inicio',{exact:true}).fill(scheduledDay+'T10:00');await page.getByLabel('Fin',{exact:true}).fill(scheduledDay+'T10:30');
    await page.getByLabel('Objetivo y preparativos',{exact:true}).fill('Revisión conjunta de la configuración; preparar el catálogo de pruebas.');
    const response=await mutate('POST','/cases/'+caseId+'/remote-sessions',()=>page.getByRole('button',{name:'Proponer al cliente',exact:true}).click());sessionId=(await response.json()).id;
    assert.equal((await response.json()).status,'PROPOSED');await assertBalances(120,58,0,2);
  });
  await step('Cliente recibe campana con correo pausado, consulta historial y confirma agenda',async()=>{
    await logout();await login(clientUsername,password,'USER');
    const inbox=ok(await api('/inbox'));assert.ok(inbox.items.some(item=>item.caseId===caseId&&item.eventType==='REMOTE_SESSION_PROPOSED'));
    assert.ok(inbox.items.some(item=>item.eventType==='LOW_BALANCE'&&item.institutionId===institutionId&&item.description.includes('58 minutos')&&item.description.includes('remoto')));
    await page.getByRole('button',{name:'Notificaciones y respuestas pendientes',exact:true}).click();
    await expect(page.getByRole('region',{name:'Notificaciones internas',exact:true})).toContainText('aunque el correo esté pausado');
    await page.getByRole('button',{name:'Cerrar notificaciones',exact:true}).click();await openCase();await caseTab('Agenda remota');
    await mutate('POST','/remote-sessions/'+sessionId+'/confirm',()=>page.getByRole('button',{name:'Confirmar horario',exact:true}).click());
    assert.equal(ok(await api('/cases/'+caseId+'/remote-sessions')).find(item=>item.id===sessionId).status,'CONFIRMED');await assertBalances(120,58,0,2);
    await caseTab('Seguimiento');const entry=ok(await api('/cases/'+caseId)).entries.find(item=>item.description.includes('Se revisó la configuración'));
    await page.getByRole('link',{name:'Ver detalle de entrada '+entry.id,exact:true}).click();await expect(page.getByRole('heading',{name:'Detalle de entrada #'+entry.id,exact:true})).toBeVisible();
    await page.getByRole('link',{name:'← Volver al historial',exact:true}).click();await expect(page.getByRole('heading',{name:'Historial de atención',exact:true})).toBeVisible();await snapshot('06-cliente-historial');
  });
  await step('Administrativo consulta historial financiero y verifica que ningún aviso salió por SMTP',async()=>{
    await logout();await login(adminUser,adminPassword);const detail=await policy();assert.equal(detail.payments.length,4);assert.equal(detail.history.filter(item=>item.action==='CREATE_PAYMENT').length,4);
    const notices=ok(await api('/notifications')).filter(item=>item.caseId===caseId||paymentIds.includes(item.paymentId));assert.ok(notices.length>0);assert.ok(notices.every(item=>item.status==='SUPPRESSED'&&item.attempts===0&&item.sentAt===null));
    assert.equal(ok(await api('/notifications/config')).configuredEnabled,false);assert.deepEqual(jsErrors,[]);await logout();
  });
} catch(error) {
  console.error(`FAIL ${steps.at(-1)?.name||'Preparación'}: ${error.stack||error}`);await snapshot('billing-v15-failure').catch(()=>{});process.exitCode=1;
} finally {
  if(process.env.E2E_REPORT_DIR){await mkdir(process.env.E2E_REPORT_DIR,{recursive:true});await writeFile(resolve(process.env.E2E_REPORT_DIR,'billing-v15-results.json'),JSON.stringify({baseURL,runId,institutionId,policyId,paymentIds,paymentAttachmentId,caseId,timerId,sessionId,attachmentId,steps,jsErrors},null,2));}
  await browser.close();console.log(`${steps.filter(item=>item.result==='PASS').length}/${steps.length} verificaciones reales de pólizas, cobranza y soporte satisfactorias.`);
}
