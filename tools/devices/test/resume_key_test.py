#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Replay of the vendor's STR resume key on every family (devices/_image/resume_key.py, the one rule).

The captured wake (G0111 x50-1.0.10-beta16, devices/g0111/test/fixtures/x50_resume_key_1126.txt): the BT remote's
power key wakes Android at once (WAKE_REASON_WAKE_KEY), 7.3 s later the vendor power HAL ends its resume and writes
KEY_POWER 116 into "MTK Smart TV IR Receiver" (Vendor_3697_Product_0001.kl), and the awake projector took it as a power
key press and slept. Every MTK family's stock power HAL writes the same event after every resume (resume_key.FAMILIES).

For each family this test builds the key layouts as its image recipe does (the base layouts the recipe installs, then
the recipe's own edit: x50_power_keys.py for the X50 / TITAN / RS30 / MIRA images, resume_key.py for the Play 6) and
replays the capture through the framework rules that decide it (Lineage 21 PhoneWindowManager: STB_POWER = the
projector app's global power key while awake, wake while asleep with WAKE; POWER while awake = the family's
config_shortPressOnPowerBehavior; WAKEUP wakes and does nothing while awake; a scancode without a line does nothing).
The vendor's key must do nothing to an awake projector (no sleep, no press of the app's power key, whose own wake guard
is only PowerKey.WAKE_GUARD_MS) and still wake a sleeping one. Every remote power key keeps what it does: the BT power
on / power off pair of the XGIMI remotes (113 KPPOWER and 185 SECONDARY_POWER, stock kPPowerPress for both keycodes
2100 / 2130), the c081 / af02 power key (116 KPPOWER), the IR power and sleep keys (190 / 75), the keypad (30 / 116 /
260), Generic KEY_SLEEP (142).

  python3 tools/devices/test/resume_key_test.py
"""
import os
import re
import shutil
import sys
import tempfile

sys.dont_write_bytecode = True
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "devices", "_image"))
sys.path.insert(0, os.path.join(ROOT, "devices", "g0111", "image"))
import resume_key  # noqa: E402
import x50_power_keys  # noqa: E402

LOG = os.path.join(ROOT, "devices", "g0111", "test", "fixtures", "x50_resume_key_1126.txt")
V1 = os.path.join(ROOT, "overlay", "v1", "keylayout")
BASE109 = os.path.join(ROOT, "devices", "g0111", "test", "base109_keylayout")
POWERKEY = os.path.join(ROOT, "apps", "LumenProjector", "core", "src", "org", "lumen", "projector", "power", "PowerKey.java")
IR, KEYPAD = resume_key.IR, "Vendor_3697_Product_0002.kl"
VENDOR = (IR, resume_key.SCANCODE)
# family -> (descriptor whose recipe it is, base layouts, the recipe's edit, the framework overlay with the POWER rule)
FAM = {
    "z9x": ("devices/horse_popular/build.conf", V1, None, "apps/Z9xFrameworkKeysOverlay/res/values/config.xml"),
    "hb": ("devices/goat_elegant/build.conf", V1, None, "apps/HbFrameworkOverlay/res/values/config.xml"),
    "rs20": ("devices/_family/rs20/rs20.build.conf", V1, None, "apps/Z9xFrameworkKeysOverlay/res/values/config.xml"),
    "play6": ("devices/dragon_healthy/build.conf", V1, "resume_key", "apps/Play6FrameworkOverlay/res/values/config.xml"),
    "x50": ("devices/_family/x50/x50.build.conf", BASE109, "x50_power_keys", "apps/X50PowerOverlay/res/values/config.xml"),
    "titan": ("devices/_family/titan/titan.build.conf", BASE109, "x50_power_keys", "apps/X50PowerOverlay/res/values/config.xml"),
    "rs30": ("devices/_family/rs30/rs30.build.conf", V1, "x50_power_keys", "apps/X50PowerOverlay/res/values/config.xml"),
    "mira": ("devices/_family/mira/mira.build.conf", V1, "x50_power_keys", "apps/X50PowerOverlay/res/values/config.xml"),
}
SHORT = {0: "nothing", 1: "sleep"}
# remote power keys: (file, scancode) -> what a press does while awake, per app kind ("app" = the projector app's
# power key rule: short press per the user's setting, held = the other action). Unmapped = the line is absent.
BT_PAIR = [("Vendor_000d_Product_%s.kl" % p, sc) for p in ("3838", "3840", "3841", "3842") for sc in (113, 185)]
USER_KEYS = {
    "gmpf": dict([(k, "app" if k[1] == 113 else "nothing") for k in BT_PAIR]
                 + [(("Vendor_000d_Product_3843.kl", 113), "app"), (("Vendor_1d5a_Product_c081.kl", 116), "app"),
                    (("Vendor_1d5a_Product_c081.kl", 185), "nothing"), (("Vendor_26e3_Product_af02.kl", 116), "app"),
                    ((IR, 190), "app"), ((IR, 75), "app"), ((KEYPAD, 30), "app"), ((KEYPAD, 116), "app"),
                    ((KEYPAD, 260), "app")]),
    "x50": dict([(k, "app") for k in BT_PAIR]
                + [(("Vendor_000d_Product_3843.kl", 113), "app"), (("Vendor_1d5a_Product_c081.kl", 116), "sleep"),
                   (("Vendor_1d5a_Product_c081.kl", 185), "nothing"), (("Vendor_26e3_Product_af02.kl", 116), "sleep"),
                   ((IR, 190), "sleep"), ((IR, 75), "sleep"), ((KEYPAD, 30), "sleep"), ((KEYPAD, 116), "sleep"),
                   ((KEYPAD, 260), "sleep"), (("Generic.kl", 142), "sleep")]),
}

fails = []


def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        fails.append(name)


def recipe(conf):
    """The descriptor's text with its include= chain."""
    text = open(os.path.join(ROOT, conf), encoding="utf-8").read()
    for inc in re.findall(r"(?m)^include=(\S+)", text):
        text += "\n" + recipe(inc)
    return text


