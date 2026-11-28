#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Manual focus idle walk on an EMULATOR (never a user's projector): the manual focus screen stays open without keys.

Decision 2026-11-28 for every family: no idle close (stock FocusAdjust hides 3 s after the last key). The REAL
screen of the app (core ManualFocusActivity for the gmpf families, X50 FocusActivity) is started, then no key is sent:
it must still be the resumed activity at 2, 10, 20 and HOLD (default 31) seconds; then BACK must close it (its own exit
key still works). On the old head (3 s idle close) the 10 s check is red.

  python3 apps/core/lumen-menu/test/ui/focus_idle_walk.py SERIAL PACKAGE ACTIVITY [HOLD]   (run_focus_idle.sh builds it)

One PASS/FAIL line per check, exit 1 on any failure.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")


def sh(serial, *cmd):
    return subprocess.run([ADB, "-s", serial, "shell"] + list(cmd), capture_output=True, text=True, timeout=60).stdout


def resumed(serial):
    """The component of the resumed activity (topResumedActivity), or ''."""
    out = sh(serial, "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=ActivityRecord\{\S+ \S+ (\S+)", out) or \
        re.search(r"mResumedActivity: ActivityRecord\{\S+ \S+ (\S+)", out)
    return m.group(1) if m else ""


def same(top, pkg, act):
    """True when top names exactly PACKAGE/ACTIVITY (dumpsys writes pkg/.Rel for a class inside the package): a suffix
    test let the X50 FocusActivity check pass on the core ManualFocusActivity of another app."""
    p, _, a = top.partition("/")
    return p == pkg and (pkg + a if a.startswith(".") else a) == act


def main(serial, pkg, act, hold=31):
    bad = 0
    comp = pkg + "/" + act
    short = act.rsplit(".", 1)[-1]

    def check(name, cond, info=""):
        nonlocal bad
        print("%s %s%s" % ("PASS" if cond else "FAIL", name, (" (%s)" % info) if info else ""))
        bad += 0 if cond else 1

    sh(serial, "input", "keyevent", "KEYCODE_WAKEUP")
    sh(serial, "am", "force-stop", pkg)
    time.sleep(1)
    sh(serial, "am", "start", "-W", "-n", comp)
    t0 = time.monotonic()
    for at in (2, 10, 20, hold):
        time.sleep(max(0.0, t0 + at - time.monotonic()))
        top = resumed(serial)
        check("%s still open %d s after the last key" % (short, at), same(top, pkg, act), top)
    sh(serial, "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    top = resumed(serial)
    check("%s closed by BACK" % short, not same(top, pkg, act), top)
    print("focus idle walk %s: %s" % (short, "all green" if not bad else "%d RED" % bad))
    return 1 if bad else 0


if __name__ == "__main__":
    a = sys.argv[1:]
    sys.exit(main(a[0], a[1], a[2], int(a[3]) if len(a) > 3 else 31))
