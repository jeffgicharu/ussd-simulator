import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

// Regression for PR #9: the cumulative KES 300,000 / UTC-day cap. The
// limit is checked before the balance check, so the second transfer is
// rejected by the limit regardless of remaining funds.
test('cumulative daily transfer limit (300,000) blocks the next transfer', async ({ page }) => {
  test.slow();
  const ussd = new UssdPhone(page);
  const sender = uniquePhone();
  const pin = '5050';

  await ussd.open(sender);
  await ussd.register(sender, pin);
  await ussd.deposit(sender, pin, '300000'); // max single deposit

  // First transfer: 290,000 (under the daily cap) — succeeds.
  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('1');
  await ussd.send('0700000002');
  await ussd.send('290000');
  expect((await ussd.send(pin)).message).toMatch(/confirmed/i);

  // Second transfer: 20,000 would push the day's total to 310,000.
  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('1');
  await ussd.send('0700000002');
  await ussd.send('20000');
  const blocked = await ussd.send(pin);
  expect(blocked.continueSession).toBe(false);
  expect(blocked.message).toMatch(/exceeded today'?s transfer limit/i);
});
