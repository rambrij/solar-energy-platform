import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import CashDrawer from './CashDrawer';
import { api, ApiError } from './api';

vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
const report = { state: 'OPEN', openingFloat: 50, cashReceived: 120, changeGiven: 10, cashIn: 100, cashOut: 25, safeDrops: 100, expectedCash: 135, movements: [] };
const props = { session: 'shift-1', revision: 0, disabled: false, onPendingChange: vi.fn(), onBusyChange: vi.fn() };
afterEach(() => { cleanup(); vi.resetAllMocks(); sessionStorage.clear(); });
function fillMovement() {
  fireEvent.change(screen.getByLabelText('Movement type'), { target: { value: 'SAFE_DROP' } });
  fireEvent.change(screen.getByLabelText('Movement amount'), { target: { value: '25' } });
  fireEvent.change(screen.getByLabelText('Movement reason'), { target: { value: 'Move to safe' } });
}
it('shows server drawer totals and refreshes history after a successful movement', async () => {
  let posted = false;
  vi.mocked(api).mockImplementation(async (path, body) => {
    if (body) { posted = true; return { id: 'movement-1' }; }
    return { ...report, safeDrops: posted ? 125 : 100, expectedCash: posted ? 110 : 135,
      movements: posted ? [{ id: 'movement-1', movement_type: 'SAFE_DROP', amount: 25, reason: 'Move to safe', cashier: 'alice', created_at: '2026-11-12' }] : [] };
  });
  render(<CashDrawer {...props} />);
  await screen.findByText('135.00');
  fillMovement(); fireEvent.click(screen.getByText('Record cash movement'));
  await screen.findByText('Cash movement recorded.');
  await screen.findByText('110.00');
  expect(within(screen.getByLabelText('Cash movement history')).getByText(/Move to safe/)).toBeTruthy();
  const call = vi.mocked(api).mock.calls.find(call => call[1]);
  expect(call?.[0]).toBe('/sessions/shift-1/cash-movements');
  expect(call?.[1]).toEqual({ type: 'SAFE_DROP', amount: '25', reason: 'Move to safe' });
  expect(call?.[2]).toBeTruthy();
  expect(sessionStorage.getItem('pos-drawer-attempt-v1:shift-1')).toBeNull();
});
it('restores an uncertain attempt on reload and retries exactly the same payload and key', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new Error('Lost response'); return report; });
  const view = render(<CashDrawer {...props} />);
  await screen.findByText('135.00');
  fillMovement(); fireEvent.click(screen.getByText('Record cash movement'));
  await screen.findByText(/outcome is uncertain/);
  expect(screen.queryByText('Cash movement recorded.')).toBeNull();
  const saved = sessionStorage.getItem('pos-drawer-attempt-v1:shift-1');
  expect(saved).toBeTruthy(); view.unmount();
  render(<CashDrawer {...props} />);
  fireEvent.click(screen.getByText('Retry same cash movement'));
  await waitFor(() => expect(vi.mocked(api).mock.calls.filter(call => call[1])).toHaveLength(2));
  const calls = vi.mocked(api).mock.calls.filter(call => call[1]);
  expect(calls[0]).toEqual(calls[1]);
  expect(props.onPendingChange).toHaveBeenCalledWith(true);
});
it('permits correction after a definite server rejection', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new ApiError(409); return report; });
  render(<CashDrawer {...props} />);
  fillMovement(); fireEvent.click(screen.getByText('Record cash movement'));
  await screen.findByText(/Movement rejected/);
  expect(sessionStorage.getItem('pos-drawer-attempt-v1:shift-1')).toBeNull();
  expect((screen.getByLabelText('Movement amount') as HTMLInputElement).disabled).toBe(false);
  expect(props.onPendingChange).toHaveBeenLastCalledWith(false);
});
it('keeps an uncertain movement when a retry loses authorization', async () => {
  const attempt = { key: 'saved-attempt', body: { type: 'CASH_IN', amount: '10', reason: 'Float' } };
  sessionStorage.setItem('pos-drawer-attempt-v1:shift-1', JSON.stringify(attempt));
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new ApiError(403); return report; });
  render(<CashDrawer {...props} />);
  fireEvent.click(screen.getByText('Retry same cash movement'));
  await screen.findByText(/outcome is uncertain/);
  expect(JSON.parse(sessionStorage.getItem('pos-drawer-attempt-v1:shift-1')!)).toEqual(attempt);
  expect(props.onPendingChange).not.toHaveBeenCalledWith(false);
});
