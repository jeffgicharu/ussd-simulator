"""Spike: sudden surge. 10 baseline -> 500 for 1 min -> back to 10.
LOCAL ONLY. Uses Locust's LoadTestShape so a single headless run drives
the whole profile.

    locust -f locustfile_spike.py --headless --host http://localhost:8082
"""
import os
import random

from locust import LoadTestShape, task

import lib.thresholds  # noqa: F401
from lib.phone_numbers import DEMO_ACCOUNTS
from lib.ussd_user import UssdUser


class SpikeUser(UssdUser):
    host = os.environ.get("TARGET_HOST", "http://localhost:8082")

    @task
    def quick_balance(self):
        phone, pin = random.choice(DEMO_ACCOUNTS)
        self.new_session(phone=phone)
        self.dial(name="01 dial")
        self.step("4", name="02 menu:balance", expect_continue=True)
        self.step(pin, name="03 balance:pin", expect_continue=False)


class SpikeShape(LoadTestShape):
    """10 users for 60s, spike to 500 for 60s, settle back to 10 for 60s."""
    stages = [
        {"duration": 60, "users": 10, "spawn_rate": 5},
        {"duration": 120, "users": 500, "spawn_rate": 100},
        {"duration": 180, "users": 10, "spawn_rate": 50},
    ]

    def tick(self):
        run_time = self.get_run_time()
        for stage in self.stages:
            if run_time < stage["duration"]:
                return (stage["users"], stage["spawn_rate"])
        return None
