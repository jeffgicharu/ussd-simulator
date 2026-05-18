import { test, expect } from '@playwright/test';

// Captures the live (Cloudflare-fronted) page-load timing for the record.
// Informational: it asserts only a generous ceiling so transient edge
// latency never flakes the smoke; the precise number goes in
// E2E_VERIFICATION.md.
test('live: capture Cloudflare-attributed page-load timing', async ({ page }) => {
  await page.goto('/', { waitUntil: 'load' });

  const nav = await page.evaluate(() => {
    const n = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
    return {
      ttfb: Math.round(n.responseStart - n.requestStart),
      domContentLoaded: Math.round(n.domContentLoadedEventEnd - n.startTime),
      load: Math.round(n.loadEventEnd - n.startTime),
    };
  });

  console.log(`[live perf] TTFB=${nav.ttfb}ms DCL=${nav.domContentLoaded}ms load=${nav.load}ms`);
  expect(nav.ttfb).toBeGreaterThan(0);
  expect(nav.load).toBeLessThan(15_000); // generous ceiling, not an SLO
});
