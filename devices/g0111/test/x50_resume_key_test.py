#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Replay of an X50 STR wake (a tester's G0111, x50-1.0.10-beta16, 2026-11-26 22:16-22:17, fixtures/x50_resume_key_1126.txt).

The chain the log shows: the power menu puts the projector to sleep, the kernel suspends, a short press of the BT remote
resumes it. The remote's power key (000d:3840 scancode 113, STB_POWER WAKE) wakes Android at once; 7.3 s later the
vendor power HAL ends its resume (gmpf wakeUp waits for DisplayResumeThread, the light comes on) and writes one power
key into the IR receiver (mediatek.hardware.power-service apm_thread_func: APM_NORMAL_RESUME -> gmpf_resume ->
sys.xgimi.str -> sendPowerKeyToAndroid, every resume; the event it writes, DAT_00104300: EV_MSC SCAN 255, EV_KEY 116
down, SYN, EV_KEY 116 up, SYN, to the input device named "MTK Smart TV IR Receiver" = /dev/input/event0 =
Vendor_3697_Product_0001.kl). Android was already awake, so PhoneWindowManager took it as a short press
(mShortPressOnPowerBehavior=1) and slept again: laser on for a second, then off, on every wake.

Stock does not sleep there: its PhoneWindowManager.interceptPowerKeyDown says "STR resume, always wake up" while
XgimiWindowManager.mSystemActive is false (set by xgimiGotoSleep, cleared only by checkWakeUpStatus after the wake),
and xgimiGotoSleep refuses while it is false (services.jar 1.3.38 PhoneWindowManager.java:643-651,
XgimiWindowManager.java:318-325, 452-459). On stock that key is the wake itself (the BT KPPOWER line has no WAKE flag).

