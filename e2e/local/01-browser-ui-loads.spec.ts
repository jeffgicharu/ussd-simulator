import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Browser phone UI loads', () => {
  test('renders, no console errors, primary controls reachable', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
    page.on('pageerror', (e) => errors.push(e.message));

    await page.goto('/');
    await expect(page).toHaveTitle(/USSD/i);
    await expect(page.locator('#screenContent')).toBeVisible();
    await expect(page.locator('#phoneNumber')).toHaveValue('+254700000001');
    await expect(page.locator('#serviceCode')).toHaveValue('*384#');
    await expect(page.locator('.btn-dial')).toBeEnabled();
    await expect(page.locator('#userInput')).toBeVisible();
    await expect(page.locator('.phone-input-area button')).toBeVisible();

    expect(errors, `console errors: ${errors.join(' | ')}`).toEqual([]);
  });

  test('axe-core: no serious/critical accessibility violations', async ({ page }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(
      serious,
      serious.map((v) => `${v.id}: ${v.help}`).join('\n'),
    ).toEqual([]);
  });
});
