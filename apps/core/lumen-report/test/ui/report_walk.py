#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Key walk of the problem report screen on an EMULATOR (never a user's projector), with the test APK of
test/ui/build.sh and the fake server of test/ui/fake_server.py (trusted by the test APK only). For each language and theme it opens the screen and
ASSERTS by D-pad keys: the focus starts on Send report, the details page opens from its row and Back returns to that
row, Send collects and uploads with a moving progress and Cancel focused, the code LR-4AD5M6 shows with Done focused,
Back returns to the start with the code as the last report, the switch flips with OK both ways, a refused report shows
why with Try again focused, an unreachable server gives the offline (queued) screen, Back on the start leaves, and the
page is drawn in the theme asked (field and pane pixels). One screenshot per state into OUT.
    python3 report_walk.py SERIAL PKG GSI OUT MODEFILE GOOD_URL DOWN_URL [lang:theme ...]   (default ru:light ru:dark en:light en:dark)"""
import os
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import walk_guard  # noqa: E402  (the fail-closed check before every send)

SERIAL, PKG, G, OUT, MODE, GOOD, DOWN = sys.argv[1:8]
COMBOS = sys.argv[8:] or ["ru:light", "ru:dark", "en:light", "en:dark"]
ADB = os.environ.get("ADB", "adb")
ACT = PKG + "/org.lumen.report.ReportActivity"
bad = 0


def adb(*a, out=False):
    r = subprocess.run([ADB, "-s", SERIAL] + list(a), stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return r.stdout if out else r.stdout.decode("utf-8", "replace")


def strings(lang):
    sfx = "" if lang == "en" else "-" + lang
    out = {}
    for f in ("apps/core/lumen-report/res/values%s/lumen_strings_report.xml",
              "apps/core/lumen-common-strings/res/values%s/lumen_strings.xml"):
        for base in ("", sfx):
            p = os.path.join(G, f % base)
            if os.path.isfile(p):
                for k, v in re.findall(r'<string name="([a-z0-9_]+)">(.*?)</string>', open(p, encoding="utf-8").read(), re.S):
                    out[k] = v.replace("\\'", "'").replace("&amp;", "&")
    for k, v in list(out.items()):
        if v.startswith("@string/"):
            out[k] = out.get(v[len("@string/"):], v)
    return out


def check(ok, what):
    global bad
    print(("ok   " if ok else "FAIL ") + what, flush=True)
    if not ok:
        bad += 1


def dump():
    for _ in range(3):
        x = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        i, j = x.find("<?xml"), x.rfind("</hierarchy>")
        if i >= 0 and j > i:
            return ET.fromstring(x[i:j + len("</hierarchy>")])
        time.sleep(0.5)
    return ET.fromstring("<hierarchy/>")


def texts(n):
    return " ".join(t for t in (e.get("text") for e in n.iter()) if t)


def focused(root):
    for n in root.iter("node"):
        if n.get("focused") == "true":
            return n
    return None


def state():
    r = dump()
    f = focused(r)
    return r, (texts(f) if f is not None else ""), (f.get("checked") if f is not None else None), texts(r)


def wait(pred, timeout, what):
    end = time.time() + timeout
    s = state()
    while time.time() < end:
        if pred(s):
            return s
        time.sleep(0.4)
        s = state()
    check(False, what + " (focused: %r)" % s[1])
    return s


def key(k, n=1):
    for _ in range(n):
        adb("shell", "input", "keyevent", k)
        time.sleep(0.35)


def shot(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", out=True))


def pixel(x, y):
    raw = adb("exec-out", "screencap", out=True)
    w, h = struct.unpack("<II", raw[:8])
    hdr = len(raw) - w * h * 4
    o = hdr + (y * w + x) * 4
    return raw[o], raw[o + 1], raw[o + 2]


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def url(u):
    """Points the app at the fake server (GOOD) or at a closed port (DOWN); stops the walk before any Send unless
    walk_guard.check proves it (local https, read back, the fake server answers or nothing listens)."""
    adb("shell", "setprop", "persist.lumen.report.url", u)
    got = adb("shell", "getprop", "persist.lumen.report.url").strip()
    why = walk_guard.check(u, got, "fake" if u == GOOD else "none")
    if why:
        print("STOP " + why)
        sys.exit(2)


def walk(lang, theme):
    S = strings(lang)
    tag = "%s_%s" % (lang, theme)
    print("== %s" % tag, flush=True)
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "pm", "clear", PKG)
    adb("shell", "settings", "put", "secure", "lumen_ui_theme", theme)
    adb("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "ru-RU" if lang == "ru" else "en-US")
    url(GOOD)
    open(MODE, "w").write("ok")
    # a TV is never in touch mode; a rooted phone image starts in it: one D-pad key before the screen opens puts the
    # emulator in the TV's key mode (the start focus is part of what is checked)
    key("KEYCODE_DPAD_DOWN")
    adb("shell", "am", "start", "-W", "-n", ACT)
    wait(lambda s: S["lumen_report_send"] in s[1], 15, tag + ": the focus starts on Send report")
    check(S["lumen_report_heading"] in state()[3], tag + ": the title of the page")
    field, pane = pixel(60, 540), pixel(1800, 540)
    if theme == "dark":
        check(lum(field) < 40 and lum(pane) < 30, tag + ": dark field %s, pane %s" % (field, pane))
    else:
        check(lum(field) > 150 and lum(pane) > 140, tag + ": light field %s, pane %s" % (field, pane))
    key("KEYCODE_DPAD_DOWN", 2)
    wait(lambda s: S["lumen_report_what"] in s[1], 5, tag + ": DOWN DOWN reaches What is in a report")
    key("KEYCODE_DPAD_CENTER")
    s = wait(lambda s: S["lumen_report_back"] in s[1], 5, tag + ": the details page opens, Back focused")
    check(S["lumen_report_inc_logs"] in s[3] and S["lumen_report_exc_accounts"] in s[3], tag + ": details list both sides")
    time.sleep(0.6)
    shot("details_" + tag)
    key("KEYCODE_BACK")
    wait(lambda s: S["lumen_report_what"] in s[1], 5, tag + ": BACK returns to the What is in a report row")
    key("KEYCODE_DPAD_UP", 2)
    wait(lambda s: S["lumen_report_send"] in s[1], 5, tag + ": UP UP back on Send report")
    key("KEYCODE_DPAD_CENTER")
    time.sleep(1.2)
    shot("sending_" + tag)
    a = adb("exec-out", "screencap", out=True)
    time.sleep(0.5)
    b = adb("exec-out", "screencap", out=True)
    check(a != b, tag + ": the progress screen keeps moving while it waits (two frames 0.5 s apart differ)")
    s = state()
    check(S["lumen_report_cancel"] in s[1], tag + ": sending shows progress with Cancel focused (focused: %r)" % s[1])
    check(S["lumen_report_collecting"] in s[3] or S["lumen_report_sending"] in s[3], tag + ": progress title")
    end = time.time() + 120
    while time.time() < end and "LR-4AD5M6" not in s[3]:
        time.sleep(0.5)
        s = state()
    s = wait(lambda s: "LR-4AD5M6" in s[3] and S["lumen_report_done"] in s[1], 5, tag + ": the code with Done focused")
    check(S["lumen_report_code"] in s[3], tag + ": the code label")
    time.sleep(0.6)
    shot("done_" + tag)
    key("KEYCODE_BACK")
    s = wait(lambda s: S["lumen_report_send"] in s[1], 5, tag + ": BACK on the code returns to Send report")
    check("LR-4AD5M6" in s[3] and S["lumen_report_last"] in s[3], tag + ": the start names the last report")
    time.sleep(0.6)
    shot("start_" + tag)
    key("KEYCODE_DPAD_DOWN")
    s = wait(lambda s: S["lumen_report_auto"] in s[1], 5, tag + ": DOWN reaches Send automatically")
    was = s[2]
    check(was == "true", tag + ": automatic reports are on by default")
    key("KEYCODE_DPAD_CENTER")
    s = wait(lambda s: s[2] is not None and s[2] != was, 5, tag + ": OK turns the switch off")
    check(S["lumen_report_auto"] in s[1], tag + ": the focus stays on the switch row")
    time.sleep(0.4)
    shot("auto_off_" + tag)
    key("KEYCODE_DPAD_CENTER")
    wait(lambda s: s[2] == was, 5, tag + ": OK turns it on again")
    url(GOOD)                                          # the fake server again before the refused send
    open(MODE, "w").write("reject")
    key("KEYCODE_DPAD_UP")
    wait(lambda s: S["lumen_report_send"] in s[1], 5, tag + ": UP back on Send report")
    key("KEYCODE_DPAD_CENTER")
    s = wait(lambda s: S["lumen_report_retry"] in s[1], 120, tag + ": a refused report shows Try again focused")
    check(S["lumen_report_failed_title"] in s[3] and S["lumen_report_err_rejected"] in s[3], tag + ": failed says why")
    time.sleep(0.6)
    shot("failed_" + tag)
    key("KEYCODE_BACK")
    wait(lambda s: S["lumen_report_send"] in s[1], 5, tag + ": BACK on failed returns to Send report")
    url(DOWN)
    key("KEYCODE_DPAD_CENTER")
    s = wait(lambda s: S["lumen_report_queued_title"] in s[3], 120, tag + ": no server: the offline (queued) screen")
    check(S["lumen_report_close"] in s[1], tag + ": offline has Close focused")
    time.sleep(0.6)
    shot("offline_" + tag)
    key("KEYCODE_BACK")
    wait(lambda s: S["lumen_report_send"] in s[1], 5, tag + ": BACK on offline returns to Send report")
    url(GOOD)
    key("KEYCODE_BACK")
    time.sleep(1.0)
    cur = adb("shell", "dumpsys", "window", "|", "grep", "mCurrentFocus")
    check("ReportActivity" not in cur, tag + ": BACK on the start leaves the screen")


os.makedirs(OUT, exist_ok=True)
adb("root")
time.sleep(3)
adb("wait-for-device")
for c in COMBOS:
    lang, theme = c.split(":")
    walk(lang, theme)
print("report walk: %d failed" % bad)
sys.exit(1 if bad else 0)
