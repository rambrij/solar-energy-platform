import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import Shell from './Shell';

afterEach(() => { cleanup(); window.history.replaceState({}, '', '/'); });

it('renders route-based links, active state and navigable breadcrumbs', () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  render(<Shell mode="pos"><section><h2>Manage product catalogue</h2></section></Shell>);
  expect(screen.getByRole('link', { name: 'Manage Products' }).getAttribute('href')).toBe('/vendor/pos/products');
  expect(screen.getByRole('link', { name: 'Manage Products' }).getAttribute('aria-current')).toBe('page');
  expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByText('Catalogue & Inventory')).toBeTruthy();
  expect(screen.queryByRole('navigation', { name: 'Workspace tabs' })).toBeNull();
});

it('updates the URL and selected workspace using sidebar navigation', () => {
  render(<Shell mode="online"><section><h2>Marketplace page</h2></section></Shell>);
  fireEvent.click(screen.getByRole('link', { name: 'My Single-Vendor Cart' }));
  expect(window.location.pathname).toBe('/marketplace/cart');
  expect(screen.getByRole('link', { name: 'My Single-Vendor Cart' }).getAttribute('aria-current')).toBe('page');
});
