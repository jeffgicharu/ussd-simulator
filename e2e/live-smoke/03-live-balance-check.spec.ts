import { test, expect } from '@playwright/test';
import { UssdPhone, ACCOUNTS } from '../helpers/ussd';

test('live: alice checks balance (~KES 75,000 seeded)', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(ACCOUNTS.alice.phone);
  await ussd.dial();
  await ussd.send('4');
  const bal = await ussd.send(ACCOUNTS.alice.pin);
  expect(bal.continueSession).toBe(false);
  expect(bal.message).toMatch(/M-Wallet balance/i);
  // Seeded at 75,000; allow drift from same-day demo activity before the
  // 03:30 UTC reset, but it must be a plausible positive KES figure.
  const kes = Number(bal.message.match(/KES\s+([\d.]+)/)?.[1]);
  expect(kes).toBeGreaterThan(0);
});
