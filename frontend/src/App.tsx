import { useEffect, useState } from 'react';
import { api, ApiError } from './api';
import CashDrawer from './CashDrawer';
import SessionStart from './SessionStart';
import ShiftClose from './ShiftClose';
import ShiftReviews from './ShiftReviews';
import ReservedDrafts from './ReservedDrafts';
import Shell from './Shell';
import OrderHistory from './OrderHistory';
import type { Receipt } from './types';
type Product = {id:number;name:string;sku:string;barcode:string;price:number;tax_rate:number;quantity:number};

export default function App() {
  const [products,setProducts]=useState<Product[]>([]),[search,setSearch]=useState(''),[session,setSession]=useState(()=>sessionStorage.getItem('pos-session-v1')??'');
  const [cart,setCart]=useState<Record<number,number>>({}),[cash,setCash]=useState(''),[invoice,setInvoice]=useState(false);
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[receipt,setReceipt]=useState<Receipt>();
  const [closePending,setClosePending]=useState(()=>{
    const stored=sessionStorage.getItem('pos-session-v1');return !!stored&&!!sessionStorage.getItem(`pos-close-attempt-v1:${stored}`);
  });
  const [drawerRevision,setDrawerRevision]=useState(0),[movementPending,setMovementPending]=useState(()=>{
    const stored=sessionStorage.getItem('pos-session-v1');
    return !!stored&&!!sessionStorage.getItem(`pos-drawer-attempt-v1:${stored}`);
  });
  const [checkingSession,setCheckingSession]=useState(()=>!!sessionStorage.getItem('pos-session-v1'));
  const [sessionCheckFailed,setSessionCheckFailed]=useState(false),[checkRevision,setCheckRevision]=useState(0);
  const [sessionState,setSessionState]=useState('OPEN');
  const [reservedBlocked,setReservedBlocked]=useState(()=>{
    const stored=sessionStorage.getItem('pos-session-v1');return !!stored&&!!sessionStorage.getItem(`pos-reserved-attempt-v1:${stored}`);
  });
  const [pending,setPending]=useState<{key:string;body:unknown}|undefined>(()=>{
    try {return JSON.parse(sessionStorage.getItem('pos-attempt-v1')??'null')??undefined;} catch {return undefined;}
  });
  useEffect(()=>{api('/products').then(setProducts).catch(e=>setError(e.message));},[]);
  useEffect(()=>{
    const stored=sessionStorage.getItem('pos-session-v1');
    if(!stored){setCheckingSession(false);return;}
    let active=true;
    setCheckingSession(true);setSessionCheckFailed(false);
    api(`/sessions/${stored}`).then(details=>{
      if(!active)return;
      const hasAttempt=!!sessionStorage.getItem('pos-attempt-v1')||!!sessionStorage.getItem(`pos-drawer-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-close-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-reserved-attempt-v1:${stored}`);
      if(details.state==='CLOSED'&&!hasAttempt){
        sessionStorage.removeItem('pos-session-v1');setSession('');
        setError('Your previous session is closed. Open or resume a session below.');
      }else{
        setSessionState(details.state);
        setError(details.state==='CLOSED'?'This session is closed. Resolve saved attempts before choosing another session.':'');
      }
      setCheckingSession(false);
    }).catch(e=>{
      if(!active)return;
      const hasAttempt=!!sessionStorage.getItem('pos-attempt-v1')||!!sessionStorage.getItem(`pos-drawer-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-close-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-reserved-attempt-v1:${stored}`);
      if(e instanceof ApiError&&e.status===404&&!hasAttempt){
        sessionStorage.removeItem('pos-session-v1');setSession('');setCheckingSession(false);
        setError('The saved session is unavailable for your account. Choose an authorized session below.');
      }else{
        setSessionCheckFailed(true);
        setError('Could not verify your saved session. Check your login or connection and retry. Saved attempts have been kept.');
      }
    });
    return()=>{active=false;};
  },[checkRevision]);
  function activateSession(id:string,state='OPEN'){
    sessionStorage.setItem('pos-session-v1',id);setSession(id);setSessionState(state);
    setCheckingSession(false);setSessionCheckFailed(false);setError('');setCart({});setCash('');
  }
  async function checkout() {
    const attempt=pending ?? {key:crypto.randomUUID(),body:{sessionId:session,items:Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity})),cashReceived:cash,invoiceRequested:invoice}};
    sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));setPending(attempt);setBusy(true);setError('');
    try {setReceipt(await api('/sales',attempt.body,attempt.key));sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);setCart({});setCash('');setDrawerRevision(value=>value+1);api('/products').then(setProducts).catch(()=>setError('Sale completed; stock refresh unavailable.'));}
    catch(e){
      const deniedRetry=!!pending&&e instanceof ApiError&&[403,404].includes(e.status);
      if(e instanceof ApiError && [400,403,404,409].includes(e.status)&&!deniedRetry) {
        sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);
        setError(e.message+' Sale rejected. Check the session, stock and cash amounts before trying again.');
      } else setError((e as Error).message+' Retry uses the same sale key. Do not collect cash again.');
    }finally{setBusy(false);}
  }
  return <Shell mode="pos"><header className="workspace-hero"><div><span className="eyebrow">Powering your everyday business</span><h1>Saumya Solar · Store POS</h1><p>Walk-in cash checkout · No customer account required</p></div><div className="hero-status"><span className={`badge ${sessionState === 'OPEN' && session ? 'status-paid' : ''}`}>{checkingSession ? 'Verifying session' : session ? `Session ${sessionState}` : 'Register setup'}</span><span>Live pricing & shared inventory</span></div></header>
    {error && <p role="alert">{error}</p>}
    {checkingSession ? <section><p role="status">Checking saved cashier session…</p>{sessionCheckFailed&&<button onClick={()=>setCheckRevision(value=>value+1)}>Retry session check</button>}</section> : !session ? <SessionStart onSession={activateSession} requiredSessionId={(pending?.body as {sessionId?:string}|undefined)?.sessionId}/> : <>
      <div className="checkout-grid"><section id="checkout" className="catalogue-panel" aria-label="POS catalogue"><div className="section-heading"><h2>Solar essentials</h2><span className="badge">{products.length} products</span></div><label>Search name, SKU or scan barcode <input value={search} onChange={e=>setSearch(e.target.value)} /></label>
      <ul className="product-grid">{products.filter(p=>[p.name,p.sku,p.barcode??''].some(v=>v.toLowerCase().includes(search.toLowerCase()))).map(p=><li key={p.id} className="product-card"><div className="product-art" aria-hidden="true"><span className="panel-illustration"/></div><span className="eyebrow">{p.sku}</span><h3>{p.name}</h3><p className="product-price">{Number(p.price).toFixed(2)} <small>+ {p.tax_rate}% tax</small></p><span className={`stock-label ${p.quantity < 1 ? 'sold-out' : ''}`}>{p.quantity} available</span><button disabled={busy||!!pending||reservedBlocked||sessionState!=='OPEN'||p.quantity<1} onClick={()=>setCart({...cart,[p.id]:(cart[p.id]??0)+1})}>Add {p.name}</button></li>)}</ul>
      </section><section className="sale-panel" aria-labelledby="sale-title"><div className="section-heading"><h2 id="sale-title">Current sale</h2><span className="badge">{Object.values(cart).reduce((sum, q) => sum + q, 0)} items</span></div>{!Object.keys(cart).length && <div className="empty-state"><p>Your next solar sale starts here.</p><small>Add products from the catalogue.</small></div>}<fieldset disabled={busy||!!pending||reservedBlocked}>{Object.entries(cart).map(([id,q])=><label key={id}>{products.find(p=>p.id===Number(id))?.name}<input aria-label={`Quantity ${id}`} type="number" min="1" max="10000" value={q} onChange={e=>setCart({...cart,[id]:Number(e.target.value)})}/><button onClick={()=>setCart(Object.fromEntries(Object.entries(cart).filter(([key])=>key!==id)))}>Remove</button></label>)}
      <p>Estimated total: {Object.entries(cart).reduce((sum,[id,q])=>{const p=products.find(p=>p.id===Number(id));if(!p)return sum;const net=Number(p.price)*q;return sum+net+Math.round(net*Number(p.tax_rate))/100;},0).toFixed(2)}. Final prices, tax and change are calculated by the server.</p><label>Cash received <input type="number" min="0" step="0.01" value={cash} onChange={e=>setCash(e.target.value)}/></label><label><input type="checkbox" checked={invoice} onChange={e=>setInvoice(e.target.checked)}/> Request tax invoice</label></fieldset>
      <button disabled={busy||movementPending||closePending||reservedBlocked||(!pending&&(sessionState!=='OPEN'||!Object.keys(cart).length||!cash))} onClick={checkout}>{busy?'Checking sale…':pending?'Retry same sale':'Complete cash sale'}</button>
      <ReservedDrafts key={`drafts-${session}`} mode="pos" session={session} items={Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity}))} invoiceRequested={invoice} disabled={busy||!!pending||movementPending||closePending||sessionState!=='OPEN'} onBlocked={setReservedBlocked} onReceipt={(value,finalized)=>{setReceipt(value);if(finalized){setCart({});setCash('');setDrawerRevision(value=>value+1);}}} onStockChange={()=>{api('/products').then(setProducts).catch(()=>setError('Reload product stock after restoring connectivity.'));}}/>
      </section></div><div className="operations-grid" id="register"><CashDrawer key={session} session={session} revision={drawerRevision} disabled={busy||!!pending||closePending} readOnly={sessionState!=='OPEN'} onPendingChange={setMovementPending} onBusyChange={setBusy}/>
      <ShiftClose key={`close-${session}`} session={session} disabled={busy||!!pending||movementPending||reservedBlocked} onStateChange={setSessionState} onPendingChange={setClosePending} onBusyChange={setBusy}/>
      </div>{sessionState!=='OPEN'&&<button disabled={busy||!!pending||movementPending||closePending||reservedBlocked} onClick={()=>{sessionStorage.removeItem('pos-session-v1');setSession('');setError('');}}>Choose another session</button>}
    </>}
    {receipt && <section id="sale-receipt" className="sale-receipt" aria-label="Sale receipt"><h2>{receipt.copy?'COPY · ':''}Receipt {receipt.branch_id}/{receipt.receipt_number}</h2><p>Sale {receipt.id}</p>{receipt.items.map((i,n)=><p key={n}>{i.name} × {i.quantity} at {Number(i.price).toFixed(2)} + tax {Number(i.tax).toFixed(2)} ({i.tax_rate}%): {Number(i.total).toFixed(2)}</p>)}<p>Total: {Number(receipt.total).toFixed(2)} · Cash: {Number(receipt.cash_received).toFixed(2)} · Change: {Number(receipt.change_given).toFixed(2)}</p>{receipt.invoices.map(i=><p key={i.invoice_number}>Tax invoice {i.invoice_number} · {i.seller_name} · {i.outlet_name}</p>)}<button disabled={busy} onClick={async()=>{setBusy(true);try{setReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Reprint copy</button></section>}
    <OrderHistory mode="pos" onReceipt={value=>{setReceipt(value);requestAnimationFrame(()=>document.getElementById('sale-receipt')?.scrollIntoView?.({behavior:'smooth',block:'start'}));}}/>
    <ShiftReviews/>
  </Shell>;
}
