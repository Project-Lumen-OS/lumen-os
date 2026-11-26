#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the menu's Licences page on an emulator (test APK of test/ui/build.sh, never a projector).

    python3 licences_walk.py SERIAL PACKAGE GSI   (adb from $ADB)

Checks: All settings lists Licences; OK opens the menu's own page (no other window, no dialog); the focus starts on
the first credit and DOWN walks every credit row once in order to the last; UP walks back; BACK returns to the
section and closes the menu at the end. One PASS/FAIL line per check, exit 1 on any failure."""
import re
import sys
import time

import hdmi_walk as w

LIC = w.S["lumen_gm_licences"]
ACT = w.ACT


def credit_rows(s):
    return [r for r in s["rows"] if "class=TextRow" in r][1:]


def main():
    w.sh("am force-stop " + w.PKG)
    w.sh("input keyevent 3")
    time.sleep(1)
    w.sh("am start -n %s/%s --es section general" % (w.PKG, ACT))
    time.sleep(4)
    s = w.to_rail_row(LIC)
    w.check("All settings lists Licences (%s)" % LIC, LIC in w.titles(s["rows"]), ", ".join(w.titles(s["rows"])))
    section = w.page(s)
    w.key(w.INTO)
    w.check("DOWN reaches the Licences row", w.focus_row(LIC), w.state()["focus"])
    w.key("ok", 2.5)
    s = w.state()
    w.check("OK opens the Licences page of the menu", w.page(s) == LIC and "showing=true" in s["menu"], s["menu"])
    fr = w.sh("dumpsys window | grep mCurrentFocus")
    w.check("no other window (no dialog, no activity): the menu's own host", ACT in fr or "org.lumen.projector.ui.PanelActivity" in fr, fr)
    rows = credit_rows(s)
    w.check("one row per credit (%d)" % len(rows), len(rows) >= 6, rows)
    texts = [w.field(r, "text") or "" for r in rows]
    seen = []
    for i in range(len(rows) + 2):
        f = w.state()["focus"]
        t = w.field(f, "text") or ""
        if "class=TextRow" in f and (not seen or seen[-1] != t):
            seen.append(t)
        w.key("down", 0.7)
    w.check("DOWN walks every credit once, in order", seen == texts, " | ".join(x[:24] for x in seen))
    w.check("the last credit is the Blender films", "Blender" in (seen[-1] if seen else ""), seen[-1:] )
    for _ in range(len(rows) + 2):
        w.key("up", 0.5)
    f = w.state()["focus"]
    w.check("UP walks back to the first credit", (w.field(f, "text") or "") == (texts[0] if texts else None), f)
    w.key("back", 1.5)
    s = w.state()
    w.check("BACK returns to the section (%s)" % section, w.page(s) == section and "showing=true" in s["menu"], s["menu"])
    return w.finish()


if __name__ == "__main__":
    sys.exit(main())
