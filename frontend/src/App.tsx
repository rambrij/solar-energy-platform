import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from './api';
import CashDrawer from './CashDrawer';
import SessionStart from './SessionStart';
import ShiftClose from './ShiftClose';
import ShiftHistory from './ShiftHistory';
import ShiftReviews from './ShiftReviews';
import ReservedDrafts from './ReservedDrafts';
import Shell from './Shell';
import PromotionHistory from './PromotionHistory';
import OrderHistory from './OrderHistory';
import SalesInsights from './SalesInsights';
import RefundInsights from './RefundInsights';
import CashierSalesInsights from './CashierSalesInsights';
import type { Receipt } from './types';
import SaleReceipt from './SaleReceipt';
import ReceiptPdfCopy from './ReceiptPdfCopy';
import BillingBasket,{billEstimate,type SaleDiscounts} from './BillingBasket';
import {assessCash} from './cashTenderMath';
import PosCatalogue,{type PosProduct as Product} from './PosCatalogue';
import PosReturns from './PosReturns';
import CashRefunds, { refundStorageKey, type CashRefundView } from './CashRefunds';
import CashRefundReceipt from './CashRefundReceipt';
import { printDocument } from './printDocument';
import TerminalTenderReview from './TerminalTenderReview';
import StoreSettings from './StoreSettings';
import CategoryManager from './CategoryManager';
import ProductManager from './ProductManager';
import StockReceiving from './StockReceiving';
import VendorAccessAdmin from './VendorAccessAdmin';
import VendorCustomerCrm from './VendorCustomerCrm';
import BuyerDetailsForm,{type BuyerDetails} from './BuyerDetailsForm';
import {useSession} from './session';
import RouteOutlet from './RouteOutlet';
import { navigateWorkspace } from './navigation';


