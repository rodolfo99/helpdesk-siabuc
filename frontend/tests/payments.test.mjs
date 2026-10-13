import test from 'node:test';
import assert from 'node:assert/strict';
import {parseAmount,amountTotals,newRequestId} from '../src/app/payment-utils.ts';
test('importe usa un decimal exacto de hasta dos dígitos y rechaza redondeo implícito',()=>{
 assert.equal(parseAmount('1250.50'),1250.5);assert.equal(parseAmount(' 10 '),10);assert.equal(parseAmount('999999999.99'),999999999.99);
 for(const invalid of ['0','-5','1.001','1,25','NaN','Infinity','1000000000','1e2',''])assert.throws(()=>parseAmount(invalid));
});
test('resumen suma en centavos, separa monedas y excluye anulados',()=>{
 assert.deepEqual(amountTotals([{amount:0.1,currency:'MXN',voided:false},{amount:0.2,currency:'MXN',voided:false},{amount:99,currency:'MXN',voided:true},{amount:5,currency:'USD',voided:false}]),[{currency:'MXN',amount:0.3},{currency:'USD',amount:5}]);
});
test('solicitudes reciben UUID v4 distintos para nuevas operaciones',()=>{const a=newRequestId(),b=newRequestId();assert.match(a,/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);assert.notEqual(a,b);});
