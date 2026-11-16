import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {PlatformAudit,PlatformConfiguration,SupportAccess} from './PlatformSettings';
import {platformApi,ApiError} from './api';
import type {Tenant} from './PlatformTenants';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),platformApi:vi.fn()}));
afterEach(cleanup);beforeEach(()=>vi.mocked(platformApi).mockReset());
const tenant:Tenant={id:7,slug:'pa',name:'Pa Store',legalName:'L',contactEmail:'o@example.com',status:'ACTIVE'};
const fill=(label:RegExp|string,value:string)=>fireEvent.change(screen.getByLabelText(label),{target:{value}});
const entries=[{key:'platform.subscription.payment.api_secret_ref',value:'env:********',secret:true,version:1},{key:'platform.notification.sender',value:null,secret:false,version:null}];
it('shows masked secrets, refuses literal secrets client-side and saves references',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>options?{}:entries);
 render(<PlatformConfiguration can={()=>true}/>);await screen.findByText('env:********');expect(screen.getByText('Secret reference')).toBeTruthy();expect(screen.getByText('not set')).toBeTruthy();
 fill('New value for platform.subscription.payment.api_secret_ref','sk_live_abcdef');fireEvent.click(screen.getByLabelText('Save platform.subscription.payment.api_secret_ref'));
 expect(screen.getByRole('alert').textContent).toContain('Secrets are never stored');expect(vi.mocked(platformApi).mock.calls.some(c=>c[1])).toBe(false);
 fill('New value for platform.subscription.payment.api_secret_ref','env:GATEWAY_SECRET');fireEvent.click(screen.getByLabelText('Save platform.subscription.payment.api_secret_ref'));await screen.findByText('platform.subscription.payment.api_secret_ref saved.');
 expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/configuration/platform.subscription.payment.api_secret_ref',{method:'PUT',body:{value:'env:GATEWAY_SECRET'}});
 expect((screen.getByLabelText('New value for platform.subscription.payment.api_secret_ref') as HTMLInputElement).value).toBe('');
});
it('surfaces a production mock-provider rejection and hides editing without permission',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>{if(options)throw new ApiError(400,undefined,'Mock providers are forbidden in production');return [{key:'platform.subscription.payment.provider',value:null,secret:false,version:null}];});
 const view=render(<PlatformConfiguration can={()=>true}/>);await screen.findByText('platform.subscription.payment.provider');fill('New value for platform.subscription.payment.provider','MOCK');fireEvent.click(screen.getByLabelText('Save platform.subscription.payment.provider'));await screen.findByText('Mock providers are forbidden in production');
 view.unmount();vi.mocked(platformApi).mockResolvedValue(entries);render(<PlatformConfiguration can={p=>p==='PLATFORM_ANALYTICS_VIEW'}/>);await screen.findByText('env:********');expect(screen.queryByLabelText(/New value for/)).toBeNull();
});
const grant=(over={})=>({id:'g1',tenantId:7,reason:'Investigating a failed receipt reprint',ticket:'SUP-1001',expiresAt:'2030-01-01T00:00:00Z',revokedAt:null,active:true,...over});
it('requires purpose, ticket and a bounded duration before requesting access',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>options?{}:[grant()]);
 render(<SupportAccess tenant={tenant} can={()=>true}/>);await screen.findByText(/SUP-1001/);
 fireEvent.click(screen.getByText('Grant time-limited access'));expect(screen.getByRole('alert').textContent).toContain('purpose');
 fill('Purpose','Investigating a failed receipt reprint');fill('Ticket / reference','bad ticket');fireEvent.click(screen.getByText('Grant time-limited access'));expect(screen.getByRole('alert').textContent).toContain('ticket');
 fill('Ticket / reference','SUP-1002');fill(/Minutes/,'500');fireEvent.click(screen.getByText('Grant time-limited access'));expect(screen.getByRole('alert').textContent).toContain('5 to 240');
 expect(vi.mocked(platformApi).mock.calls.some(c=>c[1])).toBe(false);
 fill(/Minutes/,'45');fireEvent.click(screen.getByText('Grant time-limited access'));await screen.findByText('Support access granted.');
 expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/tenants/7/support-access',{body:{reason:'Investigating a failed receipt reprint',ticket:'SUP-1002',minutes:45}});
});
it('reads the tenant audit trail through an active grant and revokes it',async()=>{
 vi.mocked(platformApi).mockImplementation(async (path:string)=>{path=path??'';return path.endsWith('/vendor-audit')?{entries:[{actor:'cashier-1',action:'SALE_CASH',resourceId:'order-1',occurredAt:'2026-11-15T10:00:00Z'}]}:path.endsWith('/revoke')?{}:[grant(),grant({id:'g0',active:false,ticket:'SUP-0001'})];});
 render(<SupportAccess tenant={tenant} can={()=>true}/>);await screen.findByText(/SUP-1001/);expect(screen.getByText('EXPIRED')).toBeTruthy();expect(screen.getAllByText('Read tenant audit trail')).toHaveLength(1);
 fireEvent.click(screen.getByText('Read tenant audit trail'));await screen.findByText('SALE_CASH');expect(screen.getByText('Tenant audit trail (read-only)')).toBeTruthy();
 fireEvent.click(screen.getByText('Revoke'));await screen.findByText('Access revoked.');expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/support-access/g1/revoke',{body:{}});
});
it('shows a denial from the server and blocks everything without permission or a tenant',async()=>{
 vi.mocked(platformApi).mockImplementation(async (path:string)=>{path=path??'';if(path.endsWith('/vendor-audit'))throw new ApiError(403,undefined,'No active support-access grant');return [grant()];});
 const view=render(<SupportAccess tenant={tenant} can={()=>true}/>);await screen.findByText(/SUP-1001/);fireEvent.click(screen.getByText('Read tenant audit trail'));await screen.findByText('No active support-access grant');
 view.unmount();render(<SupportAccess tenant={tenant} can={()=>false}/>);expect(screen.getByText('Your role does not include support access.')).toBeTruthy();cleanup();render(<SupportAccess can={()=>true}/>);expect(screen.getByText('Select a tenant in the directory first.')).toBeTruthy();
});
const event=(id:string,over={})=>({id,occurredAt:'2026-11-15T10:00:00Z',actor:'pa-root',action:'TENANT_CREATED',targetType:'TENANT',targetId:'7',tenantId:7,reason:null,details:'slug=pa',outcome:'SUCCESS',...over});
it('filters and pages the audit trail and shows denials',async()=>{
 vi.mocked(platformApi).mockResolvedValue({items:[event('a'),event('b',{action:'TENANT_SUSPENDED',outcome:'DENIED',reason:'Missing PLATFORM_TENANT_SUSPEND',details:null})],total:40});
 render(<PlatformAudit can={()=>true}/>);await screen.findByText('TENANT_SUSPENDED');expect(screen.getByText('DENIED')).toBeTruthy();expect(screen.getByText('Missing PLATFORM_TENANT_SUSPEND')).toBeTruthy();expect(screen.getByText('Page 1 of 3 · 40 events')).toBeTruthy();
 fill('Action','TENANT_SUSPENDED');await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).toContain('action=TENANT_SUSPENDED'));
 fill('Tenant id','abc');await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).not.toContain('tenantId'));fill('Tenant id','7');await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).toContain('tenantId=7'));
 fireEvent.click(screen.getByText('Next'));await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.at(-1)![0]).toContain('page=1'));
});
it('does not request the audit trail without audit permission',()=>{
 render(<PlatformAudit can={()=>false}/>);expect(screen.getByText('Your role does not include audit access.')).toBeTruthy();expect(vi.mocked(platformApi)).not.toHaveBeenCalled();
});
