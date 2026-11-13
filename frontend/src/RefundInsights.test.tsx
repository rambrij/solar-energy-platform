import { cleanup,fireEvent,render,screen,waitFor,within } from '@testing-library/react';
import { afterEach,expect,it,vi } from 'vitest';
import RefundInsights from './RefundInsights';
import { api,ApiError } from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const totals={refunds:2,units:3,cash:132,beforeTax:120,tax:12};
const zero={refunds:0,units:0,cash:0,beforeTax:0,tax:0};
const report={branchId:1,from:'2026-11-12',to:'2026-11-13',generatedAt:'2026-11-13T12:00:00Z',totals,days:[{date:'2026-11-12',totals:zero},{date:'2026-11-13',totals}],cashiers:[{cashier:'supervisor',totals}]};
afterEach(()=>{cleanup();vi.resetAllMocks();});
function chooseRange(){fireEvent.change(screen.getByLabelText('Refunds from (UTC)'),{target:{value:'2026-11-12'}});fireEvent.change(screen.getByLabelText('Refunds to (UTC)'),{target:{value:'2026-11-13'}});}
it('loads only explicitly and displays server totals, zero days and paying cashier scope',async()=>{
 vi.mocked(api).mockResolvedValue(report);render(<RefundInsights/>);expect(api).not.toHaveBeenCalled();chooseRange();fireEvent.click(screen.getByText('Load cash refund insights'));
 await screen.findByText(/Branch 1/);expect(api).toHaveBeenCalledWith('/reports/cash-refunds?from=2026-11-12&to=2026-11-13');expect(screen.getByText('Cash refunded')).toBeTruthy();expect(screen.getByLabelText('Cash refunded on 2026-11-12').getAttribute('value')).toBe('0');expect(screen.getByLabelText('Cash refunded on 2026-11-13').getAttribute('value')).toBe('132');expect(within(screen.getByRole('article',{name:'Refunds paid by supervisor'})).getByText('132.00')).toBeTruthy();expect(screen.getByText(/not a till balance/)).toBeTruthy();expect(vi.mocked(api).mock.calls.every(call=>call.length===1)).toBe(true);
});
it('clears prior period results immediately when filters change',async()=>{
 vi.mocked(api).mockResolvedValue(report);render(<RefundInsights/>);chooseRange();fireEvent.click(screen.getByText('Load cash refund insights'));await screen.findByText(/Branch 1/);fireEvent.change(screen.getByLabelText('Refunds from (UTC)'),{target:{value:'2026-11-11'}});expect(screen.queryByText(/Branch 1/)).toBeNull();expect(api).toHaveBeenCalledTimes(1);
});
it('rejects reversed and oversized ranges without calling the backend',async()=>{
 render(<RefundInsights/>);chooseRange();fireEvent.change(screen.getByLabelText('Refunds from (UTC)'),{target:{value:'2026-11-14'}});fireEvent.click(screen.getByText('Load cash refund insights'));await screen.findByRole('alert');expect(api).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('Refunds from (UTC)'),{target:{value:'2024-01-01'}});fireEvent.click(screen.getByText('Load cash refund insights'));expect(api).not.toHaveBeenCalled();
});
it('clears stale totals on denied refresh and explains the dedicated grants',async()=>{
 vi.mocked(api).mockResolvedValueOnce(report).mockRejectedValueOnce(new ApiError(403));render(<RefundInsights/>);chooseRange();fireEvent.click(screen.getByText('Load cash refund insights'));await screen.findByText(/Branch 1/);fireEvent.click(screen.getByText('Load cash refund insights'));await screen.findByRole('alert');expect(screen.getByRole('alert').textContent).toContain('POS_REFUND_VIEW');expect(screen.queryByText(/Branch 1/)).toBeNull();
});
it('shows empty refund period separately from stock-return requests',async()=>{
 vi.mocked(api).mockResolvedValue({...report,totals:zero,cashiers:[],days:[]});render(<RefundInsights/>);chooseRange();fireEvent.click(screen.getByText('Load cash refund insights'));await screen.findByText('No paid cash refunds in this period');expect(screen.getByText(/Pending stock returns/)).toBeTruthy();expect(screen.queryByRole('article')).toBeNull();
});
it('disables duplicate submits while loading and ignores late unmounted results',async()=>{
 let finish!:(value:typeof report)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const view=render(<RefundInsights/>);chooseRange();fireEvent.click(screen.getByText('Load cash refund insights'));expect((screen.getByText('Loading cash refund insights...') as HTMLButtonElement).disabled).toBe(true);view.unmount();finish(report);await waitFor(()=>expect(api).toHaveBeenCalledTimes(1));
});
