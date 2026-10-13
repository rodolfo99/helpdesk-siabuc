import { Injectable, signal, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { User } from './models';
@Injectable({providedIn:'root'})
export class ApiService {
  private http = inject(HttpClient);
  readonly user = signal<User|null>(null);
  get<T>(path:string) { return firstValueFrom(this.http.get<T>('/api'+path)); }
  post<T>(path:string, body:unknown) { return firstValueFrom(this.http.post<T>('/api'+path,body)); }
  put<T>(path:string, body:unknown) { return firstValueFrom(this.http.put<T>('/api'+path,body)); }
  delete<T>(path:string) { return firstValueFrom(this.http.delete<T>('/api'+path)); }
  csrf() { return this.get<{token:string}>('/auth/csrf'); }
  async restore() { await this.csrf(); this.user.set(await this.get<User>('/auth/me')); }
  async login(username:string,password:string) {
    await this.csrf();
    await firstValueFrom(this.http.post('/api/auth/login',new HttpParams().set('username',username).set('password',password)));
    await this.csrf(); this.user.set(await this.get<User>('/auth/me'));
  }
  async logout() { await this.post('/auth/logout',{}); this.user.set(null); await this.csrf(); }
}
export function errorMessage(error:unknown):string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'No se pudo conectar con el servidor. Revisa que esté iniciado y vuelve a intentarlo.';
    if (typeof error.error?.message === 'string') return error.error.message;
    if (error.status === 401) return 'La sesión expiró o las credenciales no son válidas. Inicia sesión nuevamente.';
    if (error.status === 403) return 'No tienes permiso para realizar esta operación. Si tu sesión expiró, vuelve a iniciar sesión.';
    return `No se pudo completar la operación (${error.status}). Intenta nuevamente.`;
  }
  return error instanceof Error ? error.message : 'No se pudo completar la operación.';
}
export class PageState {
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  async perform(action:()=>Promise<void>) {
    if (this.busy()) return;
    this.busy.set(true); this.error.set(''); this.success.set('');
    try { await action(); } catch (e) { this.error.set(errorMessage(e)); } finally { this.busy.set(false); }
  }
}
