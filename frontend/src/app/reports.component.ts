import { caseStatusLabel, isActiveCase } from './case-status';
import {Component,OnInit,inject,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient,HttpErrorResponse} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {ApiService,PageState} from './api.service';
import {Institution,User} from './models';
import {formatTime} from './time';

interface Filters {institutionId:number|null;administratorId:number|null;channel:string|null;from:string;to:string;}
interface CaseRow {id:number;institutionId:number;institutionName:string;title:string;description:string;channel:string;status:string;administratorId:number|null;administratorName:string|null;createdBy:string;createdAt:string;}
interface Attention {id:number;caseId:number;institutionId:number;institutionName:string;caseTitle:string;administratorId:number;administratorName:string;username:string;channel:string;minutes:number;internal:boolean;description:string;createdAt:string;voided:boolean;voidReason:string|null;}
interface Consumption {institutionId:number;institutionName:string;administratorId:number;administratorName:string;channel:string;entries:number;minutes:number;}
interface SupportReport {generatedAt:string;timeZone:string;filters:Filters;totals:{cases:number;openCases:number;closedCases:number;resolvedCases:number;attentionEntries:number;voidedEntries:number;emailMinutes:number;remoteMinutes:number;};cases:CaseRow[];attentions:Attention[];consumption:Consumption[];}

