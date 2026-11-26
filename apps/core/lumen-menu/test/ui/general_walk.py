#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of All settings > General on an emulator (test APK of test/ui/build.sh, never a projector): presses remote
keys with adb and CHECKS after each step what test/ui/MenuWalkActivity dumps (panel, page, focus, rows).

    python3 general_walk.py SERIAL PACKAGE   (adb from $ADB)

Checks: the card opens; the gear (round General button of the top row) takes the focus with UP and RIGHT; OK opens
All settings; General is reached with the D-pad in the category rail; its rows list Pair remote and, right after it, Remote buttons, each
once; DOWN reaches Remote buttons; OK opens the Remote buttons screen (org.lumen.remote.keys.KeyBindActivity in front);
BACK returns to the screen the menu was opened on. One PASS/FAIL line per check, exit 1 on any failure."""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG = sys.argv[1], sys.argv[2]
ACT = "org.lumen.menu.walk.MenuWalkActivity"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
PAIR, BUTTONS, GENERAL = "Pair the remote", "Remote buttons", "General"
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def key(n, wait=1.0):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def state():
    t = sh("dumpsys activity %s/%s" % (PKG, ACT))
    s = {"rows": [], "raw": t}
    for line in t.splitlines():
        line = line.strip()
        if line.startswith("walk.menu "):
            s["menu"] = line
        elif line.startswith("walk.focus "):
            s["focus"] = line
        elif line.startswith("walk.row "):
            s["rows"].append(line)
    return s


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + detail[:300] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def front():
    return re.findall(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}", sh("dumpsys window"))


def titles(rows):
    out = []
    for r in rows:
        m = re.search(r" title=(.*?)(?: value=| text=| desc=|$)", r)
        out.append(m.group(1).strip() if m else "")
    return out


def open_buttons():
    """From a fresh start of the walk activity to the Remote buttons screen (keys_walk.py starts here too)."""
    sh("am force-stop " + PKG)
    sh("input keyevent 3")
    time.sleep(1)
    sh("am start -n %s/%s" % (PKG, ACT))
    time.sleep(4)
    s = state()
    check("the card opens", "showing=true" in s.get("menu", ""), s.get("menu", ""))
    # the gear: the last round button of the top row
    for _ in range(5):
        if "key=general" in state().get("focus", ""):
            break
        key("up")
    for _ in range(4):
        f = state().get("focus", "")
        if "key=general" in f:
            break
        key("right")
    s = state()
    check("UP and RIGHT reach the gear of the top row", "key=general" in s.get("focus", ""), s.get("focus", ""))
    key("ok", 2.0)
    s = state()
    check("OK on the gear opens All settings", "showing=true" in s.get("menu", "") and "depth=0" not in s.get("menu", ""),
          s.get("menu", ""))
    # General in the category rail of All settings (RailItem, its name as content description): DOWN walks towards
    # the end of the rail (General, then For developers), UP back
    seen = []
    for k in ["down"] * 9 + ["up"] * 9:
        f = re.search(r" desc=(.*)$", state().get("focus", ""))
        seen.append(f.group(1).strip() if f else "")
        if seen[-1] == GENERAL:
            break
        key(k)
    s = state()
    t = titles(s["rows"])
    check("All settings > General is on screen (its rail item focused, its rows shown)",
          bool(seen) and seen[-1] == GENERAL and PAIR in t, " / ".join(seen))
    if os.environ.get("WALK_DEBUG"):
        print(s["raw"])
    check("General lists Remote buttons once", t.count(BUTTONS) == 1, ", ".join(t))
    check("General lists Pair remote once", t.count(PAIR) == 1, ", ".join(t))
    check("Remote buttons stands right after Pair remote", PAIR in t and BUTTONS in t and t.index(BUTTONS) == t.index(PAIR) + 1,
          ", ".join(t))
    # focus the Remote buttons row
    key("right")
    for _ in range(len(t) + 2):
        f = state().get("focus", "")
        if "title=" + BUTTONS in f:
            break
        key("down", 0.8)
    s = state()
    check("DOWN reaches the Remote buttons row", "title=" + BUTTONS in s.get("focus", ""), s.get("focus", ""))
    key("ok", 3.0)
    fr = front()
    check("OK on Remote buttons opens its screen", any("org.lumen.remote.keys.KeyBindActivity" in x for x in fr), str(fr))
    return any("org.lumen.remote.keys.KeyBindActivity" in x for x in fr)


def main():
    open_buttons()
    key("back", 2.5)
    fr = front()
    check("BACK leaves the Remote buttons screen", not any("KeyBindActivity" in x for x in fr), str(fr))
    check("BACK returns to the screen the menu was opened on (the menu closed before the screen opened, as every"
          " screen row)", any(ACT in x for x in fr), str(fr))
    sh("am force-stop " + PKG)
    print("general_walk %s: %s" % (PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
