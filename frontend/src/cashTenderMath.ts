const maximumCents=99999999999999;
export function parseCash(value:string):number|null{
 if(!/^[0-9]{1,12}(?:\.[0-9]{1,2})?$/.test(value))return null;
 const [whole,fraction='']=value.split('.');const cents=Number(whole)*100+Number(fraction.padEnd(2,'0'));
 return Number.isSafeInteger(cents)&&cents<=maximumCents?cents:null;
}
export const formatCash=(cents:number)=>`${Math.floor(cents/100)}.${String(cents%100).padStart(2,'0')}`;
function dueCents(amount:number):number|null{if(!Number.isFinite(amount)||amount<0)return null;const cents=Math.round(amount*100);return Number.isSafeInteger(cents)&&cents<=maximumCents?cents:null;}
export function assessCash(value:string,amount:number){
 const due=dueCents(amount),received=parseCash(value);
 const status=due===null?'unavailable':value===''?'missing':received===null?'invalid':received<due?'short':'ready';
 return {status,dueCents:due,receivedCents:received,shortCents:due!==null&&received!==null?Math.max(0,due-received):null,changeCents:due!==null&&received!==null&&received>=due?received-due:null};
}
export function cashShortcuts(amount:number){
 const due=dueCents(amount);if(due===null)return [];
 const candidates=[{label:'Exact amount',cents:due},{label:'Round up to 100',cents:Math.ceil(due/10000)*10000},{label:'Round up to 500',cents:Math.ceil(due/50000)*50000}];
 return candidates.filter((item,index)=>item.cents<=maximumCents&&candidates.findIndex(candidate=>candidate.cents===item.cents)===index).map(item=>({label:item.label,value:formatCash(item.cents)}));
}
