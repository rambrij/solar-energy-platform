import {useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type Profile={tenantId:number;slug:string;name:string;legalName:string;contactEmail:string|null;contactPhone:string|null;taxIdentifier:string|null;address:string|null;lifecycleStatus:string;version:number;updatedAt:string};
type Draft={name:string;legalName:string;contactEmail:string;contactPhone:string;taxIdentifier:string;address:string};
const draftOf=(p:Profile):Draft=>({name:p.name,legalName:p.legalName,contactEmail:p.contactEmail??'',contactPhone:p.contactPhone??'',taxIdentifier:p.taxIdentifier??'',address:p.address??''});
const nullable=(v:string):string|null=>v.trim()?v.trim():null;

export default function VendorProfile(){
 const {account}=useSession();
 const canView=account?.permissions.includes('TENANT_PROFILE_VIEW')??false;
 const canUpdate=account?.permissions.includes('TENANT_PROFILE_UPDATE')??false;
 const [profile,setProfile]=useState<Profile>();const [draft,setDraft]=useState<Draft>();
 const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('');
 async function load(){
  if(!canView)return;setBusy(true);setError('');setNotice('');
  try{const p=await vendorApi('/profile') as Profile;setProfile(p);setDraft(draftOf(p));}
  catch{setError('Vendor profile could not be loaded. Check your profile permission and try again.');}
  finally{setBusy(false);}
 }
 function change<K extends keyof Draft>(key:K,value:Draft[K]){setDraft(prev=>prev?{...prev,[key]:value}:prev);}
 async function save(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(!profile||!draft||!canUpdate)return;
  setBusy(true);setError('');setNotice('');
  try{
   const p=await vendorApi('/profile','PUT',{expectedVersion:profile.version,name:draft.name,legalName:draft.legalName,contactEmail:nullable(draft.contactEmail),contactPhone:nullable(draft.contactPhone),taxIdentifier:nullable(draft.taxIdentifier),address:nullable(draft.address)}) as Profile;
   setProfile(p);setDraft(draftOf(p));setNotice('Vendor business profile saved.');
  }catch{setError('Profile was not saved. It may have changed in another session; reload before trying again.');}
  finally{setBusy(false);}
 }
 if(!canView)return <section aria-label="Vendor business profile"><p role="alert">Access denied. Vendor profile view permission is required.</p></section>;
 return <section aria-label="Vendor business profile" aria-busy={busy}>
  <h2>Vendor business profile</h2><p>Maintain the business details shown for your vendor storefront. Storefront identity and tenant lifecycle are managed separately.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <button type="button" disabled={busy} onClick={()=>void load()}>Load vendor profile</button>
  {profile&&draft&&<><dl><dt>Storefront slug</dt><dd>{profile.slug}</dd><dt>Tenant status</dt><dd>{profile.lifecycleStatus}</dd><dt>Profile version</dt><dd>{profile.version}</dd></dl>
   <form aria-label="Vendor business profile" onSubmit={save}><fieldset disabled={busy||!canUpdate}>
    <label>Business name<input required maxLength={160} value={draft.name} onChange={e=>change('name',e.target.value)}/></label>
    <label>Legal business name<input required maxLength={180} value={draft.legalName} onChange={e=>change('legalName',e.target.value)}/></label>
    <label>Contact email<input type="email" maxLength={100} value={draft.contactEmail} onChange={e=>change('contactEmail',e.target.value)}/></label>
    <label>Contact phone<input type="tel" maxLength={30} value={draft.contactPhone} onChange={e=>change('contactPhone',e.target.value)}/></label>
    <label>Tax identifier<input maxLength={40} value={draft.taxIdentifier} onChange={e=>change('taxIdentifier',e.target.value)}/></label>
    <label>Business address<textarea maxLength={500} value={draft.address} onChange={e=>change('address',e.target.value)}/></label>
    {canUpdate&&<button type="submit" disabled={busy||!draft.name.trim()||!draft.legalName.trim()}>Save business profile</button>}
   </fieldset></form></>}
 </section>;
}
