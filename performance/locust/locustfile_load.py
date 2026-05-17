"""Load: sustained nominal traffic. 100 users, ramp 0->100 over 1 min,
hold 5 min. LOCAL ONLY. Mixed light journeys on the seeded demo accounts.

    locust -f locustfile_load.py --headless -u 100 -r 2 -t 6m \
        --host http://localhost:8082
"""
import os
import random

from locust import task

import lib.thresholds  # noqa: F401
from lib.phone_numbers import DEMO_ACCOUNTS
from lib.ussd_user import UssdUser


class LoadUser(UssdUser):
    host = os.environ.get("TARGET_HOST", "http://localhost:8082")

    @task(3)
    def check_balance(self):
        phone, pin = random.choice(DEMO_ACCOUNTS)
        self.new_session(phone=phone)
        self.dial(name="01 dial")
        self.step("4", name="02 menu:balance", expect_continue=True)
        self.step(pin, name="03 balance:pin", expect_continue=False)

    @task(2)
    def browse_menu(self):
        phone, _ = random.choice(DEMO_ACCOUNTS)
        self.new_session(phone=phone)
        self.dial(name="01 dial")
        self.step("6", name="04 menu:account", expect_continue=True)
        self.step("1", name="05 account:phone", expect_continue=False)

    @task(1)
    def mini_statement(self):
        phone, pin = random.choice(DEMO_ACCOUNTS)
        self.new_session(phone=phone)
        self.dial(name="01 dial")
        self.step("6", name="04 menu:account", expect_continue=True)
        self.step("5", name="06 account:mini", expect_continue=True)
        self.step(pin, name="07 mini:pin", expect_continue=False)
