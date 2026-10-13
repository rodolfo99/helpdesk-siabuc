export type Role = 'ADMIN' | 'USER' | 'BILLING';
export type Channel = 'EMAIL' | 'REMOTE';
export interface User { id:number; username:string; displayName:string; email:string; role:Role; institutionId:number|null; active:boolean; }
export interface Institution { id:number; name:string; contactName:string; email:string; phone:string; notes:string; supportAdminId:number|null; active:boolean; emailAllocatedMinutes:number; emailUsedMinutes:number; emailRemainingMinutes:number; remoteAllocatedMinutes:number; remoteUsedMinutes:number; remoteRemainingMinutes:number; }
export interface SupportCase { id:number; institutionId:number; institutionName:string; title:string; description:string; channel:Channel; status:'OPEN'|'CLOSED'; createdBy:string; createdAt:string; updatedAt:string; totalMinutes:number; assignedAdminId:number|null; assignedAdminName:string; }
export interface CaseEntry { id:number; caseId:number; description:string; channel:Channel|null; minutes:number; internal:boolean; createdBy:string; createdAt:string; voided:boolean; voidReason:string|null; }
export interface CaseDetail extends SupportCase { entries:CaseEntry[]; }
export interface Audit { id:number; actor:string; action:string; entityType:string; entityId:number; details:string; createdAt:string; }

export type PaymentMethod = 'TRANSFER' | 'CASH' | 'CARD' | 'OTHER';
export interface Payment { id:number; requestId:string; institutionId:number; institutionName:string; reference:string; amount:number; currency:string; paidOn:string; method:PaymentMethod; emailMinutes:number; remoteMinutes:number; notes:string; createdBy:string; createdAt:string; voided:boolean; voidReason:string|null; voidedBy:string|null; voidedAt:string|null; }
