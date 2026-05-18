import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS, uniquePhone } from '../helpers/ussd';

test('send money happy path: funded sender -> bob, valid PIN, success END', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const sender = uniquePhone();
  const pin = '1357';

  await ussd.open(sender);
  await ussd.register(sender, pin);
  await ussd.deposit(sender, pin, '5000');

  await ussd.setPhone(sender);
  await ussd.dial();
  expect((await ussd.send('1')).message).toContain('recipient phone');
  expect((await ussd.send('0700000002')).message).toContain('amount');
  const confirm = await ussd.send('100');
  expect(confirm.message).toMatch(/PIN to confirm/i);
  const done = await ussd.send(pin);

  expect(done.continueSession).toBe(false);
  expect(done.message).toMatch(/confirmed/i);
  expect(done.message).toContain('100');

  // Sender balance reduced by amount + fee (5000 - 100 - fee).
  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('4');
  const bal = await ussd.send(pin);
  const kes = Number(bal.message.match(/KES\s+([\d.]+)/)?.[1]);
  expect(kes).toBeLessThan(5000);
  expect(kes).toBeGreaterThan(4800);
});
