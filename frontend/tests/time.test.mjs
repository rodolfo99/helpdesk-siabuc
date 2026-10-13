import test from 'node:test';
import assert from 'node:assert/strict';
import {toMinutes,formatTime,passwordError} from '../src/app/time.ts';
test('convierte horas y minutos enteros sin redondeo ni pérdida',()=> {assert.equal(toMinutes(2,35),155);assert.equal(toMinutes(0,1),1);assert.equal(toMinutes(0,0),0);assert.equal(formatTime(155),'2 h 35 min');});
test('rechaza negativos, decimales, minutos fuera de rango y campos vacíos',()=>{for (const [h,m] of [[-1,0],[1.5,0],[0,60],[0,-1],[null,0],[0,null],[1,0.1],[NaN,0],[Infinity,0],[40000000,0]]) assert.throws(()=>toMinutes(h,m));});
test('contraseñas miden bytes UTF-8 antes de BCrypt',()=>{assert.ok(passwordError('corta'));assert.equal(passwordError('Password-123456'),null);assert.ok(passwordError('á'.repeat(37)));assert.equal(passwordError('á'.repeat(36)),null);assert.ok(passwordError('😀'.repeat(19)));});
