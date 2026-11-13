import { useEffect, useRef, useState } from 'react';
import { api } from './api';
type Totals = { paidOrders: number; sales: number; tax: number; beforeTax: number; cashReceived: number; changeGiven: number; cashApplied: number; units: number; invoices: number; unpaidOrders: number; unpaidAmount: number; cancelledOrders: number; expiredOrders: number; discounts?: number };
type Report = { branchId: number; from: string; to: string; channel: string; generatedAt: string; totals: Totals; channels: { channel: string; totals: Totals }[]; days: { date: string; totals: Totals }[] };
const money = (value: number) => Number(value).toFixed(2);
const channelName = (channel: string) => channel === 'IN_STORE' ? 'Walk-in / Store POS' : 'Online marketplace';
export default function SalesInsights() {
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [from, setFrom] = useState(() => new Date(Date.now() - 6 * 86400000).toISOString().slice(0, 10));
  const [channel, setChannel] = useState('ALL'), [report, setReport] = useState<Report>(), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const version = useRef(0);
  useEffect(() => () => { version.current += 1; }, []);
  async function load(event: React.FormEvent) {
    event.preventDefault();
    const days = (Date.parse(to) - Date.parse(from)) / 86400000;
    if (!from || !to || !Number.isFinite(days) || days < 0 || days > 365) { setError('Choose an inclusive UTC date range of 1–366 days.'); return; }
    const current = ++version.current; setBusy(true); setError('');
    try { const value: Report = await api(`/reports/sales?${new URLSearchParams({ from, to, channel })}`); if (version.current === current) setReport(value); }
    catch { if (version.current === current) setError('Report unavailable. POS_VIEW and POS_REPORT_VIEW permissions in an active assigned branch are required. Check your connection and retry.'); }
    finally { if (version.current === current) setBusy(false); }
  }
  const maximum = Math.max(1, ...(report?.days.map(day => Number(day.totals.sales)) ?? []));
  return <section id="sales-insights" className="sales-insights" aria-labelledby="insights-title" aria-busy={busy}>
    <div className="section-heading"><div><span className="eyebrow">Your branch at a glance</span><h2 id="insights-title">Sales insights</h2></div><span className="badge">Permissioned branch report</span></div>
    <p>Recorded paid sales are separate from unpaid reservations. Figures use UTC event dates. Refund and external-payment reporting is not available.</p>
    <form className="insight-filters" onSubmit={load}><label>Sales from (UTC)<input type="date" required disabled={busy} value={from} onChange={event => setFrom(event.target.value)}/></label><label>Sales to (UTC)<input type="date" required disabled={busy} value={to} onChange={event => setTo(event.target.value)}/></label><label>Sales channel<select disabled={busy} value={channel} onChange={event => setChannel(event.target.value)}><option value="ALL">All channels</option><option value="ONLINE">Online marketplace</option><option value="IN_STORE">Walk-in / Store POS</option></select></label><button disabled={busy}>{busy ? 'Loading insights…' : report ? 'Refresh sales insights' : 'Load sales insights'}</button></form>
    {error && <p role="alert">{error}</p>}
    {report && <><p className="report-scope" role="status">Branch {report.branchId} · {report.from} to {report.to} UTC · {report.channel === 'ALL' ? 'All channels' : channelName(report.channel)} · Generated {new Date(report.generatedAt).toLocaleString()}</p>
      <div className="insight-kpis"><div><small>Recorded sales incl. tax</small><strong>{money(report.totals.sales)}</strong><span>{report.totals.paidOrders} paid orders</span></div><div><small>Before tax</small><strong>{money(report.totals.beforeTax)}</strong><span>Tax {money(report.totals.tax)}</span></div><div><small>Cash applied to sales</small><strong>{money(report.totals.cashApplied)}</strong><span>Received {money(report.totals.cashReceived)} − change {money(report.totals.changeGiven)}</span></div><div><small>Unpaid reservations</small><strong>{report.totals.unpaidOrders}</strong><span>{money(report.totals.unpaidAmount)} excluded from sales</span></div></div>
      {!report.totals.paidOrders && <div className="empty-state"><h3>No paid sales in this period</h3><p>Unpaid reservations and cancellations are shown separately. They are not revenue.</p></div>}
      <div className="channel-grid">{report.channels.map(item => <section key={item.channel} aria-label={channelName(item.channel)}><h3>{channelName(item.channel)}</h3><dl className="channel-facts"><div><dt>Recorded sales</dt><dd>{money(item.totals.sales)}</dd></div><div><dt>Paid orders / units</dt><dd>{item.totals.paidOrders} / {item.totals.units}</dd></div><div><dt>Promotion discounts</dt><dd>{money(item.totals.discounts??0)}</dd></div><div><dt>Tax / invoices</dt><dd>{money(item.totals.tax)} / {item.totals.invoices}</dd></div><div><dt>Unpaid / cancelled / expired</dt><dd>{item.totals.unpaidOrders} / {item.totals.cancelledOrders} / {item.totals.expiredOrders}</dd></div></dl></section>)}</div>
      <h3>Daily recorded sales</h3><ul className="sales-trend">{report.days.map(day => <li key={day.date}><time dateTime={day.date}>{day.date}</time><meter min="0" max={maximum} value={Number(day.totals.sales)} aria-label={`Recorded sales on ${day.date}`}/><span>{money(day.totals.sales)} · {day.totals.paidOrders} paid</span></li>)}</ul><p>Cash applied is an order tender total, not a till balance. Use the cash drawer for opening float, movements and reconciliation.</p>
    </>}
  </section>;
}