def steps_text(conf):
    s = re.findall(r"(?m)^steps=(\S+)", recipe(conf))     # the nearest descriptor's steps= wins (layered.py)
    return open(os.path.join(ROOT, s[0]), encoding="utf-8").read()


def layouts(fam):
    conf, base, tool, _ = FAM[fam]
    t = tempfile.mkdtemp(prefix="rk")
    kl = os.path.join(t, "kl")
    shutil.copytree(base, kl, ignore=shutil.ignore_patterns("SHA256SUMS", "README"))
    if tool == "x50_power_keys":
        bad = x50_power_keys.apply(kl)
    elif tool == "resume_key":
        bad = resume_key.apply(kl, fam)
    else:
        bad = []
    check("%s: the recipe's key layout edit runs clean %s" % (fam, bad), not bad)
    out = {}
    for n in os.listdir(kl):
        if n.endswith(".kl"):
            for line in open(os.path.join(kl, n), encoding="utf-8"):
                m = re.match(r"^key[ \t]+(\d+)[ \t]+(\S+)((?:[ \t]+[A-Z_]+)*)", line.split("#", 1)[0])
                if m:
                    out[(n, int(m.group(1)))] = (m.group(2), m.group(3).split())
    shutil.rmtree(t)
    return out


def recipe_shape(fam):
    """The test builds the layouts the way the recipe does: prove it from the recipe files."""
    conf, base, tool, _ = FAM[fam]
    text = recipe(conf)
    if base == V1:
        check("%s: %s installs overlay/v1/keylayout/%s" % (fam, conf, IR), "overlay/v1/keylayout/" + IR in text)
    else:
        check("%s: %s is composed from the published 1.0.9 image" % (fam, conf), "z9x-1.0.9" in text)
    st = steps_text(conf)
    if tool:
        script = tool + ".py"
        check("%s: its steps run %s apply on the image's key layouts" % (fam, script),
              re.search(r"(?m)\trun(_root)?\t%s\tapply\t\{sys\}/product/usr/keylayout" % re.escape(script), st) is not None)
    else:
        check("%s: its steps edit no key layout" % fam, "x50_power_keys.py\tapply" not in st and "resume_key.py\tapply" not in st)


def short_behavior(fam):
    m = re.search(r'<integer name="config_shortPressOnPowerBehavior">(\d+)</integer>',
                  open(os.path.join(ROOT, FAM[fam][3]), encoding="utf-8").read())
    return int(m.group(1)) if m else 0


