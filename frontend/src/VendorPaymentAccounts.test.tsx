import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import VendorPaymentAccounts from './VendorPaymentAccounts';
const {apiMock,sessionMock}=vi.hoisted(()=>({apiMock:vi.fn(),sessionMock:vi.fn()}));
vi.mock('./api',()=>({vendorApi:apiMock}));
vi.mock('./session',()=>({useSession:sessionMock}));
const account={id:10,providerKey:'RAZORPAY',maskedMerchantAccountReference:'••••7890',status:'PENDING_VERIFICATION',submittedAt:'2026-11-27T10:00:00Z',updatedAt:'2026-11-27T10:00:00Z',verifiedAt:null,rejectionReason:null,version:0};
beforeEach(()=>{
 sessionMock.mockReturnValue({account:{username:'vendor.admin@example.test',tenantId:71,permissions:['TENANT_PROFILE_VIEW','TENANT_PROFILE_UPDATE']}});
 apiMock.mockImplementation((path:string,method='GET')=>{
  if(path==='/payment-accounts'&&method==='GET')return Promise.resolve([account]);
  if(path==='/payment-accounts'&&method==='PUT')return Promise.resolve({...account,version:1});
  return Promise.resolve([]);
 });
});
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('shows only masked provider account reference and verification status',async()=>{
 render(<VendorPaymentAccounts/>);
 expect(await screen.findByText('RAZORPAY')).toBeTruthy();
 expect(screen.getByText('Account reference: ••••7890')).toBeTruthy();
 expect(screen.getByText('Status: Pending Verification')).toBeTruthy();
 expect(screen.getByText(/Online payments remain unavailable until a real provider verifies/)).toBeTruthy();
 expect(screen.queryByRole('button',{name:/mark verified/i})).toBeNull();
});
it('submits provider reference with optimistic version and an audit reason',async()=>{
 render(<VendorPaymentAccounts/>);
 await screen.findByText('RAZORPAY');
 fireEvent.change(screen.getByLabelText('Payment provider key'),{target:{value:'razorpay'}});
 fireEvent.change(screen.getByLabelText('Merchant account reference'),{target:{value:'acc_12345678'}});
 fireEvent.change(screen.getByLabelText('Reason for submission'),{target:{value:'Vendor provider onboarding submitted'}});
 fireEvent.click(screen.getByRole('button',{name:'Submit merchant account'}));
 await waitFor(()=>expect(apiMock).toHaveBeenCalledWith('/payment-accounts','PUT',{
  providerKey:'RAZORPAY',merchantAccountReference:'acc_12345678',expectedVersion:0,
  reason:'Vendor provider onboarding submitted'
 }));
 expect(await screen.findByText(/remains pending until the selected provider verifies/i)).toBeTruthy();
});
