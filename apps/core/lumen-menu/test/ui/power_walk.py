#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Power key walk on an EMULATOR (test APK of test/ui/build.sh with WALK=PowerWalkActivity, never a projector): a
virtual HID remote presses the power key (test/ui/powerhid.py, Android's own repeats) and the family's real power key
code decides; every step CHECKS the decision the code logs, the window in front and Android's wakefulness.

    python3 power_walk.py SERIAL PACKAGE    (adb from $ADB; run_power.sh builds and installs the APKs)

Checks: a fresh install has "Short press of the power key" = Power menu (stock); a short press opens the power menu and
does not sleep; Back closes it; the hold (1.2 s) sleeps; after "Sleep" is chosen a short press sleeps and the hold
opens the power menu; back to Power menu the short press opens the menu again. The menu as stock (menu_rules): five
tiles, the 15 s power off on Power off, the power key again powers off (Halt), UP and DOWN keep the focus and the count
and it powers off (Halt) 16 s after the menu settled, LEFT moves the focus off Power off and stops the count for this show
(RIGHT back does not start it again, no power off, the menu stays), the sleep timer card opens and starts a timer, with a timer set
the menu opens on it without a count. Halt's call is refused on the emulator (the walk APK has the test platform key,
REBOOT is denied), its log line is the proof. One PASS/FAIL line per check.
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


HALT = re.compile(r"LumenHalt.*power off: Android shutdown")
MONO = re.compile(r"^\s*(\d+\.\d+)")


def bpress(ms=150):
    """The power key through the walk activity's receiver (a HID key would reach the power menu window, not the app)."""
    sh("am broadcast -a org.lumen.menu.walk.PRESS --ei ms %d -p %s" % (ms, PKG))


def tile_names():
    xml = sh("uiautomator dump /data/local/tmp/pw_rules.xml >/dev/null && cat /data/local/tmp/pw_rules.xml")
    return [d for _, d in sorted((int(x1), d) for d, x1 in re.findall(r'content-desc="([^"]+)"[^>]*bounds="\[(\d+),\d+\]', xml))], xml


def at(log, what):
    """Monotonic time of the first log line containing what, None when none."""
    for l in log.splitlines():
        if what in l:
            m = MONO.match(l)
            return float(m.group(1)) if m else None
    return None


def open_menu(what):
    check(what + ": the walk activity is in front", ready(), front())
    sh("logcat -c")
    hid(150)
    time.sleep(2.5)
    log = sh("logcat -d -v monotonic")
    m = re.search(r"round power menu shown: (\d+) tiles", log)
    return log, (int(m.group(1)) if m else 0)


def menu_rules():
    x50 = "x50" in PKG
    sh("settings put global hidden_api_policy 1")    # Halt's hidden PowerManager.shutdown reaches PowerManagerService
    log, n = open_menu("second press")
    names, _ = tile_names()
    check("menu: five tiles (Turn off screen, Sleep, Power off, Restart, Sleep timer)", n == 5 and len(names) == 5,
          "%d shown | %s" % (n, " | ".join(names)))
    check("menu: the 15 s power off starts on Power off", "power off in 15 s unless the focus leaves Power off" in log, log[-300:])
    time.sleep(1.0)
    bpress(150)
    time.sleep(2.5)
    log = sh("logcat -d -v monotonic")
    check("menu: the power key again powers off (Halt), no sleep", bool(re.search(r"again: power off", log))
          and bool(HALT.search(log)) and not SLEEP_LOG.search(log), " | ".join(l for l in log.splitlines()
                                                                             if re.search("Halt|again|sleep|PowerMenu", l))[-400:])
    sh("am force-stop " + PKG)
    start()
    log, n = open_menu("up down")
    time.sleep(0.5)
    sh("input keyevent 19")
    time.sleep(0.3)
    sh("input keyevent 20")
    time.sleep(9.5)
    log = sh("logcat -d -v monotonic")
    check("up down: UP and DOWN keep the count (stock: only the focus leaving Power off stops it)",
          "countdown stopped" not in log and "power off in 15 s" in log, log[-300:])
    check("up down: no power off 13 s after the menu opened", not HALT.search(log) and "countdown end" not in log)
    time.sleep(6)
    log = sh("logcat -d -v monotonic")
    t0, t1 = at(log, "power off in 15 s"), at(log, "countdown end: power off")
    check("up down: the count powers off (Halt) 16 s after the menu settled (stock: 0 shown for a second)",
          t0 is not None and t1 is not None and 15.5 <= t1 - t0 <= 17.0 and bool(HALT.search(log)),
          "settled %s end %s" % (t0, t1))
    sh("am force-stop " + PKG)
    start()
    log, n = open_menu("arrow")
    sh("input keyevent 21")
    time.sleep(0.5)
    sh("input keyevent 22")
    time.sleep(16.5)
    log = sh("logcat -d -v monotonic")
    check("arrow: LEFT (the focus leaves Power off) stops the count", "countdown stopped: the focus left Power off" in log,
          log[-300:])
    check("arrow: RIGHT back on Power off starts no new count (stock)", log.count("power off in 15 s") == 1, log[-300:])
    check("arrow: no power off 19 s after the menu opened", not HALT.search(log) and "countdown end" not in log)
    check("arrow: the menu stays open (it closes 20 s after the last key)", ACT not in front(), front())
    sh("input keyevent 4")
    time.sleep(1.5)
    log, n = open_menu("timer")
    for _ in range(2):                                  # Power off -> Restart -> Sleep timer
        sh("input keyevent 22")
        time.sleep(0.5)
    sh("input keyevent 23")
    time.sleep(1.5)
    shown, xml = tile_names()
    check("timer: OK on Sleep timer opens its card in place of the tiles", not shown and 'focused="true"' in xml,
          " | ".join(shown) + " " + xml[-200:])
    sh("input keyevent 4")
    time.sleep(1.5)
    log = sh("logcat -d -v monotonic")
    check("timer: Back returns to the tiles, no new count", ACT not in front() and log.count("power off in 15 s") == 1,
          front())
    sh("input keyevent 23")
    time.sleep(1.5)
    if x50:
        sh("input keyevent 20")                         # the X50 page starts on Off
        time.sleep(0.5)
    sh("input keyevent 23")
    time.sleep(2)
    log = sh("logcat -d -v monotonic")
    check("timer: a 15 min timer starts from the card", bool(re.search(r"started: 15 min|sleep timer 15 min", log)), log[-300:])
    sh("input keyevent 4")
    time.sleep(1.5)
    log, n = open_menu("timer set")
    time.sleep(1)
    log = sh("logcat -d -v monotonic")
    check("timer set: the menu opens without the count (stock: on the timer)", n == 5 and "power off in" not in log, log[-300:])
    sh("input keyevent 23")                             # the timer tile has the focus: its card, turn the timer off
    time.sleep(1.5)
    if x50:
        sh("input keyevent 19")
    else:
        for _ in range(3):
            sh("input keyevent 20")
            time.sleep(0.4)
    time.sleep(0.5)
    sh("input keyevent 23")
    time.sleep(1.5)
    sh("input keyevent 4")


