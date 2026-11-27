import {useCallback,useEffect,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';

type Warranty={id:number;orderId:string;productId:number;productName:string;installationId:number|null;coverageStartDate:string;coverageEndDate:string;termsSummary:string;registeredBy:string;registeredAt:string};
type CustomerWarranty={warranty:Warranty;vendorName:string};

export default function CustomerWarranties(){
 const [rows,setRows]=useState<CustomerWarranty[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState('');
 const refresh=useCallback(async()=>{
  setLoading(true);setError('');
  try{setRows(await marketplaceApi('/api/v1/customer/warranties') as CustomerWarranty[]);}
  catch(e){setError((e as Error).message||'Your warranty details could not be loaded.');}
  finally{setLoading(false);}
 },[]);
 useEffect(()=>{void refresh();},[refresh]);
 return <section className="customer-warranties" aria-labelledby="customer-warranties-title" aria-busy={loading}>
  <header><span className="eyebrow">My purchases</span><h2 id="customer-warranties-title">My warranties</h2>
   <p>View warranty coverage registered by vendors for your paid online purchases.</p></header>
  {error&&<p role="alert">{error}</p>}{loading&&<p role="status">Loading warranty coverage…</p>}
  {!loading&&rows.length===0&&!error&&<p>No warranty coverage has been registered for your eligible purchases yet.</p>}
  {rows.length>0&&<ul>{rows.map(({warranty:w,vendorName})=><li key={w.id}>
   <h3>{w.productName}</h3><p>Warranty #{w.id} · Order {w.orderId}</p><p>Vendor: {vendorName}</p>
   <p><strong>Coverage:</strong> {w.coverageStartDate} to {w.coverageEndDate}</p>
   <p><strong>Terms:</strong> {w.termsSummary}</p>
   {w.installationId&&<p>Linked installation #{w.installationId}</p>}
  </li>)}</ul>}
  <button type="button" className="secondary" disabled={loading} onClick={()=>void refresh()}>Refresh warranty status</button>
 </section>;
}
