import {Component,Input,OnChanges,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HttpClient,HttpErrorResponse} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {PageState} from './api.service';
@Component({selector:'app-payment-receipt',standalone:true,imports:[CommonModule],template:`
<section class="receipt-download" aria-label="Recibo administrativo">
  <h3>Recibo administrativo</h3>
  <p>Descarga el recibo PDF con folio, importe, horas y método del pago #{{paymentId}}.@if(!clientMode){ Incluye responsable y descripción del registro.} No es comprobante fiscal ni factura.</p>
  <p class="muted">La primera descarga conserva una copia de los datos de emisión. Las siguientes usan el mismo folio y muestran si el pago fue anulado. Descargar no registra otro cobro ni acredita minutos.</p>
  @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
  @if(success()){<div class="notice success" role="status">{{success()}}</div>}
  <button type="button" class="btn secondary" (click)="download()" [disabled]="busy()">{{busy()?'Generando recibo…':'Descargar recibo PDF'}}</button>
</section>
`,styles:[`.receipt-download{margin-top:1rem;padding:1rem;border:1px solid #d8e4e0;border-radius:10px;background:#f7fbfa}.receipt-download h3{margin:0 0 .6rem}.receipt-download p{line-height:1.6;margin:.6rem 0;font-size:.9rem}.receipt-download .btn{margin-top:.4rem;white-space:normal}`]})
export class PaymentReceiptComponent extends PageState implements OnChanges{
  @Input({required:true}) paymentId!:number;
  @Input() clientMode=false;
  private readonly http=inject(HttpClient);private generation=0;
  ngOnChanges(){this.generation++;this.error.set('');this.success.set('');}
  download(){void this.perform(async()=>{
    const token=this.generation,id=this.paymentId;
    const path=this.clientMode?'/api/client-finance/payments/':'/api/payments/';
    try{
      const response=await firstValueFrom(this.http.get(path+id+'/receipt.pdf',{responseType:'blob',observe:'response'}));
      if(token!==this.generation)return;
      if(!response.body)throw new Error('El servidor no devolvió el recibo.');
      const folio=response.headers.get('X-Receipt-Folio');
      const filename=folio&&/^SIABUC-REC-\d+$/.test(folio)?folio+'.pdf':'SIABUC-recibo-pago-'+id+'.pdf';
      const url=URL.createObjectURL(response.body),link=document.createElement('a');link.href=url;link.download=filename;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
      this.success.set('Recibo descargado. El pago y sus minutos permanecen iguales.');
    }catch(error){
      if(token!==this.generation)return;
      if(error instanceof HttpErrorResponse&&error.error instanceof Blob){let message='No se pudo descargar el recibo ('+error.status+').';try{const data=JSON.parse(await error.error.text());if(typeof data.message==='string')message=data.message;}catch{}throw new Error(message);}throw error;
    }
  });}
}
