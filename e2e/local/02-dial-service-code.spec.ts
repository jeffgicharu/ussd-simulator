import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

test('dial *384# as a registered user shows the main menu', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.alice.phone);
  const r = await ussd.dial();

  expect(r.continueSession).toBe(true);
  for (const opt of [
    'Send Money', 'Withdraw', 'Buy Airtime', 'Check Balance',
    'Deposit', 'My Account', 'Loans',
  ]) {
    expect(r.message).toContain(opt);
  }
  await expect(page.locator('#screenContent')).toContainText('Send Money');
});
