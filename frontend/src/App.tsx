import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import CashDrawer from './CashDrawer';
import SessionStart from './SessionStart';
import ShiftClose from './ShiftClose';
import ShiftReviews from './ShiftReviews';
import ReservedDrafts from './ReservedDrafts';
import Shell from './Shell';
import OrderHistory from './OrderHistory';
import SalesInsights from './SalesInsights';
import type { Receipt } from './types';
import BillingBasket from './BillingBasket';
import CategoryNav from './CategoryNav';
import PosReturns from './PosReturns';
import CashRefunds, { refundStorageKey, type CashRefundView } from './CashRefunds';
import CashRefundReceipt from './CashRefundReceipt';
import { printDocument } from './printDocument';
type Product = {id:number;name:string;sku:string;barcode:string|null;price:number;tax_rate:number;quantity:number;list_price?:number;discount_percent?:number;category_id?:number|null;category_name?:string};

export default function App() {
  const [refundPending,setRefundPending]=useState(()=>!!sessionStorage.getItem(refundStorageKey));
  const [refundReceipt,setRefundReceipt]=useState<CashRefundView>();
  const [returnOrderId,setReturnOrderId]=useState('');
  function startReturn(id:string){setReturnOrderId(id);requestAnimationFrame(()=>document.getElementById('pos-returns')?.scrollIntoView?.({behavior:'smooth',block:'start'}));}
  const [category,setCategory]=useState('all'),[scanNotice,setScanNotice]=useState(''),[printRequested,setPrintRequested]=useState(false);
  const searchInput=useRef<HTMLInputElement>(null);
  const [products,setProducts]=useState<Product[]>([]),[search,setSearch]=useState(''),[session,setSession]=useState(()=>sessionStorage.getItem('pos-session-v1')??'');
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
  useEffect(()=>{api('/products').then(setProducts).catch(e=>setError(e.message));},[]);
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
    setCheckingSession(false);setSessionCheckFailed(false);setError('');setCart({});setCash('');
  }
  const saleLocked=refundPending||busy||!!pending||reservedBlocked||movementPending||closePending||sessionState!=='OPEN';
  const categories=Array.from(new Set(products.map(product=>String(product.category_id??'other')))).map(id=>({id,name:products.find(product=>String(product.category_id??'other')===id)?.category_name??'Other',count:products.filter(product=>String(product.category_id??'other')===id).length}));
  const visibleProducts=products.filter(product=>(category==='all'||String(product.category_id??'other')===category)&&[product.name,product.sku,product.barcode??''].some(value=>value.toLowerCase().includes(search.toLowerCase())));
  const invalidCart=Object.entries(cart).some(([id,q])=>!Number.isInteger(q)||q<1||q>Math.min(10000,products.find(product=>product.id===Number(id))?.quantity??0));
  function addProduct(product:Product,scanned=false){
    if(saleLocked)return;
    const maximum=Math.min(10000,product.quantity);
    if((cart[product.id]??0)>=maximum){setScanNotice('Available stock or quantity limit reached.');return;}
    setCart(previous=>({...previous,[product.id]:Math.min(maximum,(previous[product.id]??0)+1)}));
    setScanNotice(`Added ${product.name}`);if(scanned){setSearch('');setCategory('all');searchInput.current?.focus();}
  }
  function scan(event:React.FormEvent){event.preventDefault();if(saleLocked)return;const code=search.trim().toLowerCase();const matches=products.filter(product=>code&&[product.sku,product.barcode??''].some(value=>value.toLowerCase()===code));if(matches.length!==1){setScanNotice(matches.length?'More than one barcode match. Select the correct product.':'No exact SKU or barcode match. Use product search and Add.');return;}addProduct(matches[0],true);}
  useEffect(()=>{if(printRequested&&receipt?.copy){printDocument('sale');setPrintRequested(false);}},[printRequested,receipt]);
  function displayReceipt(value:Receipt){setReceipt(value);setReceiptError('');}
  async function checkout() {
    const attempt=pending ?? {key:crypto.randomUUID(),body:{sessionId:session,items:Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity})),cashReceived:cash,invoiceRequested:invoice}};
    sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));setPending(attempt);setBusy(true);setError('');
    try {displayReceipt(await api('/sales',attempt.body,attempt.key));sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);setCart({});setCash('');setDrawerRevision(value=>value+1);api('/products').then(setProducts).catch(()=>setError('Sale completed; stock refresh unavailable.'));}
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
      <div className="checkout-grid"><section id="checkout" className="catalogue-panel" aria-label="POS catalogue"><div className="section-heading"><h2>Solar essentials</h2><span className="badge">{products.length} products</span></div><form className="barcode-search" onSubmit={scan}><label>Search name, SKU or scan barcode <input ref={searchInput} autoComplete="off" value={search} placeholder="Scan barcode or enter SKU, then press Enter" onChange={e=>setSearch(e.target.value)} /></label><button disabled={saleLocked||!search.trim()}>Add exact match</button></form>{scanNotice&&<p role="status" className="scan-notice">{scanNotice}</p>}<CategoryNav categories={categories} selected={category} onSelect={setCategory} total={products.length}/>{!visibleProducts.length&&<div className="empty-state"><p>No products match. Try another category or search.</p></div>}
      <ul className="product-grid">{visibleProducts.map(p=><li key={p.id} className="product-card"><div className="product-art" aria-hidden="true"><span className="panel-illustration"/></div><span className="eyebrow">{p.sku}</span><h3>{p.name}</h3><p className="product-price">{Number(p.list_price??p.price)>Number(p.price)&&<del>{Number(p.list_price).toFixed(2)} </del>}{Number(p.price).toFixed(2)} <small>+ {p.tax_rate}% tax</small></p><span className={`stock-label ${p.quantity < 1 ? 'sold-out' : ''}`}>{p.quantity} available</span><button disabled={saleLocked||(cart[p.id]??0)>=Math.min(10000,p.quantity)} onClick={()=>addProduct(p)}>Add {p.name}</button></li>)}</ul>
      </section><section className="sale-panel retail-bill-panel" aria-labelledby="sale-title"><div className="section-heading"><h2 id="sale-title">Current sale</h2><span className="badge">{Object.values(cart).reduce((sum, q) => sum + q, 0)} items</span></div>{!Object.keys(cart).length && <div className="empty-state"><p>Your next solar sale starts here.</p><small>Add products from the catalogue.</small></div>}<BillingBasket products={products} cart={cart} locked={saleLocked} onQuantity={(id,q)=>setCart(value=>({...value,[id]:q}))} onRemove={id=>setCart(value=>Object.fromEntries(Object.entries(value).filter(([key])=>key!==String(id))))} cash={cash} onCash={setCash} invoice={invoice} onInvoice={setInvoice}/>
      <button disabled={refundPending||busy||movementPending||closePending||reservedBlocked||(!pending&&(sessionState!=='OPEN'||invalidCart||!Object.keys(cart).length||!cash))} onClick={checkout}>{busy?'Checking sale…':pending?'Retry same sale':'Complete cash sale'}</button>
      <ReservedDrafts key={`drafts-${session}`} mode="pos" session={session} createDisabled={invalidCart} items={Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity}))} invoiceRequested={invoice} disabled={refundPending||busy||!!pending||movementPending||closePending||sessionState!=='OPEN'} onBlocked={setReservedBlocked} onReceipt={(value,finalized)=>{displayReceipt(value);if(finalized){setCart({});setCash('');setDrawerRevision(value=>value+1);}}} onStockChange={()=>{api('/products').then(setProducts).catch(()=>setError('Reload product stock after restoring connectivity.'));}}/>
      </section></div><div className="operations-grid" id="register"><CashDrawer key={session} session={session} revision={drawerRevision} disabled={refundPending||busy||!!pending||closePending} readOnly={sessionState!=='OPEN'} onPendingChange={setMovementPending} onBusyChange={setBusy}/>
      <ShiftClose key={`close-${session}`} session={session} disabled={refundPending||busy||!!pending||movementPending||reservedBlocked} onStateChange={setSessionState} onPendingChange={setClosePending} onBusyChange={setBusy}/>
      </div>{sessionState!=='OPEN'&&<button disabled={refundPending||busy||!!pending||movementPending||closePending||reservedBlocked} onClick={()=>{sessionStorage.removeItem('pos-session-v1');setSession('');setError('');}}>Choose another session</button>}
    </>}
    {receipt && <section id="sale-receipt" className="sale-receipt" aria-label="Sale receipt"><h2>{receipt.copy?'COPY · ':''}Receipt {receipt.branch_id}/{receipt.receipt_number}</h2><p>Sale {receipt.id}</p>{receiptError&&<p className="receipt-error" role="alert">{receiptError}</p>}{receipt.items.map((i,n)=><p key={n}>{i.name} × {i.quantity} at {Number(i.price).toFixed(2)} + tax {Number(i.tax).toFixed(2)} ({i.tax_rate}%): {Number(i.total).toFixed(2)}{Number(i.discount??0)>0&&<> · Discount {Number(i.discount).toFixed(2)}</>}</p>)}<p>Total: {Number(receipt.total).toFixed(2)} · Cash: {Number(receipt.cash_received).toFixed(2)} · Change: {Number(receipt.change_given).toFixed(2)}</p>{receipt.invoices.map(i=><p key={i.invoice_number}>Tax invoice {i.invoice_number} · {i.seller_name} · {i.outlet_name}</p>)}<button disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Reprint copy</button><button className="secondary" disabled={busy} onClick={()=>startReturn(receipt.id)}>Start a stock return</button><button className="print-receipt" disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');setPrintRequested(true);}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Print receipt copy</button></section>}
    <OrderHistory mode="pos" onReturn={startReturn} onReceipt={value=>{displayReceipt(value);requestAnimationFrame(()=>document.getElementById('sale-receipt')?.scrollIntoView?.({behavior:'smooth',block:'start'}));}}/>
    <PosReturns session={session} sessionState={sessionState} disabled={refundPending||busy||!!pending||movementPending||closePending} initialOrderId={returnOrderId} refundedReturnId={refundReceipt?.returnId} onStockChange={()=>{api('/products').then(setProducts).catch(()=>setError('Reload stock after return inspection.'));}}/>
    <CashRefunds session={session} sessionState={sessionState} disabled={busy||!!pending||movementPending||closePending||reservedBlocked||checkingSession} onPendingChange={setRefundPending} onBusyChange={setBusy} onRecorded={value=>{setRefundReceipt(value);setDrawerRevision(previous=>previous+1);}}/>
    {refundReceipt&&<CashRefundReceipt value={refundReceipt} disabled={busy||refundPending} onCopy={setRefundReceipt}/>}
    <SalesInsights/>
    <ShiftReviews/>
  </Shell>;
}
