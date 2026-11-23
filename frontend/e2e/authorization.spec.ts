import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { apiAs, demo, ensureCustomSalesRole, signIn } from './helpers';

test('Vendor Admin: route refresh, browser history, mobile navigation and cross-tenant resistance', async ({ page, browser }) => {
  const token = await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await page.goto('/vendor/pos/products');
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  const own = await apiAs(page, token, '/api/v1/vendor/products?page=0&size=100');
  const forged = await apiAs(page, token, '/api/v1/vendor/products?page=0&size=100&tenantId=920001');
  expect(own.status).toBe(200);
  expect(forged.status).toBe(200);
  expect(forged.body.items.map((item: { id: number }) => item.id)).toEqual(own.body.items.map((item: { id: number }) => item.id));
  const vendorBContext = await browser.newContext();
  try {
    const vendorBPage = await vendorBContext.newPage();
    const vendorBToken = await signIn(vendorBPage, demo.vendorBAdmin.username, demo.vendorBAdmin.password);
    const vendorBProducts = await apiAs(vendorBPage, vendorBToken, '/api/v1/vendor/products?page=0&size=100');
    expect(vendorBProducts.status).toBe(200);
    const foreignProduct = vendorBProducts.body.items[0];
    expect(foreignProduct, 'the second local vendor has a seeded catalogue').toBeTruthy();
    const foreignUpdate = await apiAs(page, token, '/api/v1/vendor/products/' + foreignProduct.id, 'PUT', {
      sku: foreignProduct.sku, barcode: foreignProduct.barcode, name: foreignProduct.name,
      price: foreignProduct.price, taxRate: foreignProduct.taxRate, published: foreignProduct.published,
      categoryId: foreignProduct.categoryId, expectedVersion: foreignProduct.version,
      reason: 'P2-01 Vendor A must not update Vendor B product',
    });
    expect(foreignUpdate.status).toBe(404);
    const unchanged = await apiAs(vendorBPage, vendorBToken, '/api/v1/vendor/products?page=0&size=100');
    expect(unchanged.status).toBe(200);
    expect(unchanged.body.items.find((item: { id: number }) => item.id === foreignProduct.id)).toMatchObject({
      id: foreignProduct.id, sku: foreignProduct.sku, name: foreignProduct.name, version: foreignProduct.version,
    });
  } finally {
    await vendorBContext.close();
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Open workspace menu' }).click();
  const navigation = page.getByRole('navigation', { name: 'Workspace sections' });
  await expect(navigation.getByRole('link', { name: 'Manage Products' })).toBeVisible();
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  await page.getByRole('link', { name: 'Sales History' }).click();
  await expect(page).toHaveURL(/sales-history/);
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
});

test('Custom Sales role: product read is allowed while product creation returns 403', async ({ page }) => {
  const adminToken = await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await ensureCustomSalesRole(page, adminToken);
  await page.getByRole('button', { name: 'Sign out' }).click();
  const salesToken = await signIn(page, demo.salesExecutive.username, demo.salesExecutive.password);
  await page.goto('/vendor/pos/products');
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  const before = await apiAs(page, salesToken, '/api/v1/vendor/products?page=0&size=100');
  expect(before.status).toBe(200);
  const denied = await apiAs(page, salesToken, '/api/v1/vendor/products', 'POST', { sku: 'P2-01-DENIED-SALES-CREATE', name: 'Must Not Be Created', price: 1, taxRate: 0, published: false, expectedVersion: 0, reason: 'Verify custom Sales role cannot create products' });
  expect(denied.status).toBe(403);
  const after = await apiAs(page, salesToken, '/api/v1/vendor/products?page=0&size=100');
  expect(after.status).toBe(200);
  expect(after.body.items.map((item: { id: number }) => item.id)).toEqual(before.body.items.map((item: { id: number }) => item.id));
});

test('Cashier: POS session entry is allowed, administration routes stay hidden on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, demo.cashier.username, demo.cashier.password);
  await page.goto('/vendor/pos');
  await expect(page.getByRole('heading', { name: 'Open cashier session' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Roles & Permissions' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Manage Products' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Open workspace menu' }).click();
  await expect(page.getByRole('navigation', { name: 'Workspace sections' }).getByRole('link', { name: 'Cashier Session' })).toBeVisible();
  await page.goto('/vendor/pos/products');
  await expect(page.getByRole('alert')).toContainText('Access denied');
});

test('Accountant: reporting is available but inventory receipt access is denied', async ({ page }) => {
  const token = await signIn(page, demo.accountant.username, demo.accountant.password);
  await page.goto('/vendor/pos/sales-insights');
  await expect(page.getByRole('heading', { name: 'Sales Insights', level: 1 })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Receive Stock' })).toHaveCount(0);
  const denied = await apiAs(page, token, '/api/v1/vendor/inventory/receipts');
  expect(denied.status).toBe(403);
});

test('Platform Super Admin: platform workspace works and vendor POS context is rejected', async ({ page, request }) => {
  await signIn(page, demo.platformAdmin.username, demo.platformAdmin.password);
  await page.goto('/platform');
  await expect(page.getByRole('link', { name: 'Platform overview' })).toBeVisible();
  const metrics = page.getByRole('region', { name: 'Subscriptions by status' });
  await expect(metrics.getByText('ACTIVE', { exact: true })).toBeVisible();
  await expect(metrics.getByText('3', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Tenant subscriptions' })).toBeVisible();
  await page.getByRole('button', { name: /^Tenants/ }).click();
  await expect(page.getByRole('heading', { name: 'Tenant directory' })).toBeVisible();
  await expect(page.getByText('Saumya Solar Rooftops', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /^Configuration/ }).click();
  await expect(page.getByRole('heading', { name: 'Global platform configuration' })).toBeVisible();
  await page.getByLabel('Category').selectOption('EMAIL_TEMPLATE');
  await page.getByLabel('Template key').fill('E2E_NOTICE');
  await page.getByLabel('Email subject').fill('Platform notice');
  await page.getByLabel('Email body').fill('Platform maintenance notice.');
  await page.getByRole('button', { name: 'Save configuration' }).click();
  await expect(page.getByText('EMAIL_TEMPLATE.E2E_NOTICE', { exact: true })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'EMAIL_TEMPLATE.E2E_NOTICE' }).getByRole('button', { name: 'Edit template' }).click();
  await expect(page.getByLabel('Email subject')).toHaveValue('Platform notice');
  await page.getByLabel('Category').selectOption('INTEGRATION');
  await page.getByRole('button', { name: 'Configure MFA mail adapter' }).click();
  await page.getByLabel('MFA mail adapter').selectOption('DISABLED');
  await page.getByRole('button', { name: 'Save configuration' }).click();
  const adapter = page.getByRole('row').filter({ hasText: 'INTEGRATION.PLATFORM_MFA_MAIL.ADAPTER' });
  await expect(adapter.getByText('DISABLED', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Configure MFA mail adapter' }).click();
  await page.getByLabel('MFA mail adapter').selectOption('SMTP');
  await page.getByRole('button', { name: 'Save configuration' }).click();
  await expect(adapter.getByText('SMTP', { exact: true })).toBeVisible();
  expect((await request.get('http://127.0.0.1:8025/api/v1/messages')).ok()).toBeTruthy();
  await expect(page.getByRole('button', { name: /^Platform identities/ })).toBeVisible();
  await page.goto('/vendor/pos');
  await expect(page.getByRole('alert')).toContainText('Access denied');
});

test('Customer: purchase history opens only in the customer marketplace context', async ({ page }) => {
  await signIn(page, demo.customer.username, demo.customer.password);
  await page.goto('/marketplace/purchases');
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Your Purchases' })).toBeVisible();
});

test('Expired refresh session: protected POS route returns to the sign-in boundary', async ({ page }) => {
  await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await page.goto('/vendor/pos/products');
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }));
  await page.reload();
  const signInButton = page.getByRole('button', { name: 'Sign in / Register' });
  await expect(signInButton).toBeVisible();
  await signInButton.click();
  await expect(page.getByRole('form', { name: 'Sign in' })).toBeVisible();
});
test('Cashier: resume assigned register, complete a cash sale and receive its receipt', async ({ page }) => {
  const token = await signIn(page, demo.cashier.username, demo.cashier.password);
  await page.goto('/vendor/pos');
  await expect(page.getByRole('heading', { name: 'Open cashier session' })).toBeVisible();

  const activeBefore = await apiAs(page, token, '/api/v1/vendor/pos/sessions/active');
  expect(activeBefore.status).toBe(200);
  expect(Array.isArray(activeBefore.body)).toBe(true);

  const directory = await apiAs(page, token, '/api/v1/vendor/pos/registers?page=0&size=12');
  expect(directory.status, JSON.stringify(directory.body)).toBe(200);
  const existingSession = activeBefore.body.find((session: { state: string }) => session.state === 'OPEN');
  const assignedRegister = directory.body.items.find((item: { id: number; available: boolean }) =>
    existingSession ? item.id === existingSession.registerId : item.available);
  expect(assignedRegister, JSON.stringify({ directory: directory.body, active: activeBefore.body })).toBeTruthy();

  let sessionId: string;
  if (existingSession) {
    sessionId = existingSession.id;
  } else {
    const opened = await apiAs(page, token, '/api/v1/vendor/pos/sessions', 'POST', {
      registerId: assignedRegister.id,
      openingFloat: 0,
    });
    expect(opened.status, JSON.stringify(opened.body)).toBe(200);
    expect(opened.body.id).toBeTruthy();
    sessionId = opened.body.id;
    const activeAfter = await apiAs(page, token, '/api/v1/vendor/pos/sessions/active');
    expect(activeAfter.status).toBe(200);
    expect(activeAfter.body).toContainEqual(expect.objectContaining({
      id: sessionId,
      registerId: assignedRegister.id,
      state: 'OPEN',
    }));
  }

  await page.reload();
  const resume = page.getByRole('button', { name: /^Resume register/ });
  await expect.poll(async () => (await resume.count()) > 0, { timeout: 15_000 }).toBe(true);
  await resume.first().click();

  await expect(page.locator('.product-grid')).toBeVisible();
  await expect(page.locator('.product-grid button:not([disabled])').first()).toBeVisible();
  await page.locator('.product-grid button:not([disabled])').first().click();
  await page.getByRole('link', { name: 'Current Bill' }).click();
  await expect(page.getByRole('heading', { name: 'Current sale', level: 2 })).toBeVisible();
  await page.getByLabel('Cash received').fill('1000000');
  await page.getByRole('button', { name: 'Complete cash sale' }).click();
  await expect(page.getByText('Vendor sale receipt')).toBeVisible({ timeout: 30_000 });
});
test('Revoked custom Sales role loses existing bearer access and protected route after refresh', async ({ page, browser }) => {
  const adminContext = await browser.newContext();
  try {
    const adminPage = await adminContext.newPage();
    const adminToken = await signIn(adminPage, demo.vendorAdmin.username, demo.vendorAdmin.password);
    await ensureCustomSalesRole(adminPage, adminToken);
    const salesToken = await signIn(page, demo.salesExecutive.username, demo.salesExecutive.password);
    await page.goto('/vendor/pos/products');
    await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
    const revoked = await apiAs(adminPage, adminToken, '/api/v1/vendor/employees/' + encodeURIComponent(demo.salesExecutive.username) + '/roles', 'PUT', { roleIds: [], reason: 'Verify P2-01 permission revocation' });
    expect(revoked.status).toBe(200);
    const staleTokenRequest = await apiAs(page, salesToken, '/api/v1/vendor/products?page=0&size=20');
    expect(staleTokenRequest.status).toBe(403);
    await page.reload();
    await expect(page.getByRole('alert')).toContainText('Access denied');
  } finally {
    await adminContext.close();
  }
});

test('Customer: direct vendor POS deep link is denied outside the customer workspace', async ({ page }) => {
  await signIn(page, demo.customer.username, demo.customer.password);
  await page.goto('/vendor/pos');
  await expect(page.getByRole('alert')).toContainText('Access denied');
  await expect(page.getByRole('heading', { name: 'Open cashier session' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Manage Products' })).toHaveCount(0);
});

test('Vendor Admin: direct Platform workspace URL is rejected by the platform actor boundary', async ({ page }) => {
  await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await page.goto('/platform');
  await expect(page.getByRole('alert')).toContainText('Platform access denied');
});


test('P3-01: mobile navigation supports keyboard dismissal, focus restoration and drawer navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await page.goto('/vendor/pos/products/SKU-204/details?tab=stock');
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Breadcrumb' }).getByText('Catalogue & Inventory')).toBeVisible();

  const toggle = page.locator('.workspace-menu-toggle');
  await expect(toggle).toHaveAccessibleName('Open workspace menu');
  await toggle.focus();
  await page.keyboard.press('Enter');
  await expect(toggle).toHaveAccessibleName('Close workspace menu');
  const close = page.getByRole('button', { name: 'Close workspace navigation' });
  await expect(close).toBeFocused();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  const navigation = page.locator('#' + await toggle.getAttribute('aria-controls'));
  await expect(navigation).toBeVisible();
  const active = navigation.getByRole('link', { name: 'Manage Products' });
  await expect(active).toHaveAttribute('aria-current', 'page');
  await expect(active.locator('.navigation-current-text')).toHaveText('Current page');
  await page.keyboard.press('Shift+Tab');
  await expect(navigation.getByRole('link', { name: 'Employees' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(close).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(toggle).toBeFocused();
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');

  await toggle.click();
  await expect(close).toBeFocused();
  await page.getByRole('link', { name: 'Sales History' }).click();
  await expect(page).toHaveURL(/sales-history/);
  await expect(page.getByRole('button', { name: 'Open workspace menu' })).toHaveAttribute('aria-expanded', 'false');
  await expect(page.getByRole('heading', { name: 'Sales History', level: 1 })).toBeVisible();
});

test('P3-01: workspace navigation meets automated WCAG 2.2 AA checks', async ({ page }) => {
  await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/vendor/pos/products');
  await page.getByRole('button', { name: 'Open workspace menu' }).click();
  const mobileResults = await new AxeBuilder({ page })
    .include('.workspace-menu')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  expect(mobileResults.violations.map(v => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.map(n => ({ target: n.target, failureSummary: n.failureSummary })),
  }))).toEqual([]);

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.reload();
  const desktopResults = await new AxeBuilder({ page })
    .include('.workspace-menu')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  expect(desktopResults.violations.map(v => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.map(n => ({ target: n.target, failureSummary: n.failureSummary })),
  }))).toEqual([]);
});

test('P3-01: marketplace customer navigation remains responsive and has no horizontal overflow', async ({ page }) => {
  await signIn(page, demo.customer.username, demo.customer.password);
  for (const viewport of [
    { width: 360, height: 800 }, { width: 390, height: 844 },
    { width: 760, height: 900 }, { width: 761, height: 900 },
    { width: 768, height: 1024 }, { width: 1024, height: 768 }, { width: 1280, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/marketplace/purchases');
    await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible();
    const toggle = page.getByRole('button', { name: 'Open workspace menu' });
    const navigation = page.locator('.workspace-navigation');
    if (viewport.width <= 760) {
      await expect(toggle).toBeVisible();
      await expect(toggle).toHaveAttribute('aria-controls', await navigation.getAttribute('id'));
      await expect(navigation).toBeHidden();
      await toggle.click();
      await expect(page.getByRole('navigation', { name: 'Workspace sections' })).toBeVisible();
      await expect(navigation.getByRole('link', { name: 'Your Purchases' })).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    } else {
      await expect(toggle).toBeHidden();
      await expect(navigation.getByRole('link', { name: 'Your Purchases' })).toBeVisible();
    }
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow, `marketplace horizontal overflow at ${viewport.width}x${viewport.height}`).toBe(false);
  }
});

test('P3-01: responsive workspace layouts do not create horizontal page overflow', async ({ page }) => {
  await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  for (const viewport of [{ width: 360, height: 800 }, { width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1024, height: 768 }, { width: 1280, height: 900 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/vendor/pos/products');
    await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow, `horizontal overflow at ${viewport.width}x${viewport.height}`).toBe(false);
  }
});

test('P3-01: permission revocation removes navigation and denies a direct deep link after refresh', async ({ page }) => {
  const adminToken = await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await ensureCustomSalesRole(page, adminToken);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await signIn(page, demo.salesExecutive.username, demo.salesExecutive.password);
  await page.goto('/vendor/pos/products');
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  const salesLink = page.getByRole('link', { name: 'Manage Products' });
  await expect(salesLink).toHaveAttribute('aria-current', 'page');

  const adminContext = await page.context().browser()!.newContext();
  try {
    const adminPage = await adminContext.newPage();
    const freshAdminToken = await signIn(adminPage, demo.vendorAdmin.username, demo.vendorAdmin.password);
    const revoked = await apiAs(adminPage, freshAdminToken, '/api/v1/vendor/employees/' + encodeURIComponent(demo.salesExecutive.username) + '/roles', 'PUT', { roleIds: [], reason: 'P3-01 navigation revocation regression' });
    expect(revoked.status).toBe(200);
  } finally {
    await adminContext.close();
  }
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Access denied');
  await expect(page.getByRole('link', { name: 'Manage Products' })).toHaveCount(0);
});


test('P3-02: dual Customer/Vendor browser context switching replaces the complete server snapshot', async ({ page }) => {
  // Register a fresh Customer identity for every attempt. Playwright retries
  // must not reuse an identity already provisioned as a Vendor Admin by the
  // previous attempt; the customer/vendor coexistence assertion is then tested
  // against this same newly provisioned identity.
  const suffix = Date.now().toString() + '-' + Math.random().toString(36).slice(2, 8);
  const customerUsername = 'p3-02.customer.' + suffix + '@example.test';
  const customerPassword = 'P3-02-browser-context-' + suffix + '!A';
  // page.evaluate uses same-origin fetches below; establish the configured
  // application origin first because a fresh Playwright page starts at about:blank.
  await page.goto('/');
  const registration = await page.evaluate(async credentials => {
    const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
    const csrf = csrfResponse.ok ? (await csrfResponse.json()).token as string : '';
    const response = await fetch('/api/v1/auth/register', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) },
      body: JSON.stringify({ username: credentials.username, password: credentials.password }),
    });
    let body: any = null;
    try { body = await response.json(); } catch { /* empty response */ }
    return { status: response.status, body };
  }, { username: customerUsername, password: customerPassword });
  expect(registration.status, JSON.stringify(registration.body)).toBe(200);

  const platformToken = await signIn(page, demo.platformAdmin.username, demo.platformAdmin.password);
  const created = await apiAs(page, platformToken, '/api/v1/platform/tenants', 'POST', {
    slug: 'dual-context-' + suffix,
    name: 'Dual Context Browser Tenant',
    legalName: 'Dual Context Browser Tenant Ltd',
    contactEmail: 'dual-context@example.test',
    contactPhone: '',
    taxIdentifier: '',
    address: '',
    initialVendorAdminUsername: customerUsername,
  });
  expect(created.status, JSON.stringify(created.body)).toBe(201);
  const tenantId = Number(created.body.tenant.id);
  expect(tenantId).toBeGreaterThan(0);
  expect(created.body.vendorAdminUsername).toBe(customerUsername);
  expect(created.body.accountAlreadyActive).toBe(true);

  const review = await apiAs(page, platformToken, `/api/v1/platform/tenants/${tenantId}/lifecycle`, 'POST', {
    status: 'UNDER_REVIEW',
    reason: 'P3-02 browser actor-context fixture onboarding review',
  });
  expect(review.status, JSON.stringify(review.body)).toBe(200);
  const activate = await apiAs(page, platformToken, `/api/v1/platform/tenants/${tenantId}/lifecycle`, 'POST', {
    status: 'ACTIVE',
    reason: 'P3-02 browser actor-context fixture activated',
  });
  expect(activate.status, JSON.stringify(activate.body)).toBe(200);

  await page.getByRole('button', { name: 'Sign out' }).click();
  const signInButton = page.getByRole('button', { name: 'Sign in / Register' });
  await expect(signInButton).toBeVisible();
  await signInButton.click();
  const form = page.getByRole('form', { name: 'Sign in' });
  await expect(form).toBeVisible();
  await page.getByLabel('Email or username').fill(customerUsername);
  await page.getByLabel('Password').fill(customerPassword);
  const ambiguousLogin = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  expect((await ambiguousLogin).status()).toBe(409);
  await expect(page.getByRole('group', { name: 'Actor context' })).toBeVisible();
  // The login dropdown is viewport-bounded and scrollable; bring the
  // actor-context section into view, then use its visible styled label.
  const actorContext = page.getByRole('group', { name: 'Actor context' });
  await actorContext.scrollIntoViewIfNeeded();
  await actorContext.locator('label').filter({ hasText: 'Vendor employee' }).click();
  await expect(actorContext.getByRole('radio', { name: 'Vendor employee' })).toBeChecked();

  const vendorLogin = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Continue in selected workspace' }).click();
  const vendorLoginResponse = await vendorLogin;
  expect(vendorLoginResponse.status()).toBe(200);
  const vendorLoginBody = await vendorLoginResponse.json() as { accessToken: string };
  expect(vendorLoginBody.accessToken).toBeTruthy();

  // Reload in a Vendor route so the page boundary and all navigation/actions
  // initialise from the cookie-backed active Vendor context.
  await page.goto('/vendor/pos');
  await expect(page.getByLabel('Active actor context')).toContainText('Vendor');
  await expect(page.getByRole('button', { name: 'Switch to Customer workspace' })).toBeVisible();

  const customerSwitchResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/context') && response.request().method() === 'POST'
  );
  const customerMeResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/me') && response.request().method() === 'GET'
  );
  await page.getByRole('button', { name: 'Switch to Customer workspace' }).click();
  const [customerSwitch, customerMe] = await Promise.all([customerSwitchResponse, customerMeResponse]);
  expect(customerSwitch.status()).toBe(200);
  // The /auth/me request after the context exchange is the UI permission source.
  await expect(page.getByLabel('Active actor context')).toContainText('Customer');
  expect(customerMe.status()).toBe(200);
  const customerSnapshot = await customerMe.json() as { actorType: string; tenantId: number | null; membershipId: string | null; permissions: string[] };
  expect(customerSnapshot.actorType).toBe('CUSTOMER');
  expect(customerSnapshot.tenantId).toBeNull();
  expect(customerSnapshot.membershipId).toBeNull();
  expect(customerSnapshot.permissions).toEqual(['CUSTOMER']);
  await expect(page.getByRole('alert')).toContainText('effective permission required for this page');
  await expect(page.getByRole('heading', { name: 'Open cashier session' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Switch to Vendor workspace' })).toBeVisible();

  const vendorSwitchResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/context') && response.request().method() === 'POST'
  );
  const vendorMeResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/auth/me') && response.request().method() === 'GET'
  );
  await page.getByRole('button', { name: 'Switch to Vendor workspace' }).click();
  const [vendorSwitch, vendorMe] = await Promise.all([vendorSwitchResponse, vendorMeResponse]);
  expect(vendorSwitch.status()).toBe(200);
  await expect(page.getByLabel('Active actor context')).toContainText('Vendor');
  expect(vendorMe.status()).toBe(200);
  const vendorSnapshot = await vendorMe.json() as { actorType: string; tenantId: number | null; membershipId: string | null; permissions: string[] };
  expect(vendorSnapshot.actorType).toBe('VENDOR');
  expect(vendorSnapshot.tenantId).toBe(tenantId);
  expect(vendorSnapshot.membershipId).toBeTruthy();
  expect(vendorSnapshot.membershipId).not.toBeNull();
  expect(vendorSnapshot.permissions).not.toContain('CUSTOMER');
  expect(vendorSnapshot.permissions.every(permission => !permission.startsWith('PLATFORM_'))).toBe(true);

  // Access with the pre-switch Vendor token must not outlive its rotated family.
  const staleVendor = await apiAs(page, vendorLoginBody.accessToken, '/api/v1/vendor/pos/sessions/active');
  expect([401, 403]).toContain(staleVendor.status);
});
