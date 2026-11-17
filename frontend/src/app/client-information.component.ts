import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ApiService, PageState } from './api.service';

interface ClientPolicy {
  id: number;
  folio: string;
  description: string;
  validFrom: string;
  validUntil: string;
  status: 'ACTIVE'|'CANCELLED';
  validity: 'UPCOMING'|'CURRENT'|'EXPIRED';
  licenseId: number|null;
  licenseNumber: string|null;
}
interface ClientLicense {
  id: number;
  number: string;
  emailRemainingMinutes: number;
  remoteRemainingMinutes: number;
}
interface ClientInformation {
  institutionId: number;
  institutionName: string;
  policies: ClientPolicy[];
  licenses: ClientLicense[];
}

/** Only institution-scoped information is returned; financial details stay in their existing gated module. */
@Component({
  selector: 'app-client-information',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="page-heading">
    <div><div class="eyebrow">PORTAL DEL CLIENTE</div><h1>Mi información SIABUC</h1>
      <p>Consulta las pólizas y licencias de soporte registradas para tu institución.</p>
    </div><button type="button" class="btn secondary" (click)="load()" [disabled]="busy()">Actualizar</button>
  </div>
  @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
  @if(busy()){<p class="loading" role="status">Consultando tu información…</p>}
  @if(summary();as info){
    <section class="panel information-panel">
      <div class="eyebrow">INSTITUCIÓN</div><h2>{{info.institutionName}}</h2>
      <p class="muted">Los datos proceden del registro de soporte Helpdesk SIABUC.
      La validación comercial de licencias SIABUC9 se integrará posteriormente.</p>
    </section>
    <section class="panel information-panel">
      <h2>Mis pólizas de soporte</h2>
      @for(p of info.policies;track p.id){
        <article class="information-row">
          <div><strong>{{p.folio}}</strong> · <span>{{p.description}}</span>
            <p class="muted">Vigencia: {{p.validFrom | date:'dd/MM/yyyy':'':'es-MX'}} al {{p.validUntil | date:'dd/MM/yyyy':'':'es-MX'}}</p>
            <p class="muted">Licencia vinculada: {{p.licenseNumber || 'Sin licencia vinculada'}}</p>
          </div>
          <span class="badge" [class.closed]="p.status==='CANCELLED'||p.validity==='EXPIRED'">{{policyStatus(p)}}</span>
        </article>
      } @empty {<div class="empty-state">No existen pólizas registradas para tu institución.</div>}
    </section>
    <section class="panel information-panel">
      <h2>Mis licencias de soporte</h2>
      @for(l of info.licenses;track l.id){
        <article class="information-row"><div>
          <strong>{{l.number}}</strong><p class="muted">Tiempo disponible: correo {{duration(l.emailRemainingMinutes)}} · remoto {{duration(l.remoteRemainingMinutes)}}.</p>
        </div></article>
      } @empty {<div class="empty-state">No existen licencias de soporte registradas.</div>}
    </section>
    <p class="muted">El inicio con número de póliza se encuentra pendiente. Durante esta etapa se muestran los registros de la institución de tu cuenta.</p>
  }
  `,
  styles: [`
    .information-panel{padding:24px;margin-bottom:20px}
    .information-panel h2{font-size:19px;margin:8px 0 16px}
    .information-row{display:flex;justify-content:space-between;gap:18px;align-items:start;padding:16px 0;border-top:1px solid var(--line)}
    .information-row strong{font-size:15px}.information-row .muted{font-size:12px;margin:8px 0 0;line-height:1.6}
    .information-row .badge{flex-shrink:0}
    @media(max-width:650px){.information-panel{padding:18px}.information-row{flex-direction:column}}
  `]
})
export class ClientInformationComponent extends PageState implements OnInit {
  private readonly api=inject(ApiService);
  readonly summary=signal<ClientInformation|null>(null);
  ngOnInit(){this.load();}
  load(){void this.perform(async()=>{this.summary.set(await this.api.get<ClientInformation>('/client-portal/my-information'));});}
  policyStatus(p:ClientPolicy){
    if(p.status==='CANCELLED')return 'Cancelada';
    return {UPCOMING:'Por iniciar',CURRENT:'Vigente',EXPIRED:'Vencida'}[p.validity];
  }
  duration(minutes:number){
    const hours=Math.floor(minutes/60),remainder=minutes%60;
    return `${hours} h ${remainder} min`;
  }
}
