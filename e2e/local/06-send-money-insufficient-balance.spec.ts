import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

// A fresh account is funded with only 1,000; sending 90,000 is within the
// per-transaction max (500,000) but exceeds the balance — exercising the
// insufficient-balance path (an amount > 500,000 would be rejected earlier
// by the max-amount rule, a different branch).
test('send money is rejected when the amount exceeds the balance', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const sender = uniquePhone();
  const pin = '2244';

  await ussd.open(sender);
  await ussd.register(sender, pin);
  await ussd.deposit(sender, pin, '1000');

  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('1');
  await ussd.send('0700000002');
  await ussd.send('90000');
  const done = await ussd.send(pin);

  expect(done.continueSession).toBe(false);
  expect(done.message).toMatch(/insufficient balance/i);
});
