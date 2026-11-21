import { expect, type Page } from '@playwright/test';

export const demo = {
  vendorAdmin: { username: 'saumya.admin@somya.local', password: 'Somya-Saumya-ADM-2026!' },
  vendorBAdmin: { username: 'helio.admin@somya.local', password: 'Somya-Helio-ADM-2026!' },
  salesExecutive: { username: 'saumya.sales.executive@somya.local', password: 'Somya-Saumya-SE-2026!' },
  cashier: { username: 'saumya.pos.cashier@somya.local', password: 'Somya-Saumya-POS-2026!' },
  accountant: { username: 'saumya.accountant@somya.local', password: 'Somya-Saumya-ACC-2026!' },
  platformAdmin: { username: 'platform.superadmin@somya.local', password: 'Somya-Platform-2026!' },
  customer: { username: 'customer.asha@somya.local', password: 'Somya-Asha-Buyer-2026!' },
};

export async function signIn(page: Page, username: string, password: string): Promise<string> {
  const form = page.getByRole('form', { name: 'Sign in' });
  if (!(await form.isVisible().catch(() => false))) await page.getByRole('button', { name: 'Sign in / Register' }).click();
  await expect(form).toBeVisible();
  await page.getByLabel('Email or username').fill(username);
  await page.getByLabel('Password').fill(password);
  const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  const response = await loginResponse;
  expect(response.ok(), 'real authentication endpoint should accept the seeded actor').toBeTruthy();
  const session = await response.json() as { accessToken: string };
  expect(session.accessToken).toBeTruthy();
  return session.accessToken;
}

export async function apiAs(page: Page, accessToken: string, path: string, method = 'GET', body?: unknown): Promise<{ status: number; body: any }> {
  return page.evaluate(async input => {
    const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
    const csrf = csrfResponse.ok ? (await csrfResponse.json()).token as string : '';
    const headers: Record<string, string> = { Authorization: 'Bearer ' + input.accessToken, 'Content-Type': 'application/json' };
    if (input.method !== 'GET' && csrf) headers['X-XSRF-TOKEN'] = csrf;
    const response = await fetch(input.path, { method: input.method, credentials: 'same-origin', headers, ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }) });
    let value: any = null;
    try { value = await response.json(); } catch { /* empty response */ }
    return { status: response.status, body: value };
  }, { path, method, body, accessToken });
}

export async function ensureCustomSalesRole(page: Page, accessToken: string): Promise<number> {
  const roleKey = 'e2e-sales-browser';
  const listed = await apiAs(page, accessToken, '/api/v1/vendor/roles');
  expect(listed.status).toBe(200);
  let role = (listed.body as Array<any>).find(item => item.key === roleKey);
  if (!role) {
    const created = await apiAs(page, accessToken, '/api/v1/vendor/roles', 'POST', { roleKey, displayName: 'Browser E2E Sales', permissions: ['PRODUCT_VIEW'], reason: 'P2-01 deterministic browser E2E role fixture' });
    expect(created.status).toBe(201);
    role = created.body;
  } else if (role.permissions.length !== 1 || role.permissions[0] !== 'PRODUCT_VIEW') {
    const updated = await apiAs(page, accessToken, '/api/v1/vendor/roles/' + role.id, 'PUT', { expectedVersion: role.version, displayName: 'Browser E2E Sales', permissions: ['PRODUCT_VIEW'], reason: 'Restore deterministic P2-01 browser E2E role fixture' });
    expect(updated.status).toBe(200);
    role = updated.body;
  }
  expect(role.id).toBeTruthy();
  const employees = await apiAs(page, accessToken, '/api/v1/vendor/employees');
  expect(employees.status).toBe(200);
  const salesEmployee = (employees.body as Array<any>).find(employee => employee.username === demo.salesExecutive.username);
  expect(salesEmployee, 'local profile seeds the deferred Sales Executive identity').toBeTruthy();
  const assigned = await apiAs(page, accessToken, '/api/v1/vendor/employees/' + encodeURIComponent(demo.salesExecutive.username) + '/roles', 'PUT', { roleIds: [role.id], reason: 'Assign deterministic P2-01 custom Sales role fixture' });
  expect(assigned.status).toBe(200);
  return Number(role.id);
}
