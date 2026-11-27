import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import InstallationManagement from './InstallationManagement';
import {vendorApi} from './api';
import {useSession} from './session';

vi.mock('./api',()=>({vendorApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('creates a paid-order installation and records engineer progress',async()=>{
 vi.mocked(useSession).mockReturnValue({account:{username:'manager@vendor.test',tenantId:7,permissions:['INSTALLATION_ASSIGN','INSTALLATION_VIEW','INSTALLATION_UPDATE','INSTALLATION_COMMISSION']}} as any);
 let jobs:any[]=[];
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET',body?:any)=>{
  if(path==='/installations'&&method==='POST'){
   jobs=[{id:1,orderId:body.orderId,siteAddress:body.siteAddress,scheduledAt:body.scheduledAt,status:'ASSIGNED',assignedTo:body.assignedTo,createdAt:body.scheduledAt,updatedAt:body.scheduledAt,completedAt:null,version:0}];
   return jobs[0];
  }
  if(path==='/installations')return jobs;
  if(path==='/installations/orders')return [{orderId:'paid-order-1',orderedAt:'2026-11-26T08:00:00Z',total:100000}];
  if(path==='/installations/engineers')return ['engineer@vendor.test'];
  if(path==='/installations/1'&&method==='GET')return {job:jobs[0],events:[]};
  if(path==='/installations/1/status'&&method==='PATCH'){
   jobs=[{...jobs[0],status:body.status,version:1,completedAt:body.status==='COMPLETED'?body.note:null}];return jobs[0];
  }
  return [];
 });
 render(<InstallationManagement/>);
 fireEvent.change(await screen.findByLabelText('Paid order'),{target:{value:'paid-order-1'}});
 fireEvent.change(screen.getByLabelText('Installation site address'),{target:{value:'Plot 10, Mumbai, Maharashtra'}});
 fireEvent.change(screen.getByLabelText('Scheduled date and time'),{target:{value:'2026-12-10T10:00'}});
 fireEvent.change(screen.getByLabelText('Assigned engineer'),{target:{value:'engineer@vendor.test'}});
 fireEvent.change(screen.getByLabelText('Reason for creating job'),{target:{value:'Customer confirmed site visit'}});
 fireEvent.click(screen.getByRole('button',{name:'Create installation job'}));
 await screen.findByText(/Installation #1/);
 expect(vendorApi).toHaveBeenCalledWith('/installations','POST',expect.objectContaining({
  orderId:'paid-order-1',siteAddress:'Plot 10, Mumbai, Maharashtra',assignedTo:'engineer@vendor.test'
 }));
 fireEvent.change(screen.getByLabelText('Customer-visible progress note'),{target:{value:'Mounting structure installation started'}});
 fireEvent.click(screen.getByRole('button',{name:'Start installation'}));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/installations/1/status','PATCH',
  expect.objectContaining({expectedVersion:0,status:'IN_PROGRESS',note:'Mounting structure installation started'})));
});

it('does not expose create actions to an installation-view-only engineer',async()=>{
 vi.mocked(useSession).mockReturnValue({account:{username:'engineer@vendor.test',tenantId:7,permissions:['INSTALLATION_VIEW']}} as any);
 vi.mocked(vendorApi).mockResolvedValue([]);
 render(<InstallationManagement/>);
 await screen.findByText('No installation jobs are assigned to your account.');
 expect(screen.queryByRole('heading',{name:'Paid orders awaiting installation'})).toBeNull();
 expect(vendorApi).toHaveBeenCalledWith('/installations');
});
