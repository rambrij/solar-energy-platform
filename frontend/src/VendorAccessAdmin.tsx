import {useCallback,useEffect,useRef,useState,type FormEvent} from 'react';
import {vendorApi,ApiError} from './api';
import {useSession} from './session';

type Template={key:string;version:number;permissions:string[];deferredReason:string|null};
type Role={templateKey?:string;templateVersion?:number;id:number;key:string;displayName:string;active:boolean;version:number;permissions:string[]};
type Employee={username:string;branchId:number;enabled:boolean;roleIds:number[];registerIds:number[]};
type EmployeeBranch={id:number;name:string};
type Invitation={username:string;invitationToken:string|null;expiresAt:string|null;accountAlreadyActive:boolean};
type InvitationStatus={status:'ACTIVE'|'ACCEPTED'|'PENDING'|'EXPIRED'|'NOT_SENT';expiresAt:string|null};
type EmployeeAccessEvent={id:number;actorUsername:string;resourceType:string;resourceId:string|null;action:string;reason:string;occurredAt:string};

function errorMessage(error:unknown){
 if(error instanceof ApiError){
  if(error.status===403)return 'You do not currently have permission to perform this action.';
  if(error.status===404)return 'This role or employee is unavailable in the active vendor workspace.';
  if(error.status===409)return 'The role has changed since it was loaded. The latest role list was refreshed; reopen the role before retrying.';
 }
 return (error as Error)?.message??'The request could not be completed.';
}

