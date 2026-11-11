#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""x50_power_keys.py: the X50's power keys in the key layouts of the image (devices/g0111/README.md, "Power key").

The Z9X base maps every user power and sleep key to KEYCODE_STB_POWER WAKE, a global key that the framework RRO of
the Z9X sends to org.lumen.projector/.KeyReceiver (own standby). The X50 image has no org.lumen.projector, so those
keys did nothing (the design notes/B16). The stock X50 has these scancodes as the system power key
(vendor .kl: POWER or XGIMI's KPPOWER), and its PhoneWindowManager ends a short press in a plain
PowerManager.goToSleep (XgimiWindowManager.xgimiGotoSleep, no projector HAL call). So the X50 image turns exactly
those lines back into KEYCODE_POWER WAKE; apps/X50PowerOverlay makes the framework sleep on a short press and open
the power menu on a long one.

    x50_power_keys.py apply KL_DIR      edit the .kl files of KL_DIR in place (inode, owner, mode, label kept)
    x50_power_keys.py check BASE_DIR NEW_DIR
                                        NEW_DIR must be BASE_DIR with exactly these edits and nothing else
    x50_power_keys.py files             the file names that change, one per line

Exit 0 on success; every problem is printed. Refuses a base that does not have every expected line (another base
needs this table checked again).
"""
import os
import re
import sys

# file -> scancodes that must be "STB_POWER WAKE" in the base and become "POWER WAKE"; the stock X50 label of the
# same scancode in parentheses (dump ~/devices/g0111_mt9681/work/fs/vendor/usr/keylayout, v1.2.64)
TABLE = {
    "Vendor_3697_Product_0001.kl": (116, 190, 75),   # IR: 116 POWER, 190 KPPOWER, 75 DORMANCY (sleep key)
    "Vendor_3697_Product_0002.kl": (30, 116, 260),   # body keypad: 30 POWER, 116 POWER, 260 KPPOWER
    "Vendor_000d_Product_3838.kl": (113,),           # XGIMI BT remotes: 113 KPPOWER
    "Vendor_000d_Product_3840.kl": (113,),           # the X50's own remote "XGIMI RC Keyboard" (000d:3840)
    "Vendor_000d_Product_3841.kl": (113,),
    "Vendor_000d_Product_3842.kl": (113,),
    "Vendor_000d_Product_3843.kl": (113,),
    "Vendor_1d5a_Product_c081.kl": (116,),           # other XGIMI BT remotes: 116 KPPOWER
    "Vendor_26e3_Product_af02.kl": (116,),
    "Vendor_0001_Product_0001.kl": (116, 183),       # TV-style remote: 116 POWER, 183 KPPOWER
    "Generic.kl": (142,),                            # KEY_SLEEP of any other keyboard (stock Generic: SLEEP)
}
KEY = re.compile(r"^key[ \t]+(\d+)([ \t]+)STB_POWER([ \t]+WAKE)([ \t#].*)?$")
NOTE = "  # X50: system power key (devices/g0111/image/x50_power_keys.py)"


def edit(text, name):
    """Return (new text, problems) for one file."""
    want = set(TABLE[name])
    out, seen, bad = [], set(), []
    for line in text.split("\n"):
        m = KEY.match(line)
        if m and int(m.group(1)) in want:
            sc = int(m.group(1))
            if sc in seen:
                bad.append("%s: scancode %d twice" % (name, sc))
            seen.add(sc)
            line = "key %s%sPOWER%s%s" % (m.group(1), m.group(2), m.group(3), NOTE)
        elif m:
            bad.append("%s: key %s is STB_POWER but not in the X50 table" % (name, m.group(1)))
        out.append(line)
    for sc in sorted(want - seen):
        bad.append("%s: no 'key %d STB_POWER WAKE' line in the base" % (name, sc))
    return "\n".join(out), bad


def apply(d):
    bad = []
    for name in sorted(TABLE):
        p = os.path.join(d, name)
        if not os.path.isfile(p):
            bad.append("missing " + p)
            continue
        new, b = edit(open(p, encoding="utf-8").read(), name)
        bad += b
        if not b:
            with open(p, "r+", encoding="utf-8") as f:   # in place: inode, owner, mode and label stay the base's
                f.truncate(0)
                f.write(new)
    # no other file of the folder may keep STB_POWER (it would go to the absent org.lumen.projector)
    for name in sorted(os.listdir(d)):
        if name.endswith(".kl") and name not in TABLE and "STB_POWER" in strip_comments(os.path.join(d, name)):
            bad.append("%s maps STB_POWER, not in the X50 table" % name)
    return bad


def strip_comments(p):
    return "\n".join(l.split("#", 1)[0] for l in open(p, encoding="utf-8"))


def check(base, new):
    bad = []
    names = sorted(n for n in set(os.listdir(base)) | set(os.listdir(new)) if n.endswith(".kl"))
    for name in names:
        pb, pn = os.path.join(base, name), os.path.join(new, name)
        if not (os.path.isfile(pb) and os.path.isfile(pn)):
            bad.append("%s is not in both trees" % name)
            continue
        tb, tn = open(pb, encoding="utf-8").read(), open(pn, encoding="utf-8").read()
        if name not in TABLE:
            if tb != tn:
                bad.append("%s differs from the base but is not in the X50 table" % name)
            continue
        want, b = edit(tb, name)
        bad += b
        if tn != want:
            bad.append("%s is not the base with exactly the X50 power-key edit" % name)
    for name in names:
        p = os.path.join(new, name)
        if os.path.isfile(p) and re.search(r"(?m)^key[ \t]+\d+[ \t]+STB_POWER\b", strip_comments(p)):
            bad.append("%s still maps STB_POWER" % name)
    return bad


def main(a):
    if a == ["files"]:
        print("\n".join(sorted(TABLE)))
        return 0
    if len(a) == 2 and a[0] == "apply":
        bad = apply(a[1])
        what = "applied to %d files" % len(TABLE)
    elif len(a) == 3 and a[0] == "check":
        bad = check(a[1], a[2])
        what = "%d files are the base with the power-key edit, the rest unchanged" % len(TABLE)
    else:
        print((__doc__ or "usage: see the source").strip(), file=sys.stderr)
        return 2
    for b in bad:
        print("x50_power_keys: ERROR " + b)
    print("x50_power_keys: " + ("OK, " + what if not bad else "FAILED (%d)" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
