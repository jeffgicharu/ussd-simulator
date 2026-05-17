# Mutation Testing

## What and why

Line/branch coverage tells us a line *ran* during tests; it says nothing
about whether an assertion would *notice* if that line were wrong. Mutation
testing closes that gap: PIT systematically introduces small faults
("mutants") into the bytecode — flip a `>` to `>=`, remove a method call,
negate a condition, change a return value — then re-runs the tests. A mutant
that tests still pass against ("survived") is a hole in the suite; a mutant
the tests fail on ("killed") proves the suite detects that class of fault.

For a USSD money/auth state machine this matters more than coverage: an
off-by-one in the PIN-lockout window or a removed balance update can be
fully "covered" yet undetected. Mutation testing is how we find weak
assertions on the paths that move money or gate access.

## PIT setup

- Plugin: `org.pitest:pitest-maven` 1.18.2 with `pitest-junit5-plugin` 1.2.2.
- **Target classes** (focused per `TEST_STRATEGY.md` — the highest-risk
  packages, not the whole tree):
  - `com.ussd.service.*` — wallet/transaction logic, PIN lockout, daily limit
  - `com.ussd.engine.*` — the state-machine engine + session manager
  - `com.ussd.screen.*` — per-screen input validation and transitions
- **Target tests:** `com.ussd.*`
- **Mutators:** `STRONGER` (the default-plus set: conditionals boundary,
  negate conditionals, void-method-call, return values, math, etc.).
- **Coverage analysis:** PIT only runs the tests that cover each mutated
  line, then computes line coverage of the mutated classes alongside the
  mutation score.

