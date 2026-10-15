import { CaseStatus } from './case-status';
import {Component,Input,OnChanges,inject,signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ApiService,PageState,errorMessage} from './api.service';
import {newRequestId} from './payment-utils';

interface Survey{id:number;caseId:number;score:number;comment:string;respondentName:string;respondentUsername:string;administratorId:number|null;administratorName:string;createdAt:string;}
interface SurveyView{caseId:number;caseStatus:CaseStatus;canRespond:boolean;survey:Survey|null;}
@Component({selector:'app-satisfaction-survey',standalone:true,imports:[FormsModule,DatePipe],template:`
<section class="panel survey-panel" aria-label="Encuesta de satisfacción">
<h2>¿Cómo fue la atención?</h2><p>Comparte la experiencia de tu institución en este caso. La encuesta es opcional y no consume minutos.</p>
@if(error()){<div class="notice error" role="alert">{{error()}}</div>}
@if(success()){<div class="notice success" role="status">{{success()}}</div>}
@if(loading()){<p role="status">Consultando encuesta…</p>}
@if(view();as data){
  @if(data.survey;as result){
    <div class="survey-thanks"><h3>{{api.user()?.role==='USER'?'Gracias por compartir tu opinión':'Respuesta de la institución'}}</h3><p class="survey-score">{{result.score}} / 5 <span>{{scoreName(result.score)}}</span></p>
    @if(result.comment){<p class="survey-comment">{{result.comment}}</p>}
    <p>Respondió {{result.respondentName}} ({{result.respondentUsername}}) · {{result.createdAt|date:'dd/MM/yyyy HH:mm'}}</p>
    <small>Administrativo asignado al responder: {{result.administratorName||'Sin administrativo asignado'}}.</small>
    <small>Esta respuesta se conserva aunque el caso vuelva a abrirse. No se puede modificar ni enviar otra encuesta para el mismo caso.</small></div>
  } @else if(data.canRespond){
    <form (ngSubmit)="submit()" #surveyForm="ngForm"><fieldset [disabled]="busy()"><legend>Calificación de la atención</legend><div class="survey-choices">
    @for(value of scores;track value){<label [class.chosen]="score===value"><input type="radio" name="satisfactionScore" [value]="value" [(ngModel)]="score" required [attr.aria-label]="value+' de 5: '+scoreName(value)"><b>{{value}}</b><span>{{scoreName(value)}}</span></label>}
    </div><label>Comentario (opcional)<textarea name="satisfactionComment" [(ngModel)]="comment" maxlength="2000" rows="3" placeholder="¿Qué hicimos bien o qué podemos mejorar?"></textarea></label><small>{{comment.length}} / 2000 caracteres. No incluyas contraseñas ni datos personales ajenos al caso.</small></fieldset>
    <p>Se guarda una única respuesta por institución y caso, con tu nombre y fecha. El administrativo responsable se toma de la asignación actual, al enviar.</p>
    <button class="btn primary" [disabled]="busy()||surveyForm.invalid||!score">{{busy()?'Enviando…':'Enviar evaluación'}}</button></form>
  } @else {<p>{{data.caseStatus!=='CLOSED'?'La encuesta estará disponible cuando el caso esté cerrado.':'La institución aún no ha respondido. Sólo un usuario cliente puede enviar la evaluación.'}}</p>}
}
@if(!loading()&&!view()){<button type="button" class="btn secondary" [disabled]="busy()" (click)="load()">Actualizar encuesta</button>}
@if(error()&&view()){<button type="button" class="btn secondary" [disabled]="busy()" (click)="load()">Consultar respuesta guardada</button>}
</section>
`,styles:[`
 :host{display:block}.survey-panel{padding:24px;margin:24px 0}.survey-panel>p,form>p,small{font-size:12px;line-height:1.7;color:var(--muted,#5b6d7a)}small{display:block;margin:.5rem 0}.survey-panel h2{margin:0 0 10px;font-size:20px}fieldset{border:0;padding:0;margin:18px 0;min-width:0}legend{font-weight:700;font-size:13px;margin-bottom:12px}.survey-choices{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;margin-bottom:20px}.survey-choices label{display:flex;flex-direction:column;gap:7px;border:1px solid var(--line,#d8e1e7);border-radius:9px;padding:12px 8px;text-align:center;cursor:pointer}.survey-choices input{width:auto;margin:0 auto}.survey-choices b{font-size:23px}.survey-choices span{font-size:10px}.survey-choices label.chosen{background:#edf9f5;border-color:#267768}.survey-score{font-size:28px;font-weight:700;color:#14695f}.survey-score span{font-size:14px;font-weight:400}.survey-comment{white-space:pre-wrap;overflow-wrap:anywhere}.survey-thanks{border-left:3px solid #267768;padding-left:18px}@media(max-width:550px){.survey-panel{padding:18px}.survey-choices{grid-template-columns:repeat(3,minmax(0,1fr))}}
`]})
export class SatisfactionSurveyComponent extends PageState implements OnChanges {
 @Input({required:true})caseId=0;readonly api=inject(ApiService);readonly view=signal<SurveyView|null>(null);readonly loading=signal(false);readonly scores=[1,2,3,4,5];
 score:number|null=null;comment='';private requestId=newRequestId();private fingerprint:string|null=null;private generation=0;
 ngOnChanges(){this.generation++;this.score=null;this.comment='';this.requestId=newRequestId();this.fingerprint=null;this.success.set('');void this.load();}
 async load(){const generation=++this.generation;this.loading.set(true);this.error.set('');this.view.set(null);try{const data=await this.api.get<SurveyView>('/satisfaction/cases/'+this.caseId);if(generation===this.generation)this.view.set(data);}catch(error){if(generation===this.generation)this.error.set(errorMessage(error));}finally{if(generation===this.generation)this.loading.set(false);}}
 submit(){const caseId=this.caseId,generation=this.generation;void this.perform(async()=>{if(this.score===null||!Number.isInteger(this.score)||this.score<1||this.score>5)throw new Error('Selecciona una puntuación de 1 a 5.');const body={score:this.score,comment:this.comment.trim()},fingerprint=JSON.stringify(body);if(this.fingerprint!==null&&this.fingerprint!==fingerprint)this.requestId=newRequestId();this.fingerprint=fingerprint;const data=await this.api.post<SurveyView>('/satisfaction/cases/'+caseId,{...body,requestId:this.requestId});if(generation!==this.generation)return;this.view.set(data);this.success.set('Evaluación guardada. Gracias por ayudarnos a mejorar.');});}
 scoreName(value:number){return ['Muy mala','Mala','Regular','Buena','Excelente'][value-1]??'';}
}
