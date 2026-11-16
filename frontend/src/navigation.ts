export type WorkspaceMode='pos'|'online'|'platform';
export type NavigationItem={id:string;label:string;group:string};
const pos:NavigationItem[]=[
 {id:'main-content',label:'Overview',group:'Workspace'},
 {id:'checkout',label:'Product catalogue',group:'Sell'},
 {id:'current-sale',label:'Current bill',group:'Sell'},
 {id:'reserved-pos-checkout',label:'Held bills',group:'Sell'},
 {id:'order-history',label:'Sales history',group:'Sell'},
 {id:'sale-receipt',label:'Current receipt',group:'Sell'},
 {id:'register',label:'Drawer & shift closing',group:'Register'},
 {id:'category-management',label:'Categories',group:'Manage'},
 {id:'product-management',label:'Products',group:'Manage'},
 {id:'stock-receiving',label:'Receive stock',group:'Manage'},
 {id:'store-settings',label:'Outlet settings',group:'Manage'},
 {id:'shift-history',label:'My closed shifts',group:'Register'},
 {id:'shift-reviews',label:'Supervisor shift review',group:'Register'},
 {id:'terminal-tender-review',label:'Terminal verification',group:'Register'},
 {id:'pos-returns',label:'Returns & inspection',group:'After-sale service'},
 {id:'cash-refunds',label:'Cash refunds',group:'After-sale service'},
 {id:'promotion-history',label:'Discount audit',group:'Reports'},
 {id:'sales-insights',label:'Sales insights',group:'Reports'},
 {id:'refund-insights',label:'Refund insights',group:'Reports'},
 {id:'cashier-sales-insights',label:'Cashier sales',group:'Reports'}];
const online:NavigationItem[]=[
 {id:'main-content',label:'Overview',group:'Workspace'},
 {id:'vendor-directory',label:'Find vendors',group:'Shop'},
 {id:'storefront-products',label:'Product catalogue',group:'Shop'},
 {id:'customer-cart',label:'My cart',group:'Cart & checkout'},
 {id:'online-checkout',label:'Checkout reservations',group:'Cart & checkout'},
 {id:'saved-vendor-carts',label:'Saved vendor carts',group:'Cart & checkout'},
 {id:'order-history',label:'My orders',group:'Purchases'}];
const platform:NavigationItem[]=[
 {id:'main-content',label:'Overview',group:'Workspace'},
 {id:'platform-dashboard',label:'Dashboard',group:'Operations'},
 {id:'platform-health',label:'System health',group:'Operations'},
 {id:'platform-onboarding',label:'Onboard a vendor',group:'Tenants'},
 {id:'platform-tenants',label:'Tenant directory',group:'Tenants'},
 {id:'platform-plans',label:'Plans & versions',group:'Subscriptions'},
 {id:'platform-subscriptions',label:'Tenant subscriptions',group:'Subscriptions'},
 {id:'platform-config',label:'Configuration',group:'Governance'},
 {id:'platform-support',label:'Support access',group:'Governance'},
 {id:'platform-audit',label:'Platform audit',group:'Governance'}];
export const navigationItems=(mode:WorkspaceMode)=>mode==='pos'?pos:mode==='platform'?platform:online;
export function availableNavigation(mode:WorkspaceMode,ids:ReadonlySet<string>){return navigationItems(mode).filter(item=>ids.has(item.id));}
export function selectedNavigation(items:NavigationItem[],hash:string){const id=hash.replace(/^#/,'');return items.find(item=>item.id===id)??items.find(item=>item.id==='main-content');}