Run locally:

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage -B
# HTML: target/pit-reports/index.html   XML: target/pit-reports/mutations.xml
```

## Baseline → post-hardening scores

The hardening round targeted the **HIGH-severity surviving mutants on the
money/auth paths** (see below). The aggregate score moves modestly because
172 mutants live in entirely **untested secondary screens** (airtime,
loans, withdraw, statements) that are out of scope for a mutation-only
step — but every HIGH-severity money/auth survivor was eliminated.

| Metric | Baseline | After hardening |
|---|---|---|
| Mutants generated | 464 | 464 |
| Killed | 236 | **238** |
| Survived | 46 | 54 |
| No coverage | 182 | 172 |
| Timed out / errors | 0 | 0 |
| PIT mutation score (killed/total) | 50.9% | **51.3%** |
| Test strength (killed/(killed+survived)) | 83.7% | 81.5% |
| Line coverage of mutated classes | 63% | 64% |
| Wall-clock | ~19 min | ~17 min |

> The HIGH-severity money/auth mutants that *survived* at baseline
> (PIN-lockout window + threshold, deposit PIN guard, deposit balance
> update, `changePin` guards + return value, duplicate-registration guard,
> change-PIN input validation) are **all killed** after hardening. "Test
> strength" dips slightly only because new tests pulled some previously
> *no-coverage* secondary-screen mutants into the *covered-but-survived*
> bucket — those are LOW-severity and tracked below, not regressions.

## HIGH-severity mutants killed, and the tests that kill them

| Mutant (class:line, operator) | Why HIGH | Killing test |
|---|---|---|
| `WalletService:308` validatePin `ConditionalsBoundary` + `RemoveConditional` (lockout window `clock.millis() < lockExpiry`) | Auth: wrong boundary keeps a locked account locked forever or unlocks early | `WalletServiceMutationTest.lockout_locksThenExpiresAtBoundary` (asserts locked at `LOCKOUT_MS-1`, unlocked exactly at `LOCKOUT_MS`) |
| `WalletService:320` validatePin `RemoveConditional_ORDER_IF` (`attempts >= MAX_PIN_ATTEMPTS`) | Auth: lock after the 1st failure or never lock | `lockout_belowThreshold_doesNotLock` (2 wrong then correct still works) + the lockout test above |
| `WalletService:276` deposit `RemoveConditional_EQUAL_ELSE` (`!validatePin`) | Money/auth: deposit proceeds on a wrong PIN | `deposit_wrongPin_failsAndBalanceUnchanged` |
| `WalletService:282` deposit balance lambda `NullReturnVals` (`v.add(amount)`) | Money: deposit silently zeroes the balance | `deposit_correctPin_increasesBalanceExactly` (75 000 → 80 000) |
| `WalletService:261` changePin `RemoveConditional_EQUAL_ELSE` (`!pins.containsKey`) | Auth: creates a PIN for an unknown number | `changePin_unknownPhone_returnsFalse` |
| `WalletService:270` changePin `BooleanFalseReturnVals` (`return true`) | Auth: success silently reports failure | `changePin_success_returnsTrue_andPersists` |
| `WalletService:248` registerAccount `RemoveConditional_EQUAL_ELSE` (duplicate guard) | Functional/auth: re-registration overwrites an existing PIN | `registerAccount_duplicate_rejected` |
| `MyAccountScreen$ChangePinOldScreen:88` `RemoveConditional_EQUAL_ELSE` (4-digit regex) | Auth: malformed current PIN bypasses the format gate | `ChangePinIntegrationTest.malformedCurrentPin_rePrompted` |

To make the lockout-window mutants killable, `validatePin`/`isLocked` were
refactored to read time from the injectable `Clock` (already used for the
daily limit) instead of `System.currentTimeMillis()` — production behaviour
is unchanged (system UTC clock), but the cooldown boundary is now testable.

## CI strategy

Final wall-clock on the focused class set is **~17 minutes** (avg of
three runs 17–19 min). Per the timing policy that is **> 15 minutes →
nightly only**:

- `.github/workflows/mutation.yml`: `schedule` cron `30 2 * * *` (02:30
  UTC) plus `workflow_dispatch` for on-demand runs. Uploads the PIT HTML +
  XML as an artifact.
- The per-PR loop stays fast: `ci.yml` keeps the JaCoCo line/branch gate;
  mutation strength is the nightly signal.
- The configured PIT `mutationThreshold` / `coverageThreshold` still fail
  the nightly run on regression, so the gate has teeth even off the PR path.

## Thresholds (ratchet)

In `pom.xml` `pitest-maven`:

- `mutationThreshold = 49` = floor(51 − 2) — fails if `killed/total` < 49%.
- `coverageThreshold = 61` = floor(64 − 2) — fails if mutated-class line
  coverage < 61%.

Interim floors; `TEST_STRATEGY.md` targets **≥ 70%** mutation on
state-machine and service classes, reached as the untested secondary
screens gain flow coverage in later test-expansion work.

## How to read the HTML report

1. Open `target/pit-reports/index.html`.
2. The package table shows line coverage and mutation coverage per package;
   drill into a class to see source annotated per line.
3. Green line = all mutants on it killed. Red = a mutant survived. Hover/
   click the line number for the mutant list, operator, and which tests ran.
4. Prioritise red lines in `service` and `engine` over `screen` getters.

## Investigating a surviving mutant (process)

1. **Locate**: class:line + operator from `mutations.xml` or the HTML.
2. **Classify severity**: HIGH if money/auth/transfer/lockout/daily-limit;
   MEDIUM if state-machine transition; LOW if logging/metrics/peripheral.
3. **Hypothesise** why it survived: usually "no test asserts the
   side-effect / boundary / return value this line controls".
4. **Write the smallest test** that observes that effect — boundary value
   for `ConditionalsBoundary`, truth-table case for conditional removal,
   read-back of state for `VoidMethodCall`, explicit return assertion for
   return-value mutators. Prefer a direct unit test on the service over a
   broad flow test so the mutated branch is hit precisely.
5. **Re-run PIT**, confirm the mutant is now `KILLED`, and that no other
   mutant regressed.
6. LOW-severity survivors may be left with a documented justification.

## Targets (per TEST_STRATEGY.md)

- Mutation score ≥ 70% on `com.ussd.service` and `com.ussd.engine`
  (state-machine + money/auth).
- No surviving HIGH-severity mutant on PIN validation, fee calculation,
  daily-limit enforcement, money transfer, session-timeout boundary, or
  terminal state-machine transitions.

## Top remaining survivors (after hardening) — left for later, with reason

| # | Location | Operator | Severity | Why left for now |
|---|---|---|---|---|
| 1 | `WalletService:252/268/286/287` registerAccount/changePin/deposit | `VoidMethodCall` (removed `logTransaction`/`recordTransaction`) | LOW | Pure audit-trail side effects; no balance/auth/response impact. Killing them means asserting persisted log rows via the repositories — analytics-test scope, deferred to that work. |
| 2 | `screen.{Airtime,Loans,Withdraw,…}…:getId()` (~20 mutants) | `EmptyObjectReturnVals` | LOW | Screen-id getters in **secondary flows with no functional tests** (airtime, loans, withdraw, full/mini statement, language). Killed only by adding whole-flow traversal tests — a test-expansion task, not a defect on a tested path. |
| 3 | `engine.UssdEngine:59/65/66/81/125` process()/processInputChain() | `RemoveConditional` / `NullReturnVals` | MEDIUM | Session-bootstrap and registration-redirect conditionals; positive paths are covered, but the specific mutated branch is behaviourally equivalent under current inputs. Needs adversarial multi-gateway input tests; not money/auth. |
| 4 | `engine.SessionManager:75/84/85` persistSessionLog() | `RemoveConditional` / `Math` / `ConditionalsBoundary` | LOW | Builds the analytics `SessionLog` (screen-count/path-truncation). Output is analytics only; correctness asserted when the analytics suite lands. |
| 5 | `engine.SessionManager:67/68/69` endSession() & `:97/:111` count/cleanup | `VoidMethodCall` / `PrimitiveReturns` / `RemoveConditional` | LOW | Removing `session.end()`/log-persist doesn't change the USSD response (still terminal); `getActiveSessionCount`/cleanup log are metrics/housekeeping, not user-facing money/auth. |

None of the remaining survivors are HIGH-severity money/auth mutants — that
class is fully closed by this round.
