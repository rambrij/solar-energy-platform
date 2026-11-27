import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CustomerServiceRequests from './CustomerServiceRequests';
import {marketplaceApi} from './marketplaceApi';
vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('submits a service request for an eligible paid customer order and shows it in history',async()=>{
 const row={id:3,orderId:'owned-order',installationId:8,subject:'Inverter issue',description:'Intermittent shutdown',priority:'HIGH',status:'OPEN',createdAt:'2026-11-27T10:00:00Z',updatedAt:'2026-11-27T10:00:00Z',resolvedAt:null};
 vi.mocked(marketplaceApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/api/v1/customer/service-requests/orders')return [{orderId:'owned-order',sellerName:'Vendor A',orderedAt:null}];
  if(path==='/api/v1/customer/service-requests'&&method==='GET')return [row];
  if(path==='/api/v1/customer/service-requests'&&method==='POST')return row;
  if(path==='/api/v1/customer/service-requests/3')return {serviceRequest:row,events:[{id:1,eventType:'CREATED',toStatus:'OPEN',note:'Service request submitted',occurredAt:row.createdAt}]};
  return [];
 });
 render(<CustomerServiceRequests/>);
 await screen.findByText('Case #3 · Inverter issue');
 fireEvent.change(screen.getByLabelText('Order'),{target:{value:'owned-order'}});
 fireEvent.change(screen.getByLabelText('Subject'),{target:{value:'Inverter issue'}});
 fireEvent.change(screen.getByLabelText('Describe the issue'),{target:{value:'Intermittent shutdown'}});
 fireEvent.change(screen.getByLabelText('Priority'),{target:{value:'HIGH'}});
 fireEvent.click(screen.getByRole('button',{name:'Submit support request'}));
 await waitFor(()=>expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/service-requests','POST',{
  orderId:'owned-order',subject:'Inverter issue',description:'Intermittent shutdown',priority:'HIGH'
 }));
 expect(await screen.findByText('Your service request has been submitted.')).toBeTruthy();
 fireEvent.click(screen.getByRole('button',{name:'View updates'}));
 expect(await screen.findByText(/Service request submitted/)).toBeTruthy();
});

it('shows an empty state when no eligible paid orders or cases exist',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue([]);
 render(<CustomerServiceRequests/>);
 expect(await screen.findByText(/No eligible paid online orders/)).toBeTruthy();
 expect(screen.getByText(/You have not submitted any service requests/)).toBeTruthy();
});
