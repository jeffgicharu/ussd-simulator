# Performance Testing

## What and why

USSD is stateful and latency-sensitive: the Africa's Talking gateway
holds a 180-second session, sends one input per request, and abandons the
session if a step is slow. `TEST_STRATEGY.md` sets a hard budget of
**p95 < 200 ms per request** and **error rate < 0.1 %**. We use
[Locust](https://locust.io) because each virtual user is a real Python
object that keeps session state across many HTTP calls — the natural fit
for "thousands of virtual phones each walking a menu tree" (k6 models
this awkwardly).

## Test types and goals

| Type | Profile | Goal | Target |
|---|---|---|---|
| smoke | 1 user, 30 s | Sanity + SLO on a read-only journey | local **and** live |
| load | 0→100 over 1 min, hold 5 min | Nominal traffic stays within SLO | local |
| stress | 0→1000 over 5 min, hold 5 min | Find the break/saturation point | local |
| spike | 10 → 500 (1 min) → 10 | Survive a sudden surge | local |
| workflow | 50 users, 5 min | Full canonical journey per iteration | local |

Workflow journey per iteration (a fresh phone each time): dial
(unregistered) → create PIN → confirm → re-dial (registered) → deposit
5 000 → re-dial → send 500 to a demo account → re-dial → check balance.
Every leg asserts the expected `continueSession`, so a broken journey
counts as an error, not just an HTTP failure.

## Performance budgets (from TEST_STRATEGY.md)

| Budget | Value | Enforced by |
|---|---|---|
| Request latency | p95 < 200 ms (aggregate + per endpoint) | `lib/thresholds.py` SLO gate (exit code) |
| Error rate | < 0.1 % | `lib/thresholds.py` |
| Concurrent sessions | ≥ 1 000 sustained without OOM | stress / workflow |
| Session cap | graceful `END` at 10 000 (PR #11) | engine; not hit by short sessions |

The gate is **report-only** for two cases: `SLO_DISABLED=1` (stress —
breaching is the expected finding) and `LIVE_SMOKE=1` (the live host is
Cloudflare-fronted; the p95 budget is an *origin* SLO and cannot absorb
~300 ms of edge + TLS + transit, so latency is reported while
availability/correctness stays a hard gate).

## How to run locally

```bash
# 1. Build + start the production-like target (prod profile, port 8082,
#    192 MB heap / SerialGC — matches the live JVM tuning).
docker compose -f docker-compose.perf.yml up -d --build

# 2. Verify the target + demo accounts.
performance/seed-perf-data.sh http://localhost:8082

# 3. Run a scenario (examples).
locust -f performance/locust/locustfile_smoke.py    --headless -u 1   -r 1    -t 30s --host http://localhost:8082
locust -f performance/locust/locustfile_load.py     --headless -u 100 -r 2    -t 6m  --host http://localhost:8082
SLO_DISABLED=1 \
locust -f performance/locust/locustfile_stress.py   --headless -u 1000 -r 3.33 -t 10m --host http://localhost:8082
locust -f performance/locust/locustfile_spike.py    --headless                       --host http://localhost:8082
locust -f performance/locust/locustfile_workflow.py --headless -u 50  -r 5    -t 5m  --host http://localhost:8082

# Live smoke (read-only, Cloudflare-fronted):
LIVE_SMOKE=1 \
locust -f performance/locust/locustfile_smoke.py --headless -u 1 -r 1 -t 30s --host https://ussd.jeffgicharu.com
```

`docker compose -f docker-compose.perf.yml down` to tear down.

## Baseline results (before any tuning)

Local target: docker, prod profile, `-Xmx192m -Xms96m -XX:+UseSerialGC`.

| Test | Peak users | Requests | RPS | Error rate | p50 | p95 | p99 | p99.9 / max | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| smoke (local) | 1 | 138 | 4.7 | 0.00% | 9 ms | **18 ms** | 26 ms | 200 ms | ✅ SLO green |
| load | 100 | 155 825 | 433 | 0.00% | 12 ms | **92 ms** | 170 ms | 270 / 450 ms | ✅ SLO green |
| workflow | 50 | 138 363 | ~460 | 0.00% | 49 ms | **160 ms** | 280 ms | 780 / 3400 ms | ✅ SLO green |
| spike | 10→500→10 | 36 180 | — | 0.00% | 670 ms | 1400 ms | 1700 ms | 2000 / 2300 ms | ⚠️ surge saturates (0 errors) |
| stress | 1000 | 329 803 | ~545 | 0.00% | 940 ms | 2300 ms | 3300 ms | 4100 / 6100 ms | ⚠️ saturates (0 errors) |
| smoke (live) | 1 | 66 | 2.2 | 0.00% | 240 ms | 350 ms | — | 1000 ms | ✅ available; latency = Cloudflare |

Key reading: **nominal load (100 VUs) and the full 15-step workflow
(50 VUs) are both fully within SLO with zero errors.** Saturation appears
only at 5–10× nominal (spike/stress), and even there the app stays
**correct and error-free** — it degrades gracefully (latency rises, no
failures, no crash, no OOM on the 192 MB heap). The 10 000-session cap is
never reached because sessions are short-lived.

## Smoke against the live deployment + Cloudflare overhead

`https://ussd.jeffgicharu.com` smoke: 66 requests, **0 errors**, every
dial/menu/PIN step functionally correct. Latency p50 ≈ 240 ms,
p95 ≈ 350 ms.

The origin processes the identical journey in **p50 ≈ 9 ms / p95 ≈ 18 ms**
(local smoke). The delta — **≈ 230 ms p50, ≈ 330 ms p95** — is entirely
**Cloudflare edge + TLS handshake + client↔edge↔Germany-origin transit**,
not application time. This is why the live smoke gates on
availability/correctness only and treats latency as report-only; the
200 ms budget is an origin/app SLO and is comfortably met at the origin.

## Top 3 bottlenecks identified (from the stress run)

| # | Bottleneck | Evidence | Severity | Disposition |
|---|---|---|---|---|
| 1 | Synchronous request-path logging caps throughput | RPS plateaus ~545 from 100→1000 VUs; ~4 synchronous stdout log writes per logical step | MEDIUM | Deferred → issue #13 |
| 2 | SerialGC stop-the-world on the 192 MB heap | p99.9 ≈ 3.3 s / max ≈ 6.1 s vs p50 ≈ 0.9 s — clustered multi-second tail | MEDIUM | Deferred → issue #14 |
| 3 | Tomcat worker-pool ceiling (default 200) | RPS knee ~500-600 VUs; latency then grows ≈ linearly with VUs (queue-wait), 0 errors | MEDIUM | Deferred → issue #15 |

## Fixes applied in this step

**None.** The step's find-and-fix mandate targets **HIGH-impact**
bottlenecks, defined as: latency exceeding SLO **under nominal load**, OR
error rate > 0.1 % under nominal load, OR thread/connection exhaustion at
session counts well below the 10 000 cap. Measured against that bar:

- Nominal load (100 VUs): p95 92 ms, **0 errors** — SLO green.
- Full workflow (50 VUs): p95 160 ms, **0 errors** — SLO green.
- No errors in any run; no exhaustion below the session cap.

No bottleneck meets the HIGH bar, so per the step's severity policy the
three saturation findings (which manifest only at 5–10× nominal, with
graceful, error-free degradation, and whose fixes are GC/thread/heap
capacity-planning on a memory-constrained 2 GB shared host) are filed as
deferred `performance` issues (#13, #14, #15) rather than fixed inline.
This is the documented justification for deferral required by the step.

## CI strategy

- **Per PR — `Performance Smoke`**: builds the docker target, runs
  `locustfile_smoke.py` (1 user, 30 s) against `http://localhost:8082`,
  fails the PR if the SLO gate trips. ~2-3 min including build.
- **Nightly (cron 04:00 UTC) — `Performance Full Suite`**: load + spike
  + workflow + stress against the docker target; uploads every Locust
  HTML report as an artifact. Not a PR gate (multi-tens-of-minutes).

Both jobs are in `.github/workflows/performance.yml`.

---
*See also: [QUALITY_DASHBOARD](QUALITY_DASHBOARD.md) · [AUDIT](AUDIT.md) · [TEST_STRATEGY](TEST_STRATEGY.md) · [TEST_PLAN](TEST_PLAN.md) · [QA_BEST_PRACTICES](QA_BEST_PRACTICES.md) · [MUTATION_TESTING](MUTATION_TESTING.md) · [PERFORMANCE_TESTING](PERFORMANCE_TESTING.md) · [SECURITY_TESTING](SECURITY_TESTING.md) · [E2E_VERIFICATION](E2E_VERIFICATION.md) · [AI_TESTING_PLAYBOOK](AI_TESTING_PLAYBOOK.md)*