export default function VendorAccessAdmin({mode}:{mode:'roles'|'employees'}){
 const {account}=useSession();
 const rights=new Set(account?.permissions??[]);
 const [roles,setRoles]=useState<Role[]>([]),[employees,setEmployees]=useState<Employee[]>([]),[branches,setBranches]=useState<EmployeeBranch[]>([]);
 const [templates,setTemplates]=useState<Template[]>([]),[source,setSource]=useState<Template|null>(null);
 const [delegablePermissions,setDelegablePermissions]=useState<string[]>([]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [roleKey,setRoleKey]=useState(''),[displayName,setDisplayName]=useState(''),[selectedPermissions,setSelectedPermissions]=useState<string[]>([]);
 const [editingRole,setEditingRole]=useState<Role|null>(null),[editDisplayName,setEditDisplayName]=useState(''),[editPermissions,setEditPermissions]=useState<string[]>([]);
 const [reason,setReason]=useState(''),[employeeEmail,setEmployeeEmail]=useState(''),[employeeBranch,setEmployeeBranch]=useState(String(account?.branchId??'')),[employeeRole,setEmployeeRole]=useState('');
 const [subject,setSubject]=useState(''),[selectedRoles,setSelectedRoles]=useState<number[]>([]),[registerIds,setRegisterIds]=useState('');
 const [effective,setEffective]=useState<{username:string;permissions:string[]}|null>(null),[invitation,setInvitation]=useState<Invitation|null>(null);
 const [invitationStatus,setInvitationStatus]=useState<InvitationStatus|null>(null),[checkingInvitation,setCheckingInvitation]=useState(false);
 const [accessHistory,setAccessHistory]=useState<EmployeeAccessEvent[]>([]),[checkingAccessHistory,setCheckingAccessHistory]=useState(false);
 const canRoleView=rights.has('ROLE_VIEW'),canRoleCreate=rights.has('ROLE_CREATE'),canRoleUpdate=rights.has('ROLE_UPDATE'),canRoleAssign=rights.has('ROLE_ASSIGN');
 const canUserView=rights.has('USER_VIEW'),canUserCreate=rights.has('USER_CREATE'),canUserUpdate=rights.has('USER_UPDATE'),canUserDisable=rights.has('USER_DISABLE');
 const canReactivateEmployee=canUserUpdate&&canRoleAssign;
 const permissionOptions=[...new Set(delegablePermissions)]
  .filter(permission=>!permission.startsWith('PLATFORM_')&&permission!=='CUSTOMER')
  .sort();

 const load=useCallback(async()=>{
  setError('');
  try{
   if(canRoleView)setRoles(await vendorApi('/roles'));
   else setRoles([]);
   if(canRoleView||canRoleCreate||canRoleUpdate){
    const value=await vendorApi('/roles/permissions');
    setDelegablePermissions(value.permissions??[]);setTemplates(value.templates??[]);
   }else setDelegablePermissions([]);
   if(canUserView)setEmployees(await vendorApi('/employees'));
   else setEmployees([]);
   if(mode==='employees'&&(canUserView||canUserCreate||canUserUpdate))setBranches(await vendorApi('/employees/branches'));
   else setBranches([]);
  }catch(e){setError(errorMessage(e));}
 },[mode,canRoleView,canRoleCreate,canRoleUpdate,canUserView,canUserCreate,canUserUpdate]);
 useEffect(()=>{void load();},[load]);

 async function run(action:()=>Promise<any>,message:string){
  setBusy(true);setError('');setNotice('');
  try{const value=await action();setNotice(message);await load();return value;}
  catch(e){
   if(e instanceof ApiError&&(e.status===404||e.status===409)){
    await load();
    setEditingRole(null);
   }
   setError(errorMessage(e));return undefined;
  }finally{setBusy(false);}
 }
 function togglePermission(value:string,selected:string[],setter:(values:string[])=>void){
  setter(selected.includes(value)?selected.filter(p=>p!==value):[...selected,value]);
 }
 async function createRole(e:FormEvent){
  e.preventDefault();
  const value=await run(()=>vendorApi('/roles','POST',{roleKey,displayName,permissions:selectedPermissions,reason,...(source?{templateKey:source.key,templateVersion:source.version}:{})}),'Role created and audited.');
  if(value){setSource(null);setRoleKey('');setDisplayName('');setSelectedPermissions([]);}
 }
 function startEdit(role:Role){
  setEditingRole(role);setEditDisplayName(role.displayName);
  setEditPermissions(role.permissions.filter(p=>permissionOptions.includes(p)));
 }
 async function updateRole(e:FormEvent){
  e.preventDefault();if(!editingRole)return;
  const role=editingRole;
  const value=await run(()=>vendorApi('/roles/'+role.id,'PUT',{
   expectedVersion:role.version,displayName:editDisplayName,permissions:editPermissions,reason
  }),'Role updated and permissions replaced.');
  if(value){setEditingRole(null);setEditDisplayName('');setEditPermissions([]);}
 }
 async function createEmployee(e:FormEvent){
  e.preventDefault();
  const value=await run(()=>vendorApi('/employees','POST',{username:employeeEmail,branchId:Number(employeeBranch),roleId:Number(employeeRole),reason}),'Employee invitation and initial role assignment created.');
  if(value)setInvitation(value);
 }
 async function refreshInvitationStatus(username:string){
  if(!username||username!==selectedEmployee.current)return;
  const request=++invitationRequest.current;
  setInvitationStatus(null);setCheckingInvitation(true);
  try{
   const value=await vendorApi('/employees/'+encodeURIComponent(username)+'/invitation');
   if(request===invitationRequest.current&&selectedEmployee.current===username)setInvitationStatus(value);
  }catch(e){if(request===invitationRequest.current)setError(errorMessage(e));}
  finally{if(request===invitationRequest.current)setCheckingInvitation(false);}
 }
   async function refreshAccessHistory(username:string){
   if(!username||username!==selectedEmployee.current||!canUserView)return;
   const request=++historyRequest.current;
   setAccessHistory([]);setCheckingAccessHistory(true);
   try{
    const value=await vendorApi('/employees/'+encodeURIComponent(username)+'/access-history');
    if(request===historyRequest.current&&selectedEmployee.current===username)setAccessHistory(value??[]);
   }catch(e){if(request===historyRequest.current)setError(errorMessage(e));}
   finally{if(request===historyRequest.current)setCheckingAccessHistory(false);}
  }
  async function resendInvitation(){
  const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/invitation/resend','POST',{reason}),
   'A fresh one-time invitation was issued; the previous token is no longer valid.');
  if(value){setInvitation(value);setInvitationStatus({status:value.accountAlreadyActive?'ACTIVE':'PENDING',expiresAt:value.expiresAt});}
 }
 async function refreshEffectivePermissions(){
  if(!subject||!canUserView||subject!==selectedEmployee.current)return;
  const request=++permissionRequest.current;
  setEffective(null);
  setCheckingPermissions(true);
  try{
   const value=await vendorApi('/employees/'+encodeURIComponent(subject)+'/effective-permissions');
   if(request===permissionRequest.current)setEffective({username:subject,permissions:value.permissions??[]});
  }catch(e){
   if(request===permissionRequest.current)setError(errorMessage(e));
  }finally{
   if(request===permissionRequest.current)setCheckingPermissions(false);
  }
 }
 async function assignRoles(){
  const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/roles','PUT',{roleIds:selectedRoles,reason}),'Employee roles updated and audited.');
  if(value)await refreshEffectivePermissions();
 }
 async function assignRegisters(){
  await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/registers','PUT',{registerIds:registerIds.split(/[\s,]+/).filter(Boolean).map(Number),reason}),'Register assignments updated and audited.');
 }
 async function changeBranch(){
  const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(subject)+'/branch','PATCH',{branchId:Number(employeeBranch),reason}),'Employee branch updated; prior register assignments revoked.');
  if(value)await refreshEffectivePermissions();
 }
 async function reactivateEmployee(username:string){
  const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(username)+'/reactivate','POST',{roleIds:selectedRoles,reason}),'Employee reactivated with explicit role assignments. Previous register assignments remain revoked and must be assigned separately.');
  if(value)await refreshEffectivePermissions();
 }
 async function disableEmployee(username:string){
  const value=await run(()=>vendorApi('/employees/'+encodeURIComponent(username)+'/disable','POST',{reason}),'Employee disabled and active assignments revoked.');
  if(value&&subject===username)await refreshEffectivePermissions();
 }
 async function preview(){await refreshEffectivePermissions();}
 const chooseEmployee=(username:string)=>{
  setSubject(username);const row=employees.find(e=>e.username===username);
  setSelectedRoles(row?.roleIds??[]);setRegisterIds(row?.registerIds.join(',')??'');
  if(row)setEmployeeBranch(String(row.branchId));
  selectedEmployee.current=username;
  invitationRequest.current++;setInvitationStatus(null);setCheckingInvitation(false);
  void refreshInvitationStatus(username);
  historyRequest.current++;setAccessHistory([]);setCheckingAccessHistory(false);void refreshAccessHistory(username);
  permissionRequest.current++;
  setEffective(null);
  setCheckingPermissions(false);
 };
 const permissionRequest=useRef(0);
 const selectedEmployee=useRef('');
 const invitationRequest=useRef(0);
 const historyRequest=useRef(0);
 const [checkingPermissions,setCheckingPermissions]=useState(false);
 const savedEmployee=employees.find(employee=>employee.username===subject);
 const [employeeQuery,setEmployeeQuery]=useState('');
 const [accountStatus,setAccountStatus]=useState('all');
 const [roleFilter,setRoleFilter]=useState('');
 const query=employeeQuery.trim().toLowerCase();
 const visibleEmployees=employees.filter(employee=>
  employee.username.toLowerCase().includes(query)
  &&(accountStatus==='all'||employee.enabled===(accountStatus==='enabled'))
  &&(!canRoleView||!roleFilter||employee.roleIds.includes(Number(roleFilter)))
 );
 const savedPermissions=new Set(editingRole?.permissions??[]);
 const selectedPermissionSet=new Set(editPermissions);
 const addedPermissions=editPermissions.filter(permission=>!savedPermissions.has(permission));
 const removedPermissions=[...savedPermissions].filter(permission=>!selectedPermissionSet.has(permission));
 const retainedPermissionCount=editPermissions.length-addedPermissions.length;
 const roleNames=new Map(roles.map(role=>[role.id,role.displayName]));
 const branchNames=new Map(branches.map(branch=>[branch.id,branch.name]));
 const selectedBranchIsValid=branches.some(branch=>String(branch.id)===employeeBranch);
 type RoleAccessEvent={id:number;actorUsername:string;subjectUsername:string|null;resourceType:string;resourceId:string;action:string;reason:string;occurredAt:string};
 const [roleHistory,setRoleHistory]=useState<{roleId:number;events:RoleAccessEvent[]}|null>(null);
 const [checkingRoleHistory,setCheckingRoleHistory]=useState(false);
 const roleHistoryRequest=useRef(0);
 async function refreshRoleHistory(roleId:number){
  const request=++roleHistoryRequest.current;
  setRoleHistory({roleId,events:[]});setCheckingRoleHistory(true);setError('');
  try{
   const value:RoleAccessEvent[]=await vendorApi('/roles/'.concat(encodeURIComponent(roleId),'/access-history'));
   if(request===roleHistoryRequest.current)setRoleHistory({roleId,events:value??[]});
  }catch(e){if(request===roleHistoryRequest.current)setError(errorMessage(e));}
  finally{if(request===roleHistoryRequest.current)setCheckingRoleHistory(false);}
 }


 return <section className="vendor-access-admin">
  <header><h2>{mode==='roles'?'Roles & permissions':'Employee administration'}</h2><p>Access is resolved from the signed-in tenant membership, active role assignments and current resource assignments. Platform permissions cannot be assigned here.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <div className="vendor-access-toolbar"><span>Tenant #{account?.tenantId??'unavailable'}</span><button type="button" className="secondary" disabled={busy} onClick={()=>void load()}>Refresh</button></div>
  {mode==='roles'&&<>
   {templates.length>0&&<section><h3>Standard role templates</h3>
    <p>Branch, register, session and resource assignments still apply.</p>
    {templates.map(t=><article key={t.key}><strong>{t.key} · v{t.version}</strong>
     <p>{t.deferredReason?'Deferred · '+t.deferredReason:t.permissions.join(', ')}</p>
     {canRoleCreate&&!t.deferredReason&&<>
      <button type="button" onClick={()=>{setSource(t);setRoleKey('');setDisplayName('');setSelectedPermissions(t.permissions.filter(p=>permissionOptions.includes(p)));}}>Customize {t.key}</button>
      <button type="button" disabled={busy||!t.permissions.every(p=>permissionOptions.includes(p))} onClick={()=>void run(()=>vendorApi('/roles','POST',{roleKey:'standard:'+t.key,displayName:t.key,templateKey:t.key,templateVersion:t.version,permissions:t.permissions,reason:'Initialize standard tenant role '+t.key}),'Tenant role initialized.')}>Initialize {t.key}</button>
     </>}</article>)}</section>}
   {canRoleCreate&&<form data-testid="create-role-form" onSubmit={e=>void createRole(e)} className="vendor-access-form">
    <h3>{source?'Customize '+source.key:'Create tenant role'}</h3>
    {source&&<button type="button" onClick={()=>{setSource(null);setSelectedPermissions([]);}}>Clear template</button>}
    <label>Role key<input required pattern="[a-z][a-z0-9_-]{1,79}" value={roleKey} onChange={e=>setRoleKey(e.target.value)}/></label>
    <label>Display name<input required maxLength={120} value={displayName} onChange={e=>setDisplayName(e.target.value)}/></label>
    <fieldset><legend>Permissions you can delegate</legend>
     {permissionOptions.filter(p=>!source||source.permissions.includes(p)).map(permission=><label key={permission}><input type="checkbox" aria-label={permission} checked={selectedPermissions.includes(permission)} onChange={()=>togglePermission(permission,selectedPermissions,setSelectedPermissions)}/>{permission}</label>)}
     {!permissionOptions.length&&<p>No currently delegable vendor permissions are available.</p>}
    </fieldset>
    <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button disabled={busy||!selectedPermissions.length}>Create role</button>
   </form>}
   {canRoleView&&<div className="vendor-access-list"><h3>Tenant roles</h3>{roles.map(role=><article key={role.id}>
     <div><strong>{role.displayName}</strong><small>{role.templateKey?('Standard template '+role.templateKey+' · template v'+role.templateVersion):'Tenant custom role'} · {role.key} · {role.active?'Active':'Inactive'} · role v{role.version}</small><p>{role.permissions.join(', ')}</p></div>
    <div className="vendor-access-actions">
     {canRoleUpdate&&role.active&&<button type="button" className="secondary" disabled={busy} onClick={()=>startEdit(role)}>Edit role {role.displayName}</button>}
     {canRoleUpdate&&role.active&&<button type="button" className="secondary" disabled={busy} onClick={()=>void run(()=>vendorApi(`/roles/${encodeURIComponent(role.id)}/deactivate`,'POST',{reason}),'Role deactivated.')}>Deactivate role {role.displayName}</button>}
     <button type="button" className="secondary" disabled={busy||checkingRoleHistory} onClick={()=>void refreshRoleHistory(role.id)}>
      {checkingRoleHistory&&roleHistory?.roleId===role.id?'Loading role history…':roleHistory?.roleId===role.id?'Refresh role history':'View audit history for '+role.displayName}
     </button>
    </div>
    {roleHistory?.roleId===role.id&&<section aria-label="Role access history" aria-busy={checkingRoleHistory}>
     <h4>Recorded role activity</h4>
     {checkingRoleHistory&&<p role="status">Loading role history…</p>}
     {!checkingRoleHistory&&!roleHistory.events.length&&<p>No role access history recorded.</p>}
     {!!roleHistory.events.length&&<ol className="employee-access-history">
      {roleHistory.events.map(event=><li key={event.id}>
       <strong>{event.action.replaceAll('_',' ')}</strong>
       <p>{event.resourceType.replaceAll('_',' ')}{event.resourceId?' · '+event.resourceId:''}</p>
       {event.subjectUsername&&<p>Employee: {event.subjectUsername}</p>}
       <p>{event.reason}</p>
       <p>By {event.actorUsername} · <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString()}</time></p>
      </li>)}
     </ol>}
     <p>Newest events first. This timeline includes role changes and employee role-assignment activity.</p>
    </section>}
   </article>)}</div>}
   {editingRole&&<form data-testid="edit-role-form" onSubmit={e=>void updateRole(e)} className="vendor-access-form">
    <h3>Edit tenant role: {editingRole.displayName} Â· v{editingRole.version}</h3>
    <label>Updated role name<input required maxLength={120} value={editDisplayName} onChange={e=>setEditDisplayName(e.target.value)}/></label>
    <fieldset><legend>Replacement permissions</legend>
     {permissionOptions.map(permission=><label key={permission}><input type="checkbox" aria-label={permission} checked={editPermissions.includes(permission)} onChange={()=>togglePermission(permission,editPermissions,setEditPermissions)}/>{permission}</label>)}
    </fieldset>
    <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <section aria-label="Permission changes">
     <h4>Permission changes</h4>
     <p aria-live="polite">{addedPermissions.length} added, {removedPermissions.length} removed, {retainedPermissionCount} retained.</p>
     {addedPermissions.length>0&&<><h5>Will be added</h5><ul>{addedPermissions.map(permission=><li key={permission}>{permission}</li>)}</ul></>}
     {removedPermissions.length>0&&<><h5>Will be removed</h5><ul>{removedPermissions.map(permission=><li key={permission}>{permission}</li>)}</ul></>}
    </section>
    <button disabled={busy||!editPermissions.length}>Save role changes</button>
    <button type="button" className="secondary" disabled={busy} onClick={()=>setEditingRole(null)}>Cancel</button>
   </form>}
  </>}
  {mode==='employees'&&<>
   {canUserCreate&&<form onSubmit={e=>void createEmployee(e)} className="vendor-access-form">
    <h3>Invite employee</h3>
    <label>Employee email<input type="email" required value={employeeEmail} onChange={e=>setEmployeeEmail(e.target.value)}/></label>
    <label>Branch<select required value={employeeBranch} onChange={e=>setEmployeeBranch(e.target.value)} disabled={!branches.length}>
     <option value="">Select branch</option>{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name} (#{branch.id})</option>)}
    </select></label>
    <label>Initial role<select required value={employeeRole} onChange={e=>setEmployeeRole(e.target.value)}><option value="">Select role</option>{roles.filter(r=>r.active).map(r=><option key={r.id} value={r.id}>{r.displayName}</option>)}</select></label>
    <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button disabled={busy||!employeeRole||!selectedBranchIsValid}>Create employee invitation</button>
    {invitation&&<section aria-label="Employee invitation result">
     <p role="status">Invitation result for {invitation.username}</p>
     {invitation.invitationToken?<>
      <p>Share the one-time token securely. The employee enters it on the <a href="/vendor/invitation" target="_blank" rel="noreferrer">vendor activation page</a>.</p>
      <p><code>{invitation.invitationToken}</code></p>
      {invitation.expiresAt&&<p>Expires <time dateTime={invitation.expiresAt}>{new Date(invitation.expiresAt).toLocaleString()}</time>.</p>}
     </>:<p>This identity already has an active account and can sign in with its existing credentials.</p>}
    </section>}
   </form>}
   {canUserView&&<div className="vendor-access-list">
    <h3>Employees</h3>
    <div className="vendor-access-form" role="group" aria-label="Employee directory filters">
     <label>Search employee email<input type="search" value={employeeQuery}
      onChange={event=>setEmployeeQuery(event.target.value)}/></label>
     <label>Account status<select value={accountStatus} onChange={event=>setAccountStatus(event.target.value)}>
      <option value="all">All accounts</option><option value="enabled">Enabled</option><option value="disabled">Disabled</option>
     </select></label>
     {canRoleView&&roles.length>0&&<label>Filter by role<select value={roleFilter} onChange={event=>setRoleFilter(event.target.value)}>
      <option value="">All roles</option>
      {roles.map(role=><option key={role.id} value={role.id}>{role.displayName}{!role.active?' (inactive)':''}</option>)}
     </select></label>}
    </div>
    <p aria-live="polite">Showing {visibleEmployees.length} of {employees.length} employees.</p>
    {visibleEmployees.length===0&&<p>No employees match the current filters.</p>}
    {visibleEmployees.map(e=><article key={e.username}>
    <div><strong>{e.username}</strong><small>Branch {branchNames.get(e.branchId)??'#'+e.branchId} Â· {e.enabled?'Enabled':'Disabled'}</small><p>Roles: {e.roleIds.map(id=>roleNames.get(id)??`#${id}`).join(', ')||'None'} Â· Registers: {e.registerIds.join(', ')||'None'}</p></div>
    <div className="vendor-access-actions"><button type="button" className="secondary" onClick={()=>chooseEmployee(e.username)}>Manage</button>{canUserDisable&&e.enabled&&<button type="button" className="secondary" disabled={busy} onClick={()=>void disableEmployee(e.username)}>Disable</button>}</div>
   </article>)}</div>}
   {subject&&<div className="vendor-access-form">
    <h3>Manage {subject}</h3>
    {canUserView&&<section aria-label="Employee invitation status">
     <h4>Invitation status</h4>
     {checkingInvitation&&<p role="status">Checking invitation status…</p>}
     {invitationStatus&&<>
      <p role="status">{invitationStatus.status==='ACTIVE'?'Account active':invitationStatus.status==='ACCEPTED'?'Invitation accepted':invitationStatus.status==='PENDING'?'Invitation pending':invitationStatus.status==='EXPIRED'?'Invitation expired':'Invitation not sent'}</p>
      {invitationStatus.expiresAt&&<p>Expires <time dateTime={invitationStatus.expiresAt}>{new Date(invitationStatus.expiresAt).toLocaleString()}</time>.</p>}
      {canUserCreate&&canRoleAssign&&!['ACTIVE','ACCEPTED'].includes(invitationStatus.status)&&
       <button type="button" className="secondary" disabled={busy} onClick={()=>void resendInvitation()}>
        {invitationStatus.status==='NOT_SENT'?'Issue invitation':'Resend invitation'}
       </button>}
     </>}
     {invitation&&invitation.username===subject&&invitation.invitationToken&&<p>New one-time token: <code>{invitation.invitationToken}</code>. Share it securely; it expires <time dateTime={invitation.expiresAt??''}>{invitation.expiresAt?new Date(invitation.expiresAt).toLocaleString():'at the configured expiry'}</time>.</p>}
     {invitation&&invitation.username===subject&&invitation.accountAlreadyActive&&<p>The identity already has an active account and can sign in with its existing credentials.</p>}
    </section>}
    {savedEmployee&&<p>Saved branch #{savedEmployee.branchId}{branchNames.has(savedEmployee.branchId)?' · '+branchNames.get(savedEmployee.branchId):''} · {savedEmployee.enabled?'Enabled':'Disabled'}
     {' · Registers '}{savedEmployee.registerIds.join(', ')||'none assigned'}</p>}
    {(canRoleAssign||canUserUpdate||canUserDisable)&&<label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>}
    {canRoleAssign&&<><label>Role assignments<select multiple value={selectedRoles.map(String)} onChange={e=>setSelectedRoles(Array.from(e.target.selectedOptions).map(x=>Number(x.value)))}>{roles.filter(r=>r.active).map(r=><option key={r.id} value={r.id}>{r.displayName}</option>)}</select></label>{savedEmployee?.enabled!==false&&<button type="button" disabled={busy} onClick={()=>void assignRoles()}>Save role assignments</button>}</>}
    {canRoleAssign&&<><label>Assigned register IDs (comma separated)<input value={registerIds} onChange={e=>setRegisterIds(e.target.value)}/></label><button type="button" disabled={busy} onClick={()=>void assignRegisters()}>Save register assignments</button></>}
    {canUserUpdate&&<><label>New branch<select value={employeeBranch} onChange={e=>setEmployeeBranch(e.target.value)} disabled={!branches.length}>
     <option value="">Select branch</option>{branches.map(branch=><option key={branch.id} value={branch.id}>{branch.name} (#{branch.id})</option>)}
    </select></label><button type="button" className="secondary" disabled={busy||!selectedBranchIsValid} onClick={()=>void changeBranch()}>Change branch</button></>}
    {savedEmployee&&!savedEmployee.enabled&&canReactivateEmployee&&<><p>Reactivation requires at least one active role. Previous register assignments remain revoked.</p><button type="button" disabled={busy||selectedRoles.length===0} onClick={()=>void reactivateEmployee(subject)}>Reactivate employee with selected roles</button></>}
    {canUserView&&<button type="button" className="secondary" disabled={busy||checkingPermissions} onClick={()=>void preview()}>Preview effective permissions</button>}
    {checkingPermissions&&<p role="status">Checking effective permissions…</p>}
    {canUserView&&effective?.username===subject&&
     <p role="status">{effective.permissions.length>0?`Effective permissions: ${effective.permissions.join(', ')}`:'No effective permissions.'}</p>}
     {canUserView&&<section aria-label="Employee access history">
      <h4>Access history</h4>
      {checkingAccessHistory&&<p role="status">Loading access history…</p>}
      {!checkingAccessHistory&&accessHistory.length===0&&<p>No access history recorded for this employee.</p>}
      {accessHistory.length>0&&<ol className="employee-access-history">
       {accessHistory.map(event=><li key={event.id}>
        <strong>{event.action.replaceAll('_',' ')}</strong>
        <p>{event.resourceType.replaceAll('_',' ')}{event.resourceId?' · '+event.resourceId:''}</p>
        <p>{event.reason}</p>
        <p>By {event.actorUsername} · <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString()}</time></p>
       </li>)}
      </ol>}
     </section>}
   </div>}
  </>}
 </section>;
}
