import type {Draft} from './draftTypes';
export function canTenderReservation(draft:Draft,session:string|undefined,now:number){
 const expiry=Date.parse(draft.expiresAt);
 return draft.channel==='IN_STORE'&&draft.status==='DRAFT'&&!!session&&draft.sessionId===session&&Number.isFinite(expiry)&&!draft.expired&&expiry>now;
}
