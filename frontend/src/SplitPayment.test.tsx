import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import SplitPayment from './SplitPayment';
afterEach(cleanup);
const fill=(label:RegExp|string,value:string)=>fireEvent.change(screen.getByLabelText(label),{target:{value}});
it('stays disabled until the tenders settle the bill and shows the remaining due',()=>{
 const onSubmit=vi.fn();render(<SplitPayment amountDue={110} eligible locked={false} onSubmit={onSubmit}/>);
 const submit=screen.getByRole('button',{name:'Complete split payment'});expect((submit as HTMLInputElement).disabled).toBe(true);
 fill(/^UPI amount/,'60');expect((screen.getByRole('status'))?.textContent).toContain('do not yet cover');expect((screen.getByText('Remaining').nextSibling)?.textContent).toContain('50.00');expect((submit as HTMLInputElement).disabled).toBe(true);
 fill(/^Cash received/,'60');expect((screen.getByText('Estimated change').nextSibling)?.textContent).toContain('10.00');expect((submit as HTMLInputElement).disabled).toBe(false);
 fireEvent.click(submit);expect(onSubmit).toHaveBeenCalledWith([{method:'CASH',amount:'60.00'},{method:'UPI',amount:'60.00'}]);
});
it('fills the remaining amount into a row',()=>{
 render(<SplitPayment amountDue={110} eligible locked={false} onSubmit={vi.fn()}/>);
 fill(/^UPI amount/,'60');fireEvent.click(screen.getByRole('button',{name:'Fill remaining into Cash received'}));
 expect(Number((screen.getByLabelText(/^Cash received/) as HTMLInputElement).value)).toBe(50);expect((screen.getByRole('button',{name:'Complete split payment'}) as HTMLInputElement).disabled).toBe(false);
});
it('explains over-tendered non-cash, single-method and unnecessary cash',()=>{
 render(<SplitPayment amountDue={110} eligible locked={false} onSubmit={vi.fn()}/>);
 fill(/^UPI amount/,'70');fill(/^Card amount/,'50');expect((screen.getByRole('status'))?.textContent).toContain('exceed the amount due');
 fill(/^Card amount/,'');fill(/^UPI amount/,'110');fill(/^Cash received/,'5');expect((screen.getByRole('status'))?.textContent).toContain('Clear the cash row');
 fill(/^UPI amount/,'');fill(/^Cash received/,'120');expect((screen.getByRole('status'))?.textContent).toContain('at least two methods');expect((screen.getByRole('button',{name:'Complete split payment'}) as HTMLInputElement).disabled).toBe(true);
});
it('locks every control when the sale is locked or ineligible and warns about test payments',()=>{
 const view=render(<SplitPayment amountDue={110} eligible locked onSubmit={vi.fn()}/>);
 expect((screen.getByLabelText(/^UPI amount/) as HTMLInputElement).disabled).toBe(true);expect(screen.getByText(/test payments/)).toBeTruthy();
 view.rerender(<SplitPayment amountDue={110} eligible={false} locked={false} onSubmit={vi.fn()}/>);expect((screen.getByRole('status'))?.textContent).toContain('not ready for split payment');expect((screen.getByLabelText(/^Cash received/) as HTMLInputElement).disabled).toBe(true);
});
