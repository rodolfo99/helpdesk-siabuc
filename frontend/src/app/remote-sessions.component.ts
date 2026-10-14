import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState, errorMessage } from './api.service';

interface RemoteSession {id:number;caseId:number;adminId:number;adminName:string;startsAt:string;endsAt:string;note:string;status:'PROPOSED'|'CONFIRMED'|'DECLINED'|'CANCELLED'|'COMPLETED';response:string;respondedBy:string|null;replacesId:number|null;}
@Component({selector:'app-remote-sessions',standalone:true,imports:[FormsModule,DatePipe],styles:[`
  :host{display:block}.agenda-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:.75rem}.session-card{padding:1rem;border:1px solid var(--line,#dce3eb);border-radius:12px;margin:1rem 0}.session-card p{white-space:pre-wrap;overflow-wrap:anywhere}.agenda-actions{display:flex;gap:.5rem;flex-wrap:wrap}.agenda-form{padding:1rem;background:var(--surface,#fff);border:1px solid var(--line,#dce3eb);border-radius:12px;margin:1rem 0}small{display:block;margin:.4rem 0}
`],template:`
<section aria-label="Agenda de sesiones remotas"><h3>Agenda de sesiones remotas</h3><p>Horarios en tu zona: {{timezone}}. Agendar o confirmar no descuenta minutos.</p><small>Describe el objetivo de la sesión. No escribas contraseñas ni claves de acceso remoto.</small>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(isSupportStaff()&&caseStatus==='OPEN'){
<form class="agenda-form" (ngSubmit)="propose()" #agendaForm="ngForm"><h4>{{rescheduleId?'Reprogramar sesión #'+rescheduleId:'Proponer una sesión'}}</h4><p>Responsable de la sesión: {{api.user()?.displayName}}</p>
<div class="agenda-grid"><label>Inicio<input name="sessionStart" type="datetime-local" required [(ngModel)]="startsAt"></label><label>Fin<input name="sessionEnd" type="datetime-local" required [(ngModel)]="endsAt"></label></div>
<label>Objetivo y preparativos<textarea name="sessionNote" required maxlength="5000" rows="3" [(ngModel)]="note"></textarea></label>
@if(rescheduleId){<p>La sesión anterior quedará cancelada y el cliente deberá confirmar la nueva propuesta.</p>}
<div class="agenda-actions"><button class="btn primary" [disabled]="busy()||agendaForm.invalid">{{rescheduleId?'Guardar nueva propuesta':'Proponer al cliente'}}</button>@if(rescheduleId){<button type="button" class="btn" (click)="clearForm()">Cancelar cambio</button>}</div></form>}
<button type="button" class="btn" [disabled]="busy()" (click)="reload()">Actualizar agenda</button>
@for(s of sessions();track s.id){<article class="session-card"><h4>Sesión #{{s.id}} · {{label(s.status)}}</h4><p><strong>{{s.startsAt|date:'dd/MM/yyyy HH:mm'}} – {{s.endsAt|date:'dd/MM/yyyy HH:mm'}}</strong></p><p>Responsable: {{s.adminName}}</p><p>{{s.note}}</p>
@if(s.replacesId){<small>Sustituye la sesión #{{s.replacesId}}.</small>}@if(s.response){<p>Respuesta{{s.respondedBy?' de '+s.respondedBy:''}}: {{s.response}}</p>}
@if((api.user()?.role==='USER'&&s.status==='PROPOSED'&&caseStatus==='OPEN')||(canManage(s)&&(s.status==='PROPOSED'||s.status==='CONFIRMED'))){
<label>Comentario o motivo<textarea [name]="'response'+s.id" maxlength="2000" [(ngModel)]="responses[s.id]"></textarea></label><div class="agenda-actions">
@if(api.user()?.role==='USER'){<button type="button" class="btn primary" [disabled]="busy()" (click)="respond(s,'confirm')">Confirmar horario</button><button type="button" class="btn" [disabled]="busy()" (click)="respond(s,'decline')">Rechazar propuesta</button>}
@else {<button type="button" class="btn" [disabled]="busy()" (click)="respond(s,'cancel')">Cancelar sesión</button>@if(caseStatus==='OPEN'){<button type="button" class="btn" [disabled]="busy()" (click)="edit(s)">Reprogramar</button>}@if(s.status==='CONFIRMED'){<button type="button" class="btn" [disabled]="busy()" (click)="respond(s,'complete')">Marcar realizada</button>}}
</div>}
</article>}@empty{<p>No hay sesiones remotas programadas para este caso.</p>}
</section>`})
export class RemoteSessionsComponent extends PageState implements OnChanges {
  readonly api=inject(ApiService);@Input({required:true})caseId=0;@Input()caseStatus:'OPEN'|'CLOSED'='OPEN';@Output()updated=new EventEmitter<void>();
  readonly sessions=signal<RemoteSession[]>([]);readonly timezone=Intl.DateTimeFormat().resolvedOptions().timeZone;
  startsAt='';endsAt='';note='';rescheduleId:number|null=null;responses:Record<number,string>={};private requestVersion=0;
  ngOnChanges(changes:Record<string,unknown>){if(changes['caseId']){this.requestVersion++;this.sessions.set([]);this.clearForm();this.responses={};this.reload();}}
  reload(){const version=++this.requestVersion;this.busy.set(true);this.error.set('');this.success.set('');void this.api.get<RemoteSession[]>('/cases/'+this.caseId+'/remote-sessions').then(rows=>{if(version===this.requestVersion)this.sessions.set(rows);}).catch(e=>{if(version===this.requestVersion)this.error.set(errorMessage(e));}).finally(()=>{if(version===this.requestVersion)this.busy.set(false);});}
  clearForm(){this.startsAt='';this.endsAt='';this.note='';this.rescheduleId=null;}
  edit(s:RemoteSession){this.rescheduleId=s.id;this.startsAt=this.local(s.startsAt);this.endsAt=this.local(s.endsAt);this.note=s.note;this.success.set('Ajusta el horario en el formulario y guarda la nueva propuesta.');}
  private local(value:string){const date=new Date(value);return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);}
  propose(){const version=this.requestVersion,caseId=this.caseId;void this.perform(async()=>{const start=new Date(this.startsAt),end=new Date(this.endsAt);if(!Number.isFinite(start.getTime())||!Number.isFinite(end.getTime())||end<=start)throw new Error('Indica un inicio y fin válidos.');const path=this.rescheduleId?'/remote-sessions/'+this.rescheduleId+'/reschedule':'/cases/'+caseId+'/remote-sessions';await this.api.post(path,{startsAt:start.toISOString(),endsAt:end.toISOString(),note:this.note,adminId:this.api.user()?.id});const rows=await this.api.get<RemoteSession[]>('/cases/'+caseId+'/remote-sessions');if(version!==this.requestVersion)return;this.clearForm();this.sessions.set(rows);this.success.set('Propuesta registrada. El cliente puede confirmar desde su cuenta.');this.updated.emit();});}
  respond(s:RemoteSession,action:'confirm'|'decline'|'cancel'|'complete'){const version=this.requestVersion,caseId=this.caseId;void this.perform(async()=>{const response=(this.responses[s.id]??'').trim();if(action!=='confirm'&&!response)throw new Error('Escribe un comentario o motivo para continuar.');await this.api.post('/remote-sessions/'+s.id+'/'+action,{response});const rows=await this.api.get<RemoteSession[]>('/cases/'+caseId+'/remote-sessions');if(version!==this.requestVersion)return;this.responses[s.id]='';this.sessions.set(rows);this.success.set('Agenda e historial actualizados.');this.updated.emit();});}
  isSupportStaff(){return ['ADMIN','TECH'].includes(this.api.user()?.role??'');}
  canManage(s:RemoteSession){return this.api.user()?.role==='ADMIN'||(this.api.user()?.role==='TECH'&&s.adminId===this.api.user()?.id);}
  label(status:RemoteSession['status']){return {PROPOSED:'Propuesta',CONFIRMED:'Confirmada',DECLINED:'Rechazada',CANCELLED:'Cancelada',COMPLETED:'Realizada'}[status];}
}
