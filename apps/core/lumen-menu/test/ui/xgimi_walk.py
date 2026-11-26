#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of Back to the XGIMI system on an emulator (test APK of test/ui/build.sh, never a projector).

    python3 xgimi_walk.py SERIAL PACKAGE GSI   (adb from $ADB)

Settings > About opens the old screen's address, org.lumen.about.XgimiSystemActivity: the walk starts that activity
and checks that it only redirects (no window of its own) to the menu, which shows the stick's erase warning first
with the focus on Cancel; Cancel returns to All settings, General; Continue opens the menu's own page: the intro, the
model code of the unit (set on the emulator), the erase warning, the QR code and its link; DOWN walks every row that
takes the focus in order, UP walks back; BACK returns to General and closes the menu at the end. One PASS/FAIL line
per check, exit 1 on any failure."""
import subprocess
import sys
import time

import hdmi_walk as w

S = w.S
TITLE, ERASE, CONTINUE, CANCEL = S["lumen_about_back"], S["lumen_about_erases"], S["lumen_continue"], S["lumen_m_cancel"]
MODEL = "G0082"
SCREEN = "org.lumen.about.XgimiSystemActivity"


def unit_model():
    """The emulator has no XGIMI model code: give it one (ro.* can be set once while unset, as root)."""
    if w.sh("getprop " + "ro.boot.xgimi.modelname").strip():
        return
    subprocess.run([w.ADB, "-s", w.SERIAL, "root"], capture_output=True, timeout=40)
    time.sleep(3)
    subprocess.run([w.ADB, "-s", w.SERIAL, "wait-for-device"], capture_output=True, timeout=60)
    w.sh("setprop ro.boot.xgimi.modelname " + MODEL)


def redirect():
    w.sh("am start -n %s/%s" % (w.PKG, SCREEN))
    time.sleep(3)


def main():
    unit_model()
    has_model = w.sh("getprop ro.boot.xgimi.modelname").strip() == MODEL
    w.sh("am force-stop " + w.PKG)
    w.sh("input keyevent 3")
    time.sleep(1)
    w.sh("am start -n %s/%s --es section general" % (w.PKG, w.ACT))
    time.sleep(4)
    general = w.page(w.state())
    redirect()
    fr = w.sh("dumpsys window | grep mCurrentFocus")
    w.check("the old address only redirects: no window of its own", SCREEN not in fr, fr)
    s = w.state()
    w.check("the menu asks the erase warning first", w.page(s) == TITLE and "showing=true" in s["menu"]
            and any(w.field(r, "text") == ERASE for r in s["rows"]), s["menu"])
    w.check("the focus starts on Cancel", w.field(s["focus"], "title") == CANCEL, s["focus"])
    w.key("ok", 1.5)
    s = w.state()
    w.check("Cancel returns to All settings, General (%s)" % general, w.page(s) == general and "showing=true" in s["menu"],
            s["menu"])
    redirect()
    w.key("up", 0.8)
    w.check("UP reaches Continue", w.field(w.state()["focus"], "title") == CONTINUE, w.state()["focus"])
    w.key("ok", 2.5)
    s = w.state()
    w.check("Continue opens the page of the menu", w.page(s) == TITLE and "showing=true" in s["menu"]
            and not any(w.field(r, "title") == CONTINUE for r in s["rows"]), s["menu"])
    fr = w.sh("dumpsys window | grep mCurrentFocus")
    w.check("no other window (no dialog, no activity): the menu's own host", w.ACT in fr
            or "org.lumen.projector.ui.PanelActivity" in fr, fr)
    rows = s["rows"]
    kinds = [w.field(r, "class") or r.split()[1] for r in rows]
    w.check("the intro, the facts, the erase warning, the QR code and its link",
            "class=QrRow" in " ".join(rows) and any(w.field(r, "text") == ERASE for r in rows)
            and any((w.field(r, "text") or "").startswith("https://") for r in rows), kinds)
    model = [r for r in rows if w.field(r, "title") == S["lumen_about_model_code"]]
    if has_model:
        w.check("the model code row shows the unit's code", model and w.field(model[0], "value") == MODEL, model)
    else:
        print("SKIP the model code row: the emulator would not take a model code (no root)")
    seen = []
    for _ in range(14):
        f = w.state()["focus"]
        if not seen or seen[-1] != f:
            seen.append(f)
        w.key("down", 0.7)
    w.check("DOWN walks the rows to the link, the last one", (w.field(seen[-1], "text") or "").startswith("https://"),
            [x[:60] for x in seen])
    w.check("DOWN passes the erase warning", any(w.field(x, "text") == ERASE for x in seen), [x[:60] for x in seen])
    for _ in range(14):
        w.key("up", 0.5)
    w.check("UP walks back to the first row", w.state()["focus"] == seen[0], w.state()["focus"])
    w.key("back", 1.5)
    s = w.state()
    w.check("BACK returns to All settings, General", w.page(s) == general and "showing=true" in s["menu"], s["menu"])
    return w.finish()


if __name__ == "__main__":
    sys.exit(main())
