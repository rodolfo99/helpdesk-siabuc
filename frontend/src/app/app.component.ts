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
type Page = 'dashboard'|'institutions'|'users'|'cases'|'audit'|'account'|'payments'|'notifications'|'settings'|'reports'|'policies'|'billingReports';
@Component({selector:'app-root',standalone:true,imports:[CommonModule,FormsModule,DashboardComponent,InstitutionsComponent,UsersComponent,CasesComponent,AuditComponent,AccountComponent,PaymentsComponent,BillingDashboardComponent,NotificationsComponent,NotificationBellComponent,BalanceAlertSettingsComponent,SupportSettingsComponent,ReportsComponent,PoliciesComponent,BillingReportsComponent],template:`
@if (restoring()) { <main class="boot"><div class="brand-mark">S</div><p>Conectando con tu centro de soporte…</p><span class="spinner"></span></main> }
@else if (!api.user()) {
  <main class="login-layout">
    <section class="login-story"><div class="brand"><span class="brand-mark">S</span><span>SIABUC <small>Centro de soporte</small></span></div><div class="story-content"><div class="eyebrow light">SIEMPRE CERCA DE TU INSTITUCIÓN</div><h1>La atención que<br>da continuidad.</h1><p>Un solo espacio para tus solicitudes, el seguimiento de cada caso y el tiempo disponible de soporte.</p><div class="story-items"><div><b>01</b><span>Solicitudes con seguimiento</span></div><div><b>02</b><span>Historial de cada atención</span></div><div><b>03</b><span>Tiempo claro, minuto a minuto</span></div></div></div><div class="story-footer">Gestión de soporte técnico · SIABUC</div></section>
    <section class="login-area"><form class="login-card" (ngSubmit)="login()"><div class="eyebrow">BIENVENIDO</div><h2>Inicia sesión</h2><p class="muted">Ingresa con la cuenta asignada a tu institución o a tu equipo administrativo.</p>
      @if (error()) {<div class="notice error" role="alert">{{error()}}</div>}
      <label>Usuario<input name="username" [(ngModel)]="username" required autocomplete="username" maxlength="100" placeholder="Tu nombre de usuario"></label>
      <label>Contraseña<input name="password" [(ngModel)]="password" type="password" required autocomplete="current-password" placeholder="Tu contraseña"></label>
      <button class="btn primary full" [disabled]="busy() || !username.trim() || !password">{{busy() ? 'Verificando…' : 'Entrar al centro de soporte'}} <span aria-hidden="true">→</span></button><p class="login-help">¿Necesitas acceso? Solicita una cuenta al administrador de soporte SIABUC.</p></form></section>
  </main>
} @else {
  <div class="app-shell"><aside class="sidebar" [class.mobile-open]="menuOpen()"><div class="brand"><span class="brand-mark">S</span><span>SIABUC<small>Centro de soporte</small></span></div><div class="workspace-label">{{api.user()?.role === 'ADMIN' ? 'ADMINISTRACIÓN' : api.user()?.role === 'BILLING' ? 'COBRANZA' : 'MI INSTITUCIÓN'}}</div><nav aria-label="Navegación principal">
    <button [class.selected]="page() === 'dashboard'" (click)="navigate('dashboard')"><span aria-hidden="true">◫</span> {{api.user()?.role === 'BILLING' ? 'Resumen financiero' : 'Resumen'}}</button>
    @if(api.user()?.role !== 'BILLING'){<button [class.selected]="page() === 'cases'" (click)="navigate('cases')"><span aria-hidden="true">▤</span> Casos de soporte</button>}
    @if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<button [class.selected]="page() === 'payments'" (click)="navigate('payments')"><span aria-hidden="true">▣</span> Pagos</button><button [class.selected]="page() === 'policies'" (click)="navigate('policies')"><span aria-hidden="true">▧</span> Pólizas</button><button [class.selected]="page() === 'billingReports'" (click)="navigate('billingReports')"><span aria-hidden="true">▥</span> Reportes de cobranza</button>}
    @if (api.user()?.role === 'ADMIN') {<button [class.selected]="page() === 'institutions'" (click)="navigate('institutions')"><span aria-hidden="true">▦</span> Instituciones</button><button [class.selected]="page() === 'users'" (click)="navigate('users')"><span aria-hidden="true">♙</span> Usuarios</button><button [class.selected]="page() === 'audit'" (click)="navigate('audit')"><span aria-hidden="true">◷</span> Auditoría</button><button [class.selected]="page() === 'notifications'" (click)="navigate('notifications')"><span aria-hidden="true">✉</span> Notificaciones</button><button [class.selected]="page() === 'reports'" (click)="navigate('reports')"><span aria-hidden="true">▥</span> Reportes</button><button [class.selected]="page() === 'settings'" (click)="navigate('settings')"><span aria-hidden="true">⚙</span> Configuración de soporte</button>}
  </nav><div class="sidebar-bottom"><div class="side-note"><span class="status-dot"></span> Cada minuto cuenta<p>Correo y escritorio remoto tienen saldos independientes.</p></div><button class="profile" (click)="navigate('account')"><span class="avatar">{{api.user()?.displayName?.slice(0,1)?.toUpperCase()}}</span><span><b>{{api.user()?.displayName}}</b><small>{{api.user()?.role === 'ADMIN' ? 'Administrador' : api.user()?.role === 'BILLING' ? 'Cobranza' : 'Usuario de soporte'}}</small></span></button><button class="logout" [disabled]="busy()" (click)="logout()">Cerrar sesión ↗</button></div></aside>
  <div class="workspace"><header class="topbar"><button class="mobile-menu" (click)="menuOpen.set(!menuOpen())" aria-label="Abrir navegación" [attr.aria-expanded]="menuOpen()">☰</button><div class="breadcrumb">Centro de soporte <span>/</span> <b>{{pageTitle()}}</b></div><app-notification-bell [user]="api.user()" (openCase)="openNotificationCase($event)"/><span class="role-pill">{{api.user()?.role === 'ADMIN' ? 'Administración' : api.user()?.role === 'BILLING' ? 'Portal de cobranza' : 'Portal de atención'}}</span></header>
    <main class="main-content">@if (error()) {<div class="notice error" role="alert">{{error()}}</div>}
      @switch(page()) { @case('dashboard'){@if(api.user()?.role === 'BILLING'){<app-billing-dashboard (openPayments)="navigate('payments')" (openPolicies)="navigate('policies')" (openReports)="navigate('billingReports')"/>}@else{<app-dashboard (openCases)="navigate('cases')"/>}} @case('institutions'){<app-institutions/>} @case('users'){<app-users/>} @case('cases'){@if(api.user()?.role !== 'BILLING'){<app-cases [openRequest]="caseOpenRequest()"/>}} @case('audit'){<app-audit/>} @case('account'){<app-account/>} @case('payments'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-payments [openRequest]="paymentOpenRequest()"/>}} @case('policies'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-policies (openPayment)="openPolicyPayment($event)"/>}} @case('billingReports'){@if(api.user()?.role === 'ADMIN' || api.user()?.role === 'BILLING'){<app-billing-reports/>}} @case('notifications'){@if(api.user()?.role === 'ADMIN'){<app-notifications/>}} @case('reports'){@if(api.user()?.role === 'ADMIN'){<app-reports/>}} @case('settings'){@if(api.user()?.role === 'ADMIN'){<div class="page-heading"><div><div class="eyebrow">ADMINISTRACIÓN</div><h1>Configuración de soporte</h1><p>Horario laboral, plazos de atención y avisos de saldo disponible.</p></div></div><div class="support-settings-stack"><app-support-settings/><app-balance-alert-settings/></div>}} }
    </main><footer class="app-footer">SIABUC · Centro de soporte técnico <span>Historial y tiempo de atención en un solo lugar</span></footer>
  </div></div>
}`,styles:[`.support-settings-stack{display:grid;gap:24px}.sidebar{overflow-y:auto}.sidebar nav{flex-shrink:0}`]})
export class AppComponent extends PageState implements OnInit {
  readonly api = inject(ApiService); readonly restoring = signal(true); readonly menuOpen = signal(false); readonly page = signal<Page>('dashboard');
  readonly caseOpenRequest=signal<CaseOpenRequest|null>(null);
  private caseRequestSequence=0;
  readonly paymentOpenRequest=signal<PaymentOpenRequest|null>(null);
  private paymentRequestSequence=0;
  username=''; password='';
  async ngOnInit() { try {await this.api.restore();} catch(e) {if (!(e instanceof HttpErrorResponse && e.status === 401)) this.error.set(errorMessage(e));} finally {this.restoring.set(false);} }
  navigate(page:Page) {const role=this.api.user()?.role;if(role==='BILLING'&&!['dashboard','payments','policies','billingReports','account'].includes(page))return;if(role==='USER'&&!['dashboard','cases','account'].includes(page))return;this.caseOpenRequest.set(null);this.paymentOpenRequest.set(null);this.page.set(page);this.menuOpen.set(false);this.error.set('');}
  openNotificationCase(caseId:number){if(this.api.user()?.role==='BILLING'||!Number.isSafeInteger(caseId)||caseId<=0)return;this.navigate('cases');this.caseOpenRequest.set({caseId,sequence:++this.caseRequestSequence});}
  openPolicyPayment(policyId:number){if(!['ADMIN','BILLING'].includes(this.api.user()?.role??'')||!Number.isSafeInteger(policyId)||policyId<=0)return;this.navigate('payments');this.paymentOpenRequest.set({policyId,sequence:++this.paymentRequestSequence});}
  pageTitle() {return {dashboard:'Resumen',cases:'Casos de soporte',institutions:'Instituciones',users:'Usuarios',audit:'Auditoría',account:'Mi cuenta',payments:'Pagos',notifications:'Correo y notificaciones',settings:'Configuración de soporte',reports:'Reportes',policies:'Pólizas',billingReports:'Reportes de cobranza'}[this.page()];}
  login() {void this.perform(async()=>{await this.api.login(this.username.trim(),this.password);this.password='';this.navigate('dashboard');});}
  logout() {void this.perform(async()=>{await this.api.logout();this.password='';this.caseOpenRequest.set(null);this.paymentOpenRequest.set(null);this.page.set('dashboard');});}
}
