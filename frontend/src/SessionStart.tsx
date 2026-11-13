import { useEffect, useState } from 'react';
import { api, ApiError } from './api';
import RegisterPicker from './RegisterPicker';

type Session = { id: string; registerId: number; openingFloat: number; state: string };
type Props = { onSession: (id: string, state?: string) => void; requiredSessionId?: string };

export default function SessionStart({ onSession, requiredSessionId }: Props) {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [register,setRegister]=useState(''),[sessionListFailed,setSessionListFailed]=useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api('/sessions/active').then(value => {
      if (active) {setSessions(value);setSessionListFailed(false);}
    }).catch(() => {
      if (active) {setSessionListFailed(true);setError('Could not load your open sessions. Check your login and refresh the list before opening a new session.');}
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [revision]);

  async function resume(id: string) {
    setBusy(true); setError('');
    try {
      const session: Session = await api(`/sessions/${id}/resume`, {});
      onSession(session.id, session.state);
    } catch {
      setError('Could not resume this session. It may have closed or your access may have changed. Refresh the list and try again.');
      setRevision(value => value + 1);
    } finally { setBusy(false); }
  }

  async function open(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (requiredSessionId||!register||sessionListFailed||loading||busy) return;
    const data = new FormData(event.currentTarget);
    setBusy(true); setError('');
    try {
      const session = await api('/sessions', { registerId: Number(data.get('register')), openingFloat: data.get('float') });
      onSession(session.id, session.state);
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409
        ? 'This register already has an open session. Resume your own session below. A session owned by another cashier must be closed by that cashier or an authorized supervisor.'
        : 'Session opening was not confirmed. Check your open sessions before retrying; do not add the opening float again.');
      setRevision(value => value + 1);
    } finally { setBusy(false); }
  }

  return <section aria-label="Cashier session selection">
    {error && <p role="alert">{error}</p>}
    {requiredSessionId && <p role="alert">A saved sale is unresolved. Resume its original session before starting another one.</p>}
    <form onSubmit={open}>
      <h2>Open cashier session</h2>
      <fieldset disabled={busy || !!requiredSessionId || loading || sessionListFailed}>
        <RegisterPicker disabled={busy||!!requiredSessionId||loading||sessionListFailed} onSelect={setRegister}/>
        <label>Opening float <input name="float" type="number" min="0" step="0.01" required /></label>
        <button disabled={!register}>Open session</button>
      </fieldset>
    </form>
    <h2>Your open sessions</h2>
    <p>Resume keeps the original float and transactions. It does not open another shift or add cash.</p>
    {loading ? <p role="status">Loading open sessions…</p> : sessionListFailed ? <p>Open-session status is unavailable. Refresh before choosing a register.</p> : sessions.length === 0 ? <p>No open sessions found for your account.</p> :
      <ul>{sessions.map(session => <li key={session.id}>
        Register {session.registerId} · Opening float {Number(session.openingFloat).toFixed(2)} {session.state === 'CLOSING' ? '· Awaiting supervisor review' : ''}
        <button disabled={busy || (!!requiredSessionId && requiredSessionId !== session.id)} onClick={() => resume(session.id)}>
          Resume register {session.registerId}
        </button>
      </li>)}</ul>}
    <button disabled={busy || loading} onClick={() => { setError(''); setRevision(value => value + 1); }}>Refresh open sessions</button>
  </section>;
}
