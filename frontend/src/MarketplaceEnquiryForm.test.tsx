import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import MarketplaceEnquiryForm from './MarketplaceEnquiryForm';
import {marketplaceApi} from './marketplaceApi';
const {sessionMock}=vi.hoisted(()=>({sessionMock:vi.fn()}));
vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn()}));
vi.mock('./session',()=>({useSession:sessionMock}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('requires explicit consent and sends a product enquiry to the selected vendor',async()=>{
 sessionMock.mockReturnValue({account:null});
 vi.mocked(marketplaceApi).mockResolvedValue({id:44,status:'NEW'});
 render(<MarketplaceEnquiryForm slug="vendor-a" productId={7} productName="Solar panel"/>);
 fireEvent.click(screen.getByText('Send a product enquiry'));
 fireEvent.change(screen.getByLabelText('Your name'),{target:{value:'A Customer'}});
 fireEvent.change(screen.getByLabelText('Email address'),{target:{value:'customer@example.test'}});
 fireEvent.change(screen.getByLabelText('Your question'),{target:{value:'Please share installation details'}});
 const submit=screen.getByText('Send enquiry') as HTMLButtonElement;
 expect(submit.disabled).toBe(true);
 fireEvent.click(screen.getByLabelText(/I agree to share my contact details/));
 expect(submit.disabled).toBe(false);
 fireEvent.click(submit);
 await waitFor(()=>expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/marketplace/vendors/vendor-a/enquiries','POST',{
  contactName:'A Customer',contactEmail:'customer@example.test',contactPhone:'',message:'Please share installation details',productId:7,consent:true,linkToAccount:false
 }));
 expect(await screen.findByText('Your enquiry has been sent to this vendor.')).toBeTruthy();
});
it('offers account linking only in customer actor context',()=>{
 sessionMock.mockReturnValue({account:{actorType:'CUSTOMER'}});
 render(<MarketplaceEnquiryForm slug="vendor-a" productId={7} productName="Solar panel"/>);
 fireEvent.click(screen.getByText('Send a product enquiry'));
 expect(screen.getByLabelText(/Link this enquiry to my customer account/)).toBeTruthy();
});
