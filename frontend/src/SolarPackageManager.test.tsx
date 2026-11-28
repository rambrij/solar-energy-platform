import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import SolarPackageManager from './SolarPackageManager';
import { vendorApi } from './api';
import { authenticatedFetch, useSession } from './session';

vi.mock('./api', () => ({ vendorApi: vi.fn(), vendorUpload: vi.fn() }));
vi.mock('./session', () => ({ authenticatedFetch: vi.fn(), useSession: vi.fn() }));

const imageId = '7df8b7b1-696f-42a6-a700-43cf9e6f139e';
const createObjectURL = vi.fn(() => 'blob:preview');
const revokeObjectURL = vi.fn();

beforeEach(() => {
 const BrowserURL = URL;
 class TestURL extends BrowserURL {
  static createObjectURL = createObjectURL;
  static revokeObjectURL = revokeObjectURL;
 }
 vi.stubGlobal('URL', TestURL);
 vi.mocked(useSession).mockReturnValue({ account: { permissions: ['PRODUCT_VIEW'] } } as ReturnType<typeof useSession>);
 vi.mocked(vendorApi).mockImplementation(async path => {
  if (path.startsWith('/products?')) return { items: [{ id: 7, sku: 'PV7', name: 'Solar panel' }] };
  if (path.startsWith('/packages?')) return { items: [] };
  if (path === '/products/7/images') return [{ id: imageId, altText: 'Panel front', byteSize: 1024 }];
  throw new Error('Unexpected request ' + path);
 });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); vi.unstubAllGlobals(); });

it('loads preview bytes with the authenticated helper and releases the preview on exit', async () => {
 createObjectURL.mockReturnValue('blob:preview');
 vi.mocked(authenticatedFetch).mockResolvedValue(new Response(new Blob(['demo'], { type: 'image/png' })));
 const { unmount } = render(<SolarPackageManager />);
 await screen.findByRole('option', { name: 'Solar panel (PV7)' });
 fireEvent.change(screen.getByLabelText('Select product'), { target: { value: '7' } });
 const preview = await screen.findByRole('img', { name: 'Panel front' });
 expect(preview.getAttribute('src')).toBe('blob:preview');
 expect(authenticatedFetch).toHaveBeenCalledWith('/api/v1/vendor/products/7/images/' + imageId,
  expect.objectContaining({ method: 'GET', cache: 'no-store' }));
 expect(screen.queryByRole('button', { name: 'Upload product image' })).toBeNull();
 expect(screen.queryByRole('button', { name: 'Remove image' })).toBeNull();
 unmount();
 expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview');
});

it('shows an unavailable preview when the API denies image access', async () => {
 vi.mocked(authenticatedFetch).mockResolvedValue(new Response(null, { status: 403 }));
 render(<SolarPackageManager />);
 await screen.findByRole('option', { name: 'Solar panel (PV7)' });
 fireEvent.change(screen.getByLabelText('Select product'), { target: { value: '7' } });
 await screen.findByText('Preview unavailable: Panel front');
 expect(screen.queryByRole('img')).toBeNull();
 expect(createObjectURL).not.toHaveBeenCalled();
});
