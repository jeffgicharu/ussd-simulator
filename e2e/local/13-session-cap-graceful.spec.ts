import { test, expect } from '@playwright/test';
import { postUssd } from '../helpers/ussd';

// Regression for PR #11. A dedicated low-cap instance runs on :8083
// (USSD_MAX_SESSIONS=2) so we can hit the cap deterministically without
// touching the main target or the live demo.
const CAP_BASE = process.env.E2E_CAP_BASEURL || 'http://localhost:8083';

test('exceeding the session cap returns a graceful END, not an error', async ({ request }) => {
  const a = await postUssd(request, CAP_BASE, {
    sessionId: 'cap-A', phoneNumber: '+254700000001', input: '',
  });
  const b = await postUssd(request, CAP_BASE, {
    sessionId: 'cap-B', phoneNumber: '+254700000001', input: '',
  });
  expect(a.status).toBe(200);
  expect(b.status).toBe(200);

  const c = await postUssd(request, CAP_BASE, {
    sessionId: 'cap-C', phoneNumber: '+254700000001', input: '',
  });
  expect(c.status).toBe(200);
  expect(c.json.continueSession).toBe(false);
  expect(c.json.message).toMatch(/temporarily unavailable/i);
});
