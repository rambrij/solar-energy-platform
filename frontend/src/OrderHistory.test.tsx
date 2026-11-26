import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import {useSession} from './session';
import OrderHistory from './OrderHistory';
import { api } from './api';
import { marketplaceApi } from './marketplaceApi';
vi.mock('./api', () => ({ api: vi.fn() }));
vi.mock('./marketplaceApi', () => ({ marketplaceApi: vi.fn() }));
vi.mock('./session',()=>({useSession:vi.fn()}));
beforeEach(()=>{vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'cashier',tenantId:1,branchId:10,membershipId:'1:cashier',actorType:'VENDOR',permissions:['POS_VIEW','POS_RECEIPT_VIEW','POS_RETURN_CREATE'],availableActorTypes:['VENDOR']},error:null} as ReturnType<typeof useSession>);});
const order = { id: '12345678-abcd-4321-abcd-123456789000', vendorName: 'Solar Partner', channel: 'IN_STORE', status: 'PAID', paymentState: 'CONFIRMED', createdAt: '2026-11-13T09:00:00Z', expiresAt: null, total: 110, receiptNumber: 4, taxInvoice: true };
const page = { items: [order], page: 0, size: 10, totalElements: 11, totalPages: 2 };
afterEach(() => { cleanup(); vi.resetAllMocks(); });
it('opens a paid customer receipt directly from a cross-vendor order card',async()=>{
 vi.mocked(marketplaceApi).mockImplementation(async path=>path.endsWith('/receipt')?{orderId:order.id,seller:'Saved seller',outlet:'Saved outlet',issuedAt:order.createdAt,total:110,paymentMethod:'MOCK_CARD',copy:false,items:[],invoices:[]}:page);
 render(<OrderHistory mode="online"/>);fireEvent.click(screen.getByText('Load order history'));fireEvent.click(await screen.findByText('Open receipt'));await screen.findByText('Saved seller');expect(marketplaceApi).toHaveBeenCalledWith(`/api/v1/customer/orders/${order.id}/receipt`);expect(api).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Close purchase receipt'));expect(screen.queryByLabelText('Online order receipt')).toBeNull();
});
it('does not request protected history until explicitly loaded', () => {
  render(<OrderHistory mode="online"/>);
  expect(api).not.toHaveBeenCalled(); expect(marketplaceApi).not.toHaveBeenCalled();
  expect(screen.getByText('My orders')).toBeTruthy();
});
it('keeps pagination bound to applied filters rather than unsubmitted edits', async () => {
  vi.mocked(api).mockResolvedValue(page);
  render(<OrderHistory mode="pos"/>);
  fireEvent.change(screen.getByLabelText('Order status'), { target: { value: 'PAID' } });
  fireEvent.click(screen.getByText('Load order history'));
  await screen.findByText('Solar Partner');
  fireEvent.change(screen.getByLabelText('Order status'), { target: { value: 'CANCELLED' } });
  fireEvent.click(screen.getByText('Next orders'));
  await waitFor(() => expect(api).toHaveBeenLastCalledWith('/orders?status=PAID&reference=&page=1&size=10'));
});
it('loads customer snapshot details and never offers a POS receipt action', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => path.includes('/shipments') ? [] : path.includes('/orders/') ? { order: { ...order, channel: 'ONLINE', status: 'CANCELLED' }, items: [{ productId: 1, name: 'Original panel', quantity: 1, unitPrice: 100, tax: 10, total: 110 }], cashReceived: null, changeGiven: null, cancellationReason: 'Buying later', statusChangedAt: '2026-11-13T09:01:00Z' } : page);
  render(<OrderHistory mode="online"/>);
  fireEvent.click(screen.getByText('Load order history')); await screen.findByText('Payment: CONFIRMED'); fireEvent.click(await screen.findByText(`View order ${order.id}`));
  await screen.findByText('Original panel'); await screen.findByText('Cancellation reason: Buying later'); await screen.findByText('Payment state: CONFIRMED');
  expect(marketplaceApi).toHaveBeenCalledWith(`/api/v1/customer/orders/${order.id}`);
  expect(screen.queryByText('Open receipt 4')).toBeNull(); expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('Close order details')); expect(screen.queryByLabelText('Order details')).toBeNull();
});
it('recovers an existing paid receipt without creating a financial command', async () => {
  const receipt = { id: order.id, items: [], invoices: [] };
  vi.mocked(api).mockImplementation(async path => path.startsWith('/receipts/') ? receipt : page);
  const onReceipt = vi.fn(); render(<OrderHistory mode="pos" onReceipt={onReceipt}/>);
  fireEvent.click(screen.getByText('Load order history')); fireEvent.click(await screen.findByText('Open receipt 4'));
  await waitFor(() => expect(onReceipt).toHaveBeenCalledWith(receipt));
  expect(api).toHaveBeenCalledWith(`/receipts/${order.id}`);
});
it('rejects reversed dates locally and preserves the loaded page after a failed filter', async () => {
  vi.mocked(api).mockResolvedValueOnce(page).mockRejectedValueOnce(new Error('Denied'));
  render(<OrderHistory mode="pos"/>);
  fireEvent.click(screen.getByText('Load order history')); await screen.findByText('Solar Partner');
  fireEvent.change(screen.getByLabelText('From date (UTC)'), { target: { value: '2026-11-14' } });
  fireEvent.change(screen.getByLabelText('To date (UTC)'), { target: { value: '2026-11-13' } });
  fireEvent.click(screen.getByText('Apply history filters')); await screen.findByText('From date must not follow to date.'); expect(api).toHaveBeenCalledTimes(1);
  fireEvent.change(screen.getByLabelText('From date (UTC)'), { target: { value: '2026-11-12' } }); fireEvent.click(screen.getByText('Apply history filters'));
  await screen.findByRole('alert'); expect(screen.getByText('Solar Partner')).toBeTruthy();
});
it('shows an explicit empty result and disables both page controls', async () => {
  vi.mocked(marketplaceApi).mockResolvedValue({ ...page, items: [], totalElements: 0, totalPages: 0 });
  render(<OrderHistory mode="online"/>); fireEvent.click(screen.getByText('Load order history')); await screen.findByText('No orders found');
  expect((screen.getByText('Previous orders') as HTMLButtonElement).disabled).toBe(true); expect((screen.getByText('Next orders') as HTMLButtonElement).disabled).toBe(true);
});
it('reports receipt permission failures without discarding history', async () => {
  vi.mocked(api).mockResolvedValueOnce(page).mockRejectedValueOnce(new Error('Forbidden'));
  render(<OrderHistory mode="pos" onReceipt={vi.fn()}/>); fireEvent.click(screen.getByText('Load order history')); fireEvent.click(await screen.findByText('Open receipt 4'));
  await screen.findByText(/Receipt-view permission is required/); expect(screen.getByText('Solar Partner')).toBeTruthy();
});


