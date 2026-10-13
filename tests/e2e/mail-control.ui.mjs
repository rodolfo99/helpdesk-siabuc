// Browser regression tests for the Angular mail controls, with a simulated API.
// Requires `npm run build` in frontend. No database, SMTP connection or email is used.
// Run: node tests/e2e/mail-control.ui.mjs
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
const contentTypes = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = createServer(async (request, response) => {
  try {
    const path = new URL(request.url, 'http://localhost').pathname;
    const file = resolve(build, '.' + (path === '/' ? '/index.html' : decodeURIComponent(path)));
    if (!file.startsWith(build + sep)) throw new Error('Invalid path');
    const body = await readFile(file);
    response.writeHead(200, { 'Content-Type': contentTypes[extname(file)] || 'application/octet-stream' });
    response.end(body);
  } catch { response.writeHead(404); response.end(); }
});
await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
const baseURL = `http://127.0.0.1:${server.address().port}`;
const launchOptions = { headless: true };
if (process.env.E2E_CHROMIUM_PATH) launchOptions.executablePath = process.env.E2E_CHROMIUM_PATH;
if (process.env.E2E_CHROMIUM_ARGS) launchOptions.args = JSON.parse(process.env.E2E_CHROMIUM_ARGS);
let browser;
const failures = [];
let passed = 0;
const initialConfig = {
  enabled: true, configuredEnabled: true, paused: false, host: 'smtp', port: 25,
  username: '', fromAddress: 'soporte@example.test', fromName: 'Soporte SIABUC',
  security: 'NONE', auth: false, replyTo: '', pendingCount: 3, failedCount: 0
};

