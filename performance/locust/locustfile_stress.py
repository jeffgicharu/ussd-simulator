"""Stress: find the breaking point. 0->1000 users over 5 min, hold 5 min.
LOCAL ONLY. Run with SLO_DISABLED=1 — breaching the budget *is* the
finding here; we want to see where RPS plateaus, p99 explodes, or the
10,000-session cap surfaces the graceful 'temporarily unavailable' END.

    SLO_DISABLED=1 locust -f locustfile_stress.py --headless \
        -u 1000 -r 3.33 -t 10m --host http://localhost:8082
"""
import os
import random

from locust import task

import lib.thresholds  # noqa: F401
from lib.phone_numbers import DEMO_ACCOUNTS
from lib.ussd_user import UssdUser


class StressUser(UssdUser):
    host = os.environ.get("TARGET_HOST", "http://localhost:8082")

    @task
    def short_session(self):
        phone, pin = random.choice(DEMO_ACCOUNTS)
        self.new_session(phone=phone)
        # Empty-input dials are not flagged as journey failures here: under
        # the session cap the engine returns a terminal "temporarily
        # unavailable" END, which is correct behaviour, not an error.
        self.dial(name="01 dial")
        self.step("4", name="02 menu:balance")
        self.step(pin, name="03 balance:pin")
