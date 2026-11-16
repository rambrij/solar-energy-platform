/** Client mirrors of the server's platform rules. Used to guide the operator; the server is authoritative. */
export type TenantStatus = 'PENDING' | 'UNDER_REVIEW' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
export const tenantTransitions: Record<TenantStatus, TenantStatus[]> = {
  PENDING: ['UNDER_REVIEW', 'CLOSED'], UNDER_REVIEW: ['ACTIVE', 'PENDING', 'CLOSED'], ACTIVE: ['SUSPENDED', 'CLOSED'], SUSPENDED: ['ACTIVE', 'CLOSED'], CLOSED: []
};
/** Suspend and close have dedicated permissions; every other lifecycle step uses PLATFORM_TENANT_UPDATE. */
export const permissionForTarget = (target: TenantStatus) => target === 'SUSPENDED' ? 'PLATFORM_TENANT_SUSPEND' : target === 'CLOSED' ? 'PLATFORM_TENANT_CLOSE' : 'PLATFORM_TENANT_UPDATE';
export const transitionLabel: Record<TenantStatus, string> = { PENDING: 'Return to pending', UNDER_REVIEW: 'Start review', ACTIVE: 'Activate / reactivate', SUSPENDED: 'Suspend', CLOSED: 'Close permanently' };

export type SubscriptionState = 'PENDING' | 'ACTIVE' | 'PAST_DUE' | 'GRACE_PERIOD' | 'SUSPENDED' | 'CANCEL_AT_PERIOD_END' | 'CANCELLED' | 'EXPIRED';
export const subscriptionTransitions: Record<SubscriptionState, SubscriptionState[]> = {
  PENDING: ['ACTIVE', 'PAST_DUE'], ACTIVE: ['PAST_DUE', 'CANCELLED', 'CANCEL_AT_PERIOD_END', 'EXPIRED'], PAST_DUE: ['GRACE_PERIOD', 'ACTIVE'], GRACE_PERIOD: ['ACTIVE', 'SUSPENDED'],
  SUSPENDED: ['ACTIVE', 'CANCELLED'], CANCEL_AT_PERIOD_END: ['CANCELLED'], CANCELLED: [], EXPIRED: []
};

const slugPattern = /^[a-z0-9](?:[a-z0-9-]{1,78}[a-z0-9])$/;
const reserved = new Set(['admin', 'api', 'platform', 'marketplace', 'vendors', 'login', 'static', 'assets', 'www']);
export function slugProblem(slug: string): string | null {
  const value = slug.trim();
  if (!value) return 'Slug is required.';
  if (!slugPattern.test(value) || reserved.has(value)) return 'Use 3-80 lower-case letters, digits or hyphens; reserved words are not allowed.';
  return null;
}
export const usernameProblem = (value: string) => /^[a-z0-9][a-z0-9._-]{2,98}$/.test(value.trim()) ? null : 'Use 3-99 lower-case letters, digits, dots, hyphens or underscores.';
export const secretRefPattern = /^(env|vault):[A-Za-z0-9_./-]{1,120}$/;
export function configProblem(secret: boolean, value: string): string | null {
  if (!value.trim()) return 'A value is required.';
  if (secret && !secretRefPattern.test(value.trim())) return 'Secrets are never stored. Enter a reference such as env:NAME or vault:path.';
  return null;
}
export function supportProblem(reason: string, ticket: string, minutes: number): string | null {
  if (reason.trim().length < 15) return 'Describe the purpose in at least 15 characters.';
  if (!/^[A-Za-z0-9][A-Za-z0-9._/-]{2,59}$/.test(ticket.trim())) return 'Enter a valid ticket or reference.';
  if (!Number.isInteger(minutes) || minutes < 5 || minutes > 240) return 'Access must last 5 to 240 minutes.';
  return null;
}
export const money = (value: number | string, currency: string) => `${currency} ${Number(value).toFixed(2)}`;
