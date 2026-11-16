import { useState, useEffect } from 'react';
import { authApi } from './api';

/**
 * User permissions and authorization context from backend.
 * Resolved from JWT claims and server-side membership, never from client state.
 */
export interface UserPermissions {
  username: string;
  tenantId: number | null;
  branchId: number | null;
  permissions: string[];
}

/**
 * Hook to access current user's permissions.
 * Fetches from /api/v1/auth/me endpoint which resolves from JWT.
 */
export function usePermissions(): {
  permissions: UserPermissions | null;
  loading: boolean;
  error: Error | null;
  hasPermission: (permission: string) => boolean;
  hasAnyPermission: (...permissions: string[]) => boolean;
  hasAllPermissions: (...permissions: string[]) => boolean;
  isPlatformUser: boolean;
  isCustomer: boolean;
  isVendorEmployee: boolean;
} {
  const [permissions, setPermissions] = useState<UserPermissions | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let mounted = true;

    async function fetchPermissions() {
      try {
        const response = await authApi('/api/v1/auth/me');
        if (mounted) {
          setPermissions(response);
          setLoading(false);
        }
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err : new Error('Failed to load permissions'));
          setLoading(false);
        }
      }
    }

    fetchPermissions();

    return () => {
      mounted = false;
    };
  }, []);

  const hasPermission = (permission: string): boolean => {
    return permissions?.permissions.includes(permission) ?? false;
  };

  const hasAnyPermission = (...perms: string[]): boolean => {
    if (!permissions) return false;
    return perms.some(p => permissions.permissions.includes(p));
  };

  const hasAllPermissions = (...perms: string[]): boolean => {
    if (!permissions) return false;
    return perms.every(p => permissions.permissions.includes(p));
  };

  const isPlatformUser = permissions?.permissions.some(p => p.startsWith('PLATFORM_')) ?? false;
  const isCustomer = permissions?.permissions.includes('CUSTOMER') ?? false;
  const isVendorEmployee = permissions?.tenantId != null && !isPlatformUser;

  return {
    permissions,
    loading,
    error,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    isPlatformUser,
    isCustomer,
    isVendorEmployee,
  };
}

/**
 * Permission-aware navigation item filtering.
 * Only show menu items when user has required permission.
 */
export interface PermissionRequirement {
  /** At least one of these permissions required (OR logic) */
  anyOf?: string[];
  /** All of these permissions required (AND logic) */
  allOf?: string[];
}

export const NAVIGATION_PERMISSIONS: Record<string, PermissionRequirement> = {
  // POS workspace navigation permissions
  'checkout': { allOf: ['POS_VIEW'] },
  'current-sale': { allOf: ['POS_VIEW'] },
  'reserved-pos-checkout': { allOf: ['POS_VIEW'] },
  'order-history': { allOf: ['POS_VIEW'] },
  'sale-receipt': { anyOf: ['POS_RECEIPT_VIEW'] },
  'pos-returns': { allOf: ['POS_VIEW', 'POS_RETURN_VIEW'] },
  'cash-refunds': { allOf: ['POS_VIEW', 'POS_REFUND_VIEW'] },
  'category-management': { anyOf: ['PRODUCT_VIEW', 'PRODUCT_CREATE', 'PRODUCT_UPDATE'] },
  'product-management': { anyOf: ['PRODUCT_VIEW', 'PRODUCT_CREATE', 'PRODUCT_UPDATE'] },
  'stock-receiving': { anyOf: ['INVENTORY_VIEW', 'INVENTORY_RECEIVE'] },
  'register': { allOf: ['POS_VIEW'] },
  'shift-history': { allOf: ['POS_VIEW'] },
  'shift-reviews': { anyOf: ['POS_SHIFT_VARIANCE_APPROVE'] },
  'terminal-tender-review': { anyOf: ['POS_TENDER_MANUAL_VERIFY'] },
  'store-settings': { allOf: ['POS_VIEW'] },
  'promotion-history': { allOf: ['POS_VIEW', 'CATALOG_PROMOTION_MANAGE', 'POS_DISCOUNT_APPROVE'] },
  'sales-insights': { allOf: ['POS_VIEW', 'POS_REPORT_VIEW'] },
  'refund-insights': { allOf: ['POS_VIEW', 'POS_REPORT_VIEW', 'POS_REFUND_VIEW'] },
  'cashier-sales-insights': { allOf: ['POS_VIEW', 'POS_REPORT_VIEW', 'POS_CASHIER_REPORT_VIEW'] },

  // Online marketplace navigation (customer permissions)
  'vendor-directory': { anyOf: ['CUSTOMER'] }, // Public/customer access
  'storefront-products': { anyOf: ['CUSTOMER'] },
  'customer-cart': { anyOf: ['CUSTOMER'] },
  'online-checkout': { anyOf: ['CUSTOMER'] },
  'saved-vendor-carts': { anyOf: ['CUSTOMER'] },
  'customer-purchases': { anyOf: ['CUSTOMER'] },
};

/**
 * Check if user has permission to access a navigation item.
 */
export function hasNavigationPermission(
  itemId: string,
  userPermissions: string[]
): boolean {
  const requirement = NAVIGATION_PERMISSIONS[itemId];
  if (!requirement) {
    // No explicit requirement - deny by default for security
    return false;
  }

  const hasAny = requirement.anyOf
    ? requirement.anyOf.some(permission => userPermissions.includes(permission))
    : true;
  const hasAll = requirement.allOf
    ? requirement.allOf.every(permission => userPermissions.includes(permission))
    : true;

  return hasAny && hasAll && Boolean(requirement.anyOf || requirement.allOf);
}
