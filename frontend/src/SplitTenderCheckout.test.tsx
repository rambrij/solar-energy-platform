import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import SplitTenderCheckout from './SplitTenderCheckout';
import {api} from './api';
vi.mock('./api',()=>({api:vi.fn(),ApiError:class extends Error{constructor(public status:number){super('Rejected');}}}));
vi.mock('./session',()=>({useSession:()=>({ready:true,account:{username:'cashier',tenantId:1,branchId:10,actorType:'VENDOR',permissions:['POS_SALE_CREATE','POS_TENDER_CASH_RECORD','POS_TENDER_EXTERNAL_RECORD']}})}));
afterEach(()=>{cleanup();sessionStorage.clear();vi.resetAllMocks();});
const balance={orderId:'sale-1',orderStatus:'DRAFT',total:110,confirmed:0,remaining:110,splitEnabled:true,tenders:[]};
it('records partial cash with change and keeps sale unpaid',async()=>{
 const complete=vi.fn();vi.mocked(api).mockImplementation(async(_path,body)=>body?{...balance,confirmed:40,remaining:70,tenders:[{attemptId:'cash-1',method:'CASH',state:'CONFIRMED',amount:40}]}:balance);
 render(<SplitTenderCheckout orderId="sale-1" onComplete={complete} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Load tender balance'));await screen.findByText('DRAFT · Paid 0.00 · Remaining 110.00');
 fireEvent.change(screen.getByLabelText('Amount applied'),{target:{value:'40'}});fireEvent.change(screen.getByLabelText('Tender cash received'),{target:{value:'50'}});fireEvent.click(screen.getByText('Record received cash'));
 await screen.findByText('DRAFT · Paid 40.00 · Remaining 70.00');expect(complete).not.toHaveBeenCalled();expect(api).toHaveBeenLastCalledWith('/sales/sale-1/tenders',{method:'CASH',appliedAmount:'40',cashReceived:'50'},expect.any(String));
});
it('blocks another charge while external tender outcome is unknown',async()=>{
 vi.mocked(api).mockResolvedValue({...balance,tenders:[{attemptId:'upi-1',method:'UPI',state:'UNKNOWN_RECONCILIATION_REQUIRED',amount:110}]});render(<SplitTenderCheckout orderId="sale-1" onComplete={vi.fn()} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Load tender balance'));await screen.findByRole('alert');expect(screen.getByLabelText('Tender method').matches(':disabled')).toBe(true);expect(api).toHaveBeenCalledTimes(1);
});
it('uncertain tender retry retains the original operation key and body',async()=>{
 vi.mocked(api).mockResolvedValueOnce(balance).mockRejectedValueOnce(Error('Disconnected')).mockResolvedValueOnce({...balance,orderStatus:'PAID',confirmed:110,remaining:0});const complete=vi.fn();render(<SplitTenderCheckout orderId="sale-1" onComplete={complete} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Load tender balance'));await screen.findByText('DRAFT · Paid 0.00 · Remaining 110.00');fireEvent.change(screen.getByLabelText('Amount applied'),{target:{value:'110'}});fireEvent.change(screen.getByLabelText('Tender cash received'),{target:{value:'110'}});fireEvent.click(screen.getByText('Record received cash'));fireEvent.click(await screen.findByText('Retry same tender'));await waitFor(()=>expect(complete).toHaveBeenCalledOnce());expect(vi.mocked(api).mock.calls[1]).toEqual(vi.mocked(api).mock.calls[2]);
});

it('allows only the remaining collection on an expired bill with confirmed funds',async()=>{
 vi.mocked(api).mockResolvedValue({...balance,confirmed:40,remaining:70,tenders:[{attemptId:'cash',method:'CASH',state:'CONFIRMED',amount:40}]});
 render(<SplitTenderCheckout orderId="sale-1" reservationExpired onComplete={vi.fn()} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Load tender balance'));await screen.findByText(/confirmed funds retain this bill/);expect(screen.getByLabelText('Tender method').matches(':disabled')).toBe(false);
});
it('keeps an expired unpaid bill disabled after checking the saved balance',async()=>{
 vi.mocked(api).mockResolvedValue(balance);render(<SplitTenderCheckout orderId="sale-1" reservationExpired onComplete={vi.fn()} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Load tender balance'));await screen.findByLabelText('Tender method');expect(screen.getByLabelText('Tender method').matches(':disabled')).toBe(true);
});
