import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import StoreSettings from './StoreSettings';
import {api} from './api';
import {useSession} from './session';
vi.mock('./api',()=>({api:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn()}));
beforeEach(()=>{vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'test-user',tenantId:1,branchId:1,membershipId:'1:test-user',actorType:'VENDOR',permissions:['POS_VIEW','POS_SETTINGS_MANAGE','POS_DISCOUNT_OVERRIDE'],availableActorTypes:['VENDOR']},error:null} as ReturnType<typeof useSession>);});
afterEach(()=>{cleanup();vi.resetAllMocks();});
const settings={branch:1,name:'Outlet',version:2,enabled:false,upi:null,card:null,split:false,days:0,cashRefunds:false,invoiceRequired:false,buyerDetailsRequired:false,creditNotesEnabled:false,currency:'INR'};
it('edits grouped settings with version checks and never sends a tenant or branch override',async()=>{vi.mocked(api).mockImplementation(async(path,body)=>path==='/discount-policy'?{maximumPercent:20}:body?{...settings,version:3,split:true}:settings);render(<StoreSettings/>);fireEvent.click(screen.getByText('Load authorized settings'));fireEvent.click(await screen.findByRole('tab',{name:'Payments'}));fireEvent.click(screen.getByLabelText('Enable split payments'));fireEvent.change(screen.getByLabelText('Configuration reason'),{target:{value:'Approved'}});fireEvent.click(screen.getByText('Save outlet settings'));await screen.findByRole('status');const body=vi.mocked(api).mock.calls.find(call=>call[0]==='/settings'&&call[1])?.[1] as Record<string,unknown>;expect(body.expectedVersion).toBe(2);expect(body.split).toBe(true);expect(body).not.toHaveProperty('branch');expect(body).not.toHaveProperty('tenantId');});
it('a failed configuration save asks for a reload and does not show success',async()=>{vi.mocked(api).mockImplementation(async(path,body)=>{if(body)throw Error('Version conflict');return path==='/settings'?settings:{maximumPercent:0};});render(<StoreSettings/>);fireEvent.click(screen.getByText('Load authorized settings'));await screen.findByLabelText('Outlet name');fireEvent.change(screen.getByLabelText('Configuration reason'),{target:{value:'Approved'}});fireEvent.click(screen.getByText('Save outlet settings'));await screen.findByRole('alert');expect(screen.queryByText('Outlet settings saved with audit history.')).toBeNull();});


it('loads only discount policy for a discount-only operator',async()=>{
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'discount',tenantId:1,branchId:1,membershipId:'1:discount',actorType:'VENDOR',permissions:['POS_DISCOUNT_OVERRIDE'],availableActorTypes:['VENDOR']},error:null} as ReturnType<typeof useSession>);
 vi.mocked(api).mockResolvedValue({maximumPercent:15});
 render(<StoreSettings/>);
 fireEvent.click(screen.getByText('Load authorized settings'));
 fireEvent.click(screen.getByText('Discount authority and till setup'));
 await screen.findByLabelText('Approved combined discount limit %');
 expect(api).toHaveBeenCalledWith('/discount-policy');
 expect(api).not.toHaveBeenCalledWith('/settings');
 expect(screen.queryByRole('tablist',{name:'Outlet settings sections'})).toBeNull();
 expect(screen.queryByText('Create a till for this outlet')).toBeNull();
});
