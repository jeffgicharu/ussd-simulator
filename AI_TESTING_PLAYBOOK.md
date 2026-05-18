# AI-Assisted Testing Playbook — ussd-simulator

How AI was actually used to build the test and quality layers of this
codebase, grounded in the real PRs that produced them. The goal is an
honest calibration — where AI accelerated the work, where it was neutral,
and where it actively had to be overruled.

## Why AI in testing for a Java/Spring Boot + HTML browser-UI codebase

`ussd-simulator` is a stateful menu engine (a screen state machine over
in-memory wallet state) plus a thin HTML phone simulator. That shape has
a lot of *mechanical* test surface — parameterised boundary tables, fuzz
input alphabets, multi-step session walks, Playwright page objects — and
a small amount of *load-bearing* reasoning — state-machine correctness,
PIN/lockout timing, log-injection, session ownership. AI is strong on the
first and dangerous on the second. The discipline below keeps it on the
mechanical work and keeps a human on the reasoning.

## The four-step workflow

1. **Frame.** Write the test intent and the exact behaviour first, in
   prose, before any prompt. "A caller supplying another phone's
   sessionId must not continue that session" is a frame; "write session
   tests" is not.
2. **Prompt with an exemplar.** Give the model one real, passing test
   from this repo as the shape to match (e.g. an existing
   `@SpringBootTest` + `MockMvc` integration test). Output that matches
   house style needs less rework.
3. **Review with skepticism.** Assume the assertions are wrong until
   proven. The model will happily assert the behaviour it *imagined*, not
   the behaviour the engine has. Every assertion is checked against the
   actual screen/source.
4. **Run + harden.** Run it; then harden against a mutation/adversary —
   for unit/service code that means PIT (does the test actually kill the
   mutant?), for the UI that means axe-core and cross-browser, for the
   webhook that means a fuzz alphabet.

## Worked example 1 — integration test scaffolding (PR #8)

**Frame:** the session lifecycle was untested — timeout, the max-session
cap, same-phone concurrency, eviction on END.

**Prompt (paraphrased):** "Here is `UssdIntegrationTest` (one passing
`@SpringBootTest` + MockMvc test, pasted). Write a
`SessionLifecycleIntegrationTest` with cases for: forced expiry rebuild,
the max-session cap, two concurrent sessions for one phone, eviction
after END, resumption within the window."

**Raw output:** good *shape* — correct annotations, a JSON-body helper,
sensible names. But three concrete problems: (a) the expiry test used a
real `Thread.sleep(181_000)` (a banned 3-minute flake); (b) the cap test
asserted an HTTP 500, but the cap actually threw an unhandled exception
that MockMvc rethrows; (c) it assumed `serviceCode` defaulted server-side
when the JSON path needs it explicit.

**Manual hardening:** replaced the sleep with a property-overridden
`ussd.session-timeout-seconds=-1` nested context (deterministic, no
wait); rewrote the cap assertion to characterise the *real* behaviour
(`assertThatThrownBy(...).hasRootCauseInstanceOf(...)`) — which then
surfaced a genuine robustness gap that became issue #7 and fix PR #11;
fixed the `serviceCode`. **Net: scaffolding was a strong accelerator;
every assertion needed correction against real behaviour.**

## Worked example 2 — the PIT-driven Clock-injection refactor (PR #12)

**Frame:** PIT showed surviving mutants on `validatePin` — the
`ConditionalsBoundary` and `RemoveConditional` mutants on the lockout
window (`System.currentTimeMillis() < lockExpiry`) and the
`attempts >= MAX_PIN_ATTEMPTS` threshold. They survived because no test
could advance time past the 15-minute cooldown.

**Where AI helped:** generating the boundary test table once the seam
existed — at/just-before/just-after `LOCKOUT_MS` — and the
`changePin`/`deposit`-guard kill tests. Mechanical, and good.

**Where AI did *not* help (the load-bearing decision):** the model's
first instinct was to suppress the survivors (exclude the mutators) or to
sleep past the cooldown. Both wrong. The correct move — refactor
`validatePin`/`isLocked` to read an **injectable `Clock`** (the same seam
already used for the daily limit), so the cooldown boundary becomes
testable while production behaviour is unchanged — was a human design
decision. The model accelerated the *tests around* the refactor; it would
have shipped a defanged gate if left to drive. **Net: AI neutral-to-good
on the tests, actively wrong on the remediation strategy.**

## Worked example 3 — the accessibility fix (PR #20)

**Frame:** the public phone UI must be axe-core clean (it's the demo
surface).

