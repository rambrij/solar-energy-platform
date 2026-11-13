import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import SalesInsights from './SalesInsights';
import { api } from './api';
vi.mock('./api', () => ({ api: vi.fn() }));
const totals = { paidOrders: 1, sales: 110, tax: 10, beforeTax: 100, cashReceived: 120, changeGiven: 10, cashApplied: 110, units: 1, invoices: 1, unpaidOrders: 2, unpaidAmount: 500, cancelledOrders: 3, expiredOrders: 4 };
const report = { branchId: 1, from: '2026-11-12', to: '2026-11-13', channel: 'ALL', generatedAt: '2026-11-13T12:00:00Z', totals, channels: [{ channel: 'IN_STORE', totals }], days: [{ date: '2026-11-13', totals }] };
afterEach(() => { cleanup(); vi.resetAllMocks(); });
function dates(from = '2026-11-12', to = '2026-11-13') { fireEvent.change(screen.getByLabelText('Sales from (UTC)'), { target: { value: from } }); fireEvent.change(screen.getByLabelText('Sales to (UTC)'), { target: { value: to } }); }
it('does not request staff reports before explicit loading', () => { render(<SalesInsights/>); expect(api).not.toHaveBeenCalled(); });
it('submits UTC dates and channel without a client tenant and shows recorded totals separately', async () => {
  vi.mocked(api).mockResolvedValue(report); render(<SalesInsights/>); dates(); fireEvent.change(screen.getByLabelText('Sales channel'), { target: { value: 'IN_STORE' } }); fireEvent.click(screen.getByText('Load sales insights'));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/reports/sales?from=2026-11-12&to=2026-11-13&channel=IN_STORE'));
  await screen.findByText('500.00 excluded from sales'); expect(screen.getByText('Received 120.00 − change 10.00')).toBeTruthy();
  const branch = screen.getByLabelText('Walk-in / Store POS'); expect(within(branch).getByText('1 / 1')).toBeTruthy(); expect(screen.getByRole('meter').getAttribute('value')).toBe('110');
});
it('rejects reversed and oversized date ranges locally', async () => {
  render(<SalesInsights/>); dates('2026-11-14', '2026-11-13'); fireEvent.click(screen.getByText('Load sales insights')); await screen.findByRole('alert'); expect(api).not.toHaveBeenCalled();
  dates('2020-01-01', '2026-11-13'); fireEvent.click(screen.getByText('Load sales insights')); expect(api).not.toHaveBeenCalled();
});
it('keeps the prior report and its server date labels on refresh failure', async () => {
  vi.mocked(api).mockResolvedValueOnce(report).mockRejectedValueOnce(new Error('Denied')); render(<SalesInsights/>); dates(); fireEvent.click(screen.getByText('Load sales insights')); await screen.findByText('500.00 excluded from sales');
  dates('2026-11-10', '2026-11-11'); fireEvent.click(screen.getByText('Refresh sales insights')); await screen.findByRole('alert'); expect(screen.getByText(/2026-11-12 to 2026-11-13 UTC/)).toBeTruthy();
});
it('does not describe unpaid orders as revenue in an empty paid period', async () => {
  vi.mocked(api).mockResolvedValue({ ...report, totals: { ...totals, paidOrders: 0, sales: 0, tax: 0, beforeTax: 0, cashApplied: 0 } }); render(<SalesInsights/>); dates(); fireEvent.click(screen.getByText('Load sales insights')); await screen.findByText('No paid sales in this period'); expect(screen.getByText('500.00 excluded from sales')).toBeTruthy();
});
