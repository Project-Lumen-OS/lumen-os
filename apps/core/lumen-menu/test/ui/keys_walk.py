#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Remote buttons key walk on an EMULATOR (never a user's projector): every key of the XGIMI remote key table that a
user may give an app reaches the Remote buttons screen while it waits for a key, and binds.

  python3 apps/core/lumen-menu/test/ui/keys_walk.py SERIAL PACKAGE      (run_keys.sh builds and installs the APK)

The keys come from the data: each XGIMI remote row of devices/_remotes/keys.tsv with bind=app, resolved through the
remote's own Lumen key layout (overlay/v1/keylayout, the scancode's line), so a layout that maps a key to a code the
screen never sees (the Shortcut key as FEATURED_APP_1, eaten by PhoneWindowManager) turns this red. Per key: OK on
"Assign a button" waits, the key opens its app list (target = that key), DOWN and OK give it the first choice, the
list shows it bound. One PASS/FAIL line per check, exit 1 on any failure.
"""
import os
import re
import sys
import time

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "remotes"))
sys.path.insert(0, HERE)
import gen_catalog  # noqa: E402
import general_walk as gw  # noqa: E402  (same SERIAL PACKAGE arguments, its way to the screen)

SCREEN = "org.lumen.remote.keys.KeyBindActivity"
# On the images every table key is a global key: PhoneWindowManager hands it to the projector app after
# interceptSystemKeysAndShortcuts, the stage that ate FEATURED_APP_1, and this walk sends each key through that same
# stage. The SDK emulator is a user build without the Lumen global keys, so a key also passes interceptKeyBeforeQueueing
# there, which drops these when they are not global keys (seen on the emulator walk): they cannot be sent there.
QUEUE_DROPS = {"MACRO_1", "MACRO_2", "MACRO_3", "MACRO_4"}


def table_keys():
    """(remote, scancode, KEYCODE name, value) of every bindable XGIMI remote key, as its Lumen layout maps it."""
    remotes = {r["id"]: r for r in gen_catalog.read_table()}
    out, seen = [], set()
    for r in gen_catalog.read_keys(remotes=list(remotes.values())):
        if r["bind"] != "app" or r["remote"] not in remotes or not r["remote"].startswith("xgimi_"):
            continue
        vid, pid = remotes[r["remote"]]["vid_pid"].split(":")
        kl = os.path.join(G, "overlay", "v1", "keylayout", "Vendor_%s_Product_%s.kl" % (vid, pid))
        m = re.search(r"^key\s+%s\s+(\S+)" % r["scancode"], open(kl, encoding="utf-8").read(), re.M)
        name = m.group(1) if m else "-"
        out.append((r["remote"], r["scancode"], name, gen_catalog.KEYCODES.get(name)))
    return out


def screen():
    t = gw.sh("dumpsys activity %s/%s" % (gw.PKG, SCREEN))
    m = re.search(r"keys\.screen mode=(\S+) target=(-?\d+) focus=(\d+) note=(\S+) bound=(\S*)", t)
    if not m:
        return {}
    return {"mode": m.group(1), "target": int(m.group(2)), "focus": int(m.group(3)), "note": m.group(4),
            "bound": [int(x) for x in m.group(5).split(",") if x]}


def press(code, wait=1.2):
    gw.sh("input keyevent %d" % code)
    time.sleep(wait)


def main():
    keys = table_keys()
    gw.check("the table names bindable XGIMI remote keys", len(keys) > 0)
    for remote, sc, name, value in keys:
        gw.check("%s %s: its layout maps it to a known key code (%s)" % (remote, sc, name), value is not None)
    for name in sorted({n for _, _, n, _ in keys if n in QUEUE_DROPS}):
        print("SKIP KEYCODE_%s: dropped before the screen on the emulator only (QUEUE_DROPS)" % name)
    codes = sorted({v for _, _, n, v in keys if v is not None and n not in QUEUE_DROPS})
    if not gw.check("the Remote buttons screen opens", gw.open_buttons()):
        return 1
    left = screen().get("bound", [])
    if left:                                    # binds of an earlier run: "Reset all" is the last row of the list
        for _ in range(len(left) + 1):
            gw.key("down", 0.5)
        gw.key("ok", 1.5)
    gw.check("the walk starts with no key bound", not screen().get("bound"), str(screen()))
    for code in codes:
        who = ", ".join("%s %s" % (r, sc) for r, sc, _, v in keys if v == code)
        name = [n for _, _, n, v in keys if v == code][0]
        for _ in range(12):
            s = screen()
            if s.get("mode") == "LIST" and s.get("focus") == 0:
                break
            gw.key("up", 0.6)
        gw.key("ok", 1.2)
        s = screen()
        gw.check("KEYCODE_%s: Assign a button waits for a key" % name, s.get("mode") == "CAPTURE", str(s))
        press(code)
        s = screen()
        if not gw.check("KEYCODE_%s (%s) reaches the screen and opens its app list" % (name, who),
                        s.get("mode") == "PICK" and s.get("target") == code, str(s)):
            gw.key("back", 1.0)
            continue
        gw.key("down", 0.6)
        gw.key("ok", 1.5)
        s = screen()
        gw.check("KEYCODE_%s binds (the list shows it)" % name, s.get("mode") == "LIST" and code in s.get("bound", []),
                 str(s))
    s = screen()
    gw.check("every bindable key of the table is bound at the end", sorted(s.get("bound", [])) == codes,
             "%s vs %s" % (s.get("bound"), codes))
    gw.sh("am force-stop " + gw.PKG)
    gw.sh("pm clear " + gw.PKG)
    print("keys_walk %s: %s" % (gw.PKG, "PASS" if not gw.FAILS else "FAIL %d" % len(gw.FAILS)))
    return 1 if gw.FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
