import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorServiceRequests from './VendorServiceRequests';
import {vendorApi} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn()}));
vi.mock('./session',()=>({useSession:()=>({account:{username:'manager@vendor.test',tenantId:7,permissions:['SERVICE_VIEW','SERVICE_CREATE','SERVICE_ASSIGN','SERVICE_UPDATE','SERVICE_RESOLVE']}})}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('creates and assigns a tenant service request and records status progress',async()=>{
 const row={id:12,orderId:'order-12',installationId:3,subject:'Panel inspection',description:'Customer reports reduced output',priority:'HIGH',status:'OPEN',assignedTo:null,createdAt:'2026-11-27T10:00:00Z',updatedAt:'2026-11-27T10:00:00Z',resolvedAt:null,version:0};
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/service-requests/orders')return [{orderId:'order-12',sellerName:'Customer order',orderedAt:null}];
  if(path==='/service-requests/assignees')return ['engineer@vendor.test'];
  if(path==='/service-requests'&&method==='GET')return [row];
  if(path==='/service-requests'&&method==='POST')return row;
  if(path==='/service-requests/12/assignment')return row;
  if(path==='/service-requests/12/status')return row;
  if(path==='/service-requests/12')return {serviceRequest:row,events:[]};
  return [];
 });
 render(<VendorServiceRequests/>);
 await screen.findByText('Case #12 · Panel inspection');
 fireEvent.change(screen.getByLabelText('Customer order'),{target:{value:'order-12'}});
 fireEvent.change(screen.getByLabelText('Subject'),{target:{value:'Panel inspection'}});
 fireEvent.change(screen.getByLabelText('Issue details'),{target:{value:'Customer reports reduced output'}});
 fireEvent.change(screen.getByLabelText('Priority'),{target:{value:'HIGH'}});
 fireEvent.click(screen.getByRole('button',{name:'Create service request'}));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/service-requests','POST',{
  orderId:'order-12',subject:'Panel inspection',description:'Customer reports reduced output',priority:'HIGH'
 }));
 expect(await screen.findByText('Service request created.')).toBeTruthy();
});

it('shows no-request state when the vendor has no assigned or managed cases',async()=>{
 vi.mocked(vendorApi).mockResolvedValue([]);
 render(<VendorServiceRequests/>);
 expect(await screen.findByText(/No service requests are assigned to or managed/)).toBeTruthy();
});