it('hides return and receipt actions without their exact POS grants',async()=>{
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'cashier',tenantId:1,branchId:10,membershipId:'1:cashier',actorType:'VENDOR',permissions:['POS_VIEW'],availableActorTypes:['VENDOR']},error:null} as ReturnType<typeof useSession>);
 vi.mocked(api).mockResolvedValue(page);
 render(<OrderHistory mode="pos" onReceipt={vi.fn()} onReturn={vi.fn()}/>);
 fireEvent.click(screen.getByText('Load order history'));
 await screen.findByText('Solar Partner');
 expect(screen.queryByText('Start return 4')).toBeNull();
 expect(screen.queryByText('Open receipt 4')).toBeNull();
 expect(screen.getByText('View order '+order.id)).toBeTruthy();
});

it('loads customer-owned shipment events for an online order detail',async()=>{
 vi.mocked(marketplaceApi).mockImplementation(async path=>{
  if(path.includes('/shipments'))return [{id:9,carrier:'Solar Carrier',trackingReference:'TRK-9',trackingUrl:'https://carrier.example/TRK-9',createdAt:'2026-11-26T08:00:00Z',events:[{id:10,eventCode:'IN_TRANSIT',description:'Parcel moving',occurredAt:'2026-11-26T09:00:00Z'}]}];
  if(path.includes('/orders/'))return {order:{...order,channel:'ONLINE'},items:[],cashReceived:null,changeGiven:null,cancellationReason:null,statusChangedAt:order.createdAt};
  return page;
 });
 render(<OrderHistory mode="online"/>);fireEvent.click(screen.getByText('Load order history'));fireEvent.click(await screen.findByText('View order '+order.id));
 await screen.findByText('Solar Carrier');await screen.findByText(/Parcel moving/);
 expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/orders/'+order.id+'/shipments');
});

