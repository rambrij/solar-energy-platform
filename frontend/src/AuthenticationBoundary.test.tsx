import {cleanup,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import AuthenticationBoundary from './AuthenticationBoundary';
import {useSession} from './session';
vi.mock('./session',()=>({useSession:vi.fn(),restoreSession:vi.fn(),signIn:vi.fn(),signOut:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('does not mount financial workflows until authentication and permission are resolved',()=>{vi.mocked(useSession).mockReturnValue({ready:false,account:null});const view=render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);expect(screen.queryByText('Billing workflow')).toBeNull();vi.mocked(useSession).mockReturnValue({ready:true,account:{username:'buyer',tenantId:null,branchId:null,permissions:['CUSTOMER']}});view.rerender(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);expect(screen.getByRole('alert').textContent).toContain('does not have access');expect(screen.queryByText('Billing workflow')).toBeNull();});
it('accepts a permission predicate so platform operators with any PLATFORM_ grant reach the console, and vendor accounts do not',()=>{
 const operator=(permissions:string[])=>permissions.some(permission=>permission.startsWith('PLATFORM_'));
 vi.mocked(useSession).mockReturnValue({ready:true,account:{username:'pa-root',tenantId:null,branchId:null,permissions:['PLATFORM_AUDIT_VIEW']}});
 const view=render(<AuthenticationBoundary permission={operator} mode="platform"><p>Console</p></AuthenticationBoundary>);expect(screen.getByText('Console')).toBeTruthy();view.unmount();
 vi.mocked(useSession).mockReturnValue({ready:true,account:{username:'cashier',tenantId:1,branchId:1,permissions:['POS_VIEW','POS_SALE_CREATE']}});
 render(<AuthenticationBoundary permission={operator} mode="platform"><p>Console</p></AuthenticationBoundary>);expect(screen.queryByText('Console')).toBeNull();expect(screen.getByRole('alert').textContent).toContain('does not have access');
});
