#!/usr/bin/env python3
"""Key layouts of the XGIMI remotes against their stock files: the gear hold.

The XGIMI remotes detect a held gear themselves and send the stock MENU scancode once (BT 127 = KEY_COMPOSE on the
"XGIMI RC Keyboard" 000d:3841 and its siblings, IR / keypad 139); stock KEYCODE_MENU = the game menu. Lumen maps it
to its own gear-hold key (KEYCODE_TV_MEDIA_CONTEXT_MENU, Z9xKeys.GEAR_HOLD) routed to org.lumen.projector, whose
KeyReceiver runs the gear long press. A stock MENU scancode mapped to ALL_APPS (Lumen Home "All apps" on a held
gear) or to anything else fails here, as does a stale "four-diamond" note.

Stock labels: overlay/keylayout/<name>.kl.orig (the stock file) where it exists, else the "# XGIMI: <stock label>"
note make_keylayouts.py wrote from the stock file.

  python3 tools/devices/test/keylayout_gear_test.py [--self-test]
"""
import os, re, sys

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
GEAR_HOLD = "TV_MEDIA_CONTEXT_MENU"
LAYOUTS = ["overlay/v1/keylayout", "overlay/play6/keylayout"]
GLOBAL_KEYS = ["apps/Z9xFrameworkKeysOverlay/res/xml/global_keys.xml", "apps/common/overlay/xml/global_keys.xml",
               "apps/HbFrameworkOverlay/res/xml/global_keys.xml", "apps/Play6FrameworkOverlay/res/xml/global_keys.xml"]
RECEIVERS = ["apps/Z9xProjector/src/org/lumen/projector", "apps/HbProjector/src/org/lumen/projector",
             "apps/Play6Projector/src/org/lumen/projector"]
# the eye guard each of those apps runs: the HB app takes the Z9X one (no fork; its short gear press on the mask is
# the profile's keys.eye_gear_short)
EYE_GUARDS = {"apps/HbProjector/src/org/lumen/projector": "apps/Z9xProjector/src/org/lumen/projector"}
LINE = re.compile(r"^key\s+(\d+)\s+(\S+)(.*)$")


def parse(text):
    """{scancode: (label, note)} of the key lines (usage lines and comments skipped)."""
    out = {}
    for l in text.splitlines():
        m = LINE.match(l.strip())
        if m:
            note = m.group(3).split("#", 1)[1].strip() if "#" in m.group(3) else ""
            out[int(m.group(1))] = (m.group(2), note)
    return out


def stock_labels(name, lumen):
    orig = os.path.join(G, "overlay/keylayout", name + ".orig")
    if os.path.exists(orig):
        return {k: v[0] for k, v in parse(open(orig, encoding="utf-8").read()).items()}, "stock file"
    st = {}
    for k, (_, note) in lumen.items():
        m = re.match(r"XGIMI: ([A-Z0-9_]+)", note)
        if m:
            st[k] = m.group(1)
    return st, "XGIMI notes"


def check_layout(name, text, errs):
    lumen = parse(text)
    stock, src = stock_labels(name, lumen)
    menus = 0
    for k, lab in sorted(stock.items()):
        if lab != "MENU" or k not in lumen:
            continue
        menus += 1
        got = lumen[k][0]
        if got == "ALL_APPS":
            errs.append("%s: key %d is stock MENU (the gear held, %s) but maps to ALL_APPS: a held gear would open "
                        "All apps instead of the game menu" % (name, k, src))
        elif got != GEAR_HOLD:
            errs.append("%s: key %d is stock MENU (%s) but maps to %s, not the gear hold %s" % (name, k, src, got, GEAR_HOLD))
    for k, (lab, note) in lumen.items():
        if "four-diamond" in note:
            errs.append("%s: key %d note names a four-diamond key (the remote has none)" % (name, k))
        if lab == GEAR_HOLD and stock.get(k) != "MENU":
            errs.append("%s: key %d maps to the gear hold but its stock label is %s, not MENU" % (name, k, stock.get(k)))
    return menus


