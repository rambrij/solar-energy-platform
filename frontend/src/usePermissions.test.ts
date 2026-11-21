import {renderHook,waitFor} from '@testing-library/react';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {usePermissions,hasNavigationPermission,NAVIGATION_PERMISSIONS,type UserPermissions} from './usePermissions';
import {navigationItems} from './navigation';

const {mockAuthApi,mockUseSession}=vi.hoisted(()=>({mockAuthApi:vi.fn(),mockUseSession:vi.fn()}));
vi.mock('./api',()=>({authApi:mockAuthApi}));
vi.mock('./session',()=>({useSession:mockUseSession}));

const vendorAccount:UserPermissions={username:'cashier@vendor.com',tenantId:1,branchId:10,permissions:['POS_VIEW','POS_SALE_CREATE'],actorType:'VENDOR'};
const customerAccount:UserPermissions={username:'customer@example.com',tenantId:null,branchId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER'};
const platformAccount:UserPermissions={username:'admin@platform.com',tenantId:null,branchId:null,permissions:['PLATFORM_SUPER_ADMIN'],actorType:'PLATFORM'};

describe('usePermissions',()=>{
 beforeEach(()=>{vi.clearAllMocks();mockUseSession.mockReturnValue({ready:true,account:vendorAccount});});
 function response(account=vendorAccount){return {...account};}
 it('fetches permissions from /auth/me and verifies the active actor context',async()=>{
  mockAuthApi.mockResolvedValue(response());
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.loading).toBe(false));
  expect(mockAuthApi).toHaveBeenCalledWith('/api/v1/auth/me');
  expect(result.current.permissions?.username).toBe(vendorAccount.username);
  expect(result.current.permissions?.tenantId).toBe(1);
  expect(result.current.permissions?.branchId).toBe(10);
  expect(result.current.permissions?.permissions).toEqual(vendorAccount.permissions);
  expect(result.current.permissions?.actorType).toBe('VENDOR');
 });
 it('hasPermission returns true only for granted permissions',async()=>{
  mockAuthApi.mockResolvedValue(response());
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.loading).toBe(false));
  expect(result.current.hasPermission('POS_VIEW')).toBe(true);
  expect(result.current.hasPermission('POS_REFUND_APPROVE')).toBe(false);
 });
 it('hasAnyPermission and hasAllPermissions use server-resolved grants',async()=>{
  mockAuthApi.mockResolvedValue(response());
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.loading).toBe(false));
  expect(result.current.hasAnyPermission('POS_VIEW','POS_REFUND_APPROVE')).toBe(true);
  expect(result.current.hasAllPermissions('POS_VIEW','POS_SALE_CREATE')).toBe(true);
  expect(result.current.hasAllPermissions('POS_VIEW','POS_REFUND_APPROVE')).toBe(false);
 });
 it('identifies vendor employees using explicit actor type',async()=>{
  mockAuthApi.mockResolvedValue(response());
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.isVendorEmployee).toBe(true));
  expect(result.current.isCustomer).toBe(false);
  expect(result.current.isPlatformUser).toBe(false);
 });
 it('identifies customers using explicit actor type',async()=>{
  mockUseSession.mockReturnValue({ready:true,account:customerAccount});
  mockAuthApi.mockResolvedValue(response(customerAccount));
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.isCustomer).toBe(true));
  expect(result.current.isVendorEmployee).toBe(false);
  expect(result.current.isPlatformUser).toBe(false);
 });
 it('identifies platform users using explicit actor type',async()=>{
  mockUseSession.mockReturnValue({ready:true,account:platformAccount});
  mockAuthApi.mockResolvedValue(response(platformAccount));
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.isPlatformUser).toBe(true));
  expect(result.current.isCustomer).toBe(false);
  expect(result.current.isVendorEmployee).toBe(false);
 });
 it('fails closed when /auth/me differs from the active account snapshot',async()=>{
  mockAuthApi.mockResolvedValue(response(customerAccount));
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.loading).toBe(false));
  expect(result.current.permissions).toBeNull();
  expect(result.current.error?.message).toContain('actor context changed');
 });
 it('still resolves server identity when the client session snapshot is not hydrated yet',async()=>{
  mockUseSession.mockReturnValue({ready:false,account:null});
  mockAuthApi.mockResolvedValue(response(customerAccount));
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.isCustomer).toBe(true));
  expect(mockAuthApi).toHaveBeenCalledWith('/api/v1/auth/me');
 });
 it('handles API errors gracefully',async()=>{
  mockAuthApi.mockRejectedValue(new Error('Network error'));
  const {result}=renderHook(()=>usePermissions());
  await waitFor(()=>expect(result.current.loading).toBe(false));
  expect(result.current.permissions).toBeNull();
  expect(result.current.error?.message).toBe('Network error');
 });
});

