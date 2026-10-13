import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState, errorMessage } from './api.service';

export type CaseCategory = 'INSTALLATION'|'CATALOGING'|'LOANS'|'REPORTS'|'IMPORT'|'OTHER';
export type CasePriority = 'LOW'|'NORMAL'|'HIGH'|'URGENT';
export const CASE_CATEGORIES: {value:CaseCategory;label:string}[] = [
  {value:'INSTALLATION',label:'Instalación'},{value:'CATALOGING',label:'Catalogación'},
  {value:'LOANS',label:'Préstamos'},{value:'REPORTS',label:'Reportes'},
  {value:'IMPORT',label:'Importación / exportación'},{value:'OTHER',label:'Otro'}
];
export const CASE_PRIORITIES: {value:CasePriority;label:string}[] = [
  {value:'LOW',label:'Baja'},{value:'NORMAL',label:'Normal'},{value:'HIGH',label:'Alta'},{value:'URGENT',label:'Urgente'}
];
export interface CaseProfileInput {
  category:CaseCategory;priority:CasePriority;siabucVersion:string;operatingSystem:string;errorMessage:string;reproductionSteps:string;
}
export interface CaseProfile extends CaseProfileInput {
  caseId:number;sla:{timezone:string;firstResponseDueAt:string;firstRespondedAt:string|null;resolutionDueAt:string;resolvedAt:string|null;
    firstResponseState:string;resolutionState:string;firstResponseMinutes:number;resolutionMinutes:number;legacy:boolean};
}
export const emptyCaseProfile = ():CaseProfileInput => ({category:'OTHER',priority:'NORMAL',siabucVersion:'',operatingSystem:'',errorMessage:'',reproductionSteps:''});

