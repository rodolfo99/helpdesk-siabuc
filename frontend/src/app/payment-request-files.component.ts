import {CommonModule} from '@angular/common';
import {Component,ElementRef,EventEmitter,Input,OnChanges,Output,ViewChild,inject,signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {PageState,errorMessage} from './api.service';
import {newRequestId} from './payment-utils';
export interface RequestProof{id:number;requestId:number;filename:string;contentType:string;sizeBytes:number;sha256:string;uploadedBy:string;createdAt:string;}
@Component({selector:'app-payment-request-files',standalone:true,imports:[CommonModule],template:`
<section aria-label="Evidencia de la solicitud"><h3>Evidencia de la solicitud</h3>
<p class="muted">Sube PDF, PNG o JPG/JPEG antes de la revisión. Adjuntar evidencia no registra dinero ni acredita minutos. Máximo 10 MiB por archivo, 10 archivos y 50 MiB en total.</p>
@if(error()){<p class="notice error" role="alert">{{error()}}</p>}@if(success()){<p class="notice success" role="status">{{success()}}</p>}
@if(loading()){<p>Cargando evidencia…</p>}@else{<ul>@for(file of files();track file.id){<li><strong>{{file.filename}}</strong> · {{file.sizeBytes | number}} bytes · {{file.uploadedBy}} · {{file.createdAt | date:'dd/MM/yyyy HH:mm'}} <button type="button" class="btn secondary" (click)="download(file)" [disabled]="busy()">Descargar {{file.filename}}</button></li>}@empty{<li>Sin comprobantes adjuntos.</li>}</ul>}
@if(status==='PENDING'){<form (submit)="upload($event)"><label>Comprobante de la solicitud<input #fileInput type="file" accept=".pdf,.png,.jpg,.jpeg" [disabled]="busy()" (change)="choose($event)"></label><button class="btn primary" type="submit" [disabled]="busy()||!selectedFile">Adjuntar evidencia</button></form>}
@else{<p class="muted">Solicitud resuelta: la evidencia permanece disponible y ya no puede modificarse.</p>}
</section>`,styles:[`section{margin-top:1rem;border-top:1px solid var(--line);padding-top:1rem}li{margin:.6rem 0;overflow-wrap:anywhere}li button{margin:.4rem}form{display:flex;gap:1rem;align-items:end;flex-wrap:wrap}form label{flex:1}input{max-width:100%}`]})
export class PaymentRequestFilesComponent extends PageState implements OnChanges{
 @Input({required:true}) requestId!:number;@Input() status='PENDING';@Input() clientMode=false;@Output() updated=new EventEmitter<void>();
 @ViewChild('fileInput') input?:ElementRef<HTMLInputElement>;
 private http=inject(HttpClient);readonly files=signal<RequestProof[]>([]);readonly loading=signal(false);selectedFile:File|null=null;private uuid='';private generation=0;
 path(){return '/api'+(this.clientMode?'/client-finance':'')+'/payment-requests';}
 ngOnChanges(){this.generation++;this.selectedFile=null;this.uuid='';if(this.input)this.input.nativeElement.value='';void this.load();}
 async load(){const generation=this.generation;this.loading.set(true);this.error.set('');try{const files=await firstValueFrom(this.http.get<RequestProof[]>(this.path()+'/'+this.requestId+'/attachments'));if(generation===this.generation)this.files.set(files);}catch(e){if(generation===this.generation)this.error.set(errorMessage(e));}finally{if(generation===this.generation)this.loading.set(false);}}
 choose(event:Event){this.selectedFile=(event.target as HTMLInputElement).files?.[0]??null;this.uuid=newRequestId();this.error.set('');if(this.selectedFile&&(!/\.(pdf|png|jpe?g)$/i.test(this.selectedFile.name)||this.selectedFile.size<1||this.selectedFile.size>10485760)){this.error.set('Selecciona un PDF, PNG o JPG/JPEG de hasta 10 MiB.');this.selectedFile=null;}}
 upload(event:Event){event.preventDefault();if(!this.selectedFile)return;const form=new FormData();form.append('file',this.selectedFile);form.append('requestId',this.uuid);const generation=this.generation,path=this.path()+'/'+this.requestId+'/attachments';void this.perform(async()=>{await firstValueFrom(this.http.post(path,form));if(generation!==this.generation)return;this.selectedFile=null;if(this.input)this.input.nativeElement.value='';await this.load();this.success.set('Evidencia guardada. La solicitud sigue pendiente de validación; el saldo no cambió.');this.updated.emit();});}
 download(file:RequestProof){void this.perform(async()=>{const blob=await firstValueFrom(this.http.get(this.path()+'/attachments/'+file.id+'/download',{responseType:'blob'}));const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=file.filename;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);});}
}
