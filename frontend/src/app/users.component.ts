import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
import { Institution, Role, User } from './models';
import { passwordError } from './time';
interface LoginSettings {maxAttempts:number;lockMinutes:number;revision:number}
interface ClientLock {userId:number;failedAttempts:number;lockedUntil:string|null}
@Component({selector:'app-users',standalone:true,imports:[CommonModule,FormsModule],template:`
<div class="page-heading"><div><div class="eyebrow">CONTROL DE ACCESO</div><h1>Usuarios</h1><p>Administra cuentas, correos de contacto y permisos por institución.</p></div><button class="btn primary" (click)="create()" [disabled]="busy()">＋ Nuevo usuario</button></div>
<section class="panel" style="padding:22px;margin-bottom:20px" aria-label="Bloqueo del portal de clientes">
  <h2>Protección del inicio de sesión de clientes</h2>
  @if(loginSettings();as cfg){
    <form (ngSubmit)="saveSettings()" #settingsForm="ngForm" class="form-grid">
      <label>Máximo de intentos fallidos<input name="maxAttempts" type="number" [(ngModel)]="maxAttempts" min="3" max="10" required></label>
      <label>Duración del bloqueo (minutos)<input name="lockMinutes" type="number" [(ngModel)]="lockMinutes" min="1" max="1440" required></label>
      <div class="span-2"><button class="btn primary" type="submit" [disabled]="busy()||settingsForm.invalid">Guardar configuración</button>
        <p class="muted">Se aplica exclusivamente a cuentas de clientes. El acceso del personal interno se mantiene independiente.</p>
      </div>
    </form>
  } @else {<p>Configuración no disponible. <button class="btn secondary" type="button" (click)="loadSecurity()">Reintentar</button></p>}
</section>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(editing()) {<section class="panel form-panel"><div class="panel-heading"><div><h2>{{editId ? 'Editar usuario' : 'Nuevo usuario'}}</h2><p>{{editId ? 'Solo el administrador puede restablecer contraseñas. Deja el campo vacío para conservarla. Para cambiar el rol o la institución, crea otra cuenta y desactiva esta.' : 'Asigna permisos y una contraseña inicial.'}}</p></div><button class="icon-button" (click)="editing.set(false)" [disabled]="busy()" aria-label="Cerrar formulario">×</button></div><form (ngSubmit)="save()" #userForm="ngForm"><div class="form-grid"><label>Nombre visible<input name="displayName" [(ngModel)]="form.displayName" required maxlength="150"></label><label>Usuario de acceso<input name="username" [(ngModel)]="form.username" [disabled]="!!editId" required maxlength="100" autocomplete="off"></label><label>{{form.role==='USER'?'Correo de contacto del cliente':'Correo de notificaciones'}}<input name="userEmail" [(ngModel)]="form.email" type="email" email maxlength="254"><small>El administrador interno puede actualizar este correo de contacto. No se envían códigos de recuperación ni se modifica automáticamente el correo de la licencia SIABUC9.</small></label><label>Permiso<select name="role" [(ngModel)]="form.role" [disabled]="!!editId"><option value="USER">Usuario de soporte</option><option value="ADMIN">Administrador</option><option value="BILLING">Cobranza</option><option value="TECH">Técnico de soporte</option></select></label><label>Estado<select name="active" [(ngModel)]="form.active"><option [ngValue]="true">Activo</option><option [ngValue]="false">Inactivo</option></select></label>@if(form.role === 'USER'){<label class="span-2">Institución<select name="institutionId" [(ngModel)]="form.institutionId" [disabled]="!!editId && originalInstitution !== null" required><option [ngValue]="null">Selecciona una institución</option>@for(item of institutions();track item.id){<option [ngValue]="item.id">{{item.name}}{{item.active?'':' (inactiva)'}}</option>}</select><small>El historial se conserva vinculado a la institución original. No puede reasignarse.</small></label>}<label class="span-2">{{editId ? 'Restablecer contraseña por administrador (opcional)' : 'Contraseña inicial'}}<input name="password" [(ngModel)]="form.password" type="password" [required]="!editId" autocomplete="new-password"><small>Al menos 12 caracteres. Máximo 72 bytes UTF-8 (los acentos y emojis ocupan más de un byte).</small></label></div><div class="notice info">Los administradores gestionan todas las instituciones y su auditoría. El técnico atiende casos sin administrar usuarios, cobranza ni respaldos. Cobranza registra pagos y consulta saldos. Los usuarios consultan y comentan únicamente los casos de su institución.</div><div class="form-actions"><button class="btn secondary" type="button" (click)="editing.set(false)" [disabled]="busy()">Cancelar</button><button class="btn primary" [disabled]="busy() || userForm.invalid">{{busy()?'Guardando…':'Guardar usuario'}}</button></div></form></section>}
<section class="panel"><div class="toolbar"><label class="search-field"><span aria-hidden="true">⌕</span><input [(ngModel)]="search" placeholder="Buscar usuario, nombre o institución…" aria-label="Buscar usuarios"></label><span class="count-label">{{filtered().length}} usuarios</span></div><div class="table-scroll"><table><thead><tr><th>Usuario</th><th>Institución</th><th>Permisos</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead><tbody>@for(user of filtered();track user.id){<tr><td><strong>{{user.displayName}}</strong><small>{{user.username}}</small></td><td>{{institutionName(user.institutionId)}}</td><td><span class="badge" [class.admin]="user.role==='ADMIN'">{{user.role==='ADMIN'?'Administrador':user.role==='BILLING'?'Cobranza':user.role==='TECH'?'Técnico de soporte':'Usuario'}}</span></td><td><span class="badge" [class.open]="user.active" [class.closed]="!user.active">{{user.active?'Activo':'Inactivo'}}</span></td><td><button class="text-button" (click)="edit(user)" [disabled]="busy()">Editar acceso</button>@if(user.role==='USER'){<div><small>@if(lockInfo(user.id);as state){@if(state.lockedUntil && isLocked(state.lockedUntil)){Bloqueado temporalmente · {{state.lockedUntil | date:'dd/MM/yyyy HH:mm'}}<br>}@else if(state.failedAttempts>0){Intentos fallidos: {{state.failedAttempts}}<br>}}</small><button class="text-button" type="button" (click)="unlock(user.id)" [disabled]="busy()">Desbloquear acceso</button></div>}</td></tr>}@empty{<tr><td colspan="5"><div class="empty-state"><span>♙</span><h3>{{busy()?'Cargando usuarios…':'Sin usuarios para mostrar'}}</h3><p>Modifica la búsqueda o crea una nueva cuenta.</p></div></td></tr>}</tbody></table></div></section>
`})
export class UsersComponent extends PageState implements OnInit {
  private api=inject(ApiService);
  readonly loginSettings=signal<LoginSettings|null>(null);
  readonly locks=signal<ClientLock[]>([]);
  maxAttempts=5;lockMinutes=15;
  lockInfo(id:number){return this.locks().find(l=>l.userId===id);}
  isLocked(value:string){return new Date(value).getTime()>Date.now();}
  loadSecurity(){void this.perform(async()=>{await this.refreshSecurity();});}
  private async refreshSecurity(){
    const [settings,locks]=await Promise.all([this.api.get<LoginSettings>('/auth/client-login-settings'),this.api.get<ClientLock[]>('/auth/client-login-locks')]);
    this.loginSettings.set(settings);this.maxAttempts=settings.maxAttempts;this.lockMinutes=settings.lockMinutes;this.locks.set(locks);
  }
  saveSettings(){void this.perform(async()=>{
    const settings=this.loginSettings();
    if(!settings)throw new Error('Recarga la configuración.');
    const saved=await this.api.put<LoginSettings>('/auth/client-login-settings',{
      maxAttempts:this.maxAttempts,lockMinutes:this.lockMinutes,revision:settings.revision});
    this.loginSettings.set(saved);
    this.success.set('Se actualizaron los límites de acceso del cliente.');
  });}
  unlock(userId:number){void this.perform(async()=>{
    await this.api.post('/auth/client-login-locks/'+userId+'/unlock',{});
    await this.refreshSecurity();
    this.success.set('Se reiniciaron los intentos fallidos y se desbloqueó la cuenta.');
  });}readonly users=signal<User[]>([]);readonly institutions=signal<Institution[]>([]);readonly editing=signal(false);search='';editId:number|null=null;originalInstitution:number|null=null;form=this.blank();
  blank(){return {username:'',displayName:'',email:'',password:'',role:'USER' as Role,institutionId:null as number|null,active:true};}
  ngOnInit(){void this.perform(async()=>{await this.refresh();await this.refreshSecurity();});}async refresh(){const [users,institutions]=await Promise.all([this.api.get<User[]>('/users'),this.api.get<Institution[]>('/institutions')]);this.users.set(users);this.institutions.set(institutions);}
  institutionName(id:number|null){return id===null?'Ámbito general':this.institutions().find(i=>i.id===id)?.name||'Institución #'+id;}
  filtered(){const q=this.search.trim().toLocaleLowerCase();return this.users().filter(u=>`${u.username} ${u.displayName} ${this.institutionName(u.institutionId)}`.toLocaleLowerCase().includes(q));}
  create(){this.editId=null;this.originalInstitution=null;this.form=this.blank();this.editing.set(true);this.error.set('');}
  edit(u:User){this.editId=u.id;this.originalInstitution=u.institutionId;this.form={username:u.username,displayName:u.displayName,email:u.email??'',password:'',role:u.role,institutionId:u.institutionId,active:u.active};this.error.set('');this.editing.set(true);window.scrollTo({top:0,behavior:'smooth'});}
  save(){void this.perform(async()=>{if(!this.form.username.trim()||!this.form.displayName.trim())throw new Error('Completa el nombre visible y el usuario.');if(this.form.password||!this.editId){const error=passwordError(this.form.password);if(error)throw new Error(error);}if(this.form.role==='USER'&&!this.form.institutionId)throw new Error('Selecciona la institución del usuario.');const body={...this.form,username:this.form.username.trim(),displayName:this.form.displayName.trim(),institutionId:this.form.role==='USER'?this.form.institutionId:null};if(this.editId)await this.api.put('/users/'+this.editId,body);else await this.api.post('/users',body);this.editing.set(false);this.form.password='';await this.refresh();await this.refreshSecurity();this.success.set('Usuario guardado. Los permisos de acceso están actualizados.');});}
}
