# Test Strategy — ussd-simulator

## Purpose & Scope

This document defines how `ussd-simulator` is tested. The system under test
is a **single Spring Boot service** that exposes an Africa's Talking USSD
webhook, a JSON API, an analytics/metrics surface, and a self-contained
browser phone simulator, backed by an **in-memory state machine and H2
store**. Scope covers everything in this repository: the USSD engine and
screens, session management, wallet/transaction logic, the HTTP surface,
the browser UI, and the live deployment at https://ussd.jeffgicharu.com.

Audiences: engineers writing features, reviewers gating PRs, and future
contributors onboarding to the codebase.

## Testing Philosophy

1. **State-machine completeness beats line coverage.** A USSD app is its
   menu graph. Every screen transition — including invalid input and
   back-navigation at every node — matters more than hitting a coverage
   percentage. Coverage is a floor, traversal completeness is the goal.
2. **The session lifecycle is the highest-risk surface.** Sessions are
   in-memory, time-bounded (180 s), and concurrent. Timeout, eviction, the
   10 000-session cap, and same-phone collisions are tested explicitly, not
   assumed.
3. **AT-webhook contract conformance is non-negotiable.** Every terminal
   response must be correctly prefixed (`CON ` to continue, `END ` to
   terminate) and stay within USSD length limits. A contract regression
   silently breaks every real handset.
4. **Prefer real collaborators.** The state machine, `SessionManager`, and
   H2 are fast and deterministic — use the real ones. Mock only the outer
   edge (the AT request/response shape). A test that mocks the engine tests
   nothing.
5. **Security is first-class for a money + PIN flow.** PIN brute force,
   lockout, session hijacking, and state fuzzing get a dedicated suite, not
   incidental coverage.
6. **The live demo is a test target, not just a showcase.** Because a
   hosted instance exists, E2E, DAST, and load tests run against a
   production-like surface — with the daily 03:30 UTC reset as a known
   clean baseline.

## Test Pyramid for ussd-simulator

| Layer | Tooling | What it covers |
|---|---|---|
| **Unit** | JUnit 5 + AssertJ + Mockito | `WalletService` (15 fee tiers, lockout state machine, insufficient balance, deposit/withdraw/airtime), per-screen `handleInput` validators, `UssdSession` (`goBack`, `isNearTimeout`), input parsing |
| **Integration** | `@SpringBootTest` + `TestRestTemplate`/MockMvc + real H2 (same engine as prod) | USSD webhook end to end; multi-step **state-machine traversal** driving whole sessions through the menu tree; analytics/metrics query correctness; persistence of `SessionLog`/`TransactionLog` |
| **Contract** | `@SpringBootTest` assertions on the raw AT response | `CON `/`END ` prefixing, response length within USSD limits, form-field handling, shortcode chain replay parity with step-by-step input |
| **End-to-End** | Playwright | Browser phone UI: against the live URL (smoke, using the seeded demo accounts) and against local `docker-compose` (comprehensive flows) |
| **Performance** | Locust (primary — Python virtual phones each holding a stateful session) or k6 with custom session state | Thousands of concurrent sessions, 10 000-session cap behaviour, USSD latency SLA, soak/OOM on the 2 GB VPS |
| **Security — SAST** | Spotbugs + find-sec-bugs, GitHub CodeQL | Static defect & vulnerability analysis on the Java source |
| **Security — dependency** | OWASP Dependency-Check (Snyk if a token is available) | Vulnerable transitive dependencies |
| **Security — container** | Trivy (filesystem + built image) | OS/library CVEs in the published Docker image |
| **Security — DAST** | OWASP ZAP baseline | Dynamic scan against https://ussd.jeffgicharu.com |
| **Security — custom USSD suite** | JUnit-driven scenarios | Session hijacking (guessable `sessionId`), cross-session PIN brute force, state-machine fuzzing, unknown-screen handling |
| **Mutation** | PIT (PITest) | Fault-detection strength of unit/integration suites on `engine`, `screen`, `service` classes |
| **Accessibility** | axe-core (via Playwright) | The browser phone UI — the public demo surface |

## Coverage Targets

| Metric | Target |
|---|---|
| Line coverage | ≥ 80% |
| Branch coverage | ≥ 70% |
| PIT mutation score (state-machine & service classes: `engine`, `screen`, `service`) | ≥ 70% |

