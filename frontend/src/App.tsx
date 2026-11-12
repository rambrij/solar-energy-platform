import { useEffect, useState } from 'react';
import { api, ApiError } from './api';
type Product = {id:number;name:string;sku:string;barcode:string;price:number;tax_rate:number;quantity:number};
type Receipt = {id:string;receipt_number:number;branch_id:number;total:number;cash_received:number;change_given:number;tax_invoice:boolean;copy?:boolean;invoices:{invoice_number:string;seller_name:string;outlet_name:string}[];items:{name:string;quantity:number;price:number;tax:number;tax_rate:number;total:number}[]};
export default function App() {
  const [products,setProducts]=useState<Product[]>([]),[search,setSearch]=useState(''),[session,setSession]=useState(()=>sessionStorage.getItem('pos-session-v1')??'');
  const [cart,setCart]=useState<Record<number,number>>({}),[cash,setCash]=useState(''),[invoice,setInvoice]=useState(false);
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[receipt,setReceipt]=useState<Receipt>();
  const [counted,setCounted]=useState('');
  const [pending,setPending]=useState<{key:string;body:unknown}|undefined>(()=>{
    try {return JSON.parse(sessionStorage.getItem('pos-attempt-v1')??'null')??undefined;} catch {return undefined;}
  });
  useEffect(()=>{api('/products').then(setProducts).catch(e=>setError(e.message));},[]);
  async function open(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault();setBusy(true);setError('');
    const data=new FormData(event.currentTarget);
    try {const id=(await api('/sessions',{registerId:Number(data.get('register')),openingFloat:data.get('float')})).id;sessionStorage.setItem('pos-session-v1',id);setSession(id);}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  async function checkout() {
    const attempt=pending ?? {key:crypto.randomUUID(),body:{sessionId:session,items:Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity})),cashReceived:cash,invoiceRequested:invoice}};
    sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));setPending(attempt);setBusy(true);setError('');
    try {setReceipt(await api('/sales',attempt.body,attempt.key));sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);setCart({});setCash('');api('/products').then(setProducts).catch(()=>setError('Sale completed; stock refresh unavailable.'));}
    catch(e){
      if(e instanceof ApiError && [400,403,404,409].includes(e.status)) {
        sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);
        setError(e.message+' Sale rejected. Check the session, stock and cash amounts before trying again.');
      } else setError((e as Error).message+' Retry uses the same sale key. Do not collect cash again.');
    }finally{setBusy(false);}
  }
  return <main><h1>Saumya Solar · Store POS</h1><p>Walk-in cash checkout · No customer account required</p>
    {error && <p role="alert">{error}</p>}
    {!session ? <form onSubmit={open}><h2>Open cashier session</h2><label>Assigned register <input name="register" type="number" min="1" required /></label><label>Opening float <input name="float" type="number" min="0" step="0.01" required /></label><button disabled={busy}>Open session</button></form> : <>
      <label>Search name, SKU or scan barcode <input value={search} onChange={e=>setSearch(e.target.value)} /></label>
      <ul>{products.filter(p=>[p.name,p.sku,p.barcode??''].some(v=>v.toLowerCase().includes(search.toLowerCase()))).map(p=><li key={p.id}>{p.name} · {p.sku} · {Number(p.price).toFixed(2)} + {p.tax_rate}% tax · {p.quantity} available <button disabled={busy||!!pending||p.quantity<1} onClick={()=>setCart({...cart,[p.id]:(cart[p.id]??0)+1})}>Add {p.name}</button></li>)}</ul>
      <h2>Current sale</h2><fieldset disabled={busy||!!pending}>{Object.entries(cart).map(([id,q])=><label key={id}>{products.find(p=>p.id===Number(id))?.name}<input aria-label={`Quantity ${id}`} type="number" min="1" max="10000" value={q} onChange={e=>setCart({...cart,[id]:Number(e.target.value)})}/><button onClick={()=>setCart(Object.fromEntries(Object.entries(cart).filter(([key])=>key!==id)))}>Remove</button></label>)}
      <p>Estimated total: {Object.entries(cart).reduce((sum,[id,q])=>{const p=products.find(p=>p.id===Number(id));if(!p)return sum;const net=Number(p.price)*q;return sum+net+Math.round(net*Number(p.tax_rate))/100;},0).toFixed(2)}. Final prices, tax and change are calculated by the server.</p><label>Cash received <input type="number" min="0" step="0.01" value={cash} onChange={e=>setCash(e.target.value)}/></label><label><input type="checkbox" checked={invoice} onChange={e=>setInvoice(e.target.checked)}/> Request tax invoice</label></fieldset>
      <button disabled={busy||(!pending&&(!Object.keys(cart).length||!cash))} onClick={checkout}>{busy?'Checking sale…':pending?'Retry same sale':'Complete cash sale'}</button>
      <form onSubmit={async e=>{e.preventDefault();setBusy(true);try{await api(`/sessions/${session}/close`,{countedCash:counted});sessionStorage.removeItem('pos-session-v1');setSession('');setError('');setCart({});}catch(e){setError((e as Error).message+' Session remains open; check cash count or contact your supervisor.');}finally{setBusy(false);}}}><label>Counted drawer cash <input type="number" step="0.01" min="0" required value={counted} onChange={e=>setCounted(e.target.value)}/></label><button disabled={busy||!!pending}>Reconcile and close session</button></form>
    </>}
    {receipt && <section aria-label="Sale receipt"><h2>{receipt.copy?'COPY · ':''}Receipt {receipt.branch_id}/{receipt.receipt_number}</h2><p>Sale {receipt.id}</p>{receipt.items.map((i,n)=><p key={n}>{i.name} × {i.quantity} at {Number(i.price).toFixed(2)} + tax {Number(i.tax).toFixed(2)} ({i.tax_rate}%): {Number(i.total).toFixed(2)}</p>)}<p>Total: {Number(receipt.total).toFixed(2)} · Cash: {Number(receipt.cash_received).toFixed(2)} · Change: {Number(receipt.change_given).toFixed(2)}</p>{receipt.invoices.map(i=><p key={i.invoice_number}>Tax invoice {i.invoice_number} · {i.seller_name} · {i.outlet_name}</p>)}<button disabled={busy} onClick={async()=>{setBusy(true);try{setReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Reprint copy</button></section>}
  </main>;
}
