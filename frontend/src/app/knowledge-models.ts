import { CaseCategory } from './case-profile.component';
export type KnowledgeStatus='DRAFT'|'PUBLISHED'|'ARCHIVED';
export interface KnowledgeSummary {id:number;title:string;category:CaseCategory;siabucVersion:string;summary:string;status:KnowledgeStatus;revision:number;updatedAt:string;publishedAt:string|null}
export interface KnowledgeArticle extends KnowledgeSummary {body:string;createdAt:string}
export interface KnowledgePage {items:KnowledgeSummary[];total:number;page:number;pageSize:number}
export interface KnowledgeHistory {id:number;revision:number;action:string;title:string;category:CaseCategory;siabucVersion:string;summary:string;body:string;status:KnowledgeStatus;changeNote:string;actor:string;createdAt:string}
export interface KnowledgeLink {caseId:number;article:KnowledgeSummary;linkedAt:string}
export interface KnowledgeInput {title:string;category:CaseCategory;siabucVersion:string;summary:string;body:string;changeNote:string;expectedRevision:number|null}
export const knowledgeStatusLabel=(status:KnowledgeStatus)=>({DRAFT:'Borrador',PUBLISHED:'Publicado',ARCHIVED:'Archivado'})[status];
export const emptyKnowledge=():KnowledgeInput=>({title:'',category:'OTHER',siabucVersion:'',summary:'',body:'',changeNote:'',expectedRevision:null});
