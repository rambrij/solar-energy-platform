# Responsive workspace navigation

Implements the requested grouped menus, active menu state, breadcrumbs and mobile hamburger disclosure on the existing marketplace/POS screens. It follows the SRS React UI/navigation scope and retains all existing domain/module boundaries.

- Desktop: sticky grouped section sidebar alongside the workspace. Main navigation switches Marketplace / Store POS; the active workspace uses aria-current=page.
- POS groups: workspace, selling, register operations, after-sale service and reports. Marketplace groups: workspace, shopping, cart/checkout and purchases.
- Only destinations currently mounted are linked. Current bill/catalogue/register/reservation links appear after session recovery; storefront products appear only on a storefront. These links are not permission grants: existing APIs still enforce all access checks.
- Selected fragment uses aria-current=location and a solar-yellow highlight. Breadcrumbs show workspace/group/section. Active section follows explicit fragment navigation and browser history; ordinary scrolling does not rewrite the URL or select a different section.
- Section anchors move focus and scroll without unmounting forms, resetting carts, sending API commands or touching saved attempts. Modified clicks keep native browser behavior. Browser hash/back/forward events update the selected item. Initial fragments wait for asynchronously mounted targets.
- Under 1100 CSS pixels, a hamburger button expands/collapses an inline navigation disclosure with aria-expanded/aria-controls. Selecting a section closes it and focuses the target; Escape closes it and returns focus to the toggle. It is not a modal, so no focus trap or backdrop is introduced. Desktop navigation remains visible.
- Supervisor review navigation opens the existing details disclosure; data still requires its existing explicit load/permissions. Reduced-motion settings disable smooth scrolling. Navigation and breadcrumbs are excluded from print layouts.

No backend/schema/API/permission changes are necessary. A small pure TypeScript navigation model owns allowed destinations and availability/selection; Shell owns UI/hash lifecycle. Existing operational components gain stable anchor IDs only. The modular monolith and current Spring Data JPA services remain unchanged.

## Review and tests

Rebuild frontend, open either workspace, and check grouped links/breadcrumbs. Open a POS register to see selling/register links appear. Use a section link, browser Back/Forward and a direct fragment URL. At mobile/tablet widths, open the hamburger, use keyboard navigation and Escape, and select a section. Enter an unfinished bill or retain a saved retry and verify section navigation leaves it intact. API permissions still apply when a panel is explicitly loaded.

Unit tests cover unique workspace-specific destinations, mounted-target filtering and unknown-fragment fallback. Shell component tests cover skip/main landmarks, active workspace, mobile disclosure/Escape focus return, breadcrumbs/target focus, preserved form and saved attempt, browser fragment history, conditional mount/removal, modified clicks and delayed initial fragments. Full backend/frontend regression suites and the TypeScript/Vite production build are run. Component tests use jsdom; real-device/mobile viewport and assistive-technology acceptance are not claimed.
