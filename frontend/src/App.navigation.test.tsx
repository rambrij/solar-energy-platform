import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import App from './App';
import { api } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));

afterEach(() => { cleanup(); vi.resetAllMocks(); sessionStorage.clear(); window.history.replaceState({}, '', '/vendor/pos'); });

it('renders only the selected POS workspace page instead of stacking operations', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  vi.mocked(api).mockImplementation(async path => {
    if (path === '/sessions/active') return [];
    if (path.startsWith('/products')) return [];
    return [];
  });
  render(<App />);
  await screen.findByRole('heading', { name: 'Manage Products' });
  expect(screen.queryByText('Opening float')).toBeNull();
  expect(screen.queryByText('Supervisor shift review')).toBeNull();
});
