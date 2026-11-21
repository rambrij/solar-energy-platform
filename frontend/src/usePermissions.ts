import {useState,useEffect} from 'react';
import {authApi} from './api';
import {useSession} from './session';

/** Effective permissions for exactly one server-resolved actor context. */
export interface UserPermissions {
 username:string;
 tenantId:number|null;
 branchId:number|null;
 permissions:string[];
 actorType:'PLATFORM'|'VENDOR'|'CUSTOMER';
}

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
  setPermissions(null);setError(null);setLoading(Boolean(account));
  if(!account){setLoading(false);return ()=>{mounted=false;};}
  async function fetchPermissions(){
   try{
    const response=await authApi('/api/v1/auth/me');
    if(mounted){
     if(response.actorType!==account.actorType||response.username!==account.username||
       response.tenantId!==account.tenantId||response.permissions.some((p:string)=>account.permissions.indexOf(p)<0)||
       account.permissions.some(p=>response.permissions.indexOf(p)<0))
      throw new Error('The server actor context changed. Restore the session before continuing.');
     setPermissions(response);setLoading(false);
    }
   }catch(err){
    if(mounted){setError(err instanceof Error?err:new Error('Failed to load permissions'));setLoading(false);}
   }
  }
  void fetchPermissions();
  return ()=>{mounted=false;};
 },[account?.username,account?.actorType,account?.tenantId,account?.permissions.join('|')]);

 const hasPermission=(permission:string)=>permissions?.permissions.includes(permission)??false;
 const hasAnyPermission=(...perms:string[])=>!!permissions&&perms.some(p=>permissions.permissions.includes(p));
 const hasAllPermissions=(...perms:string[])=>!!permissions&&perms.every(p=>permissions.permissions.includes(p));
 const isPlatformUser=permissions?.actorType==='PLATFORM';
 const isCustomer=permissions?.actorType==='CUSTOMER';
 const isVendorEmployee=permissions?.actorType==='VENDOR';

 return {permissions,loading,error,hasPermission,hasAnyPermission,hasAllPermissions,isPlatformUser,isCustomer,isVendorEmployee};
}

export interface PermissionRequirement {anyOf?:string[];allOf?:string[];}
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
