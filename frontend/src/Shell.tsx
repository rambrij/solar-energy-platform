import SessionControls from './SessionControls';
import { useEffect, useId, useRef, useState, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react';
import {
  availableNavigation,
  navigationItems,
  navigateWorkspace,
  navigationPath,
  selectedNavigation,
  type WorkspaceMode,
} from './navigation';
import { useWorkspaceRoute } from './RouteOutlet';
import { usePermissions, hasNavigationPermission } from './usePermissions';

export function SolarMark() {
  return <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"><circle cx="31" cy="15" r="7" fill="currentColor" /><path d="M31 3v3m0 18v3M19 15h-3m30 0h-3M22 6l2 2m14 14 2 2M40 6l-2 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /><path d="M8 23h23l6 17H2z" fill="#173c64" stroke="#fff" strokeWidth="1.5" /><path d="M12 23 9 40m14-17 4 17M5 32h29" stroke="#91b5cf" strokeWidth="1.5" /><path d="M15 40v5m-5 0h15" stroke="#173c64" strokeWidth="2" /></svg>;
}

export default function Shell({ mode, children, availableIds }: { mode: WorkspaceMode; children: ReactNode; availableIds?: string[] }) {
  const [open, setOpen] = useState(false);
  const [jumpOpen, setJumpOpen] = useState(false);
  const route = useWorkspaceRoute(mode);
  const jumpToggle = useRef<HTMLButtonElement>(null);
  const jumpRoot = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const navigationRef = useRef<HTMLElement>(null);
  const mainContentRef = useRef<HTMLElement>(null);
  const previousRoute = useRef(route);
  const navigationId = useId();

  // /auth/me supplies the effective grants and active actor context. The
  // session snapshot is revalidated by usePermissions; all failures deny UI access.
  const { permissions, loading } = usePermissions();
  const allItems = navigationItems(mode);
  const featureFlaggedItems = availableNavigation(
    mode,
    new Set(availableIds ?? allItems.map(item => item.id))
  );
  const expectedActor = mode === 'pos' ? 'VENDOR' : 'CUSTOMER';
  const actorMatchesWorkspace = permissions?.actorType === expectedActor;
  const items = loading || !actorMatchesWorkspace
    ? []
    : featureFlaggedItems.filter(item =>
        hasNavigationPermission(item.id, permissions?.permissions ?? [])
      );

  // Vendor directory and storefront discovery remain public. Customer cart and
  // purchase navigation, and every POS entry, require their matching actor.
  const publicRoute = mode === 'online' && ['vendor-directory', 'storefront-products'].includes(route);
  const routeAllowed = !loading && (publicRoute || (actorMatchesWorkspace && items.some(item => item.id === route)));
  const selected = routeAllowed ? selectedNavigation(items, route) : undefined;
  const groups = Array.from(new Set(items.map(item => item.group)));
  const homePath = mode === 'pos' ? '/vendor/pos' : '/vendors';

  function closeMobileMenu(restoreToggleFocus = false) {
    setOpen(false);
    if (restoreToggleFocus) toggle.current?.focus();
  }

  useEffect(() => {
    if (previousRoute.current !== route) {
      closeMobileMenu();
      setJumpOpen(false);
      mainContentRef.current?.focus();
      previousRoute.current = route;
    }
  }, [route]);

  useEffect(() => {
    if (!open) return;

    // Put keyboard focus inside the drawer immediately. Keep Tab navigation
    // inside while it is open, and restore the menu button on Escape/close.
    const closeButton = navigationRef.current?.querySelector<HTMLButtonElement>('.workspace-drawer-close');
    (closeButton ?? navigationRef.current?.querySelector<HTMLElement>('a[href]'))?.focus();

    function onDocumentKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        closeMobileMenu(true);
        return;
      }
      if (event.key !== 'Tab' || !navigationRef.current) return;
      const focusable = Array.from(
        navigationRef.current.querySelectorAll<HTMLElement>('a[href], button:not([disabled])')
      );
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === toggle.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', onDocumentKeyDown);
    return () => document.removeEventListener('keydown', onDocumentKeyDown);
  }, [open]);

  useEffect(() => {
    if (!jumpOpen) return;
    function outside(event: globalThis.MouseEvent) {
      if (jumpRoot.current && !jumpRoot.current.contains(event.target as Node)) setJumpOpen(false);
    }
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [jumpOpen]);

  function navigate(event: ReactMouseEvent<HTMLAnchorElement>, id: string) {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigateWorkspace(mode, id);
    closeMobileMenu();
    setJumpOpen(false);
    mainContentRef.current?.focus();
  }

  return (
    <div className={`app-shell app-shell-${mode}`}>
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header">
        <a className="brand" href={homePath} aria-label="Saumya Solar home">
          <span className="brand-mark"><SolarMark /></span>
          <span className="brand-copy">Saumya Solar<small>ENERGY FOR A BRIGHTER TOMORROW</small></span>
        </a>
        <nav className="primary-navigation" aria-label="Main navigation">
          <a href="/vendors" aria-current={mode === 'online' ? 'page' : undefined}>Marketplace</a>
          {mode === 'pos' && <a href="/vendor/pos" aria-current="page">Store POS</a>}
        </nav>
        <div className="header-tools">
          <div className="quick-jump" ref={jumpRoot} onKeyDown={event => {
            if (event.key === 'Escape' && jumpOpen) { setJumpOpen(false); jumpToggle.current?.focus(); }
          }}>
            <button type="button" ref={jumpToggle} className="quick-jump-toggle" aria-haspopup="menu" aria-expanded={jumpOpen} aria-controls="quick-jump-menu" onClick={() => setJumpOpen(value => !value)}>
              Go to <span aria-hidden="true">⌄</span>
            </button>
            {jumpOpen && <div id="quick-jump-menu" role="menu" aria-label="Go to page">
              {groups.map(group => <div role="group" aria-label={group} key={group}>
                <span className="quick-jump-group" aria-hidden="true">{group}</span>
                {items.filter(item => item.group === group).map(item => (
                  <button type="button" role="menuitem" key={item.id} aria-current={selected?.id === item.id ? 'page' : undefined} onClick={() => { navigateWorkspace(mode, item.id); setJumpOpen(false); mainContentRef.current?.focus(); }}>{item.label}</button>
                ))}
              </div>)}
            </div>}
          </div>
          <SessionControls />
        </div>
      </header>

      <div className="workspace-bar">
        <span className="workspace-label"><span className="status-dot" />{mode === 'pos' ? 'Retail workspace' : 'Solar marketplace'}</span>
        <span className="workspace-context">{mode === 'pos' ? 'Store operations' : 'Discover independent solar vendors'}</span>
        <span className="workspace-bar-note">Saumya Solar Energy</span>
      </div>

      <div className="workspace-layout">
        <aside className={`workspace-menu ${open ? 'menu-open' : ''}`} aria-label="Workspace menu">
          {open && <div className="workspace-menu-backdrop" aria-hidden="true" onClick={() => closeMobileMenu(true)} />}
          <button type="button" ref={toggle} className="workspace-menu-toggle" aria-label={open ? 'Close workspace menu' : 'Open workspace menu'} aria-expanded={open} aria-controls={navigationId} onClick={() => setOpen(value => !value)}>
            <span aria-hidden="true">{open ? '×' : '☰'}</span> {open ? 'Close workspace menu' : 'Open workspace menu'}
          </button>
          <nav ref={navigationRef} id={navigationId} className="workspace-navigation" aria-label="Workspace sections">
            <div className="workspace-drawer-header">
              <span>{mode === 'pos' ? 'Store operations' : 'Marketplace'} navigation</span>
              <button type="button" className="workspace-drawer-close" aria-label="Close workspace navigation" onClick={() => closeMobileMenu(true)}>×</button>
            </div>
            <div className="workspace-menu-heading">
              <span className="workspace-menu-kicker">WORKSPACE</span>
              <strong>{mode === 'pos' ? 'Store operations' : 'Marketplace'}</strong>
              <span>{mode === 'pos' ? 'Sales, stock and service' : 'Browse and manage purchases'}</span>
            </div>
            {groups.map(group => <div className={`workspace-menu-group ${selected?.group === group ? 'is-active' : ''}`} key={group}>
              <h2>{group}{selected?.group === group && <span className="workspace-current-section">Current section</span>}</h2>
              <ul>{items.filter(item => item.group === group).map(item => <li key={item.id}>
                <a href={navigationPath(mode, item.id)} aria-current={selected?.id === item.id ? 'page' : undefined} onClick={event => navigate(event, item.id)}>
                  <span>{item.label}</span>
                  {selected?.id === item.id && <><span className="navigation-active-mark" aria-hidden="true" /><span className="navigation-current-text">Current page</span></>}
                </a>
              </li>)}</ul>
            </div>)}
            <div className="workspace-menu-foot"><span className="status-dot" /> Your workspace is connected</div>
          </nav>
        </aside>

        <div className="workspace-content">
          <div className="workspace-topline">
            <nav className="workspace-breadcrumb" aria-label="Breadcrumb">
              <ol>
                <li><a href={homePath}>{mode === 'pos' ? 'Store POS' : 'Marketplace'}</a></li>
                {selected && <>
                  <li><a href={navigationPath(mode, items.find(item => item.group === selected.group)?.id ?? selected.id)}>{selected.group}</a></li>
                  <li aria-current="page"><span>{selected.label}</span></li>
                </>}
              </ol>
            </nav>
            <span className="workspace-mode-label">{mode === 'pos' ? 'RETAIL OPERATIONS' : 'PUBLIC MARKETPLACE'}</span>
          </div>
          {selected && <div className="workspace-page-heading">
            <div><h1>{selected.label}</h1><p>{selected.description}</p></div>
            <span className="workspace-page-group">{selected.group}</span>
          </div>}
          <main ref={mainContentRef} tabIndex={-1} id="main-content" className={`workspace ${mode === 'pos' ? 'pos-workspace' : 'marketplace-workspace'} workspace-page-${route}`}>
            {loading
              ? <p role="status">Checking your workspace access…</p>
              : routeAllowed
                ? children
                : <section className="access-denied" role="alert">
                    <h2>Access denied</h2>
                    <p>Your account does not have the effective permission required for this page.</p>
                    <p>Choose an authorized page from the workspace menu or contact your administrator.</p>
                  </section>}
          </main>
        </div>
      </div>
      <footer className="site-footer"><span>Saumya Solar Energy</span><span>Smarter energy. Thoughtful service.</span></footer>
    </div>
  );
}
