import CashTender from './CashTender';
export type BillingProduct = { id: number; name: string; price: number; list_price?: number; tax_rate: number; quantity: number };
export type SaleDiscounts = {linePercent:Record<number,number>;billPercent:number;reason:string};
export function billEstimate(products: BillingProduct[], cart: Record<number, number>, discounts?:SaleDiscounts) {
  let subtotal = 0, discount = 0, tax = 0, net = 0;
  const percent=(value:number)=>Number.isFinite(value)?Math.min(100,Math.max(0,value)):0;
  const lines=Object.entries(cart).sort(([a],[b])=>Number(a)-Number(b)).flatMap(([id,quantity])=>{
    const product=products.find(item=>item.id===Number(id));
    if(!product||!Number.isInteger(quantity)||quantity<1)return [];
    const unit=Math.round(Number(product.price)*100),list=Math.round(Number(product.list_price??product.price)*100);
    subtotal+=list*quantity;discount+=(list-unit)*quantity;
    const lineDiscount=Math.round(unit*quantity*percent(Number(discounts?.linePercent[Number(id)]??0))/100);
    discount+=lineDiscount;return [{net:unit*quantity-lineDiscount,rate:Number(product.tax_rate)}];
  });
  const base=lines.reduce((sum,line)=>sum+line.net,0),billDiscount=Math.round(base*percent(Number(discounts?.billPercent??0))/100);
  const allocated=lines.map(line=>base?Math.floor(billDiscount*line.net/base):0);
  let remaining=billDiscount-allocated.reduce((a,b)=>a+b,0);
  for(let i=0;remaining>0&&lines.length;i=(i+1)%lines.length){if(allocated[i]<lines[i].net){allocated[i]++;remaining--;}}
  lines.forEach((line,index)=>{const amount=line.net-allocated[index];net+=amount;tax+=Math.round(amount*line.rate/100);});
  discount+=billDiscount;
  return { subtotal: subtotal / 100, discount: discount / 100, tax: tax / 100, total: (net + tax) / 100 };
}
export default function BillingBasket({ products, cart, locked, onQuantity, onRemove, cash, onCash, invoice, onInvoice, discounts, onDiscounts }: { products: BillingProduct[]; cart: Record<number, number>; locked: boolean; onQuantity: (id: number, quantity: number) => void; onRemove: (id: number) => void; cash: string; onCash: (value: string) => void; invoice: boolean; onInvoice: (value: boolean) => void; discounts?:SaleDiscounts; onDiscounts?:(value:SaleDiscounts)=>void }) {
  const amounts = billEstimate(products, cart,discounts);
  const validBill=Object.keys(cart).length>0&&Object.entries(cart).every(([id,q])=>{const product=products.find(item=>item.id===Number(id));return !!product&&Number.isInteger(q)&&q>=1&&q<=Math.min(10000,product.quantity);});
  return <fieldset className="billing-basket" disabled={locked}>
    <div className="bill-columns" aria-hidden="true"><span>Item</span><span>Qty</span><span>Amount</span></div>
    <ul className="bill-lines">{Object.entries(cart).map(([id, quantity]) => {
      const product = products.find(item => item.id === Number(id)); if (!product) return <li key={id}>Product unavailable. <button onClick={() => onRemove(Number(id))}>Remove unavailable product</button></li>;
      return <li key={id}><div className="bill-line-name"><strong>{product.name}</strong><small>{Number(product.price).toFixed(2)} + {product.tax_rate}% tax</small>{Number(product.list_price ?? product.price) > Number(product.price) && <span className="saving-label">Promotion savings {((Number(product.list_price) - Number(product.price)) * quantity).toFixed(2)}</span>}<button className="remove-line" onClick={() => onRemove(Number(id))}>Remove {product.name}</button></div><div className="quantity-stepper"><button aria-label={`Decrease ${product.name}`} disabled={quantity <= 1} onClick={() => onQuantity(Number(id), quantity - 1)}>−</button><input aria-label={`Quantity ${id}`} type="number" min="1" max={Math.min(10000, product.quantity)} value={quantity} onChange={event => onQuantity(Number(id), Number(event.target.value))}/><button aria-label={`Increase ${product.name}`} disabled={quantity >= Math.min(10000, product.quantity)} onClick={() => onQuantity(Number(id), quantity + 1)}>+</button></div>{onDiscounts&&<label>Line discount % {product.name}<input type="number" min="0" max="100" step="0.01" value={discounts?.linePercent[Number(id)]??0} onChange={event=>onDiscounts({linePercent:{...discounts?.linePercent,[Number(id)]:Number(event.target.value)},billPercent:discounts?.billPercent??0,reason:discounts?.reason??''})}/></label>}<strong className="bill-line-total">{(Number(product.price) * quantity).toFixed(2)}</strong></li>;
    })}</ul>
    {onDiscounts&&<div className="discount-controls"><label>Bill discount %<input type="number" min="0" max="100" step="0.01" value={discounts?.billPercent??0} onChange={event=>onDiscounts({linePercent:discounts?.linePercent??{},billPercent:Number(event.target.value),reason:discounts?.reason??''})}/></label><label>Discount reason<input maxLength={250} value={discounts?.reason??''} onChange={event=>onDiscounts({linePercent:discounts?.linePercent??{},billPercent:discounts?.billPercent??0,reason:event.target.value})}/></label><p>The server checks your discount permission and the vendor's approved combined limit.</p></div>}
    <dl className="bill-summary"><div><dt>Subtotal before discounts</dt><dd>{amounts.subtotal.toFixed(2)}</dd></div><div className="saving-label"><dt>Total discounts</dt><dd>−{amounts.discount.toFixed(2)}</dd></div><div><dt>Tax</dt><dd>{amounts.tax.toFixed(2)}</dd></div><div className="amount-due"><dt>Amount due (estimate)</dt><dd>{amounts.total.toFixed(2)}</dd></div></dl>
    <p className="bill-hint">Final prices, approved discounts, tax and change are calculated by the server.</p>
    <div className="payment-methods" role="group" aria-label="Payment method"><label><input type="radio" name="pos-payment" checked readOnly/>Cash</label><label><input type="radio" name="pos-payment" disabled/>UPI</label><label><input type="radio" name="pos-payment" disabled/>Card</label></div><p className="bill-hint">Cash is available now. For vendor UPI/card terminal payments, reserve this bill below, prepare the tender and obtain independent supervisor verification.</p>
    <CashTender amountDue={amounts.total} cash={cash} onCash={onCash} eligible={validBill} locked={locked}/>
    <label className="check-label"><input type="checkbox" checked={invoice} onChange={event => onInvoice(event.target.checked)}/> Request tax invoice</label>
  </fieldset>;
}
