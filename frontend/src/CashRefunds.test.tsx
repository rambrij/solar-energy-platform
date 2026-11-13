import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,expect,it,vi } from 'vitest';
import CashRefunds,{refundStorageKey,type CashRefundView} from './CashRefunds';
import CashRefundReceipt from './CashRefundReceipt';
import { api,ApiError } from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const quote={returnId:'return-1',orderId:'order-1',originalReceipt:'1/7',sessionId:'shift-1',amount:110,drawerCash:200,enoughCash:true,quoteHash:'a'.repeat(64),items:[{productId:1,name:'Panel',quantity:1,net:100,tax:10,amount:110}],message:'Original discounted sale values'};
const paid:CashRefundView={...quote,id:'refund-1',branchId:'1',cashier:'manager',reason:'Goods returned',paidAt:'2026-11-13T09:00:00Z',method:'CASH',status:'PAID',copy:false};
const props={session:'shift-1',sessionState:'OPEN',disabled:false,onPendingChange:vi.fn(),onBusyChange:vi.fn(),onRecorded:vi.fn()};
afterEach(()=>{cleanup();vi.resetAllMocks();sessionStorage.clear();});
async function review(){fireEvent.change(screen.getByLabelText('Inspected return ID'),{target:{value:'return-1'}});fireEvent.click(screen.getByText('Calculate cash refund'));await screen.findByText('Review original receipt 1/7');fireEvent.change(screen.getByLabelText('Cash refund reason'),{target:{value:'Goods returned'}});}
it('requires exact server quote and independent cash confirmation before posting',async()=>{
 vi.mocked(api).mockImplementation(async (_path,body)=>body?paid:quote);render(<CashRefunds {...props}/>);expect(api).not.toHaveBeenCalled();await review();
 expect((screen.getByText('Approve & record cash handover') as HTMLButtonElement).disabled).toBe(true);
 fireEvent.click(screen.getByLabelText(/I independently approve/));fireEvent.click(screen.getByText('Approve & record cash handover'));
 await waitFor(()=>expect(props.onRecorded).toHaveBeenCalledWith(paid));
 expect(api).toHaveBeenCalledWith('/refunds',{returnId:'return-1',sessionId:'shift-1',reason:'Goods returned',quoteHash:quote.quoteHash,confirmation:'CASH_HANDED_TO_BUYER'},expect.any(String));expect(sessionStorage.getItem(refundStorageKey)).toBeNull();
});
it('preserves exact retry through uncertain response and closed session without offering discard',async()=>{
 vi.mocked(api).mockImplementation(async (_path,body)=>{if(body)throw new Error('Disconnected');return quote;});const view=render(<CashRefunds {...props}/>);await review();fireEvent.click(screen.getByLabelText(/I independently approve/));fireEvent.click(screen.getByText('Approve & record cash handover'));await screen.findByText('Retry same cash refund');
 const attempt=JSON.parse(sessionStorage.getItem(refundStorageKey)!);expect(attempt.safeToDiscard).toBe(false);view.unmount();
 vi.mocked(api).mockRejectedValue(new ApiError(403));render(<CashRefunds {...props} session='other-shift' sessionState='CLOSED' disabled/>);fireEvent.click(screen.getByText('Retry same cash refund'));
 await waitFor(()=>expect(api).toHaveBeenLastCalledWith('/refunds',attempt.body,attempt.key));await screen.findByRole('alert');expect(screen.queryByText('Discard rejected refund attempt')).toBeNull();expect(JSON.parse(sessionStorage.getItem(refundStorageKey)!).safeToDiscard).toBe(false);
});
it('requires physical no-cash reconciliation to discard a definite first rejection',async()=>{
 vi.mocked(api).mockImplementation(async (_path,body)=>{if(body)throw new ApiError(409);return quote;});render(<CashRefunds {...props}/>);await review();fireEvent.click(screen.getByLabelText(/I independently approve/));fireEvent.click(screen.getByText('Approve & record cash handover'));
 await screen.findByText('Discard rejected refund attempt');expect((screen.getByText('Discard rejected refund attempt') as HTMLButtonElement).disabled).toBe(true);fireEvent.click(screen.getByLabelText(/I reconciled the rejection/));fireEvent.click(screen.getByText('Discard rejected refund attempt'));expect(sessionStorage.getItem(refundStorageKey)).toBeNull();
});
it('recovers paid refund and requires cash reconciliation acknowledgement to unlock',async()=>{
 sessionStorage.setItem(refundStorageKey,JSON.stringify({key:'saved-key',body:{returnId:'return-1',sessionId:'shift-1',reason:'Goods returned',quoteHash:quote.quoteHash,confirmation:'CASH_HANDED_TO_BUYER'},safeToDiscard:false}));vi.mocked(api).mockResolvedValue(paid);render(<CashRefunds {...props} disabled/>);fireEvent.click(screen.getByText('Recover paid refund'));await screen.findByText(/Recorded PAID refund/);expect(sessionStorage.getItem(refundStorageKey)).not.toBeNull();fireEvent.click(screen.getByText('Acknowledge recorded refund & cash reconciliation'));expect(sessionStorage.getItem(refundStorageKey)).toBeNull();expect(props.onRecorded).toHaveBeenCalledWith(paid);
});
it('does not offer payout from insufficient drawer funds',async()=>{
 vi.mocked(api).mockResolvedValue({...quote,enoughCash:false,drawerCash:50});render(<CashRefunds {...props}/>);await review();fireEvent.click(screen.getByLabelText(/I independently approve/));expect((screen.getByText('Approve & record cash handover') as HTMLButtonElement).disabled).toBe(true);
});
it('audits refund copy before printing only the refund document',async()=>{
 const print=vi.spyOn(window,'print').mockImplementation(()=>{expect(document.body.dataset.printDocument).toBe('refund');});vi.mocked(api).mockResolvedValue({...paid,copy:true});let current=paid;
 const view=render(<CashRefundReceipt value={current} disabled={false} onCopy={value=>{current=value;view.rerender(<CashRefundReceipt value={current} disabled={false} onCopy={()=>{}}/>);}}/>);fireEvent.click(screen.getByText('Print refund copy'));await waitFor(()=>expect(print).toHaveBeenCalledTimes(1));expect(api).toHaveBeenCalledWith('/refunds/refund-1/copy',{});expect(document.body.dataset.printDocument).toBeUndefined();print.mockRestore();
});

it('keeps unreadable saved handover locked instead of starting a replacement payout',async()=>{
 sessionStorage.setItem(refundStorageKey,'{broken');render(<CashRefunds {...props}/>);expect(screen.getByRole('alert').textContent).toContain('cannot be read');expect(props.onPendingChange).toHaveBeenCalledWith(true);expect((screen.getByText('Calculate cash refund') as HTMLButtonElement).disabled).toBe(true);expect(api).not.toHaveBeenCalled();
});
