import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import WorkspaceErrorBoundary from './WorkspaceErrorBoundary';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('shows a recoverable workspace fallback without exposing render details', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  function BrokenPanel(): never {
    throw new Error('internal implementation detail');
  }

  render(<WorkspaceErrorBoundary><BrokenPanel /></WorkspaceErrorBoundary>);

  expect(screen.getByRole('alert')).toBeTruthy();
  expect(screen.getByRole('heading', { name: 'This page could not be displayed' })).toBeTruthy();
  expect(screen.getByText(/sign-in and workspace permissions have not been changed/i)).toBeTruthy();
  expect(screen.queryByText('internal implementation detail')).toBeNull();
  expect(screen.getByRole('button', { name: 'Reload workspace' })).toBeTruthy();
});
