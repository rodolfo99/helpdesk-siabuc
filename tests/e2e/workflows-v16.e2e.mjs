import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { inflateSync } from 'node:zlib';
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
const institutionName = `E2E Flujos 1.6 ${runId}`, folio = `POLIZA16-${runId}`, quoteFolio = `COT16-${runId}`;
const billingUsername = `cobranza16-${runId}`, clientUsername = `cliente16-${runId}`, otherUsername = `ajeno16-${runId}`;
const password = randomBytes(18).toString('base64url');
const caseTitle = `Caso de satisfacción ${runId}`, articleTitle = `Solución publicada ${runId}`, draftTitle = `Borrador privado ${runId}`;
const articleBody = `1. Abrir el catálogo de prueba.\n2. Verificar la configuración.\nDiagnóstico documentado ${runId}.`;
const reference = `SOLICITUD16-${runId}`, receiptName = `evidencia16-${runId}.pdf`;
const receiptBytes = syntheticPdf('Comprobante sintetico 1.6 sin valor fiscal. '+runId);
const surveyComment = `Atención clara y solución documentada ${runId}.`;
const steps = [];
let institutionId, otherInstitutionId, policyId, quoteId, requestId, requestProofId, paymentId, caseId, otherCaseId, articleId, draftId, surveyId, adminId, surveyPayload;
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
  return {filename:download.suggestedFilename(),bytes};
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


// Inspect server PDFBox Standard14 text streams, including compression and hex strings.
// The Java receipt integration tests additionally parse and verify the PDF document itself.
function pdfTextStreams(bytes) {
  const raw=bytes.toString('latin1');let text=raw;
  for(const match of raw.matchAll(/stream\r?\n/g)) {
    const start=match.index+match[0].length,end=raw.indexOf('endstream',start);if(end<0)continue;
    try{text+='\n'+inflateSync(bytes.subarray(start,end)).toString('latin1');}catch{}
  }
  text=text.replace(/\\([0-7]{1,3})/g,(_,octal)=>String.fromCharCode(parseInt(octal,8)));
  return text+'\n'+Array.from(text.matchAll(/<([0-9A-Fa-f]{2,})>/g),match=>Buffer.from(match[1],'hex').toString('latin1')).join('\n');
}

