import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {TenantDetail,TenantDirectory,TenantOnboarding,type Tenant} from './PlatformTenants';
import {platformApi,ApiError} from './api';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),platformApi:vi.fn()}));
afterEach(cleanup);beforeEach(()=>vi.mocked(platformApi).mockReset());
const tenant=(over:Partial<Tenant>={}):Tenant=>({id:7,slug:'pa-store',name:'Pa Store',legalName:'Pa Store Pvt Ltd',contactEmail:'o@example.com',status:'PENDING',...over});
const all=()=>true;
const fill=(label:RegExp|string,value:string)=>fireEvent.change(screen.getByLabelText(label),{target:{value}});
it('validates then creates a tenant and reports idempotent replays',async()=>{
 const onCreated=vi.fn();vi.mocked(platformApi).mockResolvedValueOnce({tenant:tenant(),created:true}).mockResolvedValueOnce({tenant:tenant(),created:false});
 render(<TenantOnboarding can={all} onCreated={onCreated}/>);
 fireEvent.click(screen.getByText('Create tenant'));expect(screen.getByRole('alert').textContent).toContain('Slug is required');
 fill('Slug','Bad Slug');fireEvent.click(screen.getByText('Create tenant'));expect(screen.getByRole('alert').textContent).toContain('lower-case');
 fill('Slug','pa-store');fireEvent.click(screen.getByText('Create tenant'));expect(screen.getByRole('alert').textContent).toContain('required');expect(vi.mocked(platformApi)).not.toHaveBeenCalled();
 fill('Display name','Pa Store');fill('Legal name','Pa Store Pvt Ltd');fill('Contact email','o@example.com');fireEvent.click(screen.getByText('Create tenant'));
 await screen.findByText('Tenant pa-store created as PENDING.');expect(vi.mocked(platformApi).mock.calls[0][0]).toBe('/tenants');expect(onCreated).toHaveBeenCalledOnce();
 fill('Slug','pa-store');fill('Display name','Pa Store');fill('Legal name','Pa Store Pvt Ltd');fill('Contact email','o@example.com');fireEvent.click(screen.getByText('Create tenant'));await screen.findByText(/already exists with this profile; nothing changed/);
});
it('shows the server reason when onboarding is rejected and locks the form without permission',async()=>{
 vi.mocked(platformApi).mockRejectedValueOnce(new ApiError(409,undefined,'Slug is already in use by a different tenant'));
 const view=render(<TenantOnboarding can={all} onCreated={vi.fn()}/>);fill('Slug','pa-store');fill('Display name','N');fill('Legal name','L');fill('Contact email','o@example.com');fireEvent.click(screen.getByText('Create tenant'));await screen.findByText('Slug is already in use by a different tenant');
 view.unmount();render(<TenantOnboarding can={()=>false} onCreated={vi.fn()}/>);expect((screen.getByText('Create tenant') as HTMLButtonElement).disabled).toBe(true);expect(screen.getByText('Your role cannot create tenants.')).toBeTruthy();
});
it('lists tenants with filters and pagination and selects one to manage',async()=>{
 const rows=[tenant(),tenant({id:8,slug:'pa-two',name:'Pa Two',status:'ACTIVE'})];
 vi.mocked(platformApi).mockResolvedValue({items:rows,total:25});const onSelect=vi.fn();
 render(<TenantDirectory can={all} onSelect={onSelect} refresh={0}/>);await screen.findByText('Pa Two');
 expect(screen.getByText('Page 1 of 3 · 25 tenants')).toBeTruthy();fireEvent.click(screen.getByLabelText('Manage Pa Two'));expect(onSelect).toHaveBeenCalledWith(rows[1]);
 fireEvent.change(screen.getByLabelText('Status'),{target:{value:'ACTIVE'}});await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).toContain('status=ACTIVE'));
 fireEvent.click(screen.getByText('Next'));await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).toContain('page=1'));
 fireEvent.change(screen.getByLabelText('Search'),{target:{value:'pa two'}});await waitFor(()=>{const last=vi.mocked(platformApi).mock.calls.at(-1)![0];expect(last).toContain('q=pa%20two');expect(last).toContain('page=0');});
});
it('offers only valid lifecycle steps, enforces a reason and per-target permissions',async()=>{
 const onChanged=vi.fn();vi.mocked(platformApi).mockResolvedValue({...tenant({status:'ACTIVE'}),name:'Pa Store'});
 const only=(permission:string)=>(p:string)=>p===permission;
 const view=render(<TenantDetail tenant={tenant({status:'ACTIVE'})} can={only('PLATFORM_TENANT_UPDATE')} onChanged={onChanged}/>);
 expect(screen.queryByText('Activate / reactivate')).toBeNull();expect(screen.queryByText('Start review')).toBeNull();
 expect((screen.getByText('Suspend') as HTMLButtonElement).disabled).toBe(true);expect((screen.getByText('Close permanently') as HTMLButtonElement).disabled).toBe(true);
 view.unmount();render(<TenantDetail tenant={tenant({status:'ACTIVE'})} can={only('PLATFORM_TENANT_SUSPEND')} onChanged={onChanged}/>);
 fireEvent.click(screen.getByText('Suspend'));expect(screen.getByRole('alert').textContent).toContain('A reason is required');expect(vi.mocked(platformApi)).not.toHaveBeenCalled();
 fill(/Reason/,'Compliance hold');fireEvent.click(screen.getByText('Suspend'));await screen.findByText(/is now ACTIVE/);
 expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/tenants/7/status',{body:{status:'SUSPENDED',reason:'Compliance hold'}});expect(onChanged).toHaveBeenCalledOnce();
});
it('treats closed tenants as terminal and shows server conflicts',async()=>{
 const view=render(<TenantDetail tenant={tenant({status:'CLOSED'})} can={all} onChanged={vi.fn()}/>);
 expect(screen.getByText(/Closed tenants are terminal/)).toBeTruthy();expect(screen.getByText('Closed tenants cannot receive a Vendor Admin.')).toBeTruthy();view.unmount();
 vi.mocked(platformApi).mockRejectedValueOnce(new ApiError(409,undefined,'Cannot move a tenant from PENDING to ACTIVE'));
 render(<TenantDetail tenant={tenant()} can={all} onChanged={vi.fn()}/>);fill(/Reason/,'Skip');fireEvent.click(screen.getByText('Start review'));await screen.findByText('Cannot move a tenant from PENDING to ACTIVE');
});
it('provisions a Vendor Admin once, showing the temporary password a single time',async()=>{
 vi.mocked(platformApi).mockResolvedValueOnce({username:'store-admin',created:true,temporaryPassword:'Abcd1234Efgh5678Jkmn'}).mockResolvedValueOnce({username:'store-admin',created:false,temporaryPassword:null});
 render(<TenantDetail tenant={tenant()} can={all} onChanged={vi.fn()}/>);
 fill('Username','X Y');fireEvent.click(screen.getByText('Provision Vendor Admin'));expect(screen.getByRole('alert').textContent).toContain('lower-case');expect(vi.mocked(platformApi)).not.toHaveBeenCalled();
 fill('Username','store-admin');fireEvent.click(screen.getByText('Provision Vendor Admin'));
 const secret=await screen.findByText('Abcd1234Efgh5678Jkmn');expect(within(secret.closest('[role=alert]') as HTMLElement).getByText(/shown once/)).toBeTruthy();
 fireEvent.click(screen.getByText('Provision Vendor Admin'));await screen.findByText(/already exists for this tenant; no new password was issued/);expect(screen.queryByText('Abcd1234Efgh5678Jkmn')).toBeNull();
});
