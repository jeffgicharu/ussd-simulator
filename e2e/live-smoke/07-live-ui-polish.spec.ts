import { test, expect } from '@playwright/test';

/**
 * Live guard for the visible polish fixes. Read-only: it only renders the
 * landing page and presses Dial once, so it is safe to run against the
 * shared live deployment on the daily cron.
 */
test.describe('live UI polish', () => {
  test('idle welcome line is clean and the log has a friendly empty state', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
    page.on('pageerror', (e) => errors.push(e.message));

    await page.goto('/');
    expect(await page.locator('#screenContent').textContent()).toBe(
      'Dial *384# to start.',
    );
    await expect(page.locator('#logEmpty')).toBeVisible();
    await expect(page.locator('#logEmpty')).toContainText('No requests yet');
    await expect(page.locator('link[rel="icon"]')).toHaveCount(1);
    expect(errors, `console errors: ${errors.join(' | ')}`).toEqual([]);
  });

  test('dialing populates the request log and activates the session', async ({
    page,
  }) => {
    await page.goto('/');
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ussd/api')),
      page.click('.btn-dial'),
    ]);
    await expect(page.locator('#logEmpty')).toBeHidden();
    await expect(page.locator('#sessionStatus')).toHaveText('Active');
    await expect(page.locator('#screenContent')).toContainText(
      'Welcome to M-Wallet',
    );
  });
});
