import { ReactNode } from 'react';
import { usePermissions } from './usePermissions';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredPermissions: string[];
  requireAll?: boolean; // If true, must have ALL permissions; if false, need ANY
  fallback?: ReactNode;
}

export function ProtectedRoute({ 
  children, 
  requiredPermissions,
  requireAll = true,
  fallback = (
    <div className="access-denied" style={{ padding: '2rem', textAlign: 'center', maxWidth: '600px', margin: '4rem auto' }}>
      <h2 style={{ fontSize: '1.5rem', marginBottom: '1rem', color: '#d32f2f' }}>Access Denied</h2>
      <p style={{ marginBottom: '0.5rem', color: '#666' }}>You don't have permission to access this page.</p>
      <p style={{ marginBottom: '1.5rem', color: '#666' }}>Contact your administrator if you believe this is an error.</p>
      <p><a href="/" style={{ color: '#0b2545', textDecoration: 'underline' }}>Return to home</a></p>
    </div>
  )
}: ProtectedRouteProps) {
  const { permissions, loading, hasAnyPermission, hasAllPermissions } = usePermissions();

  if (loading) {
    return (
      <div className="loading" aria-live="polite" style={{ padding: '2rem', textAlign: 'center' }}>
        Loading...
      </div>
    );
  }

  if (!permissions) {
    return <>{fallback}</>;
  }

  const hasAccess = requireAll
    ? hasAllPermissions(...requiredPermissions)
    : hasAnyPermission(...requiredPermissions);

  if (requiredPermissions.length === 0 || !hasAccess) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
