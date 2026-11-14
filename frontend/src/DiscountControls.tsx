import {discountsValid,hasManualDiscount,lineDiscountPercent,noDiscounts,parsePercent,type Discounts} from './discountMath';
export default function DiscountControls({lines,discounts,locked,onChange}:{lines:{id:number;name:string}[];discounts:Discounts;locked:boolean;onChange:(value:Discounts)=>void}){
 const valid=discountsValid(discounts,lines.map(line=>line.id)),active=hasManualDiscount(discounts);
 return <details className="discount-controls" open={active}>
  <summary>Manual discounts{active?' (applied)':''}</summary>
  <p className="bill-hint">Cashier limits are set per branch; higher discounts need a supervisor with approval permission. Discounted bills are reserved first, then paid by cash, split or test UPI/card.</p>
  <label>Bill discount % <input type="number" min="0" max="99.99" step="0.01" inputMode="decimal" disabled={locked} value={discounts.bill} aria-invalid={parsePercent(discounts.bill)===null} onChange={event=>onChange({...discounts,bill:event.target.value})}/></label>
  <ul className="discount-lines">{lines.map(line=>{const combined=lineDiscountPercent(discounts,line.id);return <li key={line.id}>
   <label>{line.name} discount % <input type="number" min="0" max="99.99" step="0.01" inputMode="decimal" disabled={locked} value={discounts.lines[line.id]??''} aria-invalid={parsePercent(discounts.lines[line.id]??'')===null} onChange={event=>onChange({...discounts,lines:{...discounts.lines,[line.id]:event.target.value}})}/></label>
   {combined!==null&&combined>0&&<small>Effective {combined.toFixed(2)}%</small>}</li>;})}</ul>
  {!valid&&<p role="alert">Enter discounts as percentages from 0 to 99.99 with at most two decimals.</p>}
  {active&&<button type="button" className="secondary" disabled={locked} onClick={()=>onChange(noDiscounts)}>Clear all discounts</button>}
 </details>;
}
