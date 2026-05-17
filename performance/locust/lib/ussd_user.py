"""Stateful USSD virtual-user base class.

A real USSD session is stateful: the gateway sends one input at a time
and the server tracks which screen the caller is on, keyed by sessionId.
The JSON endpoint POST /ussd/api models exactly that — one input per
request, server-side session state — so it is the right surface for
per-VU stateful load (the form callback /ussd/callback is the
Africa's-Talking-compatible cumulative-text variant of the same engine).

Each Locust task should start a fresh logical session with `new_session()`
then walk the menu with `step(...)`. Request names are grouped so Locust
stats aggregate per logical step, not per unique URL.
"""
import uuid

from locust import HttpUser, between

from lib.phone_numbers import unique_phone


class UssdUser(HttpUser):
    abstract = True
    # USSD callers think between key presses; keep it short so a VU
    # represents a realistically brisk session, not a human typing slowly.
    wait_time = between(0.2, 1.0)

    def on_start(self):
        self.phone = unique_phone()
        self.session_id = None

    # ── session helpers ────────────────────────────────────────────────
    def new_session(self, phone: str = None):
        """Begin a new logical USSD session (fresh sessionId)."""
        self.session_id = "perf-" + uuid.uuid4().hex[:16]
        if phone is not None:
            self.phone = phone
        return self

    def step(self, user_input: str, name: str, expect_continue=None):
        """Send one input to the engine; return parsed {message, continueSession}.

        `name` groups the request in Locust stats. If `expect_continue` is
        set, a mismatch (or non-200) marks the sample as a failure so the
        error-rate SLO reflects broken journeys, not just HTTP errors.
        """
        payload = {
            "sessionId": self.session_id,
            "phoneNumber": self.phone,
            "serviceCode": "*384#",
            "input": user_input,
        }
        with self.client.post(
            "/ussd/api", json=payload, name=name, catch_response=True
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"HTTP {resp.status_code}")
                return None
            try:
                data = resp.json()
            except Exception as e:  # noqa: BLE001
                resp.failure(f"bad JSON: {e}")
                return None
            if expect_continue is not None and data.get(
                "continueSession"
            ) != expect_continue:
                resp.failure(
                    f"continueSession={data.get('continueSession')} "
                    f"expected={expect_continue}: {data.get('message')!r}"
                )
                return data
            resp.success()
            return data

    def dial(self, name="01 dial"):
        """Initial dial (empty input) — returns the first screen."""
        return self.step("", name=name)
