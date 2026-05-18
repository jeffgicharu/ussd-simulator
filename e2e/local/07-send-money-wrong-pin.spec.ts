import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

test('send money with the wrong PIN is rejected', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const sender = uniquePhone();
  const pin = '3690';

  await ussd.open(sender);
  await ussd.register(sender, pin);
  await ussd.deposit(sender, pin, '5000');

  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('1');
  await ussd.send('0700000002');
  await ussd.send('100');
  const done = await ussd.send('0000'); // wrong PIN

  expect(done.continueSession).toBe(false);
  expect(done.message).toMatch(/wrong pin/i);

  // The money did not move — balance is intact.
  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('4');
  const bal = await ussd.send(pin);
  expect(bal.message).toContain('5000');
});
