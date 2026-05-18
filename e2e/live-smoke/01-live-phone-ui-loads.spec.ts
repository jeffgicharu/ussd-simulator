import { test, expect } from '@playwright/test';

test('live phone simulator renders', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/USSD/i);
  await expect(page.locator('#screenContent')).toBeVisible();
  await expect(page.locator('.btn-dial')).toBeEnabled();
  await expect(page.locator('#phoneNumber')).toHaveValue('+254700000001');
});
