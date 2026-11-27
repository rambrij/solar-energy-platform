export type WorkspaceMode = 'pos' | 'online';
export type NavigationItem = {
  id: string;
  label: string;
  group: string;
  description: string;
  path: string;
};

const pos: NavigationItem[] = [
  { id: 'checkout', label: 'Cashier Session', group: 'POS', description: 'Open your register and create walk-in sales using the existing outlet catalogue.', path: '/vendor/pos' },
  { id: 'current-sale', label: 'Current Bill', group: 'POS', description: 'Review the active sale, buyer details, discounts and cash tender.', path: '/vendor/pos/current-sale' },
  { id: 'reserved-pos-checkout', label: 'Held Bills', group: 'POS', description: 'Continue or review existing unpaid walk-in reservations.', path: '/vendor/pos/held-bills' },
  { id: 'order-history', label: 'Sales History', group: 'POS', description: 'Find completed sales and open their existing receipt or return actions.', path: '/vendor/pos/sales-history' },
  { id: 'sale-receipt', label: 'Current Receipt', group: 'POS', description: 'Review, reprint or download the currently selected receipt.', path: '/vendor/pos/receipt' },
  { id: 'pos-returns', label: 'After-sale Service', group: 'After-Sale', description: 'Review returns and inspection details for completed sales.', path: '/vendor/pos/after-sales' },
  { id: 'cash-refunds', label: 'Cash Refunds', group: 'After-Sale', description: 'Review and record cash refunds using the existing refund workflow.', path: '/vendor/pos/refunds' },
  { id: 'category-management', label: 'Categories', group: 'Catalogue & Inventory', description: 'Manage the categories already available to this outlet.', path: '/vendor/pos/categories' },
  { id: 'product-management', label: 'Manage Products', group: 'Catalogue & Inventory', description: 'Maintain the products available in the outlet catalogue.', path: '/vendor/pos/products' },
  { id: 'solar-package-management', label: 'Solar Packages & Images', group: 'Catalogue & Inventory', description: 'Build tenant-owned solar system packages and manage product image assets.', path: '/vendor/pos/solar-packages' },
  { id: 'stock-receiving', label: 'Receive Stock', group: 'Catalogue & Inventory', description: 'Record stock receipts using the existing inventory workflow.', path: '/vendor/pos/stock' },
  { id: 'register', label: 'Opening Float & Drawer', group: 'Store Management', description: 'Review the current drawer, cash movements and shift closing controls.', path: '/vendor/pos/register' },
  { id: 'shift-history', label: 'Drawer Records', group: 'Store Management', description: 'Review your recorded closed-shift drawer snapshots.', path: '/vendor/pos/drawer-records' },
  { id: 'shift-reviews', label: 'Supervisor Shift Review', group: 'Store Management', description: 'Review existing shifts awaiting supervisor action.', path: '/vendor/pos/shift-review' },
  { id: 'terminal-tender-review', label: 'Terminal Verification', group: 'Store Management', description: 'Resolve existing terminal tender verification items.', path: '/vendor/pos/terminal-verification' },
  { id: 'store-settings', label: 'Outlet & Payment Settings', group: 'Store Management', description: 'Review the current outlet and payment configuration.', path: '/vendor/pos/settings' },
  { id: 'promotion-history', label: 'Discount Audit', group: 'Reports', description: 'Review the existing promotion and discount audit trail.', path: '/vendor/pos/discount-audit' },
  { id: 'sales-insights', label: 'Sales Insights', group: 'Reports', description: 'Review available sales reporting for this workspace.', path: '/vendor/pos/sales-insights' },
  { id: 'refund-insights', label: 'Refund Insights', group: 'Reports', description: 'Review available refund reporting for this workspace.', path: '/vendor/pos/refund-insights' },
  { id: 'cashier-sales-insights', label: 'Cashier Sales', group: 'Reports', description: 'Review existing cashier sales reporting.', path: '/vendor/pos/cashier-sales' },
  { id: 'role-management', label: 'Roles & Permissions', group: 'Administration', description: 'Manage tenant roles and their permission grants.', path: '/vendor/pos/roles' },
  { id: 'employee-management', label: 'Employees', group: 'Administration', description: 'Invite employees and manage roles, branch and register assignments.', path: '/vendor/pos/employees' },
  { id: 'vendor-profile', label: 'Vendor Profile', group: 'Administration', description: 'Manage your tenant-owned business profile and storefront contact details.', path: '/vendor/profile' },
  { id: 'vendor-customer-crm', label: 'Customers & CRM', group: 'Customer Management', description: 'Review verified customer relationships and vendor-private CRM notes.', path: '/vendor/customers' },
  { id: 'vendor-site-surveys', label: 'Site Surveys', group: 'Customer Management', description: 'Schedule and record site surveys linked to existing vendor leads.', path: '/vendor/pos/site-surveys' },
  { id: 'vendor-quotations', label: 'Quotations', group: 'Customer Management', description: 'Create price-snapshotted quotations and manage revisions for assigned leads.', path: '/vendor/pos/quotations' },
  { id: 'shipment-management', label: 'Shipments', group: 'Fulfilment', description: 'Register tracking for paid online orders and append shipment events.', path: '/vendor/shipments' },
  { id: 'installation-management', label: 'Installations', group: 'Fulfilment', description: 'Assign engineers and track solar installation jobs for paid customer orders.', path: '/vendor/installations' },
];

