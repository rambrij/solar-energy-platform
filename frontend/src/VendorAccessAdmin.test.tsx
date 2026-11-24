import {act,cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import VendorAccessAdmin from './VendorAccessAdmin';

const {mockVendorApi,mockUseSession}=vi.hoisted(()=>({mockVendorApi:vi.fn(),mockUseSession:vi.fn()}));
vi.mock('./api',()=>({
 vendorApi:mockVendorApi,
 ApiError:class extends Error{status:number;constructor(status:number){super(`Request failed (${status}).`);this.status=status;}}
}));
vi.mock('./session',()=>({useSession:mockUseSession}));

const role={id:7,key:'sales',displayName:'Sales role',active:true,version:2,permissions:['PRODUCT_VIEW']};
const account={username:'admin@vendor.test',tenantId:7,branchId:12,membershipId:'7:admin@vendor.test',actorType:'VENDOR',availableActorTypes:['VENDOR'],permissions:['ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE','PRODUCT_VIEW','POS_VIEW']};

beforeEach(()=>{
 vi.clearAllMocks();
 mockUseSession.mockReturnValue({status:'authenticated',loading:false,refreshing:false,account,error:null});
 mockVendorApi.mockImplementation(async(path:string,method='GET')=>{
  if(path==='/roles')return [role];
  if(path==='/roles/permissions')return {permissions:['PRODUCT_VIEW','POS_VIEW','PLATFORM_TENANT_CREATE']};
  return [];
 });
});
afterEach(()=>cleanup());

describe('VendorAccessAdmin role editor',()=>{
 it('offers only server-resolved vendor permissions and never exposes platform permissions',async()=>{
  render(<VendorAccessAdmin mode="roles"/>);
  const form=await screen.findByTestId('create-role-form');
  expect(within(form).getByRole('checkbox',{name:'PRODUCT_VIEW'})).toBeTruthy();
  expect(within(form).getByRole('checkbox',{name:'POS_VIEW'})).toBeTruthy();
  expect(within(form).queryByRole('checkbox',{name:'PLATFORM_TENANT_CREATE'})).toBeNull();
  expect(within(form).queryByLabelText(/Permissions \(comma/)).toBeNull();
 });

 it('distinguishes materialized standard template snapshots from tenant custom roles',async()=>{
  mockVendorApi.mockImplementation(async(path:string,method='GET')=>{
   if(path==='/roles')return [
    {...role,id:8,templateKey:'POS_CASHIER',templateVersion:1,key:'standard:POS_CASHIER',displayName:'POS Cashier'},
    role
   ];
   if(path==='/roles/permissions')return {permissions:['PRODUCT_VIEW','POS_VIEW'],templates:[
    {key:'POS_CASHIER',version:1,permissions:['POS_VIEW'],deferredReason:null}
   ]};
   return [];
  });
  render(<VendorAccessAdmin mode="roles"/>);
  expect(await screen.findByText(/Standard template POS_CASHIER · template v1/)).toBeTruthy();
  expect(screen.getByText(/Tenant custom role · sales/)).toBeTruthy();
  expect(screen.getAllByText('POS_VIEW').length).toBeGreaterThan(0);
 });
 it('replaces permissions using the loaded optimistic version and refreshes the role list',async()=>{
  let saved={...role};
  mockVendorApi.mockImplementation(async(path:string,method='GET',body?:any)=>{
   if(path==='/roles'&&method==='GET')return [saved];
   if(path==='/roles/permissions')return {permissions:['PRODUCT_VIEW','POS_VIEW']};
   if(path==='/roles/7'&&method==='PUT'){
    saved={...saved,...body,permissions:body.permissions,version:3};return saved;
   }
   return [];
  });
  render(<VendorAccessAdmin mode="roles"/>);
  fireEvent.click(await screen.findByRole('button',{name:'Edit role Sales role'}));
  const form=screen.getByTestId('edit-role-form');
  fireEvent.change(within(form).getByLabelText('Updated role name'),{target:{value:'Sales role updated'}});
  fireEvent.click(within(form).getByRole('checkbox',{name:'PRODUCT_VIEW'}));
  fireEvent.click(within(form).getByRole('checkbox',{name:'POS_VIEW'}));
  const changes=within(form).getByRole('region',{name:'Permission changes'});
  expect(within(changes).getByText('1 added, 1 removed, 0 retained.')).toBeTruthy();
  expect(within(changes).getByText('POS_VIEW')).toBeTruthy();
  expect(within(changes).getByText('PRODUCT_VIEW')).toBeTruthy();
  fireEvent.change(within(form).getByLabelText('Audit reason'),{target:{value:'Replace least privilege grant'}});
  fireEvent.click(within(form).getByRole('button',{name:'Save role changes'}));
  await screen.findByText('Sales role updated');
  expect(mockVendorApi).toHaveBeenCalledWith('/roles/7','PUT',{
   expectedVersion:2,displayName:'Sales role updated',permissions:['POS_VIEW'],reason:'Replace least privilege grant'
  });
  await waitFor(()=>expect(mockVendorApi.mock.calls.filter((call:any[])=>call[0]==='/roles').length).toBeGreaterThanOrEqual(2));
 });

 it.each([
  [403,'You do not currently have permission'],
  [404,'unavailable in the active vendor workspace'],
  [409,'changed since it was loaded']
 ])('handles role editor HTTP %i safely and refreshes after conflicts',async(status,expectedMessage)=>{
  mockVendorApi.mockImplementation(async(path:string,method='GET')=>{
   if(path==='/roles')return [role];
   if(path==='/roles/permissions')return {permissions:['PRODUCT_VIEW','POS_VIEW']};
   if(path==='/roles/7'&&method==='PUT')throw new (await import('./api')).ApiError(status as number);
   return [];
  });
  render(<VendorAccessAdmin mode="roles"/>);
  fireEvent.click(await screen.findByRole('button',{name:'Edit role Sales role'}));
  const form=screen.getByTestId('edit-role-form');
  fireEvent.change(within(form).getByLabelText('Audit reason'),{target:{value:'Retry role update'}});
  fireEvent.click(within(form).getByRole('button',{name:'Save role changes'}));
  expect((await screen.findByRole('alert')).textContent).toContain(expectedMessage);
  if(status===409){
   await waitFor(()=>expect(screen.queryByTestId('edit-role-form')).toBeNull());
   expect(mockVendorApi.mock.calls.filter((call:any[])=>call[0]==='/roles').length).toBeGreaterThanOrEqual(2);
  }
 });

 it('refreshes employee effective permissions after role assignment succeeds',async()=>{
  mockUseSession.mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{
   username:'admin@vendor.test',tenantId:7,branchId:12,membershipId:'7:admin@vendor.test',actorType:'VENDOR',availableActorTypes:['VENDOR'],
   permissions:['ROLE_VIEW','ROLE_ASSIGN','USER_VIEW']
  },error:null});
  mockVendorApi.mockImplementation(async(path:string,method='GET')=>{
   if(path==='/roles')return [role];
   if(path==='/roles/permissions')return {permissions:['ROLE_VIEW']};
   if(path==='/employees')return [{username:'staff@vendor.test',branchId:12,enabled:true,roleIds:[7],registerIds:[]}];
   if(path==='/employees/staff%40vendor.test/roles'&&method==='PUT')return {username:'staff@vendor.test',roleIds:[7]};
   if(path==='/employees/staff%40vendor.test/effective-permissions')return {permissions:['POS_VIEW']};
   return [];
  });
  render(<VendorAccessAdmin mode="employees"/>);
  fireEvent.click(await screen.findByRole('button',{name:'Manage'}));
  fireEvent.change(screen.getByLabelText('Audit reason'),{target:{value:'Confirm employee role access'}});
  fireEvent.click(screen.getByRole('button',{name:'Save role assignments'}));
  expect(await screen.findByText('Effective permissions: POS_VIEW')).toBeTruthy();
  expect(mockVendorApi).toHaveBeenCalledWith('/employees/staff%40vendor.test/effective-permissions');
 });
});

