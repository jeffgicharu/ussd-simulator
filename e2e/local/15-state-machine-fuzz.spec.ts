import { test, expect } from '@playwright/test';
import { postUssd, ACCOUNTS } from '../helpers/ussd';

const BASE = process.env.E2E_LOCAL_BASEURL || 'http://localhost:8082';
const ALPHABET = ['', '0', '1', '2', '4', '7', '9', '1234',
  '0700000002', 'abc', '*', '#', '-1', '999999999'];

// Property test: 50 random input walks from the menu never crash the app
// and always yield a well-formed USSD response (HTTP 200, boolean
// continueSession, non-empty message).
test('state-machine fuzzing yields only valid CON/END responses', async ({ request }) => {
  test.slow();
  for (let iter = 0; iter < 50; iter++) {
    const sid = `fuzz-${Date.now()}-${iter}`;
    const phone = [ACCOUNTS.alice, ACCOUNTS.bob, ACCOUNTS.carol][iter % 3].phone;
    const steps = 1 + (iter % 6);
    for (let s = 0; s < steps; s++) {
      const input = ALPHABET[Math.floor(Math.random() * ALPHABET.length)];
      const r = await postUssd(request, BASE, { sessionId: sid, phoneNumber: phone, input });
      expect(r.status, `iter ${iter} step ${s} input ${JSON.stringify(input)}`).toBe(200);
      expect(typeof r.json.continueSession).toBe('boolean');
      expect(r.json.message.length).toBeGreaterThan(0);
      if (!r.json.continueSession) break; // session ended; next iteration
    }
  }
});
