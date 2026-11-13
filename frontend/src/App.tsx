import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from './api';
import CashDrawer from './CashDrawer';
import SessionStart from './SessionStart';
import ShiftClose from './ShiftClose';
import ShiftHistory from './ShiftHistory';
import ShiftReviews from './ShiftReviews';
import ReservedDrafts from './ReservedDrafts';
import Shell from './Shell';
import OrderHistory from './OrderHistory';
import SalesInsights from './SalesInsights';
import RefundInsights from './RefundInsights';
import CashierSalesInsights from './CashierSalesInsights';
import type { Receipt } from './types';
import BillingBasket from './BillingBasket';
import PosCatalogue,{type PosProduct as Product} from './PosCatalogue';
import PosReturns from './PosReturns';
import CashRefunds, { refundStorageKey, type CashRefundView } from './CashRefunds';
import CashRefundReceipt from './CashRefundReceipt';
import { printDocument } from './printDocument';


export default function App() {
  const [refundPending,setRefundPending]=useState(()=>!!sessionStorage.getItem(refundStorageKey));
  const [refundReceipt,setRefundReceipt]=useState<CashRefundView>();
  const [returnOrderId,setReturnOrderId]=useState('');
  function startReturn(id:string){setReturnOrderId(id);requestAnimationFrame(()=>document.getElementById('pos-returns')?.scrollIntoView?.({behavior:'smooth',block:'start'}));}
  const [printRequested,setPrintRequested]=useState(false),[catalogueRevision,setCatalogueRevision]=useState(0);
  const refreshProducts=useCallback((items:Product[])=>setProducts(previous=>previous.map(product=>items.find(item=>item.id===product.id)??product)),[]);
  const [products,setProducts]=useState<Product[]>([]),[session,setSession]=useState(()=>sessionStorage.getItem('pos-session-v1')??'');
  const [cart,setCart]=useState<Record<number,number>>({}),[cash,setCash]=useState(''),[invoice,setInvoice]=useState(false);
  const [receiptError,setReceiptError]=useState('');
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

  useEffect(()=>{
    const stored=sessionStorage.getItem('pos-session-v1');
    if(!stored){setCheckingSession(false);return;}
    let active=true;
    setCheckingSession(true);setSessionCheckFailed(false);
    api(`/sessions/${stored}`).then(details=>{
      if(!active)return;
      const hasAttempt=!!sessionStorage.getItem(refundStorageKey)||!!sessionStorage.getItem('pos-attempt-v1')||!!sessionStorage.getItem(`pos-drawer-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-close-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-reserved-attempt-v1:${stored}`);
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
      const hasAttempt=!!sessionStorage.getItem(refundStorageKey)||!!sessionStorage.getItem('pos-attempt-v1')||!!sessionStorage.getItem(`pos-drawer-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-close-attempt-v1:${stored}`)||!!sessionStorage.getItem(`pos-reserved-attempt-v1:${stored}`);
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
    setCheckingSession(false);setSessionCheckFailed(false);setError('');setCart({});setCash('');setProducts([]);
  }
  const saleLocked=refundPending||busy||!!pending||reservedBlocked||movementPending||closePending||sessionState!=='OPEN';
  const invalidCart=Object.entries(cart).some(([id,q])=>!Number.isInteger(q)||q<1||q>Math.min(10000,products.find(product=>product.id===Number(id))?.quantity??0));
  function addProduct(product:Product){
    if(saleLocked||(!(product.id in cart)&&Object.keys(cart).length>=100))return false;
    const maximum=Math.min(10000,product.quantity);if((cart[product.id]??0)>=maximum)return false;
    setProducts(previous=>[...previous.filter(item=>item.id!==product.id),product]);
    setCart(previous=>({...previous,[product.id]:Math.min(maximum,(previous[product.id]??0)+1)}));return true;
  }
  useEffect(()=>{if(printRequested&&receipt?.copy){printDocument('sale');setPrintRequested(false);}},[printRequested,receipt]);
  function displayReceipt(value:Receipt){setReceipt(value);setReceiptError('');}
  async function checkout() {
    const attempt=pending ?? {key:crypto.randomUUID(),body:{sessionId:session,items:Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity})),cashReceived:cash,invoiceRequested:invoice}};
    sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));setPending(attempt);setBusy(true);setError('');
    try {displayReceipt(await api('/sales',attempt.body,attempt.key));sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);setCart({});setCash('');setDrawerRevision(value=>value+1);setProducts([]);setCatalogueRevision(value=>value+1);}
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
      <div className="checkout-grid"><PosCatalogue key={session} locked={saleLocked} quantities={cart} revision={catalogueRevision} onProducts={refreshProducts} onAdd={addProduct} onBusyChange={setBusy}/>
      <section id="current-sale" className="sale-panel retail-bill-panel" aria-labelledby="sale-title"><div className="section-heading"><h2 id="sale-title">Current sale</h2><span className="badge">{Object.values(cart).reduce((sum, q) => sum + q, 0)} items</span></div>{!Object.keys(cart).length && <div className="empty-state"><p>Your next solar sale starts here.</p><small>Add products from the catalogue.</small></div>}<BillingBasket products={products} cart={cart} locked={saleLocked} onQuantity={(id,q)=>setCart(value=>({...value,[id]:q}))} onRemove={id=>{setCart(value=>Object.fromEntries(Object.entries(value).filter(([key])=>key!==String(id))));setProducts(value=>value.filter(product=>product.id!==id));}} cash={cash} onCash={setCash} invoice={invoice} onInvoice={setInvoice}/>
      <button disabled={refundPending||busy||movementPending||closePending||reservedBlocked||(!pending&&(sessionState!=='OPEN'||invalidCart||!Object.keys(cart).length||!cash))} onClick={checkout}>{busy?'Checking sale…':pending?'Retry same sale':'Complete cash sale'}</button>
      <ReservedDrafts key={`drafts-${session}`} mode="pos" session={session} createDisabled={invalidCart} items={Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity}))} invoiceRequested={invoice} disabled={refundPending||busy||!!pending||movementPending||closePending||sessionState!=='OPEN'} onBlocked={setReservedBlocked} onReceipt={(value,finalized)=>{displayReceipt(value);if(finalized){setCart({});setProducts([]);setCash('');setDrawerRevision(value=>value+1);}}} onStockChange={()=>{setCatalogueRevision(value=>value+1);}}/>
      </section></div><div className="operations-grid" id="register"><CashDrawer key={session} session={session} revision={drawerRevision} disabled={refundPending||busy||!!pending||closePending} readOnly={sessionState!=='OPEN'} onPendingChange={setMovementPending} onBusyChange={setBusy}/>
      <ShiftClose key={`close-${session}`} session={session} disabled={refundPending||busy||!!pending||movementPending||reservedBlocked} onStateChange={setSessionState} onPendingChange={setClosePending} onBusyChange={setBusy}/>
      </div>{sessionState!=='OPEN'&&<button disabled={refundPending||busy||!!pending||movementPending||closePending||reservedBlocked} onClick={()=>{sessionStorage.removeItem('pos-session-v1');setSession('');setError('');}}>Choose another session</button>}
    </>}
    {receipt && <section id="sale-receipt" className="sale-receipt" aria-label="Sale receipt"><h2>{receipt.copy?'COPY · ':''}Receipt {receipt.branch_id}/{receipt.receipt_number}</h2><p>Sale {receipt.id}</p>{receiptError&&<p className="receipt-error" role="alert">{receiptError}</p>}{receipt.items.map((i,n)=><p key={n}>{i.name} × {i.quantity} at {Number(i.price).toFixed(2)} + tax {Number(i.tax).toFixed(2)} ({i.tax_rate}%): {Number(i.total).toFixed(2)}{Number(i.discount??0)>0&&<> · Discount {Number(i.discount).toFixed(2)}</>}</p>)}<p>Total: {Number(receipt.total).toFixed(2)} · Cash: {Number(receipt.cash_received).toFixed(2)} · Change: {Number(receipt.change_given).toFixed(2)}</p>{receipt.invoices.map(i=><p key={i.invoice_number}>Tax invoice {i.invoice_number} · {i.seller_name} · {i.outlet_name}</p>)}<button disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Reprint copy</button><button className="secondary" disabled={busy} onClick={()=>startReturn(receipt.id)}>Start a stock return</button><button className="print-receipt" disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');setPrintRequested(true);}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Print receipt copy</button></section>}
    <OrderHistory mode="pos" onReturn={startReturn} onReceipt={value=>{displayReceipt(value);requestAnimationFrame(()=>document.getElementById('sale-receipt')?.scrollIntoView?.({behavior:'smooth',block:'start'}));}}/>
    <PosReturns session={session} sessionState={sessionState} disabled={refundPending||busy||!!pending||movementPending||closePending} initialOrderId={returnOrderId} refundedReturnId={refundReceipt?.returnId} onStockChange={()=>{setCatalogueRevision(value=>value+1);}}/>
    <CashRefunds session={session} sessionState={sessionState} disabled={busy||!!pending||movementPending||closePending||reservedBlocked||checkingSession} onPendingChange={setRefundPending} onBusyChange={setBusy} onRecorded={value=>{setRefundReceipt(value);setDrawerRevision(previous=>previous+1);}}/>
    {refundReceipt&&<CashRefundReceipt value={refundReceipt} disabled={busy||refundPending} onCopy={setRefundReceipt}/>}
    <SalesInsights/>
    <RefundInsights/>
    <CashierSalesInsights/>
    <ShiftHistory disabled={busy||checkingSession} revision={`${session}:${sessionState}`}/>
    <ShiftReviews/>
  </Shell>;
}
