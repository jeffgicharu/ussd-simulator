import { test, expect } from '@playwright/test';
import { UssdPhone, uniquePhone } from '../helpers/ussd';

test('three wrong PINs lock the account; correct PIN still rejected within the window', async ({ page }) => {
  const ussd = new UssdPhone(page);
  const phone = uniquePhone();
  const pin = '4812';

  await ussd.open(phone);
  await ussd.register(phone, pin);

  for (let i = 0; i < 3; i++) {
    await ussd.setPhone(phone);
    await ussd.dial();
    await ussd.send('4');
    const r = await ussd.send('0000');
    expect(r.message).toMatch(/wrong pin/i);
  }

  // Locked: even the correct PIN fails until the cooldown expires.
  await ussd.setPhone(phone);
  await ussd.dial();
  await ussd.send('4');
  const locked = await ussd.send(pin);
  expect(locked.message).toMatch(/wrong pin/i);
});
