import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

// The e2e target runs with USSD_SESSION_TIMEOUT_SECONDS=30 so this is
// deterministic without a 181s wait. Chromium only — one slow wait is
// enough to prove the behaviour.
test.describe('session timeout', () => {
  test.skip(
    ({ browserName }) => browserName !== 'chromium',
    'one browser is sufficient for the timed wait',
  );

  test('an idle session expires and the next input starts fresh', async ({ page }) => {
    test.setTimeout(75_000);
    const ussd = new UssdPhone(page);
    await ussd.open(ACCOUNTS.alice.phone);
    await ussd.dial(); // MAIN_MENU
    await ussd.send('1'); // -> SEND_MONEY_PHONE

    await page.waitForTimeout(33_000); // exceed the 30s server timeout

    // Session gone -> a fresh MAIN_MENU; "2" routes to Withdraw
    // ("agent number"). Had state persisted we'd be at SEND_MONEY_PHONE
    // and "2" would be "Invalid phone number".
    const r = await ussd.send('2');
    expect(r.message.toLowerCase()).toContain('agent number');
  });
});
