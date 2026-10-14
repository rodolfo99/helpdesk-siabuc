import { CaseCategory } from './case-profile.component';
export interface ReplyTemplateSummary {id:number;title:string;category:CaseCategory;siabucVersion:string;status:'ACTIVE'|'ARCHIVED';revision:number;updatedAt:string;}
export interface ReplyTemplate extends ReplyTemplateSummary {body:string;}
export interface ReplyTemplateHistory {id:number;revision:number;actor:string;action:string;snapshot:ReplyTemplate;createdAt:string;}
