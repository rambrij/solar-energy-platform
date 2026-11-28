import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {ApiError,vendorApi} from './api';
import {useSession} from './session';

type Warehouse={id:number;branchId:number;code:string;name:string;active:boolean;version:number};
type WarehouseStock={productId:number;sku:string;productName:string;onHand:number;reserved:number;damaged:number;available:number;version:number};
type Transfer={id:string;sourceType:'BRANCH'|'WAREHOUSE';sourceWarehouseId:number|null;sourceBranchId:number;destinationType:'BRANCH'|'WAREHOUSE';destinationWarehouseId:number|null;destinationBranchId:number;productId:number;quantity:number;receivedQuantity:number;status:string;reason:string;createdBy:string;approvedBy:string|null;dispatchedBy:string|null;completedBy:string|null;createdAt:string;dispatchedAt:string|null;completedAt:string|null;version:number};
type Detail={transfer:Transfer;events:{id:string;eventType:string;actorUsername:string;quantity:number|null;finalReceipt:boolean|null;detail:string;occurredAt:string}[]};

export default function WarehouseTransfers(){
 const {account}=useSession();
 const [warehouses,setWarehouses]=useState<Warehouse[]>([]),[transfers,setTransfers]=useState<Transfer[]>([]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [code,setCode]=useState(''),[name,setName]=useState(''),[warehouseReason,setWarehouseReason]=useState('');
 const [sourceType,setSourceType]=useState<'BRANCH'|'WAREHOUSE'>('BRANCH'),[sourceWarehouseId,setSourceWarehouseId]=useState('');
 const [destinationType,setDestinationType]=useState<'BRANCH'|'WAREHOUSE'>('BRANCH'),[destinationWarehouseId,setDestinationWarehouseId]=useState('');
 const [destinationBranchId,setDestinationBranchId]=useState(String(account?.branchId??'')),[productId,setProductId]=useState(''),[quantity,setQuantity]=useState('1'),[reason,setReason]=useState('');
 const [detail,setDetail]=useState<Detail>();
 const [stock,setStock]=useState<{warehouseId:number;items:WarehouseStock[]}>();
 const [receiveQuantity,setReceiveQuantity]=useState('1'),[finalReceipt,setFinalReceipt]=useState(true),[receiptReason,setReceiptReason]=useState('');
 const canView=!!account?.permissions.includes('INVENTORY_VIEW'),canTransfer=!!account?.permissions.includes('INVENTORY_TRANSFER');

 const load=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [w,t]=await Promise.all([vendorApi('/inventory/warehouses'),vendorApi('/inventory/transfers')]);
   setWarehouses(w);setTransfers(t);
  }catch(e){setError(e instanceof ApiError?e.message:'Warehouse and transfer data unavailable. Check permissions and retry.');}
  finally{setBusy(false);}
 },[]);
 useEffect(()=>{if(canView)void load();},[canView,load]);

 async function createWarehouse(event:FormEvent){
  event.preventDefault();setBusy(true);setError('');setNotice('');
  try{await vendorApi('/inventory/warehouses','POST',{code:code.trim(),name:name.trim(),reason:warehouseReason.trim()});setCode('');setName('');setWarehouseReason('');setNotice('Warehouse created.');await load();}
  catch(e){setError(e instanceof ApiError?e.message:'Warehouse could not be created.');}
  finally{setBusy(false);}
 }
 async function createTransfer(event:React.FormEvent){
  event.preventDefault();setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/inventory/transfers','POST',{
    sourceType,sourceWarehouseId:sourceType==='WAREHOUSE'?Number(sourceWarehouseId):null,
    destinationType,destinationWarehouseId:destinationType==='WAREHOUSE'?Number(destinationWarehouseId):null,
    destinationBranchId:Number(destinationBranchId),productId:Number(productId),quantity:Number(quantity),reason:reason.trim()
   },crypto.randomUUID());
   setNotice('Transfer created in DRAFT. A different authorized employee must approve it before dispatch.');setProductId('');setQuantity('1');setReason('');await load();
  }catch(e){setError(e instanceof ApiError?e.message:'Transfer could not be created.');}
  finally{setBusy(false);}
 }
 async function action(path:string,body:unknown){
  setBusy(true);setError('');setNotice('');
  try{await vendorApi(path,'POST',body);setNotice('Transfer updated.');setDetail(undefined);await load();}
  catch(e){setError(e instanceof ApiError?e.message:'Transfer action failed. Reload before retrying.');}
  finally{setBusy(false);}
 }
 async function openWarehouseStock(id:number){
  setBusy(true);setError('');
  try{const items=await vendorApi('/inventory/warehouses/'+id+'/stock');setStock({warehouseId:id,items});}
  catch(e){setError(e instanceof ApiError?e.message:'Warehouse stock unavailable.');}
  finally{setBusy(false);}
 }
 async function openDetail(id:string){
  setBusy(true);setError('');
  try{const result=await vendorApi('/inventory/transfers/'+encodeURIComponent(id));setDetail(result);setReceiveQuantity(String(Math.max(1,result.transfer.quantity-result.transfer.receivedQuantity)));setFinalReceipt(true);setReceiptReason('');}
  catch(e){setError(e instanceof ApiError?e.message:'Transfer details unavailable.');}
  finally{setBusy(false);}
 }
 if(!canView&&!canTransfer)return <section aria-label="Warehouse operations"><h2>Warehouses &amp; transfers</h2><p>INVENTORY_VIEW or INVENTORY_TRANSFER permission is required.</p></section>;
 return <section aria-label="Warehouse and transfer management" aria-busy={busy}>
  <header><h2>Warehouses &amp; stock transfers</h2><p>Manage tenant-owned warehouses and move stock through an auditable approval, dispatch and receipt workflow. Branch stock continues to use the existing POS inventory location.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {canTransfer&&<form onSubmit={createWarehouse}>
   <h3>Create warehouse for this branch</h3>
   <label>Warehouse code<input required maxLength={40} value={code} onChange={e=>setCode(e.target.value.toUpperCase())}/></label>
   <label>Warehouse name<input required maxLength={160} value={name} onChange={e=>setName(e.target.value)}/></label>
   <label>Reason<input required maxLength={500} value={warehouseReason} onChange={e=>setWarehouseReason(e.target.value)}/></label>
   <button disabled={busy||!code.trim()||!name.trim()||!warehouseReason.trim()}>Create warehouse</button>
  </form>}
  <h3>Warehouses in your assigned branch</h3>
  {warehouses.length===0?<p>No warehouses found for this branch.</p>:<ul>{warehouses.map(w=><li key={w.id}>{w.code} — {w.name} ({w.active?'Active':'Inactive'}) <button type="button" disabled={busy||!canView} onClick={()=>void openWarehouseStock(w.id)}>View stock</button></li>)}</ul>}
  {stock&&<section aria-label="Warehouse stock balances"><h3>Stock in warehouse #{stock.warehouseId}</h3>{stock.items.length===0?<p>No stock recorded in this warehouse.</p>:<table><thead><tr><th>Product</th><th>SKU</th><th>On hand</th><th>Reserved</th><th>Damaged</th><th>Available</th></tr></thead><tbody>{stock.items.map(item=><tr key={item.productId}><td>{item.productName}</td><td>{item.sku}</td><td>{item.onHand}</td><td>{item.reserved}</td><td>{item.damaged}</td><td>{item.available}</td></tr>)}</tbody></table>}</section>}
  {canTransfer&&<form onSubmit={createTransfer}>
   <h3>Create stock transfer</h3>
   <label>Source location<select value={sourceType} onChange={e=>setSourceType(e.target.value as 'BRANCH'|'WAREHOUSE')}><option value="BRANCH">Assigned branch POS stock</option><option value="WAREHOUSE">Warehouse</option></select></label>
   {sourceType==='WAREHOUSE'&&<label>Source warehouse<select required value={sourceWarehouseId} onChange={e=>setSourceWarehouseId(e.target.value)}><option value="">Choose source warehouse</option>{warehouses.filter(w=>w.active).map(w=><option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}</select></label>}
   <label>Destination location<select value={destinationType} onChange={e=>setDestinationType(e.target.value as 'BRANCH'|'WAREHOUSE')}><option value="BRANCH">Branch POS stock</option><option value="WAREHOUSE">Warehouse</option></select></label>
   <label>Destination branch ID<input required type="number" min="1" value={destinationBranchId} onChange={e=>setDestinationBranchId(e.target.value)}/></label>
   {destinationType==='WAREHOUSE'&&<label>Destination warehouse ID<input required type="number" min="1" value={destinationWarehouseId} onChange={e=>setDestinationWarehouseId(e.target.value)}/></label>}
   <label>Product ID<input required type="number" min="1" value={productId} onChange={e=>setProductId(e.target.value)}/></label>
   <label>Quantity<input required type="number" min="1" max="1000000" value={quantity} onChange={e=>setQuantity(e.target.value)}/></label>
   <label>Transfer reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
   <button disabled={busy||!destinationBranchId||!productId||Number(quantity)<1||!reason.trim()||(sourceType==='WAREHOUSE'&&!sourceWarehouseId)||(destinationType==='WAREHOUSE'&&!destinationWarehouseId)}>Create draft transfer</button>
  </form>}
  <h3>Transfers involving your assigned branch</h3>
  {transfers.length===0?<p>No transfers found.</p>:<ul>{transfers.map(t=><li key={t.id}>
   <strong>{t.status}</strong> — Product #{t.productId}, {t.receivedQuantity}/{t.quantity} received
   <button type="button" disabled={busy} onClick={()=>void openDetail(t.id)}>View transfer</button>
  </li>)}</ul>}
  {detail&&<article aria-label="Transfer detail">
   <h3>Transfer {detail.transfer.id}</h3>
   <p>{detail.transfer.status} — {detail.transfer.receivedQuantity}/{detail.transfer.quantity} units received</p>
   <p>Source: {detail.transfer.sourceType} {detail.transfer.sourceWarehouseId??detail.transfer.sourceBranchId} · Destination: {detail.transfer.destinationType} {detail.transfer.destinationWarehouseId??detail.transfer.destinationBranchId}</p>
   {canTransfer&&detail.transfer.status==='DRAFT'&&detail.transfer.sourceBranchId===account?.branchId&&detail.transfer.createdBy!==account?.username&&<button disabled={busy} onClick={()=>void action('/inventory/transfers/'+detail.transfer.id+'/approve',{expectedVersion:detail.transfer.version,reason:'Approved after independent stock and destination review'})}>Approve transfer</button>}
   {canTransfer&&detail.transfer.status==='APPROVED'&&detail.transfer.sourceBranchId===account?.branchId&&<button disabled={busy} onClick={()=>void action('/inventory/transfers/'+detail.transfer.id+'/dispatch',{expectedVersion:detail.transfer.version,reason:'Dispatched after source stock verification'})}>Dispatch transfer</button>}
   {canTransfer&&['IN_TRANSIT','PARTIALLY_RECEIVED'].includes(detail.transfer.status)&&detail.transfer.destinationBranchId===account?.branchId&&<form onSubmit={e=>{e.preventDefault();void action('/inventory/transfers/'+detail.transfer.id+'/receive',{expectedVersion:detail.transfer.version,quantity:Number(receiveQuantity),finalReceipt,commandKey:crypto.randomUUID(),reason:receiptReason.trim()});}}>
    <h4>Receive stock</h4>
    <label>Quantity received<input required type="number" min="1" max={detail.transfer.quantity-detail.transfer.receivedQuantity} value={receiveQuantity} onChange={e=>setReceiveQuantity(e.target.value)}/></label>
    <label><input type="checkbox" checked={finalReceipt} onChange={e=>setFinalReceipt(e.target.checked)}/> Final receipt (close transfer and record any shortage as a discrepancy)</label>
    <label>Receipt notes<input required maxLength={500} value={receiptReason} onChange={e=>setReceiptReason(e.target.value)}/></label>
    <button disabled={busy||Number(receiveQuantity)<1||!receiptReason.trim()}>Record receipt</button>
   </form>}
   <h4>Immutable transfer history</h4><ol>{detail.events.map(e=><li key={e.id}>{e.eventType} — {e.actorUsername} — {e.detail} — {new Date(e.occurredAt).toLocaleString()}</li>)}</ol>
   <button type="button" onClick={()=>setDetail(undefined)}>Close details</button>
  </article>}
 </section>;
}
