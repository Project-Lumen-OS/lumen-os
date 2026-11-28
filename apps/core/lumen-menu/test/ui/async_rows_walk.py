#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the menu's own row values after a device row changes (owner bug: after 'Open at power-on' changed, the
'Sound at power-on' row below it showed the loading dots until General was opened again), on an emulator (test APK of
test/ui/build.sh with MenuWalkActivity, never a projector), remote keys through adb.

    python3 async_rows_walk.py SERIAL PACKAGE FEATURE   (adb from $ADB; FEATURE = a device row of General, e.g.
                                                         power.boot_source, power.on_ac on the X50 family)

The stand-in adapter answers FEATURE as a real adapter does (MenuWalkActivity extra echo): the write is stored and
150 ms later the read-back of its device rows alone comes back (Result.done(read)). Every other value on screen comes
from the menu itself or Android: the power-on sound (BootSound), key tone, the screen saver delay, language, date,
storage, version, model, the volume. Checks: All settings opens on General, the device row and 'Sound at power-on' on their page (Advanced settings >
Power on and off where the model has it);
the device row takes the change (its read-back was applied); after it EVERY row of the menu (all pages,
MenuController.rows) that showed a value before shows the same value (no switch without a state, no choice or slider
without its value, no empty value on a screen row); the same after the row is put back; BACK closes the menu.
One PASS/FAIL line per check, exit 1 on any failure."""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG, FEATURE = sys.argv[1], sys.argv[2], sys.argv[3]
ACT = "org.lumen.menu.walk.MenuWalkActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
MUSIC, GENERAL = "power.on_music", "General"
ADV, POF = "Advanced settings", "Power on and off"   # stock General > more_setting > boot_and_power_off_settings
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def key(n, wait=0.8):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def state():
    s = {"rows": [], "all": {}, "menu": "", "focus": ""}
    for line in sh("dumpsys activity %s/%s" % (PKG, ACT)).splitlines():
        line = line.strip()
        if line.startswith("walk.menu "):
            s["menu"] = line
        elif line.startswith("walk.focus "):
            s["focus"] = line
        elif line.startswith("walk.row "):
            s["rows"].append(line)
        elif line.startswith("walk.all "):
            m = re.match(r"walk\.all f=(\S+) (.*)$", line)
            if m:
                s["all"][m.group(1)] = m.group(2)
    return s


def check(name, ok, detail: object = ""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:400] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def title(line):
    m = re.search(r" title=(.*?)(?: value=| text=| checked=| index=| known=| desc=|$)", line)
    return m.group(1).strip() if m else ""


def field(line, name):
    m = re.search(r"(?:^| )%s=(\S+)" % name, line)
    return m.group(1) if m else None


def cls(line):
    return field(line, "class") or ""


def shown(line):
    """What the row shows as its value, or None for the loading dots / no value."""
    c = cls(line)
    if c == "ToggleRow":
        return field(line, "checked")
    if c == "ChoiceRow":
        i = field(line, "index")
        return None if i in (None, "-1") else i
    if c == "SliderRow":
        return field(line, "value") if field(line, "known") == "true" else None
    if c == "NavRow":
        m = re.search(r" value=(.*?)(?: text=| checked=| index=| known=| desc=|$)", line)
        return m.group(1).strip() if m and m.group(1).strip() else None
    return None


def keeps(before, after, skip, when):
    lost = []
    for f, line in before.items():
        was = shown(line)
        if f in skip or was is None:
            continue
        now = shown(after.get(f, ""))
        ok = now == was
        check("%s: %s (%s) still shows %s" % (when, title(line) or f, f, was), ok, after.get(f))
        if not ok:
            lost.append(f)
    return lost


def change(row_cls, back=False):
    key("ok" if row_cls == "ToggleRow" else ("left" if back else "right"), 2.0)


def main():
    sh("am force-stop " + PKG)
    sh("input keyevent 3")
    time.sleep(1)
    sh("am start -n %s/%s --es echo %s" % (PKG, ACT, FEATURE))
    time.sleep(4)
    check("the card opens", "showing=true" in state()["menu"], state()["menu"])
    for k, n in (("up", 5), ("right", 4)):
        for _ in range(n):
            if "key=general" in state()["focus"]:
                break
            key(k)
    check("UP and RIGHT reach the gear", "key=general" in state()["focus"], state()["focus"])
    key("ok", 2.5)
    s = state()
    check("All settings opens on General", (field(s["focus"], "desc") or "") == GENERAL or " desc=General" in s["focus"],
          s["focus"])
    before = s["all"]
    check("the menu lists its rows (walk.all)", len(before) > 5, len(before))
    dev, music = before.get(FEATURE, ""), before.get(MUSIC, "")
    titles = [title(r) for r in s["rows"]]
    inpage = title(dev) not in titles and ADV in titles
    if inpage:                                   # the power rows stand on Advanced settings > Power on and off
        key("right", 1.0)
        for name in (ADV, POF):
            for _ in range(len(state()["rows"]) + 2):
                if title(state()["focus"]) == name:
                    break
                key("down", 0.6)
            check("DOWN reaches " + name, title(state()["focus"]) == name, state()["focus"])
            key("ok", 1.5)
        s = state()
        titles = [title(r) for r in s["rows"]]
    check("the device row %s is on its page" % FEATURE, title(dev) in titles and title(dev) != "", (title(dev), titles))
    check("'Sound at power-on' is on the same page", title(music) in titles and title(music) != "", (title(music), titles))
    check("the device row shows its read value", shown(dev) is not None, dev)
    check("'Sound at power-on' shows its value before", shown(music) is not None, music)
    if title(dev) not in titles or shown(dev) is None:
        return
    if not inpage:
        key("right", 1.0)
    for _ in range(len(s["rows"]) + 2):
        if title(state()["focus"]) == title(dev):
            break
        key("down", 0.6)
    check("DOWN reaches the device row", title(state()["focus"]) == title(dev), state()["focus"])
    change(cls(dev))
    a = state()
    check("the device row took the change (its read-back is on screen)",
          shown(a["all"].get(FEATURE, "")) not in (None, shown(dev)), a["all"].get(FEATURE))
    check("'Sound at power-on' shows its value right after the change",
          shown(a["all"].get(MUSIC, "")) == shown(music), a["all"].get(MUSIC))
    lost = keeps(before, a["all"], {FEATURE}, "after the change")
    check("no row lost its value after the change", not lost, lost)
    check("the focus stays on the device row", title(a["focus"]) == title(dev), a["focus"])
    change(cls(dev), back=True)
    b = state()
    check("the device row is back on its first value", shown(b["all"].get(FEATURE, "")) == shown(dev),
          b["all"].get(FEATURE))
    lost = keeps(before, b["all"], {FEATURE}, "after the change back")
    check("no row lost its value after the change back", not lost, lost)
    for _ in range(6):
        if "showing=true" not in state()["menu"]:
            break
        key("back", 1.2)
    check("BACK closes the menu", "showing=true" not in state()["menu"], state()["menu"])


if __name__ == "__main__":
    main()
    print("async rows walk: %s" % ("PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    sys.exit(1 if FAILS else 0)
