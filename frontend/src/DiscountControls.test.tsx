import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import DiscountControls from './DiscountControls';
import BillingBasket from './BillingBasket';
import {noDiscounts} from './discountMath';
afterEach(cleanup);
const lines=[{id:1,name:'Panel'}];
it('reports bill and per-line percentages and shows the effective stacked percent',()=>{
 const onChange=vi.fn();const view=render(<DiscountControls lines={lines} discounts={noDiscounts} locked={false} onChange={onChange}/>);
 fireEvent.change(screen.getByLabelText('Bill discount %'),{target:{value:'10'}});expect(onChange).toHaveBeenLastCalledWith({bill:'10',lines:{}});
 fireEvent.change(screen.getByLabelText('Panel discount %'),{target:{value:'5'}});expect(onChange).toHaveBeenLastCalledWith({bill:'',lines:{1:'5'}});
 view.rerender(<DiscountControls lines={lines} discounts={{bill:'10',lines:{1:'10'}}} locked={false} onChange={onChange}/>);
 expect(screen.getByText('Effective 19.00%')).toBeTruthy();expect(screen.getByText('Manual discounts (applied)')).toBeTruthy();
 fireEvent.click(screen.getByText('Clear all discounts'));expect(onChange).toHaveBeenLastCalledWith(noDiscounts);
});
it('flags out-of-range percentages and locks while the sale is locked',()=>{
 render(<DiscountControls lines={lines} discounts={{bill:'100',lines:{}}} locked onChange={vi.fn()}/>);
 expect((screen.getByRole('alert'))?.textContent).toContain('0 to 99.99');expect((screen.getByLabelText('Bill discount %') as HTMLInputElement).disabled).toBe(true);
});
it('shows manual discounts in the bill estimate',()=>{
 const products=[{id:1,name:'Panel',price:100,tax_rate:10,quantity:5}];
 render(<BillingBasket products={products} cart={{1:1}} locked={false} onQuantity={vi.fn()} onRemove={vi.fn()} cash="" onCash={vi.fn()} invoice={false} onInvoice={vi.fn()} discounts={{bill:'10',lines:{}}} onDiscounts={vi.fn()}/>);
 expect((screen.getByText('Manual discounts').nextSibling)?.textContent).toContain('−10.00');expect((screen.getByText('Amount due (estimate)').nextSibling)?.textContent).toContain('99.00');
});
