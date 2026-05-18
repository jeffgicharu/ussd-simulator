import { defineConfig, devices } from '@playwright/test';

/**
 * Two target layers:
 *  - local-*      → the docker/Spring target on :8082, comprehensive,
 *                   no retries, full timeout, parallel (run with -j4).
 *  - live-*       → https://ussd.jeffgicharu.com, gentle: 1 worker,
 *                   2 retries, 30s timeout (Cloudflare-fronted, shared).
 * Each layer runs on chromium + firefox + webkit.
 */
const LOCAL = process.env.E2E_LOCAL_BASEURL || 'http://localhost:8082';
const LIVE = process.env.E2E_LIVE_BASEURL || 'https://ussd.jeffgicharu.com';

export default defineConfig({
  testDir: '.',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { outputFolder: '../playwright-report', open: 'never' }]],
  outputDir: '../test-results',
  use: {
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    // ── local (comprehensive) ──
    {
      name: 'local-chromium',
      testMatch: /local\/.*\.spec\.ts/,
      retries: 0,
      timeout: 60_000,
      use: { ...devices['Desktop Chrome'], baseURL: LOCAL },
    },
    {
      name: 'local-firefox',
      testMatch: /local\/.*\.spec\.ts/,
      retries: 0,
      timeout: 60_000,
      use: { ...devices['Desktop Firefox'], baseURL: LOCAL },
    },
    {
      name: 'local-webkit',
      testMatch: /local\/.*\.spec\.ts/,
      retries: 0,
      timeout: 60_000,
      use: { ...devices['Desktop Safari'], baseURL: LOCAL },
    },
    // ── live-smoke (gentle) ──
    {
      name: 'live-chromium',
      testMatch: /live-smoke\/.*\.spec\.ts/,
      retries: 2,
      timeout: 30_000,
      use: { ...devices['Desktop Chrome'], baseURL: LIVE },
    },
    {
      name: 'live-firefox',
      testMatch: /live-smoke\/.*\.spec\.ts/,
      retries: 2,
      timeout: 30_000,
      use: { ...devices['Desktop Firefox'], baseURL: LIVE },
    },
    {
      name: 'live-webkit',
      testMatch: /live-smoke\/.*\.spec\.ts/,
      retries: 2,
      timeout: 30_000,
      use: { ...devices['Desktop Safari'], baseURL: LIVE },
    },
  ],
});
