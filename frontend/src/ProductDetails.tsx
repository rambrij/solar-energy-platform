import { useEffect, useRef, useState } from 'react';
import { marketplaceApi } from './marketplaceApi';

type Detail = { id: number; vendorSlug: string; vendorName: string; sku: string; name: string; unitPrice: number; taxRate: number; unitTax: number; unitTotal: number; inStock: boolean; listPrice?: number; discountPercent?: number; categoryName?: string };
export default function ProductDetails({ slug, productId, disabled, onClose, onAdd }: { slug: string; productId: number; disabled: boolean; onClose: () => void; onAdd: (id: number, quantity: number) => void }) {
  const [product, setProduct] = useState<Detail>(), [quantity, setQuantity] = useState(1), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const dialog = useRef<HTMLDialogElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const element = dialog.current;
    if (element && typeof element.showModal === 'function') element.showModal(); else element?.setAttribute('open', '');
    closeRef.current?.focus();
    return () => { if (element?.open && typeof element.close === 'function') element.close(); previous?.focus(); };
  }, []);
  useEffect(() => {
    let active = true; setProduct(undefined); setQuantity(1); setError('');
    marketplaceApi(`/api/marketplace/vendors/${encodeURIComponent(slug)}/products/${productId}`)
      .then(value => { if (active) setProduct(value); })
      .catch(() => { if (active) setError('This product is unavailable. It may have been withdrawn, or the storefront could not be reached.'); });
    return () => { active = false; };
  }, [slug, productId, revision]);
  return <dialog ref={dialog} className="product-dialog" aria-labelledby="product-detail-title" onCancel={event => { event.preventDefault(); if (!disabled) onClose(); }}>
    <div className="section-heading"><span className="eyebrow">Explore solar essentials</span><button ref={closeRef} className="secondary" disabled={disabled} onClick={onClose}>Close product details</button></div>
    {error ? <><h2 id="product-detail-title">Product unavailable</h2><p role="alert">{error}</p><button onClick={() => setRevision(value => value + 1)}>Retry product details</button></> : !product ? <><h2 id="product-detail-title">Product details</h2><p role="status">Loading product details…</p></> : <>
      <div className="detail-visual" aria-hidden="true"><span className="panel-illustration"/></div><span className="eyebrow">{product.vendorName} · {product.categoryName??'Solar essentials'}</span><h2 id="product-detail-title">{product.name}</h2>
      <span className={`badge ${product.inStock ? 'status-paid' : 'status-expired'}`}>{product.inStock ? 'In stock' : 'Out of stock'}</span>
      <dl className="product-facts">{Number(product.listPrice??product.unitPrice)>Number(product.unitPrice)&&<div><dt>Original unit price</dt><dd><del>{Number(product.listPrice).toFixed(2)}</del> · {product.discountPercent}% off</dd></div>}<div><dt>SKU</dt><dd>{product.sku}</dd></div><div><dt>Unit price before tax</dt><dd>{Number(product.unitPrice).toFixed(2)}</dd></div><div><dt>Unit tax ({product.taxRate}%)</dt><dd>{Number(product.unitTax).toFixed(2)}</dd></div><div><dt>Unit total with tax</dt><dd>{Number(product.unitTotal).toFixed(2)}</dd></div></dl>
      <p>Availability refers to this vendor’s designated online stock location. Checkout rechecks prices, tax and stock at the designated location. Adding to your cart does not reserve stock.</p>
      <label>Product detail quantity<input type="number" min="1" max="10000" value={quantity} disabled={disabled} onChange={event => setQuantity(Number(event.target.value))}/></label>
      <button className="detail-add" disabled={disabled || !product.inStock || !Number.isInteger(quantity) || quantity < 1 || quantity > 10000} onClick={() => onAdd(product.id, quantity)}>Add or update this product in cart</button>
    </>}
  </dialog>;
}
