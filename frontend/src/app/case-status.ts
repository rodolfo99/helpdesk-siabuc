export type CaseStatus = 'OPEN'|'IN_PROGRESS'|'WAITING_CLIENT'|'RESOLVED'|'CLOSED';
export const CASE_STATUSES: ReadonlyArray<{value:CaseStatus;label:string}> = [
  {value:'OPEN',label:'Recibido'},
  {value:'IN_PROGRESS',label:'En atención'},
  {value:'WAITING_CLIENT',label:'Requiere respuesta del cliente'},
  {value:'RESOLVED',label:'Resuelto'},
  {value:'CLOSED',label:'Cerrado'}
];
export function caseStatusLabel(status:string):string {
  return CASE_STATUSES.find(item=>item.value===status)?.label??'Estado desconocido';
}
export function isActiveCase(status:CaseStatus):boolean {
  return status==='OPEN'||status==='IN_PROGRESS'||status==='WAITING_CLIENT';
}
export function canRunCaseTimer(status:CaseStatus):boolean {
  return status==='OPEN'||status==='IN_PROGRESS';
}
export function caseStatusTargets(status:CaseStatus) {
  return CASE_STATUSES.filter(item=>item.value!==status&&item.value!=='OPEN'&&
    (isActiveCase(status)||item.value==='IN_PROGRESS'||(status==='RESOLVED'&&item.value==='CLOSED')));
}
