#!/usr/bin/env python3
"""Key walk of the casting page on an emulator (test APKs of test/ui/build.sh, never a projector).

    python3 cast_walk.py SERIAL APPDIR OUT     (APPDIR apps/LumenAirPlay, OUT the build dir; adb from $ADB)

The old addresses (Miracast org.lumen.miracast/.SettingsActivity, AirPlay action org.lumen.airplay.SETTINGS) open the
casting page and nothing of their own; the rows come in the order of the cards and carry the cards' names (AirPlay,
Miracast, the code on the screen, ask before connecting, the device name), the Miracast picture row sits under its
Miracast caption and the licences close the list; OK on a switch flips it in the receiver and the focus stays on the
row, OK / RIGHT and LEFT step a choice row and LEFT undoes RIGHT, BACK leaves the page. One PASS/FAIL line per check,
screenshots OUT/cast_top.png and OUT/cast_end.png, exit 1 on any failure."""
import glob
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
SERIAL, APPDIR, OUT = sys.argv[1:4]
KEY = {"up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "back": 4}
CAST = "org.lumen.airplay/org.lumen.airplay.CastActivity"
FAILS = []


def strings():
    raw = {}
    for f in glob.glob(os.path.join(OUT, "tree", "airplay", "res", "values", "*.xml")):
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(f, encoding="utf-8").read(), re.S):
            raw[m.group(1)] = m.group(2).replace("\\'", "'")
    def res(v, n=0):
        return res(raw[v[8:]], n + 1) if v.startswith("@string/") and n < 5 else v
    return {k: res(v) for k, v in raw.items()}


S = strings()


def adb(*a, timeout=60):
    return subprocess.run([ADB, "-s", SERIAL] + list(a), capture_output=True, text=True, timeout=timeout).stdout


def sh(cmd):
    return adb("shell", cmd)


def key(n, wait=0.8):
    sh("input keyevent %d" % KEY[n])
    time.sleep(wait)


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else ": " + detail))
    if not ok:
        FAILS.append(name)


def front():
    return " ".join(l for l in sh("dumpsys window").splitlines() if "mCurrentFocus" in l)


def dump():
    for _ in range(3):
        x = sh("uiautomator dump /sdcard/cw.xml >/dev/null 2>&1; cat /sdcard/cw.xml")
        if "<hierarchy" in x:
            return ET.fromstring(x[x.index("<hierarchy"):])
        time.sleep(1)
    return ET.fromstring("<hierarchy/>")


def row_of(n):
    texts = [t.get("text") for t in n.iter("node") if t.get("class") == "android.widget.TextView" and t.get("text")]
    descs = [t.get("content-desc") for t in n.iter("node") if t.get("content-desc")]
    return (texts[0] if texts else None, texts[1] if len(texts) > 1 else None, descs[0] if descs else None)


def focused():
    for n in dump().iter("node"):
        if n.get("focused") == "true" and n.get("class") == "android.widget.LinearLayout":
            return row_of(n)
    return (None, None, None)


def screen_texts():
    return [n.get("text") for n in dump().iter("node") if n.get("text")]


def walk_labels():
    """DOWN from the first row to the last: the labels in order."""
    seen, last = [], None
    for _ in range(40):
        f = focused()
        if f == last:
            break
        seen.append(f[0])
        last = f
        key("down", 0.6)
    return seen


def to_row(label, nth=1):
    for _ in range(40):
        key("up", 0.3)
    hit = 0
    for _ in range(40):
        f = focused()
        if f[0] == label:
            hit += 1
            if hit == nth:
                return f
        key("down", 0.5)
    return None


def main():
    sh("am force-stop org.lumen.airplay")
    sh("am start -n org.lumen.miracast/.SettingsActivity")
    time.sleep(4)
    check("the old Miracast settings address opens the casting page", CAST in front(), front())
    key("back", 1.5)
    sh("am start -a org.lumen.airplay.SETTINGS")
    time.sleep(4)
    check("the old AirPlay settings address opens the casting page", CAST in front(), front())
    air, mira = S["lumen_airplay"], S["cast_miracast"]
    f = focused()
    check("the focus starts on the AirPlay row", f[0] == air, str(f))
    subprocess.run("%s -s %s exec-out screencap -p > %s/cast_top.png" % (ADB, SERIAL, OUT), shell=True)
    texts = screen_texts()
    check("AirPlay: one name on the row and the card", texts.count(air) >= 2, str(texts.count(air)))
    check("Miracast: one name on the row and the card", texts.count(mira) >= 2, str(texts.count(mira)))
    labels = walk_labels()
    subprocess.run("%s -s %s exec-out screencap -p > %s/cast_end.png" % (ADB, SERIAL, OUT), shell=True)
    want = [air, mira, S["cast_row_code"], S["cast_row_ask"], S["set_name"]]
    check("rows in the order of the cards: " + " / ".join(want), labels[:5] == want, str(labels))
    check("the Miracast picture row is there", S["set_quality"] in labels[5:], str(labels))
    check("the licences close the list", labels and labels[-1] == S["cast_row_licences"], str(labels[-3:]))
    check("no Screen mirroring row and no AirPlay settings row (old names)",
          not {"Screen mirroring", "AirPlay settings"} & set(labels), str(labels))
    # switches: the receiver takes the change, the focus stays on the row
    for label in (mira, S["cast_row_ask"], air):
        f0 = to_row(label)
        key("ok", 2.5)
        f1 = focused()
        check(label + ": OK flips the switch, the focus stays", f0 and f1[0] == label and f1[2] and f1[2] != f0[2],
              "%s -> %s" % (f0, f1))
        key("ok", 2.5)
        f2 = focused()
        check(label + ": OK again restores it", f0 and f2[0] == label and f2[2] == f0[2], "%s -> %s" % (f0, f2))
    # choice rows: RIGHT / OK step forward, LEFT back
    f0 = to_row(S["cast_row_code"])
    key("right", 1.2)
    f1 = focused()
    check("code on the screen: RIGHT takes the next choice", f0 and f1[1] == S["access_pin"] and f0[1] == S["access_off"],
          "%s -> %s" % (f0, f1))
    key("left", 1.2)
    check("code on the screen: LEFT goes back", f0 and focused()[1] == f0[1])
    nth = labels.count(S["set_quality"])
    f0 = to_row(S["set_quality"], nth)
    key("ok", 2.5)
    f1 = focused()
    check("Miracast picture: OK takes the next quality in the receiver", f0 and f1[1] and f1[1] != f0[1],
          "%s -> %s" % (f0, f1))
    key("left", 2.5)
    check("Miracast picture: LEFT goes back", f0 and focused()[1] == f0[1], str(focused()))
    key("back", 1.5)
    check("BACK leaves the casting page", CAST not in front(), front())
    print("cast_walk: " + ("PASS" if not FAILS else "FAIL (%d)" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
