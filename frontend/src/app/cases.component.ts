import { SavedViewsComponent, CaseViewFilters } from './saved-views.component';
import { ReplyTemplatePickerComponent } from './reply-template-picker.component';
import { CaseIncidentsComponent } from './case-incidents.component';
import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, PageState, errorMessage } from './api.service';
import { CaseDetail, CaseEntry, Channel, Institution, SupportCase, User } from './models';
import { formatTime,toMinutes } from './time';
import { CaseProfileComponent, CaseCategory, CasePriority, CASE_CATEGORIES, CASE_PRIORITIES, emptyCaseProfile } from './case-profile.component';
import { AttachmentsComponent } from './attachments.component';
import { CaseTimerComponent } from './case-timer.component';
import { RemoteSessionsComponent } from './remote-sessions.component';
import { CaseKnowledgeComponent } from './case-knowledge.component';
import { SatisfactionSurveyComponent } from './satisfaction-survey.component';
interface CaseProfileSummary {caseId:number;category:CaseCategory;priority:CasePriority;}
export interface CaseOpenRequest {caseId:number;sequence:number;}

@Component({selector:'app-cases',standalone:true,imports:[SavedViewsComponent,ReplyTemplatePickerComponent,CaseIncidentsComponent,CommonModule,FormsModule,CaseProfileComponent,AttachmentsComponent,CaseTimerComponent,RemoteSessionsComponent,CaseKnowledgeComponent,SatisfactionSurveyComponent],templateUrl:'./cases.component.html',styles:[`
  .history-entry-clickable{cursor:pointer;border-radius:0 8px 8px 0;transition:background .15s}
  .history-entry-clickable:hover{background:#f5f9f8}
  .history-entry-clickable:focus-within{outline:2px solid #6d9b90;outline-offset:3px}
  .history-entry-link{display:inline-block;font-size:11px;font-weight:700;color:#367d6b;margin:10px 18px 0 0;text-decoration:underline;text-underline-offset:3px}
  .history-entry-link:focus-visible,.history-return:focus-visible{outline:2px solid #367d6b;outline-offset:4px}
  [aria-disabled="true"]{cursor:wait;opacity:.65}
  .entry-detail-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px}
  .entry-detail-grid{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:24px;align-items:start}
  .entry-detail-description{margin-top:24px;padding:24px}
  .entry-detail-description h2{font-size:16px;margin:0 0 16px}
  .entry-detail-description p{line-height:1.85;overflow-wrap:anywhere}
  .entry-detail-void{margin-top:24px;padding:22px 24px;border:1px solid #e8d4cd;border-radius:12px;background:#fff9f6}
  .entry-detail-void h2{font-size:16px;margin:0 0 16px}
  .entry-detail-void p{line-height:1.75;overflow-wrap:anywhere}
  .entry-detail-context-description{white-space:pre-wrap;overflow-wrap:anywhere}
  .case-detail-grid>div,.case-aside{min-width:0}
  .case-tabs{display:flex;gap:8px;margin-bottom:20px;overflow-x:auto;padding-bottom:8px;border-bottom:1px solid var(--line)}
  .case-tabs button{border:0;background:transparent;color:var(--muted);font-weight:700;font-size:12px;white-space:nowrap;border-radius:8px;padding:12px 14px}
  .case-tabs button.selected{background:#e2f1ec;color:#096c62}
  .case-profile-tags{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}
  .case-profile-tags .badge{font-size:10px}
  .priority-high{background:#fff2d8;color:#885514}.priority-urgent{background:#ffe5e3;color:#a8322e}
  .case-profile-filters{display:flex;gap:16px;flex-wrap:wrap;padding:0 22px 18px}
  .case-profile-filters label{min-width:180px}
  .profile-create-details{border:1px solid var(--line);border-radius:8px;padding:16px}
  .profile-create-details summary{cursor:pointer;font-weight:700;font-size:13px}
  .profile-create-details[open] summary{margin-bottom:16px}
  .feature-stack{display:grid;gap:20px;margin:20px 0}
  .feature-panel{padding:20px;min-width:0}
  @media(max-width:900px){.entry-detail-grid{grid-template-columns:1fr}}
  @media(max-width:540px){.entry-detail-meta{grid-template-columns:1fr}}
`]})
export class CasesComponent extends PageState implements OnInit,OnDestroy {
  readonly api=inject(ApiService);readonly items=signal<SupportCase[]>([]);readonly institutions=signal<Institution[]>([]);readonly admins=signal<User[]>([]);readonly detail=signal<CaseDetail|null>(null);readonly creating=signal(false);readonly editing=signal(false);readonly statusPrompt=signal(false);readonly voidEntry=signal<CaseEntry|null>(null);readonly time=formatTime;
  readonly openRequest=input<CaseOpenRequest|null>(null);
  readonly detailTab=signal<'followup'|'profile'|'attachments'|'agenda'>('followup');
  readonly profileSummaries=signal<Record<number,CaseProfileSummary>>({});
  readonly categories=CASE_CATEGORIES;
  readonly priorities=CASE_PRIORITIES;
  categoryFilter='all';priorityFilter='all';
  private readonly initialized=signal(false);
  private readonly requestedCaseId=signal<number|null>(null);
  private readonly refreshRequested=signal(false);
  private readonly element=inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly selectedEntryId=signal<number|null>(null);
  private readonly claimBlockedCaseId=signal<number|null>(null);
  readonly selectedEntry=computed(()=>this.detail()?.entries.find(entry=>entry.id===this.selectedEntryId()&&entry.caseId===this.detail()?.id&&(!entry.internal||this.isSupportStaff()))??null);
  private historyPosition={x:0,y:0};private historyFocusId:number|null=null;private navigationFrame:number|null=null;
  search='';statusFilter='all';assignmentFilter='all';institutionFilter:number|null=null;newForm={institutionId:null as number|null,title:'',description:'',channel:'EMAIL' as Channel,assignedAdminId:null as number|null,profile:emptyCaseProfile()};editForm={title:'',description:'',channel:'EMAIL' as Channel,assignedAdminId:null as number|null};entryDescription='';entryKind:'COMMENT'|'EMAIL'|'REMOTE'='COMMENT';entryHours:number|null=0;entryMinutes:number|null=0;entryInternal=false;statusReason='';voidReason='';
  constructor(){
    super();
    effect(()=>{const request=this.openRequest();if(request&&Number.isSafeInteger(request.caseId)&&request.caseId>0)untracked(()=>this.requestedCaseId.set(request.caseId));});
    effect(()=>{const id=this.requestedCaseId(),ready=this.initialized(),busy=this.busy(),refresh=this.refreshRequested();if(!ready||busy)return;untracked(()=>{if(id!==null){this.requestedCaseId.set(null);this.refreshRequested.set(false);this.selectId(id);}else if(refresh){this.refreshRequested.set(false);void this.perform(async()=>{const c=this.detail();if(c)this.detail.set(await this.api.get<CaseDetail>(`/cases/${c.id}`));await this.refresh();});}});});
  }
  ngOnInit(){void this.perform(async()=>{await this.refresh();this.initialized.set(true);});}ngOnDestroy(){this.cancelNavigation();}isAdmin(){return this.api.user()?.role==='ADMIN';}isSupportStaff(){return ['ADMIN','TECH'].includes(this.api.user()?.role??'');}
  private cancelNavigation(){if(this.navigationFrame!==null){cancelAnimationFrame(this.navigationFrame);this.navigationFrame=null;}}
  private resetHistoryDetail(){this.cancelNavigation();this.selectedEntryId.set(null);this.historyFocusId=null;this.voidEntry.set(null);this.claimBlockedCaseId.set(null);}
  private focusAfterRender(selector:string,position:{x:number;y:number}){this.cancelNavigation();this.navigationFrame=requestAnimationFrame(()=>{this.navigationFrame=null;this.element.nativeElement.querySelector<HTMLElement>(selector)?.focus({preventScroll:true});window.scrollTo({left:position.x,top:position.y,behavior:'auto'});});}
  openEntryFromCard(entry:CaseEntry,event:MouseEvent){if((event.target as Element).closest('a,button,input,textarea,select')||window.getSelection()?.toString())return;this.openEntry(entry,event);}
  openEntry(entry:CaseEntry,event:Event){event.preventDefault();event.stopPropagation();if(this.busy()||this.voidEntry())return;const c=this.detail(),authorized=c?.entries.find(item=>item.id===entry.id&&item.caseId===c.id);if(!authorized||(authorized.internal&&!this.isSupportStaff()))return;this.historyPosition={x:window.scrollX,y:window.scrollY};this.historyFocusId=authorized.id;this.selectedEntryId.set(authorized.id);this.focusAfterRender('#case-entry-detail-title',{x:0,y:0});}
  backToHistory(event:Event){event.preventDefault();if(this.busy())return;const id=this.historyFocusId;this.selectedEntryId.set(null);this.focusAfterRender(id===null?'#case-history':`#case-history-link-${id}`,this.historyPosition);this.historyFocusId=null;}
  load(){void this.perform(async()=>{await this.refresh();this.initialized.set(true);});}
  reloadDetail(){this.refreshRequested.set(true);}
  async refresh(){const [cases,institutions,users,profiles]=await Promise.all([this.api.get<SupportCase[]>('/cases'),this.api.get<Institution[]>('/institutions'),this.isAdmin()?this.api.get<User[]>('/users'):Promise.resolve([] as User[]),this.api.get<CaseProfileSummary[]>('/case-profiles')]);this.items.set(cases);this.institutions.set(institutions);this.admins.set(users.filter(u=>['ADMIN','TECH'].includes(u.role)&&u.active));this.profileSummaries.set(Object.fromEntries(profiles.map(profile=>[profile.caseId,profile])));}
  filtered(){const q=this.search.trim().toLocaleLowerCase();return this.items().filter(c=>(this.categoryFilter==='all'||this.profileSummaries()[c.id]?.category===this.categoryFilter)&&(this.priorityFilter==='all'||this.profileSummaries()[c.id]?.priority===this.priorityFilter)&&(this.statusFilter==='all'||c.status===this.statusFilter)&&(!this.institutionFilter||c.institutionId===this.institutionFilter)&&(!this.isSupportStaff()||this.assignmentFilter==='all'||(this.assignmentFilter==='unassigned'?c.assignedAdminId===null:c.assignedAdminId===this.api.user()?.id))&&`${c.id} ${c.title} ${c.institutionName} ${c.description}`.toLocaleLowerCase().includes(q)).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));}
  currentViewFilters():CaseViewFilters{return {search:this.search,status:this.statusFilter,assignment:this.isSupportStaff()?this.assignmentFilter:'all',institutionId:this.institutionFilter,category:this.categoryFilter,priority:this.priorityFilter};}
  applyCaseView(filters:CaseViewFilters){this.search=filters.search;this.statusFilter=filters.status;this.assignmentFilter=this.isSupportStaff()?filters.assignment:'all';this.institutionFilter=this.isSupportStaff()?filters.institutionId:null;this.categoryFilter=filters.category as CaseCategory|'all';this.priorityFilter=filters.priority as CasePriority|'all';}
  insertTemplate(text:string){if(!this.isSupportStaff()||this.detail()?.status!=='OPEN')return;const next=this.entryDescription.trim()?this.entryDescription+'\n\n'+text:text;if(next.length>10000){this.error.set('La plantilla y el borrador superan 10 000 caracteres. Acorta el texto antes de insertarla.');return;}this.entryDescription=next;this.success.set('Plantilla insertada en el borrador. Revisa el mensaje antes de agregarlo al historial.');}
  openRelatedCase(id:number){if(this.busy()||!Number.isSafeInteger(id)||id<=0)return;this.selectId(id);}
  categoryLabel(id:number){return this.categories.find(item=>item.value===this.profileSummaries()[id]?.category)?.label??'Otro';}
  priorityLabel(id:number){return this.priorities.find(item=>item.value===this.profileSummaries()[id]?.priority)?.label??'Normal';}
  activeInstitutions(){return this.institutions().filter(i=>i.active);}
  institution(id:number|null){return this.institutions().find(i=>i.id===id);}
  balance(id:number|null,channel:Channel){const i=this.institution(id);return i?(channel==='EMAIL'?i.emailRemainingMinutes:i.remoteRemainingMinutes):0;}
  channelLabel(channel:Channel|null){return channel==='EMAIL'?'Correo':channel==='REMOTE'?'Escritorio remoto':'Comentario';}
  canClaim(c:SupportCase){return this.isSupportStaff()&&c.status==='OPEN'&&c.assignedAdminId===null&&this.institution(c.institutionId)?.active===true&&this.claimBlockedCaseId()!==c.id;}
  claimCase(){void this.perform(async()=>{const c=this.detail();if(!c||!this.canClaim(c))return;try{this.detail.set(await this.api.post<CaseDetail>(`/cases/${c.id}/claim`,{}));this.editing.set(false);this.statusPrompt.set(false);await this.refresh();this.success.set('Caso asignado a tu cuenta. La toma del caso quedó registrada en el historial.');}catch(error){if(error instanceof HttpErrorResponse&&error.status===409){this.claimBlockedCaseId.set(c.id);try{this.detail.set(await this.api.get<CaseDetail>(`/cases/${c.id}`));this.editing.set(false);this.statusPrompt.set(false);await this.refresh();}catch{throw new Error(errorMessage(error)+' No fue posible actualizar el caso; vuelve al listado y ábrelo de nuevo.');}}throw error;}});}
  create(){if(this.busy())return;this.resetHistoryDetail();this.newForm={institutionId:this.isSupportStaff()?null:this.api.user()?.institutionId??null,title:'',description:'',channel:'EMAIL',assignedAdminId:null,profile:emptyCaseProfile()};this.detailTab.set('followup');this.creating.set(true);this.detail.set(null);this.error.set('');this.success.set('');}
  saveNew(){void this.perform(async()=>{if(!this.newForm.institutionId)throw new Error('Selecciona la institución.');if(!this.newForm.title.trim()||!this.newForm.description.trim())throw new Error('Escribe el título y la descripción del caso.');if(this.balance(this.newForm.institutionId,this.newForm.channel)<=0)throw new Error('Esta institución no tiene saldo disponible para el canal seleccionado.');const result=await this.api.post<CaseDetail>('/cases',{...this.newForm,assignedAdminId:this.isAdmin()?this.newForm.assignedAdminId:null,title:this.newForm.title.trim(),description:this.newForm.description.trim()});this.creating.set(false);this.detailTab.set('followup');this.detail.set(result);await this.refresh();this.resetEntry();this.success.set('Caso creado. El tiempo se descontará cuando el personal de soporte registre una atención.');});}
  select(c:SupportCase){this.selectId(c.id);}
  private selectId(id:number){void this.perform(async()=>{const selected=await this.api.get<CaseDetail>('/cases/'+id);this.resetHistoryDetail();this.detailTab.set('followup');this.detail.set(selected);this.creating.set(false);this.editing.set(false);this.statusPrompt.set(false);this.resetEntry();window.scrollTo({top:0,behavior:'smooth'});});}
  back(){if(this.busy())return;this.resetHistoryDetail();this.detail.set(null);this.editing.set(false);this.statusPrompt.set(false);this.error.set('');this.success.set('');}
  edit(){const c=this.detail();if(!c)return;this.editForm={title:c.title,description:c.description,channel:c.channel,assignedAdminId:c.assignedAdminId??null};this.editing.set(true);this.statusPrompt.set(false);}
  saveEdit(){void this.perform(async()=>{const c=this.detail();if(!c)return;if(!this.editForm.title.trim()||!this.editForm.description.trim())throw new Error('Completa el título y la descripción.');await this.api.put('/cases/'+c.id,{...this.editForm,title:this.editForm.title.trim(),description:this.editForm.description.trim()});this.detail.set(await this.api.get<CaseDetail>('/cases/'+c.id));this.editing.set(false);await this.refresh();this.success.set('Caso actualizado. La modificación quedó registrada en auditoría.');});}
  openStatus(){this.statusReason='';this.statusPrompt.set(true);this.editing.set(false);this.error.set('');}
  changeStatus(){void this.perform(async()=>{const c=this.detail();if(!c)return;if(!this.statusReason.trim())throw new Error('Describe el motivo del cambio de estado.');await this.api.post('/cases/'+c.id+'/status',{status:c.status==='OPEN'?'CLOSED':'OPEN',reason:this.statusReason.trim()});this.detail.set(await this.api.get<CaseDetail>('/cases/'+c.id));this.statusPrompt.set(false);await this.refresh();this.success.set('Estado actualizado. Se conservó el historial del caso.');});}
  entryTotal(){try{return toMinutes(this.entryHours,this.entryMinutes);}catch{return 0;}}
  entryBalance(){const c=this.detail();return c&&this.entryKind!=='COMMENT'?this.balance(c.institutionId,this.entryKind):0;}
  resetEntry(){this.entryDescription='';this.entryKind='COMMENT';this.entryHours=0;this.entryMinutes=0;this.entryInternal=false;}
  addEntry(){void this.perform(async()=>{const c=this.detail();if(!c)return;if(!this.entryDescription.trim())throw new Error('Describe la atención o comentario.');const charge=this.isSupportStaff()&&this.entryKind!=='COMMENT';const minutes=charge?toMinutes(this.entryHours,this.entryMinutes):0;if(charge&&minutes<=0)throw new Error('La atención debe registrar al menos 1 minuto.');if(charge&&minutes>this.entryBalance())throw new Error('El tiempo indicado excede el saldo disponible de este canal.');const result=await this.api.post<CaseDetail>('/cases/'+c.id+'/entries',{description:this.entryDescription.trim(),channel:charge?this.entryKind:null,minutes,internal:this.isSupportStaff()&&this.entryInternal});this.detail.set(result);this.resetEntry();await this.refresh();this.success.set(charge?'Atención registrada y saldo descontado correctamente.':'Comentario agregado al historial.');});}
  requestVoid(entry:CaseEntry){if(!this.isAdmin())return;this.voidEntry.set(entry);this.voidReason='';this.error.set('');}
  cancelEntry(){if(!this.isAdmin())return;void this.perform(async()=>{const c=this.detail(),e=this.voidEntry();if(!c||!e)return;if(!this.voidReason.trim())throw new Error('Escribe el motivo de anulación.');this.detail.set(await this.api.post<CaseDetail>(`/cases/${c.id}/entries/${e.id}/void`,{reason:this.voidReason.trim()}));this.voidEntry.set(null);await this.refresh();this.success.set('Atención anulada. El tiempo se devolvió al saldo y la entrada se conserva en el historial.');});}
}