@Component({selector:'app-reports',standalone:true,imports:[CommonModule,FormsModule],template:`
<div class="page-heading"><div><div class="eyebrow">ANÁLISIS DE SOPORTE</div><h1>Reportes</h1><p>Casos y consumo por institución, administrativo, canal y periodo.</p></div></div>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}
@if(success()){<div class="notice success" role="status">{{success()}}</div>}
<section class="panel" style="padding:1.25rem;margin-bottom:1rem">
  <form (ngSubmit)="load()" class="form-grid">
    <label>Institución<select name="institution" [(ngModel)]="filters.institutionId"><option [ngValue]="null">Todas las instituciones</option>@for(i of institutions();track i.id){<option [ngValue]="i.id">{{i.name}}{{i.active?'':' (inactiva)'}}</option>}</select></label>
    <label>Administrativo<select name="administrator" [(ngModel)]="filters.administratorId"><option [ngValue]="null">Todos los administrativos</option>@for(a of administrators();track a.id){<option [ngValue]="a.id">{{a.displayName}}{{a.active?'':' (inactivo)'}}</option>}</select></label>
    <label>Canal<select name="channel" [(ngModel)]="filters.channel"><option [ngValue]="null">Ambos canales</option><option value="EMAIL">Correo</option><option value="REMOTE">Escritorio remoto</option></select></label>
    <label>Desde<input name="from" type="date" [(ngModel)]="filters.from" required></label>
    <label>Hasta (inclusive)<input name="to" type="date" [(ngModel)]="filters.to" required></label>
    <div style="align-self:end"><button type="submit" class="btn primary" [disabled]="busy()">{{busy()?'Procesando…':'Consultar reporte'}}</button></div>
  </form>
  <p class="muted" style="margin-top:1rem">Máximo 366 días y 5000 registros. Los casos se filtran por creación y responsable actual; los consumos por fecha, canal y autor de la atención. Las anulaciones se muestran sin sumar minutos. Las notas internas están disponibles solo para administrativos.</p>
</section>
@if(report();as r){
  <section class="panel" style="padding:1.25rem;margin-bottom:1rem">
    <div class="page-heading"><div><h2>Resultado: {{r.filters.from}} a {{r.filters.to}}</h2><p>Zona horaria: {{r.timeZone}} · Generado: {{date(r.generatedAt)}}</p></div><div style="display:flex;gap:.5rem;flex-wrap:wrap"><button class="btn secondary" (click)="download('xlsx')" [disabled]="busy()">Exportar Excel</button><button class="btn secondary" (click)="download('pdf')" [disabled]="busy()">Exportar PDF</button></div></div>
    <div style="display:flex;gap:2rem;flex-wrap:wrap"><div><strong>{{r.totals.cases}}</strong><p>Casos · {{r.totals.openCases}} pendientes / {{r.totals.resolvedCases}} resueltos / {{r.totals.closedCases}} cerrados</p></div><div><strong>{{time(r.totals.emailMinutes)}}</strong><p>Correo · {{r.totals.emailMinutes}} minutos</p></div><div><strong>{{time(r.totals.remoteMinutes)}}</strong><p>Remoto · {{r.totals.remoteMinutes}} minutos</p></div><div><strong>{{r.totals.attentionEntries}}</strong><p>Atenciones vigentes · {{r.totals.voidedEntries}} anuladas</p></div></div>
    <p class="muted">Las exportaciones usan los filtros de este resultado y los datos vigentes al descargarlas. Consulta de nuevo para actualizar los totales de pantalla.</p>
  </section>
  <section class="panel" style="margin-bottom:1rem"><h2 style="padding:1rem">Consumo agrupado</h2><div class="table-scroll"><table><thead><tr><th>Institución</th><th>Administrativo</th><th>Canal</th><th>Atenciones vigentes</th><th>Minutos</th></tr></thead><tbody>@for(g of r.consumption;track g.institutionId+':'+g.administratorId+':'+g.channel){<tr><td>{{g.institutionName}}</td><td>{{g.administratorName}}</td><td>{{channel(g.channel)}}</td><td>{{g.entries}}</td><td>{{g.minutes}}</td></tr>}@empty{<tr><td colspan="5">Sin consumos vigentes con estos filtros.</td></tr>}</tbody></table></div></section>
  <section class="panel" style="margin-bottom:1rem"><h2 style="padding:1rem">Casos creados en el periodo</h2><div class="table-scroll"><table><thead><tr><th>Caso / institución</th><th>Responsable actual</th><th>Canal</th><th>Estado actual</th><th>Creación</th></tr></thead><tbody>@for(c of r.cases;track c.id){<tr><td><strong>#{{c.id}} · {{c.title}}</strong><small>{{c.institutionName}}</small><details><summary>Descripción actual</summary><p style="white-space:pre-wrap;overflow-wrap:anywhere">{{c.description}}</p></details></td><td>{{c.administratorName||'Sin responsable'}}</td><td>{{channel(c.channel)}}</td><td>{{statusLabel(c.status)}}</td><td>{{date(c.createdAt)}}<small>{{c.createdBy}}</small></td></tr>}@empty{<tr><td colspan="5">Sin casos creados con estos filtros. Puede haber consumos de casos anteriores.</td></tr>}</tbody></table></div></section>
  <section class="panel"><h2 style="padding:1rem">Atenciones con consumo en el periodo</h2><div class="table-scroll"><table><thead><tr><th>Atención / caso</th><th>Institución</th><th>Administrativo</th><th>Canal / minutos</th><th>Fecha</th><th>Vigencia</th></tr></thead><tbody>@for(e of r.attentions;track e.id){<tr><td><strong>Atención #{{e.id}} · Caso #{{e.caseId}}</strong><small>{{e.caseTitle}} · {{e.internal?'Interna':'Pública'}}</small><details><summary>Descripción de atención</summary><p style="white-space:pre-wrap;overflow-wrap:anywhere">{{e.description}}</p>@if(e.voided){<p style="white-space:pre-wrap;overflow-wrap:anywhere">Motivo de anulación: {{e.voidReason}}</p>}</details></td><td>{{e.institutionName}}</td><td>{{e.administratorName}}<small>{{e.username}}</small></td><td>{{channel(e.channel)}}<small>{{e.minutes}} min</small></td><td>{{date(e.createdAt)}}</td><td>{{e.voided?'Anulada · no suma':'Vigente'}}</td></tr>}@empty{<tr><td colspan="6">Sin atenciones con consumo en este periodo.</td></tr>}</tbody></table></div></section>
}
`})
export class ReportsComponent extends PageState implements OnInit {
 readonly statusLabel=caseStatusLabel;readonly isActiveCase=isActiveCase;
  private readonly api=inject(ApiService);private readonly http=inject(HttpClient);
  readonly institutions=signal<Institution[]>([]);readonly administrators=signal<User[]>([]);readonly report=signal<SupportReport|null>(null);readonly time=formatTime;
  filters:Filters={institutionId:null,administratorId:null,channel:null,from:'',to:''};
  ngOnInit(){void this.perform(async()=>{const [institutions,users,report]=await Promise.all([this.api.get<Institution[]>('/institutions'),this.api.get<User[]>('/users'),this.api.get<SupportReport>('/reports/support')]);this.institutions.set(institutions);this.administrators.set(users.filter(u=>u.role==='ADMIN'));this.report.set(report);this.filters={...report.filters};});}
  channel(value:string){return value==='REMOTE'?'Escritorio remoto':'Correo';}
  date(value:string){return new Intl.DateTimeFormat('es-MX',{dateStyle:'short',timeStyle:'short',timeZone:this.report()?.timeZone||'America/Mexico_City'}).format(new Date(value));}
  private query(f:Filters){const q=new URLSearchParams({from:f.from,to:f.to});if(f.institutionId!==null)q.set('institutionId',String(f.institutionId));if(f.administratorId!==null)q.set('administratorId',String(f.administratorId));if(f.channel)q.set('channel',f.channel);return q.toString();}
  load(){void this.perform(async()=>{if(!this.filters.from||!this.filters.to)throw new Error('Selecciona ambas fechas.');this.report.set(await this.api.get<SupportReport>('/reports/support?'+this.query(this.filters)));});}
  download(extension:'xlsx'|'pdf'){void this.perform(async()=>{
    const report=this.report();if(!report)return;
    try {
      const response=await firstValueFrom(this.http.get('/api/reports/support.'+extension+'?'+this.query(report.filters),{responseType:'blob',observe:'response'}));
      if(!response.body)throw new Error('El servidor no devolvió el archivo.');
      const url=URL.createObjectURL(response.body);const link=document.createElement('a');link.href=url;link.download='SIABUC-soporte-'+report.filters.from+'-'+report.filters.to+'.'+extension;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);this.success.set('Reporte descargado.');
    } catch(error) {
      if(error instanceof HttpErrorResponse&&error.error instanceof Blob){let message='No se pudo descargar el reporte ('+error.status+').';try{const body=JSON.parse(await error.error.text());if(typeof body.message==='string')message=body.message;}catch{}throw new Error(message);}throw error;
    }
  });}
}
