import {useEffect,useRef,useState} from 'react';
import {ApiError,vendorApi} from './api';
import {useSession} from './session';
type Product={id:number;name:string;sku:string;published:boolean};
type Page<T>={items:T[];page:number;totalPages:number};
type Receipt={id:string;productId:number;quantity:number;availableAfter:number;actor:string;reason:string;createdAt:string};
type Command={key:string;body:{productId:number;quantity:number;reason:string}};
export default function StockReceiving({onChanged}:{onChanged?:()=>void}){
 const {account}=useSession(),alive=useRef(true);const storageKey=`stock-receiving:${account?.username}:${account?.tenantId}:${account?.branchId}`;
 const [products,setProducts]=useState<Page<Product>>(),[history,setHistory]=useState<Page<Receipt>>(),[receipt,setReceipt]=useState<Receipt>(),[product,setProduct]=useState(''),[quantity,setQuantity]=useState('1'),[reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const [historyProduct,setHistoryProduct]=useState(''),[appliedProduct,setAppliedProduct]=useState('');
 const [pending,setPending]=useState<Command|undefined>(()=>{try{return JSON.parse(sessionStorage.getItem(storageKey)??'null')??undefined;}catch{return undefined;}});
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 async function load(kind:'products'|'history',page=0,productFilter=appliedProduct){setBusy(true);setError('');try{const value=await vendorApi(kind==='products'?`/products?page=${page}&size=20`:`/inventory/receipts?page=${page}&size=20${productFilter?`&productId=${encodeURIComponent(productFilter)}`: ''}`);if(alive.current){if(kind==='products')setProducts(value);else{setHistory(value);setAppliedProduct(productFilter);}}}catch{if(alive.current)setError('Stock receiving data unavailable. Check permissions and retry.');}finally{if(alive.current)setBusy(false);}}
 async function submit(command:Command){if(!canReceive){setError('INVENTORY_RECEIVE permission is required to submit or retry a stock receipt.');return;}try{sessionStorage.setItem(storageKey,JSON.stringify(command));}catch{setError('Cannot save retry details. No stock request was submitted.');return;}
  const retry=!!pending;setPending(command);setBusy(true);setError('');try{const value=await vendorApi('/inventory/receipts','POST',command.body,command.key);if(!alive.current)return;sessionStorage.removeItem(storageKey);setPending(undefined);setReceipt(value);setReason('');onChanged?.();}
  catch(e){if(alive.current){if(!retry&&e instanceof ApiError&&[400,403,404,409].includes(e.status)){sessionStorage.removeItem(storageKey);setPending(undefined);setError(e.message+' Stock request rejected; check the original command before trying again.');}else setError('Stock receiving outcome is uncertain. Retry the same saved command; do not submit the delivery again.');}}finally{if(alive.current)setBusy(false);}
 }
 const canReceive=!!account?.permissions.includes('INVENTORY_RECEIVE'),canBrowse=!!account?.permissions.includes('PRODUCT_VIEW'),canView=!!account?.permissions.includes('INVENTORY_VIEW');
 return <section id="stock-receiving" aria-label="Stock receiving" aria-busy={busy}><h2>Receive stock</h2><p>Record a physical delivery into your assigned outlet. Available stock updates for store and marketplace sales at that location.</p>{error&&<p role="alert">{error}</p>}
 {receipt&&<p role="status">Stock receipt {receipt.id}: {receipt.quantity} received. Available immediately after this receipt: {receipt.availableAfter}. This saved figure is not a live stock count.</p>}
 {canBrowse&&<button disabled={busy} onClick={()=>void load('products')}>Load products for receiving</button>}{canBrowse&&products&&<div className="pagination-controls"><button disabled={busy||products.page===0} onClick={()=>void load('products',products.page-1)}>Previous receiving products</button><button disabled={busy||products.page+1>=products.totalPages} onClick={()=>void load('products',products.page+1)}>Next receiving products</button></div>}
 {pending&&<div><p>A saved stock receipt is awaiting acknowledgement.</p>{canReceive&&<button disabled={busy} onClick={()=>void submit(pending)}>Retry same stock receipt</button>}</div>}
 {canReceive&&canBrowse&&<form onSubmit={e=>{e.preventDefault();void submit({key:crypto.randomUUID(),body:{productId:Number(product),quantity:Number(quantity),reason:reason.trim()}});}}><fieldset disabled={busy||!!pending||!canReceive}>
 <label>Receiving product<select required value={product} onChange={e=>setProduct(e.target.value)}><option value="">Choose a product</option>{product&&!products?.items.some(p=>String(p.id)===product)&&<option value={product}>Selected product from another page</option>}{products?.items.map(p=><option key={p.id} value={p.id}>{p.name} ({p.sku}){p.published?'':' - unpublished'}</option>)}</select></label><label>Quantity received<input required type="number" min="1" max="1000000" step="1" value={quantity} onChange={e=>setQuantity(e.target.value)}/></label><label>Stock receiving reason<input required maxLength={250} value={reason} onChange={e=>setReason(e.target.value)}/></label><button disabled={!product||!Number.isInteger(Number(quantity))||Number(quantity)<1||Number(quantity)>1000000||!reason.trim()}>Record stock receipt</button></fieldset></form>}{canReceive&&!canBrowse&&<p role="status">Stock receipt submission is permitted, but selecting a product requires PRODUCT_VIEW. Retry an already-saved receipt below, or ask an administrator for product catalogue viewing access.</p>}
 {canView&&<>
  <form onSubmit={event=>{event.preventDefault();void load('history',0,historyProduct);}}>
   <label>Filter stock receipt history by product ID<input inputMode="numeric" pattern="[1-9][0-9]{0,18}"
    value={historyProduct} onChange={event=>setHistoryProduct(event.target.value)}/></label>
   <button disabled={busy}>Load stock receipt history</button>
  </form>
  {history&&<>
   {appliedProduct&&<p>Showing receipts for product #{appliedProduct}.</p>}
   {history.items.length===0?<p>No stock receipts found for this outlet and filter.</p>:
    <ul>{history.items.map(row=><li key={row.id}>Product #{row.productId} | {row.quantity} received by {row.actor} on {new Date(row.createdAt).toLocaleString()} | {row.reason} | Receipt {row.id}</li>)}</ul>}
   <div className="pagination-controls"><button disabled={busy||history.page===0} onClick={()=>void load('history',history.page-1)}>Previous stock receipts</button><button disabled={busy||history.page+1>=history.totalPages} onClick={()=>void load('history',history.page+1)}>Next stock receipts</button></div></>}</>}
 </section>;
}
