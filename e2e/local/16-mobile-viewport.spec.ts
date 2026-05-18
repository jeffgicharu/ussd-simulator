import { test, expect, devices } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

test.use({ ...devices['Pixel 5'] });

test('canonical send-money flow works on an emulated Pixel 5 viewport', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const sender = uniquePhone();
  const pin = '7531';

  await ussd.open(sender);
  await expect(page.locator('.btn-dial')).toBeVisible();

  await ussd.register(sender, pin);
  await ussd.deposit(sender, pin, '2000');

  await ussd.setPhone(sender);
  await ussd.dial();
  await ussd.send('1');
  await ussd.send('0700000002');
  await ussd.send('100');
  const done = await ussd.send(pin);
  expect(done.continueSession).toBe(false);
  expect(done.message).toMatch(/confirmed/i);
});
