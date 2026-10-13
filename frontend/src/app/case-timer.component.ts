import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState, errorMessage } from './api.service';

interface WorkTimer { id:number; caseId:number; status:'RUNNING'|'PAUSED'|'FINISHED'|'COMMITTED'|'CANCELLED'; elapsedSeconds:number; suggestedMinutes:number; entryId:number|null; confirmedMinutes:number|null; }

@Component({selector:'app-case-timer',standalone:true,imports:[FormsModule],styles:[`
  :host{display:block;margin:1rem 0}.timer-box{padding:1rem;border:1px solid var(--line,#dce3eb);border-radius:12px;background:var(--surface,#fff)}
  .timer-clock{font-size:1.8rem;font-variant-numeric:tabular-nums;margin:.5rem 0}.timer-actions{display:flex;gap:.5rem;flex-wrap:wrap}.timer-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:.75rem}form{margin-top:1rem}small{display:block;margin:.35rem 0}.timer-confirm{background:#fff7e5;padding:1rem;border-radius:8px}
`],template:`
@if(api.user()?.role==='ADMIN'){
<section class="timer-box" aria-label="Cronómetro de atención"><h3>Cronómetro de atención</h3>
<small>El tiempo se conserva al salir. Finaliza y confirma los minutos para descontarlos del saldo.</small>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}
@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(timer();as t){
<div class="timer-clock" aria-live="off">{{clock()}}</div><p>{{statusLabel(t.status)}}</p>
<div class="timer-actions">
@if(t.status==='RUNNING'){<button type="button" class="btn" [disabled]="busy()" (click)="action('pause')">Pausar</button>}
@if(t.status==='PAUSED'){<button type="button" class="btn" [disabled]="busy()||caseStatus==='CLOSED'" (click)="action('resume')">Continuar</button>}
@if(t.status==='RUNNING'||t.status==='PAUSED'){<button type="button" class="btn primary" [disabled]="busy()" (click)="action('finish')">Finalizar atención</button>}
@if(t.status==='RUNNING'||t.status==='PAUSED'||t.status==='FINISHED'){<button type="button" class="btn" [disabled]="busy()" (click)="discardRequested=true">Descartar cronómetro</button>}
</div>
@if(discardRequested){<div class="timer-confirm"><p>¿Descartar este cronómetro? No se descontarán minutos. La acción queda registrada.</p><button type="button" class="btn" [disabled]="busy()" (click)="action('cancel')">Sí, descartar</button> <button type="button" class="btn" (click)="discardRequested=false">Conservar</button></div>}
@if(t.status==='FINISHED'){
<form (ngSubmit)="commit()" #commitForm="ngForm"><h4>Confirmar atención</h4><p>Tiempo sugerido: {{t.suggestedMinutes}} minutos, redondeado hacia arriba.</p>
<div class="timer-grid"><label>Minutos a descontar<input name="timerMinutes" type="number" min="1" max="1000000000" step="1" required [(ngModel)]="minutes"></label>
<label>Canal<select name="timerChannel" [(ngModel)]="channel"><option value="EMAIL">Correo</option><option value="REMOTE">Escritorio remoto</option></select></label></div>
<label>Descripción de la atención<textarea name="timerDescription" required maxlength="7500" rows="3" [(ngModel)]="description"></textarea></label>
@if(minutes!==t.suggestedMinutes){<label>Motivo del ajuste de minutos<textarea name="timerReason" required maxlength="2000" [(ngModel)]="adjustmentReason"></textarea></label>}
<label><input name="timerInternal" type="checkbox" [(ngModel)]="internal"> Nota interna; sólo administrativos</label>
@if(caseStatus==='CLOSED'){<p class="notice">El caso está cerrado. Reábrelo para confirmar; el tiempo sigue guardado.</p>}
<button class="btn primary" [disabled]="busy()||commitForm.invalid||caseStatus==='CLOSED'">Confirmar y descontar {{minutes||0}} minutos</button></form>}
@if(t.status==='COMMITTED'){<small>Atención registrada #{{t.entryId}}: {{t.confirmedMinutes}} minutos. No se volverán a descontar.</small>}
}
@if(!timer()||timer()?.status==='COMMITTED'||timer()?.status==='CANCELLED'){<button type="button" class="btn" [disabled]="busy()||caseStatus==='CLOSED'" (click)="start()">Iniciar cronómetro</button>}
</section>}
`})
export class CaseTimerComponent extends PageState implements OnChanges,OnDestroy {
  readonly api=inject(ApiService); @Input({required:true}) caseId=0; @Input() caseStatus:'OPEN'|'CLOSED'='OPEN'; @Output() updated=new EventEmitter<void>();
  readonly timer=signal<WorkTimer|null>(null); readonly displayedSeconds=signal(0);
  minutes=1; channel:'EMAIL'|'REMOTE'='EMAIL'; description=''; internal=false; adjustmentReason=''; discardRequested=false;
  private tick:ReturnType<typeof setInterval>|undefined; private observedAt=0; private requestVersion=0;
  ngOnChanges(changes:Record<string,unknown>){if(changes['caseId']){this.requestVersion++;this.stopClock();this.timer.set(null);this.description='';this.adjustmentReason='';this.internal=false;this.discardRequested=false;if(this.api.user()?.role==='ADMIN')void this.load();}}
  ngOnDestroy(){this.requestVersion++;this.stopClock();}
  private stopClock(){if(this.tick)clearInterval(this.tick);this.tick=undefined;}
  private accept(t:WorkTimer|null){this.stopClock();this.timer.set(t);this.observedAt=Date.now();this.displayedSeconds.set(t?.elapsedSeconds??0);if(t?.status==='FINISHED')this.minutes=Math.max(1,t.suggestedMinutes);if(t?.status==='RUNNING')this.tick=setInterval(()=>this.displayedSeconds.set(t.elapsedSeconds+Math.max(0,Math.floor((Date.now()-this.observedAt)/1000))),1000);}
  private async load(){const version=this.requestVersion;this.busy.set(true);this.error.set('');this.success.set('');try{const t=await this.api.get<WorkTimer|null>('/cases/'+this.caseId+'/timer');if(version===this.requestVersion)this.accept(t);}catch(e){if(version===this.requestVersion)this.error.set(errorMessage(e));}finally{if(version===this.requestVersion)this.busy.set(false);}}
  start(){const version=this.requestVersion;void this.perform(async()=>{const result=await this.api.post<WorkTimer>('/cases/'+this.caseId+'/timer/start',{});if(version!==this.requestVersion)return;this.accept(result);this.success.set('Cronómetro iniciado; todavía no se descuentan minutos.');});}
  action(action:'pause'|'resume'|'finish'|'cancel'){const t=this.timer(),version=this.requestVersion;if(!t)return;void this.perform(async()=>{const result=await this.api.post<WorkTimer>('/timers/'+t.id+'/'+action,{});if(version!==this.requestVersion)return;this.accept(result);this.discardRequested=false;this.success.set(action==='finish'?'Tiempo guardado. Revisa los minutos y confirma la atención.':action==='cancel'?'Cronómetro descartado sin descuento de minutos.':'Cronómetro actualizado.');});}
  commit(){const t=this.timer(),version=this.requestVersion;if(!t)return;void this.perform(async()=>{if(!Number.isSafeInteger(this.minutes)||this.minutes<1)throw new Error('Indica minutos enteros positivos.');const result=await this.api.post<WorkTimer>('/timers/'+t.id+'/commit',{minutes:this.minutes,channel:this.channel,description:this.description,internal:this.internal,adjustmentReason:this.adjustmentReason});if(version!==this.requestVersion)return;this.accept(result);this.success.set('Atención registrada y saldo actualizado.');this.updated.emit();});}
  clock(){const seconds=this.displayedSeconds();return [Math.floor(seconds/3600),Math.floor(seconds%3600/60),seconds%60].map(n=>String(n).padStart(2,'0')).join(':');}
  statusLabel(status:WorkTimer['status']){return {RUNNING:'En marcha',PAUSED:'Pausado',FINISHED:'Finalizado, pendiente de confirmar',COMMITTED:'Atención confirmada',CANCELLED:'Descartado'}[status];}
}
