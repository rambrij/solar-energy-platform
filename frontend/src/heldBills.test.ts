import {describe,expect,it} from 'vitest';
import {countDrafts,filterDrafts,formatTimeLeft} from './heldBills';
import type {Draft} from './draftTypes';
const d=(id:string,status:string):Draft=>({id,vendorName:'V',channel:'IN_STORE',status,expiresAt:'2030-01-01T00:00:00Z',total:1,message:'',sessionId:'s',items:[]});
const rows=[d('a','DRAFT'),d('b','PAID'),d('c','CANCELLED'),d('e','DRAFT')];
describe('held bills',()=>{
 it('filters held and completed drafts',()=>{
  expect(filterDrafts(rows,'all')).toHaveLength(4);expect(filterDrafts(rows,'held').map(r=>r.id)).toEqual(['a','e']);expect(filterDrafts(rows,'done').map(r=>r.id)).toEqual(['b','c']);
 });
 it('counts per tab',()=>{expect(countDrafts(rows)).toEqual({all:4,held:2,done:2});expect(countDrafts([])).toEqual({all:0,held:0,done:0});});
 it('formats the time left on a hold and never goes negative',()=>{
  expect(formatTimeLeft(0)).toBe('Expired');expect(formatTimeLeft(-5)).toBe('Expired');expect(formatTimeLeft(NaN)).toBe('Expired');
  expect(formatTimeLeft(65_000)).toBe('1:05');expect(formatTimeLeft(3_600_000+120_000)).toBe('1h 2m');expect(formatTimeLeft(90_000_000)).toBe('1d 1h');
 });
});
