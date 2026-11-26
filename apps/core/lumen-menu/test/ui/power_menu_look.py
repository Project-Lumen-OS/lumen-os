#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Which screen the power menu is, on an EMULATOR (never a user's projector), with the power walk APK of a model
(run_power.sh builds and installs it): a short press of the virtual HID power key opens the ROUND power menu of every
family (ui/RoundTilePanel: the tiles in one row, centred on the screen, full-screen stage), never the side list; RIGHT
reaches the last tile, Back closes the menu. Screenshots in the system language and theme set by the arguments.
  python3 power_menu_look.py SERIAL PACKAGE OUT_DIR LOCALE THEME     (THEME light | dark)
One PASS/FAIL line per check, exit 1 on a FAIL."""
import os
import re
import sys
import time

SERIAL, PKG, OUT, LOCALE, THEME = sys.argv[1:6]
sys.argv = sys.argv[:3]
import power_walk as pw  # noqa: E402  (reads SERIAL and PACKAGE from argv)

SHOWN = re.compile(r"round power menu shown: (\d+) tiles")
NODE = re.compile(r'<node [^>]*content-desc="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def shot(name):
    os.makedirs(OUT, exist_ok=True)
    p = os.path.join(OUT, name + ".png")
    with open(p, "wb") as f:
        f.write(__import__("subprocess").run([pw.ADB, "-s", SERIAL, "exec-out", "screencap", "-p"],
                                              capture_output=True).stdout)
    print("SHOT " + p)


def tiles():
    pw.sh("uiautomator dump /data/local/tmp/pm_look.xml >/dev/null")
    xml = pw.sh("cat /data/local/tmp/pm_look.xml")
    return [(d, int(a), int(b), int(c), int(e)) for d, a, b, c, e in NODE.findall(xml)
            if int(c) - int(a) > 150 and abs((int(e) - int(b)) - (int(c) - int(a)) * 270 / 230) < 40]


def main():
    pw.sh("cmd locale set-app-locales %s --locales %s" % (PKG, LOCALE))
    pw.sh("settings put secure lumen_ui_theme %s" % THEME)
    pw.sh("am force-stop %s" % PKG)
    pw.wake()
    pw.start()
    pw.check("the walk activity is in front", pw.ready(), pw.front())
    pw.sh("logcat -c")
    pw.hid(150)
    time.sleep(2.0)
    log = pw.sh("logcat -d -s PowerMenu:I Z9xPowerMenu:I")
    m = SHOWN.search(log)
    pw.check("short press: the round power menu is shown", m is not None and int(m.group(1)) >= 3, log[-300:])
    w = int(re.search(r"Physical size: (\d+)x", pw.sh("wm size")).group(1))
    t = tiles()
    row = len(t) >= 3 and len({b for _, _, b, _, _ in t}) == 1
    mid = (min(a for _, a, _, _, _ in t) + max(c for _, _, _, c, _ in t)) / 2 if t else 0
    pw.check("the tiles are one row centred on the screen (not a side list)", row and abs(mid - w / 2) < w * 0.03,
             "%s centre %s of %s" % (t, mid, w))
    shot("menu")
    for _ in range(len(t) - 1):
        pw.sh("input keyevent 22")
        time.sleep(0.4)
    time.sleep(0.6)
    shot("menu_last_tile")
    pw.sh("input keyevent 4")
    time.sleep(1.5)
    pw.check("Back closes the round power menu", pw.ACT in pw.front(), pw.front())
    sys.exit(1 if pw.FAILS else 0)


if __name__ == "__main__":
    main()
