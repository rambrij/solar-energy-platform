import {useSession,type ActorType} from './session';

export interface UserPermissions {
 username:string;
 tenantId:number|null;
 branchId:number|null;
 membershipId:string|null;
 permissions:string[];
 actorType:ActorType;
}

/**
 * Read-only permission facade over the canonical /auth/me-derived session snapshot.
 * It deliberately performs no network access and owns no independent permission cache.
 */
export function usePermissions():{
 permissions:UserPermissions|null;loading:boolean;error:Error|null;
 hasPermission:(permission:string)=>boolean;
 hasAnyPermission:(...permissions:string[])=>boolean;
 hasAllPermissions:(...permissions:string[])=>boolean;
 isPlatformUser:boolean;isCustomer:boolean;isVendorEmployee:boolean;
}{
 const session=useSession();
 const permissions:UserPermissions|null=session.account?{
  username:session.account.username,tenantId:session.account.tenantId,branchId:session.account.branchId,
  membershipId:session.account.membershipId,permissions:session.account.permissions,actorType:session.account.actorType
 }:null;
 const hasPermission=(permission:string)=>permissions?.permissions.includes(permission)??false;
 const hasAnyPermission=(...perms:string[])=>!!permissions&&perms.some(p=>permissions.permissions.includes(p));
 const hasAllPermission=(...perms:string[])=>!!permissions&&perms.every(p=>permissions.permissions.includes(p));
 return {
  permissions,loading:session.loading,error:session.error,
  hasPermission,hasAnyPermission,hasAllPermissions:hasAllPermission,
  isPlatformUser:session.account?.actorType==='PLATFORM',
  isCustomer:session.account?.actorType==='CUSTOMER',
  isVendorEmployee:session.account?.actorType==='VENDOR'
 };
}
export interface PermissionRequirement {anyOf?:string[];allOf?:string[];}

export const PLATFORM_PAGE_PERMISSIONS:Record<string,PermissionRequirement>={
 'dashboard':{allOf:['PLATFORM_ANALYTICS_VIEW']},
 'tenants':{anyOf:['PLATFORM_TENANT_VIEW','PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_TENANT_REACTIVATE']},
 'plans':{anyOf:['PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW']},
 'audit':{allOf:['PLATFORM_AUDIT_VIEW']},
 'support':{allOf:['PLATFORM_SUPPORT_ACCESS']},
 'configuration':{allOf:['PLATFORM_CONFIG_MANAGE']},
 'identities':{allOf:['PLATFORM_IDENTITY_MANAGE']},
 'platform-subscriptions':{allOf:['PLATFORM_SUBSCRIPTION_VIEW']},
};
export function hasPlatformPagePermission(pageId:string,userPermissions:string[]):boolean{
 const requirement=PLATFORM_PAGE_PERMISSIONS[pageId];
 if(!requirement)return false;
 const any=requirement.anyOf?requirement.anyOf.some(permission=>userPermissions.includes(permission)):true;
 const all=requirement.allOf?requirement.allOf.every(permission=>userPermissions.includes(permission)):true;
 return any&&all&&Boolean(requirement.anyOf||requirement.allOf);
}

export const NAVIGATION_PERMISSIONS:Record<string,PermissionRequirement>={
 'checkout':{allOf:['POS_VIEW']},
 'current-sale':{allOf:['POS_VIEW']},
 'reserved-pos-checkout':{allOf:['POS_VIEW']},
 'order-history':{allOf:['POS_VIEW']},
 'sale-receipt':{anyOf:['POS_RECEIPT_VIEW']},
 'pos-returns':{allOf:['POS_VIEW','POS_RETURN_VIEW']},
 'cash-refunds':{allOf:['POS_VIEW','POS_REFUND_VIEW']},
 'category-management':{anyOf:['PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE']},
 'product-management':{anyOf:['PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE']},
 'stock-receiving':{anyOf:['INVENTORY_VIEW','INVENTORY_RECEIVE']},
 'register':{allOf:['POS_VIEW']},
 'shift-history':{allOf:['POS_VIEW']},
 'shift-reviews':{anyOf:['POS_SHIFT_VARIANCE_APPROVE']},
 'terminal-tender-review':{anyOf:['POS_TENDER_MANUAL_VERIFY']},
 'store-settings':{anyOf:['POS_DISCOUNT_OVERRIDE','POS_SETTINGS_MANAGE']},
 'promotion-history':{allOf:['POS_VIEW','CATALOG_PROMOTION_MANAGE','POS_DISCOUNT_APPROVE']},
 'sales-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW']},
 'refund-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW','POS_REFUND_VIEW']},
 'cashier-sales-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW','POS_CASHIER_REPORT_VIEW']},
 'role-management':{anyOf:['ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE','ROLE_ASSIGN']},
 'employee-management':{anyOf:['USER_VIEW','USER_CREATE','USER_UPDATE','USER_DISABLE']},
 'vendor-customer-crm':{allOf:['CUSTOMER_RELATION_VIEW']},
 'vendor-directory':{anyOf:['CUSTOMER']},
 'storefront-products':{anyOf:['CUSTOMER']},
 'customer-cart':{anyOf:['CUSTOMER']},
 'online-checkout':{anyOf:['CUSTOMER']},
 'saved-vendor-carts':{anyOf:['CUSTOMER']},
 'customer-dashboard':{allOf:['CUSTOMER']},
 'customer-purchases':{anyOf:['CUSTOMER']},
 'customer-profile':{allOf:['CUSTOMER']},
};

export function hasNavigationPermission(itemId:string,userPermissions:string[]):boolean{
 const requirement=NAVIGATION_PERMISSIONS[itemId];
 if(!requirement)return false;
 const hasAny=requirement.anyOf?requirement.anyOf.some(permission=>userPermissions.includes(permission)):true;
 const hasAll=requirement.allOf?requirement.allOf.every(permission=>userPermissions.includes(permission)):true;
 return hasAny&&hasAll&&Boolean(requirement.anyOf||requirement.allOf);
}