it('combines email, status, and role filters within the authorized employee directory',async()=>{
 mockUseSession.mockReturnValue({account:{...account,permissions:['USER_VIEW','ROLE_VIEW']}});
 mockVendorApi.mockImplementation(async(path:string)=>{
  if(path==='/roles')return [role,{...role,id:8,displayName:'Inventory role'}];
  if(path==='/employees')return [
   {username:'sales.enabled@vendor.test',branchId:12,enabled:true,roleIds:[7],registerIds:[]},
   {username:'sales.disabled@vendor.test',branchId:12,enabled:false,roleIds:[7],registerIds:[]},
   {username:'inventory.enabled@vendor.test',branchId:12,enabled:true,roleIds:[8],registerIds:[]},
  ];
  return {permissions:[]};
 });
 render(<VendorAccessAdmin mode="employees"/>);
 await screen.findByText('Showing 3 of 3 employees.');
 fireEvent.change(screen.getByLabelText('Filter by role'),{target:{value:'7'}});
 expect(screen.getByText('Showing 2 of 3 employees.')).toBeTruthy();
 expect(screen.queryByText('inventory.enabled@vendor.test')).toBeNull();
 fireEvent.change(screen.getByLabelText('Account status'),{target:{value:'disabled'}});
 expect(screen.getByText('sales.disabled@vendor.test')).toBeTruthy();
 expect(screen.queryByText('sales.enabled@vendor.test')).toBeNull();
 fireEvent.change(screen.getByLabelText('Search employee email'),{target:{value:' ENABLED '}});
 expect(screen.getByText('No employees match the current filters.')).toBeTruthy();
 expect(mockVendorApi.mock.calls.filter(([url])=>url==='/employees')).toHaveLength(1);
});