def framework(kl, k, interactive, short):
    if k not in kl:
        return "nothing"
    key, flags = kl[k]
    if key == "STB_POWER":
        return "app" if interactive else ("wake" if "WAKE" in flags else "nothing")
    if key == "POWER":
        return SHORT.get(short, "other") if interactive else "wake"
    if key == "WAKEUP":
        return "nothing" if interactive else "wake"
    return "nothing"


def ts(line):
    m = re.match(r"^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d{3})", line)
    return ((int(m.group(1)) * 60 + int(m.group(2))) * 60 + int(m.group(3))) * 1000 + int(m.group(4))


def capture():
    lines = open(LOG, encoding="utf-8").read().splitlines()
    i_res = next(i for i, l in enumerate(lines) if "APM_NORMAL_RESUME" in l)
    i_wake = next(i for i, l in enumerate(lines) if i > i_res and "Waking up from Asleep" in l and "WAKE_KEY" in l)
    i_vendor = next(i for i, l in enumerate(lines) if i > i_wake and "sendPowerKeyToAndroid write event to" in l)
    check("fixture: the captured run slept on the vendor's key", any("Going to sleep due to power_button" in l
                                                                     for l in lines[i_vendor:]))
    return ts(lines[i_vendor]) - ts(lines[i_wake])


def main():
    delay = capture()
    guard = int(re.search(r"WAKE_GUARD_MS = (\d+)", open(POWERKEY, encoding="utf-8").read()).group(1))
    print("vendor key %d ms after the remote's wake, the projector app's wake guard %d ms" % (delay, guard))
    check("the capture's vendor key comes after the app's wake guard (a STB_POWER there is a real press)", delay > guard)
    check("x50_power_keys.py RESUME is the shared rule's key (no second rule)",
          x50_power_keys.RESUME == {resume_key.IR: (resume_key.SCANCODE,)})
    check("every family of this replay has a row in resume_key.FAMILIES and back", set(FAM) == set(resume_key.FAMILIES))
    base = resume_key.edit(open(os.path.join(V1, IR), encoding="utf-8").read(), "z9x")
    check("the base layout every image installs carries the rule (overlay/v1/keylayout/%s key %d WAKEUP)"
          % (IR, resume_key.SCANCODE), not base[1] and base[0] == open(os.path.join(V1, IR), encoding="utf-8").read())
    for fam in sorted(FAM):
        injects, why = resume_key.FAMILIES[fam]
        print("== %s (stock power HAL sends the resume key: %s; %s)" % (fam, "yes" if injects else "no", why))
        recipe_shape(fam)
        kl, short = layouts(fam), short_behavior(fam)
        kind = "x50" if FAM[fam][2] == "x50_power_keys" else "gmpf"
        v = kl.get(VENDOR, ("-", []))
        if injects:
            a = framework(kl, VENDOR, True, short)
            print("  vendor resume key: IR %d -> %s %s, Android awake -> %s" % (VENDOR[1], v[0], " ".join(v[1]), a))
            check("%s: the vendor's resume key does nothing to the awake projector (no sleep, no app power key press)" % fam,
                  a == "nothing")
            check("%s: the vendor's resume key wakes a sleeping Android (stock: it is the wake)" % fam,
                  framework(kl, VENDOR, False, short) == "wake")
        else:
            check("%s: stock sends no resume key, IR %d stays the user power key it was (%s)" % (fam, VENDOR[1], resume_key.USER),
                  v == ("STB_POWER", ["WAKE"]))
        if fam == "play6":   # pinned apart from the table: the Play 6 power HAL (MStar) has no resume key at all
            check("play6: IR %d is the user power key of every Lumen Play 6 image so far (STB_POWER WAKE)" % VENDOR[1],
                  v == ("STB_POWER", ["WAKE"]))
        for k, want in sorted(USER_KEYS[kind].items()):
            if k[0] == "Generic.kl" and kind != "x50":
                continue
            got = framework(kl, k, True, short)
            check("%s: remote key %s %d while awake -> %s" % (fam, k[0], k[1], want), got == want)
            if want != "nothing":
                check("%s: remote key %s %d while asleep -> wake" % (fam, k[0], k[1]), framework(kl, k, False, short) == "wake")
    print("resume_key_test: %s" % ("GREEN" if not fails else "RED (%d)" % len(fails)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
