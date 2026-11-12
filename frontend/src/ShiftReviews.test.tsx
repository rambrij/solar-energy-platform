import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ShiftReviews from './ShiftReviews';
import { api, ApiError } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const row = { sessionId: 's1', registerId: 1, cashier: 'alice', requestId: 'close-1', expectedCash: 50, countedCash: 45, variance: -5, varianceReason: 'Shortage' };
afterEach(() => { cleanup(); vi.resetAllMocks(); sessionStorage.clear(); });
async function load() { fireEvent.click(screen.getByText('Supervisor shift review')); fireEvent.click(screen.getByText('Load pending shift reviews')); await screen.findByText('Register 1 · Cashier alice'); }
it('requires a supervisor reason and posts only the immutable request identity', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => body ? { decision: 'APPROVE' } : [row]);
  render(<ShiftReviews />); await load();
  expect((screen.getByText('Approve variance for register 1') as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Supervisor reason for register 1'), { target: { value: 'Verified count' } });
  fireEvent.click(screen.getByText('Approve variance for register 1'));
  await screen.findByText(/Supervisor decision recorded/);
  expect(api).toHaveBeenCalledWith('/sessions/s1/approve-variance', { requestId: 'close-1', reason: 'Verified count' });
  expect(sessionStorage.getItem('pos-shift-review-attempt-v1')).toBeNull();
});
it('supports returning a count for correction', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => body ? {} : [row]);
  render(<ShiftReviews />); await load();
  fireEvent.change(screen.getByLabelText('Supervisor reason for register 1'), { target: { value: 'Recount drawer' } });
  fireEvent.click(screen.getByText('Return register 1 for correction'));
  await screen.findByText(/Supervisor decision recorded/);
  expect(api).toHaveBeenCalledWith('/sessions/s1/return-for-correction', { requestId: 'close-1', reason: 'Recount drawer' });
});
it('replays the same uncertain decision after reload', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new Error('Lost response'); return [row]; });
  const view = render(<ShiftReviews />); await load();
  fireEvent.change(screen.getByLabelText('Supervisor reason for register 1'), { target: { value: 'Verified count' } });
  fireEvent.click(screen.getByText('Approve variance for register 1')); await screen.findByText(/Decision was not confirmed/);
  view.unmount(); render(<ShiftReviews />); fireEvent.click(screen.getByText('Supervisor shift review'));
  fireEvent.click(screen.getByText('Retry same review decision'));
  await waitFor(() => expect(vi.mocked(api).mock.calls.filter(call => call[1])).toHaveLength(2));
  const calls = vi.mocked(api).mock.calls.filter(call => call[1]); expect(calls[0]).toEqual(calls[1]);
});
it('does not claim approval when access is denied', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new ApiError(403); return [row]; });
  render(<ShiftReviews />); await load();
  fireEvent.change(screen.getByLabelText('Supervisor reason for register 1'), { target: { value: 'Verified count' } });
  fireEvent.click(screen.getByText('Approve variance for register 1')); await screen.findByText(/Review rejected/);
  expect(screen.queryByText(/Supervisor decision recorded/)).toBeNull();
});