Coverage is measured by JaCoCo (to be introduced — see `AUDIT.md`; there is
no numeric baseline today). Targets apply to the whole module; the mutation
target is scoped to the highest-risk packages rather than the whole tree.

## CI Quality Gates

Every pull request must pass, before merge:

1. **Build & tests** — `mvn verify -B` (all unit/integration/contract green).
2. **Coverage thresholds** — JaCoCo check fails the build below the line/
   branch targets above.
3. **Mutation threshold** — PIT mutation score ≥ 70% on the scoped packages.
4. **SAST** — Spotbugs/find-sec-bugs and CodeQL with no new High findings.
5. **Dependency & container scans** — OWASP Dependency-Check and Trivy with
   no new Critical/High vulnerabilities.
6. **DAST (scheduled / pre-release)** — ZAP baseline against the live URL,
   reviewed out-of-band so it never flakes the PR gate.

Performance and full E2E suites run on a schedule and pre-release rather
than per-PR, to keep the PR loop fast and deterministic.

## Non-functional Targets

| Target | Value | Rationale |
|---|---|---|
| USSD response latency | p95 < 200 ms | AT gateways enforce tight per-step SLAs; slow steps mean abandoned sessions |
| Concurrent sessions | ≥ 1 000 sustained on the 2 GB VPS without OOM | Demo runs alongside other services on a memory-constrained host |
| Session timeout | enforced at 180 s | AT default; stale sessions must not linger or leak memory |
| PIN lockout | after 3 wrong attempts, 15-minute cooldown | Brute-force resistance for a money flow |
| Max sessions | cap at 10 000 with graceful rejection | Bound in-memory growth deterministically |

## Tooling Inventory

| # | Tool | Layer | Purpose |
|---|---|---|---|
| 1 | Maven | Build | Compile, test, package the fat jar |
| 2 | JUnit 5 | Unit/Integration | Test runner & lifecycle |
| 3 | AssertJ | Unit/Integration | Fluent, readable assertions |
| 4 | Mockito | Unit | Mock the outer edge only (AT response shape) |
| 5 | Spring Boot Test | Integration | `@SpringBootTest` context, MockMvc |
| 6 | TestRestTemplate / MockMvc | Integration/Contract | HTTP-level webhook & API assertions |
| 7 | H2 (in-memory) | Integration | Real persistence layer, identical to prod |
| 8 | JaCoCo | Coverage | Line/branch/instruction measurement & gate |
| 9 | PIT (PITest) | Mutation | Fault-detection strength on core packages |
| 10 | Spotbugs + find-sec-bugs | SAST | Static defect/vulnerability analysis |
| 11 | GitHub CodeQL | SAST | Semantic security analysis, SARIF to Security tab |
| 12 | OWASP Dependency-Check | Security (deps) | Vulnerable dependency detection |
| 13 | Snyk (optional, token-gated) | Security (deps) | Supplementary dependency scanning |
| 14 | Trivy | Security (container) | Filesystem + Docker image CVE scan |
| 15 | OWASP ZAP | DAST | Dynamic scan against the live URL |
| 16 | Custom USSD security suite | Security | Hijacking, PIN brute force, state fuzzing |
| 17 | Playwright | E2E | Browser phone UI, live + local |
| 18 | axe-core | Accessibility | A11y audit of the browser UI |
| 19 | Locust | Performance | Stateful concurrent-session load model |
| 20 | k6 | Performance | Alternative scripted load with session state |
| 21 | GitHub Actions | CI | Orchestrates all gates above |

---
*See also: [QUALITY_DASHBOARD](QUALITY_DASHBOARD.md) · [AUDIT](AUDIT.md) · [TEST_STRATEGY](TEST_STRATEGY.md) · [TEST_PLAN](TEST_PLAN.md) · [QA_BEST_PRACTICES](QA_BEST_PRACTICES.md) · [MUTATION_TESTING](MUTATION_TESTING.md) · [PERFORMANCE_TESTING](PERFORMANCE_TESTING.md) · [SECURITY_TESTING](SECURITY_TESTING.md) · [E2E_VERIFICATION](E2E_VERIFICATION.md) · [AI_TESTING_PLAYBOOK](AI_TESTING_PLAYBOOK.md)*
