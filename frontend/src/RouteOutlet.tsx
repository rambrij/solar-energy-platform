import { useEffect, useState, type ReactNode } from 'react';
import { resolveWorkspaceRoute, type WorkspaceMode } from './navigation';

export function useWorkspaceRoute(mode: WorkspaceMode) {
  const [route, setRoute] = useState(() => resolveWorkspaceRoute(mode));
  useEffect(() => {
    const sync = () => setRoute(resolveWorkspaceRoute(mode));
    window.addEventListener('popstate', sync);
    window.addEventListener('hashchange', sync);
    sync();
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hashchange', sync);
    };
  }, [mode]);
  return route;
}

type Props = { id: string; mode: WorkspaceMode; children: ReactNode; aliases?: string[] };

export default function RouteOutlet({ id, mode, children, aliases = [] }: Props) {
  const route = useWorkspaceRoute(mode);
  return route === id || aliases.includes(route) ? <>{children}</> : null;
}
