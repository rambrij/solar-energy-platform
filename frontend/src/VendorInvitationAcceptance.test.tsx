import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import {authPost} from './api';
import VendorInvitationAcceptance from './VendorInvitationAcceptance';

vi.mock('./api',()=>({authPost:vi.fn(),ApiError:class extends Error{}}));
afterEach(()=>{cleanup();vi.resetAllMocks();window.history.replaceState({},'','/');});

it('accepts a pasted invitation token from the clean activation URL',async()=>{
 window.history.replaceState({},'','/vendor/invitation');
 vi.mocked(authPost).mockResolvedValue({});
 render(<VendorInvitationAcceptance/>);
 fireEvent.change(screen.getByLabelText('Invitation token'),{target:{value:'  one-time-token  '}});
 fireEvent.change(screen.getByLabelText('New password'),{target:{value:'Long-demo-password!'}});
 fireEvent.change(screen.getByLabelText('Confirm password'),{target:{value:'Long-demo-password!'}});
 fireEvent.click(screen.getByRole('button',{name:'Activate account'}));
 expect(await screen.findByRole('status')).toBeTruthy();
 expect(authPost).toHaveBeenCalledWith('/invitations/accept',{token:'one-time-token',password:'Long-demo-password!'});
 expect(window.location.search).toBe('');
});