This test takes the image's key layouts as the image recipe builds them (overlay/v1/keylayout + x50_power_keys.py
apply) and the framework value of apps/X50PowerOverlay, and replays the log's key events through the framework rules
that decide them (Lineage 21 PhoneWindowManager: POWER while awake = config_shortPressOnPowerBehavior, while asleep =
wake; WAKEUP wakes and does nothing while awake; STB_POWER = the global key of org.lumen.x50.projector). The vendor's
resume key must never put an awake projector to sleep; every user power key must keep what it does today.
"""
import os
import re
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "devices", "g0111", "image"))
import x50_power_keys  # noqa: E402

LOG = os.path.join(HERE, "fixtures", "x50_resume_key_1126.txt")
OVERLAY = os.path.join(ROOT, "apps", "X50PowerOverlay", "res", "values", "config.xml")
IR = "Vendor_3697_Product_0001.kl"      # "MTK Smart TV IR Receiver" (dumpsys input KeyLayoutFile, live 2026-11-26)
VENDOR_RESUME_SCANCODE = 116            # KEY_POWER in the power HAL's event (DAT_00104300)
BT_REMOTE = ("Vendor_000d_Product_3840.kl", 113)
SHORT = {0: "nothing", 1: "sleep"}      # PhoneWindowManager SHORT_PRESS_POWER_NOTHING / _GO_TO_SLEEP

fails = []


def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        fails.append(name)


def layouts(base):
    t = tempfile.mkdtemp(prefix="x50rk")
    kl = os.path.join(t, "kl")
    shutil.copytree(base, kl, ignore=shutil.ignore_patterns("SHA256SUMS"))
    bad = x50_power_keys.apply(kl)
    check("x50_power_keys apply on %s has no errors %s" % (os.path.relpath(base, ROOT), bad), not bad)
    out = {}
    for n in os.listdir(kl):
        if n.endswith(".kl"):
            for line in open(os.path.join(kl, n), encoding="utf-8"):
                m = re.match(r"^key[ \t]+(\d+)[ \t]+(\S+)((?:[ \t]+[A-Z_]+)*)", line.split("#", 1)[0])
                if m:
                    out[(n, int(m.group(1)))] = (m.group(2), m.group(3).split())
    shutil.rmtree(t)
    return out


def short_press_behavior():
    m = re.search(r'<integer name="config_shortPressOnPowerBehavior">(\d+)</integer>', open(OVERLAY, encoding="utf-8").read())
    return int(m.group(1))


def framework(key, flags, interactive, short):
    """What the framework does with one press of this key (Lineage 21 PhoneWindowManager, the rules this test needs)."""
    if key == "STB_POWER":
        return "app" if interactive or "WAKE" not in flags else "wake"
    if key == "POWER":
        return SHORT.get(short, "other") if interactive else "wake"
    if key == "WAKEUP":
        return "nothing" if interactive else "wake"
    return "nothing"


def replay(kl, short):
    """The log's events in order: the remote press after the resume, then the vendor's resume key."""
    lines = open(LOG, encoding="utf-8").read().splitlines()
    i_resume = next(i for i, l in enumerate(lines) if "APM_NORMAL_RESUME" in l)
    i_wake = next(i for i, l in enumerate(lines) if i > i_resume and "Waking up from Asleep" in l)
    i_vendor = next(i for i, l in enumerate(lines) if i > i_wake and "sendPowerKeyToAndroid write event to /dev/input/event0" in l)
    i_slept = [i for i, l in enumerate(lines) if i > i_vendor and "Going to sleep due to power_button" in l]
    check("fixture: the captured run slept on the vendor's resume key (the bug the replay reproduces)", bool(i_slept))
    interactive = False
    bt = kl[BT_REMOTE]
    a = framework(bt[0], bt[1], interactive, short)
    check("the BT remote press after the resume wakes Android (fixture line %d)" % (i_wake + 1), a == "wake")
    interactive = a == "wake"
    v = kl[(IR, VENDOR_RESUME_SCANCODE)]
    a = framework(v[0], v[1], interactive, short)
    print("  vendor resume key: IR %d -> %s %s, Android awake -> %s" % (VENDOR_RESUME_SCANCODE, v[0], " ".join(v[1]), a))
    check("the vendor's resume key (IR %d, fixture line %d) does nothing to the awake projector (no sleep, no power key press)"
          % (VENDOR_RESUME_SCANCODE, i_vendor + 1), a == "nothing")
    a = framework(v[0], v[1], False, short)
    check("the vendor's resume key still wakes an Android that is asleep (stock order: it is the wake)", a == "wake")


def user_keys(kl, short):
    """Every user power key does what it did before the fix (sleep or the app's power key rule while awake)."""
    want = {
        (IR, 190): "sleep", (IR, 75): "sleep",
        ("Vendor_3697_Product_0002.kl", 30): "sleep", ("Vendor_3697_Product_0002.kl", 116): "sleep",
        ("Vendor_3697_Product_0002.kl", 260): "sleep", ("Vendor_1d5a_Product_c081.kl", 116): "sleep",
        ("Generic.kl", 142): "sleep", BT_REMOTE: "app", ("Vendor_000d_Product_3840.kl", 185): "app",
    }
    for k, w in sorted(want.items()):
        key, flags = kl[k]
        check("user key %s %d while awake -> %s" % (k[0], k[1], w), framework(key, flags, True, short) == w)
        check("user key %s %d while asleep -> wake" % (k[0], k[1]), framework(key, flags, False, short) == "wake")


# the image's base (the published 1.0.9 layouts the recipe edits) and the tree's later base
BASES = (os.path.join(ROOT, "devices", "g0111", "test", "base109_keylayout"), os.path.join(ROOT, "overlay", "v1", "keylayout"))


def main():
    short = short_press_behavior()
    check("X50PowerOverlay keeps the short press = sleep (Standby injects KEYCODE_POWER, stock shutDownsendKeyCode)", short == 1)
    for base in BASES:
        print("== " + os.path.relpath(base, ROOT))
        kl = layouts(base)
        replay(kl, short)
        user_keys(kl, short)
    print("x50_resume_key_test: %s" % ("GREEN" if not fails else "RED (%d)" % len(fails)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
