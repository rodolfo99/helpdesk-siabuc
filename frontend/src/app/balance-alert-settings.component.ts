import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';

interface BalanceAlertSetting { institutionId:number; institutionName:string; emailThresholdMinutes:number; remoteThresholdMinutes:number; enabled:boolean; emailRemainingMinutes:number; remoteRemainingMinutes:number; }
@Component({selector:'app-balance-alert-settings',standalone:true,imports:[FormsModule],template:`
<div class="page-heading"><div><div class="eyebrow">CONTROL DEL SOPORTE</div><h1>Avisos de saldo</h1><p>Configura cuándo avisar que quedan pocos minutos de correo o escritorio remoto.</p></div></div>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(api.user()?.role==='ADMIN'){
  <section class="panel">
    <div class="panel-heading"><div><h2>Umbrales por institución</h2><p>Los avisos aparecen en la campana de administrativos, cobranza y usuarios de esa institución, aunque el correo esté pausado.</p></div></div>
    <div class="settings-body">
    @if(busy()&&!settings().length){<p role="status">Cargando configuración…</p>}
    @else if(!settings().length){<p>No hay instituciones activas. Registra una institución para configurar sus avisos.</p>}
    @else{
      <label>Institución<select name="alertInstitution" [(ngModel)]="institutionId" (ngModelChange)="choose()">@for(setting of settings();track setting.institutionId){<option [ngValue]="setting.institutionId">{{setting.institutionName}}</option>}</select></label>
      @if(selected();as current){
        <p>Saldo actual: <strong>{{current.emailRemainingMinutes}} min de correo</strong> · <strong>{{current.remoteRemainingMinutes}} min de remoto</strong>.</p>
        <form (ngSubmit)="save()" #alertsForm="ngForm">
          <label class="check"><input name="alertsEnabled" type="checkbox" [(ngModel)]="enabled"> Activar avisos de saldo bajo para esta institución</label>
          <div class="form-grid">
            <label>Avisar al llegar a estos minutos de correo<input name="emailThreshold" type="number" min="0" max="1000000000" step="1" required [(ngModel)]="emailThresholdMinutes"></label>
            <label>Avisar al llegar a estos minutos de remoto<input name="remoteThreshold" type="number" min="0" max="1000000000" step="1" required [(ngModel)]="remoteThresholdMinutes"></label>
          </div>
          <p class="muted">Se avisa cuando el saldo es igual o menor al umbral. Un umbral de 0 avisa sólo al agotarse. Se genera un aviso por canal durante cada periodo de saldo bajo; podrá generarse otro cuando el saldo suba por encima del umbral y vuelva a bajar. Guardar evalúa el saldo inmediatamente.</p>
          <div class="form-actions"><button class="btn primary" [disabled]="busy()||alertsForm.invalid">{{busy()?'Guardando…':'Guardar umbrales'}}</button></div>
        </form>
      }
    }
    </div>
  </section>
}
`,styles:[`.settings-body{padding:0 24px 24px}.settings-body>p{margin:0 0 20px;line-height:1.6}.settings-body .check{flex-direction:row;align-items:center;gap:10px}.settings-body .muted{line-height:1.7;margin-top:4px}.settings-body .form-actions{margin-top:22px}@media(max-width:740px){.settings-body{padding:0 20px 20px}}`]})
export class BalanceAlertSettingsComponent extends PageState implements OnInit {
  readonly api=inject(ApiService);readonly settings=signal<BalanceAlertSetting[]>([]);
  institutionId:number|null=null;emailThresholdMinutes=60;remoteThresholdMinutes=30;enabled=true;
  ngOnInit(){if(this.api.user()?.role!=='ADMIN'){this.error.set('Esta configuración es exclusiva del administrativo.');return;}void this.perform(async()=>{this.settings.set(await this.api.get<BalanceAlertSetting[]>('/balance-alert-settings'));this.institutionId=this.settings()[0]?.institutionId??null;this.choose();});}
  selected(){return this.settings().find(setting=>setting.institutionId===this.institutionId);}
  choose(){const current=this.selected();if(!current)return;this.emailThresholdMinutes=current.emailThresholdMinutes;this.remoteThresholdMinutes=current.remoteThresholdMinutes;this.enabled=current.enabled;this.success.set('');}
  save(){void this.perform(async()=>{
    if(this.institutionId===null)throw new Error('Selecciona una institución.');
    if(!Number.isSafeInteger(this.emailThresholdMinutes)||!Number.isSafeInteger(this.remoteThresholdMinutes)||this.emailThresholdMinutes<0||this.remoteThresholdMinutes<0||this.emailThresholdMinutes>1000000000||this.remoteThresholdMinutes>1000000000)throw new Error('Los umbrales deben ser minutos enteros entre 0 y 1000000000.');
    const changed=await this.api.put<BalanceAlertSetting>('/balance-alert-settings/'+this.institutionId,{emailThresholdMinutes:this.emailThresholdMinutes,remoteThresholdMinutes:this.remoteThresholdMinutes,enabled:this.enabled});
    this.settings.update(values=>values.map(value=>value.institutionId===changed.institutionId?changed:value));this.success.set('Umbrales guardados. El saldo se evaluó con la nueva configuración.');
  });}
}
