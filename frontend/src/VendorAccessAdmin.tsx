import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi,ApiError} from './api';
import {useSession} from './session';

type Role={id:number;key:string;displayName:string;active:boolean;version:number;permissions:string[]};
type Employee={username:string;branchId:number;enabled:boolean;roleIds:number[];registerIds:number[]};
type Invitation={username:string;invitationToken:string|null;expiresAt:string|null;accountAlreadyActive:boolean};

export default function VendorAccessAdmin({mode}:{mode:'roles'|'employees'}){
 const {account}=useSession();
 const rights=new Set(account?.permissions??[]);
 const [roles,setRoles]=useState<Role[]>([]),[employees,setEmployees]=useState<Employee[]>([]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [roleKey,setRoleKey]=useState(''),[displayName,setDisplayName]=useState(''),[permissions,setPermissions]=useState('POS_VIEW');
 const [reason,setReason]=useState(''),[employeeEmail,setEmployeeEmail]=useState(''),[employeeBranch,setEmployeeBranch]=useState(String(account?.branchId??'')),[employeeRole,setEmployeeRole]=useState('');
 const [subject,setSubject]=useState(''),[selectedRoles,setSelectedRoles]=useState<number[]>([]),[registerIds,setRegisterIds]=useState('');
 const [effective,setEffective]=useState<string[]>([]),[invitation,setInvitation]=useState<Invitation|null>(null);
 const canRoleView=rights.has('ROLE_VIEW'),canRoleCreate=rights.has('ROLE_CREATE'),canRoleUpdate=rights.has('ROLE_UPDATE'),canRoleAssign=rights.has('ROLE_ASSIGN');
 const canUserView=rights.has('USER_VIEW'),canUserCreate=rights.has('USER_CREATE'),canUserUpdate=rights.has('USER_UPDATE'),canUserDisable=rights.has('USER_DISABLE');
 const load=useCallback(async()=>{
  setError('');
  try{
   if(canRoleView||canRoleCreate||canRoleUpdate){const value=await vendorApi('/roles');setRoles(value);}
   if(canUserView||canUserCreate||canUserUpdate||canUserDisable){const value=await vendorApi('/employees');setEmployees(value);}
  }catch(e){setError((e as Error).message);}
 },[canRoleView,canRoleCreate,canRoleUpdate,canUserView,canUserCreate,canUserUpdate,canUserDisable]);
 useEffect(()=>{void load();},[load]);
 async function run(action:()=>Promise<any>,message:string){
  setBusy(true);setError('');setNotice('');
  try{const value=await action();setNotice(message);await load();return value;}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);return undefined;}
  finally{setBusy(false);}
 }
 function parsedPermissions(){return [...new Set(permissions.split(/[\s,]+/).map(x=>x.trim().toUpperCase()).filter(Boolean))];}
 async function createRole(e:FormEvent){e.preventDefault();const value=await run(()=>vendorApi('/roles','POST',{roleKey,displayName,permissions:parsedPermissions(),reason}), 'Role created and audited.');if(value){setRoleKey('');setDisplayName('');}}
 async function createEmployee(e:FormEvent){e.preventDefault();const value=await run(()=>vendorApi('/employees','POST',{username:employeeEmail,branchId:Number(employeeBranch),roleId:Number(employeeRole),reason}),'Employee invitation and initial role assignment created.');if(value)setInvitation(value);}
 async function assignRoles(){await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/roles','PUT',{roleIds:selectedRoles,reason}),'Employee roles updated and audited.');}
 async function assignRegisters(){await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/registers','PUT',{registerIds:registerIds.split(/[\s,]+/).filter(Boolean).map(Number),reason}),'Register assignments updated and audited.');}
 async function changeBranch(){await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/branch','PATCH',{branchId:Number(employeeBranch),reason}),'Employee branch updated; prior register assignments revoked.');}
 async function disableEmployee(username:string){await run(()=>vendorApi('/employees/'+encodeURIComponent(username)+'/disable','POST',{reason}),'Employee disabled and active assignments revoked.');}
 async function preview(){const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/effective-permissions'),'Effective permission preview refreshed.');if(value)setEffective(value.permissions??[]);}
 const chooseEmployee=(username:string)=>{setSubject(username);const row=employees.find(e=>e.username===username);setSelectedRoles(row?.roleIds??[]);setRegisterIds(row?.registerIds.join(',')??'');if(row)setEmployeeBranch(String(row.branchId));setEffective([]);};
 return <section className="vendor-access-admin">
  <header><h2>{mode==='roles'?'Roles & permissions':'Employee administration'}</h2><p>Access is resolved from the signed-in tenant membership, active role assignments and current resource assignments. Platform permissions cannot be assigned here.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <div className="vendor-access-toolbar"><span>Tenant #{account?.tenantId??'unavailable'}</span><button type="button" className="secondary" disabled={busy} onClick={()=>void load()}>Refresh</button></div>
  {mode==='roles'&&<>
   {canRoleCreate&&<form onSubmit={e=>void createRole(e)} className="vendor-access-form">
    <h3>Create tenant role</h3>
    <label>Role key<input required pattern="[a-z][a-z0-9_-]{1,79}" value={roleKey} onChange={e=>setRoleKey(e.target.value)}/></label>
    <label>Display name<input required maxLength={120} value={displayName} onChange={e=>setDisplayName(e.target.value)}/></label>
    <label>Permissions (comma or newline separated)<textarea required rows={5} value={permissions} onChange={e=>setPermissions(e.target.value)}/></label>
    <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button disabled={busy}>Create role</button>
   </form>}
   <div className="vendor-access-list"><h3>Tenant roles</h3>{roles.map(role=><article key={role.id}>
    <div><strong>{role.displayName}</strong><small>{role.key} · {role.active?'Active':'Inactive'} · v{role.version}</small><p>{role.permissions.join(', ')}</p></div>
    {canRoleUpdate&&role.active&&<button className="secondary" disabled={busy} onClick={()=>void run(()=>vendorApi('/roles/'+role.id+'/deactivate','POST',{reason}),'Role deactivated.')}>Deactivate</button>}
   </article>)}</div>
  </>}
  {mode==='employees'&&<>
   {canUserCreate&&<form onSubmit={e=>void createEmployee(e)} className="vendor-access-form">
    <h3>Invite employee</h3>
    <label>Employee email<input type="email" required value={employeeEmail} onChange={e=>setEmployeeEmail(e.target.value)}/></label>
    <label>Branch ID<input type="number" required min="1" value={employeeBranch} onChange={e=>setEmployeeBranch(e.target.value)}/></label>
    <label>Initial role<select required value={employeeRole} onChange={e=>setEmployeeRole(e.target.value)}><option value="">Select role</option>{roles.filter(r=>r.active).map(r=><option key={r.id} value={r.id}>{r.displayName}</option>)}</select></label>
    <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button disabled={busy||!employeeRole}>Create employee invitation</button>
    {invitation&&<p role="status">Invitation for {invitation.username}: {invitation.invitationToken??'This identity already has an active account; no invitation token issued.'}</p>}
   </form>}
   <div className="vendor-access-list"><h3>Employees</h3>{employees.map(e=><article key={e.username}>
    <div><strong>{e.username}</strong><small>Branch {e.branchId} · {e.enabled?'Enabled':'Disabled'}</small><p>Roles: {e.roleIds.join(', ')||'None'} · Registers: {e.registerIds.join(', ')||'None'}</p></div>
    <div className="vendor-access-actions"><button type="button" className="secondary" onClick={()=>chooseEmployee(e.username)}>Manage</button>{canUserDisable&&e.enabled&&<button type="button" className="secondary" disabled={busy} onClick={()=>void disableEmployee(e.username)}>Disable</button>}</div>
   </article>)}</div>
   {subject&&<div className="vendor-access-form">
    <h3>Manage {subject}</h3>
    {canRoleAssign&&<><label>Role assignments<select multiple value={selectedRoles.map(String)} onChange={e=>setSelectedRoles(Array.from(e.target.selectedOptions).map(x=>Number(x.value)))}>{roles.filter(r=>r.active).map(r=><option key={r.id} value={r.id}>{r.displayName}</option>)}</select></label><button type="button" disabled={busy} onClick={()=>void assignRoles()}>Save role assignments</button></>}
    {canRoleAssign&&<><label>Assigned register IDs (comma separated)<input value={registerIds} onChange={e=>setRegisterIds(e.target.value)}/></label><button type="button" disabled={busy} onClick={()=>void assignRegisters()}>Save register assignments</button></>}
    {canUserUpdate&&<><label>New branch ID<input type="number" min="1" value={employeeBranch} onChange={e=>setEmployeeBranch(e.target.value)}/></label><button type="button" className="secondary" disabled={busy} onClick={()=>void changeBranch()}>Change branch</button></>}
    {canUserView&&<button type="button" className="secondary" disabled={busy} onClick={()=>void preview()}>Preview effective permissions</button>}
    {effective.length>0&&<p>Effective permissions: {effective.join(', ')}</p>}
   </div>}
  </>}
 </section>;
}
