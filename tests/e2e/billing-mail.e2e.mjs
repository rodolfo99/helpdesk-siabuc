import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Independent extension suite. Run only on a disposable database with MAIL_ENABLED=false.
const baseURL = process.env.E2E_BASE_URL || 'http://localhost:4200';
const adminUser = process.env.E2E_ADMIN_USER;
const adminPassword = process.env.E2E_ADMIN_PASSWORD;
if (!adminUser || !adminPassword) throw new Error('Define E2E_ADMIN_USER y E2E_ADMIN_PASSWORD de una instalación de pruebas.');
const moduleRoot = process.env.E2E_PLAYWRIGHT_PATH;
const { chromium } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'index.mjs')).href : 'playwright');
const { expect: baseExpect } = await import(moduleRoot ? pathToFileURL(resolve(moduleRoot, 'test.mjs')).href : 'playwright/test');
const expect = baseExpect.configure({ timeout: 15000 });
const launchOptions = { headless: process.env.E2E_HEADLESS !== 'false' };
if (process.env.E2E_CHROMIUM_PATH) launchOptions.executablePath = process.env.E2E_CHROMIUM_PATH;
if (process.env.E2E_CHROMIUM_ARGS) launchOptions.args = JSON.parse(process.env.E2E_CHROMIUM_ARGS);
const browser = await chromium.launch(launchOptions);
const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(15000);
const jsErrors = [];
page.on('pageerror', error => jsErrors.push(error.message));
const runId = `${Date.now()}-${randomBytes(3).toString('hex')}`;
const institutionName = `E2E Cobranza ${runId}`;
const institutionEmail = `institucion-${runId}@example.test`;
const supportName = `Responsable E2E ${runId}`;
const supportUsername = `admin-${runId}`;
const supportEmail = `responsable-${runId}@example.test`;
const billingUsername = `cobranza-${runId}`;
const billingEmail = `cobranza-${runId}@example.test`;
const clientUsername = `cliente-${runId}`;
const password = randomBytes(18).toString('base64url');
const reference = `PAGO-E2E-${runId}`;
const caseTitle = `Avisos del caso ${runId}`;
const internalNote = `Información reservada del soporte ${runId}`;
const steps = [];
let supportId, institutionId, paymentId, caseId, paymentInput;

