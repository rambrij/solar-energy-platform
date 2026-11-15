import SessionControls from './SessionControls';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { availableNavigation, navigationItems, selectedNavigation, type WorkspaceMode } from './navigation';

export function SolarMark() {
  return <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"><circle cx="31" cy="15" r="7" fill="currentColor" /><path d="M31 3v3m0 18v3M19 15h-3m30 0h-3M22 6l2 2m14 14 2 2M40 6l-2 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /><path d="M8 23h23l6 17H2z" fill="#173c64" stroke="#fff" strokeWidth="1.5" /><path d="M12 23 9 40m14-17 4 17M5 32h29" stroke="#91b5cf" strokeWidth="1.5" /><path d="M15 40v5m-5 0h15" stroke="#173c64" strokeWidth="2" /></svg>;
}

export default function Shell({ mode, children, availableIds }: { mode: WorkspaceMode; children: ReactNode; availableIds?: string[] }) {
  const [open, setOpen] = useState(false);
  const [jumpOpen, setJumpOpen] = useState(false);
  const [hash, setHash] = useState(window.location.hash);
  const jumpToggle = useRef<HTMLButtonElement>(null);
  const jumpRoot = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const items = availableNavigation(mode, new Set(availableIds ?? navigationItems(mode).map(item => item.id)));
  const selected = selectedNavigation(items, hash);
  const groups = Array.from(new Set(items.map(item => item.group)));

  useEffect(() => {
    function sync() { setHash(window.location.hash); setOpen(false); setJumpOpen(false); }
    sync();
    window.addEventListener('hashchange', sync);
    window.addEventListener('popstate', sync);
    return () => {
      window.removeEventListener('hashchange', sync);
      window.removeEventListener('popstate', sync);
    };
  }, [mode]);

  useEffect(() => {
    if (!jumpOpen) return;
    function outside(event: MouseEvent) {
      if (jumpRoot.current && !jumpRoot.current.contains(event.target as Node)) setJumpOpen(false);
    }
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [jumpOpen]);

  function jump(id: string) {
    setJumpOpen(false);
    if (window.location.hash !== `#${id}`) window.location.hash = id;
    setHash(`#${id}`);
    setOpen(false);
  }

  function navigate(event: React.MouseEvent<HTMLAnchorElement>, id: string) {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (window.location.hash !== `#${id}`) window.location.hash = id;
    setHash(`#${id}`);
    setOpen(false);
  }

  return (
    <div className={`app-shell app-shell-${mode}`}>
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header">
        <a className="brand" href={mode === 'pos' ? '/vendor/pos' : '/vendors'} aria-label="Saumya Solar home">
          <span className="brand-mark"><SolarMark /></span>
          <span className="brand-copy">Saumya Solar<small>ENERGY FOR A BRIGHTER TOMORROW</small></span>
        </a>
        <nav className="primary-navigation" aria-label="Main navigation">
          <a href="/vendors" aria-current={mode === 'online' ? 'page' : undefined}>Marketplace</a>
          <a href="/vendor/pos" aria-current={mode === 'pos' ? 'page' : undefined}>Store POS</a>
        </nav>
        <div className="header-tools">
          <div className="quick-jump" ref={jumpRoot} onKeyDown={event => {
            if (event.key === 'Escape' && jumpOpen) { setJumpOpen(false); jumpToggle.current?.focus(); }
          }}>
            <button type="button" ref={jumpToggle} className="quick-jump-toggle" aria-haspopup="menu" aria-expanded={jumpOpen} aria-controls="quick-jump-menu" onClick={() => setJumpOpen(value => !value)}>
              Go to <span aria-hidden="true">⌄</span>
            </button>
            {jumpOpen && <div id="quick-jump-menu" role="menu" aria-label="Go to section">
              {groups.map(group => <div role="group" aria-label={group} key={group}>
                <span className="quick-jump-group" aria-hidden="true">{group}</span>
                {items.filter(item => item.group === group).map(item => <button type="button" role="menuitem" key={item.id} aria-current={selected?.id === item.id ? 'location' : undefined} onClick={() => jump(item.id)}>{item.label}</button>)}
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
        <aside className={`workspace-menu ${open ? 'menu-open' : ''}`} aria-label="Workspace menu" onKeyDown={event => {
          if (event.key === 'Escape' && open) { setOpen(false); toggle.current?.focus(); }
        }}>
          <div className="workspace-menu-heading">
            <span className="workspace-menu-kicker">WORKSPACE</span>
            <strong>{mode === 'pos' ? 'Store operations' : 'Marketplace'}</strong>
            <span>{mode === 'pos' ? 'Sales, stock and service' : 'Browse and manage purchases'}</span>
          </div>
          <button type="button" ref={toggle} className="workspace-menu-toggle" aria-expanded={open} aria-controls="workspace-navigation" onClick={() => setOpen(value => !value)}>
            <span aria-hidden="true">☰</span> {open ? 'Close workspace menu' : 'Open workspace menu'}
          </button>
          <nav id="workspace-navigation" className="workspace-navigation" aria-label="Workspace sections">
            {groups.map(group => <div className="workspace-menu-group" key={group}>
              <h2>{group}</h2>
              <ul>{items.filter(item => item.group === group).map(item => <li key={item.id}>
                <a href={`#${item.id}`} aria-current={selected?.id === item.id ? 'location' : undefined} onClick={event => navigate(event, item.id)}>
                  <span>{item.label}</span>{selected?.id === item.id && <span className="navigation-active-mark" aria-hidden="true" />}
                </a>
              </li>)}</ul>
            </div>)}
          </nav>
          <div className="workspace-menu-foot"><span className="status-dot" /> Your workspace is connected</div>
        </aside>

        <div className="workspace-content">
          <div className="workspace-topline">
            <nav className="workspace-breadcrumb" aria-label="Breadcrumb">
              <ol><li>{mode === 'pos' ? 'Store POS' : 'Marketplace'}</li>
                {selected && <><li>{selected.group}</li><li aria-current="location">{selected.label}</li></>}
              </ol>
            </nav>
            <span className="workspace-mode-label">{mode === 'pos' ? 'RETAIL OPERATIONS' : 'PUBLIC MARKETPLACE'}</span>
          </div>
          <nav className="workspace-tabs" aria-label="Workspace tabs">
            <ul>{groups.map(group => {
              const first = items.find(item => item.group === group)!;
              const active = selected?.group === group;
              return <li key={group}><a href={`#${first.id}`} aria-current={active ? 'true' : undefined} onClick={event => navigate(event, first.id)}>{group}</a></li>;
            })}</ul>
          </nav>
          <main tabIndex={-1} id="main-content" className={mode === 'pos' ? 'workspace pos-workspace' : 'workspace marketplace-workspace'}>
            {children}
          </main>
        </div>
      </div>
      <footer className="site-footer"><span>Saumya Solar Energy</span><span>Smarter energy. Thoughtful service.</span></footer>
    </div>
  );
}
