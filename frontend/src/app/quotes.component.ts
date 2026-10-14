import {Component,OnInit,inject,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ApiService,PageState} from './api.service';
import {Institution,SupportPolicy} from './models';
import {formatTime,toMinutes} from './time';
import {money,parseAmount,calendarDate} from './payment-utils';
import {PackageVersion} from './support-packages.component';
export interface SupportQuote {
 id:number;institutionId:number;institutionName:string;folio:string;description:string;expiresOn:string;validFrom:string;validUntil:string;
 agreedAmount:number;currency:string;emailMinutes:number;remoteMinutes:number;status:'DRAFT'|'SENT'|'ACCEPTED'|'REJECTED'|'CONVERTED';
 previousPolicyId:number|null;previousPolicyFolio:string|null;convertedPolicyId:number|null;convertedPolicyFolio:string|null;
 createdBy:string;createdAt:string;updatedAt:string;sentAt:string|null;sentEvidence:string;decisionContact:string|null;
 decisionEvidence:string|null;decidedOn:string|null;decisionRecordedBy:string|null;decisionRecordedAt:string|null;expired:boolean;
}
export interface QuoteDetail {quote:SupportQuote;packageSnapshot:PackageVersion|null;packageOverrideReason:string|null;history:{id:number;quoteId:number;action:string;description:string;createdBy:string;createdAt:string}[];}
export function quoteToday(){const p=new Intl.DateTimeFormat('en-US',{timeZone:'America/Mexico_City',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());return ['year','month','day'].map(k=>p.find(v=>v.type===k)?.value).join('-');}
@Component({selector:'app-quotes',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./quotes.component.html',styles:[`
.quote-detail{padding:24px;margin-bottom:22px}.quote-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.quote-meta p{font-size:12px}.quote-actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:20px}.quote-history{padding:0 24px 24px;list-style:none}.quote-history li{padding:16px 0;border-bottom:1px solid var(--line)}.quote-history p,.quote-evidence{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.7}.quote-history small{color:var(--muted)}.quote-action-form{margin-top:24px;padding-top:20px;border-top:1px solid var(--line)}.quote-input{padding:0 24px 24px}.quote-input fieldset{border:0;padding:0;min-width:0}.quote-link{background:none;border:0;text-align:left;padding:0;color:#14786d;font-weight:700}.quote-tags{display:flex;gap:8px;flex-wrap:wrap}@media(max-width:650px){.quote-meta{grid-template-columns:1fr}}
`]})
export class QuotesComponent extends PageState implements OnInit {
 readonly api=inject(ApiService);readonly items=signal<SupportQuote[]>([]);readonly institutions=signal<Institution[]>([]);readonly policies=signal<SupportPolicy[]>([]);readonly selected=signal<QuoteDetail|null>(null);readonly creating=signal(false);readonly editing=signal(false);
 readonly packages=signal<PackageVersion[]>([]);
 readonly money=money;readonly time=formatTime;readonly dateOnly=calendarDate;search='';statusFilter='all';institutionFilter:number|null=null;
 form=this.blank();emailHours:number|null=0;emailMinutes:number|null=0;remoteHours:number|null=0;remoteMinutes:number|null=0;
 action:''|'send'|'decision'|'convert'='';evidence='';contactName='';decidedOn=quoteToday();decision:'ACCEPTED'|'REJECTED'='ACCEPTED';policyFolio='';
 blank(){const today=quoteToday();return{institutionId:null as number|null,previousPolicyId:null as number|null,folio:'',description:'',expiresOn:today,validFrom:today,validUntil:today,agreedAmount:'',packageVersionId:null as number|null,packageOverrideReason:''};}
 ngOnInit(){this.load();}load(){void this.perform(()=>this.refresh());}
 async refresh(){const [items,institutions,policies,packages]=await Promise.all([this.api.get<SupportQuote[]>('/quotes'),this.api.get<Institution[]>('/institutions'),this.api.get<SupportPolicy[]>('/policies'),this.api.get<PackageVersion[]>('/support-packages')]);this.items.set(items);this.institutions.set(institutions);this.policies.set(policies);this.packages.set(packages);}
 filtered(){const q=this.search.trim().toLocaleLowerCase();return this.items().filter(i=>(!this.institutionFilter||i.institutionId===this.institutionFilter)&&(this.statusFilter==='all'||i.status===this.statusFilter)&&`${i.folio} ${i.institutionName} ${i.description}`.toLocaleLowerCase().includes(q));}
 statusName(s:string){return ({DRAFT:'Borrador',SENT:'Emitida',ACCEPTED:'Aceptada',REJECTED:'Rechazada',CONVERTED:'Convertida a póliza'} as Record<string,string>)[s]||s;}
 historyName(s:string){return ({CREATE_QUOTE:'Creación',UPDATE_QUOTE:'Edición del borrador',SEND_QUOTE:'Emisión',ACCEPT_QUOTE:'Aceptación',REJECT_QUOTE:'Rechazo',CONVERT_QUOTE:'Conversión a póliza'} as Record<string,string>)[s]||s;}
 availablePackages(){const items=this.packages().filter(p=>p.active);const previous=this.selected()?.packageSnapshot;if(previous&&!items.some(p=>p.id===previous.id))return[previous,...items];return items;}
 applyPackage(){const p=this.availablePackages().find(p=>p.id===this.form.packageVersionId);if(!p)return;this.form.description=p.description;this.form.agreedAmount=Number(p.amount).toFixed(2);this.emailHours=Math.floor(p.emailMinutes/60);this.emailMinutes=p.emailMinutes%60;this.remoteHours=Math.floor(p.remoteMinutes/60);this.remoteMinutes=p.remoteMinutes%60;this.form.packageOverrideReason='';}
 activeInstitutions(){return this.institutions().filter(i=>i.active);}previousPolicies(){return this.policies().filter(p=>p.institutionId===this.form.institutionId);}
 institutionChanged(){this.form.previousPolicyId=null;}
 usePrevious(){const p=this.policies().find(p=>p.id===this.form.previousPolicyId);if(!p)return;this.form.description=p.description;this.form.agreedAmount=Number(p.agreedAmount).toFixed(2);this.emailHours=Math.floor(p.emailMinutes/60);this.emailMinutes=p.emailMinutes%60;this.remoteHours=Math.floor(p.remoteMinutes/60);this.remoteMinutes=p.remoteMinutes%60;const start=new Date(p.validUntil+'T12:00:00Z');start.setUTCDate(start.getUTCDate()+1);this.form.validFrom=start.toISOString().slice(0,10);const end=new Date(start);end.setUTCFullYear(end.getUTCFullYear()+1);end.setUTCDate(end.getUTCDate()-1);this.form.validUntil=end.toISOString().slice(0,10);}
 create(){if(this.busy())return;this.form=this.blank();this.emailHours=this.emailMinutes=this.remoteHours=this.remoteMinutes=0;this.selected.set(null);this.creating.set(true);this.editing.set(false);this.action='';this.error.set('');this.success.set('');}
 select(q:SupportQuote){void this.perform(async()=>{this.selected.set(await this.api.get<QuoteDetail>('/quotes/'+q.id));this.creating.set(false);this.editing.set(false);this.action='';});}
 back(){if(this.busy())return;this.selected.set(null);this.creating.set(false);this.editing.set(false);this.action='';this.error.set('');this.success.set('');}
 edit(){const q=this.selected()?.quote;if(!q||q.status!=='DRAFT'||this.busy())return;this.form={institutionId:q.institutionId,previousPolicyId:q.previousPolicyId,folio:q.folio,description:q.description,expiresOn:q.expiresOn,validFrom:q.validFrom,validUntil:q.validUntil,agreedAmount:Number(q.agreedAmount).toFixed(2),packageVersionId:this.selected()?.packageSnapshot?.id??null,packageOverrideReason:this.selected()?.packageOverrideReason??''};this.emailHours=Math.floor(q.emailMinutes/60);this.emailMinutes=q.emailMinutes%60;this.remoteHours=Math.floor(q.remoteMinutes/60);this.remoteMinutes=q.remoteMinutes%60;this.editing.set(true);this.action='';}
 save(){void this.perform(async()=>{if(!this.form.institutionId)throw new Error('Selecciona una institución.');const emailMinutes=toMinutes(this.emailHours,this.emailMinutes),remoteMinutes=toMinutes(this.remoteHours,this.remoteMinutes);if(emailMinutes+remoteMinutes<=0)throw new Error('Cotiza tiempo en al menos un canal.');const body={...this.form,agreedAmount:parseAmount(this.form.agreedAmount),emailMinutes,remoteMinutes};const id=this.editing()?this.selected()?.quote.id:null;this.selected.set(id?await this.api.put<QuoteDetail>('/quotes/'+id,body):await this.api.post<QuoteDetail>('/quotes',body));this.creating.set(false);this.editing.set(false);await this.refresh();this.success.set('Borrador guardado. No se acreditaron minutos.');});}
 prepare(action:'send'|'decision'|'convert',decision:'ACCEPTED'|'REJECTED'='ACCEPTED'){this.action=action;this.evidence='';this.contactName='';this.decidedOn=quoteToday();this.decision=decision;this.policyFolio='';this.error.set('');this.success.set('');}
 submitAction(){void this.perform(async()=>{const q=this.selected()?.quote;if(!q)return;let endpoint:string,body:unknown;
 if(this.action==='send'){endpoint='/send';body={evidence:this.evidence};}else if(this.action==='decision'){endpoint='/decision';body={decision:this.decision,contactName:this.contactName,evidence:this.evidence,decidedOn:this.decidedOn};}else if(this.action==='convert'){endpoint='/convert';body={policyFolio:this.policyFolio};}else return;
 this.selected.set(await this.api.post<QuoteDetail>('/quotes/'+q.id+endpoint,body));this.action='';await this.refresh();this.success.set(endpoint==='/convert'?'Póliza creada sin acreditar soporte. Registra el pago para acreditar los minutos acordados.':'Constancia registrada en el historial.');});}
}
