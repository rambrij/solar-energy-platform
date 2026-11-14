import {act,cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CashierSalesInsights from './CashierSalesInsights';
import {api,ApiError} from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const totals={paidOrders:1,sales:143,tax:13,beforeTax:130,cashReceived:200,changeGiven:57,cashApplied:143,units:3,invoices:1,discounts:10};
const report={branchId:1,from:'2026-11-12',to:'2026-11-13',generatedAt:'2026-11-13T12:00:00Z',page:0,size:12,totalElements:13,totalPages:2,pageTotals:totals,items:[{cashier:'alice',totals}]};
afterEach(()=>{cleanup();vi.resetAllMocks();});
function dates(){fireEvent.change(screen.getByLabelText('Cashier sales from (UTC)'),{target:{value:'2026-11-12'}});fireEvent.change(screen.getByLabelText('Cashier sales to (UTC)'),{target:{value:'2026-11-13'}});}
it('loads explicitly with bounded filters and labels page-only gross values',async()=>{
 vi.mocked(api).mockResolvedValue(report);render(<CashierSalesInsights/>);expect(api).not.toHaveBeenCalled();dates();fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByText('alice');expect(api).toHaveBeenCalledWith('/reports/cashier-sales?from=2026-11-12&to=2026-11-13&size=12&page=0');expect(screen.getByText('Shown-page gross sales')).toBeTruthy();expect(within(screen.getByRole('article',{name:'Sales by alice'})).getByText('143.00 / 1')).toBeTruthy();expect(screen.getByText(/Original selling cashier/)).toBeTruthy();expect(screen.queryByText('Download sales CSV')).toBeNull();
});
it('pages sellers without presenting shown totals as whole-branch totals',async()=>{
 vi.mocked(api).mockResolvedValueOnce(report).mockResolvedValueOnce({...report,page:1,items:[{cashier:'manager',totals}]});render(<CashierSalesInsights/>);dates();fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByText('alice');fireEvent.click(screen.getByText('Next cashiers'));await screen.findByText('manager');expect(screen.queryByText('alice')).toBeNull();expect(api).toHaveBeenLastCalledWith('/reports/cashier-sales?from=2026-11-12&to=2026-11-13&size=12&page=1');expect(screen.getByText(/Totals above cover only/)).toBeTruthy();
});
it('invalidates existing employee data when filters or page size change',async()=>{
 vi.mocked(api).mockResolvedValue(report);render(<CashierSalesInsights/>);dates();fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByText('alice');fireEvent.change(screen.getByLabelText('Cashiers per page'),{target:{value:'24'}});expect(screen.queryByText('alice')).toBeNull();expect(api).toHaveBeenCalledTimes(1);
});
it('rejects invalid ranges before requesting employee data',async()=>{
 render(<CashierSalesInsights/>);dates();fireEvent.change(screen.getByLabelText('Cashier sales from (UTC)'),{target:{value:'2026-11-14'}});fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByRole('alert');expect(api).not.toHaveBeenCalled();
});
it('clears stale rows after denied refresh and explains the dedicated permission',async()=>{
 vi.mocked(api).mockResolvedValueOnce(report).mockRejectedValueOnce(new ApiError(403));render(<CashierSalesInsights/>);dates();fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByText('alice');fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByRole('alert');expect(screen.getByRole('alert').textContent).toContain('POS_CASHIER_REPORT_VIEW');expect(screen.queryByText('alice')).toBeNull();
});
it('shows a paid-sales empty state and ignores an unmounted response',async()=>{
 vi.mocked(api).mockResolvedValueOnce({...report,totalElements:0,totalPages:0,items:[]});const view=render(<CashierSalesInsights/>);dates();fireEvent.click(screen.getByText('Load selling cashier insights'));await screen.findByText('No paid walk-in sales in this period');let finish!:(value:typeof report)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));fireEvent.click(screen.getByText('Load selling cashier insights'));view.unmount();await act(async()=>finish(report));expect(screen.queryByText('alice')).toBeNull();
});
