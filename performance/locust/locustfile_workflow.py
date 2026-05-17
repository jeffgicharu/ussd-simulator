"""Workflow: the full canonical journey per iteration. 50 users, 5 min.
LOCAL ONLY.

Each iteration uses a brand-new phone number and walks:
  dial (unregistered) -> create PIN -> confirm -> registered
  -> re-dial -> deposit 5000 -> re-dial -> send 500 to a demo account
  -> re-dial -> check balance.

Registration and every money step ends the session (END), so a new
sessionId is started before each subsequent leg — exactly how a real
handset re-dials *384#.

    locust -f locustfile_workflow.py --headless -u 50 -r 5 -t 5m \
        --host http://localhost:8082
"""
import os

from locust import task

import lib.thresholds  # noqa: F401
from lib.phone_numbers import RECIPIENT_LOCAL, unique_phone
from lib.ussd_user import UssdUser

PIN = "2468"


class WorkflowUser(UssdUser):
    host = os.environ.get("TARGET_HOST", "http://localhost:8082")

    @task
    def full_journey(self):
        phone = unique_phone()

        # 1) Register a new number.
        self.new_session(phone=phone)
        self.dial(name="W1 dial:unregistered")
        self.step(PIN, name="W2 reg:create-pin", expect_continue=True)
        self.step(PIN, name="W3 reg:confirm", expect_continue=False)

        # 2) Re-dial (now registered) and deposit to fund the wallet.
        self.new_session(phone=phone)
        self.dial(name="W4 dial:registered")
        self.step("5", name="W5 menu:deposit", expect_continue=True)
        self.step("5000", name="W6 deposit:amount", expect_continue=True)
        self.step(PIN, name="W7 deposit:pin", expect_continue=False)

        # 3) Re-dial and send money to a seeded demo account.
        self.new_session(phone=phone)
        self.dial(name="W8 dial")
        self.step("1", name="W9 menu:send", expect_continue=True)
        self.step(RECIPIENT_LOCAL, name="W10 send:recipient",
                  expect_continue=True)
        self.step("500", name="W11 send:amount", expect_continue=True)
        self.step(PIN, name="W12 send:pin", expect_continue=False)

        # 4) Re-dial and check the resulting balance.
        self.new_session(phone=phone)
        self.dial(name="W13 dial")
        self.step("4", name="W14 menu:balance", expect_continue=True)
        self.step(PIN, name="W15 balance:pin", expect_continue=False)
