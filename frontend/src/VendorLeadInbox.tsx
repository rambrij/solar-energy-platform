import { useEffect, useState, type FormEvent } from 'react';
import { vendorApi } from './api';
import { usePermissions } from './usePermissions';

type Lead = {
 id: number;
 productName: string | null;
 contactName: string;
 contactEmail: string;
 contactPhone: string | null;
 message: string;
 createdAt: string;
 status: string;
 assignedTo: string | null;
 version: number;
};
const nextStatuses: Record<string, string[]> = {
 NEW: ['CONTACTED', 'LOST'],
 CONTACTED: ['QUALIFIED', 'LOST'],
 QUALIFIED: ['LOST'],
};

export default function VendorLeadInbox() {
 const { hasPermission } = usePermissions();
 const canUpdate = hasPermission('LEAD_UPDATE');
 const canAssign = hasPermission('LEAD_ASSIGN');
 const [leads, setLeads] = useState<Lead[]>([]);
 const [assignees, setAssignees] = useState<string[]>();
 const [revision, setRevision] = useState(0);
 const [loading, setLoading] = useState(true);
 const [loadError, setLoadError] = useState('');
 const [selected, setSelected] = useState<Lead>();
 const [action, setAction] = useState<'status' | 'assignment'>('status');
 const [value, setValue] = useState('');
 const [reason, setReason] = useState('');
 const [saving, setSaving] = useState(false);
 const [error, setError] = useState('');
 const [notice, setNotice] = useState('');

 useEffect(() => {
  let active = true;
  setLoading(true);
  setLoadError('');
  vendorApi('/leads')
   .then((rows: Lead[]) => { if (active) setLeads(rows); })
   .catch(e => { if (active) { setLeads([]); setLoadError(e.message); } })
   .finally(() => { if (active) setLoading(false); });
  return () => { active = false; };
 }, [revision]);

 useEffect(() => {
  if (!canAssign) return;
  let active = true;
  setAssignees(undefined);
  vendorApi('/leads/assignees')
   .then((names: string[]) => { if (active) setAssignees(names); })
   .catch(e => { if (active) setError(e.message); });
  return () => { active = false; };
 }, [canAssign, revision]);

 function edit(lead: Lead, kind: typeof action) {
  setSelected(lead);
  setAction(kind);
  setValue(kind === 'assignment' ? lead.assignedTo ?? '' : '');
  setReason('');
  setNotice('');
  setError('');
 }

 const maySave = selected && reason.trim() && (action === 'status'
  ? canUpdate && nextStatuses[selected.status]?.includes(value)
  : canAssign && assignees && value !== (selected.assignedTo ?? '') && (!value || assignees.includes(value)));

 async function save(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
  if (!selected || !maySave || saving) return;
  setSaving(true);
  setError('');
  const change = { expectedVersion: selected.version, reason: reason.trim() };
  try {
   const updated: Lead = action === 'status'
    ? await vendorApi(`/leads/${selected.id}`, 'PATCH', { ...change, status: value })
    : await vendorApi(`/leads/${selected.id}/assignment`, 'PUT', { ...change, employeeUsername: value || null });
   setLeads(rows => rows.map(row => row.id === updated.id ? updated : row));
   setSelected(undefined);
   setNotice(action === 'status' ? 'Lead status saved.' : 'Lead assignment saved.');
  } catch (e) {
   setError((e as Error).message);
  } finally {
   setSaving(false);
  }
 }

 return <section aria-label="Vendor enquiry inbox">
  <h2>Marketplace enquiries</h2>
  {loadError && <p role="alert">{loadError}</p>}
  {error && <p role="alert">{error}</p>}
  {notice && <p role="status">{notice}</p>}
  {loading && <p role="status">Loading enquiries…</p>}
  {!loading && !loadError && !leads.length && <p className="empty-state">No marketplace enquiries are available to your role.</p>}
  <button type="button" className="secondary" disabled={loading || saving} onClick={() => {
   setSelected(undefined);
   setError('');
   setRevision(current => current + 1);
  }}>Refresh enquiries</button>
  {!loading && <ul>{leads.map(lead => <li key={lead.id}>
   <h3>{lead.contactName}{lead.productName && ' · ' + lead.productName}</h3>
   <p><a href={'mailto:' + encodeURIComponent(lead.contactEmail)}>{lead.contactEmail}</a>{lead.contactPhone && ' · ' + lead.contactPhone}</p>
   <p>{lead.message}</p>
   <p>{new Date(lead.createdAt).toLocaleString()} · {lead.status} · {lead.assignedTo ?? 'Unassigned'}</p>
   {canUpdate && nextStatuses[lead.status] && <button type="button" disabled={saving} onClick={() => edit(lead, 'status')}>Update lead status</button>}
   {canAssign && assignees && lead.status !== 'LOST' && <button type="button" disabled={saving} onClick={() => edit(lead, 'assignment')}>Change lead assignment</button>}
   {selected?.id === lead.id && (action === 'status' ? canUpdate : canAssign) && <form onSubmit={save}>
    {action === 'status' ? <label>New status<select required value={value} onChange={e => setValue(e.target.value)}>
     <option value="">Select a status</option>
     {nextStatuses[lead.status]?.map(status => <option key={status}>{status}</option>)}
    </select></label> : <label>Assignee<select value={value} onChange={e => setValue(e.target.value)}>
     <option value="">Unassigned</option>
     {lead.assignedTo && !assignees?.includes(lead.assignedTo) && <option disabled>{lead.assignedTo}</option>}
     {assignees?.map(name => <option key={name}>{name}</option>)}
    </select></label>}
    <label>Reason<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
    <button type="submit" disabled={!maySave || saving}>{saving ? 'Saving…' : 'Save lead change'}</button>
    <button type="button" className="secondary" disabled={saving} onClick={() => setSelected(undefined)}>Cancel</button>
   </form>}
  </li>)}</ul>}
 </section>;
}
