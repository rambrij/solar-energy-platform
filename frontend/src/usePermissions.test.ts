import {renderHook} from '@testing-library/react';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import {usePermissions,hasNavigationPermission,NAVIGATION_PERMISSIONS,type UserPermissions,PLATFORM_PAGE_PERMISSIONS,hasPlatformPagePermission} from './usePermissions';
import {navigationItems} from './navigation';

const {mockUseSession}=vi.hoisted(()=>({mockUseSession:vi.fn()}));
vi.mock('./session',()=>({useSession:mockUseSession}));

const vendorAccount:UserPermissions={username:'cashier@vendor.com',tenantId:1,branchId:10,membershipId:'1:cashier@vendor.com',permissions:['POS_VIEW','POS_SALE_CREATE'],actorType:'VENDOR'};
const customerAccount:UserPermissions={username:'customer@example.com',tenantId:null,branchId:null,membershipId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER'};
const platformAccount:UserPermissions={username:'admin@platform.com',tenantId:null,branchId:null,membershipId:null,permissions:['PLATFORM_SUPER_ADMIN'],actorType:'PLATFORM'};
function snapshot(account:UserPermissions|null,status:'loading'|'authenticated'|'anonymous'|'error'='authenticated',error:Error|null=null){
 return {status,loading:status==='loading',refreshing:false,account,error};
}

describe('usePermissions canonical session adapter',()=>{
 beforeEach(()=>{vi.clearAllMocks();mockUseSession.mockReturnValue(snapshot(vendorAccount));});
 it('exposes the one session-derived permission snapshot without making another request',()=>{
  const fetchSpy=vi.spyOn(globalThis,'fetch');
  const {result}=renderHook(()=>usePermissions());
  expect(result.current.permissions).toEqual(vendorAccount);
  expect(result.current.loading).toBe(false);
  expect(result.current.error).toBeNull();
  expect(result.current.hasPermission('POS_VIEW')).toBe(true);
  expect(result.current.hasPermission('POS_REFUND_APPROVE')).toBe(false);
  expect(fetchSpy).not.toHaveBeenCalled();
  expect(mockUseSession).toHaveBeenCalled();
  fetchSpy.mockRestore();
 });
 it('evaluates any-of and all-of permissions only against the canonical grant set',()=>{
  const {result}=renderHook(()=>usePermissions());
  expect(result.current.hasAnyPermission('POS_VIEW','POS_REFUND_APPROVE')).toBe(true);
  expect(result.current.hasAllPermissions('POS_VIEW','POS_SALE_CREATE')).toBe(true);
  expect(result.current.hasAllPermissions('POS_VIEW','POS_REFUND_APPROVE')).toBe(false);
 });
 it('identifies Vendor, Customer and Platform only from the explicit active actor context',()=>{
  const vendor=renderHook(()=>usePermissions());
  expect(vendor.result.current.isVendorEmployee).toBe(true);
  expect(vendor.result.current.isCustomer).toBe(false);
  expect(vendor.result.current.isPlatformUser).toBe(false);
  vendor.unmount();

  mockUseSession.mockReturnValue(snapshot(customerAccount));
  const customer=renderHook(()=>usePermissions());
  expect(customer.result.current.isCustomer).toBe(true);
  expect(customer.result.current.isVendorEmployee).toBe(false);
  expect(customer.result.current.isPlatformUser).toBe(false);
  customer.unmount();

  mockUseSession.mockReturnValue(snapshot(platformAccount));
  const platform=renderHook(()=>usePermissions());
  expect(platform.result.current.isPlatformUser).toBe(true);
  expect(platform.result.current.isCustomer).toBe(false);
  expect(platform.result.current.isVendorEmployee).toBe(false);
 });
 it('has no permissions while session restoration is pending',()=>{
  mockUseSession.mockReturnValue(snapshot(null,'loading'));
  const {result}=renderHook(()=>usePermissions());
  expect(result.current.loading).toBe(true);
  expect(result.current.permissions).toBeNull();
  expect(result.current.hasPermission('POS_VIEW')).toBe(false);
  expect(result.current.hasAnyPermission('POS_VIEW','PRODUCT_VIEW')).toBe(false);
 });
 it('exposes canonical session validation errors without resolving permissions independently',()=>{
  const validationError=new Error('The active actor context could not be verified.');
  mockUseSession.mockReturnValue(snapshot(null,'error',validationError));
  const {result}=renderHook(()=>usePermissions());
  expect(result.current.loading).toBe(false);
  expect(result.current.permissions).toBeNull();
  expect(result.current.error).toBe(validationError);
  expect(result.current.hasPermission('POS_VIEW')).toBe(false);
 });
 it('removes permissions immediately when the canonical snapshot is replaced after refresh',()=>{
  const {result,rerender}=renderHook(()=>usePermissions());
  expect(result.current.hasPermission('POS_SALE_CREATE')).toBe(true);
  mockUseSession.mockReturnValue(snapshot({...vendorAccount,permissions:['POS_VIEW']}));
  rerender();
  expect(result.current.hasPermission('POS_SALE_CREATE')).toBe(false);
  expect(result.current.hasPermission('POS_VIEW')).toBe(true);
 });
 it('removes all permission access when every role has been revoked',()=>{
  mockUseSession.mockReturnValue(snapshot({...vendorAccount,permissions:[]}));
  const {result}=renderHook(()=>usePermissions());
  expect(result.current.permissions?.permissions).toEqual([]);
  expect(result.current.hasPermission('POS_VIEW')).toBe(false);
  expect(result.current.hasAnyPermission('POS_VIEW','PRODUCT_VIEW')).toBe(false);
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
  expect(hasNavigationPermission('vendor-service-visits',['SERVICE_VIEW'])).toBe(true);
  expect(hasNavigationPermission('vendor-service-visits',['SHIPMENT_VIEW'])).toBe(false);
  expect(hasNavigationPermission('customer-service-visits',['CUSTOMER'])).toBe(true);
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
 it('allows Store Settings only with a settings operation grant',()=>{
  expect(hasNavigationPermission('store-settings',['POS_VIEW'])).toBe(false);
  expect(hasNavigationPermission('store-settings',['POS_SETTINGS_MANAGE'])).toBe(true);
  expect(hasNavigationPermission('store-settings',['POS_DISCOUNT_OVERRIDE'])).toBe(true);
  expect(hasNavigationPermission('store-settings',['POS_SALE_CREATE'])).toBe(false);
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

describe('platform page permission metadata',()=>{
 it('defines a canonical policy for every platform page and denies unknown pages',()=>{
  for(const page of ['dashboard','tenants','plans','audit','support','configuration','identities','platform-subscriptions'])expect(PLATFORM_PAGE_PERMISSIONS[page],page).toBeDefined();
  expect(hasPlatformPagePermission('unclassified',['PLATFORM_ANALYTICS_VIEW'])).toBe(false);
 });
 it('requires the matching platform grant for each page',()=>{
  expect(hasPlatformPagePermission('dashboard',['PLATFORM_ANALYTICS_VIEW'])).toBe(true);
  expect(hasPlatformPagePermission('dashboard',['PLATFORM_SUPPORT_ACCESS'])).toBe(false);
  expect(hasPlatformPagePermission('support',['PLATFORM_SUPPORT_ACCESS'])).toBe(true);
  expect(hasPlatformPagePermission('identities',['PLATFORM_IDENTITY_MANAGE'])).toBe(true);
  expect(hasPlatformPagePermission('platform-subscriptions',['PLATFORM_SUBSCRIPTION_ADJUST'])).toBe(false);
  expect(hasPlatformPagePermission('plans',['PLATFORM_SUBSCRIPTION_VIEW'])).toBe(true);
 });
});