const online: NavigationItem[] = [
  { id: 'vendor-directory', label: 'Find Energy Partners', group: 'Shop', description: 'Discover independent solar vendors and open a vendor storefront.', path: '/marketplace/vendors' },
  { id: 'storefront-products', label: 'Vendor Catalogue', group: 'Shop', description: 'Browse products from the selected vendor and review product details.', path: '/marketplace/catalogue' },
  { id: 'customer-cart', label: 'My Single-Vendor Cart', group: 'Cart', description: 'Review your active vendor cart, quantities and server-calculated estimate.', path: '/marketplace/cart' },
  { id: 'online-checkout', label: 'Reserve Vendor Cart', group: 'Cart', description: 'Review existing reservation and checkout steps for your vendor cart.', path: '/marketplace/reserved-cart' },
  { id: 'saved-vendor-carts', label: 'Saved Vendor Carts', group: 'Cart', description: 'Load and restore your archived vendor carts.', path: '/marketplace/saved-carts' },
  { id: 'customer-dashboard', label: 'My Dashboard', group: 'Account', description: 'See a private overview of your global account, vendor orders and current cart.', path: '/marketplace/dashboard' },
  { id: 'customer-purchases', label: 'Your Purchases', group: 'Purchases', description: 'Review your existing marketplace purchase history.', path: '/marketplace/purchases' },
  { id: 'customer-quotations', label: 'My Quotations', group: 'Purchases', description: 'Review vendor quotations, compare revisions and accept or reject a valid offer.', path: '/marketplace/quotations' },
  { id: 'customer-installations', label: 'My Installations', group: 'Purchases', description: 'See your solar installation schedule and vendor-shared progress history.', path: '/marketplace/installations' },
  { id: 'customer-profile', label: 'My Profile', group: 'Account', description: 'Manage your global customer display name and account details.', path: '/marketplace/profile' },
];

export const navigationItems = (mode: WorkspaceMode): NavigationItem[] => mode === 'pos' ? pos : online;

export function availableNavigation(mode: WorkspaceMode, ids: ReadonlySet<string>) {
  return navigationItems(mode).filter(item => ids.has(item.id));
}

export function selectedNavigation(items: NavigationItem[], routeId: string): NavigationItem | undefined {
  const id = routeId.replace(/^#/, '');
  return items.find(item => item.id === id);
}

export function navigationPath(mode: WorkspaceMode, id: string, currentPath = window.location.pathname) {
  const item = navigationItems(mode).find(candidate => candidate.id === id);
  if (!item) return mode === 'pos' ? '/vendor/pos' : '/vendors';
  if (mode === 'online' && id === 'storefront-products') {
    return currentPath.startsWith('/vendors/') ? currentPath.split('?')[0] : '/vendors';
  }
  return item.path;
}

function normalizedPathname(pathname: string) {
  const pathOnly = pathname.split(/[?#]/, 1)[0] || '/';
  if (pathOnly === '/') return '/';
  let normalized = pathOnly;
  while (normalized.length > 1 && normalized.endsWith('/')) normalized = normalized.slice(0, -1);
  return normalized;
}

function pathMatchesRoute(pathname: string, routePath: string) {
  const path = normalizedPathname(pathname);
  const route = normalizedPathname(routePath);
  return path === route || path.startsWith(route + '/');
}

export function resolveWorkspaceRoute(mode: WorkspaceMode, pathname = window.location.pathname, hash = window.location.hash) {
  const items = navigationItems(mode);
  const legacyId = hash.replace(/^#/, '');
  if (legacyId && items.some(item => item.id === legacyId)) return legacyId;

  const path = normalizedPathname(pathname);
  if (mode === 'online') {
    // The purchase-association consent flow belongs to the signed-in customer
    // purchase area; it is not public vendor discovery.
    if (pathMatchesRoute(path, '/customer/associate')) return 'customer-purchases';
    // Storefront slugs are public route selectors, not authorization evidence.
    if (pathMatchesRoute(path, '/vendors') && path !== '/vendors') return 'storefront-products';
    if (pathMatchesRoute(path, '/marketplace/catalogue')) return 'vendor-directory';
  }

  // Prefer the most specific registered route so details below a page retain
  // the same parent navigation item. Segment boundaries prevent false prefixes.
  const match = items
    .filter(item => {
      // The workspace root is a landing page, not a wildcard for every child URL.
      const routePath = normalizedPathname(item.path);
      return routePath === path || (routePath !== '/vendor/pos' && pathMatchesRoute(path, routePath));
    })
    .sort((a, b) => normalizedPathname(b.path).length - normalizedPathname(a.path).length)[0];
  if (match) return match.id;

  if (path === '/' || (mode === 'online' && path === '/marketplace')) {
    return mode === 'pos' ? 'checkout' : 'vendor-directory';
  }
  return 'not-found';
}

export function navigateWorkspace(mode: WorkspaceMode, id: string, currentPath = window.location.pathname) {
  const destination = navigationPath(mode, id, currentPath);
  if (window.location.pathname + window.location.search !== destination) {
    window.history.pushState({}, '', destination);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }
}
