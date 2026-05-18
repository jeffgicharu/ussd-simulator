# End-to-End Verification

Browser (Playwright) + webhook (curl) verification at two layers:
comprehensive **local** and gentle **live-smoke**.

## Re-verified after redeploy on 2026-05-18

The live demo was redeployed with merged `main` (Spring Boot 3.5.14,
PRs #9/#10/#11/#18), closing the deployment-lag divergence (issue #19).
Verified against `https://ussd.jeffgicharu.com` post-redeploy:

| Fixed behaviour | Check | Result |
|---|---|---|
| Session hijack (#18) | A's sessionId reused by B | `END Session error` — **fixed live** |
| Daily transfer limit (PR #9) | deposit 300k, send 290k then 20k | 2nd `exceeded today's transfer limit` — **fixed live** |
| Change-PIN (PR #10) | change PIN; old vs new | old `Wrong PIN`, new authorises — **fixed live** |
| Graceful session-cap (PR #11) | `SessionLimitExceededException` in deployed jar + `UssdController` references it | typed handler **deployed** |
| Log sanitisation (#18) | CRLF + digit-run in input → server log | `input: 'ZZINJ*** ***_FAKELINE-...'` (digits masked, CRLF stripped) — **fixed live** |
| Spring Boot 3.5.14 (CVE bump) | boot banner + deployed jar lib | `(v3.5.14)`, `spring-boot-3.5.14.jar` — **live** |

Post-redeploy re-run: **live-smoke 12 passed / 0 skipped / 0 failed**
(the cross-user probe now *actively asserts* the secure behaviour — no
longer skip-with-reason). **curl-live 5/5 flows green.** Memory after
redeploy: free 96→111 Mi, available 485→511 Mi, new JVM RSS 236 MB
(Spring Boot 3.5.14, `-Xmx192m`) — no pressure increase, no JVM tuning
needed. Backup `ussd-simulator.jar.pre-redeploy-20260518-052016`
retained on the VPS for rollback.

## Tested against

| Target | URL | Notes |
|---|---|---|
| Local | `http://localhost:8082` (+ `:8083` low-cap) | docker-compose.e2e.yml, prod profile, `USSD_SESSION_TIMEOUT_SECONDS=8`, in-memory H2, reseeds on restart |
| Live | `https://ussd.jeffgicharu.com` | Cloudflare-fronted; seeded demo accounts; daily reset 03:30 UTC. **Runs a pre-PR-#18 build** (not redeployed in this step — see Divergence) |
| Date | 2026-05-18 (UTC) | |

Demo accounts: alice `+254700000001`/1234, bob `+254700000002`/5678,
carol `+254700000003`/4321. Write-heavy local tests use throwaway numbers
(parallel-safe); live tests are read-only / pre-PIN only.

## Local Playwright results (16 spec files, 36 tests/run × browser)

Run: chromium + firefox here (35 passed, 1 skipped — the timed
session-timeout spec is chromium-only by design). **webkit** cannot
launch on this host (missing system libs the failed apt `--with-deps`
step would install); it runs in CI where `--with-deps` succeeds, so all
three browsers are exercised on every PR.

| Spec | chromium | firefox | webkit (CI) |
|---|---|---|---|
| 01 browser-ui-loads (render + axe-core a11y) | ✅ | ✅ | CI |
| 02 dial-service-code | ✅ | ✅ | CI |
| 03 unregistered-routing | ✅ | ✅ | CI |
| 04 register-new-user (+ fund) | ✅ | ✅ | CI |
| 05 send-money happy path | ✅ | ✅ | CI |
| 06 send-money insufficient balance | ✅ | ✅ | CI |
| 07 send-money wrong PIN | ✅ | ✅ | CI |
| 08 PIN lockout | ✅ | ✅ | CI |
| 09 change-PIN flow (regression PR #10) | ✅ | ✅ | CI |
| 10 daily-transfer-limit (regression PR #9) | ✅ | ✅ | CI |
| 11 balance + mini-statement | ✅ | ✅ | CI |
| 12 session timeout | ✅ | (chromium-only) | (chromium-only) |
| 13 session-cap graceful (regression PR #11) | ✅ | ✅ | CI |
| 14 cross-user isolation (regression PR #18) | ✅ | ✅ | CI |
| 15 state-machine fuzz (50 iterations) | ✅ | ✅ | CI |
| 16 mobile viewport (Pixel 5) | ✅ | ✅ | CI |

**Net local: 35 passed, 1 skipped, 0 failed.**

## Live-smoke results (6 spec files)

| Spec | chromium | firefox |
|---|---|---|
| 01 phone UI loads | ✅ | ✅ |
| 02 dial *384# (alice) → menu | ✅ | ✅ |
| 03 balance check (alice) | ✅ | ✅ |
| 04 send read-only walk (stops pre-PIN) | ✅ | ✅ |
| 05 cross-user probe (regression PR #18) | ⏭ skip-with-reason | ⏭ skip-with-reason |
| 06 Cloudflare perf baseline | ✅ | ✅ |

**Net live: 10 passed, 2 skipped (documented divergence), 0 failed.**

## Local vs live divergence

`05-live-cross-user-probe` is the one divergence: the session-hijacking
regression **passes locally** (`e2e/local/14`) but the same probe on live
shows the victim's session continuing instead of `Session error`. Cause:
**the live demo runs a build from the initial deployment and has not been
redeployed**, so PR #18 (and PRs #9/#10/#11, the log-sanitisation, and
the Spring Boot 3.5.14 CVE bump) are merged to `main` but not yet live.
This is a deployment-lag divergence, not a code regression — tracked in
**issue #19**; redeployment is a separately planned step. The live probe
self-adapts: it skips-with-reason while live predates #18 and will
automatically assert the secure behaviour once live is redeployed.

## curl webhook verification

| Target | Flows | Pass | Fail |
|---|---|---|---|
| Local (`/ussd/callback`) | 10 (dial, nav, invalid, send, withdraw, deposit, airtime, balance, account, loans) | 10 | 0 |
| Live (read-only) | 5 (dial, nav, send-walk-no-PIN, balance, invalid) | 5 | 0 |

Full table written to `scripts/verify-endpoints-result.md` (gitignored).
Both the JSON `/ussd/api` and form `/ussd/callback` surfaces return the
correct `CON`/`END` shape on both targets.

## What works in the live demo today

- Browser phone simulator loads and is interactive on all browsers.
- Dial `*384#` → correct M-Wallet main menu.
- Balance check returns the seeded amount.
- Send-money flow navigates correctly up to PIN entry.
- Invalid input safely re-displays the menu.
- AT webhook contract (`CON`/`END`, plain text) is correct.

## What's broken / surfaced

- **Live session-hijacking exposure** (issue #19): the live build predates
  the PR #18 fix, so a caller can continue another phone's session on the
  live demo. Fix is merged to `main` with local regression coverage;
  closes on the next (separately planned) redeploy.
- **Browser-UI accessibility defects — fixed inline this step:** axe-core
  found one *critical* (form inputs `#phoneNumber`/`#serviceCode` had no
  associated `<label>`) and *serious* colour-contrast failures (status
  bar text, Send/Dial/End buttons). Fixed in `static/index.html`
  (label `for=` association + `aria-label`, darker button greens/red,
  lighter status-bar text); `e2e/local/01` axe assertion now green. This
  improvement is part of `main` and will reach the live demo on the next
  redeploy.

## Known and characterized (open issues mapped to tests)

| Issue | What | Verified by |
|---|---|---|
| #19 | Live runs pre-#18 build (session-hijack live) | `e2e/live-smoke/05` (skip-with-reason) + `e2e/local/14` (green) |
| #13/#14/#15 | Deferred LOW perf (logging/GC/threads) | out of scope here; perf suite |
| #17 | Analytics IDOR (deferred MEDIUM) | security suite |

## Cloudflare-attributed latency

Live page load (Navigation Timing API): **TTFB ≈ 251 ms, DOMContentLoaded
≈ 1051 ms, load ≈ 1052 ms**. The origin serves the same static page in
the low tens of ms locally, so ≈ 250 ms TTFB is Cloudflare edge + TLS +
client↔Germany-origin transit. This sits between the prior baselines
noted for the estate: comparable to ContractorOS's baseline and below
the wallet pair's ~700 ms — consistent with a single small static
document plus one JSON XHR per USSD step rather than a heavier SPA bundle.

---
*See also: [QUALITY_DASHBOARD](QUALITY_DASHBOARD.md) · [AUDIT](AUDIT.md) · [TEST_STRATEGY](TEST_STRATEGY.md) · [TEST_PLAN](TEST_PLAN.md) · [QA_BEST_PRACTICES](QA_BEST_PRACTICES.md) · [MUTATION_TESTING](MUTATION_TESTING.md) · [PERFORMANCE_TESTING](PERFORMANCE_TESTING.md) · [SECURITY_TESTING](SECURITY_TESTING.md) · [E2E_VERIFICATION](E2E_VERIFICATION.md) · [AI_TESTING_PLAYBOOK](AI_TESTING_PLAYBOOK.md)*
