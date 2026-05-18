import { test, expect } from '@playwright/test';

/**
 * Regression for the broken mobile layout: the three-column flex row had
 * fixed widths and never stacked, so on a phone the Settings panel was
 * pushed off-screen and the page overflowed horizontally (~713px wide on
 * a 375px viewport). The layout must collapse to a single stacked column.
 */
test.describe('Mobile layout (iPhone 13 — 375x812)', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('no horizontal overflow and every panel is on-screen', async ({
    page,
  }) => {
    await page.goto('/');

    const { scrollWidth, clientWidth } = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    // Allow a 1px rounding slack; the bug overflowed by ~340px.
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);

    for (const sel of ['.settings', '.phone', '.log-panel']) {
      const box = await page.locator(sel).boundingBox();
      expect(box, `${sel} should be laid out`).not.toBeNull();
      expect(box!.x, `${sel} starts off-screen`).toBeGreaterThanOrEqual(-1);
      expect(
        box!.x + box!.width,
        `${sel} overflows the viewport`,
      ).toBeLessThanOrEqual(clientWidth + 1);
    }
  });

  test('panels are stacked vertically, not in a row', async ({ page }) => {
    await page.goto('/');
    const settings = (await page.locator('.settings').boundingBox())!;
    const log = (await page.locator('.log-panel').boundingBox())!;
    // Stacked: the log panel sits below the settings panel.
    expect(log.y).toBeGreaterThan(settings.y + settings.height - 1);
  });

  test('core dial flow still works at phone width', async ({ page }) => {
    await page.goto('/');
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ussd/api')),
      page.click('.btn-dial'),
    ]);
    await expect(page.locator('#screenContent')).toContainText(
      'Welcome to M-Wallet',
    );
  });
});
