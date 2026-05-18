import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

test('live: dial *384# (alice) shows the main menu', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.alice.phone);
  const r = await ussd.dial();
  expect(r.continueSession).toBe(true);
  expect(r.message).toContain('Send Money');
  expect(r.message).toContain('Check Balance');
});
