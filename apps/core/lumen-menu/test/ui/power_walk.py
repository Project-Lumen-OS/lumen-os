#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Power key walk on an EMULATOR (test APK of test/ui/build.sh with WALK=PowerWalkActivity, never a projector): a
virtual HID remote presses the power key (test/ui/powerhid.py, Android's own repeats) and the family's real power key
code decides; every step CHECKS the decision the code logs, the window in front and Android's wakefulness.

    python3 power_walk.py SERIAL PACKAGE    (adb from $ADB; run_power.sh builds and installs the APKs)

Checks: a fresh install has "Short press of the power key" = Power menu (stock); a short press opens the power menu and
does not sleep; Back closes it; the hold (1.2 s) sleeps; after "Sleep" is chosen a short press sleeps and the hold
opens the power menu; back to Power menu the short press opens the menu again. One PASS/FAIL line per check.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
ACT = "org.lumen.menu.walk.PowerWalkActivity"
FAILS = []
MENU_LOG = re.compile(r"(short press|hold): power menu|remote power key: power menu")
SLEEP_LOG = re.compile(r"(short press|hold): sleep|remote power key: power key|remote power key, .*: power off")


def adb(*a, timeout=60):
    return subprocess.run([ADB, "-s", SERIAL] + list(a), capture_output=True, text=True, timeout=timeout).stdout.replace("\r", "")


def sh(cmd):
    return adb("shell", cmd)


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + detail[:400] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def awake():
    m = re.search(r"mWakefulness=(\w+)", sh("dumpsys power"))
    return m.group(1) if m else "?"


def wake():
    for _ in range(3):
        if awake() == "Awake":
            break
        sh("input keyevent 224")
        time.sleep(2)
    sh("wm dismiss-keyguard")


def front():
    return " ".join(re.findall(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}", sh("dumpsys window")))


def start(extra=""):
    sh("am start -W --activity-single-top -n %s/%s %s" % (PKG, ACT, extra))
    time.sleep(2.5)


def standby():
    return "power.standby=true" in sh("dumpsys activity %s/%s" % (PKG, ACT))


def choice():
    m = re.search(r"power\.choice=(\w+)", sh("dumpsys activity %s/%s" % (PKG, ACT)))
    return m.group(1) if m else "?"


def ready():
    """The walk activity in front and awake before a press (a sleep before it may still be ending)."""
    if standby():                           # the lamp-only standby (display on): one power press wakes, as on a unit
        hid(150)
        time.sleep(3)
        check("a power press ends the lamp-only standby", not standby())
    for _ in range(6):
        if awake() == "Awake" and ACT in front():
            return True
        wake()
        start()
        time.sleep(1)
    return False


def hid(ms):
    j = subprocess.run([sys.executable, os.path.join(HERE, "powerhid.py"), str(ms)], capture_output=True, text=True).stdout
    p = os.path.join(os.environ.get("TMPDIR", "/tmp"), "power_walk_%s.json" % SERIAL)
    open(p, "w").write(j)
    adb("push", p, "/data/local/tmp/power_walk.json")
    sh("hid /data/local/tmp/power_walk.json")


def press(ms):
    """One press held ms, then the decision lines of the power key code and the state 2 s later."""
    check("the walk activity is in front before the press", ready(), front())
    sh("logcat -c")
    hid(ms)
    time.sleep(2.0)
    log = sh("logcat -d -s PowerKeyPress:I X50Proj:I Z9xPowerKey:I Z9xPowerMenu:I Z9xPowerUi:I P6Power:I PowerWalk:* AndroidRuntime:E")
    return log, front(), awake()


def case(what, ms, want):
    log, fr, w = press(ms)
    menu, sleep = bool(MENU_LOG.search(log)), bool(SLEEP_LOG.search(log))
    got = "power down" in log
    lines = " | ".join(l.split(": ", 1)[-1] for l in log.splitlines() if "beginning of" not in l and l.strip())[:300]
    check(what + ": the press reached the power key code", got, lines)
    shown = ACT not in fr and (PKG in fr or "PowerMenu" in fr)
    if want == "menu":
        check(what + ": the power menu opens", menu and not sleep, lines)
        check(what + ": the projector does not sleep", w == "Awake", w)
        check(what + ": the menu window is in front", shown, fr)
        sh("input keyevent 4")
        time.sleep(1.5)
        check(what + ": Back closes the menu", ACT in front(), front())
    else:
        check(what + ": the projector goes to sleep", sleep and not menu, lines)
        check(what + ": no power menu", not shown or w != "Awake", fr)
        time.sleep(4)                       # the sleep's own checks end (busy flag, curtain) before the next press
        wake()
        start()


def main():
    sh("pm clear " + PKG)
    sh("appops set %s SYSTEM_ALERT_WINDOW allow" % PKG)
    sh("settings put system screen_off_timeout 1800000")
    wake()
    start()
    check("fresh install: the setting is Power menu (stock)", choice() == "power_menu", choice())
    case("Power menu, short press", 150, "menu")
    case("Power menu, held 1.2 s", 1200, "sleep")
    start("--es power_short sleep")
    check("set to Sleep through the menu's module setting", choice() == "sleep", choice())
    case("Sleep, short press", 150, "sleep")
    case("Sleep, held 1.2 s", 1200, "menu")
    start("--es power_short power_menu")
    check("set back to Power menu", choice() == "power_menu", choice())
    case("Power menu again, short press", 150, "menu")
    sh("am force-stop " + PKG)
    print("power_walk %s: %s" % (PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