async function scenario(name, action) {
  await action(); passed++; console.log(`PASS ${name}`);
}
async function open({ role = 'ADMIN', config = {}, notifications = [], onPost } = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('pageerror', error => failures.push(error.message));
  let current = { ...initialConfig, ...config };
  let items = notifications;
  const posts = [];
  await context.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const reply = (body, status = 200) => route.fulfill({ status, json: body });
    if (path === '/api/inbox') return reply({ items: [], unreadCount: 0, pendingReplies: [] });
    if (path === '/api/auth/csrf') return reply({ token: 'test-token' });
    if (path === '/api/auth/me') return reply({ id: 1, username: 'qa', displayName: 'Prueba', role, email: 'qa@example.test', institutionId: role === 'USER' ? 1 : null });
    if (['/api/institutions', '/api/cases', '/api/payments'].includes(path)) return reply([]);
    if (path === '/api/notifications') return reply(items);
    if (path === '/api/notifications/config') {
      if (request.method() === 'POST') {
        const body = request.postDataJSON(); posts.push(body);
        if (onPost) {
          const failure = await onPost(body);
          if (failure) return reply({ message: failure }, 500);
        }
        current = { ...current, paused: body.paused, enabled: current.configuredEnabled && !body.paused };
        if (body.paused) {
          current = { ...current, pendingCount: 0, failedCount: 0 };
          items = items.map(item => ['PENDING', 'FAILED', 'SKIPPED'].includes(item.status) ? { ...item, status: 'SUPPRESSED' } : item);
        }
      }
      return reply(current);
    }
    failures.push(`Unexpected request: ${request.method()} ${path}`);
    return reply({ message: 'Unexpected request' }, 404);
  });
  await page.goto(baseURL);
  await expect(page.getByRole('navigation')).toBeVisible();
  if (role === 'ADMIN') {
    await page.getByRole('navigation').getByRole('button', { name: 'Notificaciones', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Control de envío', exact: true })).toBeVisible();
  }
  return { page, context, posts };
}

try {
  browser = await chromium.launch(launchOptions);
  await scenario('Pausa confirmada, bloqueo mientras guarda y conservación del estado al recargar', async () => {
    let release;
    const pending = new Promise(resolvePost => { release = resolvePost; });
    const { page, context, posts } = await open({ onPost: () => pending });
    const pause = page.getByRole('button', { name: 'Pausar envío de correos', exact: true });
    await pause.click();
    const confirmation = page.getByRole('region', { name: 'Confirmar pausa del correo' });
    await expect(confirmation).toContainText('3 pendientes');
    await expect(confirmation).toContainText('No podrán reintentarse ni se enviarán al reactivar');
    assert.equal(posts.length, 0);
    await confirmation.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(confirmation).toHaveCount(0);
    assert.equal(posts.length, 0);
    await pause.click();
    await confirmation.getByRole('button', { name: 'Confirmar pausa', exact: true }).click();
    await expect(pause).toBeDisabled();
    release();
    await expect(page.getByRole('status')).toContainText('Envío de correos en pausa');
    assert.deepEqual(posts, [{ paused: true }]);
    await expect(page.getByText('El envío de correos está en pausa.', { exact: true })).toBeVisible();
    await expect(page.getByText(/Los mensajes que ya estén en curso/)).toBeVisible();
    await page.reload();
    await page.getByRole('navigation').getByRole('button', { name: 'Notificaciones', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Reactivar envío de correos', exact: true })).toBeEnabled();
    assert.equal(posts.length, 1);
    await context.close();
  });
  await scenario('Reactivación informa que sólo enviará avisos nuevos', async () => {
    const { page, context, posts } = await open({ config: { enabled: false, paused: true } });
    await page.getByRole('button', { name: 'Reactivar envío de correos', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Sólo se enviarán los avisos nuevos');
    assert.deepEqual(posts, [{ paused: false }]);
    await expect(page.getByRole('button', { name: 'Pausar envío de correos', exact: true })).toBeEnabled();
    await context.close();
  });
  for (const paused of [false, true]) {
    await scenario(`MAIL_ENABLED=false bloquea la reactivación (pausa=${paused})`, async () => {
      const { page, context, posts } = await open({ config: { configuredEnabled: false, enabled: false, paused } });
      await expect(page.getByRole('button', { name: 'Encolar correo de prueba', exact: true })).toBeDisabled();
      await expect(page.getByText('El correo está desactivado en el servidor.', { exact: true })).toBeVisible();
      assert.equal(posts.length, 0);
      if (!paused) {
        await page.getByRole('button', { name: 'Pausar envío de correos', exact: true }).click();
        await page.getByRole('button', { name: 'Confirmar pausa', exact: true }).click();
        await expect(page.getByRole('status')).toContainText('Envío de correos en pausa');
        assert.deepEqual(posts, [{ paused: true }]);
      }
      await expect(page.getByRole('button', { name: 'Reactivar envío de correos', exact: true })).toBeDisabled();
      await context.close();
    });
  }
  await scenario('Un error al guardar no cambia el estado ni muestra éxito', async () => {
    const { page, context } = await open({ onPost: () => 'No se pudo guardar la pausa.' });
    await page.getByRole('button', { name: 'Pausar envío de correos', exact: true }).click();
    await page.getByRole('button', { name: 'Confirmar pausa', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('No se pudo guardar la pausa');
    await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Pausar envío de correos', exact: true })).toBeEnabled();
    await expect(page.locator('.notice.success')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Reactivar envío de correos', exact: true })).toHaveCount(0);
    await context.close();
  });
  for (const role of ['BILLING', 'USER']) {
    await scenario(`${role} no tiene acceso al control en la interfaz`, async () => {
      const { page, context, posts } = await open({ role });
      await expect(page.getByRole('navigation').getByRole('button', { name: 'Notificaciones', exact: true })).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Control de envío', exact: true })).toHaveCount(0);
      assert.equal(posts.length, 0);
      await context.close();
    });
  }
  await scenario('SUPPRESSED se muestra, filtra y no permite reintento después de reactivar', async () => {
    const notification = { id: 1, caseId: 10, paymentId: null, eventType: 'CASE_UPDATED', recipient: 'cliente@example.test', subject: 'Aviso suspendido', status: 'SUPPRESSED', attempts: 0, lastError: null, createdAt: '2026-10-13T18:00:00Z', sentAt: null };
    const { page, context } = await open({ config: { enabled: false, paused: true }, notifications: [notification] });
    await expect(page.getByRole('button', { name: 'Encolar correo de prueba', exact: true })).toBeDisabled();
    await page.getByLabel('Estado de notificación').selectOption('SUPPRESSED');
    await expect(page.getByRole('row').filter({ hasText: 'Aviso suspendido' })).toContainText('No enviado por desactivación');
    await expect(page.getByRole('button', { name: 'Reintentar notificación 1', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Reactivar envío de correos', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Encolar correo de prueba', exact: true })).toBeEnabled();
    await expect(page.getByRole('row').filter({ hasText: 'Aviso suspendido' })).toContainText('Registro histórico: no se enviará ni podrá reintentarse');
    await expect(page.getByRole('button', { name: 'Reintentar notificación 1', exact: true })).toHaveCount(0);
    await context.close();
  });
  await scenario('Reintentos deshabilitados ante datos de la lista anteriores a la pausa', async () => {
    const notification = { id: 2, caseId: 10, paymentId: null, eventType: 'CASE_UPDATED', recipient: 'cliente@example.test', subject: 'Aviso fallido', status: 'FAILED', attempts: 3, lastError: 'Fallo de prueba', createdAt: '2026-10-13T18:00:00Z', sentAt: null };
    const { page, context, posts } = await open({ config: { enabled: false, paused: true }, notifications: [notification] });
    await expect(page.getByRole('button', { name: 'Reintentar notificación 2', exact: true })).toBeDisabled();
    assert.equal(posts.length, 0);
    await context.close();
  });
  await scenario('Confirmación de pausa usable a 390 píxeles', async () => {
    const { page, context } = await open();
    await page.setViewportSize({ width: 390, height: 844 });
    await expect.poll(() => page.locator('.sidebar').evaluate(element => element.getBoundingClientRect().right)).toBeLessThanOrEqual(1);
    await page.getByRole('button', { name: 'Pausar envío de correos', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Confirmar pausa', exact: true })).toBeVisible();
    if (process.env.E2E_REPORT_DIR) {
      await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
      await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, 'mail-control-mobile.png'), fullPage: true });
    }
    const widths = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth,
      overflowing: [...document.querySelectorAll('main *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1).slice(0, 12).map(element => `${element.tagName}.${element.className}`) }));
    assert.ok(widths.document <= widths.viewport + 1, JSON.stringify(widths));
    await context.close();
  });
  assert.deepEqual(failures, [], 'No unexpected API calls or JavaScript errors');
  console.log(`${passed} pruebas de interfaz aprobadas (API simulada, sin envío de correo).`);
} finally {
  if (browser) await browser.close();
  await new Promise(resolveClose => server.close(resolveClose));
}
