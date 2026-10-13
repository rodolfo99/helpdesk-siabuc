import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// A running, disposable test environment is required: this suite creates records.
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
const institutionName = `E2E Biblioteca ${runId}`;
const privateInstitutionName = `E2E Otra biblioteca ${runId}`;
const username = `e2e-${runId}`;
const userPassword = randomBytes(18).toString('base64url');
const caseTitle = `Importación MARC E2E ${runId}`;
const internalNote = `Nota confidencial administrativa ${runId}`;
const steps = [];
let institutionId, caseId, privateCaseId;

async function step(name, task) {
  const startedAt = Date.now();
  try { await task(); steps.push({ name, result: 'PASS', milliseconds: Date.now() - startedAt }); console.log(`PASS ${name}`); }
  catch (error) { steps.push({ name, result: 'FAIL', milliseconds: Date.now() - startedAt, error: error.message }); throw error; }
}
async function login(user, password) {
  await page.goto('/');
  await page.getByRole('heading', { name: 'Inicia sesión' }).waitFor();
  await page.getByLabel('Usuario', { exact: true }).fill(user);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Entrar al centro de soporte/ }).click();
  await expect(page.getByRole('heading', { name: 'Tu soporte, al día.' })).toBeVisible();
}
async function logout() {
  await page.getByRole('button', { name: /Cerrar sesión/ }).click();
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
}
async function navigate(name) {
  await page.getByRole('navigation').getByRole('button', { name, exact: true }).click();
}
async function mutate(method, path, action) {
  const pending = page.waitForResponse(response => {
    const pathname = new URL(response.url()).pathname;
    return response.request().method() === method && (typeof path === 'string' ? pathname === '/api' + path : path.test(pathname));
  });
  await action();
  const response = await pending;
  assert.ok([200, 201, 204].includes(response.status()), `${method} ${response.url()} respondió ${response.status()}`);
}
async function api(path, method = 'GET', body) {
  return page.evaluate(async ({ path, method, body }) => {
    const tokenCookie = document.cookie.split('; ').find(cookie => cookie.startsWith('XSRF-TOKEN='));
    const headers = { 'Content-Type': 'application/json' };
    if (tokenCookie) headers['X-XSRF-TOKEN'] = decodeURIComponent(tokenCookie.slice('XSRF-TOKEN='.length));
    const response = await fetch('/api' + path, { method, headers, credentials: 'same-origin', ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    const text = await response.text();
    let result = text;
    try { result = JSON.parse(text); } catch { /* Empty or non-JSON error response. */ }
    return { status: response.status, body: result };
  }, { path, method, body });
}
async function getInstitution() {
  const result = await api('/institutions');
  assert.equal(result.status, 200);
  return result.body.find(item => item.id === institutionId);
}
async function registerEntry(kind, minutes, description, internal = false) {
  await page.getByLabel('Tipo de registro').selectOption(kind);
  await page.getByLabel('Nota interna', { exact: false }).setChecked(internal);
  if (kind !== 'COMMENT') {
    await page.getByLabel('Horas utilizadas').fill(String(Math.floor(minutes / 60)));
    await page.getByLabel('Minutos utilizados').fill(String(minutes % 60));
  }
  await page.getByLabel('Descripción de la atención o seguimiento').fill(description);
  await mutate('POST', `/cases/${caseId}/entries`, () => page.getByRole('button', { name: 'Agregar al historial' }).click());
  await expect(page.locator('.timeline-entry').filter({ hasText: description })).toBeVisible();
  await expect(page.getByRole('status')).toContainText(kind === 'COMMENT' ? 'Comentario agregado' : 'Atención registrada');
}
async function changeStatus(buttonName, reason, resultStatus) {
  await page.getByRole('button', { name: buttonName, exact: true }).click();
  await page.getByLabel('Motivo', { exact: true }).fill(reason);
  await mutate('POST', `/cases/${caseId}/status`, () => page.getByRole('button', { name: 'Confirmar cambio' }).click());
  await expect(page.locator('.case-heading .badge')).toHaveText(resultStatus);
}
async function noOverflow(label) {
  const layout = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert.ok(layout.document <= layout.viewport + 1 && layout.body <= layout.viewport + 1, `${label}: desbordamiento horizontal ${JSON.stringify(layout)}`);
}

try {
  await step('Inicio de sesión y persistencia de sesión administrativa', async () => {
    await login(adminUser, adminPassword);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Tu soporte, al día.' })).toBeVisible();
    await noOverflow('Escritorio/resumen');
  });
  await step('Alta y actualización institucional; 1 h 5 min se almacena como 65 minutos', async () => {
    await navigate('Instituciones');
    await page.getByRole('button', { name: /Nueva institución/ }).click();
    await page.getByLabel('Nombre de la institución').fill(institutionName);
    await page.getByLabel('Persona de contacto').fill('Contacto de prueba');
    await page.getByLabel('Correo electrónico').fill('biblioteca@example.test');
    const emailBudget = page.getByRole('group', { name: /Presupuesto total de correo/ });
    await emailBudget.getByLabel('Horas', { exact: true }).fill('1');
    await emailBudget.getByLabel('Minutos', { exact: true }).fill('5');
    const remoteBudget = page.getByRole('group', { name: /Presupuesto total de remoto/ });
    await remoteBudget.getByLabel('Minutos', { exact: true }).fill('30');
    await mutate('POST', '/institutions', () => page.getByRole('button', { name: 'Guardar institución' }).click());
    await expect(page.getByRole('button', { name: 'Guardar institución' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Institución guardada');
    const created = (await api('/institutions')).body.find(item => item.name === institutionName);
    assert.ok(created?.id); institutionId = created.id;
    assert.equal(created.emailAllocatedMinutes, 65); assert.equal(created.remoteAllocatedMinutes, 30);
    await page.getByRole('row').filter({ hasText: institutionName }).getByRole('button', { name: 'Editar', exact: true }).click();
    await page.getByLabel('Persona de contacto').fill('Contacto actualizado E2E');
    await page.getByLabel('Notas administrativas').fill('Nota privada institucional E2E');
    await mutate('PUT', `/institutions/${institutionId}`, () => page.getByRole('button', { name: 'Guardar institución' }).click());
    await expect(page.getByRole('button', { name: 'Guardar institución' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Institución guardada');
    assert.equal((await getInstitution()).contactName, 'Contacto actualizado E2E');
  });
  await step('Preparación real de una segunda institución para comprobar aislamiento', async () => {
    const other = await api('/institutions', 'POST', { name: privateInstitutionName, contactName: '', email: '', phone: '', notes: '', emailAllocatedMinutes: 20, remoteAllocatedMinutes: 15 });
    assert.ok([200, 201].includes(other.status), JSON.stringify(other));
    const foreignCase = await api('/cases', 'POST', { institutionId: other.body.id, title: `Caso reservado ${runId}`, description: 'Solo visible para la otra institución y administración.', channel: 'EMAIL' });
    assert.ok([200, 201].includes(foreignCase.status), JSON.stringify(foreignCase));
    privateCaseId = foreignCase.body.id;
  });
  await step('Alta de un usuario institucional desde la interfaz', async () => {
    await navigate('Usuarios');
    await page.getByRole('button', { name: /Nuevo usuario/ }).click();
    await page.getByLabel('Nombre visible').fill('Bibliotecario E2E');
    await page.getByLabel('Usuario de acceso').fill(username);
    await page.getByLabel('Institución', { exact: false }).selectOption({ label: institutionName });
    await page.getByLabel('Contraseña inicial', { exact: false }).fill(userPassword);
    await mutate('POST', '/users', () => page.getByRole('button', { name: 'Guardar usuario' }).click());
    await expect(page.getByRole('button', { name: 'Guardar usuario' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Usuario guardado');
    await expect(page.getByRole('row').filter({ hasText: username })).toContainText(institutionName);
  });
  await step('Apertura de caso e historial descriptivo público e interno', async () => {
    await navigate('Casos de soporte');
    await page.getByRole('button', { name: /Nuevo caso/ }).click();
    await page.locator('select[name="institutionId"]').selectOption({ label: institutionName });
    await page.getByLabel('Título del caso').fill(caseTitle);
    await page.getByLabel('Descripción', { exact: true }).fill('Error al importar registros MARC; se requiere revisar formato y codificación.');
    await mutate('POST', '/cases', () => page.getByRole('button', { name: 'Crear caso', exact: true }).click());
    await expect(page.getByRole('heading', { name: caseTitle, exact: true })).toBeVisible();
    const supportCase = (await api('/cases')).body.find(item => item.title === caseTitle);
    assert.ok(supportCase?.id); caseId = supportCase.id;
    await registerEntry('COMMENT', 0, internalNote, true);
    await registerEntry('EMAIL', 20, 'Revisión por correo: formato MARC y codificación comprobados.');
    const balance = await getInstitution();
    assert.equal(balance.emailUsedMinutes, 20); assert.equal(balance.emailRemainingMinutes, 45);
    assert.equal(balance.remoteUsedMinutes, 0); assert.equal(balance.remoteRemainingMinutes, 30);
  });
  await step('Consumo remoto independiente y anulación con devolución sin borrar historial', async () => {
    const description = `Atención remota corregible ${runId}`;
    await registerEntry('REMOTE', 10, description);
    assert.equal((await getInstitution()).remoteRemainingMinutes, 20);
    const entry = page.locator('.timeline-entry').filter({ hasText: description });
    await entry.getByRole('button', { name: 'Anular atención y devolver tiempo' }).click();
    await page.getByLabel('Motivo de la anulación').fill('Corrección documentada del registro de tiempo.');
    await mutate('POST', /\/api\/cases\/\d+\/entries\/\d+\/void$/, () => page.getByRole('button', { name: 'Anular y devolver tiempo', exact: true }).click());
    await expect(entry).toContainText('Anulada · tiempo devuelto');
    assert.equal((await getInstitution()).remoteRemainingMinutes, 30);
    assert.equal((await getInstitution()).emailRemainingMinutes, 45);
  });
  await step('Editar caso, cerrar y reabrir conservando el historial', async () => {
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    await page.getByLabel('Descripción', { exact: true }).fill('Descripción actualizada: registros MARC con problemas de codificación.');
    await mutate('PUT', `/cases/${caseId}`, () => page.getByRole('button', { name: 'Guardar cambios' }).click());
    await expect(page.getByRole('button', { name: 'Guardar cambios' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Caso actualizado');
    await changeStatus('Cerrar caso', 'Solución entregada y probada.', 'Cerrado');
    await expect(page.getByText('Este caso está cerrado', { exact: true })).toBeVisible();
    await changeStatus('Reabrir caso', 'Seguimiento adicional autorizado con saldo disponible.', 'Abierto');
    await expect(page.locator('.timeline-entry').filter({ hasText: internalNote })).toBeVisible();
  });
  await step('Denegación de sobregiro en interfaz y servidor; agotamiento exacto de ambos canales', async () => {
    await page.getByLabel('Tipo de registro').selectOption('EMAIL');
    await page.getByLabel('Minutos utilizados').fill('46');
    await page.getByLabel('Descripción de la atención o seguimiento').fill('Este consumo no debe poder enviarse.');
    await expect(page.getByRole('alert')).toContainText('supera el saldo');
    await expect(page.getByRole('button', { name: 'Agregar al historial' })).toBeDisabled();
    const rejected = await api(`/cases/${caseId}/entries`, 'POST', { description: 'Rechazo servidor: sin saldo suficiente.', channel: 'EMAIL', minutes: 46, internal: false });
    assert.equal(rejected.status, 409, JSON.stringify(rejected));
    assert.equal((await getInstitution()).emailRemainingMinutes, 45);
    await registerEntry('EMAIL', 45, 'Cierre de revisión por correo: pruebas finales y documentación.');
    await page.getByLabel('Tipo de registro').selectOption('EMAIL');
    await expect(page.getByRole('alert')).toContainText('Sin tiempo disponible');
    assert.equal((await getInstitution()).emailRemainingMinutes, 0);
    await registerEntry('REMOTE', 30, 'Sesión remota: revisión y verificación final en el equipo institucional.');
    const balance = await getInstitution();
    assert.equal(balance.remoteRemainingMinutes, 0); assert.equal(balance.emailUsedMinutes, 65); assert.equal(balance.remoteUsedMinutes, 30);
    const detail = await api(`/cases/${caseId}`);
    assert.equal(detail.body.totalMinutes, 95);
  });
  await step('Portal institucional: historial propio, notas internas ocultas y permisos reales', async () => {
    await logout(); await login(username, userPassword);
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Instituciones', exact: true })).toHaveCount(0);
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Usuarios', exact: true })).toHaveCount(0);
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Auditoría', exact: true })).toHaveCount(0);
    await expect(page.getByText(privateInstitutionName, { exact: true })).toHaveCount(0);
    const institutions = await api('/institutions');
    assert.equal(institutions.body.length, 1); assert.equal(institutions.body[0].id, institutionId);
    const cases = await api('/cases');
    assert.ok(cases.body.every(item => item.institutionId === institutionId));
    const foreign = await api(`/cases/${privateCaseId}`);
    assert.ok([403, 404].includes(foreign.status));
    assert.equal((await api('/users')).status, 403);
    assert.equal((await api('/audit')).status, 403);
    assert.equal((await api(`/cases/${caseId}/entries`, 'POST', { description: 'Intento no autorizado de cargar minutos.', channel: 'EMAIL', minutes: 1, internal: false })).status, 403);
    await navigate('Casos de soporte');
    await page.getByRole('button', { name: new RegExp(caseTitle) }).click();
    await expect(page.getByText(internalNote, { exact: true })).toHaveCount(0);
    const visibleDetail = await api(`/cases/${caseId}`);
    assert.ok(visibleDetail.body.entries.every(entry => !entry.internal));
    await expect(page.getByLabel('Tipo de registro')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cerrar caso', exact: true })).toHaveCount(0);
    await page.getByLabel('Tu comentario').fill('Confirmamos recepción de las indicaciones y agradecemos el seguimiento.');
    await mutate('POST', `/cases/${caseId}/entries`, () => page.getByRole('button', { name: 'Agregar al historial' }).click());
    await expect(page.getByRole('status')).toContainText('Comentario agregado');
    assert.equal((await getInstitution()).emailUsedMinutes, 65);
  });
  await step('Vistas de 320, 390 y 768 píxeles sin desbordamiento horizontal', async () => {
    for (const width of [320, 390, 768]) {
      await page.setViewportSize({ width, height: 900 });
      await noOverflow(`Detalle/${width}`);
      await expect(page.getByRole('heading', { name: 'Historial de atención' })).toBeVisible();
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
  });
  await step('El portal deniega nuevos casos cuando el canal está agotado', async () => {
    await page.getByRole('button', { name: /Todos los casos/ }).click();
    await page.getByRole('button', { name: /Nuevo caso/ }).click();
    await page.getByLabel('Título del caso').fill('Solicitud sin saldo');
    await page.getByLabel('Descripción', { exact: true }).fill('Esta solicitud debe quedar bloqueada hasta recargar tiempo.');
    await expect(page.getByText(/No es posible abrir este caso/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Crear caso', exact: true })).toBeDisabled();
    await page.getByLabel('Canal de atención').selectOption('REMOTE');
    await expect(page.getByRole('button', { name: 'Crear caso', exact: true })).toBeDisabled();
    await logout();
  });
  await step('Baja institucional conserva casos y bloquea el acceso de sus usuarios', async () => {
    await login(adminUser, adminPassword);
    await navigate('Instituciones');
    await page.getByRole('row').filter({ hasText: institutionName }).getByRole('button', { name: 'Dar de baja' }).click();
    await mutate('DELETE', `/institutions/${institutionId}`, () => page.getByRole('button', { name: 'Confirmar baja' }).click());
    await expect(page.getByRole('button', { name: 'Confirmar baja' })).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Institución dada de baja');
    assert.equal((await getInstitution()).active, false);
    const preserved = await api(`/cases/${caseId}`);
    assert.equal(preserved.status, 200); assert.equal(preserved.body.totalMinutes, 95);
    assert.ok(preserved.body.entries.some(entry => entry.description === internalNote));
    await logout();
    await page.getByLabel('Usuario', { exact: true }).fill(username);
    await page.getByLabel('Contraseña', { exact: true }).fill(userPassword);
    await page.getByRole('button', { name: /Entrar al centro de soporte/ }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
  });
  await step('Sin excepciones JavaScript en el flujo completo', async () => assert.deepEqual(jsErrors, []));
} catch (error) {
  console.error(`FAIL ${steps.at(-1)?.name || 'Preparación'}: ${error.stack || error}`);
  if (process.env.E2E_REPORT_DIR) {
    await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
    await page.screenshot({ path: resolve(process.env.E2E_REPORT_DIR, 'failure.png'), fullPage: true }).catch(() => {});
  }
  process.exitCode = 1;
} finally {
  if (process.env.E2E_REPORT_DIR) {
    await mkdir(process.env.E2E_REPORT_DIR, { recursive: true });
    await writeFile(resolve(process.env.E2E_REPORT_DIR, 'results.json'), JSON.stringify({ baseURL, runId, institutionId, caseId, steps, jsErrors }, null, 2));
  }
  await browser.close();
  console.log(`${steps.filter(step => step.result === 'PASS').length}/${steps.length} verificaciones satisfactorias.`);
}
