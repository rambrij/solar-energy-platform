import { useEffect, useState } from 'react';
import { marketplaceApi, MarketplaceError } from './marketplaceApi';
import ReservedDrafts from './ReservedDrafts';
import Shell from './Shell';
import OrderHistory from './OrderHistory';
import ProductDetails from './ProductDetails';
import VendorDirectory from './VendorDirectory';
import CategoryNav, { type CategoryChoice } from './CategoryNav';

type Product = { id: number; sku: string; name: string; price: number; taxRate: number; inStock: boolean; listPrice?: number; discountPercent?: number; categoryId?: number | null; categoryName?: string };
type Catalogue = {items:Product[];page:number;size:number;totalElements:number;totalPages:number;catalogueTotal:number;categories:CategoryChoice[];vendorName?:string;vendorSlug?:string};
type Line = { productId: number; name: string; quantity: number; unitPrice: number | null; tax: number | null; total: number | null; available: boolean; listPrice?: number | null; discount?: number | null };
type Cart = { id: string | null; vendorSlug: string | null; vendorName: string | null; items: Line[]; total: number; checkoutAvailable: boolean; message: string };
type Target = { vendorSlug: string; productId: number; quantity: number };
const money = (amount: number | null) => amount === null ? 'Unavailable' : Number(amount).toFixed(2);

function readProductId() { const value = new URLSearchParams(window.location.search).get('product'); const id = Number(value); return value && /^[1-9][0-9]*$/.test(value) && Number.isSafeInteger(id) ? id : undefined; }

