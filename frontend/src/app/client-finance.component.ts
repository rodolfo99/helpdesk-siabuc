import {CommonModule} from '@angular/common';
import {Component,OnInit,inject,signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {ApiService,PageState} from './api.service';
import {PaymentMethod} from './models';
import {calendarDate,money,paymentMethodName} from './payment-utils';
import {PaymentRequestsComponent,RequestPolicy} from './payment-requests.component';
import {PaymentReceiptComponent} from './payment-receipt.component';
interface Policy extends RequestPolicy{validFrom:string;validUntil:string;agreedAmount:number;currency:string;emailMinutes:number;remoteMinutes:number;paidAmount:number;remainingAmount:number;creditedEmailMinutes:number;creditedRemoteMinutes:number;}
interface Payment{id:number;policyId:number|null;policyFolio:string|null;reference:string;amount:number;currency:string;paidOn:string;method:PaymentMethod;emailMinutes:number;remoteMinutes:number;voided:boolean;createdAt:string;}
interface Summary{institutionId:number;institutionName:string;policies:Policy[];payments:Payment[];}
interface Proof{id:number;paymentId:number;filename:string;contentType:string;sizeBytes:number;sha256:string;createdAt:string;}
@Component({selector:'app-client-finance',standalone:true,imports:[CommonModule,PaymentRequestsComponent,PaymentReceiptComponent],template:`
<section><div class="heading"><div><h2>Finanzas institucionales</h2><p>Consulta las pólizas, pagos y evidencias de tu institución; envía solicitudes para validación.</p></div><button class="btn secondary" (click)="load()" [disabled]="busy()">Actualizar información</button></div>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}
@if(loading()){<p role="status">Consultando acceso financiero…</p>}
@else if(!enabled()){<div class="notice">El portal financiero de tu institución no está habilitado. Solicita a administración que revise el acceso. Puedes continuar consultando tus casos de soporte.</div>}
@else if(summary();as s){
<h3>{{s.institutionName}}</h3><p class="muted">Los minutos contratados y acreditados se muestran por póliza o pago. El saldo actual de soporte también considera consumos. Enviar una solicitud no aumenta ese saldo.</p>
<h3>Pólizas</h3><div class="table-wrap"><table><thead><tr><th>Folio / vigencia</th><th>Importe / pagado / pendiente</th><th>Minutos contratados</th><th>Minutos acreditados</th><th>Estado</th></tr></thead><tbody>@for(p of s.policies;track p.id){<tr><td>{{p.folio}}<br>{{date(p.validFrom)}} a {{date(p.validUntil)}}</td><td>{{money(p.agreedAmount,p.currency)}}<br>Pagado {{money(p.paidAmount,p.currency)}}<br>Pendiente {{money(p.remainingAmount,p.currency)}}</td><td>{{p.emailMinutes}} correo / {{p.remoteMinutes}} remoto</td><td>{{p.creditedEmailMinutes}} correo / {{p.creditedRemoteMinutes}} remoto</td><td>{{p.status==='ACTIVE'?'Activa':'Cancelada'}}</td></tr>}@empty{<tr><td colspan="5">No hay pólizas registradas.</td></tr>}</tbody></table></div>
<h3>Pagos registrados</h3><p class="muted">Los pagos anulados se conservan y no cuentan como abonos ni minutos acreditados.</p>
@for(p of s.payments;track p.id){<details class="payment" (toggle)="toggle(p.id,$event)"><summary>Pago #{{p.id}} · {{p.reference}} · {{money(p.amount,p.currency)}} · {{p.voided?'Anulado':'Registrado'}}</summary><p>{{date(p.paidOn)}} · {{method(p.method)}} · {{p.policyFolio || 'Sin póliza'}}. Minutos: {{p.emailMinutes}} correo y {{p.remoteMinutes}} remoto.</p>
@if(expanded().has(p.id)){<h4>Comprobantes del pago</h4>@if(proofs()[p.id];as files){<ul>@for(file of files;track file.id){<li>{{file.filename}} · {{file.createdAt | date:'dd/MM/yyyy HH:mm'}} <button class="btn secondary" (click)="download(file)" [disabled]="busy()">Descargar {{file.filename}}</button></li>}@empty{<li>No hay archivos de evidencia adjuntos.</li>}</ul>}@else{<p>Cargando comprobantes…</p>}<app-payment-receipt [paymentId]="p.id" [clientMode]="true"/>}</details>
}@empty{<p>No hay pagos registrados.</p>}
<app-payment-requests [clientMode]="true" [institutionId]="s.institutionId" [clientPolicies]="s.policies"/>
}</section>`,styles:[`.heading{display:flex;justify-content:space-between;gap:1rem;flex-wrap:wrap}.table-wrap{overflow:auto;margin-bottom:1.5rem}.payment{padding:1rem;border:1px solid var(--line);border-radius:8px;margin:.8rem 0}.payment summary{cursor:pointer;font-weight:600}.payment p{line-height:1.6}.payment li{margin:.6rem 0;overflow-wrap:anywhere}.payment li button{margin:.5rem}th,td{min-width:130px}`]})
export class ClientFinanceComponent extends PageState implements OnInit{
 private api=inject(ApiService);private http=inject(HttpClient);readonly enabled=signal(false);readonly loading=signal(true);readonly summary=signal<Summary|null>(null);readonly expanded=signal<Set<number>>(new Set());readonly proofs=signal<Record<number,Proof[]>>({});readonly money=money;readonly date=calendarDate;readonly method=paymentMethodName;
 ngOnInit(){this.load();}
 load(){void this.perform(async()=>{this.loading.set(true);this.summary.set(null);this.proofs.set({});this.expanded.set(new Set());try{const access=await this.api.get<{enabled:boolean}>('/client-finance/access');this.enabled.set(access.enabled);if(access.enabled)this.summary.set(await this.api.get<Summary>('/client-finance'));}finally{this.loading.set(false);}});}
 toggle(id:number,event:Event){const open=(event.target as HTMLDetailsElement).open;this.expanded.update(ids=>{const next=new Set(ids);if(open)next.add(id);else next.delete(id);return next;});if(open&&!this.proofs()[id])void this.perform(async()=>{const files=await this.api.get<Proof[]>('/client-finance/payments/'+id+'/attachments');this.proofs.update(before=>({...before,[id]:files}));});}
 download(file:Proof){void this.perform(async()=>{const blob=await firstValueFrom(this.http.get('/api/client-finance/payment-attachments/'+file.id+'/download',{responseType:'blob'}));const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=file.filename;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);});}
}