it('shows empty effective access and ignores a response for a previously selected employee',async()=>{
 mockUseSession.mockReturnValue({account:{...account,permissions:['USER_VIEW']}});
 let finishFirst:(result:{permissions:string[]})=>void=()=>{};
 mockVendorApi.mockImplementation(async(path:string)=>{
  if(path==='/employees')return [
   {username:'first@vendor.test',branchId:12,enabled:true,roleIds:[],registerIds:[19]},
   {username:'second@vendor.test',branchId:13,enabled:false,roleIds:[],registerIds:[]},
  ];
  if(path.includes('first%40'))return new Promise(resolve=>{finishFirst=resolve;});
  return {permissions:[]};
 });
 render(<VendorAccessAdmin mode="employees"/>);
 const first=await screen.findByText('first@vendor.test');
 fireEvent.click(within(first.closest('article')!).getByRole('button',{name:'Manage'}));
 fireEvent.click(screen.getByRole('button',{name:'Preview effective permissions'}));
 expect(screen.getByText(/Checking effective permissions/)).toBeTruthy();
 const second=screen.getByText('second@vendor.test');
 fireEvent.click(within(second.closest('article')!).getByRole('button',{name:'Manage'}));
 fireEvent.click(screen.getByRole('button',{name:'Preview effective permissions'}));
 expect(await screen.findByText('No effective permissions.')).toBeTruthy();
 expect(screen.getByText(/Saved branch #13.*Disabled.*none assigned/)).toBeTruthy();
 await act(async()=>{finishFirst({permissions:['POS_VIEW']});});
 expect(screen.queryByText('Effective permissions: POS_VIEW')).toBeNull();
 expect(screen.getByText('No effective permissions.')).toBeTruthy();
});

it('shows the invitation expiry and a token-free activation page link',async()=>{
 mockUseSession.mockReturnValue({account:{...account,permissions:['ROLE_VIEW','USER_CREATE']}});
 mockVendorApi.mockImplementation(async(path:string,method='GET')=>{
  if(path==='/roles')return [role];
  if(path==='/employees'&&method==='POST')return {username:'new@vendor.test',invitationToken:'one-time-token',
   expiresAt:'2026-11-25T10:00:00Z',accountAlreadyActive:false};
  return {permissions:[]};
 });
 render(<VendorAccessAdmin mode="employees"/>);
 const initialRole=await screen.findByLabelText('Initial role');
 await screen.findByRole('option',{name:'Sales role'});
 fireEvent.change(screen.getByLabelText('Employee email'),{target:{value:'new@vendor.test'}});
 fireEvent.change(initialRole,{target:{value:'7'}});
 fireEvent.change(screen.getByLabelText('Audit reason'),{target:{value:'Onboard staff'}});
 fireEvent.click(screen.getByRole('button',{name:'Create employee invitation'}));
 const result=await screen.findByRole('region',{name:'Employee invitation result'});
 expect(within(result).getByText('one-time-token')).toBeTruthy();
 expect(within(result).getByRole('link',{name:'vendor activation page'}).getAttribute('href')).toBe('/vendor/invitation');
 expect(result.querySelector('time')?.dateTime).toBe('2026-11-25T10:00:00Z');
});
