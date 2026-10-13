import type { Payment } from './models';
/** Normalize decimal input before the request fingerprint. Never silently round money. */
export function parseAmount(input:string):number {
  const text=input.trim();
  if(!/^\d{1,9}(?:\.\d{1,2})?$/.test(text)) throw new Error('Escribe un importe positivo con punto decimal y máximo dos decimales (ej. 1250.50).');
  const value=Number(text);
  if(value<=0 || value>999999999.99) throw new Error('El importe debe ser mayor que cero y no exceder 999999999.99.');
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