async function step(name, task) {
  const startedAt = Date.now();
  try { await task(); steps.push({ name, result: 'PASS', milliseconds: Date.now() - startedAt }); console.log(`PASS ${name}`); }
  catch (error) { steps.push({ name, result: 'FAIL', milliseconds: Date.now() - startedAt, error: error.message }); throw error; }
}
async function login(username, pass, role = 'ADMIN') {
  await page.goto('/');
  await page.getByRole('heading', { name: 'Inicia sesión' }).waitFor();
  await page.getByLabel('Usuario', { exact: true }).fill(username);
  await page.getByLabel('Contraseña', { exact: true }).fill(pass);
  await page.getByRole('button', { name: /Entrar al centro de soporte/ }).click();
  await expect(page.getByRole('heading', { name: role === 'BILLING' ? 'Cobranza, al día.' : 'Tu soporte, al día.' })).toBeVisible();
}
async function logout() {
  await page.getByRole('button', { name: /Cerrar sesión/ }).click();
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
}
async function navigate(name) { await page.getByRole('navigation').getByRole('button', { name, exact: true }).click(); }
async function mutate(method, path, action) {
  const pending = page.waitForResponse(response => response.request().method() === method && new URL(response.url()).pathname === '/api' + path);
  await action();
  const response = await pending;
  assert.ok([200, 201, 204].includes(response.status()), `${method} ${response.url()} respondió ${response.status()}`);
  return response;
}
async function api(path, method = 'GET', body) {
  return page.evaluate(async ({ path, method, body }) => {
    const token = document.cookie.split('; ').find(cookie => cookie.startsWith('XSRF-TOKEN='));
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token.slice('XSRF-TOKEN='.length));
    const response = await fetch('/api' + path, { method, headers, credentials: 'same-origin', ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    const text = await response.text();
    let result = text;
    try { result = JSON.parse(text); } catch { /* Empty/non-JSON response. */ }
    return { status: response.status, body: result };
  }, { path, method, body });
}
async function institutions() { const result = await api('/institutions'); assert.equal(result.status, 200); return result.body; }
async function institution() { return (await institutions()).find(item => item.id === institutionId); }
async function notifications() { const result = await api('/notifications'); assert.equal(result.status, 200); return result.body; }
function ok(result) { assert.ok([200, 201].includes(result.status), JSON.stringify(result)); return result.body; }
function institutionInput(current, changes = {}) {
  const { name, contactName, email, phone, notes, supportAdminId, active, emailAllocatedMinutes, remoteAllocatedMinutes } = current;
  return { name, contactName, email, phone, notes, supportAdminId, active, emailAllocatedMinutes, remoteAllocatedMinutes, ...changes };
}
async function createAccount(username, displayName, email, role) {
  await navigate('Usuarios');
  await page.getByRole('button', { name: /Nuevo usuario/ }).click();
  await page.getByLabel('Nombre visible').fill(displayName);
  await page.getByLabel('Usuario de acceso').fill(username);
  await page.getByLabel('Correo de notificaciones', { exact: false }).fill(email);
  await page.locator('select[name="role"]').selectOption(role);
  await expect(page.locator('select[name="institutionId"]')).toHaveCount(0);
  await page.getByLabel('Contraseña inicial', { exact: false }).fill(password);
  await mutate('POST', '/users', () => page.getByRole('button', { name: 'Guardar usuario' }).click());
  await expect(page.getByRole('button', { name: 'Guardar usuario' })).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('Usuario guardado');
  const user = ok(await api('/users')).find(item => item.username === username);
  assert.equal(user.role, role); assert.equal(user.email, email); assert.equal(user.institutionId, null);
  return user;
}
function assertRecipients(items, expected) {
  assert.deepEqual(items.map(item => item.recipient).sort(), [...expected].sort());
  assert.ok(items.every(item => item.status === 'PENDING' && item.attempts === 0 && item.sentAt === null));
}

try {
  await step('Precondición: correo desactivado y configuración sin secretos', async () => {
    await login(adminUser, adminPassword);
    const config = ok(await api('/notifications/config'));
    assert.equal(config.enabled, false, 'Esta suite requiere MAIL_ENABLED=false; no debe enviar ningún correo.');
    assert.ok(!Object.keys(config).some(key => /password|secret|token/i.test(key)));
  });
  await step('Alta UI de Cobranza y responsable administrativo con correos registrados', async () => {
    const support = await createAccount(supportUsername, supportName, supportEmail, 'ADMIN'); supportId = support.id;
    await createAccount(billingUsername, `Cobranza E2E ${runId}`, billingEmail, 'BILLING');
  });
  await step('Alta institucional UI con contacto y responsable predeterminado', async () => {
    await navigate('Instituciones');
    await page.getByRole('button', { name: /Nueva institución/ }).click();
    await page.getByLabel('Nombre de la institución').fill(institutionName);
    await page.getByLabel('Persona de contacto').fill('Contacto de pruebas de cobranza');
    await page.getByLabel('Correo electrónico', { exact: true }).fill(institutionEmail);
    await page.getByLabel('Responsable de soporte predeterminado', { exact: false }).selectOption({ label: `${supportName} · ${supportEmail}` });
    await page.getByLabel('Notas administrativas').fill(`Nota administrativa no visible a cobranza ${runId}`);
    await mutate('POST', '/institutions', () => page.getByRole('button', { name: 'Guardar institución' }).click());
    await expect(page.getByRole('button', { name: 'Guardar institución' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Institución guardada');
    const created = (await institutions()).find(item => item.name === institutionName); institutionId = created.id;
    assert.equal(created.email, institutionEmail); assert.equal(created.supportAdminId, supportId);
    assert.equal(created.emailAllocatedMinutes, 0); assert.equal(created.remoteAllocatedMinutes, 0);
    ok(await api('/users', 'POST', { username: clientUsername, displayName: 'Cliente de pruebas', email: `cliente-${runId}@example.test`, password, role: 'USER', institutionId, active: true }));
  });
  await step('Cobranza: resumen y pagos, cuenta propia, permisos reales y notas ocultas', async () => {
    await logout(); await login(billingUsername, password, 'BILLING');
    const navigation = (await page.getByRole('navigation').getByRole('button').allTextContents()).map(text => text.trim().replace(/^◫\s*|^▣\s*/g, ''));
    assert.deepEqual(navigation, ['Resumen financiero', 'Pagos']);
    await page.locator('.profile').click();
    await expect(page.getByRole('heading', { name: 'Mi cuenta', exact: true })).toBeVisible();
    await expect(page.getByText(billingEmail, { exact: true })).toBeVisible();
    for (const path of ['/cases', '/users', '/audit', '/notifications', '/notifications/config']) assert.equal((await api(path)).status, 403, path);
    const before = await institution(); assert.equal(before.notes, '');
    const validInstitution = { name: institutionName, contactName: before.contactName, email: institutionEmail, phone: '', notes: '', supportAdminId: supportId, active: true, emailAllocatedMinutes: 0, remoteAllocatedMinutes: 0 };
    assert.equal((await api('/institutions', 'POST', { ...validInstitution, name: `No autorizado ${runId}` })).status, 403);
    assert.equal((await api(`/institutions/${institutionId}`, 'PUT', validInstitution)).status, 403);
    assert.equal((await api(`/institutions/${institutionId}`, 'DELETE')).status, 403);
    assert.equal((await api('/notifications/test', 'POST', {})).status, 403);
    assert.equal((await api('/cases', 'POST', { institutionId, title: 'No permitido', description: 'Cobranza no atiende casos.', channel: 'EMAIL' })).status, 403);
  });
  await step('Pago UI: importe y referencia, acredita 65 minutos de correo y 30 de remoto', async () => {
    await navigate('Pagos');
    await page.getByRole('button', { name: '＋ Registrar pago', exact: true }).click();
    await page.getByLabel('Institución del pago').selectOption({ label: institutionName });
    await page.getByLabel('Fecha del pago').fill('2026-10-13');
    await page.getByLabel('Importe recibido', { exact: false }).fill('1250.50');
    await page.getByLabel('Moneda', { exact: false }).fill('MXN');
    await page.getByLabel('Método de pago').selectOption('TRANSFER');
    await page.getByLabel('Referencia del pago', { exact: false }).fill(reference);
    await page.getByLabel('Descripción del pago (se incluye en el aviso)').fill('Pago recibido por renovación de soporte de correo y escritorio remoto.');
    await page.getByLabel('Horas de correo', { exact: true }).fill('1');
    await page.getByLabel('Minutos de correo', { exact: true }).fill('5');
    await page.getByLabel('Minutos de remoto', { exact: true }).fill('30');
    const response = await mutate('POST', '/payments', () => page.getByRole('button', { name: 'Guardar pago y acreditar tiempo' }).click());
    paymentInput = response.request().postDataJSON();
    await expect(page.getByRole('button', { name: 'Guardar pago y acreditar tiempo' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Pago registrado y tiempo acreditado');
    const payment = ok(await api('/payments')).find(item => item.reference === reference); paymentId = payment.id;
    assert.equal(Number(payment.amount), 1250.5); assert.equal(payment.createdBy, billingUsername);
    assert.equal(payment.emailMinutes, 65); assert.equal(payment.remoteMinutes, 30); assert.equal(payment.voided, false);
    const balance = await institution(); assert.equal(balance.emailRemainingMinutes, 65); assert.equal(balance.remoteRemainingMinutes, 30);
    await expect(page.getByRole('row').filter({ hasText: reference })).toContainText('1 h 5 min');
    await expect(page.getByRole('button', { name: 'Anular', exact: true })).toHaveCount(0);
    assert.equal((await api(`/payments/${paymentId}/void`, 'POST', { reason: 'Cobranza no puede anular.' })).status, 403);
    if (process.env.E2E_REPORT_DIR) {
      await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
      await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, 'payments-desktop.png'), fullPage: true, animations: 'disabled' });
      await page.setViewportSize({ width: 390, height: 844 });
      await expect.poll(() => page.locator('.sidebar').evaluate(element => element.getBoundingClientRect().right)).toBeLessThanOrEqual(1);
      const layout = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
      assert.ok(layout.document <= layout.viewport + 1 && layout.body <= layout.viewport + 1, `Pagos móvil: desbordamiento ${JSON.stringify(layout)}`);
      await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, 'payments-mobile.png'), fullPage: true, animations: 'disabled' });
      await page.setViewportSize({ width: 1440, height: 1000 });
    }
  });
  await step('Idempotencia y referencia única impiden acreditar dos veces', async () => {
    const repeated = ok(await api('/payments', 'POST', paymentInput)); assert.equal(repeated.id, paymentId);
    assert.equal((await api('/payments', 'POST', { ...paymentInput, amount: 1251 })).status, 409);
    assert.equal((await api('/payments', 'POST', { ...paymentInput, requestId: randomUUID(), reference: `  ${reference.toLowerCase()}  ` })).status, 409);
    const ledger = ok(await api('/payments')).filter(item => item.institutionId === institutionId); assert.equal(ledger.length, 1);
    const balance = await institution(); assert.equal(balance.emailRemainingMinutes, 65); assert.equal(balance.remoteRemainingMinutes, 30);
  });
  await step('Administrador consulta el libro de pagos y anula por UI conservando el registro', async () => {
    await logout(); await login(supportUsername, password);
    const paymentNotices = (await notifications()).filter(item => item.paymentId === paymentId && item.eventType === 'CREATE_PAYMENT');
    assertRecipients(paymentNotices, [institutionEmail, billingEmail]);
    await navigate('Pagos');
    const row = page.getByRole('row').filter({ hasText: reference });
    await expect(row).toContainText('Registrado');
    await row.getByRole('button', { name: 'Anular', exact: true }).click();
    await page.getByLabel('Motivo de anulación del pago').fill('Corrección de referencia administrativa; la devolución monetaria se gestiona externamente.');
    await mutate('POST', `/payments/${paymentId}/void`, () => page.getByRole('button', { name: 'Anular pago y retirar tiempo', exact: true }).click());
    await expect(page.getByRole('button', { name: 'Anular pago y retirar tiempo', exact: true })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Pago anulado y acreditación retirada');
    await expect(row).toContainText('Anulado');
    const payment = ok(await api('/payments')).find(item => item.id === paymentId);
    assert.equal(payment.voided, true); assert.equal(payment.voidedBy, supportUsername); assert.ok(payment.voidReason);
    const balance = await institution(); assert.equal(balance.emailRemainingMinutes, 0); assert.equal(balance.remoteRemainingMinutes, 0);
    assert.equal((await api(`/payments/${paymentId}/void`, 'POST', { reason: 'Segundo intento no debe retirar minutos otra vez.' })).status, 409);
    assertRecipients((await notifications()).filter(item => item.paymentId === paymentId && item.eventType === 'VOID_PAYMENT'), [institutionEmail, billingEmail]);
  });
  await step('Responsable institucional aplicado al caso; edición UI conserva asignación', async () => {
    const current = await institution();
    ok(await api(`/institutions/${institutionId}`, 'PUT', institutionInput(current, { emailAllocatedMinutes: 60 })));
    await navigate('Casos de soporte');
    await page.getByRole('button', { name: /Nuevo caso/ }).click();
    await page.locator('select[name="institutionId"]').selectOption({ label: institutionName });
    await expect(page.locator('select[name="assignedAdminId"]')).toBeVisible();
    await page.getByLabel('Título del caso').fill(caseTitle);
    await page.getByLabel('Descripción', { exact: true }).fill('Solicitud para comprobar asignación y notificación del soporte.');
    await mutate('POST', '/cases', () => page.getByRole('button', { name: 'Crear caso', exact: true }).click());
    await expect(page.getByRole('heading', { name: caseTitle, exact: true })).toBeVisible();
    const created = ok(await api('/cases')).find(item => item.title === caseTitle); caseId = created.id;
    assert.equal(created.assignedAdminId, supportId); assert.equal(created.assignedAdminName, supportName);
    assertRecipients((await notifications()).filter(item => item.caseId === caseId && item.eventType === 'CREATE_CASE'), [institutionEmail, supportEmail]);
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    await page.locator('select[name="editAssignedAdminId"]').selectOption({ label: `${supportName} · ${supportEmail}` });
    await mutate('PUT', `/cases/${caseId}`, () => page.getByRole('button', { name: 'Guardar cambios', exact: true }).click());
    await expect(page.getByRole('button', { name: 'Guardar cambios', exact: true })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Caso actualizado');
    assert.equal(ok(await api(`/cases/${caseId}`)).assignedAdminId, supportId);
  });
  await step('Cola de casos: nota interna sólo responsable; cierre avisa al cliente y responsable', async () => {
    const previousIds = new Set((await notifications()).map(item => item.id));
    ok(await api(`/cases/${caseId}/entries`, 'POST', { description: internalNote, channel: null, minutes: 0, internal: true }));
    const internal = (await notifications()).filter(item => !previousIds.has(item.id));
    assert.equal(internal.length, 1); assert.equal(internal[0].eventType, 'CREATE_ENTRY');
    assertRecipients(internal, [supportEmail]);
    const closed = ok(await api(`/cases/${caseId}/status`, 'POST', { status: 'CLOSED', reason: 'Caso resuelto y confirmado por la institución.' }));
    assert.equal(closed.status, 'CLOSED');
    const closedNotices = (await notifications()).filter(item => item.caseId === caseId && item.eventType === 'CHANGE_CASE_STATUS');
    assertRecipients(closedNotices, [institutionEmail, supportEmail]);
    assert.ok(closedNotices.every(item => !JSON.stringify(item).includes(internalNote)));
  });
  await step('Notificaciones UI: SMTP desactivado y prueba exclusivamente al correo propio', async () => {
    await navigate('Notificaciones');
    await expect(page.getByRole('heading', { name: 'Correo y notificaciones', exact: true })).toBeVisible();
    await expect(page.getByText('El envío SMTP está desactivado.', { exact: true })).toBeVisible();
    const previousIds = new Set((await notifications()).map(item => item.id));
    await mutate('POST', '/notifications/test', () => page.getByRole('button', { name: 'Encolar correo de prueba' }).click());
    await expect(page.getByRole('status')).toContainText('registrada para el correo de tu cuenta');
    const created = (await notifications()).filter(item => !previousIds.has(item.id));
    assert.equal(created.length, 1); assert.equal(created[0].eventType, 'TEST_MAIL'); assertRecipients(created, [supportEmail]);
    const rejected = await api('/notifications/test', 'POST', { recipient: 'arbitrario@example.test', subject: 'No permitido' });
    assert.equal(rejected.status, 400);
    assert.equal((await notifications()).filter(item => !previousIds.has(item.id)).length, 1);
    await page.getByLabel('Estado de notificación').selectOption('PENDING');
    await page.getByLabel('Buscar notificaciones').fill(`Caso #${caseId}`);
    await expect(page.getByRole('row').filter({ hasText: 'CHANGE_CASE_STATUS' })).toHaveCount(2);
  });
  await step('Aviso omitido: corrige contacto y reintenta por UI sin enviar SMTP', async () => {
    const skippedInstitution = ok(await api('/institutions', 'POST', { name: `Sin correo ${runId}`, contactName: '', email: '', phone: '', notes: '', supportAdminId: supportId, emailAllocatedMinutes: 1, remoteAllocatedMinutes: 0 }));
    const skippedCase = ok(await api('/cases', 'POST', { institutionId: skippedInstitution.id, title: `Prueba omitida ${runId}`, description: 'El contacto se completará antes del reintento.', channel: 'EMAIL' }));
    const skipped = (await notifications()).find(item => item.caseId === skippedCase.id && item.status === 'SKIPPED'); assert.ok(skipped?.id);
    const correctedEmail = `corregido-${runId}@example.test`;
    ok(await api(`/institutions/${skippedInstitution.id}`, 'PUT', institutionInput(skippedInstitution, { email: correctedEmail, active: true })));
    await navigate('Notificaciones');
    await page.getByRole('button', { name: '↻ Actualizar', exact: true }).click();
    await page.getByLabel('Estado de notificación').selectOption('SKIPPED');
    await page.getByLabel('Buscar notificaciones').fill(`Prueba omitida ${runId}`);
    await mutate('POST', `/notifications/${skipped.id}/retry`, () => page.getByRole('button', { name: `Reintentar notificación ${skipped.id}`, exact: true }).click());
    await expect(page.getByRole('status')).toContainText(`Notificación #${skipped.id} preparada para reintento`);
    const retried = (await notifications()).find(item => item.id === skipped.id); assertRecipients([retried], [correctedEmail]);
    assert.equal((await api(`/notifications/${skipped.id}/retry`, 'POST', {})).status, 409);
    assert.equal(ok(await api('/notifications/config')).enabled, false);
  });
  await step('Portal cliente: oculta notas internas y no permite consultar pagos ni correo', async () => {
    await logout(); await login(clientUsername, password, 'USER');
    const detail = ok(await api(`/cases/${caseId}`));
    assert.equal(detail.status, 'CLOSED'); assert.ok(detail.entries.every(item => !item.internal));
    assert.ok(!JSON.stringify(detail).includes(internalNote));
    for (const path of ['/payments', '/notifications', '/notifications/config']) assert.equal((await api(path)).status, 403, path);
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Pagos', exact: true })).toHaveCount(0);
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Notificaciones', exact: true })).toHaveCount(0);
    assert.deepEqual(jsErrors, []);
    await logout();
  });
} catch (error) {
  console.error(`FAIL ${steps.at(-1)?.name || 'Preparación'}: ${error.stack || error}`);
  if (process.env.E2E_REPORT_DIR) {
    await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
    await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, 'billing-mail-failure.png'), fullPage: true }).catch(() => {});
  }
  process.exitCode = 1;
} finally {
  if (process.env.E2E_REPORT_DIR) {
    await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
    await writeFile(resolve(process.env.E2E_REPORT_DIR, 'billing-mail-results.json'), JSON.stringify({ baseURL, runId, institutionId, paymentId, caseId, steps, jsErrors }, null, 2));
  }
  await browser.close();
  console.log(`${steps.filter(item => item.result === 'PASS').length}/${steps.length} verificaciones de cobranza y correo satisfactorias.`);
}
