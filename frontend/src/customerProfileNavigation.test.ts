import {expect,it} from 'vitest';
import {navigationItems,resolveWorkspaceRoute} from './navigation';
import {hasNavigationPermission} from './usePermissions';
it('exposes profile only to the customer actor and resolves its route',()=>{
 expect(navigationItems('online').some(item=>item.id==='customer-profile')).toBe(true);
 expect(resolveWorkspaceRoute('online','/marketplace/profile','')).toBe('customer-profile');
 expect(hasNavigationPermission('customer-profile',['CUSTOMER'])).toBe(true);
 expect(hasNavigationPermission('customer-profile',['POS_VIEW'])).toBe(false);
});
