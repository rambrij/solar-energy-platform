import CashTender from './CashTender';
import DiscountControls from './DiscountControls';
import {discountedUnitCents,lineDiscountPercent,hasManualDiscount,noDiscounts,type Discounts} from './discountMath';
export type BillingProduct = { id: number; name: string; price: number; list_price?: number; tax_rate: number; quantity: number };
export function billEstimate(products: BillingProduct[], cart: Record<number, number>, discounts: Discounts = noDiscounts) {
  let subtotal = 0, discount = 0, manual = 0, tax = 0, net = 0;
  for (const [id, quantity] of Object.entries(cart)) {
    const product = products.find(item => item.id === Number(id));
    if (!product || !Number.isInteger(quantity) || quantity < 1) continue;
    const catalogue = Math.round(Number(product.price) * 100), list = Math.round(Number(product.list_price ?? product.price) * 100);
    const unit = discountedUnitCents(catalogue, lineDiscountPercent(discounts, Number(id)) ?? 0);
    subtotal += list * quantity; discount += (list - catalogue) * quantity; manual += (catalogue - unit) * quantity; net += unit * quantity;
    tax += Math.round(unit * quantity * Number(product.tax_rate) / 100);
  }
  return { subtotal: subtotal / 100, discount: discount / 100, manualDiscount: manual / 100, tax: tax / 100, total: (net + tax) / 100 };
}
export default function BillingBasket({ products, cart, locked, onQuantity, onRemove, cash, onCash, invoice, onInvoice, discounts = noDiscounts, onDiscounts }: { products: BillingProduct[]; cart: Record<number, number>; locked: boolean; onQuantity: (id: number, quantity: number) => void; onRemove: (id: number) => void; cash: string; onCash: (value: string) => void; invoice: boolean; onInvoice: (value: boolean) => void; discounts?: Discounts; onDiscounts?: (value: Discounts) => void }) {
  const amounts = billEstimate(products, cart, discounts), discounted = hasManualDiscount(discounts);
  const validBill=Object.keys(cart).length>0&&Object.entries(cart).every(([id,q])=>{const product=products.find(item=>item.id===Number(id));return !!product&&Number.isInteger(q)&&q>=1&&q<=Math.min(10000,product.quantity);});
  return <fieldset className="billing-basket" disabled={locked}>
    <div className="bill-columns" aria-hidden="true"><span>Item</span><span>Qty</span><span>Amount</span></div>
    <ul className="bill-lines">{Object.entries(cart).map(([id, quantity]) => {
      const product = products.find(item => item.id === Number(id)); if (!product) return <li key={id}>Product unavailable. <button onClick={() => onRemove(Number(id))}>Remove unavailable product</button></li>;
      return <li key={id}><div className="bill-line-name"><strong>{product.name}</strong><small>{Number(product.price).toFixed(2)} + {product.tax_rate}% tax</small>{Number(product.list_price ?? product.price) > Number(product.price) && <span className="saving-label">Promotion savings {((Number(product.list_price) - Number(product.price)) * quantity).toFixed(2)}</span>}<button className="remove-line" onClick={() => onRemove(Number(id))}>Remove {product.name}</button></div><div className="quantity-stepper"><button aria-label={`Decrease ${product.name}`} disabled={quantity <= 1} onClick={() => onQuantity(Number(id), quantity - 1)}>−</button><input aria-label={`Quantity ${id}`} type="number" min="1" max={Math.min(10000, product.quantity)} value={quantity} onChange={event => onQuantity(Number(id), Number(event.target.value))}/><button aria-label={`Increase ${product.name}`} disabled={quantity >= Math.min(10000, product.quantity)} onClick={() => onQuantity(Number(id), quantity + 1)}>+</button></div><strong className="bill-line-total">{(Number(product.price) * quantity).toFixed(2)}</strong></li>;
    })}</ul>
    <dl className="bill-summary"><div><dt>Subtotal before discounts</dt><dd>{amounts.subtotal.toFixed(2)}</dd></div><div className="saving-label"><dt>Promotion discounts</dt><dd>−{amounts.discount.toFixed(2)}</dd></div>{discounted&&<div className="saving-label"><dt>Manual discounts</dt><dd>−{amounts.manualDiscount.toFixed(2)}</dd></div>}<div><dt>Tax</dt><dd>{amounts.tax.toFixed(2)}</dd></div><div className="amount-due"><dt>Amount due (estimate)</dt><dd>{amounts.total.toFixed(2)}</dd></div></dl>
    {onDiscounts&&<DiscountControls lines={Object.keys(cart).map(id=>({id:Number(id),name:products.find(item=>item.id===Number(id))?.name??`Product ${id}`}))} discounts={discounts} locked={locked} onChange={onDiscounts}/>}
    <p className="bill-hint">Final prices, approved discounts, tax and change are calculated by the server.</p>
    <div className="payment-methods" role="group" aria-label="Payment method"><label><input type="radio" name="pos-payment" checked readOnly/>Cash</label><label><input type="radio" name="pos-payment" disabled/>UPI</label><label><input type="radio" name="pos-payment" disabled/>Card</label></div><p className="bill-hint">Cash is available now. For UPI/card, reserve this bill below and check vendor payment options. Test payments require explicit pre-production configuration.</p>
    <CashTender amountDue={amounts.total} cash={cash} onCash={onCash} eligible={validBill} locked={locked}/>
    <label className="check-label"><input type="checkbox" checked={invoice} onChange={event => onInvoice(event.target.checked)}/> Request tax invoice</label>
  </fieldset>;
}
