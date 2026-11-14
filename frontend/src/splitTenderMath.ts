import {formatCash,parseCash} from './cashTenderMath';
/** Client-side mirror of the server's TenderPlan rules. Display only: the server remains authoritative. */
export type TenderMethod='CASH'|'UPI'|'CARD';
export const tenderMethods:TenderMethod[]=['CASH','UPI','CARD'];
export type SplitEntries=Record<TenderMethod,string>;
export const emptySplit:SplitEntries={CASH:'',UPI:'',CARD:''};
export type SplitStatus='unavailable'|'empty'|'invalid'|'over'|'short'|'cash-not-needed'|'single'|'ready';
export type TenderPayload={method:TenderMethod;amount:string};
export type SplitAssessment={status:SplitStatus;dueCents:number|null;enteredCents:number;remainingCents:number;changeCents:number;cashAppliedCents:number;tenders:TenderPayload[]};

const dueOf=(amount:number)=>{if(!Number.isFinite(amount)||amount<=0)return null;const cents=Math.round(amount*100);return Number.isSafeInteger(cents)?cents:null;};

export function assessSplit(amountDue:number,entries:SplitEntries):SplitAssessment{
 const due=dueOf(amountDue);
 const blank:SplitAssessment={status:'unavailable',dueCents:due,enteredCents:0,remainingCents:due??0,changeCents:0,cashAppliedCents:0,tenders:[]};
 if(due===null)return blank;
 const parsed:Partial<Record<TenderMethod,number>>={};
 for(const method of tenderMethods){
  const raw=entries[method].trim();if(raw==='')continue;
  const cents=parseCash(raw);if(cents===null||cents<=0)return {...blank,status:'invalid'};
  parsed[method]=cents;
 }
 const used=tenderMethods.filter(method=>parsed[method]!==undefined);
 if(!used.length)return {...blank,status:'empty'};
 const nonCash=(parsed.UPI??0)+(parsed.CARD??0),cash=parsed.CASH;
 const entered=nonCash+(cash??0);
 const base={dueCents:due,enteredCents:entered,tenders:[] as TenderPayload[]};
 if(nonCash>due)return {...base,status:'over',remainingCents:0,changeCents:0,cashAppliedCents:0};
 const needed=due-nonCash;
 if(cash===undefined)return needed>0?{...base,status:'short',remainingCents:needed,changeCents:0,cashAppliedCents:0}:finish(due,parsed,used,0,0,0);
 if(needed===0)return {...base,status:'cash-not-needed',remainingCents:0,changeCents:0,cashAppliedCents:0};
 if(cash<needed)return {...base,status:'short',remainingCents:needed-cash,changeCents:0,cashAppliedCents:0};
 return finish(due,parsed,used,needed,cash-needed,needed);
}
function finish(due:number,parsed:Partial<Record<TenderMethod,number>>,used:TenderMethod[],_remaining:number,change:number,cashApplied:number):SplitAssessment{
 const tenders=used.map(method=>({method,amount:formatCash(parsed[method]!)}));
 const entered=used.reduce((sum,method)=>sum+parsed[method]!,0);
 return {status:used.length<2?'single':'ready',dueCents:due,enteredCents:entered,remainingCents:0,changeCents:change,cashAppliedCents:cashApplied,tenders};
}
/** Value that would settle the bill using the other rows as entered; '' when nothing is left. */
export function fillRemaining(amountDue:number,entries:SplitEntries,method:TenderMethod):string{
 const due=dueOf(amountDue);if(due===null)return '';
 let others=0;
 for(const other of tenderMethods){if(other===method)continue;const raw=entries[other].trim();if(raw==='')continue;const cents=parseCash(raw);if(cents===null)return '';others+=cents;}
 const left=due-others;return left>0?formatCash(left):'';
}
export const splitMessages:Record<SplitStatus,string>={
 unavailable:'Bill amount needs review. Refresh the catalogue before checkout.',
 empty:'Enter at least two tender amounts, or use Fill remaining.',
 invalid:'Enter positive amounts with up to two decimals.',
 over:'UPI and card amounts exceed the amount due. Reduce them; they cannot produce change.',
 short:'Tenders do not yet cover the amount due.',
 'cash-not-needed':'UPI/card already cover the bill. Clear the cash row.',
 single:'Split payment needs at least two methods. For one method use the single-tender checkout.',
 ready:'Ready. Final change comes from the server receipt.'
};
