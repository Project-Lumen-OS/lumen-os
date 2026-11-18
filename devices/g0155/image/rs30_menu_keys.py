#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""rs30_menu_keys.py: the RS30 Ultra Max remote's picture keys in the image's key layout (devices/g0155/README.md).

The RS30's B870C remote (HID 000d:3840, stock vendor/usr/keylayout/Vendor_000d_Product_3840.kl of v1.1.42) sends three
picture keys the X50's remote does not have; Lumen's base layout leaves those scancodes unmapped. Stock opens a picture
page with each: 172 PICTURE_MODE (Settings image/mode), 217 PRO_SETTING
(image/mode_setting), 234 BRIGHTNESS (brightness/brightness_misckey). This step adds them, after x50_power_keys.py, as
keycodes no other line of any Lumen key layout sends (so the X50 overlay entries that route them to
org.lumen.x50.projector/.KeyReceiver change nothing on the X50): the picture mode popup, the whole picture page, the
light level bar. SHORTCUT_1 / SHORTCUT_2 (135 / 136, stock user-assigned custom keys) are MACRO_1 / MACRO_2 of
"Remote buttons" (x50_power_keys.py, devices/_remotes/keys.tsv), not this step's.

    rs30_menu_keys.py apply KL_DIR      edit the .kl file of KL_DIR in place (inode, owner, mode, label kept)
    rs30_menu_keys.py check BASE_DIR NEW_DIR
                                        NEW_DIR must be BASE_DIR with exactly these lines added and nothing else
    rs30_menu_keys.py lines             the added lines, one per line (the recipe's line checks)

Exit 0 on success; every problem is printed. Refuses a base that already maps one of the scancodes.
"""
import os
import re
import sys

FILE = "Vendor_000d_Product_3840.kl"
NOTE = " -> org.lumen.x50.projector (devices/g0155/image/rs30_menu_keys.py)"
# scancode -> (Android key, stock label and what Lumen opens)
ADD = (
    (172, "TV_ZOOM_MODE", "XGIMI PICTURE_MODE, the picture mode popup"),
    (217, "TV_TIMER_PROGRAMMING", "XGIMI PRO_SETTING, the whole picture page"),
    (234, "TV_NUMBER_ENTRY", "XGIMI BRIGHTNESS, the light level bar"),
)
ANY_KEY = re.compile(r"^key[ \t]+(\d+)[ \t]")
KEYCODE = re.compile(r"^key[ \t]+\d+[ \t]+(\S+)")


def lines():
    return ["key %d %s  # %s%s" % (sc, key, why, NOTE) for sc, key, why in ADD]


def edit(text):
    """Return (new text, problems)."""
    out = text.split("\n")
    bad = []
    mapped = {int(m.group(1)) for m in (ANY_KEY.match(l.split("#", 1)[0]) for l in out) if m}
    tail = []
    while out and out[-1] == "":
        tail.append(out.pop())
    for (sc, key, why), line in zip(ADD, lines()):
        if sc in mapped:
            bad.append("%s: scancode %d already mapped in the base (the RS30 addition expects it absent)" % (FILE, sc))
            continue
        out.append(line)
    return "\n".join(out + tail), bad


def keycodes(d):
    """{keycode label: [file, ...]} of every .kl line of folder d (comments stripped)."""
    seen = {}
    for name in sorted(os.listdir(d)):
        if not name.endswith(".kl"):
            continue
        for l in open(os.path.join(d, name), encoding="utf-8"):
            m = KEYCODE.match(l.split("#", 1)[0])
            if m:
                seen.setdefault(m.group(1), []).append(name)
    return seen


def unused(d):
    """The added keycodes must not be sent by any other line of the folder (else the global key steals it there)."""
    seen = keycodes(d)
    return ["%s is already sent by %s" % (key, ", ".join(seen[key])) for _, key, _ in ADD if key in seen]


def apply(d):
    p = os.path.join(d, FILE)
    if not os.path.isfile(p):
        return ["missing " + p]
    bad = unused(d)
    new, b = edit(open(p, encoding="utf-8").read())
    bad += b
    if not bad:
        with open(p, "r+", encoding="utf-8") as f:           # in place: inode, owner, mode and label stay the base's
            f.truncate(0)
            f.write(new)
    return bad


def check(base, new):
    bad = []
    names = sorted(n for n in set(os.listdir(base)) | set(os.listdir(new)) if n.endswith(".kl"))
    for name in names:
        pb, pn = os.path.join(base, name), os.path.join(new, name)
        if not (os.path.isfile(pb) and os.path.isfile(pn)):
            bad.append("%s is not in both trees" % name)
            continue
        tb, tn = open(pb, encoding="utf-8").read(), open(pn, encoding="utf-8").read()
        if name != FILE:
            if tb != tn:
                bad.append("%s differs from the base but is not the RS30 remote's layout" % name)
            continue
        want, b = edit(tb)
        bad += b
        if tn != want:
            bad.append("%s is not the base with exactly the RS30 picture keys added" % name)
    bad += unused(base)
    return bad


def main(a):
    if a == ["lines"]:
        print("\n".join(lines()))
        return 0
    if len(a) == 2 and a[0] == "apply":
        bad = apply(a[1])
        what = "%d picture keys added to %s" % (len(ADD), FILE)
    elif len(a) == 3 and a[0] == "check":
        bad = check(a[1], a[2])
        what = "%s is the base with the %d picture keys, the rest unchanged" % (FILE, len(ADD))
    else:
        print((__doc__ or "usage: see the source").strip(), file=sys.stderr)
        return 2
    for b in bad:
        print("rs30_menu_keys: ERROR " + b)
    print("rs30_menu_keys: " + ("OK, " + what if not bad else "FAILED (%d)" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
