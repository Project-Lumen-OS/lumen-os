#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the power-on sound row (All settings > General > Power, power.on_music) on an emulator (test APK of
test/ui/build.sh with MenuWalkActivity, never a projector), with remote keys through adb, CHECKING after each key what
the walk activity dumps and what the switch's props read.

    python3 startup_sound_walk.py SERIAL PACKAGE   (adb from $ADB)

The emulator must let the app write persist.lumen.* (userdebug image with -selinux permissive, run_startup_sound.sh).
Checks: the gear opens All settings on General; the row is in General's Power group, once; DOWN reaches it; its switch
shows the stored value (unset = on, the stock default); OK turns it off: persist.lumen.boot_sound 0 and the boot
animation gate persist.lumen.boot_sound.play 0, the switch shows off, the menu stays open with the focus on the row; a
second OK turns it on again (both 1, the music is audible); BACK closes the menu and nothing else comes to the front.
One PASS/FAIL line per check, exit 1 on any failure."""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG = sys.argv[1], sys.argv[2]
ACT = "org.lumen.menu.walk.MenuWalkActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
ROW, POWER, GENERAL = "Sound at power-on", "Power", "General"
SWITCH, GATE = "persist.lumen.boot_sound", "persist.lumen.boot_sound.play"
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def prop(name):
    return sh("getprop " + name).strip()


def key(n, wait=0.8):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def state():
    s = {"rows": [], "menu": "", "focus": ""}
    for line in sh("dumpsys activity %s/%s" % (PKG, ACT)).splitlines():
        line = line.strip()
        if line.startswith("walk.menu "):
            s["menu"] = line
        elif line.startswith("walk.focus "):
            s["focus"] = line
        elif line.startswith("walk.row "):
            s["rows"].append(line)
    return s


def check(name, ok, detail: object = ""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:400] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def title(line):
    m = re.search(r" title=(.*?)(?: value=| text=| checked=| index=| desc=|$)", line)
    if not m and "class=HeaderRow" in line:
        m = re.search(r" text=(.*?)(?: checked=| index=| desc=|$)", line)
    return m.group(1).strip() if m else ""


def checked(line):
    m = re.search(r" checked=(true|false)", line)
    return None if not m else m.group(1) == "true"


def desc(line):
    m = re.search(r" desc=(.*)$", line)
    return m.group(1).strip() if m else ""


def front():
    return re.findall(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}", sh("dumpsys window"))


def toggle(want_on):
    key("ok", 1.5)
    s = state()
    word = "on" if want_on else "off"
    check("OK turns it %s: %s = %s" % (word, SWITCH, int(want_on)), prop(SWITCH) == str(int(want_on)), prop(SWITCH))
    check("OK turns it %s: the boot animation gate %s = %s" % (word, GATE, int(want_on)), prop(GATE) == str(int(want_on)),
          prop(GATE))
    check("the switch shows %s" % word, checked(s["focus"]) is want_on, s["focus"])
    check("the menu stays open", "showing=true" in s["menu"], s["menu"])
    check("the focus stays on the row", title(s["focus"]) == ROW, s["focus"])


def main():
    sh("cmd media_session volume --stream 3 --set 5")        # an audible music level: the gate follows the switch
    sh("am force-stop " + PKG)
    sh("input keyevent 3")
    time.sleep(1)
    sh("am start -n %s/%s" % (PKG, ACT))
    time.sleep(4)
    check("the card opens", "showing=true" in state()["menu"], state()["menu"])
    for k, n in (("up", 5), ("right", 4)):
        for _ in range(n):
            if "key=general" in state()["focus"]:
                break
            key(k)
    check("UP and RIGHT reach the gear", "key=general" in state()["focus"], state()["focus"])
    key("ok", 2.0)
    s = state()
    check("All settings opens on General", desc(s["focus"]) == GENERAL, s["focus"])
    rows = s["rows"]
    titles = [title(r) for r in rows]
    check("the row is on General once", titles.count(ROW) == 1, titles)
    if ROW not in titles:
        return
    heads = [(i, title(r)) for i, r in enumerate(rows) if "class=HeaderRow" in r]
    at = titles.index(ROW)
    group = [t for i, t in heads if i < at][-1:]
    check("the row is in the Power group", group == [POWER], (group, heads))
    stored = prop(SWITCH)
    was_on = stored != "0"
    check("the switch shows the stored value (unset = on)", checked(rows[at]) is was_on, (stored, rows[at]))
    key("right", 1.0)
    for _ in range(len(rows) + 2):
        if title(state()["focus"]) == ROW:
            break
        key("down", 0.6)
    check("DOWN reaches the row", title(state()["focus"]) == ROW, state()["focus"])
    toggle(not was_on)
    toggle(was_on)
    if not was_on:
        toggle(False)
        toggle(True)
    for _ in range(4):
        if "showing=true" not in state()["menu"]:
            break
        key("back", 1.2)
    check("BACK closes the menu", "showing=true" not in state()["menu"], state()["menu"])
    fr = front()
    check("nothing else came to the front", any(ACT in x for x in fr), fr)


if __name__ == "__main__":
    main()
    print("startup sound walk: %s" % ("PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    sys.exit(1 if FAILS else 0)
