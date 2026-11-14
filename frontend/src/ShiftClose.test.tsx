import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ShiftClose from './ShiftClose';
import { api } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const props = { session: 's1', disabled: false, onStateChange: vi.fn(), onPendingChange: vi.fn(), onBusyChange: vi.fn() };
afterEach(() => { cleanup(); vi.resetAllMocks(); sessionStorage.clear(); });
it('submits zero count with a reason and displays the frozen variance', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => body ? { state: 'CLOSING', expectedCash: 50, countedCash: 0, variance: -50, varianceReason: 'Shortage' } : { state: 'OPEN' });
  render(<ShiftClose {...props} />);
  fireEvent.change(screen.getByLabelText('Counted drawer cash'), { target: { value: '0' } });
  fireEvent.change(screen.getByLabelText(/Variance reason/), { target: { value: 'Shortage' } });
  fireEvent.click(screen.getByText('Reconcile and close session'));
  await screen.findByText(/Awaiting a different supervisor/);
  const posted = vi.mocked(api).mock.calls.find(call => call[1]);
  expect(posted?.[1]).toEqual({ countedCash: '0', reason: 'Shortage', requestId: expect.any(String) });
  expect(screen.queryByLabelText('Counted drawer cash')).toBeNull();
  expect(props.onStateChange).toHaveBeenCalledWith('CLOSING');
});
it('preserves count/reason/request ID through an uncertain response and reload', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new Error('Offline'); return { state: 'OPEN' }; });
  const view = render(<ShiftClose {...props} />);
  fireEvent.change(screen.getByLabelText('Counted drawer cash'), { target: { value: '10' } });
  fireEvent.change(screen.getByLabelText(/Variance reason/), { target: { value: 'Shortage' } });
  fireEvent.click(screen.getByText('Reconcile and close session'));
  await screen.findByText(/Close outcome is uncertain/);
  const saved = sessionStorage.getItem('pos-close-attempt-v1:s1'); expect(saved).toBeTruthy(); view.unmount();
  render(<ShiftClose {...props} />);
  fireEvent.click(screen.getByText('Retry same close request'));
  await waitFor(() => expect(vi.mocked(api).mock.calls.filter(call => call[1])).toHaveLength(2));
  const calls = vi.mocked(api).mock.calls.filter(call => call[1]); expect(calls[0]).toEqual(calls[1]);
  expect(sessionStorage.getItem('pos-close-attempt-v1:s1')).toBe(saved);
});
it('refreshes approval status without changing the submitted count', async () => {
  let refreshed = false;
  vi.mocked(api).mockImplementation(async () => ({ state: refreshed ? 'CLOSED' : 'CLOSING', expectedCash: 50, countedCash: 45, variance: -5, varianceReason: 'Shortage', ...(refreshed ? { reviewedBy: 'manager', reviewReason: 'Verified' } : {}) }));
  render(<ShiftClose {...props} />); await screen.findByText(/Awaiting a different supervisor/);
  refreshed = true; fireEvent.click(screen.getByText('Refresh close status'));
  await screen.findByText(/Approved by manager: Verified/);
  expect(vi.mocked(api).mock.calls.every(call => !call[1])).toBe(true);
  expect(props.onStateChange).toHaveBeenLastCalledWith('CLOSED');
});
it('ignores a stale initial status response after the count is submitted', async () => {
  let resolveRead!: (value: unknown) => void;
  vi.mocked(api).mockImplementation(async (_path, body) => body
    ? { state: 'CLOSING', expectedCash: 50, countedCash: 45, variance: -5, varianceReason: 'Shortage' }
    : new Promise(resolve => { resolveRead = resolve; }));
  render(<ShiftClose {...props} />);
  fireEvent.change(screen.getByLabelText('Counted drawer cash'), { target: { value: '45' } });
  fireEvent.change(screen.getByLabelText(/Variance reason/), { target: { value: 'Shortage' } });
  fireEvent.click(screen.getByText('Reconcile and close session'));
  await screen.findByText(/Awaiting a different supervisor/);
  resolveRead({ state: 'OPEN' });
  await waitFor(() => expect(props.onStateChange).toHaveBeenLastCalledWith('CLOSING'));
  expect(screen.queryByLabelText('Counted drawer cash')).toBeNull();
});
