import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PageState, errorMessage } from './api.service';
import { newRequestId } from './payment-utils';

interface PaymentAttachment {
  id:number; paymentId:number; filename:string; contentType:string; sizeBytes:number;
  sha256:string; uploadedBy:string; uploadedName:string; createdAt:string;
}

@Component({selector:'app-payment-attachments',standalone:true,imports:[CommonModule],template:`
  <section class="proofs" aria-label="Comprobantes de pago">
    <h3>Comprobantes de pago</h3>
    <p class="muted">Evidencia del pago #{{paymentId}}, disponible para administración y cobranza. Adjuntar un archivo no registra otro pago ni acredita minutos.</p>
    @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
    @if(success()){<div class="notice success" role="status">{{success()}}</div>}
    @if(loading()){<p class="muted" role="status">Cargando comprobantes…</p>}
    @else {
      @if(!files().length){<p class="muted">No hay comprobantes adjuntos para este pago.</p>}
      <ul class="proof-list">
        @for(file of files();track file.id){<li>
          <div class="proof-description"><strong>{{file.filename}}</strong>
            <small>{{formatSize(file.sizeBytes)}} · {{file.uploadedName}} ({{file.uploadedBy}}) · {{file.createdAt | date:'dd/MM/yyyy HH:mm'}}</small>
            <details><summary>Ver huella SHA-256</summary><code>{{file.sha256}}</code></details>
          </div>
          <button type="button" class="btn secondary" [disabled]="busy()" (click)="download(file)" [attr.aria-label]="'Descargar '+file.filename">Descargar</button>
        </li>}
      </ul>
    }
    <form class="proof-upload" (submit)="upload($event,fileInput)">
      <label>Seleccionar comprobante<input #fileInput type="file" accept=".pdf,.png,.jpg,.jpeg" [disabled]="busy()" (change)="selectFile($event)"></label>
      <p class="muted help">PDF, PNG o JPG/JPEG; máximo 10 MiB por archivo, 10 comprobantes o 50 MiB por pago. Los archivos anteriores se conservan: para corregir una evidencia, adjunta otra. También puedes complementar un pago anulado.</p>
      <button type="submit" class="btn primary" [disabled]="busy() || !selectedFile">{{busy() ? 'Procesando…' : 'Adjuntar comprobante'}}</button>
    </form>
  </section>
`,styles:[`
  .proofs{margin-top:1.25rem;padding-top:1rem;border-top:1px solid #e3e8eb}.proofs h3{margin:0 0 .65rem}.proof-list{list-style:none;padding:0;margin:.5rem 0 1rem}.proof-list li{display:flex;gap:1rem;align-items:center;justify-content:space-between;padding:.85rem 0;border-bottom:1px solid #e3e8eb}.proof-description{min-width:0;overflow-wrap:anywhere}.proof-description strong{display:block}.proof-description small{display:block;color:#637280;margin-top:.3rem}.proof-description details{margin-top:.4rem;font-size:.8rem}.proof-description summary{cursor:pointer}.proof-description code{display:block;white-space:normal;overflow-wrap:anywhere;margin-top:.35rem}.proof-upload{padding-top:1rem}.help{font-size:.85rem;line-height:1.6;margin:.5rem 0 1rem}.proof-upload input{max-width:100%}@media(max-width:600px){.proof-list li{align-items:flex-start;flex-wrap:wrap}}
`]})
export class PaymentAttachmentsComponent extends PageState implements OnChanges {
  @Input({required:true}) paymentId!:number;
  @Output() updated=new EventEmitter<void>();
  @ViewChild('fileInput') private fileInput?:ElementRef<HTMLInputElement>;
  private readonly http=inject(HttpClient);
  readonly files=signal<PaymentAttachment[]>([]);
  readonly loading=signal(false);
  selectedFile:File|null=null;
  private requestId='';
  private loadToken=0;
  ngOnChanges(){this.selectedFile=null;this.requestId='';if(this.fileInput)this.fileInput.nativeElement.value='';this.success.set('');void this.load();}
  private path(){return '/api/payments/'+this.paymentId+'/attachments';}
  private async load(){
    const token=++this.loadToken;this.loading.set(true);this.error.set('');this.files.set([]);
    try{const files=await firstValueFrom(this.http.get<PaymentAttachment[]>(this.path()));if(token===this.loadToken)this.files.set(files);}
    catch(e){if(token===this.loadToken)this.error.set(errorMessage(e));}
    finally{if(token===this.loadToken)this.loading.set(false);}
  }
  selectFile(event:Event){
    this.selectedFile=(event.target as HTMLInputElement).files?.[0]??null;
    this.requestId=newRequestId();this.error.set('');this.success.set('');
    if(this.selectedFile && (this.selectedFile.size===0 || this.selectedFile.size>10*1024*1024)){
      this.error.set('El comprobante debe ocupar entre 1 byte y 10 MiB.');this.selectedFile=null;
    } else if(this.selectedFile && !/\.(pdf|png|jpe?g)$/i.test(this.selectedFile.name)){
      this.error.set('Selecciona un comprobante PDF, PNG o JPG/JPEG.');this.selectedFile=null;
    }
  }
  upload(event:Event,input:HTMLInputElement){
    event.preventDefault();if(!this.selectedFile)return;
    const path=this.path();const token=this.loadToken;const form=new FormData();
    form.append('file',this.selectedFile,this.selectedFile.name);form.append('requestId',this.requestId);
    void this.perform(async()=>{
      await firstValueFrom(this.http.post<PaymentAttachment>(path,form));if(token!==this.loadToken)return;
      this.selectedFile=null;this.requestId='';input.value='';await this.load();
      this.success.set('Comprobante adjuntado. El importe y los minutos del pago no cambiaron.');this.updated.emit();
    });
  }
  download(file:PaymentAttachment){
    void this.perform(async()=>{
      const blob=await firstValueFrom(this.http.get('/api/payment-attachments/'+file.id+'/download',{responseType:'blob'}));
      const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=file.filename;
      document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    });
  }
  formatSize(bytes:number){return bytes>=1024*1024?(bytes/1024/1024).toFixed(1)+' MiB':bytes>=1024?(bytes/1024).toFixed(1)+' KiB':bytes+' bytes';}
}
