import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
import { CASE_CATEGORIES } from './case-profile.component';
import { ReplyTemplate, ReplyTemplateSummary } from './reply-template-models';
@Component({selector:'app-reply-template-picker',standalone:true,imports:[FormsModule],template:`
<div class="template-picker"><button type="button" class="btn secondary" (click)="toggle()" [disabled]="busy()">{{opened()?'Ocultar plantillas':'Usar una plantilla de respuesta'}}</button>
@if(opened()){
 <p class="muted">Busca una plantilla, revisa su contenido y añádelo al borrador. La inserción no publica el comentario, no envía correo y no descuenta minutos.</p>
 @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
 <div class="picker-filters"><label>Buscar plantilla<input name="replyTemplateSearch" [(ngModel)]="q" maxlength="300"></label><label>Categoría<select name="replyTemplateCategory" [(ngModel)]="category"><option value="">Todas</option>@for(c of categories;track c.value){<option [value]="c.value">{{c.label}}</option>}</select></label><label>Versión de SIABUC<input name="replyTemplateVersion" [(ngModel)]="version" maxlength="120"></label><button type="button" class="btn secondary" (click)="search()" [disabled]="busy()">Buscar</button></div>
 <label>Plantilla activa<select name="replyTemplateId" [(ngModel)]="selectedId" (ngModelChange)="preview()" [disabled]="busy()"><option [ngValue]="null">Selecciona una plantilla</option>@for(t of items();track t.id){<option [ngValue]="t.id">{{t.title}} · {{t.siabucVersion||'Todas las versiones'}}</option>}</select></label>
 @if(!busy()&&!items().length){<p class="muted">No hay plantillas activas que coincidan con la búsqueda.</p>}
 @if(selected();as template){<div class="template-preview"><strong>Vista previa · {{template.title}}</strong><p class="muted">Revisión {{template.revision}} · {{template.siabucVersion||'Sin versión específica'}}</p><pre>{{template.body}}</pre><button type="button" class="btn primary" (click)="insert()" [disabled]="busy()">Insertar en borrador</button></div>}
}
</div>
`,styles:[`.template-picker{border:1px solid var(--line);border-radius:8px;padding:16px;margin-bottom:16px}.picker-filters{display:flex;gap:10px;align-items:end;flex-wrap:wrap;margin-bottom:16px}.picker-filters label{flex:1;min-width:150px}.template-preview{padding:16px;margin-top:14px;background:#f5f9f8;border-radius:8px}.template-preview pre{font-family:inherit;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6;font-size:13px}.muted{line-height:1.6;font-size:12px}`]})
export class ReplyTemplatePickerComponent extends PageState {
 readonly api=inject(ApiService);readonly textSelected=output<string>();readonly opened=signal(false);readonly items=signal<ReplyTemplateSummary[]>([]);readonly selected=signal<ReplyTemplate|null>(null);readonly categories=CASE_CATEGORIES;
 q='';category='';version='';selectedId:number|null=null;
 toggle(){this.opened.update(v=>!v);if(this.opened())this.search();}
 search(){void this.perform(async()=>{this.items.set([]);this.selectedId=null;this.selected.set(null);const params=new URLSearchParams({q:this.q,siabucVersion:this.version});if(this.category)params.set('category',this.category);this.items.set(await this.api.get<ReplyTemplateSummary[]>('/reply-templates?'+params));});}
 preview(){if(this.busy())return;const id=this.selectedId;this.selected.set(null);void this.perform(async()=>{if(id===null)return;const template=await this.api.get<ReplyTemplate>(`/reply-templates/${id}`);if(this.selectedId===id&&this.opened())this.selected.set(template);});}
 insert(){const value=this.selected();if(!value||this.busy())return;this.textSelected.emit(value.body);this.opened.set(false);this.selected.set(null);this.selectedId=null;}
}
