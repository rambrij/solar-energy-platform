import {useEffect,useState} from 'react';
import {vendorApi,vendorUpload} from './api';
import {authenticatedFetch,useSession} from './session';

type Product={id:number;sku:string;name:string;published:boolean;price:number|null;taxRate:number|null};
type PackageItem={productId:number;sku:string;name:string;quantity:number;unitPrice:number|null;taxRate:number|null};
type SolarPackage={id:number;sku:string;name:string;description:string|null;systemCapacityKw:number;packagePrice:number|null;taxRate:number|null;published:boolean;version:number;items:PackageItem[]};
type Image={id:string;contentType:string;byteSize:number;sha256:string;altText:string;createdAt:string;url:string};
type Page<T>={items:T[];page:number;totalPages:number;totalElements:number};
const empty={sku:'',name:'',description:'',capacity:'1.000',price:'0.00',tax:'0.00',published:false,reason:''};

function VendorImagePreview({productId,image}: {productId:string;image:Image}) {
 const [preview,setPreview]=useState('');
 const [failed,setFailed]=useState(false);
 useEffect(()=>{
  const controller=new AbortController();
  let objectUrl:string|undefined;
  (async()=>{
   try {
    const response=await authenticatedFetch(`/api/v1/vendor/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(image.id)}`,{
     method:'GET',cache:'no-store',signal:controller.signal
    });
    if(!response.ok)throw new Error('Image preview could not be loaded.');
    const blob=await response.blob();
    if(controller.signal.aborted)return;
    objectUrl=URL.createObjectURL(blob);
    setPreview(objectUrl);
   } catch {
    if(!controller.signal.aborted)setFailed(true);
   }
  })();
  return ()=>{
   controller.abort();
   if(objectUrl)URL.revokeObjectURL(objectUrl);
  };
 },[productId,image.id]);
 if(failed)return <span>Preview unavailable{image.altText?': '+image.altText:''}</span>;
 if(!preview)return <span role="status">Loading image preview…</span>;
 return <img src={preview} alt={image.altText} width="112"/>;
}

