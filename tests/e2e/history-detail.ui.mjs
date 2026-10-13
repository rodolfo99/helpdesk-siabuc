// Browser regression tests for the Angular history detail screens, with a simulated API.
// Run after `npm run build` in frontend: node tests/e2e/history-detail.ui.mjs
// These tests exercise navigation/rendering only; backend authorization is tested separately.
// No database, real account, SMTP connection or email is used.
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const build = resolve(project, 'frontend/dist/helpdesk/browser');
await readFile(resolve(build, 'index.html'));
const moduleRoot = process.env.E2E_PLAYWRIGHT_PATH;
const { chromium } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'index.mjs')).href : 'playwright');
const { expect: baseExpect } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'test.mjs')).href : 'playwright/test');
const expect = baseExpect.configure({ timeout: 10000 });
const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer(async (request, response) => {
  try {
    const path = new URL(request.url, 'http://localhost').pathname;
    const file = resolve(build, '.' + (path === '/' ? '/index.html' : decodeURIComponent(path)));
    if (!file.startsWith(build + sep)) throw new Error('Invalid path');
    const body = await readFile(file);
    response.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream' });
    response.end(body);
  } catch { response.writeHead(404); response.end(); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const baseURL = `http://127.0.0.1:${server.address().port}`;
const launchOptions = { headless: true };
if (process.env.E2E_CHROMIUM_PATH) launchOptions.executablePath = process.env.E2E_CHROMIUM_PATH;
if (process.env.E2E_CHROMIUM_ARGS) launchOptions.args = JSON.parse(process.env.E2E_CHROMIUM_ARGS);
const failures = [];
let browser;
let passed = 0;
const payload = '<img src=x onerror="window.historyDetailXss=true">';
const fullText = `Primera línea del registro\nSegunda línea con acentos: institución y atención.\n${payload}\n${'Descripción extensa de los resultados. '.repeat(120)}\nFIN COMPLETO DEL REGISTRO`;
const institution = { id: 1, name: 'Biblioteca Prueba', contactName: 'Persona cliente', email: 'cliente@example.test', phone: '000000', notes: '', supportAdminId: 1, active: true, emailAllocatedMinutes: 180, emailUsedMinutes: 20, emailRemainingMinutes: 160, remoteAllocatedMinutes: 120, remoteUsedMinutes: 45, remoteRemainingMinutes: 75 };
const supportCase = { id: 10, institutionId: 1, institutionName: institution.name, title: 'Restauración del catálogo', description: 'Descripción actual del caso', channel: 'EMAIL', status: 'OPEN', createdBy: 'cliente', createdAt: '2026-10-13T16:00:00Z', updatedAt: '2026-10-13T18:00:00Z', totalMinutes: 65, assignedAdminId: 1, assignedAdminName: 'Administrativo Ejemplo' };
const entries = [
  { id: 101, caseId: 10, description: fullText, channel: 'EMAIL', minutes: 20, internal: false, createdBy: 'administrativo', createdAt: '2026-10-13T16:10:00Z', voided: false, voidReason: null },
  { id: 102, caseId: 10, description: 'Nota reservada para el equipo administrativo', channel: null, minutes: 0, internal: true, createdBy: 'administrativo', createdAt: '2026-10-13T16:20:00Z', voided: false, voidReason: null },
  { id: 103, caseId: 10, description: 'Atención remota anulada', channel: 'REMOTE', minutes: 45, internal: false, createdBy: 'administrativo', createdAt: '2026-10-13T17:00:00Z', voided: true, voidReason: 'Captura duplicada: se devolvieron los 45 minutos.' }
];
const notification = { id: 42, caseId: 10, paymentId: null, eventType: 'CASE_CLOSED', recipient: 'cliente@example.test', subject: 'Aviso completo del catálogo', status: 'SENT', attempts: 2, lastError: null, createdAt: '2026-10-13T18:00:00Z', sentAt: '2026-10-13T18:01:00Z' };
const config = { enabled: true, configuredEnabled: true, paused: false, host: 'smtp', port: 25, username: '', fromAddress: 'soporte@example.test', fromName: 'Soporte SIABUC', security: 'NONE', auth: false, replyTo: '', pendingCount: 0, failedCount: 0, suppressedCount: 0 };

async function scenario(name, action) { await action(); passed++; console.log(`PASS ${name}`); }
async function open({ section = 'notifications', role = 'ADMIN', notices = [notification], detail = {}, detailError = null, caseOverrides = {}, allowCaseCreation = false, beforeClaim = null, claimError = null, beforeDetail = null } = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('pageerror', error => failures.push(error.message));
  const writes = [];
  const detailGets = [];
  let currentCase = { ...supportCase, ...caseOverrides };
  let currentEntries = [...entries];
  await context.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const reply = (body, status = 200) => route.fulfill({ status, json: body });
    if (!['GET', 'HEAD'].includes(request.method())) {
      writes.push({ method: request.method(), path });
      if (path === '/api/notifications/42/retry') return reply({});
      if (path === '/api/cases' && allowCaseCreation) {
        const body = request.postDataJSON();
        writes.at(-1).body = body;
        currentCase = { ...currentCase, ...body, assignedAdminId: null, assignedAdminName: '', createdBy: 'qa' };
        currentEntries = [{ ...entries[0], id: 104, description: 'Caso registrado por el cliente', minutes: 0, channel: null, createdBy: 'qa' }];
        return reply({ ...currentCase, entries: currentEntries });
      }
      if (path === '/api/cases/10/claim' && beforeClaim) {
        await beforeClaim();
        if (claimError) {
          currentCase = { ...currentCase, assignedAdminId: 2, assignedAdminName: 'Otro Administrativo' };
          return reply({ message: claimError }, 409);
        }
        currentCase = { ...currentCase, assignedAdminId: 1, assignedAdminName: 'Administrativo Ejemplo' };
        currentEntries = [...currentEntries, { ...entries[0], id: 104, description: 'Caso tomado por Administrativo Ejemplo', minutes: 0, channel: null, createdBy: 'qa' }];
        return reply({ ...currentCase, entries: currentEntries });
      }
      failures.push(`Unexpected mutation: ${request.method()} ${path}`);
      return reply({ message: 'Unexpected mutation' }, 400);
    }
    
  // v1.6 shell and case sections; this suite keeps its original regression scope.
  if (path === '/api/backups/status') return reply({settings:{enabled:false,dailyTime:'02:00',timeZone:'America/Mexico_City',retentionDays:30,keepLast:3,destinationConfirmed:false,updatedAt:'2026-10-13T18:00:00Z'},worker:{online:false,writable:false,destinationLabel:'',lastError:'',lastSeenAt:null},runs:[],lastFailure:null});
  if (path === '/api/client-finance-access') return reply([]);
  if (/^\/api\/cases\/\d+\/knowledge-links$/.test(path)) return reply([]);
  if (/^\/api\/satisfaction\/cases\/\d+$/.test(path)) return reply({caseId:Number(path.split('/').at(-1)),caseStatus:'CLOSED',canRespond:false,survey:null});
  if (path === '/api/inbox') return reply({ items: [], unreadCount: 0, pendingReplies: [] });
    if (path === '/api/policies') return reply([]);
    if (path === '/api/auth/csrf') return reply({ token: 'test-token' });
    if (path === '/api/auth/me') return reply({ id: 1, username: 'qa', displayName: 'Administrativo Ejemplo', role, email: 'qa@example.test', institutionId: role === 'USER' ? 1 : null });
    if (path === '/api/institutions') return reply([institution]);
    if (path === '/api/case-profiles') return reply([{caseId:10,category:'OTHER',priority:'NORMAL'}]);
    if (path === '/api/cases/10/timer') return reply(null);
    if (path === '/api/cases/10/attachments') return reply([]);
    if (path === '/api/cases') return reply([currentCase]);
    if (path === '/api/cases/10') return reply({ ...currentCase, entries: role === 'ADMIN' ? currentEntries : currentEntries.filter(item => !item.internal) });
    if (path === '/api/users') return reply([{ id: 1, username: 'qa', displayName: 'Administrativo Ejemplo', email: 'qa@example.test', role: 'ADMIN', institutionId: null, active: true }]);
    if (path === '/api/payments') return reply([]);
    if (path === '/api/notifications/config') return reply(config);
    if (path === '/api/notifications') return reply(notices);
    if (/^\/api\/notifications\/\d+$/.test(path)) {
      detailGets.push(path);
      if (beforeDetail) await beforeDetail();
      if (detailError) return reply({ message: detailError.message }, detailError.status);
      const row = notices.find(item => item.id === Number(path.split('/').at(-1)));
      return reply({ ...row, body: fullText, internal: false, recipientType: 'INSTITUTION', recipientEntityId: 1, ...detail });
    }
    failures.push(`Unexpected request: ${request.method()} ${path}`);
    return reply({ message: 'Unexpected request' }, 404);
  });
  await page.goto(baseURL);
  await expect(page.getByRole('navigation')).toBeVisible();
  await page.getByRole('navigation').getByRole('button', { name: section === 'notifications' ? 'Notificaciones' : 'Casos de soporte', exact: true }).click();
  await expect(page.getByRole('heading', { name: section === 'notifications' ? 'Historial de notificaciones' : 'Casos de soporte', exact: true })).toBeVisible();
  return { page, context, writes, detailGets };
}
async function assertPlainText(page) {
  const complete = page.getByText(fullText, { exact: true });
  await expect(complete).toBeVisible();
  assert.equal(await complete.textContent(), fullText);
  assert.equal(await complete.evaluate(element => getComputedStyle(element).whiteSpace), 'pre-wrap');
  await expect(page.locator('main img[src="x"]')).toHaveCount(0);
  assert.equal(await page.evaluate(() => window.historyDetailXss), undefined);
}
async function openEntry(page, id) {
  await page.getByRole('link', { name: `Ver detalle de entrada ${id}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: `Detalle de entrada #${id}`, exact: true })).toBeVisible();
}
async function screenshot(page, name) {
  if (!process.env.E2E_REPORT_DIR) return;
  await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
  await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, name), fullPage: true });
}
async function assertFits(page) {
  const widths = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(widths.document <= widths.viewport + 1, JSON.stringify(widths));
}

