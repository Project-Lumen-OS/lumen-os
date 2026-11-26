#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""resume_key.py: the vendor's STR resume key in the key layouts of every model (one rule, per-family data).

The stock power HAL of every MTK family (mediatek.hardware.power-service apm_thread_func) ends each STR resume with
gmpf_resume, sys.xgimi.str and sendPowerKeyToAndroid: one EV_MSC SCAN 255 + EV_KEY 116 (KEY_POWER) press written into
the input device "MTK Smart TV IR Receiver" (Vendor_3697_Product_0001.kl). Stock PhoneWindowManager takes it as the
wake ("STR resume, always wake up" while XgimiWindowManager.mSystemActive is false: Z9X 6.15.62, X50 1.3.38 and RS30
G0155 services.jar). Lumen's BT remote power keys (113 KPPOWER, 185 SECONDARY_POWER: STB_POWER WAKE) wake Android on
the press, so the vendor's key lands about 7 s later on an awake projector: as POWER it slept again (X50 beta16), as
STB_POWER it is a press of the projector app's power key (Z9X, HB, RS 20: past the app's 1.5 s wake guard and the wake
curtain it opens the power menu or sleeps). The rule: IR 116 = KEYCODE_WAKEUP, which wakes a sleeping Android and does
nothing while awake. It lives in the base layout every image installs (overlay/v1/keylayout); the X50 family's image
base, the published 1.0.9 layouts, predates it and gets it from devices/g0111/image/x50_power_keys.py RESUME.
No remote power key is this one (IR 190 / 75, keypad 30 / 116 / 260, BT 113 / 185, c081 / af02 116 keep their lines).

Per-family data (FAMILIES), only where the stock differs: the Play 6 (MStar mt5889) power HAL sends no key on resume
(no sendPowerKeyToAndroid, no input event table), so its IR 116 stays the user power key it was (STB_POWER WAKE).
Test: tools/devices/test/resume_key_test.py (replay of the captured wake per family).

    resume_key.py apply KL_DIR FAMILY   set the IR 116 line of KL_DIR for FAMILY, in place (inode, mode, label kept)
    resume_key.py check KL_DIR FAMILY   KL_DIR has FAMILY's IR 116 line
Exit 0 on success; every problem is printed.
"""
import os
import re
import sys

IR = "Vendor_3697_Product_0001.kl"      # "MTK Smart TV IR Receiver" (dumpsys input, X50 2026-11-26)
SCANCODE = 116                          # KEY_POWER of the power HAL's event table
WAKE = "WAKEUP"
USER = "STB_POWER WAKE"                 # the projector app's power key (the line before this rule)
NOTE = "  # XGIMI: POWER, sent by the vendor power HAL after every STR resume; wake only (devices/_image/resume_key.py)"
# family -> (its stock power HAL sends the resume key, evidence: stock mediatek.hardware.power-service sha256 prefix and
# the offset of its EV_KEY 116 down event, MSC_SCAN 255 before it)
FAMILIES = {
    "z9x": (True, "G0082 v6.15.19 / 6.15.58 / 6.15.62: 96f09d53 @0x3ef8"),
    "rs20": (True, "v1.6.50 / 1.6.55: 96f09d53 @0x3ef8 (= Z9X), v1.6.52: 1c5a4cd1 @0x3e88"),
    "hb": (True, "G0130: c24ca389, G0148: ffdb44b8 @0x2c90 (arm32)"),
    "x50": (True, "G0111 v1.2.64: 4ce049ba, v1.3.38: 8339556f @0x4328"),
    "titan": (True, "H0062 v1.2.54 / 1.2.99: c5a1afe4 / e15afda4, H0063 v1.3.38: 8339556f @0x4328"),
    "rs30": (True, "G0155 / G0169: b2135eea, G0170: c7085bf3 @0x36ac (arm32)"),
    "mira": (True, "MIRA / AURA v1.3.1: befdb23d @0x36ac (arm32)"),
    "play6": (False, "G0058 v1.7.116 / 1.8.127: 7c030c42 (MStar): no sendPowerKeyToAndroid, no event table"),
}
LINE = re.compile(r"^key([ \t]+)%d([ \t]+)([^#]*?)[ \t]*(#.*)?$" % SCANCODE)


def line(family, sp1=" ", sp2="        "):
    """The IR 116 line of a family."""
    return "key%s%d%s%s" % (sp1, SCANCODE, sp2, WAKE + NOTE if FAMILIES[family][0] else USER)


def edit(text, family):
    """(new text, problems): the IR layout with FAMILY's 116 line; the base may carry either value."""
    if family not in FAMILIES:
        return text, ["unknown family %s (one of %s)" % (family, " ".join(sorted(FAMILIES)))]
    out, bad, n = [], [], 0
    for l in text.split("\n"):
        m = LINE.match(l)
        if m:
            n += 1
            if m.group(3).split() not in ([WAKE], USER.split()):
                bad.append("%s: key %d is '%s', expected %s or %s" % (IR, SCANCODE, m.group(3), WAKE, USER))
            l = line(family, m.group(1), m.group(2))
        out.append(l)
    if n != 1:
        bad.append("%s: %d lines for key %d, expected one" % (IR, n, SCANCODE))
    return "\n".join(out), bad


def apply(d, family):
    p = os.path.join(d, IR)
    if not os.path.isfile(p):
        return ["missing " + p]
    new, bad = edit(open(p, encoding="utf-8").read(), family)
    if not bad:
        with open(p, "r+", encoding="utf-8") as f:       # in place: inode, owner, mode and label stay the base's
            f.truncate(0)
            f.write(new)
    return bad


def check(d, family):
    p = os.path.join(d, IR)
    if not os.path.isfile(p):
        return ["missing " + p]
    text = open(p, encoding="utf-8").read()
    new, bad = edit(text, family)
    if not bad and new != text:
        bad.append("%s: key %d is not %s's line '%s'" % (IR, SCANCODE, family, line(family).strip()))
    return bad


def main(a):
    if len(a) == 3 and a[0] in ("apply", "check"):
        bad = (apply if a[0] == "apply" else check)(a[1], a[2])
    else:
        print((__doc__ or "usage: see the source").strip(), file=sys.stderr)
        return 2
    for b in bad:
        print("resume_key: ERROR " + b)
    print("resume_key: " + ("OK, %s %s: key %d %s" % (a[0], a[2], SCANCODE, WAKE if FAMILIES[a[2]][0] else USER)
                            if not bad else "FAILED (%d)" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
