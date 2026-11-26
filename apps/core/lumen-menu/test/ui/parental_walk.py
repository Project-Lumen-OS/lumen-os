#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the menu's Parental controls page on an emulator (test APK of test/ui/build.sh plus Lumen Home of the
same tree, signed with the same test key, which holds the state behind its ParentalProvider; never a projector).

    python3 parental_walk.py SERIAL PACKAGE GSI   (adb from $ADB)

Checks: All settings, General lists Parental controls; OK opens the menu's own page (lock off: the lock and the eye
note); the lock asks a new password twice in the menu's password page (arrow keys) and the rows of the lock show;
each row works and the value comes back from Home (time per session, password at power on, child mode, the apps of
child mode, the control range); Change password checks the old one first; BACK returns to General; with the lock on
the page opens only after the password (a wrong one is refused); turning the lock off asks first and drops the rows;
the old address (action org.lumen.parental.SETTINGS) only redirects to the page. One PASS/FAIL line per check, exit 1
on any failure."""
import sys
import time

import hdmi_walk as w

S = w.S
ROW, TITLE = S["lumen_m_parental"], S["lumen_lp_title"]
LOCK, SESSION, BOOT_PIN, CHILD = S["lumen_lp_lock"], S["lumen_lp_session"], S["lumen_lp_boot_pin"], S["lumen_lp_child_mode"]
APPS, CHANGE, RANGE = S["lumen_lp_child_apps"], S["lumen_lp_change_pin"], S["lumen_lp_range"]
PIN_SET, PIN_AGAIN, PIN_ENTER = S["lumen_lp_pin_set"], S["lumen_lp_pin_again"], S["lumen_lp_pin_enter"]
WRONG, OFF_TITLE, TURN_OFF = S["lumen_lp_pin_wrong"], S["lumen_lp_lock_off_title"], S["lumen_turn_off"]
ARROW = {"U": "up", "D": "down", "L": "left", "R": "right"}


def row(s, title):
    for r in s["rows"]:
        if w.field(r, "title") == title:
            return r
    return ""


def pin(keys):
    for k in keys:
        w.key(ARROW[k], 0.5)
    time.sleep(1.5)


def to_row(title, limit=14):
    for k in ["up"] * limit:
        if w.field(w.state()["focus"], "title") == title:
            return True
        w.key(k, 0.4)
    for k in ["down"] * limit:
        if w.field(w.state()["focus"], "title") == title:
            return True
        w.key(k, 0.5)
    return w.field(w.state()["focus"], "title") == title


def open_page():
    s = w.to_rail_row(ROW)
    w.key(w.INTO)
    w.focus_row(ROW)
    w.key("ok", 2.5)
    return s


def main():
    w.sh("pm clear org.lumen.home")
    w.sh("am force-stop " + w.PKG)
    w.sh("input keyevent 3")
    time.sleep(1)
    w.sh("am start -n %s/%s --es section general" % (w.PKG, w.ACT))
    time.sleep(4)
    s = w.to_rail_row(ROW)
    if not w.check("All settings lists Parental controls (%s)" % ROW, ROW in w.titles(s["rows"]), ", ".join(w.titles(s["rows"]))):
        return w.finish()
    general = w.page(s)
    w.key(w.INTO)
    w.check("DOWN reaches the Parental controls row", w.focus_row(ROW), w.state()["focus"])
    w.key("ok", 2.5)
    s = w.state()
    w.check("OK opens the page of the menu, lock off: the lock and the note", w.page(s) == TITLE
            and "checked=false" in row(s, LOCK) and len(s["rows"]) == 2, s["rows"])
    fr = w.sh("dumpsys window | grep mCurrentFocus")
    w.check("no other window (Home's old screen is gone)", w.ACT in fr or "PanelActivity" in fr, fr)
    w.key("ok", 2.0)
    w.check("the lock asks a new password in the menu", w.page(w.state()) == PIN_SET, w.state()["menu"])
    pin("UUDD")
    w.check("the second entry", w.page(w.state()) == PIN_AGAIN, w.state()["menu"])
    pin("UUDD")
    s = w.state()
    w.check("back on the page: the lock is on, its rows show", w.page(s) == TITLE and "checked=true" in row(s, LOCK)
            and all(row(s, t) for t in (RANGE, SESSION, BOOT_PIN, CHILD, APPS, CHANGE)), s["rows"])
    w.check("the focus stays on the lock", w.field(s["focus"], "title") == LOCK, s["focus"])
    to_row(SESSION)
    w.key("right", 2.5)
    w.check("time per session: the next stock choice (20 min), read back from Home",
            w.field(row(w.state(), SESSION), "index") == "1", row(w.state(), SESSION))
    to_row(BOOT_PIN)
    w.key("ok", 2.0)
    w.check("password at power on: on, read back", "checked=true" in row(w.state(), BOOT_PIN), row(w.state(), BOOT_PIN))
    to_row(CHILD)
    w.key("ok", 2.0)
    w.check("child mode: on, read back", "checked=true" in row(w.state(), CHILD), row(w.state(), CHILD))
    to_row(RANGE)
    w.key("right", 2.5)
    w.check("control range: everywhere, read back", w.field(row(w.state(), RANGE), "index") == "1", row(w.state(), RANGE))
    to_row(APPS)
    w.key("ok", 2.0)
    s = w.state()
    w.check("the apps of child mode: a page of switches", w.page(s) == APPS and s["rows"]
            and all("class=ToggleRow" in r for r in s["rows"]), s["rows"][:3])
    w.key("ok", 2.0)
    w.check("an app picked", "checked=true" in w.state()["focus"], w.state()["focus"])
    w.key("back", 2.0)
    s = w.state()
    w.check("BACK: the page counts the pick", w.page(s) == TITLE and w.field(row(s, APPS), "value")
            == S["lumen_lp_child_apps_count"].replace("%1$d", "1"), row(s, APPS))
    to_row(CHANGE)
    w.key("ok", 2.0)
    w.check("Change password checks the old one first", w.page(w.state()) == PIN_ENTER, w.state()["menu"])
    pin("UUDD")
    w.check("then a new one", w.page(w.state()) == PIN_SET, w.state()["menu"])
    pin("LRLR")
    pin("LRLR")
    w.check("back on the page", w.page(w.state()) == TITLE, w.state()["menu"])
    w.key("back", 1.5)
    w.check("BACK returns to General", w.page(w.state()) == general, w.state()["menu"])
    to_row(ROW)
    w.key("ok", 2.5)
    w.check("with the lock on the page asks the password first", w.page(w.state()) == PIN_ENTER, w.state()["menu"])
    pin("UUDD")
    s = w.state()
    w.check("the old password is refused", w.page(s) == PIN_ENTER and any(w.field(r, "text") == WRONG for r in s["rows"]),
            s["rows"])
    pin("LRLR")
    s = w.state()
    w.check("the new password opens the page, the values kept by Home", w.page(s) == TITLE
            and "checked=true" in row(s, BOOT_PIN) and "checked=true" in row(s, CHILD), s["rows"])
    to_row(LOCK)
    w.key("ok", 2.0)
    s = w.state()
    w.check("turning the lock off asks first, focus on Cancel", w.page(s) == OFF_TITLE
            and w.field(s["focus"], "title") == S["lumen_m_cancel"], s["focus"])
    w.key("up", 0.8)
    w.key("ok", 2.5)
    s = w.state()
    w.check("the lock is off, its rows are gone", w.page(s) == TITLE and "checked=false" in row(s, LOCK)
            and len(s["rows"]) == 2, s["rows"])
    w.key("back", 1.5)
    w.sh("am start -a org.lumen.parental.SETTINGS")
    time.sleep(3)
    s = w.state()
    fr = w.sh("dumpsys window | grep mCurrentFocus")
    w.check("the old address only redirects to the page", w.page(s) == TITLE and "org.lumen.home" not in fr, fr + s["menu"])
    w.key("back", 1.5)
    w.check("BACK returns to General", w.page(w.state()) == general, w.state()["menu"])
    return w.finish()


if __name__ == "__main__":
    sys.exit(main())
