import { test, expect, type Page } from '@playwright/test';

function collectThirdPartyRequests(page: Page): string[] {
  const external: string[] = [];
  page.on('request', (req) => {
    const url = new URL(req.url());
    if (url.protocol.startsWith('http') && !['127.0.0.1', 'localhost'].includes(url.hostname)) {
      external.push(req.url());
    }
  });
  return external;
}

test('anonymous visitors see the landing page with sign-in and demo', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your CV, always up to date');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();

  await page.getByRole('link', { name: 'See an example' }).click();
  await expect(page).toHaveURL(/\/demo$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Alex Example');
});

test('the landing page is translated', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Switch to Italian' }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'it');
  await expect(page.getByRole('button', { name: 'Accedi' })).toBeVisible();
});

for (const path of ['/', '/demo']) {
  test(`${path} loads no third-party resources`, async ({ page }) => {
    const external = collectThirdPartyRequests(page);
    await page.goto(path, { waitUntil: 'networkidle' });
    expect(external).toEqual([]);
  });
}
