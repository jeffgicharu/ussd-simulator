# Quality & Test Infrastructure — Baseline Audit

Baseline snapshot of `ussd-simulator` as it stands today. This is the
reference point against which later quality work is measured. No code is
changed by this document.

## Stack & versions

| Component | Version | Source |
|---|---|---|
| Java | 17 | `pom.xml` `<java.version>` |
| Spring Boot | 3.2.5 | `spring-boot-starter-parent` |
| Build tool | Maven (system `mvn`, no wrapper committed) | `pom.xml` |
| Spring modules | `web`, `validation`, `data-jpa`, `test` | starters |
| H2 database | 2.2.224 (managed by Boot 3.2.5) | `runtime` scope |
| Lombok | managed by Boot 3.2.5, `optional` | excluded from the fat jar |
| jar / repackage | maven-jar 3.3.0 + spring-boot-maven-plugin 3.2.5 | build plugins |

No JaCoCo, PIT, Spotbugs, OWASP Dependency-Check, or any other quality
plugin is declared in `pom.xml`.

## Architecture

### HTTP surface

| Method | Path | Format | Purpose |
|---|---|---|---|
| POST | `/ussd/callback` | `application/x-www-form-urlencoded` → `text/plain` | Africa's Talking gateway callback (`sessionId`, `phoneNumber`, `serviceCode`, cumulative `text`) returning `CON `/`END ` |
| POST | `/ussd/api` | JSON → JSON | Browser simulator / custom clients (single `input` per call) |
| GET | `/api/metrics` | JSON | Active session count + registered screen count (used as the health probe — there is no Spring Actuator) |
| GET | `/api/analytics/sessions` | JSON | Totals, drop-off by screen, average duration, outcome breakdown |
| GET | `/api/analytics/transactions` | JSON | Transaction volume by type |
| GET | `/api/analytics/customer/{phone}` | JSON | Per-customer session & transaction history |
| GET | `/` | HTML | Static browser phone simulator (`static/index.html`) |
| GET | `/h2-console` | — | H2 web console; **disabled by the `prod` profile** and 404-blocked at nginx |

### State-machine design

A screen-based state machine. `UssdScreen` is a three-method interface
(`getId`, `render`, `handleInput`). Each screen is a Spring `@Component`
(several flows nest multiple `@Component` static classes — e.g.
`SendMoneyScreen` contributes `SEND_MONEY_PHONE`, `SEND_MONEY_AMOUNT`,
`SEND_MONEY_CONFIRM`). `UssdEngine` injects `List<UssdScreen>` and, in a
`@PostConstruct`, builds an id→screen map; `getScreenCount()` reports **28
registered screens** spanning the 7 flows described in the README (send
money, withdraw, deposit, airtime, balance, my account, loans & savings).

Routing: `UssdEngine.process()` (AT, cumulative `text` split on `*`) and
`processStep()` (JSON, single input) resolve the session, route the latest
input to the current screen's `handleInput`, and end the session when a
screen returns a non-continue response. Whole shortcode chains
(`*384*4*1234#`) are replayed input-by-input via `processInputChain`.

### Session management

In-memory `ConcurrentHashMap<String, UssdSession>` in `SessionManager`.
Sessions are keyed by the gateway `sessionId`. `UssdSession` holds the
current screen id, a `Deque` screen history (supports `goBack`), a
free-form `data` map, timestamps, and an `active` flag. Timeout is
**180 s** (`ussd.session-timeout-seconds`, env-overridable); a
`@Scheduled(fixedRate=30000)` task evicts expired/inactive sessions, and
reads also evict-on-access. A hard cap of **10 000** concurrent sessions
(`ussd.max-sessions`) throws `IllegalStateException` once exceeded after a
cleanup pass.

### Registration flow

`UssdEngine` treats any number not present in `WalletService`'s PIN map as
unregistered and routes it to `REGISTER_PIN` → `REGISTER_CONFIRM`
(create PIN → confirm → account opened with zero balance). Mismatched
confirmation ends the session with "Registration cancelled".

### Browser phone UI

`static/index.html` is a self-contained simulator (phone keypad, screen,
request log). It POSTs JSON to `/ussd/api` with a relative path, so it
works unchanged behind the nginx reverse proxy. Defaults: phone
`+254700000001`, service code `*384#`.

### Persistence layer

`jdbc:h2:mem:ussddb;DB_CLOSE_DELAY=-1`, `ddl-auto: create-drop` — purely
in-memory, recreated each start. JPA entities: `SessionLog` (`session_logs`,
indexed on `phoneNumber`, `startedAt`) and `TransactionLog`
(`transaction_logs`, indexed on `phoneNumber`, `transactionType`,
`reference`). **Wallet state itself is not in the database**: balances,
PINs, transaction history, failed-attempt counters and lockout timers live
in `ConcurrentHashMap`s inside `WalletService`, seeded in its constructor
with three demo accounts. Repository autowiring is `required = false`, so
logging degrades gracefully. Every restart returns the system to the seeded
demo state — this is the deliberate daily-reset mechanism.

## Build & run

Verified locally:

- `mvn clean verify -B` → **BUILD SUCCESS**, 23 tests, 0 failures, no
  coverage report produced (no JaCoCo).
- `mvn clean package -B` → repackaged fat jar `target/ussd-simulator-1.0.0.jar`.
- `mvn spring-boot:run` → browser UI at `http://localhost:8181`.
- USSD via curl (note `+` must be URL-encoded in form bodies):
  ```bash
  curl -X POST http://localhost:8181/ussd/callback \
    --data-urlencode "sessionId=s1" \
    --data-urlencode "phoneNumber=+254700000001" \
    --data-urlencode "text=4*1234"      # → END ... balance ...
  ```