**Process:** axe-core (run via `@axe-core/playwright`) produced the
findings, not the model — one *critical* (`#phoneNumber`/`#serviceCode`
had no associated `<label>`) and *serious* contrast failures (status bar
text, Send/Dial/End buttons, with exact ratios). AI was useful to *apply*
the well-understood remedies: add `for=`/`aria-label`, and pick darker
green/red button shades. **Where it needed checking:** its first
suggested replacement colours still failed 4.5:1 — the fix was verified
by *re-running axe-core*, not by trusting the suggestion. The tool, not
the model, is the source of truth here; AI is a fast applicator of
tool-validated fixes.

## Anti-patterns to refuse from AI

- **Assertion theatre** — tests that assert on the response the model
  imagined, or on `not null` / `status 200` only, while claiming to test
  business behaviour.
- **Suppress-to-green** — excluding a Spotbugs/PIT finding instead of
  fixing it, or `|| true` on a gate.
- **Mock-the-thing-under-test** — mocking the engine/`SessionManager` so
  a "session test" exercises nothing.
- **Sleep-driven timing** — real `Thread.sleep` for timeouts/lockouts
  instead of an injected clock or a property override.
- **Plausible-but-wrong USSD shape** — inventing a `CON`/`END` contract
  or screen text rather than reading the screen classes.
- **Confident security assertions** — "this proves no hijack" without
  actually replaying a foreign sessionId.

## Productivity calibration (honest)

| Task | AI help |
|---|---|
| Parameterised JUnit boundary tables (fees, amount limits) | **Strong** — near-zero rework |
| State-machine fuzz alphabet + iteration loop | **Strong** — good scaffolding |
| Playwright page-object + spec shape | **Strong** — house-style match from one exemplar |
| `@SpringBootTest`/MockMvc scaffolding | **Good** — shape right, assertions wrong |
| curl verification script structure | **Good** — but missed the multi-line `grep` bug (false negatives) |
| Locust stateful user model (PR #16) | **Neutral** — needed real per-VU phone + SLO-gate design by hand |
| Session-hijack assertion edge cases | **Negative** — model asserted victim state was "isolated" without replaying the foreign sessionId |
| Log-sanitisation regex design (PR #18) | **Negative** — naive `\d+` masking proposals; digit-vs-menu-input tradeoff was a human call |
| AT-webhook `CON`/`END` conformance | **Negative** — repeatedly invented response text; only source reading was reliable |

## Prompt templates

1. **Integration test from exemplar**
   > Here is `<PassingTest.java>` (pasted). Following this exact style
   > (`@SpringBootTest`, the JSON helper, naming), write tests for
   > `<behaviours>`. Do **not** use `Thread.sleep`; if timing matters,
   > use a property override or the injectable `Clock`. List any
   > assumption you make about engine behaviour so I can verify it.

2. **Parameterised boundary table**
   > For `<method>` with thresholds `<list>`, generate a
   > `@ParameterizedTest` + `@CsvSource` covering each boundary at value,
   > value-1, value+1. Assert exact outcomes; do not collapse cases.

3. **Playwright spec from page object**
   > Using `e2e/helpers/ussd.ts` (`UssdPhone`), write a spec for
   > `<journey>`. Drive the real webhook (`waitForResponse`), assert the
   > parsed JSON, and use a throwaway phone for any write.

4. **Fuzz / property test**
   > Write a property test: N random walks over input alphabet
   > `<list>` from the menu; assert every response is HTTP 200, has a
   > boolean `continueSession`, and a non-empty message. Deterministic
   > seeds.

5. **Mutation-kill test**
   > PIT survivor: `<class:line> <operator>`. Write the smallest test
   > that observes the side-effect/boundary this mutant changes. Do not
   > suppress the mutator.

## When NOT to reach for AI

- **State-machine correctness reasoning** — whether a screen transition
  is *right* is a source-and-spec question, not a generation one.
- **Security-critical assertions** — session-hijack ownership, log
  injection, IDOR. Write these by hand and verify by actually executing
  the attack.
- **Time-sensitive flow testing** — the 180 s session timeout and 15 min
  PIN cooldown: design the time seam yourself; AI will reach for sleeps.
- **Remediation strategy for tool findings** — PIT/Spotbugs/axe tell you
  *what*; the *fix* (e.g. clock injection vs suppression) is a human
  design decision.

---
*See also: [QUALITY_DASHBOARD](QUALITY_DASHBOARD.md) ·
[AUDIT](AUDIT.md) · [TEST_STRATEGY](TEST_STRATEGY.md) ·
[TEST_PLAN](TEST_PLAN.md) · [QA_BEST_PRACTICES](QA_BEST_PRACTICES.md) ·
[MUTATION_TESTING](MUTATION_TESTING.md) ·
[PERFORMANCE_TESTING](PERFORMANCE_TESTING.md) ·
[SECURITY_TESTING](SECURITY_TESTING.md) ·
[E2E_VERIFICATION](E2E_VERIFICATION.md)*