def main():
    errs, files, menus = [], 0, 0
    for d in LAYOUTS:
        for f in sorted(os.listdir(os.path.join(G, d))):
            if not f.endswith(".kl") or f == "Generic.kl":
                continue
            files += 1
            menus += check_layout(f, open(os.path.join(G, d, f), encoding="utf-8").read(), errs)
    # the Z9X remote itself: gear short 66 = SETTINGS, gear held 127 = the gear hold, as stock (MOVE_HOME / MENU)
    for d in LAYOUTS:
        z = parse(open(os.path.join(G, d, "Vendor_000d_Product_3841.kl"), encoding="utf-8").read())
        if z.get(66, ("",))[0] != "SETTINGS":
            errs.append("%s/Vendor_000d_Product_3841.kl: key 66 (gear) is not SETTINGS" % d)
        if z.get(127, ("",))[0] != GEAR_HOLD:
            errs.append("%s/Vendor_000d_Product_3841.kl: key 127 (gear held) is not %s" % (d, GEAR_HOLD))
    for p in GLOBAL_KEYS:
        t = open(os.path.join(G, p), encoding="utf-8").read()
        if '<key keyCode="KEYCODE_%s" component="org.lumen.projector/.KeyReceiver" />' % GEAR_HOLD not in t:
            errs.append("%s: KEYCODE_%s is not routed to org.lumen.projector/.KeyReceiver" % (p, GEAR_HOLD))
    for p in ["apps/common/projector/src/org/lumen/projector/Z9xKeys.java", "apps/Z9xProjector/src/org/lumen/projector/Z9xKeys.java"]:
        if "static final int GEAR_HOLD = KeyEvent.KEYCODE_%s;" % GEAR_HOLD not in open(os.path.join(G, p), encoding="utf-8").read():
            errs.append("%s: Z9xKeys.GEAR_HOLD is not KEYCODE_%s" % (p, GEAR_HOLD))
    for d in RECEIVERS:
        kr = open(os.path.join(G, d, "KeyReceiver.java"), encoding="utf-8").read()
        if not re.search(r"if \(code == Z9xKeys\.GEAR_HOLD\) \{\s*//[^\n]*\n\s*if \(firstDown && !intent\.getBooleanExtra\("
                         r"EXTRA_BEGAN_FROM_NON_INTERACTIVE, false\)\) gearLong\(ctx\);", kr):
            errs.append("%s/KeyReceiver.java: Z9xKeys.GEAR_HOLD does not run the gear long press" % d)
        if kr.count("gearLong(ctx);") != 2:
            errs.append("%s/KeyReceiver.java: the held gear and the gear hold do not share gearLong" % d)
        eg = open(os.path.join(G, EYE_GUARDS.get(d, d), "eye/EyeGuard.java"), encoding="utf-8").read()
        # the gear press itself is the shared org.lumen.menu.GearPress (lumen-menu): no app keeps its own copy
        for name, t in (("KeyReceiver.java", kr), ("eye/EyeGuard.java", eg)):
            if re.search(r"sGearLongFired|sGearDownTime|sGearDown\b", t) \
                    or not re.search(r"(GEAR|sGear)\.down\(ev\.getRepeatCount\(\), ev\.getDownTime\(\)\)", t) \
                    or not re.search(r"(GEAR|sGear)\.up\(ev\.getDownTime\(\), ev\.isCanceled\(\)\)", t):
                errs.append("%s/%s: the gear key does not run through org.lumen.menu.GearPress" % (d, name))
        if 'restoreByUser("gear", shortGearTurnsOff());' not in eg \
                or "MenuController.profile(sApp).eyeGearShortOff()" not in eg:
            errs.append("%s/eye/EyeGuard.java: the short gear press on the mask does not follow keys.eye_gear_short" % d)
        if "private static final int KEYCODE_GEAR_HOLD = KeyEvent.KEYCODE_%s;" % GEAR_HOLD not in eg \
                or 'if (ev.getKeyCode() == KEYCODE_GEAR_HOLD) {' not in eg:
            errs.append("%s/eye/EyeGuard.java: the gear hold does not lift the eye-protection mask like a held gear" % d)
    if menus < 9:
        errs.append("only %d stock MENU keys found (want the 9 XGIMI remotes)" % menus)
    for e in errs:
        print("FAIL " + e)
    if errs:
        return 1
    print("keylayout gear: %d layouts, %d stock MENU keys = the gear hold, routed and handled on 3 apps - ok" % (files, menus))
    return 0


def self_test():
    """The check itself catches the live bug: stock MENU mapped to ALL_APPS, and the four-diamond note."""
    errs = []
    check_layout("Vendor_000d_Product_3841.kl", 'key 127        ALL_APPS  # XGIMI: MENU (four-diamond key) -> Google TV "Your apps"\n', errs)
    ok = any("maps to ALL_APPS" in e for e in errs) and any("four-diamond" in e for e in errs)
    errs2 = []
    check_layout("Vendor_X.kl", "key 139        HOME  # XGIMI: MENU\n", errs2)
    ok = ok and any("not the gear hold" in e for e in errs2)
    print("keylayout gear self-test: " + ("ok" if ok else "FAIL (the check misses a stock MENU key mapped to ALL_APPS)"))
    return 0 if ok else 1


if __name__ == "__main__":
    rc = self_test()
    sys.exit(rc or (0 if "--self-test" in sys.argv else main()))
