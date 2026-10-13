import type { Payment, PaymentMethod } from './models';
/** Normalize decimal input before the request fingerprint. Never silently round money. */
export function parseAmount(input:string,allowZero=false):number {
  const text=input.trim();
  if(!/^\d{1,9}(?:\.\d{1,2})?$/.test(text)) throw new Error('Escribe un importe con punto decimal y máximo dos decimales (ej. 1250.50).');
  const value=Number(text);
  if((allowZero?value<0:value<=0) || value>999999999.99) throw new Error(allowZero?'El importe debe estar entre 0 y 999999999.99.':'El importe debe ser mayor que cero y no exceder 999999999.99.');
  return value;
}
export function money(value:number,currency:string):string {
  return `${new Intl.NumberFormat('es-MX',{minimumFractionDigits:2,maximumFractionDigits:2}).format(value)} ${currency}`;
}
export function amountTotals(payments:Payment[]):{currency:string;amount:number}[] {
  const totals=new Map<string,number>();
  for(const p of payments.filter(p=>!p.voided)) totals.set(p.currency,(totals.get(p.currency)??0)+Math.round(Number(p.amount)*100));
  return [...totals].sort(([a],[b])=>a.localeCompare(b)).map(([currency,cents])=>({currency,amount:cents/100}));
}
/** getRandomValues remains available on HTTP LAN installations where randomUUID is unavailable. */
export function newRequestId():string {
  if(typeof globalThis.crypto.randomUUID==='function')return globalThis.crypto.randomUUID();
  const bytes=globalThis.crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

export const PAYMENT_METHODS:ReadonlyArray<{value:PaymentMethod;label:string}>=[{value:'TRANSFER',label:'Transferencia'},{value:'DEPOSIT',label:'Depósito'},{value:'CASH',label:'Efectivo'},{value:'CREDIT_CARD',label:'Tarjeta de crédito'},{value:'DEBIT_CARD',label:'Tarjeta de débito'},{value:'CHECK',label:'Cheque'},{value:'OTHER',label:'Otro'}];
export function paymentMethodName(method:PaymentMethod):string {return method==='CARD'?'Tarjeta (registro histórico)':PAYMENT_METHODS.find(item=>item.value===method)?.label??method;}

/** A contractual/payment date is a civil date, not an instant; never shift it between time zones. */
export function calendarDate(value:string):string {const match=/^(\d{4})-(\d{2})-(\d{2})$/.exec(value);return match?`${match[3]}/${match[2]}/${match[1]}`:value;}
