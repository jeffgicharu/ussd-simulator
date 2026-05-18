import { test, expect } from '@playwright/test';
import { postUssd, ACCOUNTS } from '../helpers/ussd';

// Regression for the session-hijacking fix (PR #18): a session is bound
// to the phone that created it.
const BASE = process.env.E2E_LOCAL_BASEURL || 'http://localhost:8082';

test('phone B cannot continue phone A\'s session', async ({ request }) => {
  const sid = 'iso-' + Date.now();

  // A establishes a session and is mid-flow.
  const a1 = await postUssd(request, BASE, {
    sessionId: sid, phoneNumber: ACCOUNTS.alice.phone, input: '',
  });
  expect(a1.json.message).toContain('Send Money');
  await postUssd(request, BASE, {
    sessionId: sid, phoneNumber: ACCOUNTS.alice.phone, input: '4',
  });

  // B replays A's sessionId.
  const hijack = await postUssd(request, BASE, {
    sessionId: sid, phoneNumber: ACCOUNTS.bob.phone, input: '1234',
  });
  expect(hijack.status).toBe(200);
  expect(hijack.json.message).toMatch(/session error/i);
  expect(hijack.json.message).not.toContain('75000'); // no victim balance
});
