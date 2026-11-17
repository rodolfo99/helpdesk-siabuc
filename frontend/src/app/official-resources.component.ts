import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';

type ResourceKind='INSTALLER'|'PATCH'|'REPORT'|'DOCUMENT'|'VIDEO';
type ResourceStatus='DRAFT'|'PUBLISHED'|'ARCHIVED';
interface Resource {
  id:number;title:string;kind:ResourceKind;siabucVersion:string;description:string;
  targetUrl:string;status:ResourceStatus;revision:number;updatedAt:string;publishedAt:string|null;
}
interface ResourceForm {
  title:string;kind:ResourceKind;siabucVersion:string;description:string;
  targetUrl:string;expectedRevision:number|null;
}

/** Reviewed institutional links only. No uploads, redirects or third-party executable mirrors. */
@Component({
  selector:'app-official-resources',
  standalone:true,
  imports:[CommonModule,FormsModule],
  template:`
  <div class="page-heading"><div><div class="eyebrow">CENTRO DE AYUDA</div>
    <h1>Descargas y recursos oficiales</h1>
    <p>Consulta los recursos publicados y aprobados por el Centro SIABUC.</p></div>
    @if(isAdmin()){<button type="button" class="btn primary" (click)="newResource()">Nuevo recurso</button>}
  </div>
  <section class="panel official-resources-panel">
    @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
    @if(success()){<div class="notice success" role="status">{{success()}}</div>}
    <form class="official-filters" (ngSubmit)="load()">
      <label>Buscar recurso<input name="searchResource" [(ngModel)]="search" maxlength="120" placeholder="Nombre o descripción"></label>
      <label>Tipo<select name="kindResource" [(ngModel)]="selectedKind">
        <option value="">Todos</option>
        @for(k of kinds;track k.value){<option [value]="k.value">{{k.label}}</option>}
      </select></label>
      <button type="submit" class="btn secondary" [disabled]="busy()">Buscar</button>
    </form>
    @if(busy()){<p class="loading" role="status">Consultando el catálogo…</p>}
    <div class="official-resource-list">
      @for(r of resources();track r.id){
        <article class="official-resource">
          <div><span class="badge">{{kindLabel(r.kind)}}</span>
            @if(isAdmin()){<span class="badge" [class.void]="r.status!=='PUBLISHED'">{{statusLabel(r.status)}}</span>}
            <h2>{{r.title}}</h2><p>{{r.description}}</p>
            <small>SIABUC {{r.siabucVersion||'General'}} · Actualizado {{r.updatedAt | date:'dd/MM/yyyy'}}</small>
            <div class="resource-actions">
              @if(r.status==='PUBLISHED'){
                <a class="btn secondary" [href]="r.targetUrl" target="_blank" rel="noopener noreferrer">Abrir recurso ↗</a>
              }
              @if(isAdmin()){
                @if(r.status==='DRAFT'){
                  <button type="button" class="btn secondary" (click)="edit(r)" [disabled]="busy()">Editar</button>
                  <button type="button" class="btn primary" (click)="changeStatus(r,'PUBLISHED')" [disabled]="busy()">Publicar</button>
                  <button type="button" class="btn secondary" (click)="changeStatus(r,'ARCHIVED')" [disabled]="busy()">Archivar</button>
                }
                @if(r.status==='PUBLISHED'){
                  <button type="button" class="btn secondary" (click)="changeStatus(r,'DRAFT')" [disabled]="busy()">Retirar publicación</button>
                }
                @if(r.status==='ARCHIVED'){
                  <button type="button" class="btn secondary" (click)="changeStatus(r,'DRAFT')" [disabled]="busy()">Recuperar borrador</button>
                }
              }
            </div>
          </div>
        </article>
      } @empty {
        @if(!busy()){<div class="empty-state">
          <h3>Sin recursos disponibles</h3>
          <p>El Centro SIABUC todavía no ha publicado descargas oficiales para esta búsqueda.</p>
        </div>}
      }
    </div>
  </section>
  @if(isAdmin() && editing()){
    <section class="panel official-resources-panel">
      <h2>{{currentId()?'Editar recurso en borrador':'Nuevo recurso institucional'}}</h2>
      <p class="muted">Se aceptan enlaces HTTPS a dominios de la Universidad de Colima. Los archivos no se almacenan en el Helpdesk.</p>
      <form (ngSubmit)="save()" #resourceForm="ngForm">
        <div class="form-grid">
          <label class="span-2">Título<input name="resourceTitle" [(ngModel)]="form.title" required maxlength="180"></label>
          <label>Tipo<select name="resourceKind" [(ngModel)]="form.kind" required>
            @for(k of kinds;track k.value){<option [value]="k.value">{{k.label}}</option>}
          </select></label>
          <label>Versión de SIABUC<input name="resourceVersion" [(ngModel)]="form.siabucVersion" maxlength="120" placeholder="Ej. SIABUC 9"></label>
          <label class="span-2">Descripción<textarea name="resourceDescription" [(ngModel)]="form.description" required maxlength="1200" rows="3"></textarea></label>
          <label class="span-2">Dirección institucional HTTPS<input name="resourceUrl" [(ngModel)]="form.targetUrl" required maxlength="2000" type="url" pattern="https://.*" placeholder="https://siabuc.ucol.mx/..."></label>
        </div>
        <div class="form-actions">
          <button type="button" class="btn secondary" (click)="editing.set(false)" [disabled]="busy()">Cancelar</button>
          <button type="submit" class="btn primary" [disabled]="busy()||resourceForm.invalid">Guardar borrador</button>
        </div>
      </form>
    </section>
  }
  `,
  styles:[`
    .official-resources-panel{padding:22px;margin-bottom:20px}
    .official-filters{display:flex;flex-wrap:wrap;gap:12px;align-items:end}.official-filters label{flex:1;min-width:170px}
    .official-resource{padding:20px 0;border-top:1px solid #e0e8ee}.official-resource h2{font-size:17px;margin:12px 0 6px}
    .official-resource p{font-size:14px;line-height:1.65;white-space:pre-wrap}.official-resource small{color:#62798a}
    .resource-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
    @media(max-width:650px){.official-resources-panel{padding:16px}.official-filters{display:grid}.official-filters label{min-width:0}}
  `]
})
export class OfficialResourcesComponent extends PageState implements OnInit {
  private readonly api=inject(ApiService);
  readonly kinds:{value:ResourceKind;label:string}[]=[
    {value:'INSTALLER',label:'Instaladores'}, {value:'PATCH',label:'Actualizaciones'},
    {value:'REPORT',label:'Reportes'}, {value:'DOCUMENT',label:'Documentación'},
    {value:'VIDEO',label:'Videos'}];
  readonly resources=signal<Resource[]>([]);
  readonly editing=signal(false);
  readonly currentId=signal<number|null>(null);
  search='';selectedKind:ResourceKind|''='';
  form:ResourceForm=this.emptyForm();
  isAdmin(){return this.api.user()?.role==='ADMIN';}
  kindLabel(kind:ResourceKind){return this.kinds.find(k=>k.value===kind)?.label??kind;}
  statusLabel(status:ResourceStatus){return ({DRAFT:'Borrador',PUBLISHED:'Publicado',ARCHIVED:'Archivado'})[status];}
  ngOnInit(){this.load();}
  private emptyForm():ResourceForm {return {title:'',kind:'DOCUMENT',siabucVersion:'',description:'',targetUrl:'',expectedRevision:null};}
  load(){void this.perform(async()=>{
    const params=new URLSearchParams();
    if(this.search.trim())params.set('q',this.search.trim());
    if(this.selectedKind)params.set('kind',this.selectedKind);
    const suffix=params.toString();
    this.resources.set(await this.api.get<Resource[]>('/official-resources'+(suffix?'?'+suffix:'')));
  });}
  newResource(){if(!this.isAdmin())return;this.currentId.set(null);this.form=this.emptyForm();this.editing.set(true);}
  edit(item:Resource){if(!this.isAdmin()||item.status!=='DRAFT')return;
    this.currentId.set(item.id);
    this.form={title:item.title,kind:item.kind,siabucVersion:item.siabucVersion,description:item.description,
      targetUrl:item.targetUrl,expectedRevision:item.revision};this.editing.set(true);
  }
  save(){if(!this.isAdmin())return;void this.perform(async()=>{
    const id=this.currentId();
    if(id===null)await this.api.post<Resource>('/official-resources',this.form);
    else await this.api.put<Resource>('/official-resources/'+id,this.form);
    this.editing.set(false);
    this.resources.set(await this.api.get<Resource[]>('/official-resources'));
    this.search='';this.selectedKind='';
    this.success.set('Borrador guardado. No está publicado para clientes.');
  });}
  changeStatus(item:Resource,status:ResourceStatus){if(!this.isAdmin())return;void this.perform(async()=>{
    await this.api.post<Resource>('/official-resources/'+item.id+'/status',{status,expectedRevision:item.revision});
    this.resources.set(await this.api.get<Resource[]>('/official-resources'));
    this.search='';this.selectedKind='';
    this.success.set(status==='PUBLISHED'?'Recurso publicado para consulta.':'Estado del recurso actualizado.');
  });}
}
