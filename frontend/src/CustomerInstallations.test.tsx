import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CustomerInstallations from './CustomerInstallations';
import {marketplaceApi} from './marketplaceApi';
vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('shows customer-owned installation status and only customer-visible timeline',async()=>{
 const job={id:4,orderId:'owned-order',siteAddress:'Solar site, Mumbai',scheduledAt:'2026-12-12T08:00:00Z',status:'IN_PROGRESS',assignedTo:'engineer@vendor.test',createdAt:'2026-11-27T08:00:00Z',updatedAt:'2026-11-28T08:00:00Z',completedAt:null,version:1};
 vi.mocked(marketplaceApi).mockImplementation(async(path)=>{
  if(path==='/api/v1/customer/installations')return [job];
  if(path==='/api/v1/customer/installations/4')return {job,events:[{id:1,type:'STATUS_CHANGED',status:'IN_PROGRESS',note:'Installation has started',occurredAt:'2026-11-28T08:00:00Z'}]};
  return [];
 });
 render(<CustomerInstallations/>);
 await screen.findByText('Installation #4');
 const timeline=await screen.findAllByRole('listitem');
 expect(timeline.some(item=>item.textContent?.includes('Installation has started'))).toBe(true);
 expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/installations/4');
});

it('shows an empty state when there are no installation jobs',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue([]);
 render(<CustomerInstallations/>);
 expect(await screen.findByText(/No installation jobs are currently registered/)).toBeTruthy();
});