export default function SolarPackageManager(){
 const {account}=useSession();
 const has=(permission:string)=>!!account?.permissions.includes(permission);
 const [products,setProducts]=useState<Product[]>([]),[packages,setPackages]=useState<SolarPackage[]>([]);
 const [selected,setSelected]=useState<SolarPackage>(),[form,setForm]=useState(empty),[quantities,setQuantities]=useState<Record<number,string>>({});
 const [imageProduct,setImageProduct]=useState(''),[images,setImages]=useState<Image[]>([]),[file,setFile]=useState<File>(),[alt,setAlt]=useState(''),[imageReason,setImageReason]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const canView=has('PRODUCT_VIEW'),canCreate=has('PRODUCT_CREATE')&&has('PRICE_UPDATE'),canUpdate=has('PRODUCT_UPDATE');
 useEffect(()=>{if(canView)void load();},[canView]);
 async function load(){
  setBusy(true);setError('');
  try{
   const [productPage,packagePage]=await Promise.all([vendorApi('/products?page=0&size=100'),vendorApi('/packages?page=0&size=100')]);
   setProducts((productPage as Page<Product>).items);setPackages((packagePage as Page<SolarPackage>).items);
  }catch(e){setError((e as Error).message||'Catalogue data could not be loaded.');}
  finally{setBusy(false);}
 }
 function choose(pkg?:SolarPackage){
  setSelected(pkg);
  setForm(pkg?{sku:pkg.sku,name:pkg.name,description:pkg.description??'',capacity:String(pkg.systemCapacityKw),price:pkg.packagePrice==null?'':String(pkg.packagePrice),tax:pkg.taxRate==null?'':String(pkg.taxRate),published:pkg.published,reason:''}:empty);
  setQuantities(pkg?Object.fromEntries(pkg.items.map(item=>[item.productId,String(item.quantity)])):{});
 }
 async function save(){
  if(!selected&&!canCreate)return;
  if(selected&&!canUpdate)return;
  const items=Object.entries(quantities).filter(([,q])=>Number(q)>0).map(([id,q])=>({productId:Number(id),quantity:Number(q)}));
  if(!items.length){setError('Select at least one product and quantity.');return;}
  const body={sku:form.sku.trim(),name:form.name.trim(),description:form.description.trim()||null,
   systemCapacityKw:form.capacity,packagePrice:form.price,taxRate:form.tax,published:form.published,
   expectedVersion:selected?.version??0,reason:form.reason.trim(),items};
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(selected?'/packages/'+selected.id:'/packages',selected?'PUT':'POST',body);
   setNotice(selected?'Solar package updated.':'Solar package created.');choose();await load();
  }catch(e){setError((e as Error).message||'Solar package could not be saved.');}
  finally{setBusy(false);}
 }
 async function loadImages(productId:string){
  setImageProduct(productId);setImages([]);
  if(!productId)return;
  setBusy(true);setError('');
  try{setImages(await vendorApi(`/products/${encodeURIComponent(productId)}/images`));}
  catch(e){setError((e as Error).message||'Product images could not be loaded.');}
  finally{setBusy(false);}
 }
 async function uploadImage(){
  if(!canUpdate||!imageProduct||!file||!imageReason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorUpload(`/products/${encodeURIComponent(imageProduct)}/images`,file,alt,imageReason.trim());
   setNotice('Product image uploaded.');setFile(undefined);setAlt('');setImageReason('');
   await loadImages(imageProduct);
  }catch(e){setError((e as Error).message||'Product image upload failed.');}
  finally{setBusy(false);}
 }
 async function deactivateImage(imageId:string){
  if(!canUpdate||!imageProduct)return;
  setBusy(true);setError('');
  try{
   await vendorApi(`/products/${encodeURIComponent(imageProduct)}/images/${encodeURIComponent(imageId)}`,'DELETE',{reason:'Removed from the vendor product gallery'});
   setImages(current=>current.filter(image=>image.id!==imageId));setNotice('Image removed from the active gallery.');
  }catch(e){setError((e as Error).message||'Image could not be removed.');}
  finally{setBusy(false);}
 }
 if(!canView&&!canCreate&&!canUpdate)return <section aria-label="Solar package management"><h2>Solar packages & product images</h2><p>Catalogue permissions are required to manage solar packages or product images.</p></section>;
 return <section id="solar-package-management" aria-label="Solar package and product image management" aria-busy={busy}>
  <h2>Solar packages & product images</h2>
  <p>Build vendor-owned solar system packages from products in this tenant catalogue. Product images are stored in tenant-scoped object storage.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <div className="pagination-controls"><button type="button" disabled={busy||!canView} onClick={()=>void load()}>Refresh packages and products</button><button type="button" disabled={busy||!canCreate} onClick={()=>choose()}>New solar package</button></div>
  <h3>Existing packages</h3>
  {!packages.length&&<p>No solar packages have been created yet.</p>}
  <ul>{packages.map(pkg=><li key={pkg.id}><strong>{pkg.name}</strong> — {pkg.sku} — {pkg.systemCapacityKw} kW — {pkg.published?'Published':'Unpublished'} {pkg.packagePrice!=null?'— ₹'+pkg.packagePrice:''}
   <button type="button" disabled={busy||!canUpdate} onClick={()=>choose(pkg)}>Edit package {pkg.name}</button></li>)}</ul>
  <form onSubmit={event=>{event.preventDefault();void save();}}>
   <h3>{selected?'Edit solar package':'Create solar package'}</h3>
   <fieldset disabled={busy||(!selected&&!canCreate)||(!!selected&&!canUpdate)}>
    <label>Package name<input required maxLength={160} value={form.name} onChange={e=>setForm(v=>({...v,name:e.target.value}))}/></label>
    <label>Package SKU<input required maxLength={80} value={form.sku} onChange={e=>setForm(v=>({...v,sku:e.target.value}))}/></label>
    <label>System capacity (kW)<input required type="number" min="0.001" step="0.001" value={form.capacity} onChange={e=>setForm(v=>({...v,capacity:e.target.value}))}/></label>
    <label>Package price before tax (INR)<input required type="number" min="0" step="0.01" disabled={!has('PRICE_UPDATE')} value={form.price} onChange={e=>setForm(v=>({...v,price:e.target.value}))}/></label>
    <label>Tax rate (%)<input required type="number" min="0" max="100" step="0.01" disabled={!has('PRICE_UPDATE')} value={form.tax} onChange={e=>setForm(v=>({...v,tax:e.target.value}))}/></label>
    <label>Package description<textarea maxLength={2000} value={form.description} onChange={e=>setForm(v=>({...v,description:e.target.value}))}/></label>
    <fieldset><legend>Package components</legend>
     {products.map(product=><label key={product.id}>{product.name} ({product.sku}) <input aria-label={'Quantity '+product.name} type="number" min="0" max="10000" value={quantities[product.id]??''} onChange={e=>setQuantities(v=>({...v,[product.id]:e.target.value}))}/></label>)}
    </fieldset>
    <label><input type="checkbox" checked={form.published} onChange={e=>setForm(v=>({...v,published:e.target.checked}))}/>Publish package in the vendor storefront</label>
    <label>Change reason<input required maxLength={250} value={form.reason} onChange={e=>setForm(v=>({...v,reason:e.target.value}))}/></label>
    <button disabled={busy||!form.sku.trim()||!form.name.trim()||!form.reason.trim()||!Object.values(quantities).some(q=>Number(q)>0)}>Save solar package</button>
   </fieldset>
   {selected&&<button type="button" className="secondary" disabled={busy} onClick={()=>choose()}>Cancel package edit</button>}
  </form>
  <hr/>
  <h3>Product image gallery</h3>
  <label>Select product<select disabled={busy} value={imageProduct} onChange={e=>void loadImages(e.target.value)}><option value="">Choose a product</option>{products.map(product=><option key={product.id} value={product.id}>{product.name} ({product.sku})</option>)}</select></label>
  {imageProduct&&<><ul>{images.map(image=><li key={image.id}><VendorImagePreview productId={imageProduct} image={image}/><span>{image.contentType} · {Math.round(image.byteSize/1024)} KB</span>{canUpdate&&<button type="button" disabled={busy} onClick={()=>void deactivateImage(image.id)}>Remove image</button>}</li>)}</ul>
   {canUpdate&&<form onSubmit={event=>{event.preventDefault();void uploadImage();}}>
    <label>Product image (PNG, JPEG or WebP, max 5 MB)<input type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>setFile(e.target.files?.[0])}/></label>
    <label>Image description<input maxLength={180} value={alt} onChange={e=>setAlt(e.target.value)}/></label>
    <label>Upload reason<input required maxLength={250} value={imageReason} onChange={e=>setImageReason(e.target.value)}/></label>
    <button disabled={busy||!canUpdate||!file||!imageReason.trim()}>Upload product image</button>
   </form>}
  </>}
 </section>;
}
