import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

test('an unregistered number is routed to self-service registration', async ({ page }) => {
  const ussd = new UssdPhone(page);
  await ussd.open(uniquePhone());
  const r = await ussd.dial();

  expect(r.continueSession).toBe(true);
  expect(r.message.toLowerCase()).toContain('create a 4-digit pin');
});
