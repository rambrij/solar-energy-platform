import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ReservedDrafts from './ReservedDrafts';
import { api } from './api';
import { marketplaceApi } from './marketplaceApi';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
vi.mock('./marketplaceApi', async original => ({ ...await original<typeof import('./marketplaceApi')>(), marketplaceApi: vi.fn() }));
const draft = { id: 'd1', vendorName: 'Vendor A', channel: 'IN_STORE', status: 'DRAFT', total: 110, expiresAt: '2030-01-01T00:00:00Z', message: 'Unpaid reservation', items: [{ productId: 1, name: 'Panel', quantity: 1, unitPrice: 100, tax: 10, total: 110 }] };
afterEach(() => { cleanup(); vi.resetAllMocks(); sessionStorage.clear(); });
it('does not prompt anonymous marketplace visitors until they reserve or load drafts', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async (_path, method) => method === 'POST' ? { ...draft, channel: 'ONLINE' } : [{ ...draft, channel: 'ONLINE' }]);
  render(<ReservedDrafts mode="online" cartId="cart-1" />);
  expect(marketplaceApi).not.toHaveBeenCalled(); fireEvent.click(screen.getByText('Reserve cart without payment'));
  await screen.findByText('Vendor A · DRAFT');
  expect(marketplaceApi).toHaveBeenCalledWith('/api/customer/checkout/drafts', 'POST', { expectedCartId: 'cart-1' }, expect.any(String));
  expect(screen.queryByText('Complete reserved cash sale')).toBeNull();
});
it('persists the original create request and key across reloads and edited selections', async () => {
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) throw new Error('Lost response'); return []; });
  const onBlocked = vi.fn();
  const view = render(<ReservedDrafts mode="pos" session="s1" items={[{ productId: 1, quantity: 1 }]} onBlocked={onBlocked} />);
  fireEvent.click(screen.getByText('Reserve walk-in sale without payment')); await screen.findByText(/Draft outcome is uncertain/);
  view.unmount(); render(<ReservedDrafts mode="pos" session="s1" items={[{ productId: 2, quantity: 3 }]} onBlocked={onBlocked} />);
  fireEvent.click(screen.getByText('Retry same draft operation'));
  await waitFor(() => expect(vi.mocked(api).mock.calls.filter(call => call[1])).toHaveLength(2));
  const calls = vi.mocked(api).mock.calls.filter(call => call[1]); expect(calls[0]).toEqual(calls[1]); expect(onBlocked).toHaveBeenCalledWith(true);
});
it('finalizes cash into a receipt and keeps receipt recovery separate from a new sale', async () => {
  let paid = false; const receipt = { id: 'd1', total: 110, cash_received: 120, change_given: 10 };
  vi.mocked(api).mockImplementation(async path => {
    if (path.endsWith('/finalize-cash')) { paid = true; return receipt; }
    if (path.startsWith('/receipts/')) return receipt;
    return [{ ...draft, status: paid ? 'PAID' : 'DRAFT' }];
  });
  const onReceipt = vi.fn(); render(<ReservedDrafts mode="pos" session="s1" onReceipt={onReceipt} />);
  await screen.findByText('Vendor A · DRAFT'); fireEvent.change(screen.getByLabelText('Cash received for reserved sale'), { target: { value: '120' } });
  fireEvent.click(screen.getByText('Complete reserved cash sale')); await screen.findByText('Recover cash receipt');
  expect(onReceipt).toHaveBeenCalledWith(receipt, true);
  fireEvent.click(screen.getByText('Recover cash receipt')); await waitFor(() => expect(onReceipt).toHaveBeenLastCalledWith(receipt, false));
});
it('requires a cancellation reason and reloads released stock state', async () => {
  let cancelled = false;
  vi.mocked(api).mockImplementation(async (_path, body) => { if (body) { cancelled = true; return { ...draft, status: 'CANCELLED' }; } return [{ ...draft, status: cancelled ? 'CANCELLED' : 'DRAFT' }]; });
  render(<ReservedDrafts mode="pos" session="s1" />); await screen.findByText('Cancel draft d1');
  expect((screen.getByText('Cancel draft d1') as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('Cancellation reason'), { target: { value: 'Customer changed mind' } });fireEvent.click(screen.getByText('Cancel draft d1'));
  await screen.findByText('Vendor A · CANCELLED');expect(api).toHaveBeenCalledWith('/drafts/d1/cancel', { reason: 'Customer changed mind' }, undefined);
});
it('retains an uncertain cash finalization and retries the same order and received amount', async () => {
  const attempt = { action: 'cash', id: 'd1', body: { cashReceived: '120' } };
  sessionStorage.setItem('pos-reserved-attempt-v1:s1', JSON.stringify(attempt));
  const receipt = { id: 'd1' };vi.mocked(api).mockImplementation(async (path, body) => body ? receipt : [{ ...draft, status: 'PAID' }]);
  const onReceipt = vi.fn();render(<ReservedDrafts mode="pos" session="s1" disabled onReceipt={onReceipt} />);
  fireEvent.click(screen.getByText('Retry same draft operation'));await waitFor(() => expect(onReceipt).toHaveBeenCalledWith(receipt, true));
  expect(api).toHaveBeenCalledWith('/drafts/d1/finalize-cash', { cashReceived: '120' }, undefined);
  expect(sessionStorage.getItem('pos-reserved-attempt-v1:s1')).toBeNull();
});
it('allows explicit local reconciliation when the server confirms an expired unpaid draft', async () => {
  sessionStorage.setItem('pos-reserved-attempt-v1:s1',JSON.stringify({action:'cash',id:'d1',body:{cashReceived:'120'}}));
  vi.mocked(api).mockResolvedValue([{...draft,status:'EXPIRED'}]);
  render(<ReservedDrafts mode="pos" session="s1" />); await screen.findByText('Acknowledge resolved draft status');
  fireEvent.click(screen.getByText('Acknowledge resolved draft status')); expect(sessionStorage.getItem('pos-reserved-attempt-v1:s1')).toBeNull();
});