it('shows customer-safe return and cash-refund status for a verified in-store purchase',async()=>{
 const returnStatus={id:'return-1',state:'INSPECTED',requestedAt:'2026-11-01T08:00:00Z',reviewedAt:'2026-11-01T09:00:00Z',inspectedAt:'2026-11-01T10:00:00Z',cashRefundStatus:'PAID',cashRefundAmount:75,cashRefundedAt:'2026-11-01T11:00:00Z',items:[{name:'Saved solar panel',quantity:2}]};
 vi.mocked(marketplaceApi).mockImplementation(async path=>{
  if(path.endsWith('/returns'))return {orderId:order.id,returns:[returnStatus]};
  if(path.includes('/orders/'))return {order:{...order,channel:'IN_STORE',status:'PAID'},items:[],cashReceived:100,changeGiven:0,cancellationReason:null,statusChangedAt:order.createdAt};
  return page;
 });
 render(<OrderHistory mode="online"/>);
 fireEvent.click(screen.getByText('Load order history'));
 fireEvent.click(await screen.findByText('View order '+order.id));
 await screen.findByText('Return status: INSPECTED');
 await screen.findByText('Saved solar panel × 2');
 await screen.findByText(/Cash refund: Paid 75.00 on/);
 expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/orders/'+order.id+'/returns');
 expect(screen.getByLabelText('Return and refund status')).toBeTruthy();
});

it('shows an empty return state for a verified store purchase with no return record',async()=>{
 vi.mocked(marketplaceApi).mockImplementation(async path=>{
  if(path.endsWith('/returns'))return {orderId:order.id,returns:[]};
  if(path.includes('/orders/'))return {order:{...order,channel:'IN_STORE',status:'PAID'},items:[],cashReceived:100,changeGiven:0,cancellationReason:null,statusChangedAt:order.createdAt};
  return page;
 });
 render(<OrderHistory mode="online"/>);
 fireEvent.click(screen.getByText('Load order history'));
 fireEvent.click(await screen.findByText('View order '+order.id));
 await screen.findByText('No return has been recorded for this verified store purchase.');
});

it('does not request POS return status for an online order',async()=>{
 vi.mocked(marketplaceApi).mockImplementation(async path=>{
  if(path.endsWith('/shipments'))return [];
  if(path.includes('/orders/'))return {order:{...order,channel:'ONLINE',status:'PAID'},items:[],cashReceived:null,changeGiven:null,cancellationReason:null,statusChangedAt:order.createdAt};
  return page;
 });
 render(<OrderHistory mode="online"/>);
 fireEvent.click(screen.getByText('Load order history'));
 fireEvent.click(await screen.findByText('View order '+order.id));
 await screen.findByLabelText('Shipment tracking');
 expect(marketplaceApi).not.toHaveBeenCalledWith('/api/v1/customer/orders/'+order.id+'/returns');
});