export default function App() {
  const {account}=useSession();
  const effectivePermissions=account?.permissions??[];
  const has=(permission:string)=>effectivePermissions.includes(permission);
  const canCreateSale=has('POS_SALE_CREATE');
  const canCompleteCashSale=['POS_SALE_CREATE','POS_TENDER_CASH_RECORD','POS_RECEIPT_VIEW'].every(has);
  const canApplyDiscount=has('POS_DISCOUNT_APPLY');
  const canViewPromotionHistory=has('POS_VIEW')&&has('CATALOG_PROMOTION_MANAGE')&&has('POS_DISCOUNT_APPROVE');
  const canReprintReceipt=has('POS_RECEIPT_REPRINT');
  const [refundPending,setRefundPending]=useState(()=>!!sessionStorage.getItem(refundStorageKey));
  const [refundReceipt,setRefundReceipt]=useState<CashRefundView>();
  const [promotionProduct,setPromotionProduct]=useState<number>();
  const [returnOrderId,setReturnOrderId]=useState('');
  function startReturn(id:string){setReturnOrderId(id);navigateWorkspace('pos','pos-returns');}
  const [printRequested,setPrintRequested]=useState(false),[catalogueRevision,setCatalogueRevision]=useState(0);
  const refreshProducts=useCallback((items:Product[])=>setProducts(previous=>previous.map(product=>items.find(item=>item.id===product.id)??product)),[]);
  const [buyer,setBuyer]=useState<BuyerDetails>();
  const [discounts,setDiscounts]=useState<SaleDiscounts>({linePercent:{},billPercent:0,reason:''});
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
    sessionStorage.setItem('pos-session-v1',id);setSession(id);setSessionState(state);setDiscounts({linePercent:{},billPercent:0,reason:''});setBuyer(undefined);
    setCheckingSession(false);setSessionCheckFailed(false);setError('');setCart({});setCash('');setProducts([]);
  }
  const saleLocked=refundPending||busy||!!pending||reservedBlocked||movementPending||closePending||sessionState!=='OPEN'||!canCreateSale;
  const invalidDiscounts=Object.values(discounts.linePercent).concat(discounts.billPercent).some(value=>!Number.isFinite(value)||value<0||value>100)||((discounts.billPercent>0||Object.values(discounts.linePercent).some(value=>value>0))&&!discounts.reason.trim());
  const invalidCart=invalidDiscounts||Object.entries(cart).some(([id,q])=>!Number.isInteger(q)||q<1||q>Math.min(10000,products.find(product=>product.id===Number(id))?.quantity??0));
  const cashReady=assessCash(cash,billEstimate(products,cart,discounts).total).status==='ready';
  function addProduct(product:Product){
    if(saleLocked||(!(product.id in cart)&&Object.keys(cart).length>=100))return false;
    const maximum=Math.min(10000,product.quantity);if((cart[product.id]??0)>=maximum)return false;
    setProducts(previous=>[...previous.filter(item=>item.id!==product.id),product]);
    setCart(previous=>({...previous,[product.id]:Math.min(maximum,(previous[product.id]??0)+1)}));return true;
  }
  useEffect(()=>{if(printRequested&&receipt?.copy){printDocument('sale');setPrintRequested(false);}},[printRequested,receipt]);
  function displayReceipt(value:Receipt){setReceipt(value);setReceiptError('');navigateWorkspace('pos','sale-receipt');}
  async function checkout() {
    if(!pending&&(invalidCart||!Object.keys(cart).length||!cashReady))return;
    const attempt=pending ?? {key:crypto.randomUUID(),body:{sessionId:session,items:Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity})),cashReceived:cash,invoiceRequested:invoice,discounts,...(buyer?{buyer}:{})}};
    sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));setPending(attempt);setBusy(true);setError('');
    try {displayReceipt(await api('/sales',attempt.body,attempt.key));sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);setCart({});setDiscounts({linePercent:{},billPercent:0,reason:''});setBuyer(undefined);setCash('');setDrawerRevision(value=>value+1);setProducts([]);setCatalogueRevision(value=>value+1);}
    catch(e){
      const deniedRetry=!!pending&&e instanceof ApiError&&[403,404].includes(e.status);
      if(e instanceof ApiError && [400,403,404,409].includes(e.status)&&!deniedRetry) {
        sessionStorage.removeItem('pos-attempt-v1');setPending(undefined);
        setError(e.message+' Sale rejected. Check the session, stock and cash amounts before trying again.');
      } else setError((e as Error).message+' Retry uses the same sale key. Do not collect cash again.');
    }finally{setBusy(false);}
  }
  return <Shell mode="pos"><div className="workspace-session-strip"><span className={`badge ${sessionState === 'OPEN' && session ? 'status-paid' : ''}`}>{checkingSession ? 'Verifying session' : session ? `Session ${sessionState}` : 'Register setup'}</span><span>{session ? 'Live pricing & shared inventory' : 'Walk-in billing · No customer account required'}</span></div>
    {error && <p role="alert">{error}</p>}
    {checkingSession ? <section><p role="status">Checking saved cashier session…</p>{sessionCheckFailed&&<button onClick={()=>setCheckRevision(value=>value+1)}>Retry session check</button>}</section> : !session ? <SessionStart onSession={activateSession} requiredSessionId={(pending?.body as {sessionId?:string}|undefined)?.sessionId}/> : <>
      <div className="checkout-grid"><RouteOutlet id="checkout" mode="pos"><PosCatalogue key={session} locked={saleLocked} canCreateSale={canCreateSale} quantities={cart} revision={catalogueRevision} onProducts={refreshProducts} onPromotionHistory={canViewPromotionHistory?id=>{setPromotionProduct(id);navigateWorkspace('pos','promotion-history');}:undefined} onAdd={addProduct} onBusyChange={setBusy}/></RouteOutlet>
      <RouteOutlet id="current-sale" mode="pos" aliases={["reserved-pos-checkout"]}><section id="current-sale" className="sale-panel retail-bill-panel" aria-labelledby="sale-title"><div className="section-heading"><h2 id="sale-title">Current sale</h2><span className="badge">{Object.values(cart).reduce((sum, q) => sum + q, 0)} items</span></div>{!Object.keys(cart).length && <div className="empty-state"><p>Your next solar sale starts here.</p><small>Add products from the catalogue.</small></div>}<BillingBasket products={products} cart={cart} locked={saleLocked} onQuantity={(id,q)=>setCart(value=>({...value,[id]:q}))} onRemove={id=>{setDiscounts(value=>({...value,linePercent:Object.fromEntries(Object.entries(value.linePercent).filter(([key])=>key!==String(id)))}));setCart(value=>Object.fromEntries(Object.entries(value).filter(([key])=>key!==String(id))));setProducts(value=>value.filter(product=>product.id!==id));}} cash={cash} onCash={setCash} invoice={invoice} onInvoice={setInvoice} discounts={discounts} onDiscounts={canApplyDiscount?setDiscounts:undefined}/><BuyerDetailsForm value={buyer} locked={saleLocked} onChange={setBuyer}/>
      {canCompleteCashSale&&<button disabled={refundPending||busy||movementPending||closePending||reservedBlocked||(!pending&&(sessionState!=='OPEN'||invalidCart||!Object.keys(cart).length||!cashReady))} onClick={checkout}>{busy?'Checking sale…':pending?'Retry same sale':'Complete cash sale'}</button>}
      <ReservedDrafts key={`drafts-${session}`} mode="pos" session={session} createDisabled={invalidCart||!canCreateSale} items={Object.entries(cart).map(([id,quantity])=>({productId:Number(id),quantity}))} invoiceRequested={invoice} discounts={discounts} buyer={buyer} disabled={refundPending||busy||!!pending||movementPending||closePending||sessionState!=='OPEN'} onBlocked={setReservedBlocked} onReceipt={(value,finalized)=>{displayReceipt(value);if(finalized){setDiscounts({linePercent:{},billPercent:0,reason:''});setBuyer(undefined);setCart({});setProducts([]);setCash('');setDrawerRevision(value=>value+1);}}} onStockChange={()=>{setCatalogueRevision(value=>value+1);}}/>
      </section></RouteOutlet></div><RouteOutlet id="register" mode="pos"><div className="operations-grid" id="register"><CashDrawer key={session} session={session} revision={drawerRevision} disabled={refundPending||busy||!!pending||closePending} readOnly={sessionState!=='OPEN'} onPendingChange={setMovementPending} onBusyChange={setBusy}/>
      <ShiftClose key={`close-${session}`} session={session} disabled={refundPending||busy||!!pending||movementPending||reservedBlocked} onStateChange={setSessionState} onPendingChange={setClosePending} onBusyChange={setBusy}/>
      </div></RouteOutlet>{sessionState!=='OPEN'&&<button disabled={refundPending||busy||!!pending||movementPending||closePending||reservedBlocked} onClick={()=>{sessionStorage.removeItem('pos-session-v1');setSession('');setError('');}}>Choose another session</button>}
    </>}
    {receipt && <RouteOutlet id="sale-receipt" mode="pos"><SaleReceipt receipt={receipt} error={receiptError}>{canReprintReceipt&&<button disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Reprint copy</button>}{account?.permissions.includes('POS_RETURN_CREATE') && <button className="secondary" disabled={busy} onClick={()=>startReturn(receipt.id)}>Start a stock return</button>}{canReprintReceipt&&<button className="print-receipt" disabled={busy} onClick={async()=>{setBusy(true);try{displayReceipt(await api(`/receipts/${receipt.id}/reprint`,{}));setReceiptError('');setPrintRequested(true);}catch(e){setReceiptError((e as Error).message);}finally{setBusy(false);}}}>Print receipt copy</button>}<ReceiptPdfCopy orderId={receipt.id} disabled={busy}/></SaleReceipt></RouteOutlet>}
    <RouteOutlet id="order-history" mode="pos"><OrderHistory mode="pos" onReturn={has('POS_RETURN_CREATE')?startReturn:undefined} onReceipt={has('POS_RECEIPT_VIEW')?value=>{displayReceipt(value);navigateWorkspace('pos','sale-receipt');}:undefined}/></RouteOutlet>
    <RouteOutlet id="pos-returns" mode="pos"><PosReturns session={session} sessionState={sessionState} disabled={refundPending||busy||!!pending||movementPending||closePending} initialOrderId={returnOrderId} refundedReturnId={refundReceipt?.returnId} onStockChange={()=>{setCatalogueRevision(value=>value+1);}}/></RouteOutlet>
    <RouteOutlet id="cash-refunds" mode="pos"><>
      <CashRefunds session={session} sessionState={sessionState} disabled={busy||!!pending||movementPending||closePending||reservedBlocked||checkingSession} onPendingChange={setRefundPending} onBusyChange={setBusy} onRecorded={value=>{setRefundReceipt(value);setDrawerRevision(previous=>previous+1);}}/>
      {refundReceipt&&<CashRefundReceipt value={refundReceipt} disabled={busy||refundPending} onCopy={setRefundReceipt}/>}
    </></RouteOutlet>
    <RouteOutlet id="promotion-history" mode="pos"><PromotionHistory initialProduct={promotionProduct} disabled={busy||checkingSession}/></RouteOutlet>
    <RouteOutlet id="sales-insights" mode="pos"><SalesInsights/></RouteOutlet>
    <RouteOutlet id="refund-insights" mode="pos"><RefundInsights/></RouteOutlet>
    <RouteOutlet id="cashier-sales-insights" mode="pos"><CashierSalesInsights/></RouteOutlet>
    <RouteOutlet id="shift-history" mode="pos"><ShiftHistory revision={String(drawerRevision)}/></RouteOutlet>
    {account?.permissions.some(permission=>['PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE'].includes(permission))&&<RouteOutlet id="category-management" mode="pos"><CategoryManager onChanged={()=>setCatalogueRevision(value=>value+1)}/></RouteOutlet>}
    {account?.permissions.some(permission=>['PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE'].includes(permission))&&<RouteOutlet id="product-management" mode="pos"><ProductManager onChanged={()=>setCatalogueRevision(value=>value+1)}/></RouteOutlet>}
    {account?.permissions.some(permission=>['INVENTORY_RECEIVE','INVENTORY_VIEW'].includes(permission))&&<RouteOutlet id="stock-receiving" mode="pos"><StockReceiving key={`${account.username}:${account.tenantId}:${account.branchId}`} onChanged={()=>setCatalogueRevision(value=>value+1)}/></RouteOutlet>}
    <RouteOutlet id="store-settings" mode="pos"><StoreSettings/></RouteOutlet>
    <RouteOutlet id="shift-reviews" mode="pos"><ShiftReviews/></RouteOutlet>
    <RouteOutlet id="role-management" mode="pos"><VendorAccessAdmin mode="roles"/></RouteOutlet>
    <RouteOutlet id="employee-management" mode="pos"><VendorAccessAdmin mode="employees"/></RouteOutlet>
    <RouteOutlet id="vendor-customer-crm" mode="pos"><VendorCustomerCrm/></RouteOutlet>
    <RouteOutlet id="terminal-tender-review" mode="pos"><TerminalTenderReview onResolved={()=>setCatalogueRevision(value=>value+1)}/></RouteOutlet>
  </Shell>;
}
