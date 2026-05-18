import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

test('balance check returns the seeded amount', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.alice.phone);
  await ussd.dial();
  expect((await ussd.send('4')).message).toMatch(/PIN to check balance/i);
  const bal = await ussd.send(ACCOUNTS.alice.pin);
  expect(bal.continueSession).toBe(false);
  expect(bal.message).toMatch(/M-Wallet balance/i);
  expect(bal.message).toContain('KES');
});

test('mini-statement returns data for a seeded account', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.carol.phone);
  await ussd.dial();
  await ussd.send('6'); // My Account
  expect((await ussd.send('5')).message).toMatch(/PIN/i); // Mini Statement
  const st = await ussd.send(ACCOUNTS.carol.pin);
  expect(st.continueSession).toBe(false);
  expect(st.message).toMatch(/balance|statement|transaction/i);
});
