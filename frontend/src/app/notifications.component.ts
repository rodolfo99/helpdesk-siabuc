import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState, errorMessage } from './api.service';

export type NotificationStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED';

export interface Notification {
  id: number;
  caseId: number | null;
  paymentId: number | null;
  eventType: string;
  recipient: string | null;
  subject: string;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface MailConfig {
  enabled: boolean;
  host: string;
  port: number;
  username: string;
  fromAddress: string;
  fromName: string;
  security: 'STARTTLS' | 'SSL' | 'NONE';
  auth: boolean;
  replyTo: string;
  pendingCount: number;
  failedCount: number;
}

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-heading">
      <div><div class="eyebrow">COMUNICACIÓN</div><h1>Correo y notificaciones</h1><p>Supervisa los avisos de casos y pagos y revisa su entrega.</p></div>
      @if (isAdmin()) {<button class="btn secondary" (click)="load()" [disabled]="busy()">{{busy() ? 'Procesando…' : '↻ Actualizar'}}</button>}
    </div>
    @if (error()) {<div class="notice error" role="alert">{{error()}}</div>}
    @if (success()) {<div class="notice success" role="status">{{success()}}</div>}
    @if (isAdmin()) {
      @if (config(); as mail) {
        <div class="metric-grid">
          <section class="metric"><div class="metric-icon mint" aria-hidden="true">✉</div><div class="metric-label">Envío de correo</div><strong class="small-value">{{mail.enabled ? 'Activado' : 'Desactivado'}}</strong><small>{{mail.enabled ? 'Procesamiento automático de la cola' : 'La cola se conserva sin enviar'}}</small></section>
          <section class="metric"><div class="metric-icon blue" aria-hidden="true">◷</div><div class="metric-label">Pendientes en la cola</div><strong>{{mail.pendingCount}}</strong><small>Conteo general del servidor</small></section>
          <section class="metric"><div class="metric-icon orange" aria-hidden="true">!</div><div class="metric-label">Envíos fallidos</div><strong>{{mail.failedCount}}</strong><small>Revisa su motivo antes de reintentar</small></section>
          <section class="metric"><div class="metric-icon purple" aria-hidden="true">≡</div><div class="metric-label">Notificaciones visibles</div><strong>{{items().length}}</strong><small>Hasta los últimos 500 registros</small></section>
        </div>
        @if (!mail.enabled) {
          <div class="notice info"><b>El envío SMTP está desactivado.</b> Los avisos se conservan en la cola. Las pruebas y los reintentos también quedarán pendientes hasta activar el correo.</div>
        }
        <section class="panel form-panel">
          <div class="panel-heading"><div><h2>Configuración de correo</h2><p>Consulta de los valores actuales del servidor; las contraseñas no se muestran.</p></div><span class="badge" [class.open]="mail.enabled" [class.closed]="!mail.enabled">{{mail.enabled ? 'Activado' : 'Desactivado'}}</span></div>
          <dl class="detail-list form-grid">
            <div><dt>Servidor SMTP</dt><dd>{{mail.host === 'smtp' ? 'SMTP interno (Postfix)' : mail.host || 'Sin configurar'}}</dd></div>
            <div><dt>Puerto</dt><dd>{{mail.port}}</dd></div>
            <div><dt>Seguridad de conexión</dt><dd>{{securityLabel(mail.security)}}</dd></div>
            <div><dt>Autenticación</dt><dd>{{mail.auth ? 'Activada' : 'Desactivada'}}</dd></div>
            <div><dt>Usuario SMTP</dt><dd>{{mail.username || 'Sin configurar'}}</dd></div>
            <div><dt>Remitente</dt><dd>{{mail.fromName || 'Sin nombre'}} · {{mail.fromAddress || 'Sin dirección configurada'}}</dd></div>
            <div class="span-2"><dt>Dirección de respuesta</dt><dd>{{mail.replyTo || 'Sin dirección de respuesta adicional'}}</dd></div>
          </dl>
          <div class="compact-form">
            @if (mail.host === 'smtp') {<div class="notice info">Los avisos aceptados por Postfix pueden seguir pendientes de entrega al destinatario. Consulta la cola y los registros del servicio <code>smtp</code>.</div>}
            <div class="notice info">Para configurar el hostname, el remitente o el relay del Postfix interno, ejecuta <code>./configurar.sh --smtp-interno</code>. Para conectar directamente con un SMTP externo, usa <code>./configurar.sh --correo</code>. Ejecuta el comando en la carpeta del proyecto y reinicia el sistema. Después, pulsa Actualizar para consultar la configuración.</div>
            <p class="muted">La prueba se envía únicamente al correo registrado de tu propia cuenta administrativa. Si falta, regístralo en Usuarios. Abrir esta pantalla o actualizarla no genera mensajes de prueba.</p>
            <div class="form-actions"><button class="btn primary" (click)="test()" [disabled]="busy()">Encolar correo de prueba</button></div>
          </div>
        </section>
      } @else if (busy()) {
        <div class="loading" role="status">Cargando configuración de correo…</div>
      }
      <section class="panel" [attr.aria-busy]="busy()">
        <div class="panel-heading"><div><h2>Historial de notificaciones</h2><p>Últimos 500 registros. Enviada significa que SMTP aceptó el mensaje; no confirma recepción ni lectura.</p></div></div>
        <div class="toolbar">
          <label class="search-field"><span aria-hidden="true">⌕</span><input [(ngModel)]="search" placeholder="Buscar destinatario, asunto, caso o motivo…" aria-label="Buscar notificaciones"></label>
          <label class="filter-field"><span>Estado</span><select [(ngModel)]="statusFilter" aria-label="Estado de notificación"><option value="all">Todos</option><option value="PENDING">Pendientes</option><option value="SENDING">En envío</option><option value="SENT">Enviadas</option><option value="FAILED">Fallidas</option><option value="SKIPPED">Omitidas</option></select></label>
          <span class="count-label">{{filtered().length}} notificaciones</span>
        </div>
        <div class="table-scroll"><table>
          <thead><tr><th>Fecha y referencia</th><th>Destinatario y asunto</th><th>Estado</th><th>Intentos</th><th>Último resultado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
          <tbody>
            @for (item of filtered(); track item.id) {
              <tr>
                <td class="nowrap"><strong>#{{item.id}}</strong>{{item.createdAt | date:'dd/MM/yyyy HH:mm'}}<small>{{reference(item)}}</small></td>
                <td class="audit-details"><strong>{{item.recipient || 'Sin destinatario registrado'}}</strong>{{item.subject}}<small>{{item.eventType}}</small></td>
                <td><span class="badge" [class.open]="item.status === 'SENT'" [class.void]="item.status === 'FAILED'" [class.internal]="item.status === 'SKIPPED'" [class.channel-email]="item.status === 'PENDING' || item.status === 'SENDING'">{{statusLabel(item.status)}}</span></td>
                <td>{{item.attempts}}</td>
                <td class="audit-details">
                  @if (item.lastError) {<span class="preserve-lines">{{item.lastError}}</span>}
                  @if (item.sentAt) {<small>Enviada: {{item.sentAt | date:'dd/MM/yyyy HH:mm:ss'}}</small>}
                  @else if (!item.lastError) {<span>{{item.status === 'PENDING' ? 'En espera de procesamiento' : item.status === 'SENDING' ? 'El servidor está procesando el envío' : item.status === 'SKIPPED' ? 'Revisa el correo del destinatario registrado' : item.status === 'SENT' ? 'Enviada' : 'Sin detalle disponible'}}</span>}
                </td>
                <td class="actions-cell">
                  @if (canRetry(item)) {<button class="text-button" (click)="retry(item)" [disabled]="busy()" [attr.aria-label]="'Reintentar notificación ' + item.id">Reintentar</button>}
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6"><div class="empty-state"><span aria-hidden="true">✉</span><h3>{{busy() ? 'Cargando notificaciones…' : 'Sin notificaciones para mostrar'}}</h3><p>{{search || statusFilter !== 'all' ? 'Ajusta el estado o la búsqueda.' : 'Aquí aparecerán los avisos que generen los casos y los pagos.'}}</p></div></td></tr>
            }
          </tbody>
        </table></div>
        <div class="table-footer">Si una notificación se omitió por falta de correo, corrige el contacto en Instituciones o Usuarios y pulsa Reintentar. Un aviso creado sin responsable no puede recuperarse: asigna uno para los próximos eventos. Las notificaciones enviadas no se pueden reenviar desde aquí.</div>
      </section>
    }
  `
})
export class NotificationsComponent extends PageState implements OnInit {
  private readonly api = inject(ApiService);
  readonly items = signal<Notification[]>([]);
  readonly config = signal<MailConfig | null>(null);
  search = '';
  statusFilter: NotificationStatus | 'all' = 'all';

  ngOnInit() { this.load(); }
  isAdmin() { return this.api.user()?.role === 'ADMIN'; }
  load() {
    void this.perform(async () => {
      this.requireAdmin();
      await this.refresh();
    });
  }
  private requireAdmin() {
    if (!this.isAdmin()) throw new Error('Solo los administrativos pueden consultar y gestionar las notificaciones.');
  }
  private async refresh() {
    const [config, items] = await Promise.all([
      this.api.get<MailConfig>('/notifications/config'),
      this.api.get<Notification[]>('/notifications')
    ]);
    this.config.set(config);
    this.items.set(items);
  }
  private async refreshAfterAction() {
    try { await this.refresh(); }
    catch (error) { this.error.set('La acción se completó, pero no fue posible actualizar la vista. Pulsa Actualizar. ' + errorMessage(error)); }
  }
  filtered() {
    const query = this.search.trim().toLocaleLowerCase();
    return this.items().filter(item =>
      (this.statusFilter === 'all' || item.status === this.statusFilter) &&
      `${item.id} ${item.recipient ?? ''} ${item.subject} ${item.eventType} ${this.reference(item)} ${item.lastError ?? ''} ${this.statusLabel(item.status)}`.toLocaleLowerCase().includes(query)
    );
  }
  statusLabel(status: NotificationStatus) {
    return {PENDING: 'Pendiente', SENDING: 'En envío', SENT: 'Enviada', FAILED: 'Fallida', SKIPPED: 'Omitida'}[status];
  }
  securityLabel(security: MailConfig['security']) {
    return {STARTTLS: 'STARTTLS', SSL: 'TLS / SSL', NONE: 'Sin cifrado'}[security];
  }
  reference(item: Notification) {
    if (item.caseId !== null) return `Caso #${item.caseId}`;
    if (item.paymentId !== null) return `Pago #${item.paymentId}`;
    return 'Sin caso ni pago asociado';
  }
  canRetry(item: Notification) { return item.status === 'FAILED' || item.status === 'SKIPPED'; }
  test() {
    void this.perform(async () => {
      this.requireAdmin();
      const item = await this.api.post<Notification>('/notifications/test', {});
      this.success.set(`Prueba #${item.id} registrada para el correo de tu cuenta. Su estado aparece en la cola; registrarla no confirma su entrega.`);
      await this.refreshAfterAction();
    });
  }
  retry(item: Notification) {
    void this.perform(async () => {
      this.requireAdmin();
      if (!this.canRetry(item)) throw new Error('Solo se pueden reintentar notificaciones fallidas u omitidas.');
      await this.api.post(`/notifications/${item.id}/retry`, {});
      this.success.set(`Notificación #${item.id} preparada para reintento. Consulta su estado en la cola para verificar la entrega.`);
      await this.refreshAfterAction();
    });
  }
}
