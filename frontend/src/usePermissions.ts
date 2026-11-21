import {useState,useEffect} from 'react';
import {authApi} from './api';
import {useSession,type ActorType} from './session';

export interface UserPermissions {
 username:string;
 tenantId:number|null;
 branchId:number|null;
 permissions:string[];
 actorType:ActorType;
}

/**
 * Resolve the current actor from the authenticated /me endpoint.
 * The session snapshot is a consistency check, not an authorization source;
 * backend JWT and persisted-membership checks remain authoritative.
 */
export function usePermissions():{
 permissions:UserPermissions|null;loading:boolean;error:Error|null;
 hasPermission:(permission:string)=>boolean;
 hasAnyPermission:(...permissions:string[])=>boolean;
 hasAllPermissions:(...permissions:string[])=>boolean;
 isPlatformUser:boolean;isCustomer:boolean;isVendorEmployee:boolean;
}{
 const {account}=useSession();
 const [permissions,setPermissions]=useState<UserPermissions|null>(null);
 const [loading,setLoading]=useState(true);
 const [error,setError]=useState<Error|null>(null);

 useEffect(()=>{
  let mounted=true;
  setPermissions(null);setError(null);setLoading(true);
  async function fetchPermissions(){
   try{
    const response=await authApi('/api/v1/auth/me');
    // Real backend responses always include actorType. This fallback is only for
    // older UI test fixtures; backend guards never infer or accept client actor data.
    const actorType:ActorType=response.actorType ??
      (response.permissions?.some((p:string)=>p.startsWith('PLATFORM_'))?'PLATFORM':
       response.permissions?.includes('CUSTOMER')?'CUSTOMER':
       response.tenantId!=null?'VENDOR':'CUSTOMER');
    const resolved:UserPermissions={...response,actorType};
    if(account&&(
      !response.actorType||
      response.username!==account.username||
      response.actorType!==account.actorType||
      response.tenantId!==account.tenantId||
      response.branchId!==account.branchId||
      response.permissions.some((p:string)=>!account.permissions.includes(p))||
      account.permissions.some((p:string)=>!response.permissions.includes(p))
    ))throw new Error('The server actor context changed. Restore the session before continuing.');
    if(mounted){setPermissions(resolved);setLoading(false);}
   }catch(err){
    if(mounted){setError(err instanceof Error?err:new Error('Failed to load permissions'));setLoading(false);}
   }
  }
  void fetchPermissions();
  return ()=>{mounted=false;};
 },[account?.username,account?.actorType,account?.tenantId,account?.branchId,account?.permissions.join('|')]);

 const hasPermission=(permission:string)=>permissions?.permissions.includes(permission)??false;
 const hasAnyPermission=(...perms:string[])=>!!permissions&&perms.some(p=>permissions.permissions.includes(p));
 const hasAllPermissions=(...perms:string[])=>!!permissions&&perms.every(p=>permissions.permissions.includes(p));
 const isPlatformUser=permissions?.actorType==='PLATFORM';
 const isCustomer=permissions?.actorType==='CUSTOMER';
 const isVendorEmployee=permissions?.actorType==='VENDOR';
 return {permissions,loading,error,hasPermission,hasAnyPermission,hasAllPermissions,isPlatformUser,isCustomer,isVendorEmployee};
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
 'store-settings':{allOf:['POS_VIEW']},
 'promotion-history':{allOf:['POS_VIEW','CATALOG_PROMOTION_MANAGE','POS_DISCOUNT_APPROVE']},
 'sales-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW']},
 'refund-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW','POS_REFUND_VIEW']},
 'cashier-sales-insights':{allOf:['POS_VIEW','POS_REPORT_VIEW','POS_CASHIER_REPORT_VIEW']},
 'role-management':{anyOf:['ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE','ROLE_ASSIGN']},
 'employee-management':{anyOf:['USER_VIEW','USER_CREATE','USER_UPDATE','USER_DISABLE']},
 'vendor-directory':{anyOf:['CUSTOMER']},
 'storefront-products':{anyOf:['CUSTOMER']},
 'customer-cart':{anyOf:['CUSTOMER']},
 'online-checkout':{anyOf:['CUSTOMER']},
 'saved-vendor-carts':{anyOf:['CUSTOMER']},
 'customer-purchases':{anyOf:['CUSTOMER']},
};

export function hasNavigationPermission(itemId:string,userPermissions:string[]):boolean{
 const requirement=NAVIGATION_PERMISSIONS[itemId];
 if(!requirement)return false;
 const hasAny=requirement.anyOf?requirement.anyOf.some(permission=>userPermissions.includes(permission)):true;
 const hasAll=requirement.allOf?requirement.allOf.every(permission=>userPermissions.includes(permission)):true;
 return hasAny&&hasAll&&Boolean(requirement.anyOf||requirement.allOf);
}
