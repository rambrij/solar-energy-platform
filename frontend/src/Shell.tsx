import type { ReactNode } from 'react';
export function SolarMark() {
  return <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"><circle cx="31" cy="15" r="7" fill="currentColor"/><path d="M31 3v3m0 18v3M19 15h-3m30 0h-3M22 6l2 2m14 14 2 2M40 6l-2 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><path d="M8 23h23l6 17H2z" fill="#173c64" stroke="#fff" strokeWidth="1.5"/><path d="M12 23 9 40m14-17 4 17M5 32h29" stroke="#91b5cf" strokeWidth="1.5"/><path d="M15 40v5m-5 0h15" stroke="#173c64" strokeWidth="2"/></svg>;
}
export default function Shell({ mode, children }: { mode: 'pos' | 'online'; children: ReactNode }) {
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="site-header"><a className="brand" href={mode === 'pos' ? '/' : '/vendors'}><span className="brand-mark"><SolarMark/></span><span>Saumya Solar<small>ENERGY FOR A BRIGHTER TOMORROW</small></span></a>
      <nav aria-label="Main navigation"><a href="/vendors" aria-current={mode === 'online' ? 'page' : undefined}>Marketplace</a><a href="/" aria-current={mode === 'pos' ? 'page' : undefined}>Store POS</a><a href="#order-history">{mode === 'pos' ? 'Sales history' : 'My orders'}</a>{mode === 'pos' && <><a href="#sales-insights">Sales insights</a><a href="#pos-returns">Returns</a><a href="#refund-insights">Refund insights</a><a href="#cashier-sales-insights">Cashier sales</a></>}</nav>
    </header>
    <div className="workspace-bar"><span className="workspace-label"><span className="status-dot"/>{mode === 'pos' ? 'Retail workspace' : 'Solar marketplace'}</span><span>Shared stock · Vendor-owned sales</span></div>
    <main tabIndex={-1} id="main-content" className={mode === 'pos' ? 'workspace pos-workspace' : 'workspace marketplace-workspace'}>{children}</main>
    <footer className="site-footer"><span>Saumya Solar Energy</span><span>Smarter energy. Thoughtful service.</span></footer>
  </div>;
}
