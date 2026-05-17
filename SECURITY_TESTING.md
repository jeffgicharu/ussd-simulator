# Security Testing

How `ussd-simulator` is tested for security: a layered SAST + dependency
+ container + DAST stack plus a USSD-specific test suite, with
find-AND-fix for HIGH findings.

## Threat model

Assets and the threats against them, with the control and where it is
verified.

| Asset | Threat | Mitigation | Where covered |
|---|---|---|---|
| Money (balances, transfers) | Unauthorised transfer / draining | 4-digit PIN on every money op; tiered + per-tx limits; KES 300k/day cap | `WalletServiceMutationTest`, money integration tests |
| PINs | Brute force | 3-attempt lockout + 15-min cooldown, enforced cross-session and under concurrency | `PinSecurity.bruteForce_*` |
| PINs | Disclosure via logs / responses | `LogSanitizer.maskInput` (digit-run masking) + control-char stripping; PIN never echoed | `PinSecurity.pin_notLogged`, `pin_notEchoedInResponse`; Spotbugs `CRLF_INJECTION_LOGS` |
| Sessions | Hijacking / fixation | Session bound to its phone number; mismatched phone+sessionId rejected without touching the victim | `SessionSecurity.sessionHijack_rejected`, `CrossUserIsolation.cannotOperateOnOthersAccount` |
| Sessions | Replay | No cross-session idempotency needed — terminal step on a consumed session lands on a fresh menu; replay requires the PIN and is bounded by the daily cap | `SessionSecurity.sessionReplay_noEffect` |
| Sessions | Resource exhaustion | 10k cap → graceful `END` (not a crash); short-lived sessions | session-cap tests (PR #11) |
| State machine | Skip-state / forged state | Server-side `currentScreenId`; engine always starts at `MAIN_MENU`; fuzzing | `InputHandling.skipState_*`, `fuzz_noServerErrors` |
| Input surface | Injection (SQL / script / JNDI / oversized) | In-memory maps (no SQL on money path); JPA logs use parameterised queries; bounded log fields | `InputHandling.sqlInjection_safe`, `scriptPayloads_inert`, `veryLongInput_bounded` |
| Audit log | Log forging / sensitive payloads | CR/LF + control stripped, secrets masked, bounded length | `LogSanitizer`, Spotbugs find-sec-bugs taint analysis |
| AT webhook integrity | Forged gateway requests | No shared secret in this demo (no AT integration in prod) — accepted, scoped (see Known low-severity findings) | `SensitiveData.webhookHasNoSignature_documented` |
| Customer analytics | Broken access control (IDOR) | Tracked, deferred — issue #17 | documented below |

## Tooling overview

| Layer | Tool | What it catches |
|---|---|---|
| SAST | Spotbugs + find-sec-bugs (Maven, `verify`) | Injection, crypto, log forging, taint-flow bugs |
| SAST | GitHub CodeQL (`security-extended`) | Semantic vulnerability queries, SARIF to Security tab |
| Dependency | OWASP Dependency Check (always-on gate) | Vulnerable dependencies, `failBuildOnCVSS=7` |
| Dependency | Snyk (optional, token-gated) | Vulnerable deps + license, `--severity-threshold=high` |
| Filesystem | Trivy `fs` | Secrets, IaC/misconfig, repo deps |
| Container | Trivy `image` | OS + library CVEs in the runnable image |
| DAST | OWASP ZAP | Live passive baseline (weekly); active only on demand |
| Custom | `SecurityIntegrationTest` (Spring Boot + H2) | USSD-specific: hijack, brute force, fuzz, isolation |

## Where each tool runs

| Tool | Per PR | Scheduled | Manual |
|---|---|---|---|
| Spotbugs + find-sec-bugs | ✅ `mvn verify` | — | `mvn verify` |
| CodeQL | ✅ | Mondays 03:00 | — |
| Trivy fs + image | ✅ | Daily 03:00 | — |
| OWASP Dependency Check | ✅ | Wednesdays 03:00 | — |
| Snyk | ✅ (no-op w/o token) | Tuesdays 03:00 | dispatch |
| Custom suite | ✅ `mvn verify` | — | `mvn verify` |
| ZAP baseline (passive) | ❌ | Sundays 04:30 | dispatch (baseline) |
| ZAP active | ❌ never | ❌ never | dispatch (`scan_type=api`) only |

## Required manual setup (token-gated tools)

Two scanners need a repository secret and **gracefully skip** (notice +
exit 0, never a false pass, never a PR block) until it is added:

| Secret | Tool | Why | Where to get it |
|---|---|---|---|
| `NVD_API_KEY` | OWASP Dependency Check | The NVD rejects keyless pulls (HTTP 403); without it the data cannot download | https://nvd.nist.gov/developers/request-an-api-key (free) |
| `SNYK_TOKEN` | Snyk | Snyk API auth | https://app.snyk.io/account |

Add via *Settings → Secrets and variables → Actions*. Once present the
respective gate activates automatically (OWASP is then a hard CVSS≥7
gate; OWASP Dependency Check remains the always-on gate the moment its
key exists).

## How to read findings

- **SARIF tools (CodeQL, Trivy)** → GitHub repo **Security → Code
  scanning alerts**, grouped by `category` (`trivy-fs`, `trivy-image`,
  `/language:java`).
- **Spotbugs** → fails `mvn verify` locally and in CI with the bug
  pattern + class:line; HTML at `target/spotbugs*`.
- **OWASP Dependency Check** → build fails on CVSS ≥ 7; full report
  uploaded as the `owasp-dependency-check-report` artifact.
- **Snyk** → the Snyk dashboard (if `SNYK_TOKEN` is configured) + the
  workflow log.
- **ZAP** → HTML/JSON artifacts on the workflow run; HIGH+ findings open
  a tracking issue automatically.

## Triage SLOs

| Severity | Acknowledge | Resolve |
|---|---|---|
| CRITICAL | 24 hours | 72 hours |
| HIGH | 48 hours | 7 days |
| MEDIUM | 5 days | 30 days |
| LOW | best effort | documented, batched |

## Local manual ZAP run

```bash
docker run --network=host --rm -v $(pwd)/security/zap:/zap/wrk/:rw \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t https://ussd.jeffgicharu.com \
  -r ussd-baseline-report.html -d 5
```

`-d 5` rate-limits to 5 req/s to protect the shared live host. For an
active scan locally swap `zap-baseline.py` for `zap-full-scan.py` — only
ever run that deliberately and out of demo hours.

## DAST safety guard

The live demo runs on a shared, memory-constrained VPS, so DAST is
deliberately constrained:

- The **weekly schedule only runs the passive baseline** scan (no attack
  payloads), rate-limited to 5 req/s.
- The **active scan runs only from a manual `workflow_dispatch` with
  `scan_type=api`**. The active job's `if:` is hard-pinned to
  `github.event_name == 'workflow_dispatch' && inputs.scan_type == 'api'`,
  so the scheduled trigger can **never** start it — even if the input
  default were changed, a `schedule` event fails that condition.
- Both modes are rate-limited (`-d 5`) and upload reports as artifacts;
  HIGH+ findings open a tracking issue.

## OWASP Top 10 (2021) coverage

| Category | Covered by |
|---|---|
| A01 Broken Access Control | Session↔phone binding (hijack tests); cross-user isolation tests; analytics IDOR tracked → #17 |
| A02 Cryptographic Failures | No secrets/keys in repo (Trivy fs secret scan); PINs never logged |
| A03 Injection | `InputHandling` SQL/script/JNDI tests; find-sec-bugs taint; in-memory (no SQL on money path), parameterised JPA |
| A04 Insecure Design | Threat model above; state machine cannot be skipped (`skipState_*`) |
| A05 Security Misconfiguration | Trivy fs misconfig; `prod` profile disables H2 console / error details; no stack-trace leak test |
| A06 Vulnerable & Outdated Components | OWASP Dependency Check (CVSS≥7 gate), Trivy image, Snyk |
| A07 Identification & Auth Failures | PIN lockout/cooldown, brute-force + concurrency tests, change-PIN regression |
| A08 Software & Data Integrity | CodeQL; pinned action/plugin versions; container scan of the built artifact |
| A09 Logging & Monitoring Failures | `LogSanitizer` (anti-forging + secret masking), `pin_notLogged`, audit-log threat row |
| A10 SSRF | No outbound user-controlled requests (the optional wallet-API integration is disabled by default); noted, low exposure |

## Findings handled this round

| Finding | Severity | Disposition |
|---|---|---|
| No session↔phone binding → session hijacking/fixation | HIGH | **Fixed inline** — engine rejects phone/sessionId mismatch; regressions `sessionHijack_rejected`, `cannotOperateOnOthersAccount` |
| Untrusted input (incl. PIN-bearing AT `text`) logged unsanitised → log forging (CWE-117) + PIN in logs (CWE-532); 7× Spotbugs `CRLF_INJECTION_LOGS` | HIGH | **Fixed inline** — `LogSanitizer` (control-char strip + digit masking) at all user-input log sites; find-sec-bugs taint config validates the fix; regression `pin_notLogged` |
| Dead store in `WalletService.deposit` | LOW | Fixed inline (removed) |
| Unauthenticated analytics API exposes any customer's history (IDOR) | MEDIUM | Deferred → issue #17 (intentional demo admin surface; adding an auth layer is out of scope for the webhook-hardening pass) |

## Known low-severity findings

- **No Africa's Talking webhook signature/IP allow-listing.** The AT
  gateway is not integrated in this demo deployment and there is no
  shared secret to verify; the webhook is intentionally open so the
  browser simulator works. Accepted and scoped — if a real AT account is
  ever wired up, add signature verification + source IP allow-listing.
  Not filed as an issue (documented here to avoid issue noise).
- **`EI_EXPOSE_REP`/`EI_EXPOSE_REP2` (Spotbugs, 15×).** Lombok accessors
  on the in-memory session model and Spring-injected `final` collaborator
  fields. Framework DI / single-process simulator data holders, not a
  trust boundary. Excluded with justification in `spotbugs-exclude.xml`.
- **`VA_FORMAT_STRING_USES_NEWLINE` (Spotbugs, 18×).** USSD requires a
  literal `\n` line separator; `%n` would emit a platform separator and
  corrupt rendering. Deliberate; excluded with justification.
