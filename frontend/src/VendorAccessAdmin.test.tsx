import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import VendorAccessAdmin from './VendorAccessAdmin';

const {mockVendorApi,mockUseSession}=vi.hoisted(()=>({mockVendorApi:vi.fn(),mockUseSession:vi.fn()}));
vi.mock('./api',()=>({
 vendorApi:mockVendorApi,
 ApiError:class extends Error{status:number;constructor(status:number){super(`Request failed (${status}).`);this.status=status;}}
}));
vi.mock('./session',()=>({useSession:mockUseSession}));

const role={id:7,key:'sales',displayName:'Sales role',active:true,version:2,permissions:['PRODUCT_VIEW']};
const account={username:'admin@vendor.test',tenantId:7,branchId:12,actorType:'VENDOR',permissions:['ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE','PRODUCT_VIEW','POS_VIEW']};

beforeEach(()=>{
 vi.clearAllMocks();
 mockUseSession.mockReturnValue({ready:true,account});
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
  fireEvent.change(within(form).getByLabelText('Audit reason'),{target:{value:'Replace least privilege grant'}});
  fireEvent.click(within(form).getByRole('button',{name:'Save role changes'}));
  await screen.findByText('Sales role updated');
  expect(mockVendorApi).toHaveBeenCalledWith('/roles/7','PUT',{
   expectedVersion:2,displayName:'Sales role updated',permissions:['POS_VIEW'],reason:'Replace least privilege grant'
  });
  await waitFor(()=>expect(mockVendorApi.mock.calls.filter((call:any[])=>call[0]==='/roles'&&call[1]==='GET').length).toBeGreaterThanOrEqual(2));
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
  expect(await screen.findByRole('alert')).toHaveTextContent(expectedMessage);
  if(status===409){
   await waitFor(()=>expect(screen.queryByTestId('edit-role-form')).toBeNull());
   expect(mockVendorApi.mock.calls.filter((call:any[])=>call[0]==='/roles').length).toBeGreaterThanOrEqual(2);
  }
 });

 it('refreshes employee effective permissions after role assignment succeeds',async()=>{
  mockUseSession.mockReturnValue({ready:true,account:{
   username:'admin@vendor.test',tenantId:7,branchId:12,actorType:'VENDOR',
   permissions:['ROLE_VIEW','ROLE_ASSIGN','USER_VIEW']
  }});
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
