#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Replays the recorded Power off presses (testdata/*_poweroff.txt, first line '# expect: VERDICT'): LR-YVHNX4 is the
X50 bug (the power key: a sleep), the emulator captures are the fixed path (ShutdownThread). Plus the edge cases of the
verdict. Exit 1 on a FAIL."""
import glob
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import poweroff_log as pl  # noqa: E402

FAILS = []


def check(what, ok):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


HERE = os.path.dirname(os.path.abspath(__file__))
files = sorted(glob.glob(os.path.join(HERE, "testdata", "*_poweroff.txt")))
check("recorded Power off presses: the LR-YVHNX4 report and the emulator captures",
      any("lr_yvhnx4" in f for f in files) and any("emu_" in f for f in files))
for f in files:
    lines = open(f, encoding="utf-8").read().splitlines()
    want = lines[0].split(":", 1)[1].strip()
    for i, block in enumerate(pl.presses(lines[1:])):
        check("%s press %d: %s" % (os.path.basename(f), i + 1, want), pl.verdict(block) == want)
lr = open(os.path.join(HERE, "testdata", "lr_yvhnx4_poweroff.txt"), encoding="utf-8").read().splitlines()
check("LR-YVHNX4: three presses, each a sleep and no ShutdownThread",
      len(pl.presses(lr)) == 3 and all(pl.verdict(b) == "sleep" and not any(pl.OFF.search(l) for l in b) for b in pl.presses(lr)))
halt = "11-27 18:00:00.000  1000  2588  3000 I LumenHalt: power off: Android shutdown, userrequested (power menu)"
down = "11-27 18:00:00.100  1000  1237  3001 I ShutdownThread: Notifying thread to start shutdown longPressBehavior=1"
key = "11-27 18:00:00.050  1000  1237  2794 I intercept_power: [ACTION_DOWN,0,1]"
check("only the Halt line: called (the call was made, the system stayed up)", pl.verdict([halt]) == "called")
check("Halt then ShutdownThread: off", pl.verdict([halt, down]) == "off")
check("a power key sleep wins over a later shutdown", pl.verdict([key, halt, down]) == "sleep")
check("nothing: none", pl.verdict(["11-27 18:00:00.000 I ActivityManager: idle"]) == "none")
print("poweroff_log_test: " + ("all green" if not FAILS else "%d FAIL" % len(FAILS)))
sys.exit(1 if FAILS else 0)
