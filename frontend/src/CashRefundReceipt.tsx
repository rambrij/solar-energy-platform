import { useEffect, useState } from 'react';
import { api } from './api';
import { printDocument } from './printDocument';
import type { CashRefundView } from './CashRefunds';
export default function CashRefundReceipt({value,disabled,onCopy}:{value:CashRefundView;disabled:boolean;onCopy:(value:CashRefundView)=>void}){
 const [error,setError]=useState(''),[busy,setBusy]=useState(false),[print,setPrint]=useState(false);
 useEffect(()=>{if(print&&value.copy){printDocument('refund');setPrint(false);}},[print,value]);
 return <section className="sale-receipt refund-receipt" aria-label="Cash refund acknowledgement"><h2>{value.copy?'COPY · ':''}Cash refund acknowledgement</h2><p>{value.id} · {value.status} · {value.method}</p><p>Original receipt {value.originalReceipt} · Return {value.returnId} · Order {value.orderId}</p><p>Paid by {value.cashier} · Session {value.sessionId} · {new Date(value.paidAt).toLocaleString()}</p><ul>{value.items.map(line=><li key={line.productId}>{line.name} × {line.quantity} · Net {Number(line.net).toFixed(2)} · Tax {Number(line.tax).toFixed(2)} · Refunded {Number(line.amount).toFixed(2)}</li>)}</ul><strong>Cash refunded {Number(value.amount).toFixed(2)}</strong><p>{value.reason}</p><p>This acknowledgement is not a tax credit note. Original sale remains unchanged.</p>{error&&<p role="alert">{error}</p>}<button disabled={disabled||busy} onClick={async()=>{setBusy(true);setError('');try{onCopy(await api(`/refunds/${value.id}/copy`,{}));setPrint(true);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Print refund copy</button></section>;
}
