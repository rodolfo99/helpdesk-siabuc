export type KnowledgeCategory = 'INSTALLATION'|'CATALOGING'|'LOANS'|'REPORTS'|'IMPORT'|'OTHER'|'GUIDES'|'FAQ'|'SUPPORT'|'POLICIES_LICENSES'|'TRAINING'|'CONTACT';
/** Alias de compatibilidad para los consumidores editoriales de 1.8. No es la categoría de casos. */
export type CaseCategory = KnowledgeCategory;

/** Catálogo editorial: no ampliar con estos valores el catálogo de clasificación de casos. */
export const KNOWLEDGE_CATEGORIES: ReadonlyArray<{value:KnowledgeCategory;label:string}> = [
  {value:'INSTALLATION',label:'Instalación'},
  {value:'CATALOGING',label:'Catalogación'},
  {value:'LOANS',label:'Préstamos'},
  {value:'REPORTS',label:'Reportes'},
  {value:'IMPORT',label:'Importación / exportación'},
  {value:'OTHER',label:'Otro'},
  {value:'GUIDES',label:'Guías y primeros pasos'},
  {value:'FAQ',label:'Preguntas frecuentes'},
  {value:'SUPPORT',label:'Solicitudes de soporte'},
  {value:'POLICIES_LICENSES',label:'Pólizas y licencias'},
  {value:'TRAINING',label:'Cursos y capacitación'},
  {value:'CONTACT',label:'Contacto y horarios'}
];

export type KnowledgeStatus='DRAFT'|'PUBLISHED'|'ARCHIVED';
export interface KnowledgeSummary {id:number;title:string;category:KnowledgeCategory;siabucVersion:string;summary:string;status:KnowledgeStatus;revision:number;updatedAt:string;publishedAt:string|null}
export interface KnowledgeArticle extends KnowledgeSummary {body:string;createdAt:string}
export interface KnowledgePage {items:KnowledgeSummary[];total:number;page:number;pageSize:number}
export interface KnowledgeHistory {id:number;revision:number;action:string;title:string;category:KnowledgeCategory;siabucVersion:string;summary:string;body:string;status:KnowledgeStatus;changeNote:string;actor:string;createdAt:string}
export interface KnowledgeLink {caseId:number;article:KnowledgeSummary;linkedAt:string}
export interface KnowledgeInput {title:string;category:KnowledgeCategory;siabucVersion:string;summary:string;body:string;changeNote:string;expectedRevision:number|null}
export const knowledgeStatusLabel=(status:KnowledgeStatus)=>({DRAFT:'Borrador',PUBLISHED:'Publicado',ARCHIVED:'Archivado'})[status];
export const emptyKnowledge=():KnowledgeInput=>({title:'',category:'OTHER',siabucVersion:'',summary:'',body:'',changeNote:'',expectedRevision:null});
