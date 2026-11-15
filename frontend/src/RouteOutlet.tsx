import { useEffect, useState, type ReactNode } from 'react';

type Props = { id: string; mode: 'pos' | 'online'; children: ReactNode; aliases?: string[] };

function currentRoute(mode: Props['mode'], fallback: string) {
  const hash = window.location.hash.replace(/^#/, '');
  if (hash) return hash;
  if (mode === 'online' && window.location.pathname.startsWith('/vendors/')) return 'storefront-products';
  if (mode === 'online') return 'vendor-directory';
  return fallback;
}

export default function RouteOutlet({ id, mode, children, aliases = [] }: Props) {
  const fallback = mode === 'online' ? 'vendor-directory' : 'checkout';
  const [route, setRoute] = useState(() => currentRoute(mode, fallback));
  useEffect(() => {
    const sync = () => setRoute(currentRoute(mode, fallback));
    window.addEventListener('hashchange', sync);
    window.addEventListener('popstate', sync);
    sync();
    return () => {
      window.removeEventListener('hashchange', sync);
      window.removeEventListener('popstate', sync);
    };
  }, [mode, fallback]);
  return route === id || aliases.includes(route) ? <>{children}</> : null;
}
