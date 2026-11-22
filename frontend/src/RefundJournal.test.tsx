import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import RefundJournal from './RefundJournal';
import {api,ApiError} from './api';
import type {CashRefundView} from './CashRefunds';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const row={id:'r1',returnId:'t1',orderId:'o1',originalReceipt:'1/7',cashier:'manager',amount:110,paidAt:'2026-11-12T12:00:00Z'};
const report={branchId:1,from:'2026-11-12',to:'2026-11-13',reference:'',page:0,size:12,totalElements:13,totalPages:2,items:[row]};
const full:CashRefundView={...row,branchId:'1',sessionId:'s1',reason:'Cash handed over',method:'CASH',status:'PAID',copy:false,items:[]};
const props={disabled:false,onRecovered:vi.fn()};
afterEach(()=>{cleanup();vi.resetAllMocks();});
function dates(){fireEvent.change(screen.getByLabelText('Refund journal from (UTC)'),{target:{value:'2026-11-12'}});fireEvent.change(screen.getByLabelText('Refund journal to (UTC)'),{target:{value:'2026-11-13'}});}
it('loads only explicitly and opens the existing full acknowledgement without a mutation',async()=>{
 vi.mocked(api).mockImplementation(async path=>path==='/refunds/r1'?full:report);render(<RefundJournal {...props}/>);expect(api).not.toHaveBeenCalled();dates();fireEvent.click(screen.getByText('Load cash refund journal'));fireEvent.click(await screen.findByText('Open refund r1'));await waitFor(()=>expect(props.onRecovered).toHaveBeenCalledWith(full));expect(vi.mocked(api).mock.calls.every(call=>call[1]===undefined)).toBe(true);
});
it('pages with server filters and clears old rows on filter edits',async()=>{
 vi.mocked(api).mockResolvedValueOnce(report).mockResolvedValueOnce({...report,page:1,items:[{...row,id:'r2'}]});render(<RefundJournal {...props}/>);dates();fireEvent.click(screen.getByText('Load cash refund journal'));fireEvent.click(await screen.findByText('Next refunds'));await screen.findByText('Open refund r2');expect(vi.mocked(api).mock.calls[1][0]).toContain('page=1');expect(screen.queryByText('Open refund r1')).toBeNull();fireEvent.change(screen.getByLabelText('Refund journal reference'),{target:{value:'r1'}});expect(screen.queryByText('Open refund r2')).toBeNull();
});
it('restricts saved handover recovery to the exact return',async()=>{
 vi.mocked(api).mockResolvedValue(report);render(<RefundJournal {...props} requiredReturnId="other-return"/>);dates();fireEvent.click(screen.getByText('Load cash refund journal'));await screen.findByText('Open refund r1');expect(vi.mocked(api).mock.calls[0][0]).toContain('returnId=other-return');expect((screen.getByText('Open refund r1') as HTMLButtonElement).disabled).toBe(true);expect(props.onRecovered).not.toHaveBeenCalled();
});
it('rejects malformed reference/range without requesting records',async()=>{
 render(<RefundJournal {...props}/>);dates();fireEvent.change(screen.getByLabelText('Refund journal reference'),{target:{value:'%'}});fireEvent.click(screen.getByText('Load cash refund journal'));await screen.findByRole('alert');expect(api).not.toHaveBeenCalled();
});
it('clears denied refresh data and preserves caller recovery state',async()=>{
 vi.mocked(api).mockResolvedValueOnce(report).mockRejectedValueOnce(new ApiError(403));render(<RefundJournal {...props}/>);dates();fireEvent.click(screen.getByText('Load cash refund journal'));await screen.findByText('Open refund r1');fireEvent.click(screen.getByText('Load cash refund journal'));await screen.findByRole('alert');expect(screen.queryByText('Open refund r1')).toBeNull();expect(props.onRecovered).not.toHaveBeenCalled();
});
it('ignores a late detail response when another financial action disables the journal',async()=>{
 let finish!:(v:CashRefundView)=>void;vi.mocked(api).mockImplementation(async path=>path==='/refunds/r1'?new Promise(resolve=>{finish=resolve;}):report);const view=render(<RefundJournal {...props}/>);dates();fireEvent.click(screen.getByText('Load cash refund journal'));fireEvent.click(await screen.findByText('Open refund r1'));view.rerender(<RefundJournal {...props} disabled/>);await act(async()=>finish(full));expect(props.onRecovered).not.toHaveBeenCalled();
});
