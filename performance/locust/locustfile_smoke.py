"""Smoke: 1 user, 30s. Sanity that the USSD engine answers within SLO.
Safe against BOTH the local docker target and the live deployment
(read-only: dial + balance-check on a seeded demo account).

    locust -f locustfile_smoke.py --headless -u 1 -r 1 -t 30s \
        --host http://localhost:8082
"""
import os

from locust import task

import lib.thresholds  # noqa: F401  (registers the SLO gate)
from lib.phone_numbers import DEMO_ACCOUNTS
from lib.ussd_user import UssdUser


class SmokeUser(UssdUser):
    host = os.environ.get("TARGET_HOST", "http://localhost:8082")

    @task
    def dial_and_check_balance(self):
        phone, pin = DEMO_ACCOUNTS[0]
        self.new_session(phone=phone)
        self.dial(name="01 dial")
        self.step("4", name="02 menu:balance", expect_continue=True)
        self.step(pin, name="03 enter pin", expect_continue=False)
