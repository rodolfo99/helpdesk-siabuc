import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
import { LicenseDefaults } from './license-models';
import { toMinutes } from './time';

@Component({
  selector: 'app-license-defaults', standalone: true, imports: [FormsModule],
  template: `
    <section class="panel form-panel">
      <div class="panel-heading"><div><h2>Horas predeterminadas de las licencias</h2>
        <p>Tiempo incluido al registrar una licencia nueva. Las licencias existentes conservan sus horas y consumos.</p>
      </div></div>
      @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
      @if(success()){<div class="notice success" role="status">{{success()}}</div>}
      @if(settings; as s){
        @if(!s.configured){<p class="notice info">Define y guarda las horas incluidas antes de registrar licencias. Puedes dejar un canal en cero.</p>}
        <form (ngSubmit)="save()" #defaultsForm="ngForm">
          <fieldset [disabled]="busy()"><div class="budget-form">
            <fieldset><legend>Soporte por correo</legend><div class="time-inputs">
              <label>Horas predeterminadas de correo<input type="number" name="defaultEmailHours" [(ngModel)]="emailHours" min="0" step="1" required></label>
              <label>Minutos adicionales de correo<input type="number" name="defaultEmailMinutes" [(ngModel)]="emailMinutes" min="0" max="59" step="1" required></label>
            </div></fieldset>
            <fieldset><legend>Escritorio remoto</legend><div class="time-inputs">
              <label>Horas predeterminadas de escritorio remoto<input type="number" name="defaultRemoteHours" [(ngModel)]="remoteHours" min="0" step="1" required></label>
              <label>Minutos adicionales de escritorio remoto<input type="number" name="defaultRemoteMinutes" [(ngModel)]="remoteMinutes" min="0" max="59" step="1" required></label>
            </div></fieldset>
          </div></fieldset>
          <div class="form-actions"><button type="button" class="btn secondary" (click)="load()" [disabled]="busy()">Volver a cargar valores</button>
            <button class="btn primary" [disabled]="busy() || defaultsForm.invalid">{{busy()?'Guardando…':'Guardar horas predeterminadas'}}</button></div>
        </form>
      } @else {<p>Cargando horas predeterminadas…</p><button class="btn secondary" (click)="load()" [disabled]="busy()">Reintentar</button>}
    </section>
  `,
  styles: [`form>fieldset{border:0;padding:0;min-width:0}`]
})
export class LicenseDefaultsComponent extends PageState implements OnInit {
  private readonly api = inject(ApiService);
  settings: LicenseDefaults | null = null;
  emailHours: number | null = 0; emailMinutes: number | null = 0;
  remoteHours: number | null = 0; remoteMinutes: number | null = 0;
  ngOnInit() { this.load(); }
  private apply(value: LicenseDefaults) {
    this.settings = value;
    this.emailHours = Math.floor(value.emailMinutes / 60); this.emailMinutes = value.emailMinutes % 60;
    this.remoteHours = Math.floor(value.remoteMinutes / 60); this.remoteMinutes = value.remoteMinutes % 60;
  }
  load() { void this.perform(async () => this.apply(await this.api.get<LicenseDefaults>('/license-support-defaults'))); }
  save() {
    void this.perform(async () => {
      if (!this.settings) return;
      const input = {emailMinutes: toMinutes(this.emailHours, this.emailMinutes), remoteMinutes: toMinutes(this.remoteHours, this.remoteMinutes), revision: this.settings.revision};
      this.apply(await this.api.put<LicenseDefaults>('/license-support-defaults', input));
      this.success.set('Horas predeterminadas guardadas. Se aplicarán una sola vez a cada licencia nueva.');
    });
  }
}
