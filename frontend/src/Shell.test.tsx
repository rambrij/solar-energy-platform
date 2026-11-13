import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import Shell from './Shell';
afterEach(cleanup);
it('provides keyboard navigation and identifies the active workspace', () => {
  render(<Shell mode="online"><h1>Explore solar</h1></Shell>);
  expect(screen.getByText('Skip to content').getAttribute('href')).toBe('#main-content');
  expect(screen.getByRole('main').id).toBe('main-content');
  expect(screen.getByRole('link', { name: 'Marketplace' }).getAttribute('aria-current')).toBe('page');
  expect(screen.getByRole('link', { name: 'My orders' }).getAttribute('href')).toBe('#order-history');
});
