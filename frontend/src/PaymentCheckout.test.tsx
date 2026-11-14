import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import PaymentCheckout from './PaymentCheckout';
import {api} from './api';
import {marketplaceApi} from './marketplaceApi';
vi.mock('./api',()=>({api:vi.fn()}));vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const payment={orderId:'order-1',attemptId:null,method:null,state:'NOT_STARTED',amount:110,merchantAccount:null,mockEnabled:true,message:'Pre-production simulation'};
it('keeps payments unavailable when the vendor adapter is disabled',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue({...payment,mockEnabled:false,message:'Provider not configured'});render(<PaymentCheckout mode="online" orderId="order-1" onComplete={vi.fn()} onBusyChange={vi.fn()}/>);
 expect(marketplaceApi).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Check payment options / status'));await screen.findByText('Provider not configured');expect(screen.queryByText('Start test UPI payment')).toBeNull();
});
it('persists initiation before explicit confirmation and refreshes the completed order',async()=>{
 const complete=vi.fn();vi.mocked(api).mockImplementation(async(path,body)=>path.endsWith('mock-confirm')?{...payment,state:'CONFIRMED',attemptId:'attempt-1',method:'UPI'}:body?{...payment,state:'PENDING',attemptId:'attempt-1',method:'UPI',merchantAccount:'mock-vendor-1'}:payment);
 render(<PaymentCheckout mode="pos" orderId="order-1" onComplete={complete} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Check payment options / status'));await screen.findByText('Start test UPI payment');fireEvent.click(screen.getByText('Start test UPI payment'));await screen.findByText('Simulate successful UPI payment');expect(complete).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Simulate successful UPI payment'));await waitFor(()=>expect(complete).toHaveBeenCalledOnce());expect(api).toHaveBeenLastCalledWith('/drafts/order-1/payment/mock-confirm',{attemptId:'attempt-1'});
});
it('requires status recovery after an uncertain initiation and never claims completion',async()=>{
 const complete=vi.fn();vi.mocked(marketplaceApi).mockImplementation(async(_path,method)=>{if(method==='POST')throw Error('Connection lost');return payment;});render(<PaymentCheckout mode="online" orderId="order-1" onComplete={complete} onBusyChange={vi.fn()}/>);
 fireEvent.click(screen.getByText('Check payment options / status'));await screen.findByText('Start test card payment');fireEvent.click(screen.getByText('Start test card payment'));await screen.findByRole('alert');expect(screen.queryByText('Start test card payment')).toBeNull();expect(complete).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Check payment options / status'));await screen.findByText('Start test card payment');
});
it('recovers an existing payment without starting a second attempt',async()=>{
 vi.mocked(api).mockResolvedValue({...payment,state:'PENDING',attemptId:'saved',method:'CARD'});render(<PaymentCheckout mode="pos" orderId="order-1" onComplete={vi.fn()} onBusyChange={vi.fn()}/>);fireEvent.click(screen.getByText('Check payment options / status'));await screen.findByText('Simulate successful CARD payment');expect(screen.queryByText('Start test UPI payment')).toBeNull();expect(api).toHaveBeenCalledTimes(1);
});
