import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ProductDetails from './ProductDetails';
import { marketplaceApi } from './marketplaceApi';
vi.mock('./marketplaceApi', () => ({ marketplaceApi: vi.fn() }));
const product = { id: 1, vendorSlug: 'solar', vendorName: 'Solar store', sku: 'PV-540', name: '540W Panel', unitPrice: 100, taxRate: 10, unitTax: 10, unitTotal: 110, inStock: true };
afterEach(() => { cleanup(); vi.resetAllMocks(); });
it('loads public vendor detail, shows tax-inclusive unit total and submits an absolute quantity', async () => {
  vi.mocked(marketplaceApi).mockResolvedValue(product); const add = vi.fn();
  render(<ProductDetails slug="solar" productId={1} disabled={false} onClose={vi.fn()} onAdd={add}/>);
  await screen.findByText('540W Panel'); expect(marketplaceApi).toHaveBeenCalledWith('/api/marketplace/vendors/solar/products/1'); expect(screen.getByText('110.00')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Product detail quantity'), { target: { value: '3' } }); fireEvent.click(screen.getByText('Add or update this product in cart'));
  expect(add).toHaveBeenCalledWith(1, 3);
});
it('blocks out-of-stock and invalid quantities', async () => {
  vi.mocked(marketplaceApi).mockResolvedValue(product); const view = render(<ProductDetails slug="solar" productId={1} disabled={false} onClose={vi.fn()} onAdd={vi.fn()}/>);
  await screen.findByText('540W Panel');
  for (const value of ['0', '1.5', '10001']) { fireEvent.change(screen.getByLabelText('Product detail quantity'), { target: { value } }); expect((screen.getByText('Add or update this product in cart') as HTMLButtonElement).disabled).toBe(true); }
  vi.mocked(marketplaceApi).mockResolvedValue({ ...product, id: 2, inStock: false }); view.rerender(<ProductDetails slug="solar" productId={2} disabled={false} onClose={vi.fn()} onAdd={vi.fn()}/>);
  await screen.findByText('Out of stock'); expect((screen.getByText('Add or update this product in cart') as HTMLButtonElement).disabled).toBe(true);
});
it('offers retry on a withdrawn or unavailable product without changing the cart', async () => {
  vi.mocked(marketplaceApi).mockRejectedValueOnce(new Error('Not found')).mockResolvedValueOnce(product); const add = vi.fn();
  render(<ProductDetails slug="solar" productId={1} disabled={false} onClose={vi.fn()} onAdd={add}/>);
  await screen.findByRole('alert'); fireEvent.click(screen.getByText('Retry product details')); await screen.findByText('540W Panel'); expect(add).not.toHaveBeenCalled();
});
it('focuses close and restores the original focus after dismissal', async () => {
  vi.mocked(marketplaceApi).mockResolvedValue(product); const original = document.createElement('button'); document.body.appendChild(original); original.focus();
  const close = vi.fn(); const view = render(<ProductDetails slug="solar" productId={1} disabled={false} onClose={close} onAdd={vi.fn()}/>);
  await screen.findByText('540W Panel'); expect(document.activeElement).toBe(screen.getByText('Close product details'));
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true })); expect(close).toHaveBeenCalledOnce(); view.unmount(); expect(document.activeElement).toBe(original); original.remove();
});
it('ignores stale detail responses after the selected product changes', async () => {
  let finish!: (value: unknown) => void;
  vi.mocked(marketplaceApi).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce({ ...product, id: 2, name: 'Inverter' });
  const view = render(<ProductDetails slug="solar" productId={1} disabled={false} onClose={vi.fn()} onAdd={vi.fn()}/>);
  view.rerender(<ProductDetails slug="solar" productId={2} disabled={false} onClose={vi.fn()} onAdd={vi.fn()}/>); await screen.findByText('Inverter'); finish(product);
  await waitFor(() => expect(screen.queryByText('540W Panel')).toBeNull());
});
