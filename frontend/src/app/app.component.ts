import { LicenseDefaultsComponent } from './license-defaults.component';
import { LicensesComponent } from './licenses.component';
import { SlaAlertSettingsComponent } from './sla-alert-settings.component';
import { InstitutionStatementComponent } from './institution-statement.component';
import { BankReconciliationComponent } from './bank-reconciliation.component';
import { InstallmentsComponent } from './installments.component';
import { SupportPackagesComponent } from './support-packages.component';
import { ReplyTemplatesComponent } from './reply-templates.component';
import { IncidentsComponent } from './incidents.component';
import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, PageState, errorMessage } from './api.service';
import { DashboardComponent } from './dashboard.component';
import { InstitutionsComponent } from './institutions.component';
import { UsersComponent } from './users.component';
import { CasesComponent } from './cases.component';
import { AuditComponent } from './audit.component';
import { AccountComponent } from './account.component';
import { PaymentsComponent } from './payments.component';
import { BillingDashboardComponent } from './billing-dashboard.component';
import { NotificationsComponent } from './notifications.component';
import { NotificationBellComponent } from './notification-bell.component';
import { BalanceAlertSettingsComponent } from './balance-alert-settings.component';
import { SupportSettingsComponent } from './support-settings.component';
import { ReportsComponent } from './reports.component';
import { CaseOpenRequest } from './cases.component';
import { PoliciesComponent } from './policies.component';
import { BillingReportsComponent } from './billing-reports.component';
import { PaymentOpenRequest } from './models';
import { QuotesComponent } from './quotes.component';
import { RenewalsComponent } from './renewals.component';
import { PaymentRequestsComponent } from './payment-requests.component';
import { ClientFinanceComponent } from './client-finance.component';
import { ClientInformationComponent } from './client-information.component';
import { FinanceAccessComponent } from './finance-access.component';
import { KnowledgeBaseComponent } from './knowledge-base.component';
import { SatisfactionReportsComponent } from './satisfaction-reports.component';
import { BackupStatusComponent, BackupAlertBannerComponent } from './backup-status.component';
type Page = 'licenses'|'statements'|'reconciliation'|'installments'|'packages'|'replyTemplates'|'incidents'|'dashboard'|'institutions'|'users'|'cases'|'audit'|'account'|'payments'|'notifications'|'settings'|'reports'|'policies'|'billingReports'|'quotes'|'renewals'|'paymentRequests'|'clientFinance'|'clientInformation'|'knowledge'|'satisfaction'|'backups';
@Component({selector:'app-root',standalone:true,imports:[LicensesComponent,LicenseDefaultsComponent,IncidentsComponent,ReplyTemplatesComponent,SupportPackagesComponent,InstallmentsComponent,BankReconciliationComponent,InstitutionStatementComponent,SlaAlertSettingsComponent,CommonModule,FormsModule,DashboardComponent,InstitutionsComponent,UsersComponent,CasesComponent,AuditComponent,AccountComponent,PaymentsComponent,BillingDashboardComponent,NotificationsComponent,NotificationBellComponent,BalanceAlertSettingsComponent,SupportSettingsComponent,ReportsComponent,PoliciesComponent,BillingReportsComponent,QuotesComponent,RenewalsComponent,PaymentRequestsComponent,ClientFinanceComponent,ClientInformationComponent,FinanceAccessComponent,KnowledgeBaseComponent,SatisfactionReportsComponent,BackupStatusComponent,BackupAlertBannerComponent],template:`
@if (restoring()) { <main class="boot"><div class="brand-mark">S</div><p>Conectando con tu centro de soporte…</p><span class="spinner"></span></main> }
@else if (!api.user()) {
  <main class="login-layout">
    <section class="login-story">
      <div class="brand"><span class="brand-mark">S</span><span>SIABUC <small>{{staffPortal ? 'Centro de soporte · Personal interno' : 'Portal de clientes'}}</small></span></div>
      <div class="story-content">
        <div class="eyebrow light">{{staffPortal ? 'CENTRO SIABUC · ACCESO INTERNO' : 'BIENVENIDO A TU INSTITUCIÓN'}}</div>
        <h1>{{staffPortal ? 'Gestión y atención de soporte.' : 'Tu soporte SIABUC, en un solo lugar.'}}</h1>
        <p>{{staffPortal ? 'Acceso exclusivo para personal técnico, cobranza y administración del Centro SIABUC.' : 'Consulta las solicitudes, el seguimiento de tus casos y el tiempo disponible de soporte.'}}</p>
        <div class="story-items"><div><b>01</b><span>Solicitudes con seguimiento</span></div><div><b>02</b><span>Historial de atención</span></div><div><b>03</b><span>{{staffPortal ? 'Administración según tu rol' : 'Información de tu institución'}}</span></div></div>
      </div>
      <div class="story-footer">Universidad de Colima · Centro SIABUC</div>
    </section>
    <section class="login-area"><form class="login-card" (ngSubmit)="login()">
      <div class="eyebrow">{{staffPortal ? 'ACCESO DE PERSONAL' : 'ACCESO DE CLIENTES'}}</div>
      <h2>{{staffPortal ? 'Iniciar sesión de personal' : 'Iniciar sesión de cliente'}}</h2>
      <p class="muted">{{staffPortal ? 'Ingresa con tu cuenta interna SIABUC. La Federación de Identidades UCOL todavía no está integrada.' : 'Ingresa con tu cuenta SIABUC actual. El acceso por número de póliza y correo electrónico se habilitará en otra etapa.'}}</p>
      @if (error()) {<div class="notice error" role="alert">{{error()}}</div>}
      <label>Usuario<input name="username" [(ngModel)]="username" required autocomplete="username" maxlength="100" placeholder="Tu usuario SIABUC"></label>
      <label>Contraseña<input name="password" [(ngModel)]="password" type="password" required autocomplete="current-password" placeholder="Tu contraseña"></label>
      <button class="btn primary full" [disabled]="busy() || !username.trim() || !password">{{busy() ? 'Verificando…' : 'Iniciar sesión'}} <span aria-hidden="true">→</span></button>
      @if(!staffPortal){
        <div class="login-contacts" aria-label="Contacto SIABUC">
          <h3>Soporte técnico SIABUC</h3>
          <p>Lunes a viernes, 8:00 a 16:00 hrs.</p>
          <p><a href="tel:+523123161000">312 316 1000, ext. 49007</a> · <a href="tel:+523123161052">312 316 1052</a></p>
          <p><a href="mailto:siabuc@ucol.mx">siabuc@ucol.mx</a></p>
          <h3>Comercialización</h3>
          <p>Lunes a viernes, 8:00 a 15:00 hrs. · Teléfono temporalmente suspendido</p>
          <p><a href="mailto:siabucservicios@ucol.mx">siabucservicios@ucol.mx</a></p>
          <p><a href="https://siabuc.ucol.mx" target="_blank" rel="noopener noreferrer">Sitio oficial SIABUC ↗</a></p>
        </div>
      }
      <p class="login-help">¿Necesitas acceso? Comunícate con el administrador de soporte SIABUC.</p>
      <p class="login-help">@if(staffPortal){¿Eres cliente? <a href="/poliza">Ir al portal de clientes</a>}@else{¿Atiendes o administras el sistema? <a href="/poliza/admin">Acceso para personal SIABUC</a>}</p>
    </form></section>
  </main>
} @else {
  <div class="app-shell"><aside class="sidebar" [class.mobile-open]="menuOpen()"><div class="brand"><span class="brand-mark">S</span><span>SIABUC<small>Centro de soporte</small></span></div><div class="workspace-label">{{api.user()?.role === 'ADMIN' ? 'ADMINISTRACIÓN' : api.user()?.role === 'BILLING' ? 'COBRANZA' : api.user()?.role === 'TECH' ? 'SOPORTE TÉCNICO' : 'MI INSTITUCIÓN'}}</div><nav aria-label="Navegación principal">@if(api.user()?.role === 'USER'){<button [class.selected]="page() === 'clientInformation'" (click)="navigate('clientInformation')"><span aria-hidden="true">▧</span> Mi información SIABUC</button>}<button [class.selected]="page() === 'licenses'" (click)="navigate('licenses')"><span aria-hidden="true">▧</span> Licencias</button>
    <button [class.selected]="page() === 'dashboard'" (click)="navigate('dashboard')"><span aria-hidden="true">◫</span> {{api.user()?.role === 'BILLING' ? 'Resumen financiero' : 'Resumen'}}</button>
    @if(api.user()?.role !== 'BILLING'){<button [class.selected]="page() === 'cases'" (click)="navigate('cases')"><span aria-hidden="true">▤</span> Casos de soporte</button>}
    @if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'payments'" (click)="navigate('payments')"><span aria-hidden="true">▣</span> Pagos</button><button [class.selected]="page() === 'policies'" (click)="navigate('policies')"><span aria-hidden="true">▧</span> Pólizas</button><button [class.selected]="page() === 'billingReports'" (click)="navigate('billingReports')"><span aria-hidden="true">▥</span> Reportes de cobranza</button>}
    @if(api.user()?.role !== 'BILLING'){<button [class.selected]="page() === 'knowledge'" (click)="navigate('knowledge')"><span aria-hidden="true">▤</span> Base de conocimientos</button>}
    @if(api.user()?.role === 'USER'){<button [class.selected]="page() === 'clientFinance'" (click)="navigate('clientFinance')"><span aria-hidden="true">▣</span> Finanzas institucionales</button>}
    @if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'quotes'" (click)="navigate('quotes')"><span aria-hidden="true">▧</span> Cotizaciones</button><button [class.selected]="page() === 'paymentRequests'" (click)="navigate('paymentRequests')"><span aria-hidden="true">✓</span> Validar pagos</button><button [class.selected]="page() === 'renewals'" (click)="navigate('renewals')"><span aria-hidden="true">◷</span> Renovaciones</button>}
    @if(api.user()?.role === 'ADMIN'){<button [class.selected]="page() === 'satisfaction'" (click)="navigate('satisfaction')"><span aria-hidden="true">☆</span> Satisfacción</button><button [class.selected]="page() === 'backups'" (click)="navigate('backups')"><span aria-hidden="true">▣</span> Respaldos</button>}
    @if (api.user()?.role === 'ADMIN') {<button [class.selected]="page() === 'institutions'" (click)="navigate('institutions')"><span aria-hidden="true">▦</span> Instituciones</button><button [class.selected]="page() === 'users'" (click)="navigate('users')"><span aria-hidden="true">♙</span> Usuarios</button><button [class.selected]="page() === 'audit'" (click)="navigate('audit')"><span aria-hidden="true">◷</span> Auditoría</button><button [class.selected]="page() === 'notifications'" (click)="navigate('notifications')"><span aria-hidden="true">✉</span> Notificaciones</button><button [class.selected]="page() === 'reports'" (click)="navigate('reports')"><span aria-hidden="true">▥</span> Reportes</button><button [class.selected]="page() === 'settings'" (click)="navigate('settings')"><span aria-hidden="true">⚙</span> Configuración de soporte</button>}
  @if(api.user()?.role === 'ADMIN' || api.user()?.role === 'TECH'){<button [class.selected]="page() === 'incidents'" (click)="navigate('incidents')"><span aria-hidden="true">▤</span> Incidencias generales</button>}@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'TECH'){<button [class.selected]="page() === 'replyTemplates'" (click)="navigate('replyTemplates')"><span aria-hidden="true">▤</span> Plantillas</button>}@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'packages'" (click)="navigate('packages')"><span aria-hidden="true">▤</span> Paquetes y tarifas</button>}@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'installments'" (click)="navigate('installments')"><span aria-hidden="true">▤</span> Parcialidades</button>}@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'reconciliation'" (click)="navigate('reconciliation')"><span aria-hidden="true">▤</span> Conciliación bancaria</button>}@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'statements'" (click)="navigate('statements')"><span aria-hidden="true">▤</span> Estados de cuenta</button>}</nav><div class="sidebar-bottom"><div class="side-note"><span class="status-dot"></span> Cada minuto cuenta<p>Correo y escritorio remoto tienen saldos independientes.</p></div><button class="profile" (click)="navigate('account')"><span class="avatar">{{api.user()?.displayName?.slice(0,1)?.toUpperCase()}}</span><span><b>{{api.user()?.displayName}}</b><small>{{api.user()?.role === 'ADMIN' ? 'Administrador' : api.user()?.role === 'BILLING' ? 'Cobranza' : api.user()?.role === 'TECH' ? 'Técnico de soporte' : 'Usuario de soporte'}}</small></span></button><button class="logout" [disabled]="busy()" (click)="logout()">Cerrar sesión ↗</button></div></aside>
  <div class="workspace"><header class="topbar"><button class="mobile-menu" (click)="menuOpen.set(!menuOpen())" aria-label="Abrir navegación" [attr.aria-expanded]="menuOpen()">☰</button><div class="breadcrumb">Centro de soporte <span>/</span> <b>{{pageTitle()}}</b></div>@if(api.user()?.role === 'USER'){<button type="button" class="btn secondary" (click)="showGuide()">Primeros pasos</button><button type="button" class="btn secondary" (click)="navigate('knowledge')">Centro de ayuda</button>}<app-notification-bell [user]="api.user()" (openCase)="openNotificationCase($event)"/><span class="role-pill">{{api.user()?.role === 'ADMIN' ? 'Administración' : api.user()?.role === 'BILLING' ? 'Portal de cobranza' : api.user()?.role === 'TECH' ? 'Soporte técnico' : 'Portal de atención'}}</span></header>
    <main class="main-content">@if(api.user()?.role === 'ADMIN'){<app-backup-alert-banner (openBackups)="navigate('backups')"/>}@if (error()) {<div class="notice error" role="alert">{{error()}}</div>}
      @switch(page()) { @case('licenses'){<app-licenses/>} @case('statements'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-institution-statement/>}} @case('reconciliation'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-bank-reconciliation/>}} @case('installments'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-installments/>}} @case('packages'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-support-packages/>}} @case('replyTemplates'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'TECH'){<app-reply-templates/>}} @case('incidents'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'TECH'){<app-incidents (openCase)="openNotificationCase($event)"/>}} @case('dashboard'){@if(api.user()?.role === 'BILLING'){<app-billing-dashboard (openPayments)="navigate('payments')" (openPolicies)="navigate('policies')" (openReports)="navigate('billingReports')"/>}@else{<app-dashboard [guideOpenRequest]="guideOpenRequest()" (openCases)="navigate('cases')" (openCase)="openNotificationCase($event)" (newCase)="openNewCase()" (guideDestination)="guideNavigate($event)"/>}} @case('institutions'){<app-institutions/>} @case('users'){<app-users/>} @case('cases'){@if(api.user()?.role !== 'BILLING'){<app-cases [openRequest]="caseOpenRequest()"/>}} @case('audit'){<app-audit/>} @case('account'){<app-account/>} @case('payments'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-payments [openRequest]="paymentOpenRequest()"/>}} @case('policies'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-policies (openPayment)="openPolicyPayment($event)"/>}} @case('billingReports'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-billing-reports/>}} @case('quotes'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-quotes/>}} @case('renewals'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-renewals (openQuotes)="navigate('quotes')"/>}} @case('paymentRequests'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-payment-requests/>}} @case('clientFinance'){@if(api.user()?.role === 'USER'){<app-client-finance/>}} @case('clientInformation'){@if(api.user()?.role === 'USER'){<app-client-information/>}} @case('knowledge'){@if(api.user()?.role !== 'BILLING'){<app-knowledge-base/>}} @case('satisfaction'){@if(api.user()?.role === 'ADMIN'){<app-satisfaction-reports/>}} @case('backups'){@if(api.user()?.role === 'ADMIN'){<app-backup-status/>}} @case('notifications'){@if(api.user()?.role === 'ADMIN'){<app-notifications/>}} @case('reports'){@if(api.user()?.role === 'ADMIN'){<app-reports/>}} @case('settings'){@if(api.user()?.role === 'ADMIN'){<div class="page-heading"><div><div class="eyebrow">ADMINISTRACIÓN</div><h1>Configuración de soporte</h1><p>Horas incluidas de licencias, horario laboral, plazos de atención y avisos de saldo disponible.</p></div></div><div class="support-settings-stack"><app-license-defaults/><app-support-settings/><app-sla-alert-settings/><app-balance-alert-settings/><app-finance-access/></div>}} }
    </main><footer class="app-footer">SIABUC · Centro de soporte técnico <span>Historial y tiempo de atención en un solo lugar</span></footer>
  </div></div>
}`,styles:[`.support-settings-stack{display:grid;gap:24px}.sidebar{overflow-y:auto}.sidebar nav{flex-shrink:0}.login-contacts{border-top:1px solid var(--line);padding-top:17px;margin-top:24px;color:#40596b;font-size:12px;line-height:1.55}.login-contacts h3{font-size:13px;margin:12px 0 5px;color:#203a4d}.login-contacts p{margin:4px 0}.login-contacts a{color:#166276;text-decoration:underline}.login-area{align-items:flex-start;overflow:auto}.login-card{margin:auto 0}`]})
export class AppComponent extends PageState implements OnInit {
  readonly api = inject(ApiService); readonly restoring = signal(true); readonly menuOpen = signal(false); readonly page = signal<Page>('dashboard');
  // The portal is selected by the public URL; this is presentation only.
  // The backend independently checks the role on every authenticated request.
  readonly staffPortal = ['/poliza/admin', '/poliza/admin/'].includes(window.location.pathname);
  private portalPath(role: string) { return role === 'USER' ? '/poliza' : '/poliza/admin'; }
  private correctPortal(role: string) {
    const expected = this.portalPath(role);
    if (window.location.pathname !== expected) {
      window.location.replace(expected);
      return false;
    }
    return true;
  }
  readonly caseOpenRequest=signal<CaseOpenRequest|null>(null);
  private caseRequestSequence=0;
  readonly guideOpenRequest=signal(0);
  readonly paymentOpenRequest=signal<PaymentOpenRequest|null>(null);
  private paymentRequestSequence=0;
  username=''; password='';
  async ngOnInit() { try {await this.api.restore(); const role=this.api.user()?.role; if(role)this.correctPortal(role);} catch(e) {if (!(e instanceof HttpErrorResponse && e.status === 401)) this.error.set(errorMessage(e));} finally {this.restoring.set(false);} }
  navigate(page:Page) {const role=this.api.user()?.role;if(role==='BILLING'&&!['licenses','dashboard','payments','policies','billingReports','quotes','renewals','paymentRequests','packages','installments','reconciliation','statements','account'].includes(page))return;if(role==='TECH'&&!['licenses','dashboard','cases','knowledge','incidents','replyTemplates','account'].includes(page))return;if(role==='USER'&&!['licenses','dashboard','cases','knowledge','clientFinance','clientInformation','account'].includes(page))return;this.caseOpenRequest.set(null);this.paymentOpenRequest.set(null);this.page.set(page);this.menuOpen.set(false);this.error.set('');}
  showGuide(){if(this.api.user()?.role!=='USER')return;this.navigate('dashboard');this.guideOpenRequest.update(n=>n+1);}
  guideNavigate(destination:'cases'|'newCase'|'clientInformation'|'knowledge'){if(this.api.user()?.role!=='USER')return;if(destination==='newCase')this.openNewCase();else this.navigate(destination);}
  openNewCase(){if(this.api.user()?.role!=='USER')return;this.navigate('cases');this.caseOpenRequest.set({create:true,sequence:++this.caseRequestSequence});}
  openNotificationCase(caseId:number){if(this.api.user()?.role==='BILLING'||!Number.isSafeInteger(caseId)||caseId<=0)return;this.navigate('cases');this.caseOpenRequest.set({caseId,sequence:++this.caseRequestSequence});}
  openPolicyPayment(policyId:number){if(!['ADMIN','BILLING'].includes(this.api.user()?.role??'')||!Number.isSafeInteger(policyId)||policyId<=0)return;this.navigate('payments');this.paymentOpenRequest.set({policyId,sequence:++this.paymentRequestSequence});}
  pageTitle() {return {licenses:'Licencias',statements:'Estados de cuenta',reconciliation:'Conciliación bancaria',installments:'Parcialidades',packages:'Paquetes y tarifas',replyTemplates:'Plantillas',incidents:'Incidencias generales',dashboard:'Resumen',cases:'Casos de soporte',institutions:'Instituciones',users:'Usuarios',audit:'Auditoría',account:'Mi cuenta',payments:'Pagos',notifications:'Correo y notificaciones',settings:'Configuración de soporte',reports:'Reportes',policies:'Pólizas',billingReports:'Reportes de cobranza',quotes:'Cotizaciones',renewals:'Renovaciones',paymentRequests:'Validar pagos',clientFinance:'Finanzas institucionales',clientInformation:'Mi información SIABUC',knowledge:'Base de conocimientos',satisfaction:'Satisfacción',backups:'Respaldos'}[this.page()];}
  login() {void this.perform(async()=>{await this.api.login(this.username.trim(),this.password,this.staffPortal?'staff':'client');this.password='';const role=this.api.user()?.role;if(role&&!this.correctPortal(role))return;this.navigate('dashboard');});}
  logout() {void this.perform(async()=>{await this.api.logout();this.password='';this.caseOpenRequest.set(null);this.paymentOpenRequest.set(null);this.page.set('dashboard');});}
}
