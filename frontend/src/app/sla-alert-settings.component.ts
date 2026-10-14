import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
interface SlaAlertSettings {enabled:boolean;leadMinutes:number;}
@Component({selector:'app-sla-alert-settings',standalone:true,imports:[FormsModule],template:`
<section class="panel sla-alerts"><div class="panel-heading"><div><h2>Avisos antes del vencimiento</h2><p>Recordatorios internos de primera respuesta y resolución, independientes del correo y del saldo contratado.</p></div></div>
<div class="settings-body">@if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
<form (ngSubmit)="save()" #form="ngForm"><label class="check"><input type="checkbox" name="slaAlertsEnabled" [(ngModel)]="enabled" [disabled]="busy()||!isAdmin()"> Activar avisos de vencimiento en la campana</label><label>Anticipación en minutos naturales<input type="number" name="slaAlertLead" [(ngModel)]="leadMinutes" min="1" max="43200" step="1" required [disabled]="busy()||!isAdmin()"></label><p class="muted">Se revisan cada minuto los casos abiertos de instituciones activas. Cada objetivo y fecha de vencimiento genera un aviso por destinatario, incluso si ya venció. Los plazos se calculan con el horario laboral de la ficha del caso; esta anticipación se cuenta en tiempo natural.</p><p class="muted">Administración recibe los avisos de todos los casos. Los técnicos reciben los de sus casos y los que todavía no tienen responsable. Leer un aviso no lo vuelve a generar; consulta el caso para comprobar su estado actual.</p>@if(isAdmin()){<div class="form-actions"><button class="btn primary" [disabled]="busy()||form.invalid">Guardar avisos</button><button type="button" class="btn secondary" (click)="scan()" [disabled]="busy()">Revisar plazos ahora</button></div>}</form>
</div></section>
`,styles:[`.settings-body{padding:0 24px 24px}.check{display:flex;flex-direction:row;align-items:center;gap:10px;margin-bottom:20px}.settings-body input[type=number]{max-width:280px}.muted{line-height:1.65}`]})
export class SlaAlertSettingsComponent extends PageState implements OnInit {
 readonly api=inject(ApiService);enabled=true;leadMinutes=60;
 isAdmin(){return this.api.user()?.role==='ADMIN';}
 ngOnInit(){void this.perform(async()=>{const value=await this.api.get<SlaAlertSettings>('/sla-alerts/settings');this.enabled=value.enabled;this.leadMinutes=value.leadMinutes;});}
 save(){void this.perform(async()=>{if(!Number.isSafeInteger(this.leadMinutes)||this.leadMinutes<1||this.leadMinutes>43200)throw new Error('Indica entre 1 y 43200 minutos enteros.');const value=await this.api.put<SlaAlertSettings>('/sla-alerts/settings',{enabled:this.enabled,leadMinutes:this.leadMinutes});this.enabled=value.enabled;this.leadMinutes=value.leadMinutes;this.success.set('Configuración guardada. El siguiente ciclo usará estos valores.');});}
 scan(){void this.perform(async()=>{const result=await this.api.post<{created:number}>('/sla-alerts/scan',{});this.success.set(`Revisión completada: ${result.created} avisos nuevos. Los anteriores no se duplicaron.`);});}
}
