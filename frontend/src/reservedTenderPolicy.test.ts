import {expect,it} from 'vitest';
import {canTenderReservation} from './reservedTenderPolicy';
import type {Draft} from './draftTypes';
const draft:Draft={id:'d',vendorName:'Store',channel:'IN_STORE',sessionId:'s1',status:'DRAFT',expiresAt:'2030-01-01T00:00:00Z',total:110,message:'Unpaid',items:[]};
const now=Date.parse('2026-11-13T12:00:00Z');
it('requires an in-store draft and exact current session',()=>{expect(canTenderReservation(draft,'s1',now)).toBe(true);for(const value of [{...draft,channel:'ONLINE'},{...draft,sessionId:null},{...draft,sessionId:'other'},{...draft,status:'PAID'}])expect(canTenderReservation(value,'s1',now)).toBe(false);expect(canTenderReservation(draft,undefined,now)).toBe(false);});
it('fails closed for elapsed, explicitly expired or malformed expiry',()=>{for(const value of [{...draft,expiresAt:'2026-11-13T12:00:00Z'},{...draft,expired:true},{...draft,expiresAt:'invalid'}])expect(canTenderReservation(value,'s1',now)).toBe(false);});
