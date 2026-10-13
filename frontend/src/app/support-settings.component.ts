import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
import { CASE_PRIORITIES, CasePriority } from './case-profile.component';
interface Goal {firstResponseMinutes:number;resolutionMinutes:number;}
interface SupportSettings {timezone:string;workDays:number[];startTime:string;endTime:string;holidays:string[];goals:Record<CasePriority,Goal>;}
@Component({selector:'app-support-settings',standalone:true,imports:[FormsModule],template:`
<div class="page-heading"><div><div class="eyebrow">CONFIGURACIÓN</div><h1>Horario y plazos de atención</h1><p>Define objetivos por prioridad, separados del saldo de soporte contratado.</p></div></div>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(form;as s){<section class="panel form-panel"><div class="panel-heading"><div><h2>Calendario laboral</h2><p>Los cambios se aplican a los casos nuevos. Los existentes conservan su configuración.</p></div></div>
<form (ngSubmit)="save()" #settingsForm="ngForm"><fieldset [disabled]="busy()">
<div class="form-grid"><label class="span-2">Zona horaria<input name="slaTimezone" [(ngModel)]="s.timezone" maxlength="100" required placeholder="America/Mexico_City"><small>Nombre IANA; el horario se interpreta en esta zona, incluso si el servidor utiliza otra.</small></label>
<label>Hora de apertura<input name="slaStart" type="time" [(ngModel)]="s.startTime" required></label><label>Hora de cierre<input name="slaEnd" type="time" [(ngModel)]="s.endTime" required></label></div>
<fieldset class="days"><legend>Días laborales</legend>@for(day of days;track day.value){<label class="checkbox-label"><input type="checkbox" [name]="'slaDay'+day.value" [ngModel]="s.workDays.includes(day.value)" (ngModelChange)="toggleDay(day.value,$event)">{{day.label}}</label>}</fieldset>
<label>Fechas inhábiles<textarea name="slaHolidays" [(ngModel)]="holidayText" rows="4" placeholder="2026-12-25&#10;2027-01-01"></textarea><small>Una fecha por línea, AAAA-MM-DD. No se agregan feriados automáticamente.</small></label>
<h2>Objetivos en minutos laborales</h2><p class="muted">Ejemplo: 480 minutos equivalen a un día con jornada de ocho horas. La resolución debe ser igual o mayor que la primera respuesta.</p>
<div class="table-scroll"><table><thead><tr><th>Prioridad</th><th>Primera respuesta (min)</th><th>Resolución (min)</th></tr></thead><tbody>@for(p of priorities;track p.value){<tr><td>{{p.label}}</td><td><input type="number" [name]="'response'+p.value" [attr.aria-label]="'Primera respuesta '+p.label" [(ngModel)]="s.goals[p.value].firstResponseMinutes" min="1" max="43200" step="1" required></td><td><input type="number" [name]="'resolution'+p.value" [attr.aria-label]="'Resolución '+p.label" [(ngModel)]="s.goals[p.value].resolutionMinutes" min="1" max="43200" step="1" required></td></tr>}</tbody></table></div>
</fieldset><div class="form-actions"><button class="btn primary" [disabled]="busy() || settingsForm.invalid">{{busy()?'Guardando…':'Guardar horario y plazos'}}</button></div></form></section>}
@else{<section class="panel"><p class="loading">{{busy()?'Cargando configuración…':'Configuración no disponible.'}}</p>@if(!busy()){<button class="btn secondary" (click)="load()">Reintentar</button>}</section>}
`,styles:[`fieldset{border:0;padding:0;min-width:0}.days{display:flex;flex-wrap:wrap;gap:18px;margin-bottom:20px}.days legend{font-size:12px;font-weight:600}.days label{margin:10px 0}.table-scroll{margin-top:16px}.muted{font-size:12px;line-height:1.6}`]})
export class SupportSettingsComponent extends PageState implements OnInit {
  readonly api=inject(ApiService);form:SupportSettings|null=null;holidayText='';readonly priorities=CASE_PRIORITIES;
  readonly days=[{value:1,label:'Lunes'},{value:2,label:'Martes'},{value:3,label:'Miércoles'},{value:4,label:'Jueves'},{value:5,label:'Viernes'},{value:6,label:'Sábado'},{value:7,label:'Domingo'}];
  ngOnInit(){this.load();}
  load(){void this.perform(async()=>{this.form=await this.api.get<SupportSettings>('/support-settings');this.holidayText=this.form.holidays.join('\n');});}
  toggleDay(day:number,enabled:boolean){if(!this.form)return;this.form.workDays=enabled?[...this.form.workDays.filter(d=>d!==day),day].sort():this.form.workDays.filter(d=>d!==day);}
  save(){void this.perform(async()=>{if(!this.form)return;if(!this.form.workDays.length)throw new Error('Selecciona al menos un día laboral.');const holidays=this.holidayText.split(/\r?\n/).map(d=>d.trim()).filter(Boolean);if(holidays.some(d=>!/^\d{4}-\d{2}-\d{2}$/.test(d)))throw new Error('Usa una fecha AAAA-MM-DD por línea.');this.form=await this.api.put<SupportSettings>('/support-settings',{...this.form,holidays});this.holidayText=this.form.holidays.join('\n');this.success.set('Horario y plazos guardados para los casos nuevos.');});}
}
