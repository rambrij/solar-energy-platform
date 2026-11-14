import {useId,useState} from 'react';
import {assessSplit,emptySplit,fillRemaining,splitMessages,tenderMethods,type SplitEntries,type TenderMethod,type TenderPayload} from './splitTenderMath';
import {formatCash} from './cashTenderMath';
const labels:Record<TenderMethod,string>={CASH:'Cash received',UPI:'UPI amount',CARD:'Card amount'};
export default function SplitPayment({amountDue,eligible,locked,onSubmit}:{amountDue:number;eligible:boolean;locked:boolean;onSubmit:(tenders:TenderPayload[])=>void}){
 const [entries,setEntries]=useState<SplitEntries>(emptySplit),feedback=useId();
 const result=assessSplit(amountDue,entries);
 const set=(method:TenderMethod,value:string)=>setEntries(previous=>({...previous,[method]:value}));
 const message=!eligible?'This sale is not ready for split payment.':splitMessages[result.status];
 return <section className="split-payment" aria-label="Split payment">
  <div className="section-heading"><h3>Split payment</h3><span className="badge">Cash + UPI / card</span></div>
  <p className="bill-hint">Take part of the bill by each method. UPI and card rows are <strong>test payments</strong> and need the pre-production simulation to be enabled.</p>
  <div className="split-rows">{tenderMethods.map(method=><div className="split-row" key={method}>
   <label>{labels[method]} <input type="number" min="0" step="0.01" inputMode="decimal" value={entries[method]} disabled={locked||!eligible} aria-describedby={feedback} onChange={event=>set(method,event.target.value)}/></label>
   <button type="button" className="secondary" disabled={locked||!eligible} aria-label={`Fill remaining into ${labels[method]}`} onClick={()=>set(method,fillRemaining(amountDue,entries,method))}>Fill remaining</button>
  </div>)}</div>
  <dl className="split-summary"><div><dt>Amount due</dt><dd>{result.dueCents===null?'—':formatCash(result.dueCents)}</dd></div><div><dt>Remaining</dt><dd>{result.dueCents===null?'—':formatCash(result.remainingCents)}</dd></div><div><dt>Estimated change</dt><dd>{result.status==='ready'||result.status==='single'?formatCash(result.changeCents):'—'}</dd></div></dl>
  <p id={feedback} className={`split-feedback split-${result.status}`} role="status">{message}</p>
  <button type="button" disabled={locked||!eligible||result.status!=='ready'} onClick={()=>onSubmit(result.tenders)}>Complete split payment</button>
 </section>;
}
