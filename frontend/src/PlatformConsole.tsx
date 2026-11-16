import {useState} from 'react';
import Shell from './Shell';
import {useSession} from './session';
import PlatformDashboard from './PlatformDashboard';
import {TenantDirectory,TenantOnboarding,type Tenant} from './PlatformTenants';
import {PlatformPlans,TenantSubscription} from './PlatformBilling';
import {PlatformAudit,PlatformConfiguration,SupportAccess} from './PlatformSettings';
/**
 * Platform Super Admin console. The SaaS operator is not a vendor: no catalogue, stock, orders or POS appear here.
 * Permissions come from the signed-in account (/api/v1/auth/me); they only shape the UI, every API call is authorised by the server.
 */
export default function PlatformConsole(){
 const session=useSession(),[selected,setSelected]=useState<Tenant>(),[refresh,setRefresh]=useState(0);
 const permissions=session.account?.permissions??[],can=(permission:string)=>permissions.includes(permission);
 const choose=(tenant:Tenant)=>{setSelected(tenant);setRefresh(value=>value+1);};
 return <Shell mode="platform">
  <h1>Platform console</h1>
  <PlatformDashboard can={can}/>
  <TenantOnboarding can={can} onCreated={choose}/>
  <TenantDirectory can={can} selected={selected} onSelect={choose} refresh={refresh}/>
  <PlatformPlans can={can}/>
  <TenantSubscription tenant={selected} can={can}/>
  <PlatformConfiguration can={can}/>
  <SupportAccess tenant={selected} can={can}/>
  <PlatformAudit can={can}/>
 </Shell>;
}
