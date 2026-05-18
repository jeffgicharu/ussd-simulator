import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

// Regression for PR #10: change-PIN must verify the current PIN and
// persist the new one. Uses a throwaway account (mutating a shared demo
// account's PIN would break parallel tests).
test('change PIN: current verified, new persisted, old rejected after', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const phone = uniquePhone();
  const oldPin = '1111';
  const newPin = '8888';

  await ussd.open(phone);
  await ussd.register(phone, oldPin);
  await ussd.deposit(phone, oldPin, '3000');

  // Change PIN.
  await ussd.setPhone(phone);
  await ussd.dial();
  await ussd.send('6'); // My Account
  expect((await ussd.send('2')).message).toMatch(/current pin/i);
  expect((await ussd.send(oldPin)).message).toMatch(/new pin/i);
  await ussd.send(newPin); // confirm prompt
  const done = await ussd.send(newPin);
  expect(done.message).toMatch(/changed successfully/i);

  // Old PIN no longer authorises a money op.
  await ussd.setPhone(phone);
  await ussd.dial();
  await ussd.send('4');
  expect((await ussd.send(oldPin)).message).toMatch(/wrong pin/i);

  // New PIN works.
  await ussd.setPhone(phone);
  await ussd.dial();
  await ussd.send('4');
  expect((await ussd.send(newPin)).message).toContain('KES');
});
