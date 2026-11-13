import { useEffect,useRef,useState } from 'react';
import { api,ApiError } from './api';
import CategoryNav,{type CategoryChoice} from './CategoryNav';
export type PosProduct={id:number;name:string;sku:string;barcode:string|null;price:number;tax_rate:number;quantity:number;list_price?:number;discount_percent?:number;category_id?:number|null;category_name?:string};
type Page={items:PosProduct[];page:number;size:number;totalElements:number;totalPages:number;catalogueTotal:number;categories:CategoryChoice[]};
type Props={locked:boolean;quantities:Record<number,number>;revision:number;onProducts:(items:PosProduct[])=>void;onAdd:(product:PosProduct)=>boolean;onBusyChange:(value:boolean)=>void};
export default function PosCatalogue({locked,quantities,revision,onProducts,onAdd,onBusyChange}:Props){
 const [search,setSearch]=useState(''),[query,setQuery]=useState(''),[category,setCategory]=useState('all'),[sort,setSort]=useState('name'),[stockOnly,setStockOnly]=useState(false),[page,setPage]=useState(0),[retry,setRetry]=useState(0);
 const [result,setResult]=useState<Page>(),[loaded,setLoaded]=useState(''),[error,setError]=useState(''),[notice,setNotice]=useState(''),[scanning,setScanning]=useState(false),[focusRequested,setFocusRequested]=useState(false);
 const input=useRef<HTMLInputElement>(null),alive=useRef(true);
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 useEffect(()=>{if(focusRequested&&!scanning){input.current?.focus();input.current?.select();setFocusRequested(false);}},[focusRequested,scanning]);
 useEffect(()=>{if(search.trim()===query)return;const timer=setTimeout(()=>{setQuery(search.trim());setPage(0);},250);return()=>clearTimeout(timer);},[search,query]);
 const key=JSON.stringify([query,category,sort,stockOnly,page,revision,retry]);
 const loading=loaded!==key||search.trim()!==query;
 useEffect(()=>{let active=true;setError('');if(search.trim()!==query)return()=>{active=false;};const params=new URLSearchParams({q:query,category,sort,inStock:String(stockOnly),page:String(page),size:'24'});
  api(`/catalogue?${params}`).then((value:Page)=>{if(active){setResult(value);setLoaded(key);onProducts(value.items);}}).catch(()=>{if(active){setLoaded(key);setError('POS catalogue unavailable. Check your permission or connection and retry.');}});return()=>{active=false;};
 },[key,query,category,sort,stockOnly,page,search,onProducts]);
 function add(product:PosProduct,scan=false){
  onProducts([product]);
  if((quantities[product.id]??0)>=Math.min(10000,product.quantity)){setNotice('Available stock or quantity limit reached.');return;}
  if(!onAdd(product)){setNotice('Sale is locked or the bill line limit was reached.');return;}
  setNotice(`Added ${product.name}`);if(scan){setSearch('');setCategory('all');setPage(0);setFocusRequested(true);}
 }
 async function scan(event:React.FormEvent){event.preventDefault();if(locked||scanning||!search.trim())return;setScanning(true);onBusyChange(true);setNotice('');
  try{const product:PosProduct=await api(`/catalogue/scan?${new URLSearchParams({code:search.trim()})}`);if(alive.current)add(product,true);}
  catch(e){if(alive.current)setNotice(e instanceof ApiError&&e.status===404?'No exact SKU or barcode match. Use product search and Add.':e instanceof ApiError&&e.status===409?'More than one barcode match. Select the correct product.':e instanceof ApiError&&e.status===400?'Enter a SKU or barcode up to 80 characters, or use product search and Add.':'Scan lookup was not confirmed. Retry the scan; no item was added.');}
  finally{if(alive.current){setScanning(false);setFocusRequested(true);onBusyChange(false);}}
 }
 return <section id="checkout" className="catalogue-panel" aria-label="POS catalogue" aria-busy={loading||scanning}><div className="section-heading"><h2>Solar essentials</h2><span className="badge">{result?.catalogueTotal??0} products</span></div>
 <form className="barcode-search" onSubmit={scan}><label>Search name, SKU or scan barcode <input ref={input} autoComplete="off" maxLength={120} disabled={scanning} value={search} placeholder="Scan barcode or enter SKU, then press Enter" onChange={e=>setSearch(e.target.value)}/></label><button disabled={locked||scanning||!search.trim()}>{scanning?'Checking code...':'Add exact match'}</button></form>{notice&&<p role="status" className="scan-notice">{notice}</p>}
 <CategoryNav categories={result?.categories??[]} selected={category} onSelect={id=>{setCategory(id);setPage(0);}} total={result?.catalogueTotal??0}/>
 <div className="catalogue-toolbar"><label>POS sort products<select value={sort} onChange={e=>{setSort(e.target.value);setPage(0);}}><option value="name">Name A-Z</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option></select></label><label className="check-label"><input type="checkbox" checked={stockOnly} onChange={e=>{setStockOnly(e.target.checked);setPage(0);}}/>POS in stock only</label></div>
 {loading?<p role="status">Searching assigned branch...</p>:error?<p role="alert">{error} <button onClick={()=>setRetry(value=>value+1)}>Retry POS catalogue</button></p>:<><p role="status">{result?.totalElements??0} products found</p>{!result?.items.length&&<div className="empty-state"><p>No products match. Try another category or search.</p></div>}<ul className="product-grid">{result?.items.map(product=><li key={product.id} className="product-card"><div className="product-art" aria-hidden="true"><span className="panel-illustration"/></div><span className="eyebrow">{product.sku}</span><h3>{product.name}</h3><p className="product-price">{Number(product.list_price??product.price)>Number(product.price)&&<del>{Number(product.list_price).toFixed(2)} </del>}{Number(product.price).toFixed(2)} <small>+ {product.tax_rate}% tax</small></p><span className={`stock-label ${product.quantity<1?'sold-out':''}`}>{product.quantity} available</span><button disabled={locked||scanning||(quantities[product.id]??0)>=Math.min(10000,product.quantity)} onClick={()=>add(product)}>Add {product.name}</button></li>)}</ul>
 <nav className="catalogue-pagination" aria-label="POS product pages"><button className="secondary" disabled={scanning||page===0} onClick={()=>setPage(value=>Math.max(0,value-1))}>Previous POS products</button><span>Page {page+1} of {Math.max(1,result?.totalPages??0)}</span><button disabled={scanning||page>=(result?.totalPages??0)-1} onClick={()=>setPage(value=>value+1)}>Next POS products</button>{page>0&&<button className="secondary" onClick={()=>setPage(0)}>First POS product page</button>}</nav></>}
 </section>;
}