def power_off_tile():
    """POWER_OFF=1: the round power menu's Power off tile on the emulator really powers off (Android's ShutdownThread),
    never the power key's sleep (LR-YVHNX4). The emulator goes down at the end: run it last, one model per boot."""
    import poweroff_log
    # the walk APK carries the public test platform key, not the emulator's: allow the hidden PowerManager.shutdown it
    # calls (a Lumen image's projector app is platform-signed), so the call reaches PowerManagerService
    sh("settings put global hidden_api_policy 1")
    sh("am force-stop " + PKG)
    start()
    check("the walk activity is in front before the press", ready(), front())
    sh("logcat -c")
    hid(150)
    time.sleep(2.5)                         # the enter animation and the OK guard
    xml = sh("uiautomator dump /sdcard/pw.xml >/dev/null && cat /sdcard/pw.xml")
    tiles = sorted((int(x1), d) for d, x1 in re.findall(r'content-desc="([^"]+)"[^>]*bounds="\[(\d+),\d+\]', xml))
    names = [d for _, d in tiles]
    idx = next((i for i, d in enumerate(names) if re.search(r"(?i)power ?off|выключ", d)), -1)
    check("Power off: the round menu shows a Power off tile", idx >= 0, " | ".join(names))
    if idx < 0:
        return
    for _ in range(idx):
        sh("input keyevent 22")
        time.sleep(0.4)
    sh("input keyevent 23")
    lines, gone = [], False
    for _ in range(40):
        time.sleep(1)
        if adb("get-state", timeout=10).strip() != "device":
            gone = True
            break
        lines = sh("logcat -d -b all -v threadtime").splitlines() or lines
    keep = [l for l in lines if re.search(r"LumenHalt|ShutdownThread|intercept_power|powerPress|Going to sleep|PowerWalk|"
                                          r"SecurityException|android\.permission\.REBOOT|power menu (power off|restart)|"
                                          r"shutdown \(user requested\)|shutdown failed", l)]
    out = os.environ.get("POWER_OFF_LOG")
    if out:
        with open(out, "w") as f:
            f.write("\n".join(keep) + "\n")
    v = poweroff_log.verdict(keep)
    denied = poweroff_log.denied(keep)
    check("Power off: Android's shutdown was called (ShutdownThread, or PowerManagerService.shutdown refusing only the "
          "REBOOT permission of a test-signed walk APK), never the power key's sleep",
          v == "off" or (v == "called" and denied), v + " | " + " | ".join(keep)[-400:])
    if v == "off":
        check("Power off: the emulator went down", gone)


def main():
    sh("pm clear " + PKG)
    sh("appops set %s SYSTEM_ALERT_WINDOW allow" % PKG)
    sh("appops set %s SCHEDULE_EXACT_ALARM allow" % PKG)      # the X50 sleep timer (SleepAlarm), as the real app
    sh("settings put system screen_off_timeout 1800000")
    wake()
    start()
    check("fresh install: the setting is Power menu (stock)", choice() == "power_menu", choice())
    if os.environ.get("POWER_OFF") == "1":
        power_off_tile()
        print("power_walk %s power off: %s" % (PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
        return 1 if FAILS else 0
    case("Power menu, short press", 150, "menu")
    case("Power menu, held 1.2 s", 1200, "sleep")
    start("--es power_short sleep")
    check("set to Sleep through the menu's module setting", choice() == "sleep", choice())
    case("Sleep, short press", 150, "sleep")
    case("Sleep, held 1.2 s", 1200, "menu")
    start("--es power_short power_menu")
    check("set back to Power menu", choice() == "power_menu", choice())
    case("Power menu again, short press", 150, "menu")
    if os.environ.get("MENU_RULES", "1") == "1":
        menu_rules()
    sh("am force-stop " + PKG)
    print("power_walk %s: %s" % (PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
