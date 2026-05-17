# QA Best Practices — ussd-simulator

Working conventions for everyone changing this codebase. These are the
rules a reviewer will hold a PR to.

## Code review checklist

A change is not approvable until every box is honestly ticked:

1. **Tests exist for the change** and fail without it (verified, not assumed).
2. **New menu screen → traversal test**: it is reachable from its parent,
   renders, handles valid input, and rejects invalid input safely.
3. **New/changed state transition → an integration test drives the full
   path** through `UssdEngine`, not just the screen in isolation.
4. **Bug fix → a regression test** that reproduces the original failure.
5. **AT contract preserved**: responses still start with `CON `/`END ` and
   stay within the USSD length budget.
6. **Session safety**: no new unbounded growth in `SessionManager`; timeout
   and the max-session cap still hold; no per-session state leaks into
   shared maps.
7. **Money paths are exact**: `BigDecimal` only (never `double`), correct
   scale/rounding, fee tier boundaries (`<=`) checked.
8. **PIN/lockout untouched or re-tested**: 4-digit validation, 3-attempt
   lockout, 15-minute cooldown, counter reset on success.
9. **No secrets, real MSISDNs, or production data** in code or fixtures.
10. **Config stays env-driven** with safe local defaults (see
    `CONFIGURATION.md`); the `prod` profile keeps the H2 console off.
11. **Input is validated at the screen boundary**; no unhandled
    `NumberFormatException`/NPE on hostile input.
12. **No new flaky constructs**: no real sleeps, wall-clock assertions, or
    ordering assumptions (see Flaky Test Policy).
13. **Public behaviour change → docs updated** (`README`, `AUDIT.md`,
    `TEST_PLAN.md` as relevant).
14. **Coverage & mutation gates not lowered** to make a change pass.
15. **CI is green** — no skipped/ignored tests sneaked in to go green.

## Test naming conventions

BDD-style JUnit method names — `should<Expected>When<Condition>`:

- `shouldRejectTransferWhenPinIsLockedOut`
- `shouldRouteUnregisteredNumberToRegistration`
- `shouldChargeZeroFeeWhenAmountIsAtOrBelow100`
- `shouldExpireSessionAfter180SecondsIdle`

Add `@DisplayName` with a human sentence for reports. Group related cases
in `@Nested` classes named for the screen/flow (e.g. `SendMoneyConfirm`).
Parameterise boundary tables (`@ParameterizedTest` + `@CsvSource`) rather
than copy-pasting near-identical tests.

## Test independence

- **No shared mutable state between tests.** Wallet state is in-memory and
  process-wide; use a fresh Spring context (`@DirtiesContext` where needed)
  or unique synthetic phone numbers per test so tests cannot collide.
- **Unique `sessionId` per test** (test-name or UUID prefixed).
- **Any order, any subset, fully parallel-safe.** No test may depend on
  another having run first.
- **No reliance on the daily 03:30 UTC reset** for correctness — that is a
  demo convenience, not a test fixture.
- Each test sets up and tears down its own data; no "leftover from the
  previous test" assumptions.

## Flaky test policy

- A test that fails intermittently is **quarantined within one working
  day** (`@Disabled` with a linked tracking issue) — never left to
  randomly red the pipeline, never `@Disabled` without an issue.
- Quarantine is a debt with a deadline, not a resting place: fix or delete
  within the agreed window.
- **Root-cause, don't retry-loop.** Banned flake sources here: real
  `Thread.sleep`, asserting on wall-clock timestamps, depending on
  `@Scheduled` timing, HashMap iteration order, or live-network calls in
  unit/integration tests. Use injectable clocks / awaitility-style polling
  with bounded timeouts instead.
- Three quarantined tests in the same area blocks new feature work there
  until the area is stabilised.

## Mock vs real dependencies

This is a self-contained, in-memory system — fakes add risk, not safety.

- **Use the real**: `UssdEngine`, all `UssdScreen`s, `SessionManager`,
  `WalletService`, and H2. They are fast and deterministic; testing them
  for real is the whole point of a state-machine app.
- **Mock only the outer edge**: the Africa's Talking request/response shape
  (and, later, any genuinely external integration such as the optional
  wallet API). Mock at the HTTP boundary, not internal collaborators.
- **Never mock the state machine to "simplify" a test** — that deletes the
  thing under test.
- Prefer driving behaviour through the public entry points
  (`/ussd/callback`, `/ussd/api`, `engine.process`) over reflective poking
  at internals.

## Commit conventions for tests

Conventional-commit prefixes, scoped by test layer:

| Prefix | Use |
|---|---|
| `test(api):` | webhook / JSON API behaviour tests |
| `test(integration):` | multi-step engine + H2 traversal tests |
| `test(contract):` | AT `CON`/`END`/length conformance |
| `test(security):` | PIN brute force, lockout, hijacking, fuzzing |
| `test(performance):` | load / concurrency / soak |
| `test(e2e):` | Playwright browser-UI tests |

Examples:
`test(security): cover cross-session PIN lockout for a single phone`
`test(integration): drive onboarding through to a funded P2P transfer`

Production-code commits keep `feat:` / `fix:` / `docs:` / `refactor:`.

## PR requirements

- Every new **menu screen** ships with its render + input tests and is
  proven reachable through the engine.
- Every new or modified **state transition** ships with an integration
  test traversing the full path.
- Every **bug fix** ships with a regression test that fails on the old code.
- Coverage and mutation thresholds (see `TEST_STRATEGY.md`) are met; gates
  are not weakened to pass.
- PR description states which `TEST_PLAN.md` scenario IDs the change adds
  or affects.
- CI green with no newly skipped/ignored tests.

## Onboarding — running and reading tests

**Run locally:**

```bash
mvn clean verify -B            # compile + all tests (current: 23, green)
mvn -Dtest=UssdEngineTest test # a single test class
mvn spring-boot:run            # app on http://localhost:8181 to poke by hand
```

Manual USSD check (URL-encode `+` in form bodies):

```bash
curl -X POST http://localhost:8181/ussd/callback \
  --data-urlencode "sessionId=dev1" \
  --data-urlencode "phoneNumber=+254700000001" \
  --data-urlencode "text=4*1234"     # expect: END ... balance ...
```

**Reading a CI failure:**

1. Open the failed GitHub Actions run → the `Build and test` step.
2. Find `Tests run: … Failures: …`; the failing
   `Class#shouldDoXWhenY` and its assertion message point at the broken
   behaviour — the method name tells you the expected contract.
3. Reproduce locally with `mvn -Dtest=Class#method test`.
4. If it only fails in CI, suspect a flake source from the policy above
   (timing, ordering, shared in-memory state) before assuming an env bug.
5. Fix the behaviour or the test — never silence with `@Disabled` to go
   green without a tracking issue.

New contributors: read `AUDIT.md` (where we are) and `TEST_STRATEGY.md`
(where we're going) before adding tests, so new tests fit the pyramid
instead of piling onto the happy path.
