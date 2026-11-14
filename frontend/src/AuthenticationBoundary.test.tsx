import {cleanup,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import AuthenticationBoundary from './AuthenticationBoundary';
import {useSession} from './session';
vi.mock('./session',()=>({useSession:vi.fn(),restoreSession:vi.fn(),signIn:vi.fn(),signOut:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('does not mount financial workflows until authentication and permission are resolved',()=>{vi.mocked(useSession).mockReturnValue({ready:false,account:null});const view=render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);expect(screen.queryByText('Billing workflow')).toBeNull();vi.mocked(useSession).mockReturnValue({ready:true,account:{username:'buyer',tenantId:null,branchId:null,permissions:['CUSTOMER']}});view.rerender(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);expect(screen.getByRole('alert').textContent).toContain('does not have access');expect(screen.queryByText('Billing workflow')).toBeNull();});
