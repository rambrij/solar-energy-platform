import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import SessionControls from './SessionControls';
import {useSession,signOut,switchActorContext} from './session';

vi.mock('./session',()=>({useSession:vi.fn(),signOut:vi.fn(),switchActorContext:vi.fn()}));
vi.mock('./LoginPanel',()=>({default:()=>null}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('offers Customer/Vendor switching only when both contexts were server-resolved',async()=>{
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{
  username:'dual@example.test',tenantId:55,branchId:8,membershipId:'55:dual@example.test',permissions:['PRODUCT_VIEW'],actorType:'VENDOR',
  availableActorTypes:['CUSTOMER','VENDOR']
 },error:null});
 vi.mocked(switchActorContext).mockResolvedValue();
 render(<SessionControls/>);
 expect(screen.getByLabelText('Active actor context').textContent).toContain('Vendor');
 fireEvent.click(screen.getByRole('button',{name:'Switch to Customer workspace'}));
 await waitFor(()=>expect(switchActorContext).toHaveBeenCalledWith('CUSTOMER'));
});

it('does not offer a workspace switch to Platform or single-context identities',()=>{
 vi.mocked(useSession).mockReturnValue({ready:true,account:{
  username:'platform@example.test',tenantId:null,branchId:null,membershipId:null,permissions:['PLATFORM_SUPPORT_ACCESS'],actorType:'PLATFORM',
  availableActorTypes:['PLATFORM']
 },error:null});
 const view=render(<SessionControls/>);
 expect(screen.queryByRole('button',{name:/Switch to/})).toBeNull();
 view.rerender(<SessionControls/>);
 vi.mocked(useSession).mockReturnValue({ready:true,account:{
  username:'buyer@example.test',tenantId:null,branchId:null,membershipId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER',
  availableActorTypes:['CUSTOMER']
 },error:null});
 view.rerender(<SessionControls/>);
 expect(screen.queryByRole('button',{name:/Switch to/})).toBeNull();
});
