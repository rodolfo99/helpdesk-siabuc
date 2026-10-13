import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PageState, errorMessage } from './api.service';
import { newRequestId } from './payment-utils';

interface Attachment {
  id:number; caseId:number; entryId:number|null; filename:string; contentType:string;
  sizeBytes:number; sha256:string; uploadedBy:string; createdAt:string;
}

@Component({selector:'app-attachments',standalone:true,imports:[CommonModule],template:`
  <section class="attachments" aria-label="Archivos adjuntos">
    <div class="attachment-heading"><h3>Archivos adjuntos</h3><span class="muted">{{entryId === null ? 'Del caso' : 'De este elemento del historial'}}</span></div>
    @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
    @if(success()){<div class="notice success" role="status">{{success()}}</div>}
    @if(loading()){<p class="muted" role="status">Cargando archivos…</p>}
    @else {
      @if(!files().length){<p class="muted">No hay archivos adjuntos en este apartado.</p>}
      <ul class="file-list">
        @for(file of files();track file.id){<li>
          <div class="file-description"><strong>{{file.filename}}</strong><small>{{formatSize(file.sizeBytes)}} · {{file.uploadedBy}} · {{file.createdAt | date:'dd/MM/yyyy HH:mm'}}</small></div>
          <button type="button" class="btn secondary" [disabled]="busy()" (click)="download(file)" [attr.aria-label]="'Descargar '+file.filename">Descargar</button>
        </li>}
      </ul>
    }
    @if(caseStatus === 'OPEN'){
      <form class="file-upload" (submit)="upload($event,fileInput)">
        <label>Agregar archivo<input #fileInput type="file" accept=".png,.jpg,.jpeg,.pdf,.txt,.log,.csv,.json" [disabled]="busy()" (change)="selectFile($event)"></label>
        <p class="muted file-help">Capturas PNG/JPG, PDF y diagnósticos TXT, LOG, CSV o JSON en UTF-8. Máximo 10 MiB por archivo; 100 archivos o 100 MiB por caso. Revisa que no contenga contraseñas ni información ajena al caso. Los archivos de una nota interna conservan esa privacidad.</p>
        <button type="submit" class="btn primary" [disabled]="busy() || !selectedFile">{{busy() ? 'Procesando…' : 'Adjuntar archivo'}}</button>
      </form>
    } @else {<p class="muted file-help">El caso está cerrado. Sus archivos siguen disponibles para descargar.</p>}
  </section>
`,styles:[`
  .attachments{margin-top:1rem}.attachment-heading{display:flex;align-items:center;justify-content:space-between;gap:1rem}.attachment-heading h3{margin:0 0 .5rem}.file-list{list-style:none;padding:0;margin:.5rem 0 1rem}.file-list li{display:flex;gap:1rem;align-items:center;justify-content:space-between;padding:.85rem 0;border-bottom:1px solid #e3e8eb}.file-description{min-width:0;overflow-wrap:anywhere}.file-description strong{display:block}.file-description small{display:block;color:#637280;margin-top:.3rem}.file-upload{border-top:1px solid #e3e8eb;padding-top:1rem}.file-help{font-size:.85rem;line-height:1.6;margin:.5rem 0 1rem}.file-upload input{max-width:100%}@media(max-width:600px){.attachment-heading{display:block}.file-list li{align-items:flex-start;flex-wrap:wrap}}
`]})
export class AttachmentsComponent extends PageState implements OnChanges {
  @Input({required:true}) caseId!:number;
  @Input() entryId:number|null=null;
  @Input() caseStatus:'OPEN'|'CLOSED'='OPEN';
  @Output() updated = new EventEmitter<void>();
  private readonly http=inject(HttpClient);
  readonly files=signal<Attachment[]>([]);
  readonly loading=signal(false);
  selectedFile:File|null=null;
  private requestId='';
  private loadToken=0;
  ngOnChanges(){this.selectedFile=null;this.requestId='';this.success.set('');void this.load();}
  private path(){return '/api/cases/'+this.caseId+'/attachments'+(this.entryId===null?'':'?entryId='+this.entryId);}
  private async load(){
    const token=++this.loadToken;this.loading.set(true);this.error.set('');this.files.set([]);
    try{const files=await firstValueFrom(this.http.get<Attachment[]>(this.path()));if(token===this.loadToken)this.files.set(files);}
    catch(e){if(token===this.loadToken)this.error.set(errorMessage(e));}
    finally{if(token===this.loadToken)this.loading.set(false);}
  }
  selectFile(event:Event){
    this.selectedFile=(event.target as HTMLInputElement).files?.[0]??null;
    this.requestId=newRequestId();this.error.set('');this.success.set('');
    if(this.selectedFile && (this.selectedFile.size===0 || this.selectedFile.size>10*1024*1024)){
      this.error.set('El archivo debe ocupar entre 1 byte y 10 MiB.');this.selectedFile=null;
    }
  }
  upload(event:Event,input:HTMLInputElement){
    event.preventDefault();if(!this.selectedFile || this.caseStatus!=='OPEN')return;
    const file=this.selectedFile;const path=this.path();const token=this.loadToken;
    const form=new FormData();form.append('file',file,file.name);form.append('requestId',this.requestId);
    void this.perform(async()=>{
      await firstValueFrom(this.http.post<Attachment>(path,form));
      if(token!==this.loadToken)return;
      this.selectedFile=null;this.requestId='';input.value='';await this.load();
      this.success.set('Archivo adjuntado y registrado en el historial.');this.updated.emit();
    });
  }
  download(file:Attachment){
    void this.perform(async()=>{
      const blob=await firstValueFrom(this.http.get('/api/attachments/'+file.id+'/download',{responseType:'blob'}));
      const url=URL.createObjectURL(blob);const link=document.createElement('a');
      link.href=url;link.download=file.filename;document.body.appendChild(link);link.click();link.remove();
      setTimeout(()=>URL.revokeObjectURL(url),1000);
    });
  }
  formatSize(bytes:number){return bytes>=1024*1024?(bytes/1024/1024).toFixed(1)+' MiB':bytes>=1024?(bytes/1024).toFixed(1)+' KiB':bytes+' bytes';}
}
