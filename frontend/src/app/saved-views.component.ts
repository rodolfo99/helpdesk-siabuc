import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
export interface CaseViewFilters {search:string;status:string;assignment:string;institutionId:number|null;category:string;priority:string;}
interface SavedCaseView {id:number;name:string;filters:CaseViewFilters;revision:number;updatedAt:string;}
@Component({selector:'app-saved-views',standalone:true,imports:[FormsModule],template:`
<section class="saved-views" aria-label="Vistas guardadas">
 <div class="saved-heading"><strong>Mis vistas guardadas</strong><span class="muted">Sólo tú puedes consultar y modificar estas vistas.</span></div>
 @if(error()){<div class="notice error" role="alert">{{error()}}</div>}@if(success()){<div class="notice success" role="status">{{success()}}</div>}
 <div class="saved-grid">
 <label>Elegir vista<select name="savedViewId" [(ngModel)]="selectedId" (ngModelChange)="selectName()" [disabled]="busy()"><option [ngValue]="null">Selecciona una vista</option>@for(view of views();track view.id){<option [ngValue]="view.id">{{view.name}}</option>}</select></label>
 <button type="button" class="btn secondary" (click)="apply()" [disabled]="busy()||selectedId===null">Aplicar vista</button>
 <button type="button" class="btn secondary" (click)="deletePrompt.set(true)" [disabled]="busy()||selectedId===null">Eliminar vista</button>
 </div>
 @if(deletePrompt()) {<div class="notice warning">¿Eliminar «{{selected()?.name}}»? Los casos se conservan.<div class="form-actions"><button type="button" class="btn danger" (click)="remove()" [disabled]="busy()">Confirmar eliminación</button><button type="button" class="btn secondary" (click)="deletePrompt.set(false)" [disabled]="busy()">Cancelar</button></div></div>}
 <form class="saved-grid" (ngSubmit)="save(false)"><label>Nombre de la vista<input name="savedViewName" [(ngModel)]="name" maxlength="100" placeholder="Ej. Mis casos pendientes" required [disabled]="busy()"></label><button class="btn secondary" [disabled]="busy()||!name.trim()">Guardar filtros como nueva vista</button><button type="button" class="btn secondary" (click)="save(true)" [disabled]="busy()||!selected()||!name.trim()">Actualizar vista seleccionada</button></form>
 <p class="muted">Guarda búsqueda, estado, responsable, institución, categoría y prioridad. Para «mis casos pendientes», selecciona Pendientes de resolver y Mis casos antes de guardar.</p>
</section>
`,styles:[`.saved-views{padding:18px 22px;border-top:1px solid var(--line)}.saved-heading{display:flex;gap:16px;flex-wrap:wrap;margin-bottom:12px}.saved-grid{display:flex;align-items:end;gap:10px;flex-wrap:wrap;margin-bottom:12px}.saved-grid label{min-width:210px;flex:1}.saved-grid .btn{min-height:42px}.muted{font-size:12px;line-height:1.6}.saved-views p{margin:8px 0 0}`]})
export class SavedViewsComponent extends PageState implements OnInit {
 readonly api=inject(ApiService);readonly currentFilters=input.required<CaseViewFilters>();readonly filterSelected=output<CaseViewFilters>();readonly views=signal<SavedCaseView[]>([]);readonly deletePrompt=signal(false);
 selectedId:number|null=null;name='';
 ngOnInit(){this.load();}
 load(){void this.perform(async()=>{this.views.set(await this.api.get<SavedCaseView[]>('/saved-views'));});}
 selected(){return this.views().find(v=>v.id===this.selectedId);}
 selectName(){this.name=this.selected()?.name??'';this.deletePrompt.set(false);this.success.set('');}
 apply(){void this.perform(async()=>{if(this.selectedId===null)return;const view=await this.api.get<SavedCaseView>(`/saved-views/${this.selectedId}`);this.filterSelected.emit({...view.filters});this.success.set(`Vista «${view.name}» aplicada.`);});}
 save(update:boolean){void this.perform(async()=>{const old=this.selected();if(update&&!old)throw new Error('Selecciona la vista que quieres actualizar.');const body={name:this.name.trim(),filters:{...this.currentFilters()},expectedRevision:update?old?.revision:null};const view=update?await this.api.put<SavedCaseView>(`/saved-views/${old!.id}`,body):await this.api.post<SavedCaseView>('/saved-views',body);this.views.set(await this.api.get<SavedCaseView[]>('/saved-views'));this.selectedId=view.id;this.name=view.name;this.deletePrompt.set(false);this.success.set(update?'Vista actualizada con los filtros actuales.':'Vista personal guardada.');});}
 remove(){void this.perform(async()=>{if(this.selectedId===null)return;await this.api.delete(`/saved-views/${this.selectedId}`);this.views.set(await this.api.get<SavedCaseView[]>('/saved-views'));this.selectedId=null;this.name='';this.deletePrompt.set(false);this.success.set('Vista eliminada.');});}
}
