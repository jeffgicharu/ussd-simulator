"""Unique-per-virtual-user phone-number generation.

The app keys all wallet state by phone number and treats any unseen
number as unregistered. Each VU that self-registers needs its own number
that will not collide with another VU (or with the three seeded demo
accounts +2547000000 0{1,2,3}). A random run-prefix keeps numbers unique
across repeated runs against the same long-lived container.
"""
import itertools
import random
import threading

# Seeded demo accounts (registered in code on every app start).
DEMO_ACCOUNTS = [
    ("+254700000001", "1234"),
    ("+254700000002", "5678"),
    ("+254700000003", "4321"),
]
# A stable recipient for send-money load (entered as a local 07… number).
RECIPIENT_LOCAL = "0700000002"

_lock = threading.Lock()
_counter = itertools.count(1)
# 3-digit run prefix (100-899) so generated numbers never look like the
# 0000000X demo block and differ between runs.
_RUN_PREFIX = random.randint(100, 899)


def unique_phone() -> str:
    """Return a fresh +2547######### number unique within this process."""
    with _lock:
        n = next(_counter)
    # +2547 <run prefix 3d> <counter 6d>  -> 13 chars after +, passes the
    # send-money validator (^\+?[0-9]{10,15}$) if ever used as a target.
    return f"+2547{_RUN_PREFIX:03d}{n:06d}"
