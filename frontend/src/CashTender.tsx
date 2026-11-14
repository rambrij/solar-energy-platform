import {useId} from 'react';
import {assessCash,cashShortcuts,formatCash} from './cashTenderMath';
export default function CashTender({amountDue,cash,onCash,eligible,locked,label='Cash received'}:{amountDue:number;cash:string;onCash:(value:string)=>void;eligible:boolean;locked:boolean;label?:string}){
 const feedbackId=useId();
 const tender=assessCash(cash,amountDue),available=eligible&&tender.status!=='unavailable';
 return <section className="cash-tender" aria-label="Cash tender entry"><div className="section-heading"><h3>Cash tender</h3><span className="badge">Estimate</span></div><p className="bill-hint">Use a shortcut after checking the cash physically received. Final change comes from the server receipt.</p>
 <div className="cash-shortcuts" role="group" aria-label="Cash amount shortcuts">{cashShortcuts(amountDue).map(option=><button type="button" className="secondary" key={option.label} disabled={locked||!available} onClick={()=>onCash(option.value)}>{option.label}<small>{option.value}</small></button>)}</div>
 <label>{label} <input type="number" min="0" step="0.01" inputMode="decimal" value={cash} disabled={locked} aria-describedby={feedbackId} onChange={event=>onCash(event.target.value)}/></label>
 <p id={feedbackId} className={`cash-tender-feedback cash-${tender.status}`} role="status">{!eligible?'This sale is not ready for cash entry.':tender.status==='unavailable'?'Bill amount needs review. Refresh the catalogue before checkout.':tender.status==='missing'?'Enter the cash received or choose a shortcut.':tender.status==='invalid'?'Enter a non-negative amount with up to 12 digits and two decimal places.':tender.status==='short'?`Cash still due: ${formatCash(tender.shortCents!)}`:`Cash covers the estimated bill. Estimated change: ${formatCash(tender.changeCents!)}`}</p>
 <div className="change-due"><span>Estimated change</span><strong>{available&&tender.changeCents!==null?formatCash(tender.changeCents):'—'}</strong></div></section>;
}