- A `Dockerfile` and `docker-compose.yml` are present for containerised runs.

## Live deployment context

- Live at **https://ussd.jeffgicharu.com** (browser phone simulator + USSD
  endpoints, one Spring Boot process behind nginx + Cloudflare).
- A hosted target means later quality work can run real DAST and load
  testing against a production-like surface, not just a local instance.
- Demo accounts (reset to this state daily at **03:30 UTC** via a service
  restart that re-seeds in code):

  | Phone | PIN | Balance |
  |---|---|---|
  | +254700000001 | 1234 | KES 75,000 |
  | +254700000002 | 5678 | KES 12,500 |
  | +254700000003 | 4321 | KES 3,200 |

- In-memory H2, no external database; restart-based reset.

## Existing tests

Two `@SpringBootTest` classes, **23 tests total**, all green:

**`engine/UssdEngineTest` — 12 tests** (drive `UssdEngine.process` with
cumulative AT text chains): initial dial → main menu; navigate to send
money; full send-money happy path; invalid recipient phone rejected;
balance with correct PIN; balance with wrong PIN; airtime to own phone;
My Account phone number; invalid main-menu choice; AT `CON ` formatting;
AT `END ` formatting; single-request shortcode chain.

**`integration/UssdIntegrationTest` — 11 tests** (HTTP level): AT callback
initial dial returns `CON`; AT balance check returns `END`; JSON API dial
returns menu; JSON deposit flow; unregistered number redirected to
registration; full registration flow; `/api/metrics`; session analytics;
transaction analytics; customer history; wrong PIN over HTTP.

Frameworks actually in use: JUnit 5 + Spring Boot Test (MockMvc /
`TestRestTemplate`). **No Mockito, no AssertJ** despite being on the
Boot test classpath; assertions are plain JUnit Jupiter.

### What is tested

Happy-path menu navigation for send/balance/airtime/account, registration
redirect + flow, AT `CON`/`END` prefixing, the JSON and form endpoints,
and the analytics/metrics read endpoints.

### What is NOT tested (gaps)

- **Session lifecycle**: 180 s timeout, scheduled cleanup, max-session cap
  `IllegalStateException`, evict-on-access, `goBack`, `isNearTimeout`.
- **Security logic**: 3-attempt PIN lockout, 15-minute cooldown, attempt
  counter reset on success, locked-account rejection.
- **`WalletService` directly**: tiered fee boundaries (15 tiers),
  insufficient-balance paths, deposit/withdraw/airtime, self-send
  rejection, amount min/max (10 / 500 000) bounds, recipient auto-create.
- **Per-screen units**: `WithdrawScreen`, `DepositScreen`, `AirtimeScreen`,
  `LoansScreen`, `MyAccountScreen`, `BalanceScreen`, `MainMenuScreen`,
  `RegisterScreen` have no isolated tests.
- **Concurrency**: two simultaneous sessions for the same phone number.
- **Contract**: AT response length (USSD ~182-char limit), unknown-screen
  `IllegalStateException`, malformed input chains.
- **Analytics correctness**: drop-off / average-duration / volume queries
  are exercised only for "returns 200", not for computed values.

## Existing CI

`.github/workflows/ci.yml` — triggered on push and PR to `main`:

1. `actions/checkout@v4`
2. `actions/setup-java@v4` (Temurin JDK 17, Maven cache)
3. `mvn verify -B` (compile + the 23 tests)
4. `docker build -t ussd-simulator:<sha> .`

No coverage gate, no static analysis, no dependency/container scanning, no
artifact upload, no scheduled scans, single OS/JDK.

## Coverage baseline

**Unknown / unmeasured.** No JaCoCo (or any coverage engine) is configured,
so `mvn clean verify` produces no line/branch/instruction numbers. The
first quality task must introduce coverage instrumentation before any
percentage target can be asserted. Qualitatively, coverage is concentrated
in happy-path engine routing; whole packages (`screen`, `service`,
`repository`, most of `engine`/`model`) have no direct unit coverage.

## Gap inventory

- **No coverage measurement** — JaCoCo absent; no numeric baseline exists.
- **Zero-test packages** — `com.ussd.screen` (10 source files, ~1 088 LOC),
  `com.ussd.service` (`WalletService`, the money + lockout logic),
  `com.ussd.repository`, and most of `com.ussd.model`/`engine`
  (`UssdSession`, `SessionManager`) have no targeted tests.
- **No mutation testing** — PIT absent; happy-path assertions are not
  validated for fault detection (e.g. fee-tier off-by-one).
- **No performance/load testing** — nothing exercises thousands of
  concurrent stateful sessions, the 10 000-session cap, or USSD-latency
  SLAs on the 2 GB VPS.
- **No DAST** — no automated security scan against the live URL.
- **No SAST beyond compilation** — no Spotbugs/find-sec-bugs, no CodeQL.
- **No dependency or container scanning** — no OWASP Dependency-Check /
  Snyk / Trivy, despite a published Docker image in CI.
- **No USSD-specific security suite** — session hijacking (guessable
  `sessionId`), PIN brute force across sessions, state-machine fuzzing,
  and AT-webhook spoofing are unaddressed.
- **No Playwright/E2E** — the browser phone UI (the public demo surface)
  has no automated end-to-end test.
- **No AT webhook contract test** — `CON`/`END` prefix, response length,
  and field handling are only incidentally checked.
- **No accessibility checks** — the public UI has no axe-core/a11y testing.
- **No analytics/quality dashboard** — no aggregated trend of coverage,
  mutation score, scan findings, or test counts over time.
- **CI hardening gaps** — no quality gates, no caching of test results, no
  matrix, no SARIF/security tab integration.
