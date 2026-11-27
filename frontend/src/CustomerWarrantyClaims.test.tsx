import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CustomerWarrantyClaims from './CustomerWarrantyClaims';
import {marketplaceApi,marketplaceUpload} from './marketplaceApi';
vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn(),marketplaceUpload:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('submits a claim for active customer warranty coverage',async()=>{
 const warranty={id:4,orderId:'own-order',productName:'Solar Panel',coverageStartDate:'2020-01-01',coverageEndDate:'2035-01-01',termsSummary:'Terms',vendorName:'Vendor A'};
 vi.mocked(marketplaceApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/api/v1/customer/warranties')return [warranty];
  if(path==='/api/v1/customer/warranty-claims'&&method==='GET')return [];
  if(path==='/api/v1/customer/warranty-claims'&&method==='POST')return {id:9};
  return [];
 });
 render(<CustomerWarrantyClaims/>);
 fireEvent.change(await screen.findByLabelText('Warranty'),{target:{value:'4'}});
 fireEvent.change(screen.getByLabelText('Issue title'),{target:{value:'Panel fault'}});
 fireEvent.change(screen.getByLabelText('Describe the issue'),{target:{value:'Panel stopped producing'}});
 fireEvent.click(screen.getByRole('button',{name:'Submit claim'}));
 await waitFor(()=>expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/warranty-claims','POST',{
  warrantyId:4,subject:'Panel fault',description:'Panel stopped producing'
 }));
 expect(await screen.findByText('Warranty claim submitted.')).toBeTruthy();
});
it('does not offer claim submission when there is no active registered coverage',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue([]);
 render(<CustomerWarrantyClaims/>);
 expect(await screen.findByText('No active registered warranties are eligible for a new claim.')).toBeTruthy();
});
