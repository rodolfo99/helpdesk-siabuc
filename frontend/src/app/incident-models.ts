import { CaseStatus } from './case-status';
export type IncidentStatus='OPEN'|'IN_PROGRESS'|'RESOLVED';
export interface Incident {id:number;title:string;description:string;status:IncidentStatus;revision:number;publicTitle:string;publicSummary:string;publicStatus:IncidentStatus|null;published:boolean;publishedAt:string|null;createdAt:string;updatedAt:string;}
export interface IncidentSummary {id:number;title:string;status:IncidentStatus;revision:number;published:boolean;updatedAt:string;}
export interface IncidentPage {items:IncidentSummary[];total:number;page:number;pageSize:number;}
export interface IncidentCase {id:number;institutionId:number;institutionName:string;title:string;status:CaseStatus;}
export interface IncidentHistory {id:number;action:string;revision:number;title:string;description:string;status:IncidentStatus;publicTitle:string;publicSummary:string;publicStatus:IncidentStatus|null;published:boolean;note:string;caseId:number|null;actor:string;createdAt:string;}
export interface IncidentDetail {incident:Incident;cases:IncidentCase[];history:IncidentHistory[];}
export interface StaffIncidentLink {incidentId:number;title:string;status:IncidentStatus;published:boolean;linkedAt:string;}
export interface PublicIncidentLink {title:string;summary:string;status:IncidentStatus;publishedAt:string;}
export const INCIDENT_STATUSES:ReadonlyArray<{value:IncidentStatus;label:string}>=[{value:'OPEN',label:'Abierta'},{value:'IN_PROGRESS',label:'En atención'},{value:'RESOLVED',label:'Resuelta'}];
export function incidentStatusLabel(status:IncidentStatus|null){return INCIDENT_STATUSES.find(item=>item.value===status)?.label||'Sin publicar';}
export function incidentActionLabel(action:string){return ({CREATE:'Creación',EDIT:'Actualización interna',PROGRESS:'Avance interno',PUBLISH:'Publicación para clientes',UNPUBLISH:'Retiro de publicación',LINK:'Caso vinculado',UNLINK:'Vínculo retirado'} as Record<string,string>)[action]||action;}
