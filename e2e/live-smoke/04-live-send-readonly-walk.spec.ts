import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

// Walks the send-money flow up to the PIN prompt and STOPS — no PIN is
// entered, so no money moves on the live demo (preserves seed data).
test('live: send-money flow reachable, stops before PIN', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.alice.phone);
  await ussd.dial();
  expect((await ussd.send('1')).message).toMatch(/recipient phone/i);
  expect((await ussd.send('0700000002')).message).toMatch(/amount/i);
  const confirm = await ussd.send('100');
  expect(confirm.continueSession).toBe(true);
  expect(confirm.message).toMatch(/PIN to confirm/i);
  // Intentionally end here — never submit the PIN against live.
});