try {
  browser = await chromium.launch(launchOptions);
  await scenario('Notificación completa, texto sin interpretar, metadatos y retorno con filtros', async () => {
    const { page, context, writes, detailGets } = await open({ notices: [notification, { ...notification, id: 43, status: 'FAILED', subject: 'Otro aviso' }] });
    await page.getByLabel('Buscar notificaciones').fill('catálogo');
    await page.getByLabel('Estado de notificación').selectOption('SENT');
    const link = page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true });
    await link.scrollIntoViewIfNeeded();
    const oldScroll = await page.evaluate(() => scrollY);
    await page.getByRole('row').filter({ hasText: 'Aviso completo del catálogo' }).getByText('cliente@example.test', { exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Detalle de notificación #42', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historial de notificaciones', exact: true })).toHaveCount(0);
    await assertPlainText(page);
    await expect(page.locator('main')).toContainText('cliente@example.test');
    await expect(page.locator('main')).toContainText('Aviso completo del catálogo');
    await expect(page.locator('main')).toContainText('Caso #10');
    await expect(page.locator('main')).toContainText('CASE_CLOSED');
    await expect(page.locator('main')).toContainText('Enviada');
    await screenshot(page, 'history-detail-notifications-desktop.png');
    await expect(page.getByRole('link', { name: '← Volver al historial', exact: true })).toHaveAttribute('href', /.+/);
    assert.deepEqual(detailGets, ['/api/notifications/42']);
    assert.deepEqual(writes, []);
    await page.getByRole('link', { name: '← Volver al historial', exact: true }).click();
    await expect(page.getByLabel('Buscar notificaciones')).toHaveValue('catálogo');
    await expect(page.getByLabel('Estado de notificación')).toHaveValue('SENT');
    await expect(page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver detalle de notificación 43', exact: true })).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThanOrEqual(oldScroll - 3);
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Teclado abre y vuelve del detalle de notificación', async () => {
    const { page, context, writes } = await open();
    const link = page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true });
    await link.focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Detalle de notificación #42', exact: true })).toBeVisible();
    const back = page.getByRole('link', { name: '← Volver al historial', exact: true });
    await back.focus(); await page.keyboard.press('Enter');
    await expect(link).toBeVisible();
    assert.deepEqual(writes, []);
    await context.close();
  });
  for (const status of [403, 404, 500]) {
    await scenario(`Error ${status} al consultar conserva el historial y sus filtros`, async () => {
      const { page, context, writes } = await open({ detailError: { status, message: 'No es posible consultar este registro.' } });
      await page.getByLabel('Buscar notificaciones').fill('catálogo');
      await page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true }).click();
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page.getByText(fullText, { exact: true })).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Historial de notificaciones', exact: true })).toBeVisible();
      await expect(page.getByLabel('Buscar notificaciones')).toHaveValue('catálogo');
      await expect(page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true })).toBeVisible();
      assert.deepEqual(writes, []);
      await context.close();
    });
  }
  await scenario('Una consulta tardía no altera la pantalla ni el desplazamiento tras cambiar de sección', async () => {
    let release;
    const pending = new Promise(done => { release = done; });
    const { page, context, writes } = await open({ beforeDetail: () => pending });
    await page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true }).click();
    await page.getByRole('navigation').getByRole('button', { name: 'Casos de soporte', exact: true }).click();
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    await page.evaluate(() => { document.documentElement.style.scrollBehavior = 'auto'; scrollTo({ top: 200, behavior: 'instant' }); });
    await expect.poll(() => page.evaluate(() => scrollY)).toBe(200);
    const response = page.waitForResponse(item => new URL(item.url()).pathname === '/api/notifications/42');
    release();
    await response;
    await page.evaluate(() => new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))));
    await expect(page.getByRole('heading', { name: 'Detalle de notificación #42', exact: true })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    assert.equal(await page.evaluate(() => scrollY), 200);
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Reintentar es una acción separada que no abre el detalle', async () => {
    const { page, context, writes, detailGets } = await open({ notices: [{ ...notification, status: 'FAILED', lastError: 'Error SMTP de prueba' }] });
    await page.getByRole('button', { name: 'Reintentar notificación 42', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('preparada para reintento');
    await expect(page.getByRole('heading', { name: 'Historial de notificaciones', exact: true })).toBeVisible();
    assert.deepEqual(writes, [{ method: 'POST', path: '/api/notifications/42/retry' }]);
    assert.deepEqual(detailGets, []);
    await context.close();
  });
  await scenario('Notificación anulada por pausa muestra el motivo y no ofrece reintento', async () => {
    const { page, context, writes } = await open({ notices: [{ ...notification, status: 'SUPPRESSED', attempts: 0, sentAt: null, lastError: 'Suprimida por pausa del envío' }], detail: { internal: true, recipientType: 'USER', recipientEntityId: 8 } });
    await page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Detalle de notificación #42', exact: true })).toBeVisible();
    await expect(page.locator('main')).toContainText('No enviado por desactivación');
    await expect(page.locator('main')).toContainText('Suprimida por pausa del envío');
    await expect(page.locator('main')).toContainText(/Solo administración|Interna|interna/);
    await expect(page.getByRole('button', { name: 'Reintentar notificación 42', exact: true })).toHaveCount(0);
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Entrada pública completa y regreso al mismo caso, con filtros conservados', async () => {
    const { page, context, writes } = await open({ section: 'cases' });
    await page.getByLabel('Buscar casos').fill('catálogo');
    await page.getByRole('combobox').filter({ has: page.locator('option[value="OPEN"]') }).selectOption('OPEN');
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    await page.locator('#case-history-entry-101').getByText('administrativo', { exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Detalle de entrada #101', exact: true })).toBeVisible();
    await assertPlainText(page);
    await expect(page.locator('main')).toContainText('Biblioteca Prueba');
    await expect(page.locator('main')).toContainText('administrativo');
    await expect(page.locator('main')).toContainText('20 min');
    await screenshot(page, 'history-detail-cases-desktop.png');
    await page.getByRole('link', { name: '← Volver al historial', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Restauración del catálogo', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '← Todos los casos', exact: true }).click();
    await expect(page.getByLabel('Buscar casos')).toHaveValue('catálogo');
    await expect(page.getByRole('combobox').filter({ has: page.locator('option[value="OPEN"]') })).toHaveValue('OPEN');
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Teclado abre la entrada y devuelve el foco al enlace del historial', async () => {
    const { page, context, writes } = await open({ section: 'cases' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    const link = page.getByRole('link', { name: 'Ver detalle de entrada 101', exact: true });
    await link.focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Detalle de entrada #101', exact: true })).toBeVisible();
    const back = page.getByRole('link', { name: '← Volver al historial', exact: true });
    await back.focus(); await page.keyboard.press('Enter');
    await expect(link).toBeVisible();
    await expect(link).toBeFocused();
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Administración consulta nota interna y atención anulada con motivo y tiempo', async () => {
    const { page, context, writes } = await open({ section: 'cases' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await openEntry(page, 102);
    await expect(page.locator('main')).toContainText('Nota reservada para el equipo administrativo');
    await expect(page.locator('main')).toContainText('Solo administración');
    await page.getByRole('link', { name: '← Volver al historial', exact: true }).click();
    await openEntry(page, 103);
    await expect(page.locator('main')).toContainText('Captura duplicada: se devolvieron los 45 minutos.');
    await expect(page.locator('main')).toContainText('45 min');
    await expect(page.locator('main')).toContainText(/anulada/i);
    await expect(page.getByRole('button', { name: 'Anular atención y devolver tiempo', exact: true })).toHaveCount(0);
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Cliente abre sus entradas autorizadas y regresa sin controles administrativos', async () => {
    const { page, context, writes } = await open({ section: 'cases', role: 'USER' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await expect(page.getByRole('link', { name: 'Ver detalle de entrada 102', exact: true })).toHaveCount(0);
    await expect(page.getByText('Nota reservada para el equipo administrativo', { exact: true })).toHaveCount(0);
    await openEntry(page, 101);
    await assertPlainText(page);
    await expect(page.getByRole('button', { name: 'Anular atención y devolver tiempo', exact: true })).toHaveCount(0);
    await page.getByRole('link', { name: '← Volver al historial', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    await expect(page.getByText(/Revisa tu bandeja de entrada y la carpeta/)).toBeVisible();
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('Anular atención abre su confirmación sin navegar al detalle', async () => {
    const { page, context, writes } = await open({ section: 'cases' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await page.getByRole('button', { name: 'Anular atención y devolver tiempo', exact: true }).click();
    await expect(page.getByRole('dialog', { name: 'Anular atención', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Detalle de entrada #101', exact: true })).toHaveCount(0);
    await page.getByRole('dialog').getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    assert.deepEqual(writes, []);
    await context.close();
  });
  await scenario('El cliente crea una solicitud sin asignarse un administrativo ni acceder a Tomar caso', async () => {
    const { page, context, writes } = await open({ section: 'cases', role: 'USER', allowCaseCreation: true });
    await page.getByRole('button', { name: '＋ Nuevo caso', exact: true }).click();
    await page.getByLabel('Título del caso', { exact: true }).fill('Ayuda solicitada por el cliente');
    await page.getByLabel('Descripción', { exact: true }).fill('El cliente describe el problema para solicitar atención.');
    await expect(page.getByLabel('Responsable del caso', { exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Crear caso', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Ayuda solicitada por el cliente', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Historial de atención', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Tomar caso', exact: true })).toHaveCount(0);
    assert.equal(writes.length, 1);
    assert.equal(writes[0].path, '/api/cases');
    assert.equal(writes[0].body.assignedAdminId, null);
    assert.equal(writes[0].body.institutionId, 1);
    await context.close();
  });
  await scenario('Tomar caso bloquea envíos duplicados y muestra responsable e historial actualizados', async () => {
    let release;
    const pending = new Promise(done => { release = done; });
    const { page, context, writes } = await open({ section: 'cases', caseOverrides: { assignedAdminId: null, assignedAdminName: '' }, beforeClaim: () => pending });
    await page.getByLabel('Buscar casos').fill('catálogo');
    await page.getByLabel('Asignación', { exact: true }).selectOption({ label: 'Sin responsable' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    const take = page.getByRole('button', { name: 'Tomar caso', exact: true });
    await take.click();
    await expect(take).toBeDisabled();
    assert.equal(writes.filter(item => item.path === '/api/cases/10/claim').length, 1);
    release();
    await expect(page.getByRole('status')).toContainText(/asignado|tomado|responsable/i);
    await expect(page.locator('.case-aside')).toContainText('Administrativo Ejemplo');
    await expect(page.getByText('Caso tomado por Administrativo Ejemplo', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Tomar caso', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: '← Todos los casos', exact: true }).click();
    await expect(page.getByLabel('Buscar casos')).toHaveValue('catálogo');
    await expect(page.getByLabel('Asignación', { exact: true }).locator('option:checked')).toHaveText('Sin responsable');
    await expect(page.getByRole('button', { name: 'Ver caso 10', exact: true })).toHaveCount(0);
    await page.getByLabel('Asignación', { exact: true }).selectOption({ label: 'Mis casos' });
    await expect(page.getByRole('button', { name: 'Ver caso 10', exact: true })).toBeVisible();
    assert.deepEqual(writes, [{ method: 'POST', path: '/api/cases/10/claim' }]);
    await context.close();
  });
  await scenario('Un conflicto al tomar un caso informa el error sin atribuirlo al administrador', async () => {
    const { page, context, writes } = await open({ section: 'cases', caseOverrides: { assignedAdminId: null, assignedAdminName: '' }, beforeClaim: async () => {}, claimError: 'Otro administrativo ya tomó este caso.' });
    await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click();
    await page.getByRole('button', { name: 'Tomar caso', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Otro administrativo ya tomó este caso.');
    await expect(page.locator('.notice.success')).toHaveCount(0);
    await expect(page.getByText('Caso tomado por Administrativo Ejemplo', { exact: true })).toHaveCount(0);
    await expect(page.locator('.case-aside')).not.toContainText('Administrativo Ejemplo');
    await expect(page.locator('.case-aside')).toContainText('Otro Administrativo');
    await expect(page.getByRole('button', { name: 'Tomar caso', exact: true })).toHaveCount(0);
    assert.deepEqual(writes, [{ method: 'POST', path: '/api/cases/10/claim' }]);
    await context.close();
  });
  await scenario('Detalles de correo y caso legibles a 390 píxeles', async () => {
    for (const section of ['notifications', 'cases']) {
      const { page, context, writes } = await open({ section });
      if (section === 'notifications') await page.getByRole('link', { name: 'Ver detalle de notificación 42', exact: true }).click();
      else { await page.getByRole('button', { name: 'Ver caso 10', exact: true }).click(); await openEntry(page, 101); }
      await page.setViewportSize({ width: 390, height: 844 });
      await expect.poll(() => page.locator('.sidebar').evaluate(element => element.getBoundingClientRect().right)).toBeLessThanOrEqual(1);
      await expect(page.getByRole('link', { name: '← Volver al historial', exact: true })).toBeVisible();
      await assertPlainText(page);
      await assertFits(page);
      if (process.env.E2E_REPORT_DIR) {
        await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
        await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, `history-detail-${section}-mobile.png`), fullPage: true });
      }
      assert.deepEqual(writes, []);
      await context.close();
    }
  });
  assert.deepEqual(failures, [], 'No unexpected API calls or JavaScript errors');
  console.log(`${passed} pruebas de detalle de historial aprobadas (API simulada, sin envío de correo).`);
} finally {
  if (browser) await browser.close();
  await new Promise(done => server.close(done));
}
