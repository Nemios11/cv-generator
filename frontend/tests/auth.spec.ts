import { test, expect } from '@playwright/test';

/**
 * End-to-end login against the real stack: Keycloak (compose.yaml), the backend
 * on :8080 and this frontend. Runs only when E2E_AUTH=1 (CI "stack" job).
 * Uses the fictional development user of the local realm.
 */
test.skip(!process.env.E2E_AUTH, 'requires the full stack (set E2E_AUTH=1)');

const USER = { username: 'demo', password: 'demo-local-only-password' };

test('sign in with PKCE, create, read and delete the own profile', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Keycloak login page.
  await expect(page).toHaveURL(/localhost:8180\/realms\/cv-generator\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(USER.username);
  await page.locator('#password').fill(USER.password);
  await page.locator('#kc-login').click();

  // Back in the app, authenticated: identity comes from /api/me.
  await expect(page.getByText('Hi, Demo User')).toBeVisible();
  await expect(page).not.toHaveURL(/code=/);

  // Start from a clean state if a previous run left a profile behind.
  const deleteButton = page.getByRole('button', { name: 'Delete profile' });
  if (await deleteButton.isEnabled()) {
    page.once('dialog', (d) => void d.accept());
    await deleteButton.click();
    await expect(page.getByText('Profile deleted.')).toBeVisible();
  }

  await expect(page.getByRole('heading', { name: "You don't have a profile yet" })).toBeVisible();
  await page.getByLabel('Full name').fill('Demo Example');
  await page.getByRole('button', { name: 'Create profile' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Demo Example');

  // The profile is persisted: still there after a reload.
  await page.reload();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Demo Example');

  page.once('dialog', (d) => void d.accept());
  await page.getByRole('button', { name: 'Delete profile' }).click();
  await expect(page.getByRole('heading', { name: "You don't have a profile yet" })).toBeVisible();

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
});
