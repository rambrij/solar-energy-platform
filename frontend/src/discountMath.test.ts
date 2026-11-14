import {describe,expect,it} from 'vitest';
import {combinedPercent,discountedUnitCents,discountsValid,draftPayload,hasManualDiscount,lineDiscountPercent,noDiscounts,parsePercent} from './discountMath';
describe('discount maths',()=>{
 it('parses percentages strictly',()=>{
  expect(parsePercent('')).toBe(0);expect(parsePercent('10')).toBe(10);expect(parsePercent('99.99')).toBe(99.99);
  for(const bad of ['100','-1','1.234','abc','1e1','100.00','9.'])expect(parsePercent(bad)).toBeNull();
 });
 it('stacks line and bill discounts like the server',()=>{expect(combinedPercent(10,10)).toBe(19);expect(combinedPercent(0,15)).toBe(15);expect(combinedPercent(99.99,99.99)).toBe(100);});
 it('matches the server unit rounding',()=>{
  expect(discountedUnitCents(10000,10)).toBe(9000);expect(discountedUnitCents(10000,19)).toBe(8100);
  expect(discountedUnitCents(999,10)).toBe(899);expect(discountedUnitCents(1000,0)).toBe(1000);
 });
 it('combines per-line and bill percentages and validates ranges',()=>{
  const d={bill:'10',lines:{1:'10'}};expect(lineDiscountPercent(d,1)).toBe(19);expect(lineDiscountPercent(d,2)).toBe(10);
  expect(discountsValid(d,[1,2])).toBe(true);expect(discountsValid({bill:'x',lines:{}},[1])).toBe(false);
  expect(discountsValid({bill:'99.99',lines:{1:'99.99'}},[1])).toBe(false);
 });
 it('detects whether any discount is applied',()=>{expect(hasManualDiscount(noDiscounts)).toBe(false);expect(hasManualDiscount({bill:'',lines:{1:'5'}})).toBe(true);expect(hasManualDiscount({bill:'0',lines:{1:'0'}})).toBe(false);});
 it('omits discount fields when none apply',()=>{
  expect(draftPayload({1:2},noDiscounts)).toEqual({items:[{productId:1,quantity:2}]});
  expect(draftPayload({1:2,3:1},{bill:'5',lines:{1:'10'}})).toEqual({items:[{productId:1,quantity:2,discountPercent:10},{productId:3,quantity:1}],billDiscountPercent:5});
 });
});
