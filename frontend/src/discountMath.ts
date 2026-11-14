/** Client estimate of the server's manual-discount maths. The server validates limits, approval and totals. */
export type Discounts={bill:string;lines:Record<number,string>};
export const noDiscounts:Discounts={bill:'',lines:{}};
/** '' -> 0; otherwise 0..99.99 with at most two decimals; anything else -> null. */
export function parsePercent(value:string):number|null{
 const text=value.trim();if(text==='')return 0;
 if(!/^[0-9]{1,2}(?:\.[0-9]{1,2})?$/.test(text))return null;
 const number=Number(text);return number>=0&&number<=99.99?number:null;
}
/** Stacked line + bill discount, rounded to two decimals like the server. */
export function combinedPercent(line:number,bill:number):number{return Math.round((line+bill-line*bill/100)*100)/100;}
/** Per-unit price in cents after the percent, with the server's half-up rounding of the unit reduction. */
export function discountedUnitCents(priceCents:number,percent:number):number{
 if(percent<=0)return priceCents;
 const unit=Math.round(priceCents*Math.round(percent*100)/10000);
 return priceCents-unit;
}
export function lineDiscountPercent(discounts:Discounts,productId:number):number|null{
 const line=parsePercent(discounts.lines[productId]??''),bill=parsePercent(discounts.bill);
 return line===null||bill===null?null:combinedPercent(line,bill);
}
export function hasManualDiscount(discounts:Discounts):boolean{
 return (parsePercent(discounts.bill)??0)>0||Object.values(discounts.lines).some(value=>(parsePercent(value)??0)>0);
}
export function discountsValid(discounts:Discounts,productIds:number[]):boolean{
 return parsePercent(discounts.bill)!==null&&productIds.every(id=>{const p=lineDiscountPercent(discounts,id);return p!==null&&p<=99.99;});
}
/** Request fields for a reserved POS draft. Discount keys are omitted entirely when no discount applies. */
export function draftPayload(cart:Record<number,number>,discounts:Discounts){
 const bill=parsePercent(discounts.bill)??0;
 const items=Object.entries(cart).map(([id,quantity])=>{
  const line=parsePercent(discounts.lines[Number(id)]??'')??0;
  return line>0?{productId:Number(id),quantity,discountPercent:line}:{productId:Number(id),quantity};
 });
 return bill>0?{items,billDiscountPercent:bill}:{items};
}
