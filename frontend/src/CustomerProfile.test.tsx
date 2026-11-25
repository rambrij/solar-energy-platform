import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CustomerProfile from './CustomerProfile';
import {marketplaceApi} from './marketplaceApi';
vi.mock('./marketplaceApi',async original=>({...await original<typeof import('./marketplaceApi')>(),marketplaceApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('loads the global profile and saves only the editable display name',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValueOnce({id:42,email:'buyer@example.test',displayName:'Solar Buyer'}).mockResolvedValueOnce({id:42,email:'buyer@example.test',displayName:'New name'});
 render(<CustomerProfile/>);
 const name=await screen.findByLabelText('Display name');expect((screen.getByLabelText('Email address') as HTMLInputElement).readOnly).toBe(true);
 fireEvent.change(name,{target:{value:'New name'}});fireEvent.click(screen.getByText('Save profile'));
 await waitFor(()=>expect(marketplaceApi).toHaveBeenLastCalledWith('/api/v1/customer/profile','PUT',{displayName:'New name'}));
 await screen.findByText('Profile updated.');
});
it('does not send a mutation when profile loading fails',async()=>{
 vi.mocked(marketplaceApi).mockRejectedValue(new Error('forbidden'));
 render(<CustomerProfile/>);await screen.findByRole('alert');
 expect(marketplaceApi).toHaveBeenCalledTimes(1);
});