try {
  await step('Inicio real, SMTP desactivado y creación de cuentas de prueba aisladas',async()=>{
    await login(adminUser,adminPassword);adminId=ok(await api('/auth/me')).id;
    assert.equal(ok(await api('/notifications/config')).configuredEnabled,false,'Requiere MAIL_ENABLED=false.');
    ok(await api('/notifications/config','POST',{paused:true}));
    institutionId=ok(await api('/institutions','POST',{name:institutionName,contactName:'Contacto de pruebas',email:`institucion16-${runId}@example.test`,phone:'',notes:'Fixture desechable 1.6',emailAllocatedMinutes:0,remoteAllocatedMinutes:0})).id;
    otherInstitutionId=ok(await api('/institutions','POST',{name:`Institución ajena16 ${runId}`,contactName:'',email:'',phone:'',notes:'Fixture de aislamiento',emailAllocatedMinutes:5,remoteAllocatedMinutes:5})).id;
    otherCaseId=ok(await api('/cases','POST',{institutionId:otherInstitutionId,title:`Caso ajeno16 ${runId}`,description:'Fuera de la institución cliente.',channel:'EMAIL'})).id;
    for(const [username,displayName,role,id] of [[billingUsername,'Cobranza flujos 1.6','BILLING',null],[clientUsername,'Cliente flujos 1.6','USER',institutionId],[otherUsername,'Otro cliente flujos 1.6','USER',otherInstitutionId]]) {
      ok(await api('/users','POST',{username,displayName,email:`${username}@example.test`,password,role,...(id?{institutionId:id}:{}),active:true}));
    }
    assert.equal(ok(await api('/client-finance-access')).find(item=>item.institutionId===institutionId).enabled,false);
    assert.equal(ok(await api('/backups/status')).settings.enabled,false);await assertBalances(0,0);
  });
  await step('Respaldos desactivados por defecto; guardar horario no activa calendario y worker ausente impide ejecuciones',async()=>{
    await navigate('Respaldos');await expect(page.getByRole('heading',{name:'Calendario automático: desactivado',exact:true})).toBeVisible();
    const state=ok(await api('/backups/status'));assert.equal(state.worker.online,false,'Este recorrido exige instalación desechable sin worker de respaldo.');
    await expect(page.getByRole('button',{name:'Crear respaldo ahora',exact:true})).toBeDisabled();
    await expect(page.getByRole('button',{name:'Activar respaldos automáticos',exact:true})).toBeDisabled();
    await page.getByLabel('Hora diaria',{exact:true}).fill('04:30');await page.getByLabel('Zona horaria',{exact:true}).fill('America/Mexico_City');
    await page.getByLabel('Retención en días',{exact:true}).fill('21');await page.getByLabel('Conservar al menos estas copias automáticas',{exact:true}).fill('5');
    const saved=await mutate('PUT','/backups/config',()=>page.getByRole('button',{name:'Guardar configuración',exact:true}).click());
    const data=await saved.json();assert.equal(data.settings.enabled,false);assert.equal(data.settings.dailyTime,'04:30');assert.equal(data.settings.retentionDays,21);assert.equal(data.settings.keepLast,5);
    assert.equal(ok(await api('/backups/enabled','POST',{enabled:false})).settings.enabled,false);
    assert.equal((await api('/backups/run','POST',{})).status,409);assert.equal(ok(await api('/backups/status')).runs.length,0);
    await expect(page.getByText('El respaldo manual funciona aunque el calendario esté desactivado.',{exact:false})).toBeVisible();await snapshot('01-respaldos-deshabilitados');
    await logout();await login(billingUsername,password,'BILLING');
    for(const path of ['/backups/status','/client-finance-access','/satisfaction/reports','/knowledge'])assert.equal((await api(path)).status,403,path);
    assert.equal((await api('/backups/config','PUT',{enabled:false})).status,403);
  });
  await step('Cobranza prepara cotización desde interfaz y registra emisión y aceptación con constancias',async()=>{
    await navigate('Cotizaciones');await page.getByRole('button',{name:'Nueva cotización',exact:true}).click();
    await page.locator('select[name="quoteInstitution"]').selectOption({label:institutionName});
    await page.getByLabel('Folio de cotización',{exact:true}).fill(quoteFolio);await page.getByLabel('Oferta válida hasta',{exact:true}).fill(day(30));
    await page.locator('input[name="quoteAmount"]').fill('1000.00');await page.getByLabel('Inicio de cobertura',{exact:true}).fill(day());await page.getByLabel('Fin de cobertura',{exact:true}).fill(day(365));
    await page.getByLabel('Descripción y condiciones',{exact:true}).fill('Soporte SIABUC por un año: una hora de correo y media hora de remoto.');
    await page.locator('input[name="quoteEmailHours"]').fill('1');await page.locator('input[name="quoteRemoteMinutes"]').fill('30');
    const created=await mutate('POST','/quotes',()=>page.getByRole('button',{name:'Guardar borrador',exact:true}).click());const draft=(await created.json()).quote;quoteId=draft.id;
    assert.equal(draft.status,'DRAFT');assert.equal(draft.emailMinutes,60);assert.equal(draft.remoteMinutes,30);await assertBalances(0,0);
    await page.getByRole('button',{name:'Registrar emisión',exact:true}).click();await page.getByLabel('Medio, destinatario y referencia',{exact:true}).fill('Entrega de oferta por demostración local al contacto QA. Constancia sintética; no se envió correo.');
    const sent=await mutate('POST','/quotes/'+quoteId+'/send',()=>page.getByRole('button',{name:'Confirmar registro',exact:true}).click());assert.equal((await sent.json()).quote.status,'SENT');
    await page.getByRole('button',{name:'Registrar aceptación',exact:true}).click();await page.getByLabel('Nombre del contacto que respondió',{exact:true}).fill('Contacto de pruebas');await page.getByLabel('Fecha de respuesta',{exact:true}).fill(day());
    await page.getByLabel('Constancia de la respuesta',{exact:true}).fill('Aceptación sintética capturada por cobranza para el recorrido; no constituye firma digital.');
    const accepted=await mutate('POST','/quotes/'+quoteId+'/decision',()=>page.getByRole('button',{name:'Confirmar registro',exact:true}).click());assert.equal((await accepted.json()).quote.status,'ACCEPTED');await assertBalances(0,0);
  });
  await step('Conversión a póliza conserva términos, es idempotente y no acredita soporte',async()=>{
    await page.getByRole('button',{name:'Convertir a póliza',exact:true}).click();await page.getByLabel('Folio único de la nueva póliza',{exact:true}).fill(folio);
    const response=await mutate('POST','/quotes/'+quoteId+'/convert',()=>page.getByRole('button',{name:'Confirmar conversión',exact:true}).click());const detail=await response.json();policyId=detail.quote.convertedPolicyId;
    assert.equal(detail.quote.status,'CONVERTED');assert.ok(policyId);const converted=await policy();assert.equal(Number(converted.policy.agreedAmount),1000);assert.equal(converted.policy.emailMinutes,60);assert.equal(converted.policy.remoteMinutes,30);assert.equal(converted.payments.length,0);
    assert.equal(ok(await api('/quotes/'+quoteId+'/convert','POST',{policyFolio:folio})).quote.convertedPolicyId,policyId);await assertBalances(0,0);
    await expect(page.getByText('Póliza generada:',{exact:false})).toBeVisible();await snapshot('02-cotizacion-convertida');
    await logout();await login(clientUsername,password,'USER');
  });
  await step('Cliente encuentra portal financiero cerrado y APIs privadas denegadas hasta habilitación explícita',async()=>{
    assert.equal(ok(await api('/client-finance/access')).enabled,false);
    for(const path of ['/client-finance','/client-finance/payment-requests','/quotes','/policies','/payment-requests','/backups/status','/satisfaction/reports'])assert.equal((await api(path)).status,403,path);
    assert.equal((await api('/client-finance-access/'+institutionId,'PUT',{enabled:true})).status,403);
    await navigate('Finanzas institucionales');await expect(page.getByText('El portal financiero de tu institución no está habilitado.',{exact:false})).toBeVisible();
    await expect(page.getByRole('button',{name:'Nueva solicitud',exact:true})).toHaveCount(0);
    await logout();await login(adminUser,adminPassword);await navigate('Configuración de soporte');
    await page.getByRole('row').filter({hasText:institutionName}).getByRole('button',{name:'Habilitar',exact:true}).click();
    const changed=await mutate('PUT','/client-finance-access/'+institutionId,()=>page.getByRole('button',{name:'Confirmar habilitación',exact:true}).click());assert.equal((await changed.json()).enabled,true);
    await assertBalances(0,0);await logout();await login(clientUsername,password,'USER');
  });
  await step('Cliente crea solicitud pendiente y adjunta PDF real sin acreditar saldo',async()=>{
    await navigate('Finanzas institucionales');await expect(page.getByRole('cell',{name:folio,exact:false})).toBeVisible();
    await page.getByRole('button',{name:'Nueva solicitud',exact:true}).click();await page.getByLabel('Póliza',{exact:false}).selectOption({label:folio});
    await page.getByLabel('Referencia del pago',{exact:true}).fill(reference);await page.getByLabel('Importe solicitado (MXN)',{exact:true}).fill('1000');await page.getByLabel('Fecha del pago',{exact:true}).fill(day());
    await page.locator('select[name="method"]').selectOption({label:'Transferencia'});
    await page.getByLabel('Minutos de correo solicitados',{exact:true}).fill('60');await page.getByLabel('Minutos remotos solicitados',{exact:true}).fill('30');
    await page.getByLabel('Descripción visible para tu institución',{exact:true}).fill('Liquidación sintética con minutos sujetos a validación de cobranza.');
    const saved=await mutate('POST','/client-finance/payment-requests',()=>page.getByRole('button',{name:'Guardar solicitud pendiente',exact:true}).click());requestId=(await saved.json()).request.id;
    assert.equal((await saved.json()).request.status,'PENDING');await assertBalances(0,0);
    await page.getByLabel('Comprobante de la solicitud',{exact:true}).setInputFiles({name:receiptName,mimeType:'application/pdf',buffer:receiptBytes});
    const uploaded=await mutate('POST','/client-finance/payment-requests/'+requestId+'/attachments',()=>page.getByRole('button',{name:'Adjuntar evidencia',exact:true}).click());requestProofId=(await uploaded.json()).id;
    const proof=await downloaded(()=>page.getByRole('button',{name:'Descargar '+receiptName,exact:true}).click(),'.pdf','%PDF');assert.deepEqual(proof.bytes,receiptBytes);
    assert.equal(ok(await api('/client-finance/payment-requests/'+requestId)).request.status,'PENDING');await assertBalances(0,0);
    assert.equal((await api('/payment-requests/'+requestId+'/approve','POST',{reason:'Cliente no aprueba.'})).status,403);await snapshot('03-solicitud-pendiente');
    await logout();await login(otherUsername,password,'USER');assert.equal((await api('/client-finance/payment-requests/'+requestId)).status,403);
    await logout();await login(billingUsername,password,'BILLING');
  });
  await step('Cobranza revisa evidencia y aprueba exactamente una vez con pago, póliza y saldo consistentes',async()=>{
    await navigate('Validar pagos');await page.getByRole('button',{name:'Ver solicitud #'+requestId,exact:true}).click();
    await expect(page.getByRole('button',{name:'Aprobar y registrar pago',exact:true})).toBeDisabled();
    const proof=await downloaded(()=>page.getByRole('button',{name:'Descargar '+receiptName,exact:true}).click(),'.pdf','%PDF');assert.deepEqual(proof.bytes,receiptBytes);
    await page.getByLabel('Observación privada para aprobar',{exact:true}).fill('Evidencia sintética revisada; autorización para el recorrido de pruebas.');
    await page.getByRole('checkbox',{name:'Verifiqué la evidencia, el importe y ambos saldos de minutos solicitados.',exact:true}).check();
    const response=await mutate('POST','/payment-requests/'+requestId+'/approve',()=>page.getByRole('button',{name:'Aprobar y registrar pago',exact:true}).click());const approved=(await response.json()).request;paymentId=approved.paymentId;assert.equal(approved.status,'APPROVED');assert.ok(paymentId);
    await assertBalances(60,30);assert.equal(ok(await api('/payment-requests/'+requestId+'/approve','POST',{reason:'Reintento de la misma aprobación.'})).request.paymentId,paymentId);await assertBalances(60,30);
    const contract=await policy();assert.equal(contract.payments.length,1);assert.equal(Number(contract.policy.paidAmount),1000);assert.equal(contract.policy.creditedEmailMinutes,60);assert.equal(contract.policy.creditedRemoteMinutes,30);
    assert.equal((await api('/payment-requests/'+requestId+'/reject','POST',{reason:'No puede rechazar una aprobación.'})).status,409);
    await expect(page.getByLabel('Comprobante de la solicitud',{exact:true})).toHaveCount(0);await snapshot('04-aprobacion-unica');
  });
  await step('Recibo administrativo PDF estable, evidencia conservada y descargas sin cobro adicional',async()=>{
    await navigate('Pagos');await page.getByRole('row').filter({hasText:reference}).getByRole('button',{name:'Ver detalle',exact:true}).click();
    const first=await downloaded(()=>page.getByRole('button',{name:'Descargar recibo PDF',exact:true}).click(),'.pdf','%PDF');assert.match(first.filename,/^SIABUC-REC-\d+\.pdf$/);
    const second=await downloaded(()=>page.getByRole('button',{name:'Descargar recibo PDF',exact:true}).click(),'.pdf','%PDF');assert.equal(second.filename,first.filename);
    const proof=await downloaded(()=>page.getByRole('button',{name:'Descargar '+receiptName,exact:true}).click(),'.pdf','%PDF');assert.deepEqual(proof.bytes,receiptBytes);
    await assertBalances(60,30);assert.equal((await policy()).payments.length,1);
    await logout();await login(clientUsername,password,'USER');await navigate('Finanzas institucionales');
    await page.locator('summary').filter({hasText:'Pago #'+paymentId+' · '+reference}).click();
    const clientReceipt=await downloaded(()=>page.getByRole('button',{name:'Descargar recibo PDF',exact:true}).click(),'.pdf','%PDF');assert.equal(clientReceipt.filename,first.filename);
    const publicRequest=ok(await api('/client-finance/payment-requests/'+requestId)).request;assert.equal(publicRequest.status,'APPROVED');assert.equal(publicRequest.decisionReason,null);assert.equal(publicRequest.decidedBy,null);await assertBalances(60,30);
    await logout();await login(adminUser,adminPassword);
    ok(await api('/client-finance-access/'+otherInstitutionId,'PUT',{enabled:true}));
    await logout();await login(otherUsername,password,'USER');
    for(const path of ['/client-finance/payments/'+paymentId+'/receipt.pdf','/client-finance/payment-requests/'+requestId,'/client-finance/payment-requests/attachments/'+requestProofId+'/download'])assert.equal((await api(path)).status,404,path);
    await logout();await login(adminUser,adminPassword);
  });
  await step('Base de conocimientos publica un artículo y conserva otro como borrador privado',async()=>{
    await navigate('Base de conocimientos');await page.getByRole('button',{name:'Nuevo artículo',exact:true}).click();
    await page.getByLabel('Título del artículo',{exact:true}).fill(articleTitle);await page.locator('select[name="knowledgeCategory"]').selectOption('CATALOGING');
    await page.getByLabel('Versiones de SIABUC aplicables',{exact:true}).fill('SIABUC 9');await page.getByLabel('Resumen',{exact:true}).fill('Procedimiento documentado para verificar el catálogo de pruebas.');
    await page.getByLabel('Solución y pasos',{exact:true}).fill(articleBody);await page.getByLabel('Nota editorial',{exact:true}).fill('Primera revisión pública sintética.');
    const created=await mutate('POST','/knowledge',()=>page.getByRole('button',{name:'Guardar borrador',exact:true}).click());articleId=(await created.json()).id;
    await page.getByRole('button',{name:'Publicar artículo',exact:true}).click();await page.getByLabel('Motivo del cambio',{exact:true}).fill('Solución verificada para la base de prueba.');
    const published=await mutate('POST','/knowledge/'+articleId+'/status',()=>page.getByRole('button',{name:'Confirmar cambio editorial',exact:true}).click());assert.equal((await published.json()).status,'PUBLISHED');
    draftId=ok(await api('/knowledge','POST',{title:draftTitle,category:'OTHER',siabucVersion:'',summary:'Contenido reservado para revisión editorial.',body:'BORRADOR PRIVADO '+runId,changeNote:'No publicar.'})).id;
    caseId=ok(await api('/cases','POST',{institutionId,title:caseTitle,description:'Caso de prueba con solución pública y evaluación institucional.',channel:'EMAIL'})).id;
    await openCase();const solutions=page.getByRole('region',{name:'Soluciones de la base de conocimientos',exact:true});
    await solutions.getByLabel('Buscar artículo publicado',{exact:true}).fill(articleTitle);await solutions.getByRole('button',{name:'Buscar soluciones',exact:true}).click();
    const linked=await mutate('POST','/cases/'+caseId+'/knowledge-links',()=>solutions.getByRole('button',{name:'Vincular solución',exact:true}).click());assert.equal((await linked.json())[0].article.id,articleId);await assertBalances(60,30);
    await page.getByRole('button',{name:'Cambiar estado',exact:true}).click();await page.locator('[name=statusTarget]').selectOption('CLOSED');await page.getByLabel('Motivo',{exact:true}).fill('Solución documentada y caso terminado para evaluar la atención.');
    const closed=await mutate('POST','/cases/'+caseId+'/status',()=>page.getByRole('button',{name:'Confirmar cambio',exact:true}).click());assert.equal((await closed.json()).status,'CLOSED');
    assert.equal((await api('/satisfaction/cases/'+caseId,'POST',{requestId:randomUUID(),score:5,comment:'Administración no sustituye al cliente.'})).status,403);await snapshot('05-caso-cerrado-solucion');
    await logout();await login(clientUsername,password,'USER');
  });
  await step('Cliente consulta solución pública del caso, nunca borrador ajeno y responde encuesta cerrada',async()=>{
    assert.equal((await api('/knowledge/'+draftId)).status,404);assert.equal((await api('/knowledge/'+articleId+'/history')).status,403);
    assert.ok(!ok(await api('/knowledge?q='+encodeURIComponent(runId))).items.some(item=>item.id===draftId));
    assert.equal((await api('/satisfaction/cases/'+otherCaseId)).status,404);
    await navigate('Base de conocimientos');await page.getByLabel('Buscar solución',{exact:true}).fill(articleTitle);await page.getByRole('button',{name:'Buscar',exact:true}).click();
    await page.getByRole('button',{name:articleTitle,exact:true}).click();await expect(page.getByText(articleBody,{exact:true})).toBeVisible();
    await openCase();const solutions=page.getByRole('region',{name:'Soluciones de la base de conocimientos',exact:true});await solutions.getByRole('button',{name:articleTitle,exact:true}).click();await expect(solutions.getByText(articleBody,{exact:true})).toBeVisible();
    await solutions.getByRole('button',{name:'← Volver al listado de soluciones',exact:true}).click();
    const survey=page.getByRole('region',{name:'Encuesta de satisfacción',exact:true});await survey.getByRole('radio',{name:'5 de 5: Excelente',exact:true}).check();await survey.getByLabel('Comentario (opcional)',{exact:true}).fill(surveyComment);
    const sent=await mutate('POST','/satisfaction/cases/'+caseId,()=>survey.getByRole('button',{name:'Enviar evaluación',exact:true}).click());surveyPayload=sent.request().postDataJSON();const result=(await sent.json()).survey;surveyId=result.id;assert.equal(result.score,5);assert.equal(result.respondentUsername,clientUsername);assert.equal(result.administratorId,adminId);
    await expect(survey.getByRole('heading',{name:'Gracias por compartir tu opinión',exact:true})).toBeVisible();assert.equal(ok(await api('/satisfaction/cases/'+caseId,'POST',surveyPayload)).survey.id,surveyId);
    assert.equal((await api('/satisfaction/cases/'+caseId,'POST',{...surveyPayload,requestId:randomUUID(),score:1})).status,409);await assertBalances(60,30);await snapshot('06-encuesta-cliente');
    await logout();await login(adminUser,adminPassword);
  });
  await step('Reabrir conserva evaluación; caso abierto no acepta encuesta y retirar publicación la oculta al cliente',async()=>{
    ok(await api('/cases/'+caseId+'/status','POST',{status:'OPEN',reason:'Revisión adicional sin borrar evaluación.'}));
    const open=ok(await api('/cases','POST',{institutionId,title:'Caso abierto sin encuesta '+runId,description:'No elegible mientras esté abierto.',channel:'EMAIL'}));
    const view=ok(await api('/satisfaction/cases/'+caseId));assert.equal(view.survey.id,surveyId);assert.equal(view.caseStatus,'OPEN');
    await navigate('Base de conocimientos');await page.getByLabel('Buscar solución',{exact:true}).fill(articleTitle);await page.getByRole('button',{name:'Buscar',exact:true}).click();await page.getByRole('button',{name:articleTitle,exact:true}).click();
    await page.getByRole('button',{name:'Pasar a borrador',exact:true}).click();await page.getByLabel('Motivo del cambio',{exact:true}).fill('Revisión editorial posterior conserva historial y vínculo.');
    await mutate('POST','/knowledge/'+articleId+'/status',()=>page.getByRole('button',{name:'Confirmar cambio editorial',exact:true}).click());
    await logout();await login(clientUsername,password,'USER');
    assert.equal((await api('/satisfaction/cases/'+open.id,'POST',{requestId:randomUUID(),score:5,comment:'Todavía abierto.'})).status,409);
    assert.equal(ok(await api('/satisfaction/cases/'+caseId,'POST',surveyPayload)).survey.id,surveyId);assert.equal((await api('/knowledge/'+articleId)).status,404);
    assert.equal(ok(await api('/cases/'+caseId+'/knowledge-links')).length,0);await openCase();await expect(page.getByRole('region',{name:'Encuesta de satisfacción',exact:true})).toHaveCount(0);
    await expect(page.getByRole('button',{name:articleTitle,exact:true})).toHaveCount(0);await assertBalances(60,30);
    await logout();await login(adminUser,adminPassword);ok(await api('/cases/'+caseId+'/status','POST',{status:'CLOSED',reason:'Revisión concluida; evaluación original conservada.'}));
  });
  await step('Renovaciones genera aviso dentro del umbral sin duplicados ni correo y conserva lectura',async()=>{
    await navigate('Renovaciones');await expect(page.getByRole('button',{name:'Guardar configuración',exact:true})).toBeEnabled();await page.locator('input[name="renewalThreshold"]').fill('365');
    await page.getByRole('checkbox',{name:'Generar avisos de vencimiento',exact:true}).check();
    const settings=await mutate('PUT','/renewals/settings',()=>page.getByRole('button',{name:'Guardar configuración',exact:true}).click());assert.equal((await settings.json()).daysBeforeExpiry,365);
    await mutate('POST','/renewals/scan',()=>page.getByRole('button',{name:'Revisar vencimientos ahora',exact:true}).click());
    const data=ok(await api('/renewals')),notices=data.notices.filter(item=>item.policyId===policyId);assert.equal(notices.length,1);assert.ok(data.upcoming.some(item=>item.policyId===policyId));
    const second=await mutate('POST','/renewals/scan',()=>page.getByRole('button',{name:'Revisar vencimientos ahora',exact:true}).click());assert.equal((await second.json()).created,0);
    const notice=page.locator('.renewal-notices li').filter({hasText:folio});await mutate('POST','/renewals/notices/'+notices[0].id+'/read',()=>notice.getByRole('button',{name:'Marcar leído',exact:true}).click());
    assert.ok(ok(await api('/renewals')).notices.find(item=>item.id===notices[0].id).readAt);await expect(notice.getByText('Leído',{exact:true})).toBeVisible();await assertBalances(60,30);
  });
  await step('Reporte de satisfacción filtra institución y responsable, muestra respuestas reales y exporta CSV',async()=>{ 
    await navigate('Satisfacción');await page.getByLabel('Respuestas desde',{exact:true}).fill(day());await page.getByLabel('Respuestas hasta',{exact:true}).fill(day());
    await page.locator('select[name="satisfactionInstitution"]').selectOption({label:institutionName});
    const adminOption=page.locator('select[name="satisfactionAdministrator"] option').filter({hasText:ok(await api('/auth/me')).displayName});
    await page.locator('select[name="satisfactionAdministrator"]').selectOption(await adminOption.getAttribute('value'));
    const reportResponse=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname==='/api/satisfaction/reports'&&new URL(r.url()).searchParams.get('institutionId')===String(institutionId));
    await page.getByRole('button',{name:'Consultar satisfacción',exact:true}).click();const report=await(await reportResponse).json();assert.equal(report.samples,1);assert.equal(report.scoreSum,5);assert.equal(Number(report.averageScore),5);assert.equal(report.responses[0].id,surveyId);assert.equal(report.administrators[0].samples,1);
    await expect(page.getByText(surveyComment,{exact:true})).toBeVisible();
    const pending=page.waitForEvent('download');await page.getByRole('button',{name:'Exportar respuestas CSV',exact:true}).click();const file=await pending;assert.match(file.suggestedFilename(),/\.csv$/);const csv=await readFile(await file.path(),'utf8');assert.ok(csv.startsWith('\uFEFFEncuesta,Caso'));assert.ok(csv.includes(surveyComment));assert.ok(csv.includes(clientUsername));
    await snapshot('07-satisfaccion-reporte');await page.setViewportSize({width:390,height:844});await noOverflow();await snapshot('08-satisfaccion-movil');await page.setViewportSize({width:1440,height:1000});
    await assertBalances(60,30);assert.equal((await policy()).payments.length,1);assert.equal(ok(await api('/backups/status')).settings.enabled,false);
    assert.equal(ok(await api('/notifications/config')).configuredEnabled,false);const notices=ok(await api('/notifications'));assert.ok(notices.every(item=>item.attempts===0&&item.sentAt===null&&item.status==='SUPPRESSED'));
    assert.deepEqual(jsErrors,[]);await logout();
  });

  await step('Recibo cliente oculta notas internas y motivo privado de anulación, conserva folio y muestra ANULADO',async()=>{
    await login(adminUser,adminPassword);const privateNote='PAYMENT_PRIVATE_'+runId,privateVoid='VOID_PRIVATE_'+runId,proofReference='RECIBO-PRIVADO-'+runId;
    const payment=ok(await api('/payments','POST',{requestId:randomUUID(),institutionId,reference:proofReference,amount:100,currency:'MXN',paidOn:day(),method:'TRANSFER',emailMinutes:2,remoteMinutes:1,notes:privateNote,policyId:null}));
    await assertBalances(62,31);await navigate('Pagos');await page.getByRole('row').filter({hasText:proofReference}).getByRole('button',{name:'Ver detalle',exact:true}).click();
    const staff=await downloaded(()=>page.getByRole('button',{name:'Descargar recibo PDF',exact:true}).click(),'.pdf','%PDF');assert.ok(pdfTextStreams(staff.bytes).includes(privateNote),'El recibo del personal debe conservar la nota del pago para contrastar privacidad.');
    ok(await api('/payments/'+payment.id+'/void','POST',{reason:privateVoid}));await assertBalances(60,30);
    await logout();await login(clientUsername,password,'USER');await navigate('Finanzas institucionales');
    await page.locator('summary').filter({hasText:'Pago #'+payment.id+' · '+proofReference}).click();
    const client=await downloaded(()=>page.getByRole('button',{name:'Descargar recibo PDF',exact:true}).click(),'.pdf','%PDF'),text=pdfTextStreams(client.bytes);
    assert.equal(client.filename,staff.filename);assert.ok(text.includes('ANULADO'),'El PDF cliente debe indicar la anulación actual.');
    assert.ok(!text.includes(privateNote),'Se filtró la nota privada del pago.');assert.ok(!text.includes(privateVoid),'Se filtró el motivo privado de anulación.');await assertBalances(60,30);await snapshot('09-recibo-cliente-anulado');
    assert.deepEqual(jsErrors,[]);await logout();
  });

} catch(error) {
  console.error(`FAIL ${steps.at(-1)?.name||'Preparación'}: ${error.stack||error}`);await snapshot('workflows-v16-failure').catch(()=>{});process.exitCode=1;
} finally {
  if(process.env.E2E_REPORT_DIR){await mkdir(process.env.E2E_REPORT_DIR,{recursive:true});await writeFile(resolve(process.env.E2E_REPORT_DIR,'workflows-v16-results.json'),JSON.stringify({baseURL,runId,institutionId,policyId,quoteId,requestId,paymentId,caseId,articleId,draftId,surveyId,steps,jsErrors},null,2));}
  await browser.close();console.log(`${steps.filter(item=>item.result==='PASS').length}/${steps.length} verificaciones reales de flujos 1.6 satisfactorias.`);
}