export default function Marketplace() {
  const slug = window.location.pathname.startsWith('/vendors/') ? decodeURIComponent(window.location.pathname.split('/')[2]) : '';
  const [products, setProducts] = useState<Product[]>([]);
  const [review, setReview] = useState<Cart>();
  const [cart, setCart] = useState<Cart>(), [quantities, setQuantities] = useState<Record<number, number>>({});
  const [search, setSearch] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [switchTarget, setSwitchTarget] = useState<Target>();
  const [history, setHistory] = useState<Cart[]>(), [restoreTarget, setRestoreTarget] = useState<Cart>();
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [cartEdits,setCartEdits]=useState<Record<number,string>>({});
  const [stockRevision,setStockRevision] = useState(0);
  const [page,setPage]=useState(0),[query,setQuery]=useState(''),[catalogue,setCatalogue]=useState<Catalogue>(),[catalogueLoading,setCatalogueLoading]=useState(true),[catalogueError,setCatalogueError]=useState(''),[loadedKey,setLoadedKey]=useState('');
  const searchPending=search.trim()!==query;
  useEffect(()=>{if(search.trim()===query)return;const timer=setTimeout(()=>{setQuery(search.trim());setPage(0);},250);return()=>clearTimeout(timer);},[search,query]);
  const [sort, setSort] = useState('name'), [inStockOnly, setInStockOnly] = useState(false);
  const requestKey=JSON.stringify([slug,query,selectedCategory,sort,inStockOnly,page,stockRevision]);
  const catalogueBusy=catalogueLoading||searchPending||loadedKey!==requestKey;
  const visibleProducts = catalogueBusy?[]:products;
  const [detailId, setDetailId] = useState<number | undefined>(readProductId);
  useEffect(() => { const sync = () => setDetailId(readProductId()); window.addEventListener('popstate', sync); return () => window.removeEventListener('popstate', sync); }, []);
  function openDetails(id: number) { const url = new URL(window.location.href); url.searchParams.set('product', String(id)); window.history.pushState({}, '', url); setDetailId(id); }
  function closeDetails() { const url = new URL(window.location.href); url.searchParams.delete('product'); window.history.replaceState({}, '', url); setDetailId(undefined); }
  const categories=catalogue?.categories??[];
  function categoryChanged(value:string){setSelectedCategory(value);setPage(0);}
  const confirming = !!switchTarget || !!restoreTarget;
  useEffect(()=>{
    let active=true;
    setProducts([]);setCatalogueLoading(true);setCatalogueError('');
    if(!slug||searchPending)return()=>{active=false;};
    const params=new URLSearchParams({q:query,category:selectedCategory,sort,inStock:String(inStockOnly),page:String(page),size:'24'});
    marketplaceApi(`/api/marketplace/vendors/${encodeURIComponent(slug)}/catalogue?${params}`).then((value:Catalogue)=>{if(active){setCatalogue(value);setProducts(value.items);setLoadedKey(requestKey);setCatalogueLoading(false);}}).catch(()=>{if(active){setLoadedKey(requestKey);setCatalogueLoading(false);setCatalogueError('Catalogue unavailable. Check your connection and retry the catalogue.');}});
    return()=>{active=false;};
  },[slug,query,selectedCategory,sort,inStockOnly,page,stockRevision,searchPending,requestKey]);
  function failure(e: unknown) {
    setError(e instanceof MarketplaceError && [401,403].includes(e.status)
      ? 'Sign in with your global customer account to manage your cart. Staff credentials are separate.'
      : 'Request was not confirmed. Reload your cart before retrying. No payment has been taken.');
  }
  useEffect(()=>{setCartEdits({});},[cart?.id]);
  const cartQuantity=(line:Line)=>Number(cartEdits[line.productId]??line.quantity);
  const validCartQuantity=(line:Line)=>Number.isInteger(cartQuantity(line))&&cartQuantity(line)>=1&&cartQuantity(line)<=10000;
  async function loadCart() {
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi('/api/customer/cart'));setCartEdits({}); } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function put(product: Pick<Product, 'id'>, selectedQuantity?: number) {
    const target = { vendorSlug: slug, productId: product.id, quantity: selectedQuantity ?? quantities[product.id] ?? 1 };
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi('/api/customer/cart/items', 'POST', target)); }
    catch (e) {
      if (e instanceof MarketplaceError && e.status === 409) {
        try { const current: Cart = await marketplaceApi('/api/customer/cart'); setCart(current);
          if (current.id && current.vendorSlug !== slug) setSwitchTarget(target); else setError('Cart changed. Review its current contents and try again.');
        } catch (loadError) { failure(loadError); }
      } else failure(e);
    } finally { setBusy(false); }
  }
  async function updateCartQuantity(productId:number,quantity:number) {
    if(!cart?.vendorSlug||!Number.isInteger(quantity)||quantity<1||quantity>10000)return;
    const vendorSlug=cart.vendorSlug;
    setReview(undefined); setBusy(true);setError('');setNotice('');
    try{setCart(await marketplaceApi('/api/customer/cart/items','POST',{vendorSlug,productId,quantity}));setCartEdits(value=>{const next={...value};delete next[productId];return next;});}
    catch(e){failure(e);}finally{setBusy(false);}
  }
  async function confirmSwitch() {
    if (!cart?.id || !switchTarget) return;
    setReview(undefined); setBusy(true); setError('');
    try { setCart(await marketplaceApi('/api/customer/cart/switch', 'POST', { expectedCartId: cart.id, ...switchTarget })); setSwitchTarget(undefined); }
    catch (e) { setSwitchTarget(undefined); failure(e); } finally { setBusy(false); }
  }
  async function remove(id: number) {
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi(`/api/customer/cart/items/${id}`, 'DELETE')); } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function preview() {
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try { const quote: Cart = await marketplaceApi('/api/customer/cart/quote', 'POST', {}); setCart(quote); setReview(quote); setCartEdits({}); setNotice(quote.message); }
    catch (e) { if (e instanceof MarketplaceError && e.status === 409) setError('A cart item is unavailable or the cart is empty. Reload it to check stock and prices.'); else failure(e); }
    finally { setBusy(false); }
  }
  async function loadHistory() {
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try {
      const [current, archived] = await Promise.all([marketplaceApi('/api/customer/cart'), marketplaceApi('/api/customer/cart/history')]);
      setCart(current); setHistory(archived);
    } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function restore() {
    if (!restoreTarget?.id) return;
    setReview(undefined); setBusy(true); setError(''); setNotice('');
    try {
      setCart(await marketplaceApi('/api/customer/cart/restore', 'POST', { archivedCartId: restoreTarget.id, expectedCartId: cart?.id ?? null }));
      setHistory(undefined); setRestoreTarget(undefined); setNotice('Archived cart restored with current prices. Stock is not reserved.');
    } catch (e) {
      setRestoreTarget(undefined);
      if (e instanceof MarketplaceError && [404,409].includes(e.status)) setError('The cart changed or archived products are unavailable. Reload your cart history before trying again.');
      else failure(e);
    } finally { setBusy(false); }
  }
  return <Shell mode="online">
    <header className="workspace-hero marketplace-hero grocery-hero"><div><span className="eyebrow">A brighter way to shop</span><h1>Saumya Solar Marketplace</h1><p>Choose a vendor. Your customer account works across vendors; each cart belongs to one vendor.</p><a className="hero-link" href="#vendor-directory">Explore solar vendors <span aria-hidden="true">↗</span></a></div><div className="solar-scene" aria-hidden="true"><span className="scene-sun"/><span className="scene-orbit"/><span className="scene-panels"/></div></header>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    <VendorDirectory selectedSlug={slug}/>
    <ol className="shopping-steps" aria-label="Shopping steps"><li><strong>1</strong> Choose your solar essentials</li><li><strong>2</strong> Review one vendor cart</li><li><strong>3</strong> Reserve at checkout</li></ol><div className="marketplace-grid grocery-workspace"><div className="storefront-column">{slug && <section className="catalogue-panel" aria-label="Vendor products" aria-busy={catalogueBusy}><h2>{catalogue?.vendorSlug===slug?catalogue.vendorName:'Vendor storefront'}</h2>
      <div className="market-search"><label>Search products <input placeholder="Search panels, inverters, batteries and more" maxLength={120} value={search} onChange={e => setSearch(e.target.value)} /></label></div>
      <div className="retail-catalogue-layout"><CategoryNav categories={categories} selected={selectedCategory} onSelect={categoryChanged} total={catalogue?.catalogueTotal??0}/><div className="catalogue-results"><div className="catalogue-toolbar"><label>Sort products<select value={sort} onChange={event=>{setSort(event.target.value);setPage(0);}}><option value="name">Name A–Z</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option></select></label><label className="check-label"><input type="checkbox" checked={inStockOnly} onChange={event=>{setInStockOnly(event.target.checked);setPage(0);}}/> In stock only</label><span role="status">{catalogueBusy?'Searching catalogue...':catalogueError?'Results unavailable':`${catalogue?.totalElements??0} products found`}</span></div>
      {catalogueBusy&&<p role="status">Loading products...</p>}{!catalogueBusy&&catalogueError&&<p role="alert">{catalogueError} <button onClick={()=>setStockRevision(value=>value+1)}>Retry catalogue</button></p>}
      {!catalogueBusy&&!catalogueError&&!visibleProducts.length && <div className="empty-state"><h3>No matching products</h3><p>Try another search or turn off the stock filter.</p></div>}
      <ul className="product-grid">{visibleProducts.map(product => {
        const quantity = quantities[product.id] ?? 1;
        return <li key={product.id} className="product-card"><div className="product-art" aria-hidden="true"><span className="panel-illustration"/></div><h3>{product.name}</h3><p>{product.sku} · {Number(product.listPrice??product.price)>Number(product.price)&&<><del>{money(product.listPrice??null)}</del> </>}{money(product.price)} + {product.taxRate}% tax · {product.inStock ? 'In stock' : 'Out of stock'}</p>
          {Number(product.discountPercent??0)>0&&<span className="offer-badge">{product.discountPercent}% approved offer</span>}<button className="secondary" disabled={busy || confirming} onClick={() => openDetails(product.id)}>View details for {product.name}</button>
          <label>Quantity for {product.name} <input type="number" min="1" max="10000" value={quantity} onChange={e => setQuantities({ ...quantities, [product.id]: Number(e.target.value) })} /></label>
          <button disabled={busy || confirming || !product.inStock || !Number.isInteger(quantity) || quantity < 1 || quantity > 10000} onClick={() => put(product)}>Add or update {product.name} in cart</button>
        </li>;
      })}</ul>{!catalogueBusy&&!catalogueError&&catalogue&&<nav className="catalogue-pagination" aria-label="Catalogue pages"><button className="secondary" disabled={busy||confirming||page===0} onClick={()=>setPage(value=>Math.max(0,value-1))}>Previous products</button><span>Page {page+1} of {Math.max(1,catalogue.totalPages)}</span><button disabled={busy||confirming||page>=catalogue.totalPages-1} onClick={()=>setPage(value=>value+1)}>Next products</button>{page>0&&<button className="secondary" disabled={busy||confirming} onClick={()=>setPage(0)}>First product page</button>}</nav>}</div></div>
    </section>}
    </div><aside className="cart-column"><section className="customer-cart" aria-label="Customer cart"><h2>My single-vendor cart</h2>
      <button disabled={busy || confirming} onClick={loadCart}>Sign in / reload my cart</button>
      {cart && <><h3>{cart.vendorName ?? 'Your cart is empty'}</h3><ul>{cart.items.map(line => <li key={line.productId}>{line.name} × {line.quantity} · Unit price {money(line.unitPrice)} · Tax {money(line.tax)} · Total {money(line.total)} {Number(line.discount??0)>0&&<span className="saving-label"> · Saved {money(line.discount??null)}</span>} {!line.available && '· Unavailable for checkout'} <div className="cart-quantity-editor"><button disabled={busy||confirming||line.unitPrice===null||!validCartQuantity(line)||cartQuantity(line)<=1} aria-label={`Decrease cart ${line.name}`} onClick={()=>updateCartQuantity(line.productId,cartQuantity(line)-1)}>−</button><label>Cart quantity for {line.name}<input type="number" min="1" max="10000" disabled={busy||confirming||line.unitPrice===null} value={cartEdits[line.productId]??String(line.quantity)} onChange={event=>setCartEdits(value=>({...value,[line.productId]:event.target.value}))}/></label><button disabled={busy||confirming||line.unitPrice===null||!validCartQuantity(line)||cartQuantity(line)>=10000} aria-label={`Increase cart ${line.name}`} onClick={()=>updateCartQuantity(line.productId,cartQuantity(line)+1)}>+</button><button className="secondary" disabled={busy||confirming||line.unitPrice===null||!Number.isInteger(Number(cartEdits[line.productId]??line.quantity))||Number(cartEdits[line.productId]??line.quantity)<1||Number(cartEdits[line.productId]??line.quantity)>10000} onClick={()=>updateCartQuantity(line.productId,Number(cartEdits[line.productId]??line.quantity))}>Update cart {line.name}</button></div> <button disabled={busy || confirming} onClick={() => remove(line.productId)}>Remove {line.name}</button></li>)}</ul>
        <p>Current estimate: {money(cart.total)}. Prices and stock are rechecked on preview; cart items do not reserve stock.</p>
        <button disabled={busy || confirming || !cart.items.length} onClick={preview}>Preview checkout</button>
        {review===cart&&Object.keys(cartEdits).length===0&&<section aria-label="Checkout review" className="checkout-review"><h3>Review your solar order</h3><p>Seller: {review.vendorName} · One vendor, one order</p><ul>{review.items.map(line=><li key={line.productId}>{line.name} × {line.quantity} · {money(line.total)}{!line.available&&' · Unavailable'}</li>)}</ul><dl><dt>Items before tax</dt><dd>{money(review.items.reduce((sum,line)=>sum+Number(line.unitPrice??0)*line.quantity,0))}</dd><dt>Promotion savings included</dt><dd>{money(review.items.reduce((sum,line)=>sum+Number(line.discount??0),0))}</dd><dt>Tax</dt><dd>{money(review.items.reduce((sum,line)=>sum+Number(line.tax??0),0))}</dd><dt>Server total</dt><dd>{money(review.total)}</dd></dl><p>No payment has been taken. This quote does not reserve stock. Online payment remains unavailable until the vendor payment integration is enabled; a reservation is an unpaid draft.</p></section>}
        <a className="checkout-link" href="#online-checkout">Review checkout →</a><p>{cart.message}</p>
      </>}
    </section>
    <ReservedDrafts mode="online" cartId={cart?.id} disabled={busy||confirming} onStockChange={()=>setStockRevision(value=>value+1)}/>
    </aside></div><section aria-label="Archived carts"><h2>Saved vendor carts</h2>
      <button disabled={busy || confirming} onClick={loadHistory}>Load archived carts</button>
      {history && (history.length ? <ul>{history.map(saved => <li key={saved.id}><h3>{saved.vendorName}</h3>
        <p>{saved.items.map(line => `${line.name} × ${line.quantity}`).join(', ')} · Current estimate {money(saved.total)}</p>
        <button disabled={busy || confirming || !saved.items.length} onClick={() => setRestoreTarget(saved)}>Restore {saved.vendorName} cart</button>
      </li>)}</ul> : <p>No archived carts found.</p>)}
    </section>
    <OrderHistory mode="online"/>
    {slug && detailId !== undefined && <ProductDetails slug={slug} productId={detailId} disabled={busy || confirming} onClose={closeDetails} onAdd={(id, quantity) => { closeDetails(); setQuantities(value => ({ ...value, [id]: quantity })); void put({ id }, quantity); }}/>}
    {restoreTarget && <section role="dialog" aria-modal="true" aria-labelledby="restore-title"><h2 id="restore-title">Restore archived vendor cart?</h2>
      <p>Restore your saved {restoreTarget.vendorName} cart using current prices. Your active cart will be archived with its items retained. This does not reserve stock or place an order.</p>
      <button disabled={busy} onClick={() => setRestoreTarget(undefined)}>Keep active cart</button><button disabled={busy} onClick={restore}>Confirm cart restore</button>
    </section>}
    {switchTarget && <section role="dialog" aria-modal="true" aria-labelledby="switch-title"><h2 id="switch-title">Switch vendor cart?</h2>
      <p>Your current cart belongs to {cart?.vendorName}. Confirming archives that cart and starts a new cart for this vendor. Existing cart items are retained as archived data.</p>
      <button disabled={busy} onClick={() => setSwitchTarget(undefined)}>Keep current cart</button><button disabled={busy} onClick={confirmSwitch}>Confirm vendor switch</button>
    </section>}
  </Shell>;
}
