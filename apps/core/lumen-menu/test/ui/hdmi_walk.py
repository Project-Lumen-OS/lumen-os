#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the shared HDMI page on an emulator (test APK of test/ui/build.sh plus the fake port app of
apps/core/lumen-hdmiports/test/ui/build_fake.sh, never a projector): presses remote keys with adb and CHECKS after each
step what test/ui/MenuWalkActivity dumps (panel, page, focus, rows, the Source key's inputs).

    python3 hdmi_walk.py SERIAL PACKAGE GSI   (adb from $ADB)

Checks: All settings lists the HDMI page row; OK opens it in the menu's own rows (two switches, the power-on choice,
the ports with their state from the one port state); a port plugged, unplugged or renamed by CEC changes its row live
and the Source key's list; OK on a switch writes the HDMI app's option and shows it; BACK returns to the section; the
HDMI-CEC page opens the same HDMI page and BACK returns there. One PASS/FAIL line per check, exit 1 on any failure."""
import glob
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL, PKG, GSI = sys.argv[1], sys.argv[2], sys.argv[3]
ACT = "org.lumen.menu.walk.MenuWalkActivity"
FAKE = "org.lumen.hdmiports.fake"
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
FAILS = []
INTO = "right"          # from the rail into the rows (left on a menu that stands on the right side)


def strings():
    """English texts of every module (aliases resolved), as the test APK shows them."""
    raw = {}
    for f in glob.glob(os.path.join(GSI, "apps/core/*/res/values/*.xml")):
        for n, v in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(f, encoding="utf-8").read(), re.S):
            raw[n] = v.replace("\\'", "'")
    for _ in range(4):
        raw = {n: raw.get(v[8:], v) if v.startswith("@string/") else v for n, v in raw.items()}
    return raw


S = strings()
HDMI, CEC = S["lumen_m_hdmi_options"], S["lumen_m_cec_entry"]
ON, OFF, NAME = S["lumen_connected"], S["lumen_not_connected"], "Box R 4K Plus"


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def key(n, wait=1.0):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def state():
    t = sh("dumpsys activity %s/%s" % (PKG, ACT))
    s = {"rows": [], "raw": t, "menu": "", "focus": "", "source": ""}
    for line in t.splitlines():
        line = line.strip()
        for k in ("menu", "focus", "source"):
            if line.startswith("walk.%s " % k):
                s[k] = line
        if line.startswith("walk.row "):
            s["rows"].append(line)
    return s


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:400] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


def field(row, name):
    m = re.search(r" %s=(.*?)(?: (?:key|title|value|text|checked|index|desc)=|$)" % name, row)
    return m.group(1).strip() if m else None


def titles(rows):
    return [field(r, "title") or "" for r in rows]


def page(s):
    m = re.search(r" page=(.*)$", s["menu"])
    return m.group(1).strip() if m else ""


def port_row(s, port):
    for r in s["rows"]:
        if "class=NavRow" in r and (field(r, "title") or "").startswith("HDMI %d" % port):
            return r
    return ""


def fake(port, state=None, title=None):
    a = "am broadcast -n %s/.FakePortsReceiver --ei port %d" % (FAKE, port)
    if state:
        a += " --es state " + state
    if title is not None:
        a += " --es title '%s'" % title
    sh(a)
    time.sleep(2.0)


def prefs():
    return sh("content call --uri content://org.lumen.tvinput.hdmistate --method get_hdmi_prefs")


def to_rail_row(title):
    """All settings: the rail item whose rows list `title`, DOWN towards the end, then UP (from the rows: LEFT first)."""
    global INTO
    for k in ("left", "right", "right"):      # the rail is RIGHT of the rows when the menu stands on the right side
        if "class=RailItem" in state()["focus"]:
            break
        key(k, 0.8)
        if "class=RailItem" in state()["focus"]:
            INTO = "right" if k == "left" else "left"
    for k in ["down"] * 20 + ["up"] * 20:
        s = state()
        if title in titles(s["rows"]):
            return s
        key(k, 0.8)
    return state()


def focus_row(title, limit=24):
    for _ in range(limit):
        if "title=" + title in state()["focus"]:
            return True
        key("down", 0.7)
    return "title=" + title in state()["focus"]


def hdmi_page_checks(where):
    s = state()
    check(where + ": OK opens the HDMI page", page(s) == HDMI, s["menu"])
    cls = [re.search(r"class=(\w+)", r).group(1) for r in s["rows"]]
    check(where + ": the menu's rows (two switches, the power-on choice)", cls[:3] == ["ToggleRow", "ToggleRow", "ChoiceRow"],
          cls)
    return s


def port_checks(s):
    r1, r2, r3 = port_row(s, 1), port_row(s, 2), port_row(s, 3)
    check("port 1 (eARC soundbar, +5V): connected", field(r1, "value") == ON, r1)
    check("port 2 shows the CEC name and is connected", NAME in (field(r2, "title") or "") and field(r2, "value") == ON, r2)
    check("port 3 (empty): not connected", field(r3, "value") == OFF, r3)
    check("Source key: ports 1 and 2, the CEC name", "connected=[1, 2]" in s["source"] and "2:" + NAME in s["source"],
          s["source"])
    fake(3, "on")
    s = state()
    check("plug: port 3 turns connected on the open page", field(port_row(s, 3), "value") == ON, port_row(s, 3))
    check("plug: the Source key steps to port 3 too", "connected=[1, 2, 3]" in s["source"], s["source"])
    fake(1, "disconnected")
    s = state()
    check("unplug: port 1 turns not connected", field(port_row(s, 1), "value") == OFF, port_row(s, 1))
    check("unplug: the Source key skips port 1", "connected=[2, 3]" in s["source"], s["source"])
    fake(2, title="Apple TV")
    s = state()
    check("CEC name: port 2 shows the new name", "Apple TV" in (field(port_row(s, 2), "title") or ""), port_row(s, 2))
    check("CEC name: the Source key shows it", "2:Apple TV" in s["source"], s["source"])
    fake(2, "asleep")
    s = state()
    check("CEC asleep: port 2 asleep", field(port_row(s, 2), "value") == S["lumen_asleep"], port_row(s, 2))
    # the first switch: OK writes the HDMI app's option, the row shows it
    for _ in range(12):
        if "class=ToggleRow" in state()["focus"] and HDMI == page(state()):
            f = state()["focus"]
            if field(f, "title") == S["lumen_m_hdmi_auto_switch"]:
                break
        key("up", 0.6)
    before = field(state()["focus"], "checked")
    check("UP reaches the auto switch row", field(state()["focus"], "title") == S["lumen_m_hdmi_auto_switch"], state()["focus"])
    key("ok", 1.5)
    f = state()["focus"]
    check("OK flips the switch (%s -> %s)" % (before, field(f, "checked")), before == "false" and field(f, "checked") == "true", f)
    check("the HDMI app got the option", "auto_switch_on_plug=true" in prefs(), prefs())
    key("ok", 1.5)
    check("OK again: off in the HDMI app", "auto_switch_on_plug=false" in prefs(), prefs())
    check("the focus stays on the switch", field(state()["focus"], "title") == S["lumen_m_hdmi_auto_switch"], state()["focus"])


def main():
    sh("am force-stop " + FAKE)
    sh("am force-stop " + PKG)
    sh("input keyevent 3")
    time.sleep(1)
    sh("am start -n %s/%s --ez real_pages true --es section general" % (PKG, ACT))
    time.sleep(4)
    s = state()
    check("All settings opens", "showing=true" in s["menu"] and "depth=0" not in s["menu"], s["menu"])
    s = to_rail_row(HDMI)
    direct = check("All settings lists the HDMI page row (every model, the shared base row)", HDMI in titles(s["rows"]),
                   ", ".join(titles(s["rows"])))
    if direct:
        section = page(s)
        key(INTO)
        check("DOWN reaches the HDMI row", focus_row(HDMI), state()["focus"])
        key("ok", 2.5)
        port_checks(hdmi_page_checks("All settings"))
        key("back", 1.5)
        s = state()
        check("BACK returns to the section (%s)" % section, page(s) == section and "showing=true" in s["menu"], s["menu"])
    else:
        print("SKIP no HDMI page row in All settings on this profile: the HDMI-CEC page opens it")
    # from the HDMI-CEC page
    s = to_rail_row(CEC)
    if os.environ.get("HAS_CEC") == "0":
        check("no HDMI-CEC row on a model whose profile has none", CEC not in titles(s["rows"]), ", ".join(titles(s["rows"])))
        print("SKIP the HDMI-CEC page walk: the model's profile has no settings.cec")
        if not direct:      # Play 6: its profile has neither row, only the HDMI app's old address opens the page
            print("SKIP the profile has no HDMI page entry (settings.hdmi and settings.cec both off)")
    elif check("All settings lists the HDMI-CEC row", CEC in titles(s["rows"]), ", ".join(titles(s["rows"]))):
        cec_section = page(s)
        key(INTO)
        focus_row(CEC)
        key("ok", 2.5)
        cec = page(state())
        if not check("OK opens the HDMI-CEC page", cec not in ("", cec_section), state()["menu"]):
            return finish()
        check("DOWN reaches the HDMI page row of the CEC page", focus_row(HDMI), state()["focus"])
        key("ok", 2.5)
        st = hdmi_page_checks("HDMI-CEC page")
        if not direct:
            port_checks(st)
        key("back", 1.5)
        check("BACK returns to the HDMI-CEC page", page(state()) == cec, state()["menu"])
        key("back", 1.5)
        check("BACK again returns to the section", page(state()) == cec_section, state()["menu"])
    return finish()


def finish():
    for _ in range(5):
        if "showing=false" in state()["menu"]:
            break
        key("back", 1.2)
    check("BACK closes the menu at the end", "showing=false" in state()["menu"], state()["menu"])
    if os.environ.get("WALK_DEBUG"):
        print(state()["raw"])
    sh("am force-stop " + PKG)
    print("%s %s: %s" % (os.path.basename(sys.argv[0])[:-3], PKG, "PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
