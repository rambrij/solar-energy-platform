import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { usePermissions, hasNavigationPermission, NAVIGATION_PERMISSIONS } from './usePermissions';
import { navigationItems } from './navigation';

// Mock the authenticated identity endpoint.
const { mockAuthApi } = vi.hoisted(() => ({ mockAuthApi: vi.fn() }));
vi.mock('./api', () => ({ authApi: mockAuthApi }));

describe('usePermissions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches permissions from /auth/me', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'cashier@vendor.com',
      tenantId: 1,
      branchId: 10,
      permissions: ['POS_VIEW', 'POS_SALE_CREATE'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(mockAuthApi).toHaveBeenCalledWith('/api/v1/auth/me');
    expect(result.current.permissions?.username).toBe('cashier@vendor.com');
    expect(result.current.permissions?.tenantId).toBe(1);
    expect(result.current.permissions?.branchId).toBe(10);
    expect(result.current.permissions?.permissions).toEqual(['POS_VIEW', 'POS_SALE_CREATE']);
  });

  it('hasPermission returns true for granted permissions', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'cashier@vendor.com',
      tenantId: 1,
      branchId: 10,
      permissions: ['POS_VIEW', 'POS_SALE_CREATE'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.hasPermission('POS_VIEW')).toBe(true);
      expect(result.current.hasPermission('POS_SALE_CREATE')).toBe(true);
      expect(result.current.hasPermission('POS_REFUND_APPROVE')).toBe(false);
    });
  });

  it('hasAnyPermission returns true when user has at least one', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'cashier@vendor.com',
      tenantId: 1,
      branchId: 10,
      permissions: ['POS_VIEW', 'POS_SALE_CREATE'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.hasAnyPermission('POS_VIEW', 'POS_REFUND_APPROVE')).toBe(true);
      expect(result.current.hasAnyPermission('POS_REFUND_APPROVE', 'POS_SHIFT_VARIANCE_APPROVE')).toBe(false);
    });
  });

  it('hasAllPermissions returns true only when user has all', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'cashier@vendor.com',
      tenantId: 1,
      branchId: 10,
      permissions: ['POS_VIEW', 'POS_SALE_CREATE'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.hasAllPermissions('POS_VIEW', 'POS_SALE_CREATE')).toBe(true);
      expect(result.current.hasAllPermissions('POS_VIEW', 'POS_REFUND_APPROVE')).toBe(false);
    });
  });

  it('identifies vendor employees correctly', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'cashier@vendor.com',
      tenantId: 1,
      branchId: 10,
      permissions: ['POS_VIEW'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.isVendorEmployee).toBe(true);
      expect(result.current.isCustomer).toBe(false);
      expect(result.current.isPlatformUser).toBe(false);
    });
  });

  it('identifies customers correctly', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'customer@example.com',
      tenantId: null,
      branchId: null,
      permissions: ['CUSTOMER'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.isCustomer).toBe(true);
      expect(result.current.isVendorEmployee).toBe(false);
      expect(result.current.isPlatformUser).toBe(false);
    });
  });

  it('identifies platform users correctly', async () => {
    mockAuthApi.mockResolvedValue({
      username: 'admin@platform.com',
      tenantId: null,
      branchId: null,
      permissions: ['PLATFORM_SUPER_ADMIN'],
    });

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.isPlatformUser).toBe(true);
      expect(result.current.isCustomer).toBe(false);
      expect(result.current.isVendorEmployee).toBe(false);
    });
  });

  it('handles API errors gracefully', async () => {
    mockAuthApi.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => usePermissions());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.permissions).toBeNull();
    });
  });
});

describe('navigation permission coverage', () => {
  it('defines an explicit permission rule for every workspace item', () => {
    for (const mode of ['pos', 'online'] as const) {
      for (const item of navigationItems(mode)) {
        expect(NAVIGATION_PERMISSIONS[item.id], item.id).toBeDefined();
      }
    }
  });
});

describe('hasNavigationPermission', () => {
  it('grants access to POS checkout with POS_VIEW', () => {
    expect(hasNavigationPermission('checkout', ['POS_VIEW'])).toBe(true);
    expect(hasNavigationPermission('register', ['POS_VIEW'])).toBe(true);
    expect(hasNavigationPermission('product-management', ['PRODUCT_VIEW'])).toBe(true);
  });

  it('grants access with any of the required permissions (anyOf)', () => {
    // checkout requires: anyOf: ['POS_VIEW', 'POS_SESSION_OPEN', 'POS_SALE_CREATE']
    expect(hasNavigationPermission('checkout', ['POS_VIEW'])).toBe(true);
    expect(hasNavigationPermission('checkout', ['POS_SESSION_OPEN'])).toBe(true);
    expect(hasNavigationPermission('checkout', ['POS_SALE_CREATE'])).toBe(true);
  });

  it('denies access to refunds without POS_REFUND_VIEW', () => {
    expect(hasNavigationPermission('cash-refunds', ['POS_VIEW'])).toBe(false);
    expect(hasNavigationPermission('cash-refunds', ['POS_REFUND_APPROVE'])).toBe(false);
  });

  it('requires all permissions when allOf specified', () => {
    // cash-refunds requires: allOf: ['POS_VIEW', 'POS_REFUND_VIEW']
    expect(hasNavigationPermission('cash-refunds', ['POS_VIEW', 'POS_REFUND_VIEW'])).toBe(true);
    expect(hasNavigationPermission('cash-refunds', ['POS_VIEW'])).toBe(false);
    expect(hasNavigationPermission('cash-refunds', ['POS_REFUND_VIEW'])).toBe(false);
  });

  it('denies access to supervisor features without approval permissions', () => {
    expect(hasNavigationPermission('shift-reviews', ['POS_VIEW'])).toBe(false);
    expect(hasNavigationPermission('shift-reviews', ['POS_SHIFT_VARIANCE_APPROVE'])).toBe(true);
    
    expect(hasNavigationPermission('terminal-tender-review', ['POS_VIEW'])).toBe(false);
    expect(hasNavigationPermission('terminal-tender-review', ['POS_TENDER_MANUAL_VERIFY'])).toBe(true);
  });

  it('grants access to marketplace items with CUSTOMER permission', () => {
    expect(hasNavigationPermission('vendor-directory', ['CUSTOMER'])).toBe(true);
    expect(hasNavigationPermission('storefront-products', ['CUSTOMER'])).toBe(true);
    expect(hasNavigationPermission('customer-cart', ['CUSTOMER'])).toBe(true);
    expect(hasNavigationPermission('customer-purchases', ['CUSTOMER'])).toBe(true);
  });

  it('denies by default for unmapped items', () => {
    expect(hasNavigationPermission('unknown-item', ['POS_VIEW'])).toBe(false);
    expect(hasNavigationPermission('unknown-item', ['ADMIN'])).toBe(false);
  });

  it('denies access when user has no permissions', () => {
    expect(hasNavigationPermission('checkout', [])).toBe(false);
    expect(hasNavigationPermission('cash-refunds', [])).toBe(false);
    expect(hasNavigationPermission('vendor-directory', [])).toBe(false);
  });
});
