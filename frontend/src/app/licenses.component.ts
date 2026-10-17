import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageState } from './api.service';
import { Institution } from './models';
import { LicenseDefaults, LicenseMovement, SupportLicense } from './license-models';
import { formatTime, toMinutes } from './time';

@Component({
  selector: 'app-licenses', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './licenses.component.html'
})
export class LicensesComponent extends PageState implements OnInit {
  readonly api = inject(ApiService);
  readonly items = signal<SupportLicense[]>([]);
  readonly institutions = signal<Institution[]>([]);
  readonly history = signal<LicenseMovement[]>([]);
  readonly time = formatTime;
  defaults: LicenseDefaults | null = null;
  creating = false; selected: SupportLicense | null = null; adding = false;
  institutionId: number | null = null; number = ''; filter = '';
  emailHours: number | null = 0; emailMinutes: number | null = 0;
  remoteHours: number | null = 0; remoteMinutes: number | null = 0;
  reason = ''; requestId = '';
  isAdmin() { return this.api.user()?.role === 'ADMIN'; }
  ngOnInit() { void this.perform(() => this.refresh()); }
  private async refresh() {
    const [items, institutions] = await Promise.all([this.api.get<SupportLicense[]>('/licenses'), this.api.get<Institution[]>('/institutions')]);
    this.items.set(items); this.institutions.set(institutions);
    if (this.selected) this.selected = items.find(i => i.id === this.selected?.id) ?? null;
  }
  activeInstitutions() { return this.institutions().filter(i => i.active); }
  filtered() { const text = this.filter.trim().toLocaleLowerCase(); return this.items().filter(i => `${i.number} ${i.institutionName}`.toLocaleLowerCase().includes(text)); }
  create() {
    void this.perform(async () => {
      this.defaults = await this.api.get<LicenseDefaults>('/license-support-defaults');
      this.creating = true; this.selected = null; this.adding = false; this.number = ''; this.institutionId = null;
    });
  }
  save() {
    void this.perform(async () => {
      if (!this.defaults?.configured || !this.institutionId || !this.number.trim()) throw new Error('Completa la institución, el número y la configuración inicial.');
      this.selected = await this.api.post<SupportLicense>('/licenses', {institutionId: this.institutionId, number: this.number.trim(), defaultsRevision: this.defaults.revision});
      this.creating = false; await this.refresh(); await this.loadHistory();
      this.success.set('Licencia registrada y horas iniciales acreditadas una sola vez.');
    });
  }
  select(item: SupportLicense) {
    void this.perform(async () => { this.selected = item; this.creating = false; this.adding = false; await this.loadHistory(); });
  }
  private async loadHistory() {
    this.history.set(this.selected && this.isAdmin() ? await this.api.get<LicenseMovement[]>(`/licenses/${this.selected.id}/history`) : []);
  }
  startTopup() {
    if (this.busy()) return;
    this.emailHours = this.emailMinutes = this.remoteHours = this.remoteMinutes = 0;
    this.reason = ''; this.requestId = crypto.randomUUID(); this.adding = true;
  }
  topup() {
    void this.perform(async () => {
      if (!this.selected) return;
      const emailMinutes = toMinutes(this.emailHours, this.emailMinutes), remoteMinutes = toMinutes(this.remoteHours, this.remoteMinutes);
      if (!emailMinutes && !remoteMinutes) throw new Error('Indica tiempo en al menos un canal.');
      if (!this.reason.trim()) throw new Error('Indica el motivo de la asignación adicional.');
      await this.api.post(`/licenses/${this.selected.id}/hours`, {requestId: this.requestId, emailMinutes, remoteMinutes, reason: this.reason.trim()});
      this.adding = false; await this.refresh(); await this.loadHistory();
      this.success.set('Horas adicionales acreditadas. El movimiento quedó en el historial.');
    });
  }
  delta(minutes: number) { return (minutes < 0 ? '−' : '+') + formatTime(Math.abs(minutes)); }
  kind(value: string) { return ({INITIAL:'Horas incluidas',TOPUP:'Horas adicionales',PAYMENT:'Pago de póliza',VOID_PAYMENT:'Anulación de pago',USAGE:'Atención',VOID_USAGE:'Devolución de atención'} as Record<string,string>)[value] ?? value; }
}
