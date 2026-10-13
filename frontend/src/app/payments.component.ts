import {Component,OnInit,inject,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ApiService,PageState} from './api.service';
import {Institution,Payment,PaymentMethod} from './models';
import {formatTime,toMinutes} from './time';
import {money,parseAmount,newRequestId} from './payment-utils';
@Component({selector:'app-payments',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./payments.component.html'})
export class PaymentsComponent extends PageState implements OnInit {
  readonly api=inject(ApiService);readonly payments=signal<Payment[]>([]);readonly institutions=signal<Institution[]>([]);readonly creating=signal(false);readonly voidPayment=signal<Payment|null>(null);readonly selected=signal<Payment|null>(null);readonly time=formatTime;readonly money=money;
  search='';institutionFilter:number|null=null;dateFrom='';dateTo='';statusFilter='all';reason='';form=this.blank();emailHours:number|null=0;emailMinutes:number|null=0;remoteHours:number|null=0;remoteMinutes:number|null=0;
  private requestId=newRequestId();private submittedFingerprint:string|null=null;
  blank(){const now=new Date();const day=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;return {institutionId:null as number|null,reference:'',amount:'',currency:'MXN',paidOn:day,method:'TRANSFER' as PaymentMethod,notes:''};}
  ngOnInit(){this.load();}isAdmin(){return this.api.user()?.role==='ADMIN';}
  load(){void this.perform(()=>this.refresh());}
  async refresh(){const [payments,institutions]=await Promise.all([this.api.get<Payment[]>('/payments'),this.api.get<Institution[]>('/institutions')]);this.payments.set(payments);this.institutions.set(institutions);}
  activeInstitutions(){return this.institutions().filter(i=>i.active);}
  institution(id:number|null){return this.institutions().find(i=>i.id===id);}
  filtered(){const q=this.search.trim().toLocaleLowerCase();return this.payments().filter(p=>(!this.institutionFilter||p.institutionId===this.institutionFilter)&&(!this.dateFrom||p.paidOn>=this.dateFrom)&&(!this.dateTo||p.paidOn<=this.dateTo)&&(this.statusFilter==='all'||p.voided===(this.statusFilter==='void'))&&`${p.id} ${p.reference} ${p.institutionName} ${p.notes} ${p.createdBy}`.toLocaleLowerCase().includes(q)).sort((a,b)=>b.createdAt.localeCompare(a.createdAt));}
  methodName(method:PaymentMethod){return {TRANSFER:'Transferencia',CASH:'Efectivo',CARD:'Tarjeta',OTHER:'Otro'}[method];}
  creditedTotal(channel:'email'|'remote'){return this.payments().filter(p=>!p.voided).reduce((total,p)=>total+(channel==='email'?p.emailMinutes:p.remoteMinutes),0);}
  create(){this.form=this.blank();this.emailHours=this.emailMinutes=this.remoteHours=this.remoteMinutes=0;this.requestId=newRequestId();this.submittedFingerprint=null;this.creating.set(true);this.selected.set(null);this.error.set('');this.success.set('');}
  previewMinutes(channel:'email'|'remote'){try{return channel==='email'?toMinutes(this.emailHours,this.emailMinutes):toMinutes(this.remoteHours,this.remoteMinutes);}catch{return 0;}}
  save(){void this.perform(async()=>{
    if(!this.form.institutionId)throw new Error('Selecciona una institución activa.');
    const institution=this.institution(this.form.institutionId);if(!institution?.active)throw new Error('La institución debe estar activa para acreditar soporte.');
    const emailMinutes=toMinutes(this.emailHours,this.emailMinutes),remoteMinutes=toMinutes(this.remoteHours,this.remoteMinutes);
    if(emailMinutes+remoteMinutes<=0)throw new Error('El pago debe acreditar al menos un minuto de correo o de escritorio remoto.');
    if(emailMinutes+institution.emailAllocatedMinutes>1000000000||remoteMinutes+institution.remoteAllocatedMinutes>1000000000)throw new Error('La acreditación excedería el límite de tiempo asignado de la institución.');
    const body={institutionId:this.form.institutionId,reference:this.form.reference.trim(),amount:parseAmount(this.form.amount),currency:this.form.currency.trim().toUpperCase(),paidOn:this.form.paidOn,method:this.form.method,emailMinutes,remoteMinutes,notes:this.form.notes.trim()};
    if(!body.reference||!body.notes)throw new Error('Completa la referencia y la descripción del pago.');
    if(!/^[A-Z]{3}$/.test(body.currency))throw new Error('La moneda debe ser un código de tres letras, por ejemplo MXN.');
    if(!/^\d{4}-\d{2}-\d{2}$/.test(body.paidOn))throw new Error('Selecciona la fecha del pago.');
    const fingerprint=JSON.stringify(body);if(this.submittedFingerprint!==null&&this.submittedFingerprint!==fingerprint)this.requestId=newRequestId();this.submittedFingerprint=fingerprint;
    const result=await this.api.post<Payment>('/payments',{...body,requestId:this.requestId});
    this.creating.set(false);this.selected.set(result);this.requestId=newRequestId();this.submittedFingerprint=null;
    await this.refresh();this.success.set('Pago registrado y tiempo acreditado. Los avisos de correo se procesarán en la cola de notificaciones.');
  });}
  canVoid(p:Payment){const i=this.institution(p.institutionId);return !!i&&!p.voided&&i.emailRemainingMinutes>=p.emailMinutes&&i.remoteRemainingMinutes>=p.remoteMinutes;}
  requestVoid(p:Payment){this.reason='';this.error.set('');this.voidPayment.set(p);}
  cancelPayment(){void this.perform(async()=>{const payment=this.voidPayment();if(!payment)return;if(!this.reason.trim())throw new Error('Escribe el motivo de la anulación.');if(!this.canVoid(payment))throw new Error('No hay saldo suficiente en ambos canales para retirar todo el tiempo acreditado por este pago.');const result=await this.api.post<Payment>('/payments/'+payment.id+'/void',{reason:this.reason.trim()});this.voidPayment.set(null);this.selected.set(result);await this.refresh();this.success.set('Pago anulado y acreditación retirada. El registro permanece en el historial.');});}
}
