import { CommonModule } from '@angular/common';
import { Component, OnInit, effect, inject, input, output, signal } from '@angular/core';
import { ApiService, PageState } from './api.service';

interface GuideStatus { completed: boolean; completedAt: string|null; }

/**
 * Client onboarding: server-side completion is persistent across browsers.
 * The user can reopen the guide from the Help menu without clearing completion.
 */
@Component({
  selector: 'app-client-guide',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if(visible()){
      <section class="panel guide" aria-labelledby="siabuc-welcome-title">
        <div class="eyebrow">PRIMEROS PASOS · SIABUC</div>
        <h2 id="siabuc-welcome-title">Bienvenido a tu portal de soporte</h2>
        <p>Desde aquí puedes consultar tus solicitudes, comunicarte con soporte y revisar los servicios registrados para tu institución.</p>
        @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
        <div class="guide-actions">
          <button type="button" class="btn secondary" (click)="go('cases')">Ver mis solicitudes</button>
          <button type="button" class="btn primary" (click)="go('newCase')">Crear una solicitud</button>
          <button type="button" class="btn secondary" (click)="go('clientInformation')">Pólizas y licencias</button>
          <button type="button" class="btn secondary" (click)="go('knowledge')">Centro de ayuda</button>
        </div>
        <p class="muted">Puedes volver a abrir esta guía desde «Primeros pasos» en la barra superior.</p>
        <button type="button" class="btn secondary" (click)="finish()" [disabled]="busy()">Entendido, cerrar guía</button>
      </section>
    }
  `,
  styles: [`
    .guide{padding:24px;margin-bottom:22px;border:1px solid #b2d4cc;background:#f7fbfa}
    .guide h2{font-size:22px;margin:10px 0}.guide p{font-size:13px;line-height:1.7}
    .guide-actions{display:flex;gap:10px;flex-wrap:wrap;margin:20px 0}
  `]
})
export class ClientGuideComponent extends PageState implements OnInit {
  private api=inject(ApiService);
  readonly openRequest=input(0);
  readonly navigateTo=output<'cases'|'newCase'|'clientInformation'|'knowledge'>();
  readonly visible=signal(false);
  constructor(){
    super();
    effect(()=>{
      if(this.openRequest()>0 && this.api.user()?.role==='USER')this.visible.set(true);
    });
  }
  ngOnInit(){
    if(this.api.user()?.role!=='USER')return;
    void this.perform(async()=>{
      const state=await this.api.get<GuideStatus>('/client-portal/guide');
      this.visible.set(!state.completed || this.openRequest()>0);
    });
  }
  go(destination:'cases'|'newCase'|'clientInformation'|'knowledge'){
    this.navigateTo.emit(destination);
  }
  finish(){
    void this.perform(async()=>{
      await this.api.post<GuideStatus>('/client-portal/guide/complete',{});
      this.visible.set(false);
    });
  }
}
