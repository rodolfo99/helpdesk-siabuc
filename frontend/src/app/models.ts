export type Role = 'ADMIN' | 'USER' | 'BILLING' | 'TECH';
import { CaseStatus } from './case-status';
export type Channel = 'EMAIL' | 'REMOTE';
export interface User { id:number; username:string; displayName:string; email:string; role:Role; institutionId:number|null; active:boolean; }
export interface Institution { id:number; name:string; contactName:string; email:string; phone:string; notes:string; supportAdminId:number|null; active:boolean; emailAllocatedMinutes:number; emailUsedMinutes:number; emailRemainingMinutes:number; remoteAllocatedMinutes:number; remoteUsedMinutes:number; remoteRemainingMinutes:number; }
export interface SupportCase { id:number; institutionId:number; institutionName:string; title:string; description:string; channel:Channel; status:CaseStatus; createdBy:string; createdAt:string; updatedAt:string; totalMinutes:number; assignedAdminId:number|null; assignedAdminName:string; licenseId?:number|null; licenseNumber?:string|null; }
export interface CaseEntry { id:number; caseId:number; description:string; channel:Channel|null; minutes:number; internal:boolean; createdBy:string; createdAt:string; voided:boolean; voidReason:string|null; }
export interface CaseDetail extends SupportCase { entries:CaseEntry[]; }
export interface Audit { id:number; actor:string; action:string; entityType:string; entityId:number; details:string; createdAt:string; }

export type PaymentMethod = 'TRANSFER' | 'DEPOSIT' | 'CASH' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'CHECK' | 'OTHER' | 'CARD';
export interface Payment { id:number; requestId:string; policyId:number|null; policyFolio:string|null; institutionId:number; institutionName:string; reference:string; amount:number; currency:string; paidOn:string; method:PaymentMethod; emailMinutes:number; remoteMinutes:number; notes:string; createdBy:string; createdAt:string; voided:boolean; voidReason:string|null; voidedBy:string|null; voidedAt:string|null; }

export interface SupportPolicy { licenseId?:number|null; licenseNumber?:string|null; id:number; institutionId:number; institutionName:string; folio:string; description:string; validFrom:string; validUntil:string; agreedAmount:number; currency:'MXN'; emailMinutes:number; remoteMinutes:number; status:'ACTIVE'|'CANCELLED'; validity:'UPCOMING'|'CURRENT'|'EXPIRED'; paidAmount:number; remainingAmount:number; creditedEmailMinutes:number; creditedRemoteMinutes:number; remainingEmailMinutes:number; remainingRemoteMinutes:number; paymentCount:number; activePaymentCount:number; createdBy:string; createdAt:string; updatedAt:string; cancelReason:string|null; cancelledBy:string|null; cancelledAt:string|null; }
export interface PolicyHistory { id:number; policyId:number; action:string; description:string; paymentId:number|null; createdBy:string; createdAt:string; }
export interface PolicyDetail { policy:SupportPolicy; history:PolicyHistory[]; payments:Payment[]; }
export interface PaymentOpenRequest { policyId:number; sequence:number; }