@Component({selector:'app-case-profile',standalone:true,imports:[FormsModule],template:`
<section class="panel form-panel" aria-label="Clasificación y ficha técnica">
  <div class="panel-heading"><div><h2>Ficha técnica y plazos de atención</h2><p>Clasifica el problema y comparte la información necesaria para reproducirlo.</p></div></div>
  @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
  @if(success()){<div class="notice success" role="status">{{success()}}</div>}
  @if(loading()){<p class="loading">Cargando ficha técnica…</p>}
  @if(profile();as p){
  <div class="sla-grid">
    <article><h3>Primera respuesta</h3><span class="badge" [class.void]="late(p.sla.firstResponseState)">{{stateLabel(p.sla.firstResponseState)}}</span>
      <p>Objetivo: {{p.sla.firstResponseMinutes}} minutos laborales</p><p>Fecha límite: <strong>{{date(p.sla.firstResponseDueAt,p.sla.timezone)}}</strong></p>
      @if(p.sla.firstRespondedAt){<p>Respuesta: {{date(p.sla.firstRespondedAt,p.sla.timezone)}}</p>}
    </article>
    <article><h3>Resolución</h3><span class="badge" [class.void]="late(p.sla.resolutionState)">{{stateLabel(p.sla.resolutionState)}}</span>
      <p>Objetivo: {{p.sla.resolutionMinutes}} minutos laborales</p><p>Fecha límite: <strong>{{date(p.sla.resolutionDueAt,p.sla.timezone)}}</strong></p>
      @if(p.sla.resolvedAt){<p>Cierre: {{date(p.sla.resolvedAt,p.sla.timezone)}}</p>}
    </article>
  </div>
  <p class="sla-note">Horario del caso: {{p.sla.timezone}}. Los plazos laborales no descuentan minutos contratados. La primera respuesta requiere un comentario público del administrativo. Al reabrir se inicia un nuevo plazo de resolución.</p>
  @if(p.sla.legacy){<p class="sla-note">Caso previo a la configuración de plazos: cálculo orientativo con el calendario inicial; solo se muestran fechas de atención/cierre respaldadas por el historial.</p>}
  <form (ngSubmit)="save()" #profileForm="ngForm">
    <fieldset [disabled]="busy() || caseStatus==='CLOSED'">
    <div class="form-grid">
      <label>Categoría<select name="profileCategory" [(ngModel)]="form.category" required>@for(c of categories;track c.value){<option [value]="c.value">{{c.label}}</option>}</select></label>
      <label>Prioridad<select name="profilePriority" [(ngModel)]="form.priority" required>@for(p of priorities;track p.value){<option [value]="p.value">{{p.label}}</option>}</select></label>
      <label>Versión de SIABUC<input name="profileVersion" [(ngModel)]="form.siabucVersion" maxlength="120" placeholder="Ej. SIABUC 9"></label>
      <label>Sistema operativo<input name="profileOs" [(ngModel)]="form.operatingSystem" maxlength="180" placeholder="Ej. Windows 11 / Ubuntu"></label>
      <label class="span-2">Mensaje de error<textarea name="profileError" [(ngModel)]="form.errorMessage" maxlength="10000" rows="3" placeholder="Copia el mensaje exacto; evita incluir contraseñas."></textarea></label>
      <label class="span-2">Pasos para reproducir<textarea name="profileSteps" [(ngModel)]="form.reproductionSteps" maxlength="10000" rows="4" placeholder="1. Abrir… 2. Seleccionar… 3. Resultado observado…"></textarea></label>
    </div>
    </fieldset>
    @if(caseStatus==='OPEN'){<div class="form-actions"><button class="btn primary" [disabled]="busy() || profileForm.invalid">{{busy()?'Guardando…':'Guardar ficha técnica'}}</button></div>}
    @else{<p class="sla-note">El caso está cerrado. Un administrativo puede reabrirlo para actualizar la ficha.</p>}
  </form>
  } @else if(!loading()) {<button class="btn secondary" type="button" (click)="load()">Reintentar</button>}
</section>
`,styles:[`:host{display:block}fieldset{padding:0;border:0;margin:0;min-width:0}.sla-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:0 24px}.sla-grid article{padding:16px;background:#f7fafb;border:1px solid #e1e8ee;border-radius:8px}.sla-grid h3{font-size:13px}.sla-grid p,.sla-note{font-size:11px;line-height:1.7;color:#637b8c}.sla-note{padding:0 24px}.notice{margin:0 24px 18px}@media(max-width:650px){.sla-grid{grid-template-columns:1fr}}`]})
export class CaseProfileComponent extends PageState implements OnChanges {
  @Input({required:true}) caseId!:number;
  @Input() caseStatus:'OPEN'|'CLOSED'='OPEN';
  @Output() updated=new EventEmitter<void>();
  readonly api=inject(ApiService);readonly profile=signal<CaseProfile|null>(null);readonly loading=signal(false);
  readonly categories=CASE_CATEGORIES;readonly priorities=CASE_PRIORITIES;
  form:CaseProfileInput=emptyCaseProfile();private request=0;
  ngOnChanges(){void this.load();}
  async load(){
    const request=++this.request;this.loading.set(true);this.error.set('');this.success.set('');this.profile.set(null);
    try {const p=await this.api.get<CaseProfile>('/cases/'+this.caseId+'/profile');if(request!==this.request)return;this.profile.set(p);this.form={category:p.category,priority:p.priority,siabucVersion:p.siabucVersion,operatingSystem:p.operatingSystem,errorMessage:p.errorMessage,reproductionSteps:p.reproductionSteps};}
    catch(e){if(request===this.request)this.error.set(errorMessage(e));}
    finally{if(request===this.request)this.loading.set(false);}
  }
  save(){const id=this.caseId;void this.perform(async()=>{const p=await this.api.put<CaseProfile>('/cases/'+id+'/profile',this.form);if(id!==this.caseId)return;this.profile.set(p);this.success.set('Ficha técnica guardada.');this.updated.emit();});}
  stateLabel(state:string){return ({PENDING:'Dentro del plazo',OVERDUE:'Plazo vencido',MET:'Cumplido',BREACHED:'Atendido fuera del plazo',UNKNOWN:'Sin fecha registrada'} as Record<string,string>)[state]||state;}
  late(state:string){return state==='OVERDUE'||state==='BREACHED';}
  date(value:string,zone:string){return new Intl.DateTimeFormat('es-MX',{dateStyle:'medium',timeStyle:'short',timeZone:zone}).format(new Date(value));}
}
