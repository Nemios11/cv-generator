import { test, expect } from '@playwright/test';

// The CV view with fictional sample data, available without signing in.
test.beforeEach(async ({ page }) => {
  await page.goto('/demo');
});

test('renders the main sections from sample data', async ({ page }) => {
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alex Example');
  await expect(page.getByRole('heading', { name: 'Experience' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Technical skills' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Education' })).toBeVisible();
});

test('switching language updates content and <html lang>', async ({ page }) => {
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await page.getByRole('button', { name: 'Switch to Italian' }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'it');
  await expect(page.getByRole('heading', { name: 'Esperienze' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Competenze tecniche' })).toBeVisible();
});

test('ATS view shows every experience without tabs', async ({ page }) => {
  await page.getByRole('button', { name: 'ATS view' }).click();
  await expect(page.locator('body')).toHaveAttribute('data-template', 'ats');
  await expect(page.getByRole('tablist')).toBeHidden();
  await expect(page.getByRole('tabpanel')).toHaveCount(2);
  for (const panel of await page.getByRole('tabpanel').all()) {
    await expect(panel).toBeVisible();
  }
});
