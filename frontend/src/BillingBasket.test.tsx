import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import BillingBasket, { billEstimate } from './BillingBasket';
const products = [{ id: 1, name: 'Panel', price: 90, list_price: 100, tax_rate: 10, quantity: 3 }];
afterEach(cleanup);
it('separates list subtotal, approved savings and tax using per-line cent rounding', () => {
 expect(billEstimate(products, { 1: 2 })).toEqual({ subtotal: 200, discount: 20, tax: 18, total: 198 });
 expect(billEstimate([{ ...products[0], price: .05, list_price: .1 }], { 1: 3 })).toEqual({ subtotal: .3, discount: .15, tax: .02, total: .17 });
});
it('supports quick quantity edits and preserves cash-only payment availability', () => {
 const quantity = vi.fn(), remove = vi.fn(); render(<BillingBasket products={products} cart={{ 1: 2 }} locked={false} onQuantity={quantity} onRemove={remove} cash="200" onCash={vi.fn()} invoice={false} onInvoice={vi.fn()}/>);
 fireEvent.click(screen.getByLabelText('Increase Panel')); expect(quantity).toHaveBeenCalledWith(1, 3);
 fireEvent.click(screen.getByLabelText('Decrease Panel')); expect(quantity).toHaveBeenCalledWith(1, 1);
 fireEvent.click(screen.getByText('Remove Panel')); expect(remove).toHaveBeenCalledWith(1);
 expect((screen.getByLabelText('Cash') as HTMLInputElement).checked).toBe(true); expect((screen.getByLabelText('UPI') as HTMLInputElement).disabled).toBe(true); expect((screen.getByLabelText('Card') as HTMLInputElement).disabled).toBe(true);
 expect(screen.getByText('2.00')).toBeTruthy();
});
it('freezes edits while a financial outcome is unresolved and allows unavailable line removal otherwise', () => {
 const view = render(<BillingBasket products={products} cart={{ 1: 1 }} locked onQuantity={vi.fn()} onRemove={vi.fn()} cash="" onCash={vi.fn()} invoice={false} onInvoice={vi.fn()}/>);
 expect(screen.getByLabelText('Quantity 1').matches(':disabled')).toBe(true);
 const remove = vi.fn(); view.rerender(<BillingBasket products={[]} cart={{ 99: 1 }} locked={false} onQuantity={vi.fn()} onRemove={remove} cash="" onCash={vi.fn()} invoice={false} onInvoice={vi.fn()}/>);
 fireEvent.click(screen.getByText('Remove unavailable product')); expect(remove).toHaveBeenCalledWith(99);
});