describe('navigation permission coverage',()=>{
 it('defines an explicit permission rule for every workspace item',()=>{
  for(const mode of ['pos','online'] as const)for(const item of navigationItems(mode))expect(NAVIGATION_PERMISSIONS[item.id],item.id).toBeDefined();
 });
});

describe('hasNavigationPermission',()=>{
 it('grants access to POS checkout with POS_VIEW',()=>{
  expect(hasNavigationPermission('checkout',['POS_VIEW'])).toBe(true);
  expect(hasNavigationPermission('register',['POS_VIEW'])).toBe(true);
  expect(hasNavigationPermission('product-management',['PRODUCT_VIEW'])).toBe(true);
 });
 it('requires POS_VIEW to enter the checkout workspace',()=>{
  expect(hasNavigationPermission('checkout',['POS_VIEW'])).toBe(true);
  expect(hasNavigationPermission('checkout',['POS_SESSION_OPEN'])).toBe(false);
  expect(hasNavigationPermission('checkout',['POS_SALE_CREATE'])).toBe(false);
 });
 it('denies access to refunds without POS_REFUND_VIEW',()=>{
  expect(hasNavigationPermission('cash-refunds',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('cash-refunds',['POS_REFUND_APPROVE'])).toBe(false);
 });
 it('requires all permissions when allOf is specified',()=>{
  expect(hasNavigationPermission('cash-refunds',['POS_VIEW','POS_REFUND_VIEW'])).toBe(true);
  expect(hasNavigationPermission('cash-refunds',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('cash-refunds',['POS_REFUND_VIEW'])).toBe(false);
 });
 it('allows update-only product and category users to enter their pages',()=>{
  expect(hasNavigationPermission('product-management',['PRODUCT_UPDATE'])).toBe(true);
  expect(hasNavigationPermission('category-management',['PRODUCT_UPDATE'])).toBe(true);
  expect(hasNavigationPermission('product-management',['PRODUCT_VIEW'])).toBe(true);
  expect(hasNavigationPermission('category-management',['PRODUCT_CREATE'])).toBe(true);
 });
 it('allows cashier-owned shift history with POS_VIEW',()=>{
  expect(hasNavigationPermission('shift-history',['POS_VIEW'])).toBe(true);
  expect(hasNavigationPermission('shift-history',['POS_REPORT_VIEW'])).toBe(false);
 });
 it('denies supervisor features without approval permissions',()=>{
  expect(hasNavigationPermission('shift-reviews',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('shift-reviews',['POS_SHIFT_VARIANCE_APPROVE'])).toBe(true);
  expect(hasNavigationPermission('terminal-tender-review',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('terminal-tender-review',['POS_TENDER_MANUAL_VERIFY'])).toBe(true);
 });
 it('grants marketplace items to a CUSTOMER context',()=>{
  expect(hasNavigationPermission('vendor-directory',['CUSTOMER'])).toBe(true);
  expect(hasNavigationPermission('storefront-products',['CUSTOMER'])).toBe(true);
  expect(hasNavigationPermission('customer-cart',['CUSTOMER'])).toBe(true);
  expect(hasNavigationPermission('customer-purchases',['CUSTOMER'])).toBe(true);
 });
 it('denies unknown items and empty permission sets',()=>{
  expect(hasNavigationPermission('unknown-item',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('unknown-item',['ADMIN'])).toBe(false);
  expect(hasNavigationPermission('checkout',[])).toBe(false);
  expect(hasNavigationPermission('cash-refunds',[])).toBe(false);
  expect(hasNavigationPermission('vendor-directory',[])).toBe(false);
 });
});
