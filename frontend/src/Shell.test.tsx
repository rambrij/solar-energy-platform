import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import Shell from './Shell';
afterEach(()=>{cleanup();window.history.replaceState({},'','/');vi.restoreAllMocks();});
it('provides keyboard navigation and identifies the active workspace',()=>{
 render(<Shell mode="online"><h1>Explore solar</h1><section id="order-history">Orders</section></Shell>);expect(screen.getByText('Skip to content').getAttribute('href')).toBe('#main-content');expect(screen.getByRole('main').id).toBe('main-content');expect(screen.getByRole('link',{name:'Marketplace'}).getAttribute('aria-current')).toBe('page');expect(screen.getByRole('link',{name:'My orders'}).getAttribute('href')).toBe('#order-history');expect(screen.queryByRole('link',{name:'Cash refunds'})).toBeNull();
});
it('opens the mobile disclosure and supports Escape with focus return',()=>{
 render(<Shell mode="pos"><section id="order-history">History</section></Shell>);const toggle=screen.getByRole('button',{name:'Open workspace menu'});expect(toggle.getAttribute('aria-expanded')).toBe('false');fireEvent.click(toggle);expect(toggle.getAttribute('aria-expanded')).toBe('true');const link=screen.getByRole('link',{name:'Sales history'});link.focus();fireEvent.keyDown(link,{key:'Escape'});expect(toggle.getAttribute('aria-expanded')).toBe('false');expect(document.activeElement).toBe(toggle);
});
it('navigates in place, updates breadcrumbs and focus without clearing form or saved attempts',()=>{
 sessionStorage.setItem('pos-attempt-v1','saved');render(<Shell mode="pos"><section id="order-history"><h1>Saved sales</h1><input aria-label="Unsent search" defaultValue="keep this"/></section></Shell>);fireEvent.click(screen.getByRole('button',{name:'Open workspace menu'}));const link=screen.getByRole('link',{name:'Sales history'});fireEvent.click(link);expect(window.location.hash).toBe('#order-history');expect(link.getAttribute('aria-current')).toBe('location');expect(document.activeElement?.id).toBe('order-history');expect(within(screen.getByRole('navigation',{name:'Breadcrumb'})).getByText('Sales history').getAttribute('aria-current')).toBe('location');expect((screen.getByLabelText('Unsent search') as HTMLInputElement).value).toBe('keep this');expect(sessionStorage.getItem('pos-attempt-v1')).toBe('saved');sessionStorage.removeItem('pos-attempt-v1');expect(screen.getByRole('button',{name:'Open workspace menu'}).getAttribute('aria-expanded')).toBe('false');
});
it('tracks deep links and browser back/forward fragment changes',()=>{
 window.history.replaceState({},'','/#cash-refunds');render(<Shell mode="pos"><section id="cash-refunds">Payout</section><section id="order-history">History</section></Shell>);expect(screen.getByRole('link',{name:'Cash refunds'}).getAttribute('aria-current')).toBe('location');window.history.replaceState({},'','/#order-history');fireEvent(window,new PopStateEvent('popstate'));expect(screen.getByRole('link',{name:'Sales history'}).getAttribute('aria-current')).toBe('location');window.history.replaceState({},'','/#unknown');fireEvent(window,new HashChangeEvent('hashchange'));expect(screen.getByRole('link',{name:'Overview'}).getAttribute('aria-current')).toBe('location');
});
it('adds and removes conditional section links as panels become available',async()=>{
 const view=render(<Shell mode="pos"><h1>Start a register</h1></Shell>);expect(screen.queryByRole('link',{name:'Current bill'})).toBeNull();view.rerender(<Shell mode="pos"><section id="current-sale">Bill</section></Shell>);await screen.findByRole('link',{name:'Current bill'});view.rerender(<Shell mode="pos"><h1>Register closed</h1></Shell>);await waitFor(()=>expect(screen.queryByRole('link',{name:'Current bill'})).toBeNull());
});
it('preserves modified-click browser behavior',()=>{render(<Shell mode="online"><section id="order-history">Orders</section></Shell>);const push=vi.spyOn(window.history,'pushState');const link=screen.getByRole('link',{name:'My orders'});link.addEventListener('click',event=>event.preventDefault());fireEvent.click(link,{ctrlKey:true});expect(push).not.toHaveBeenCalled();expect(window.location.hash).toBe('');});

it('resolves an initial fragment once an asynchronous section is mounted',async()=>{
 window.history.replaceState({},'','/#checkout');const view=render(<Shell mode="pos"><p>Checking session</p></Shell>);expect(screen.queryByRole('link',{name:'Product catalogue'})).toBeNull();view.rerender(<Shell mode="pos"><section id="checkout">Products</section></Shell>);await waitFor(()=>expect(document.activeElement?.id).toBe('checkout'));await waitFor(()=>expect(screen.getByRole('link',{name:'Product catalogue'}).getAttribute('aria-current')).toBe('location'));
});
