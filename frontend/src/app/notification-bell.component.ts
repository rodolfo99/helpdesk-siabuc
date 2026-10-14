import { Component, EventEmitter, HostListener, Input, OnChanges, OnDestroy, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService, errorMessage } from './api.service';
import { User } from './models';

interface InboxItem { id:number; institutionId:number; caseId:number|null; eventType:string; title:string; description:string; internal:boolean; createdAt:string; readAt:string|null; }
interface PendingReply { caseId:number; title:string; institutionName:string; waitingFor:string; assignedAdminName:string; updatedAt:string; }
interface Inbox { items:InboxItem[]; unreadCount:number; pendingReplies:PendingReply[]; }

@Component({selector:'app-notification-bell',standalone:true,imports:[DatePipe],template:`
@if(user){
  <div class="bell-container">
    <button type="button" class="bell-trigger" (click)="toggle()" [attr.aria-expanded]="expanded()" aria-controls="portal-inbox" aria-label="Notificaciones y respuestas pendientes">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"></path><path d="M10 21h4M12 2V1"></path></svg><span>Notificaciones</span>
      @if(inbox().unreadCount>0){<strong class="unread-count" aria-label="Notificaciones sin leer">{{inbox().unreadCount>99?'99+':inbox().unreadCount}}</strong>}
    </button>
    @if(expanded()){
      <section id="portal-inbox" class="inbox-panel" aria-label="Notificaciones internas">
        <header><h2>Notificaciones</h2><button type="button" class="btn" (click)="expanded.set(false)" aria-label="Cerrar notificaciones">Cerrar</button></header>
        <p class="inbox-hint">Los avisos de la plataforma funcionan aunque el correo esté pausado.</p>
        @if(error()){<p class="notice error" role="alert">{{error()}}</p>}
        <div class="inbox-actions"><button type="button" class="btn" [disabled]="loading()" (click)="refresh()">Actualizar</button><button type="button" class="btn" [disabled]="working() || !inbox().unreadCount" (click)="readAll()">Marcar todas como leídas</button></div>
        @if(user.role!=='BILLING'){
          <h3>Respuestas pendientes de {{['ADMIN','TECH'].includes(user.role)?'atención':'tu institución'}}</h3>
          @if(!inbox().pendingReplies.length){<p class="inbox-empty">No hay respuestas pendientes para tu cuenta.</p>}
          @for(pending of inbox().pendingReplies;track pending.caseId){
            <button type="button" class="pending-case" (click)="goToCase(pending.caseId)"><strong>#{{pending.caseId}} · {{pending.title}}</strong><span>{{pending.institutionName}}</span><small>Responsable: {{pending.assignedAdminName}}</small></button>
          }
        }
        <h3>Avisos recientes</h3><small>Hasta 100 avisos; el contador incluye todos los avisos sin leer.</small>
        @if(!inbox().items.length){<p class="inbox-empty">Aún no tienes notificaciones.</p>}
        <ul class="inbox-items">
          @for(item of inbox().items;track item.id){
            <li [class.unread]="!item.readAt">
              <div class="item-heading"><strong>{{item.title}}</strong>@if(!item.readAt){<span class="unread-dot" aria-label="Sin leer"></span>}</div>
              @if(item.internal){<small class="internal-label">Sólo personal de soporte</small>}
              <p>{{item.description}}</p><time>{{item.createdAt|date:'dd/MM/yyyy HH:mm'}}</time>
              <div class="inbox-actions">@if(item.caseId!==null){<button type="button" class="btn" (click)="openItem(item)" [disabled]="working()">Ver caso</button>}@if(!item.readAt){<button type="button" class="btn" (click)="read(item.id)" [disabled]="working()">Marcar leída</button>}</div>
            </li>
          }
        </ul>
      </section>
    }
  </div>
}`,styles:[`
  :host{display:block;position:relative}.bell-container{position:relative}.bell-trigger{display:flex;align-items:center;gap:.45rem;border:1px solid #d5dfed;background:#fff;border-radius:10px;padding:.65rem .8rem;color:#18324b;cursor:pointer;font:inherit;font-size:.83rem}.bell-trigger>span:first-child{font-size:1.25rem}.unread-count{border-radius:999px;background:#a22e34;color:white;padding:.1rem .4rem;font-size:.72rem}.inbox-panel{position:absolute;right:0;top:calc(100% + .6rem);z-index:80;background:#fff;color:#18324b;border:1px solid #d5dfed;box-shadow:0 12px 44px #10294226;border-radius:14px;width:min(440px,calc(100vw - 32px));max-height:min(75vh,720px);overflow:auto;padding:1.1rem}.inbox-panel header{display:flex;justify-content:space-between;align-items:center;gap:.75rem}.inbox-panel h2{font-size:1.1rem;margin:0}.inbox-panel h3{font-size:.9rem;margin:1.2rem 0 .5rem}.inbox-hint,.inbox-empty{font-size:.8rem;color:#5c7087;line-height:1.5}.inbox-actions{display:flex;gap:.4rem;flex-wrap:wrap;margin:.5rem 0}.inbox-actions .btn,.inbox-panel header .btn{font-size:.74rem;padding:.35rem .55rem}.inbox-items{padding:0;list-style:none;margin:.6rem 0 0}.inbox-items li{border:1px solid #dce4ee;border-radius:9px;padding:.7rem;margin:.6rem 0}.inbox-items li.unread{background:#f0f6ff;border-left:3px solid #2466ad}.item-heading{display:flex;gap:.5rem;align-items:flex-start;font-size:.85rem;line-height:1.4;overflow-wrap:anywhere}.unread-dot{background:#2466ad;border-radius:50%;width:8px;height:8px;flex-shrink:0;margin-top:.35rem}.inbox-items p{font-size:.8rem;white-space:pre-wrap;overflow-wrap:anywhere;margin:.6rem 0;line-height:1.5}.inbox-items time,.inbox-panel small{font-size:.72rem;color:#5c7087}.internal-label{display:block;margin-top:.3rem}.pending-case{display:flex;flex-direction:column;text-align:left;gap:.3rem;width:100%;border:1px solid #c8dcee;background:#f4f9fd;border-radius:9px;padding:.7rem;cursor:pointer;color:inherit;margin:.4rem 0;font:inherit;font-size:.82rem}.pending-case span{font-size:.75rem;color:#536c80}.pending-case strong{overflow-wrap:anywhere}
`]})
export class NotificationBellComponent implements OnChanges,OnDestroy {
  @Input() user:User|null=null;
  @Output() openCase=new EventEmitter<number>();
  private readonly api=inject(ApiService);
  readonly inbox=signal<Inbox>({items:[],unreadCount:0,pendingReplies:[]});
  readonly expanded=signal(false);readonly loading=signal(false);readonly working=signal(false);readonly error=signal('');
  private timer:ReturnType<typeof setInterval>|null=null;private generation=0;private destroyed=false;
  ngOnChanges(){
    this.generation++;if(this.timer)clearInterval(this.timer);this.timer=null;this.loading.set(false);this.working.set(false);this.error.set('');
    this.inbox.set({items:[],unreadCount:0,pendingReplies:[]});this.expanded.set(false);
    if(this.user){void this.refresh();this.timer=setInterval(()=>void this.refresh(),30000);}
  }
  ngOnDestroy(){this.destroyed=true;this.generation++;if(this.timer)clearInterval(this.timer);}
  @HostListener('document:keydown.escape') close(){this.expanded.set(false);}
  toggle(){this.expanded.update(value=>!value);if(this.expanded())void this.refresh();}
  async refresh(){
    if(!this.user||this.loading()||this.destroyed)return;
    const generation=this.generation;this.loading.set(true);
    try{const data=await this.api.get<Inbox>('/inbox');if(generation===this.generation&&!this.destroyed){this.inbox.set(data);this.error.set('');}}
    catch(error){if(generation===this.generation&&!this.destroyed)this.error.set(errorMessage(error));}
    finally{if(generation===this.generation&&!this.destroyed)this.loading.set(false);}
  }
  async read(id:number){await this.mutate('/inbox/'+id+'/read');}
  async readAll(){await this.mutate('/inbox/read-all');}
  private async mutate(path:string){
    if(this.working()||!this.user)return;this.working.set(true);const generation=this.generation;
    try{await this.api.post(path,{});if(generation===this.generation&&!this.destroyed)await this.refresh();}
    catch(error){if(generation===this.generation&&!this.destroyed)this.error.set(errorMessage(error));}
    finally{if(generation===this.generation&&!this.destroyed)this.working.set(false);}
  }
  async openItem(item:InboxItem){if(item.caseId===null)return;const generation=this.generation;if(!item.readAt)await this.read(item.id);if(generation===this.generation&&!this.destroyed)this.goToCase(item.caseId);}
  goToCase(id:number){if(this.user?.role==='BILLING')return;this.expanded.set(false);this.openCase.emit(id);}
}
