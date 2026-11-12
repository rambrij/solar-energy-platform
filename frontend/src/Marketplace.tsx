import { useEffect, useState } from 'react';
import { marketplaceApi, MarketplaceError } from './marketplaceApi';

type Vendor = { id: number; slug: string; name: string };
type Product = { id: number; sku: string; name: string; price: number; taxRate: number; inStock: boolean };
type Line = { productId: number; name: string; quantity: number; unitPrice: number | null; tax: number | null; total: number | null; available: boolean };
type Cart = { id: string | null; vendorSlug: string | null; vendorName: string | null; items: Line[]; total: number; checkoutAvailable: boolean; message: string };
type Target = { vendorSlug: string; productId: number; quantity: number };
const money = (amount: number | null) => amount === null ? 'Unavailable' : Number(amount).toFixed(2);

export default function Marketplace() {
  const slug = window.location.pathname.startsWith('/vendors/') ? decodeURIComponent(window.location.pathname.split('/')[2]) : '';
  const [vendors, setVendors] = useState<Vendor[]>([]), [products, setProducts] = useState<Product[]>([]);
  const [cart, setCart] = useState<Cart>(), [quantities, setQuantities] = useState<Record<number, number>>({});
  const [search, setSearch] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [switchTarget, setSwitchTarget] = useState<Target>();
  const [history, setHistory] = useState<Cart[]>(), [restoreTarget, setRestoreTarget] = useState<Cart>();
  const confirming = !!switchTarget || !!restoreTarget;
  useEffect(() => {
    let active = true;
    marketplaceApi('/api/marketplace/vendors').then(value => { if (active) setVendors(value); }).catch(() => { if (active) setError('Vendor directory unavailable.'); });
    if (slug) marketplaceApi(`/api/marketplace/vendors/${encodeURIComponent(slug)}/products`).then(value => { if (active) setProducts(value); }).catch(() => { if (active) setError('Vendor storefront unavailable.'); });
    return () => { active = false; };
  }, [slug]);
  function failure(e: unknown) {
    setError(e instanceof MarketplaceError && [401,403].includes(e.status)
      ? 'Sign in with your global customer account to manage your cart. Staff credentials are separate.'
      : 'Request was not confirmed. Reload your cart before retrying. No payment has been taken.');
  }
  async function loadCart() {
    setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi('/api/customer/cart')); } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function put(product: Product) {
    const target = { vendorSlug: slug, productId: product.id, quantity: quantities[product.id] ?? 1 };
    setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi('/api/customer/cart/items', 'POST', target)); }
    catch (e) {
      if (e instanceof MarketplaceError && e.status === 409) {
        try { const current: Cart = await marketplaceApi('/api/customer/cart'); setCart(current);
          if (current.id && current.vendorSlug !== slug) setSwitchTarget(target); else setError('Cart changed. Review its current contents and try again.');
        } catch (loadError) { failure(loadError); }
      } else failure(e);
    } finally { setBusy(false); }
  }
  async function confirmSwitch() {
    if (!cart?.id || !switchTarget) return;
    setBusy(true); setError('');
    try { setCart(await marketplaceApi('/api/customer/cart/switch', 'POST', { expectedCartId: cart.id, ...switchTarget })); setSwitchTarget(undefined); }
    catch (e) { setSwitchTarget(undefined); failure(e); } finally { setBusy(false); }
  }
  async function remove(id: number) {
    setBusy(true); setError(''); setNotice('');
    try { setCart(await marketplaceApi(`/api/customer/cart/items/${id}`, 'DELETE')); } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function preview() {
    setBusy(true); setError(''); setNotice('');
    try { const quote: Cart = await marketplaceApi('/api/customer/cart/quote', 'POST', {}); setCart(quote); setNotice(quote.message); }
    catch (e) { if (e instanceof MarketplaceError && e.status === 409) setError('A cart item is unavailable or the cart is empty. Reload it to check stock and prices.'); else failure(e); }
    finally { setBusy(false); }
  }
  async function loadHistory() {
    setBusy(true); setError(''); setNotice('');
    try {
      const [current, archived] = await Promise.all([marketplaceApi('/api/customer/cart'), marketplaceApi('/api/customer/cart/history')]);
      setCart(current); setHistory(archived);
    } catch (e) { failure(e); } finally { setBusy(false); }
  }
  async function restore() {
    if (!restoreTarget?.id) return;
    setBusy(true); setError(''); setNotice('');
    try {
      setCart(await marketplaceApi('/api/customer/cart/restore', 'POST', { archivedCartId: restoreTarget.id, expectedCartId: cart?.id ?? null }));
      setHistory(undefined); setRestoreTarget(undefined); setNotice('Archived cart restored with current prices. Stock is not reserved.');
    } catch (e) {
      setRestoreTarget(undefined);
      if (e instanceof MarketplaceError && [404,409].includes(e.status)) setError('The cart changed or archived products are unavailable. Reload your cart history before trying again.');
      else failure(e);
    } finally { setBusy(false); }
  }
  return <main>
    <nav><a href="/vendors">Vendor directory</a> · <a href="/">Physical store POS</a></nav>
    <h1>Saumya Solar Marketplace</h1><p>Choose a vendor. Your customer account works across vendors; each cart belongs to one vendor.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    <h2>Vendors</h2><ul>{vendors.map(vendor => <li key={vendor.id}><a href={`/vendors/${encodeURIComponent(vendor.slug)}`}>{vendor.name}</a></li>)}</ul>
    {slug && <section aria-label="Vendor products"><h2>{vendors.find(vendor => vendor.slug === slug)?.name ?? 'Vendor storefront'}</h2>
      <label>Search products <input value={search} onChange={e => setSearch(e.target.value)} /></label>
      <ul>{products.filter(product => `${product.name} ${product.sku}`.toLowerCase().includes(search.toLowerCase())).map(product => {
        const quantity = quantities[product.id] ?? 1;
        return <li key={product.id}><h3>{product.name}</h3><p>{product.sku} · {money(product.price)} + {product.taxRate}% tax · {product.inStock ? 'In stock' : 'Out of stock'}</p>
          <label>Quantity for {product.name} <input type="number" min="1" max="10000" value={quantity} onChange={e => setQuantities({ ...quantities, [product.id]: Number(e.target.value) })} /></label>
          <button disabled={busy || confirming || !product.inStock || !Number.isInteger(quantity) || quantity < 1 || quantity > 10000} onClick={() => put(product)}>Add or update {product.name} in cart</button>
        </li>;
      })}</ul>
    </section>}
    <section aria-label="Customer cart"><h2>My single-vendor cart</h2>
      <button disabled={busy || confirming} onClick={loadCart}>Sign in / reload my cart</button>
      {cart && <><h3>{cart.vendorName ?? 'Your cart is empty'}</h3><ul>{cart.items.map(line => <li key={line.productId}>{line.name} × {line.quantity} · Unit price {money(line.unitPrice)} · Tax {money(line.tax)} · Total {money(line.total)} {!line.available && '· Unavailable for checkout'} <button disabled={busy || confirming} onClick={() => remove(line.productId)}>Remove {line.name}</button></li>)}</ul>
        <p>Current estimate: {money(cart.total)}. Prices and stock are rechecked on preview; cart items do not reserve stock.</p>
        <button disabled={busy || confirming || !cart.items.length} onClick={preview}>Preview checkout</button>
        <p>{cart.message}</p>
      </>}
    </section>
    <section aria-label="Archived carts"><h2>Saved vendor carts</h2>
      <button disabled={busy || confirming} onClick={loadHistory}>Load archived carts</button>
      {history && (history.length ? <ul>{history.map(saved => <li key={saved.id}><h3>{saved.vendorName}</h3>
        <p>{saved.items.map(line => `${line.name} × ${line.quantity}`).join(', ')} · Current estimate {money(saved.total)}</p>
        <button disabled={busy || confirming || !saved.items.length} onClick={() => setRestoreTarget(saved)}>Restore {saved.vendorName} cart</button>
      </li>)}</ul> : <p>No archived carts found.</p>)}
    </section>
    {restoreTarget && <section role="dialog" aria-modal="true" aria-labelledby="restore-title"><h2 id="restore-title">Restore archived vendor cart?</h2>
      <p>Restore your saved {restoreTarget.vendorName} cart using current prices. Your active cart will be archived with its items retained. This does not reserve stock or place an order.</p>
      <button disabled={busy} onClick={() => setRestoreTarget(undefined)}>Keep active cart</button><button disabled={busy} onClick={restore}>Confirm cart restore</button>
    </section>}
    {switchTarget && <section role="dialog" aria-modal="true" aria-labelledby="switch-title"><h2 id="switch-title">Switch vendor cart?</h2>
      <p>Your current cart belongs to {cart?.vendorName}. Confirming archives that cart and starts a new cart for this vendor. Existing cart items are retained as archived data.</p>
      <button disabled={busy} onClick={() => setSwitchTarget(undefined)}>Keep current cart</button><button disabled={busy} onClick={confirmSwitch}>Confirm vendor switch</button>
    </section>}
  </main>;
}
