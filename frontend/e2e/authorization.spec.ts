import { test, expect } from '@playwright/test';
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

test('Platform Super Admin: platform workspace works and vendor POS context is rejected', async ({ page }) => {
  await signIn(page, demo.platformAdmin.username, demo.platformAdmin.password);
  await page.goto('/platform');
  await expect(page.getByRole('link', { name: 'Platform overview' })).toBeVisible();
  await page.goto('/vendor/pos');
  await expect(page.getByRole('alert')).toContainText('does not have access to this workspace');
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
  await expect(page.getByRole('form', { name: 'Sign in' })).toBeVisible();
});
test('Cashier: open assigned register, complete a cash sale and receive its receipt', async ({ page }) => {
  await signIn(page, demo.cashier.username, demo.cashier.password);
  await page.goto('/vendor/pos');
  await expect(page.getByRole('heading', { name: 'Open cashier session' })).toBeVisible();
  await page.getByRole('button', { name: 'Load assigned registers' }).click();
  await expect(page.getByText(/configured registers/)).toBeVisible();
  const register = page.getByLabel('Assigned register');
  const availableOption = register.locator('option:not([value=""])').first();
  await expect(availableOption).toContainText('Available');
  const registerValue = await availableOption.getAttribute('value');
  expect(registerValue).toBeTruthy();
  await register.selectOption(registerValue!);
  await page.getByLabel('Opening float').fill('0');
  await page.getByRole('button', { name: 'Open session' }).click();
  await expect(page.locator('.product-grid')).toBeVisible();
  await expect(page.locator('.product-grid button').first()).toBeVisible();
  await page.locator('.product-grid button').first().click();
  await page.getByRole('link', { name: 'Current Bill' }).click();
  await expect(page.getByRole('heading', { name: 'Current sale', level: 2 })).toBeVisible();
  await page.getByLabel('Cash received').fill('1000000');
  await page.getByRole('button', { name: 'Complete cash sale' }).click();
  await expect(page.getByText('Vendor sale receipt')).toBeVisible({ timeout: 30_000 });
});

test('Revoked custom Sales role loses existing bearer access and protected route after refresh', async ({ page }) => {
  const adminToken = await signIn(page, demo.vendorAdmin.username, demo.vendorAdmin.password);
  await ensureCustomSalesRole(page, adminToken);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('form', { name: 'Sign in' })).toBeVisible();
  const salesToken = await signIn(page, demo.salesExecutive.username, demo.salesExecutive.password);
  await page.goto('/vendor/pos/products');
  await expect(page.getByRole('heading', { name: 'Manage Products', level: 1 })).toBeVisible();
  const revoked = await apiAs(page, adminToken, '/api/v1/vendor/employees/' + encodeURIComponent(demo.salesExecutive.username) + '/roles', 'PUT', { roleIds: [], reason: 'Verify P2-01 permission revocation' });
  expect(revoked.status).toBe(200);
  const staleTokenRequest = await apiAs(page, salesToken, '/api/v1/vendor/products?page=0&size=20');
  expect(staleTokenRequest.status).toBe(403);
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Access denied');
});
