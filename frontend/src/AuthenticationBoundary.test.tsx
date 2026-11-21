import {cleanup,render,screen} from '@testing-library/react';
import {useState} from 'react';
import {afterEach,expect,it,vi} from 'vitest';
import AuthenticationBoundary from './AuthenticationBoundary';
import {useSession} from './session';

vi.mock('./session',()=>({useSession:vi.fn(),restoreSession:vi.fn()}));
vi.mock('./Shell',()=>({default:({children}:{children:import('react').ReactNode})=><div>{children}</div>}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('does not mount protected work before a secure session is restored',()=>{
 vi.mocked(useSession).mockReturnValue({ready:false,account:null});
 render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);
 expect(screen.queryByText('Billing workflow')).toBeNull();
});

it('denies a Customer context access to the Vendor POS workspace',()=>{
 vi.mocked(useSession).mockReturnValue({ready:true,account:{
  username:'buyer',tenantId:null,branchId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER',availableActorTypes:['CUSTOMER','VENDOR']
 }});
 render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);
 expect(screen.getByRole('alert').textContent).toContain('does not have access');
 expect(screen.queryByText('Billing workflow')).toBeNull();
});

it('clears page-local state when actor or tenant changes',()=>{
 vi.mocked(useSession).mockReturnValue({ready:true,account:{
  username:'dual',tenantId:null,branchId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER',availableActorTypes:['CUSTOMER','VENDOR']
 }});
 const view=render(<AuthenticationBoundary permission="CUSTOMER" mode="online"><ActorScopedState/></AuthenticationBoundary>);
 expect(screen.getByText('1')).toBeTruthy();
 vi.mocked(useSession).mockReturnValue({ready:true,account:{
  username:'dual',tenantId:55,branchId:7,permissions:['PRODUCT_VIEW'],actorType:'VENDOR',availableActorTypes:['CUSTOMER','VENDOR']
 }});
 view.rerender(<AuthenticationBoundary permission="PRODUCT_VIEW" mode="pos"><ActorScopedState/></AuthenticationBoundary>);
 expect(screen.getByText('1')).toBeTruthy();
});

function ActorScopedState(){
 const [count,setCount]=useState(1);
 return <button onClick={()=>setCount(x=>x+1)}>{count}</button>;
}
