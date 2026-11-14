import {describe,expect,it} from 'vitest';
import {assessSplit,emptySplit,fillRemaining} from './splitTenderMath';
const e=(v:Partial<typeof emptySplit>)=>({...emptySplit,...v});
describe('assessSplit',()=>{
 it('settles UPI plus cash with change from cash only',()=>{
  const r=assessSplit(110,e({UPI:'60',CASH:'60'}));
  expect(r.status).toBe('ready');expect(r.changeCents).toBe(1000);expect(r.cashAppliedCents).toBe(5000);expect(r.remainingCents).toBe(0);
  expect(r.tenders).toEqual([{method:'CASH',amount:'60.00'},{method:'UPI',amount:'60.00'}]);
 });
 it('supports exact three-way splits',()=>{
  const r=assessSplit(100.01,e({CARD:'30',UPI:'30.01',CASH:'40'}));expect(r.status).toBe('ready');expect(r.changeCents).toBe(0);
 });
 it('reports the remaining amount while short',()=>{
  expect(assessSplit(110,e({UPI:'60',CASH:'40'}))).toMatchObject({status:'short',remainingCents:1000});
  expect(assessSplit(110,e({UPI:'60'}))).toMatchObject({status:'short',remainingCents:5000});
 });
 it('rejects non-cash over the bill instead of turning it into change',()=>{
  expect(assessSplit(110,e({UPI:'70',CARD:'50'})).status).toBe('over');
  expect(assessSplit(110,e({CARD:'110.01'})).status).toBe('over');
 });
 it('rejects a cash row when non-cash already covers the bill',()=>{expect(assessSplit(110,e({UPI:'110',CASH:'5'})).status).toBe('cash-not-needed');});
 it('requires at least two methods',()=>{
  expect(assessSplit(110,e({CASH:'120'})).status).toBe('single');expect(assessSplit(110,e({CARD:'110'})).status).toBe('single');
 });
 it('flags empty, invalid and unavailable input',()=>{
  expect(assessSplit(110,emptySplit).status).toBe('empty');
  for(const bad of ['-1','0','1.001','abc','1e3'])expect(assessSplit(110,e({CASH:bad})).status).toBe('invalid');
  expect(assessSplit(0,e({CASH:'1'})).status).toBe('unavailable');expect(assessSplit(NaN,e({CASH:'1'})).status).toBe('unavailable');
 });
 it('never produces a payload unless ready',()=>{expect(assessSplit(110,e({UPI:'60',CASH:'40'})).tenders).toEqual([]);});
});
describe('fillRemaining',()=>{
 it('fills what the other rows leave',()=>{
  expect(fillRemaining(110,e({UPI:'60'}),'CASH')).toBe('50.00');
  expect(fillRemaining(110,e({UPI:'60'}),'UPI')).toBe('110.00');
  expect(fillRemaining(110,e({UPI:'60',CARD:'50'}),'CASH')).toBe('');
  expect(fillRemaining(110,e({UPI:'x'}),'CASH')).toBe('');
 });
});
