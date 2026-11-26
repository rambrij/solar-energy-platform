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

it('requires confirmation before revoking other sessions and keeps the current device signed in',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValueOnce({id:42,email:'buyer@example.test',displayName:'Solar Buyer'}).mockResolvedValueOnce({revoked:true});
 render(<CustomerProfile/>);await screen.findByLabelText('Display name');
 fireEvent.click(screen.getByText('Sign out other devices'));
 expect(screen.getByRole('dialog').textContent).toContain('current device remains active');
 expect(marketplaceApi).toHaveBeenCalledTimes(1);
 fireEvent.click(screen.getByText('Keep other sessions'));expect(screen.queryByRole('dialog')).toBeNull();
 fireEvent.click(screen.getByText('Sign out other devices'));fireEvent.click(screen.getByText('Confirm sign out'));
 await waitFor(()=>expect(marketplaceApi).toHaveBeenLastCalledWith('/api/v1/auth/sessions/revoke-others','POST',{}));
 await screen.findByText('Other active sessions were signed out. This device remains signed in.');
});
