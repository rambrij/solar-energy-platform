import {cleanup,render,screen,within} from '@testing-library/react';
import {afterEach,expect,it} from 'vitest';
import CheckoutSteps from './CheckoutSteps';
afterEach(cleanup);
const base={cartItems:0,reviewing:false,reserved:false,paid:false};
it('marks exactly one current step and earlier steps done',()=>{
 render(<CheckoutSteps {...base} cartItems={1} reviewing/>);
 const list=screen.getByRole('list',{name:'Shopping steps'}),items=within(list).getAllByRole('listitem');
 expect(items).toHaveLength(4);expect(items.filter(item=>item.getAttribute('aria-current')==='step')).toHaveLength(1);
 expect(items[2].getAttribute('aria-current')).toBe('step');expect(items[0].className).toBe('step-done');expect(items[1].className).toBe('step-done');expect(items[3].className).toBe('');
 expect(within(items[0]).getByText('(done)')).toBeTruthy();
});
it('shows every step done once the order is paid',()=>{
 render(<CheckoutSteps {...base} paid/>);
 const items=within(screen.getByRole('list',{name:'Shopping steps'})).getAllByRole('listitem');
 expect(items.every(item=>item.className==='step-done')).toBe(true);expect(items.some(item=>item.getAttribute('aria-current')==='step')).toBe(false);
});
it('starts at the first step with an empty cart',()=>{
 render(<CheckoutSteps {...base}/>);expect(screen.getByText('Choose your solar essentials').closest('li')?.getAttribute('aria-current')).toBe('step');
});
