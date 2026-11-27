#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""What a power menu Power off did, from logcat lines (threadtime, any buffers): "off" when Android's shutdown ran
(ShutdownThread), "called" when only the shared Halt line is there (the call was made, the system did not go down),
"sleep" when the system power key put Android to sleep (an injected KEYCODE_POWER: intercept_power, powerPress,
"Going to sleep due to power_button", the bug of report LR-YVHNX4), "none" otherwise. A sleep line wins.
  python3 poweroff_log.py LOGCAT_FILE      (prints the verdict)"""
import re
import sys

SLEEP = re.compile(r"Going to sleep due to power_button|intercept_power: \[ACTION_DOWN|powerPress: .*interactive=true")
OFF = re.compile(r"ShutdownThread: (Notifying thread to start shutdown|Sending shutdown broadcast|Shutting down|"
                 r"Performing low-level shutdown)")
HALT = re.compile(r"LumenHalt: power off: Android shutdown")
DENIED = re.compile(r"SecurityException.*android\.permission\.REBOOT")


def denied(lines):
    """PowerManagerService.shutdown refused the caller only for the REBOOT permission (a test-signed APK)."""
    return any(DENIED.search(l) for l in lines)


def verdict(lines):
    lines = [l for l in lines if not l.startswith("#")]
    if any(SLEEP.search(l) for l in lines):
        return "sleep"
    if any(OFF.search(l) for l in lines):
        return "off"
    if any(HALT.search(l) for l in lines):
        return "called"
    return "none"


def presses(lines):
    """The '# press' blocks of a fixture, each a list of lines."""
    out = []
    for l in lines:
        if l.startswith("# press"):
            out.append([])
        elif out and not l.startswith("#"):
            out[-1].append(l)
    return out or [lines]


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        print(verdict(f.read().splitlines()))
