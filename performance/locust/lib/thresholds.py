"""SLO gate.

Importing this module registers a `quitting` listener that fails the
Locust process (non-zero exit) when the run violates the budgets from
TEST_STRATEGY.md:

    * p95 latency  < 200 ms   (per request, aggregated + per endpoint)
    * error rate   < 0.1 %

Modes (env vars):
  * `SLO_DISABLED=1` — stress / break-point runs: budget breach is the
    *expected* finding, so report only, never fail.
  * `LIVE_SMOKE=1`   — runs against the live, Cloudflare-fronted host:
    the p95 budget is an *origin/app* SLO and cannot account for ~300 ms
    of Cloudflare + TLS + geographic transit, so latency is report-only
    while availability/correctness (error rate, requests made) stays a
    hard gate. The local smoke (the CI gate) keeps the full budget.
"""
import os

from locust import events

P95_BUDGET_MS = 200
ERROR_RATE_BUDGET = 0.001  # 0.1 %


@events.quitting.add_listener
def _assert_slos(environment, **_kw):
    if os.environ.get("SLO_DISABLED") == "1":
        print("[SLO] disabled for this run (stress/break-point) — "
              "reporting only")
        return

    live = os.environ.get("LIVE_SMOKE") == "1"
    stats = environment.stats
    total = stats.total
    failures = total.num_failures
    requests = total.num_requests
    err_rate = (failures / requests) if requests else 0.0
    p95 = total.get_response_time_percentile(0.95)

    problems = []
    if requests == 0:
        problems.append("no requests were made")
    if err_rate >= ERROR_RATE_BUDGET:
        problems.append(
            f"error rate {err_rate:.4%} >= {ERROR_RATE_BUDGET:.2%} "
            f"({failures}/{requests})"
        )

    if live:
        # Latency is report-only against the Cloudflare-fronted host.
        print(f"[SLO] LIVE_SMOKE: aggregate p95={p95} ms "
              f"(origin budget {P95_BUDGET_MS} ms is report-only here; "
              f"the gap is Cloudflare + TLS + geographic transit, not the "
              f"app — see PERFORMANCE_TESTING.md)")
    else:
        if p95 is not None and p95 >= P95_BUDGET_MS:
            problems.append(f"aggregate p95 {p95} ms >= {P95_BUDGET_MS} ms")
        for name, entry in stats.entries.items():
            ep95 = entry.get_response_time_percentile(0.95)
            if ep95 is not None and ep95 >= P95_BUDGET_MS:
                problems.append(
                    f"{name[0]} p95 {ep95} ms >= {P95_BUDGET_MS} ms")

    if problems:
        print("[SLO] FAIL:")
        for p in problems:
            print("   - " + p)
        environment.process_exit_code = 1
    else:
        print(
            f"[SLO] OK: p95={p95} ms, error_rate={err_rate:.4%}, "
            f"requests={requests}"
        )
        environment.process_exit_code = 0
