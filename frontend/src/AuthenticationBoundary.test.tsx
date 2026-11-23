import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {useState} from 'react';
import {afterEach,expect,it,vi} from 'vitest';
import AuthenticationBoundary from './AuthenticationBoundary';
import {useSession} from './session';

vi.mock('./session',()=>({useSession:vi.fn(),restoreSession:vi.fn()}));
const session=(account:any,status:'loading'|'authenticated'|'anonymous'|'error'='authenticated')=>({status,loading:status==='loading',refreshing:false,account,error:null});
vi.mock('./Shell',()=>({default:({children}:{children:import('react').ReactNode})=><div>{children}</div>}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('does not mount protected work before a secure session is restored',()=>{
 vi.mocked(useSession).mockReturnValue(session(null,'loading'));
 render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);
 expect(screen.queryByText('Billing workflow')).toBeNull();
});

it('denies a Customer context access to the Vendor POS workspace',()=>{
 vi.mocked(useSession).mockReturnValue(session({
  username:'buyer',tenantId:null,branchId:null,membershipId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER',availableActorTypes:['CUSTOMER','VENDOR']
 }));
 render(<AuthenticationBoundary permission="POS_VIEW" mode="pos"><p>Billing workflow</p></AuthenticationBoundary>);
 expect(screen.getByRole('alert').textContent).toContain('does not have access');
 expect(screen.queryByText('Billing workflow')).toBeNull();
});

it('clears page-local state when the active tenant context changes',()=>{
 vi.mocked(useSession).mockReturnValue(session({
  username:'staff',tenantId:55,branchId:7,membershipId:'55:staff',permissions:['PRODUCT_VIEW'],actorType:'VENDOR',availableActorTypes:['VENDOR']
 }));
 const view=render(<AuthenticationBoundary permission="PRODUCT_VIEW" mode="pos"><ActorScopedState/></AuthenticationBoundary>);
 fireEvent.click(screen.getByRole('button',{name:'1'}));
 expect(screen.getByText('2')).toBeTruthy();
 vi.mocked(useSession).mockReturnValue(session({
  username:'staff',tenantId:56,branchId:8,membershipId:'56:staff',permissions:['PRODUCT_VIEW'],actorType:'VENDOR',availableActorTypes:['VENDOR']
 }));
 view.rerender(<AuthenticationBoundary permission="PRODUCT_VIEW" mode="pos"><ActorScopedState/></AuthenticationBoundary>);
 expect(screen.getByText('1')).toBeTruthy();
});

function ActorScopedState(){
 const [count,setCount]=useState(1);
 return <button onClick={()=>setCount(x=>x+1)}>{count}</button>;
}
