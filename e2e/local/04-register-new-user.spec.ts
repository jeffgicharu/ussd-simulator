import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

test('full registration: create PIN, confirm, then reach the menu and fund', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const phone = uniquePhone();
  const pin = '2468';
  await ussd.open(phone);

  // create PIN -> confirm -> registered (END)
  expect((await ussd.dial()).message.toLowerCase()).toContain('create a 4-digit pin');
  expect((await ussd.send(pin)).message.toLowerCase()).toContain('confirm your pin');
  const done = await ussd.send(pin);
  expect(done.continueSession).toBe(false);
  expect(done.message).toContain('Registration successful');

  // Re-dial: now registered -> main menu.
  const menu = await ussd.dial();
  expect(menu.message).toContain('Send Money');

  // New account starts at zero balance.
  await ussd.send('4');
  const bal = await ussd.send(pin);
  expect(bal.message).toContain('KES 0.00');

  // Fund it and confirm the deposit lands.
  const dep = await ussd.deposit(phone, pin, '5000');
  expect(dep.continueSession).toBe(false);
  expect(dep.message).toMatch(/confirmed|deposited/i);
});
