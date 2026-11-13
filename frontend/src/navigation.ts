export type WorkspaceMode='pos'|'online';
export type NavigationItem={id:string;label:string;group:string};
const pos:NavigationItem[]=[
 {id:'main-content',label:'Overview',group:'Workspace'},
 {id:'checkout',label:'Product catalogue',group:'Sell'},
 {id:'current-sale',label:'Current bill',group:'Sell'},
 {id:'reserved-pos-checkout',label:'Reserved sales',group:'Sell'},
 {id:'order-history',label:'Sales history',group:'Sell'},
 {id:'register',label:'Drawer & shift closing',group:'Register'},
 {id:'shift-history',label:'My closed shifts',group:'Register'},
 {id:'shift-reviews',label:'Supervisor shift review',group:'Register'},
 {id:'pos-returns',label:'Returns & inspection',group:'After-sale service'},
 {id:'cash-refunds',label:'Cash refunds',group:'After-sale service'},
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
export const navigationItems=(mode:WorkspaceMode)=>mode==='pos'?pos:online;
export function availableNavigation(mode:WorkspaceMode,ids:ReadonlySet<string>){return navigationItems(mode).filter(item=>ids.has(item.id));}
export function selectedNavigation(items:NavigationItem[],hash:string){const id=hash.replace(/^#/,'');return items.find(item=>item.id===id)??items.find(item=>item.id==='main-content');}
