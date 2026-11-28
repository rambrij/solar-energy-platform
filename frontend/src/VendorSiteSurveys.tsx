import {useCallback,useEffect,useState} from 'react';
import type {FormEvent} from 'react';
import {vendorApi} from './api';
import {usePermissions} from './usePermissions';

type Lead={id:number;status:string;contactName:string;productName:string|null;assignedTo:string|null};
type Survey={id:number;enquiryId:number;siteAddress:string;scheduledAt:string;status:string;roofType:string|null;
 monthlyConsumptionKwh:number|null;proposedCapacityKw:number|null;notes:string|null;findings:string|null;
 assignedTo:string;version:number};

export default function VendorSiteSurveys(){
 const {hasPermission,permissions}=usePermissions();
 const canUpdate=hasPermission('LEAD_UPDATE');
 const canManageUnassigned=hasPermission('LEAD_ASSIGN');
 const canEditSurvey=(survey:Survey)=>canUpdate&&(canManageUnassigned||survey.assignedTo===permissions?.username);
 const [leads,setLeads]=useState<Lead[]>([]);
 const [surveys,setSurveys]=useState<Survey[]>([]);
 const [leadId,setLeadId]=useState('');
 const [siteAddress,setSiteAddress]=useState('');
 const [scheduledAt,setScheduledAt]=useState('');
 const [roofType,setRoofType]=useState('');
 const [monthlyConsumption,setMonthlyConsumption]=useState('');
 const [capacity,setCapacity]=useState('');
 const [notes,setNotes]=useState('');
 const [reason,setReason]=useState('');
 const [findings,setFindings]=useState<Record<number,string>>({});
 const [busy,setBusy]=useState(true);
 const [saving,setSaving]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');

 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [leadRows,surveyRows]=await Promise.all([
    canUpdate ? vendorApi('/leads') as Promise<Lead[]> : Promise.resolve([]),
    vendorApi('/site-surveys') as Promise<Survey[]>
   ]);
   setLeads(leadRows.filter(lead=>lead.status!=='LOST'));
   setSurveys(surveyRows);
  }catch(e){setError((e as Error).message||'Site surveys could not be loaded.');}
  finally{setBusy(false);}
 },[canUpdate]);

 useEffect(()=>{void refresh();},[refresh]);

 async function schedule(event:FormEvent){
  event.preventDefault();
  if(!canUpdate||!leadId||!siteAddress.trim()||!scheduledAt||!reason.trim())return;
  setSaving(true);setError('');setNotice('');
  try{
   await vendorApi(`/leads/${leadId}/site-surveys`,'POST',{
    siteAddress:siteAddress.trim(),scheduledAt:new Date(scheduledAt).toISOString(),
    roofType:roofType.trim()||null,
    monthlyConsumptionKwh:monthlyConsumption?Number(monthlyConsumption):null,
    proposedCapacityKw:capacity?Number(capacity):null,
    notes:notes.trim()||null,reason:reason.trim()
   });
   setSiteAddress('');setScheduledAt('');setRoofType('');setMonthlyConsumption('');
   setCapacity('');setNotes('');setReason('');
   setNotice('Site survey scheduled.');
   await refresh();
  }catch(e){setError((e as Error).message||'Site survey could not be scheduled.');}
  finally{setSaving(false);}
 }

 async function update(survey:Survey,status:'COMPLETED'|'CANCELLED'){
  if(!canEditSurvey(survey)||(status==='COMPLETED'&&!findings[survey.id]?.trim()))return;
  setSaving(true);setError('');setNotice('');
  try{
   await vendorApi(`/site-surveys/${survey.id}`,'PATCH',{
    expectedVersion:survey.version,status,
    findings:status==='COMPLETED'?(findings[survey.id]||''):null,
    reason:status==='COMPLETED'?'Site survey completed':'Site survey cancelled'
   });
   setNotice(status==='COMPLETED'?'Survey marked complete.':'Survey cancelled.');
   await refresh();
  }catch(e){setError((e as Error).message||'Survey update failed. Reload and retry.');}
  finally{setSaving(false);}
 }

 return <section aria-label="Vendor site surveys">
  <h2>Site surveys</h2>
  <p>Surveys are attached to this vendor’s existing leads. Employees see only work assigned to them; tenant managers can see all surveys for their tenant.</p>
  {error&&<p role="alert">{error}</p>}
  {notice&&<p role="status">{notice}</p>}
  {canUpdate&&<form onSubmit={schedule}>
   <h3>Schedule a site survey</h3>
   <label>Lead
    <select required value={leadId} onChange={event=>setLeadId(event.target.value)}>
     <option value="">Select a lead</option>
     {leads.map(lead=><option key={lead.id} value={lead.id}>{lead.contactName}{lead.productName?' · '+lead.productName:''} · {lead.status}</option>)}
    </select>
   </label>
   <label>Site address
    <input required maxLength={500} value={siteAddress} onChange={event=>setSiteAddress(event.target.value)}/>
   </label>
   <label>Scheduled date and time
    <input required type="datetime-local" value={scheduledAt} onChange={event=>setScheduledAt(event.target.value)}/>
   </label>
   <label>Roof type
    <input maxLength={40} value={roofType} onChange={event=>setRoofType(event.target.value)}/>
   </label>
   <label>Monthly consumption (kWh)
    <input type="number" min="0.01" step="0.01" value={monthlyConsumption} onChange={event=>setMonthlyConsumption(event.target.value)}/>
   </label>
   <label>Proposed capacity (kW)
    <input type="number" min="0.01" step="0.01" value={capacity} onChange={event=>setCapacity(event.target.value)}/>
   </label>
   <label>Notes
    <textarea maxLength={2000} value={notes} onChange={event=>setNotes(event.target.value)}/>
   </label>
   <label>Reason
    <input required maxLength={500} value={reason} onChange={event=>setReason(event.target.value)}/>
   </label>
   <button type="submit" disabled={saving||busy||!leadId||!siteAddress.trim()||!scheduledAt||!reason.trim()}>
    {saving?'Saving…':'Schedule survey'}
   </button>
  </form>}
  <h3>Scheduled and completed surveys</h3>
  {busy&&!surveys.length&&<p role="status">Loading site surveys…</p>}
  {!busy&&!surveys.length&&!error&&<p className="empty-state">No site surveys have been scheduled.</p>}
  <ul>{surveys.map(survey=><li key={survey.id}>
   <h4>Survey #{survey.id} · Lead #{survey.enquiryId}</h4>
   <p>{survey.siteAddress}</p>
   <p>{new Date(survey.scheduledAt).toLocaleString()} · {survey.status}</p>
   <p>{survey.roofType||'Roof type not recorded'} · {survey.monthlyConsumptionKwh??'—'} kWh/month · {survey.proposedCapacityKw??'—'} kW</p>
   {survey.notes&&<p>{survey.notes}</p>}
   {canEditSurvey(survey)&&survey.status==='SCHEDULED'&&<>
    <label>Findings
     <textarea maxLength={4000} value={findings[survey.id]??''}
      onChange={event=>setFindings(value=>({...value,[survey.id]:event.target.value}))}/>
    </label>
    <button disabled={saving||!findings[survey.id]?.trim()} onClick={()=>update(survey,'COMPLETED')}>Mark completed</button>
    <button type="button" className="secondary" disabled={saving} onClick={()=>update(survey,'CANCELLED')}>Cancel survey</button>
   </>}
   {survey.findings&&<p>{survey.findings}</p>}
   <small>Assigned to {survey.assignedTo}</small>
  </li>)}</ul>
  <button type="button" className="secondary" disabled={busy||saving} onClick={()=>void refresh()}>Refresh surveys</button>
 </section>;
}
