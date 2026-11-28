#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""HB 3.5 mm output wiring (source level, 2026-11-10, 4PDA: HDMI sound only on the speaker, no Headphones row):
the jack row in the panel rows, the jack kept connected by AudioOutputChoice.apply, the HB reporter as a pure vendor
adapter of lumen-audio PathSwitch (switch_end=4 with headphone_volume stored first is the shared rule) and the jack row
of the HB chip volume table."""
import glob, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
HB = os.path.abspath(os.path.join(HERE, "..", ".."))

# the one projector app: an HB source is in this folder (or the assembled tree of
# build.sh), in the HB family adapter or in the core
GSI_ROOT = os.environ.get("LUMEN_GSI") or os.path.abspath(os.path.join(HB, "..", ".."))
SRC_BASES = (HB, os.path.join(GSI_ROOT, "apps/LumenProjector/vendor/xgimi/aidl-gmpf"),
             os.path.join(GSI_ROOT, "apps/LumenProjector/core"))


def src_of(rel):
    for base in SRC_BASES:
        f = os.path.join(base, "src", rel)
        if os.path.exists(f):
            return f
    return os.path.join(HB, "src", rel)


def src_glob(pattern):
    seen = {}
    for base in SRC_BASES:
        for f in glob.glob(os.path.join(base, "src", pattern)):
            seen.setdefault(os.path.basename(f), f)
    return list(seen.values())

A = src_of("org/lumen/projector/audio")
n = bad = 0


def ok(what, cond):
    global n, bad
    n += 1
    if not cond:
        bad += 1
        print("FAIL " + what)


def body(src, sig):
    i = src.find(sig)
    if i < 0: return ""
    j = src.find("{", i); depth = 0
    for k in range(j, len(src)):
        if src[k] == "{": depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0: return src[i:k + 1]
    return ""


def rd(name):
    return open(os.path.join(A, name), encoding="utf-8").read()


def core(name):
    """A lumen-audio source: the module in the repository, or its copy in the assembled tree of build.sh."""
    dirs = [os.path.join(HB, "../core/lumen-audio/src/org/lumen/audio"), src_of("org/lumen/audio")]
    if os.environ.get("LUMEN_GSI"):
        dirs.append(os.path.join(os.environ["LUMEN_GSI"], "apps/core/lumen-audio/src/org/lumen/audio"))
    for d in dirs:
        f = os.path.join(d, name)
        if os.path.exists(f):
            return open(f, encoding="utf-8").read()
    raise SystemExit("check_wiring: lumen-audio " + name + " not found")


def first(*paths):
    """The first of the files that exists: the repository place, then the place in the assembled tree."""
    for f in paths:
        if os.path.exists(f):
            return open(f, encoding="utf-8").read()
    raise SystemExit("check_wiring: none of " + ", ".join(paths))


opt = rd("HbOutputOptions.java")
jack = open(src_of("org/lumen/projector/audio/HbWiredJack.java"), encoding="utf-8").read()
# one AudioOutputChoice in the core since the one projector app; the HB jack is its family's (Family.jackKeep ...)
aoc = first(os.path.join(HB, "../LumenProjector/core/src/org/lumen/projector/audio/AudioOutputChoice.java"),
            os.path.join(A, "AudioOutputChoice.java"))
# one AudioPathReporter in the core since the one projector app; the HB's way to register is its family adapter
apr = first(os.path.join(HB, "../LumenProjector/core/src/org/lumen/projector/audio/AudioPathReporter.java"),
            os.path.join(A, "AudioPathReporter.java"))
fam = first(os.path.join(HB, "../LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/Family.java"),
            src_of("org/lumen/projector/Family.java"))

# ---- rows
ok("panel rows ask for the jack row", "HbOutputRows.kinds(connected, known, chosen, HbWiredJack.offered())" in opt)
ok("jack row behind HbFeatures WIRED_HEADPHONE", "HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE)" in body(jack, "static boolean offered()"))

# ---- AudioOutputChoice.apply: the jack kept first (connected for good, role), then the device; Auto from AutoOutput
ap = body(aoc, "private static void apply(String why)")
i_keep = ap.find("if (Family.jackKeep(am, media, k, why)) return;")
i_find = ap.find("route(am, media, app, k, why)")
ok("apply keeps the jack before resolving the device, and returns after connecting", 0 <= i_keep < i_find)
ok("apply never removes the jack (no HEADSET_PLUG 0: players do not pause)", "connect(am, false" not in aoc
   and "setWiredDeviceConnectionState" not in aoc)
rt = body(aoc, "private static boolean route(")
ok("route: Auto from AutoOutput without the jack", "AutoOutput.device(am, Family.JACK_SENSED)" in rt
   and "public static final boolean JACK_SENSED = false;" in fam)
ok("the HB family keeps and settles its jack", "return org.lumen.projector.audio.HbWiredJack.keep(am, media, chosen, why);" in fam
   and "org.lumen.projector.audio.HbWiredJack.settle(am, media, chosen, why);" in fam)
ok("apply: the jack role settled after the preferred device", "if (route(am, media, app, k, why)) Family.jackSettle(am, media, k, why);" in ap)

# ---- HbWiredJack: the HB adapter of lumen-audio JackOutput
jo = core("JackOutput.java")
kp = body(jack, "public static boolean keep(") + body(jack, "public static void settle(")
ok("jack keep / settle: only where the projector has the jack, chosen = Headphones",
   "return offered() && JackOutput.keep(am, media, chosen == AudioOutputKind.WIRED, why);" in kp
   and "if (offered()) JackOutput.settle(am, media, chosen == AudioOutputKind.WIRED, why);" in kp)
ok("jack not sensed on the HB", "static final boolean SENSED = false;" in jack)
ok("JackOutput connects TYPE_WIRED_HEADPHONES as an output, once", "AudioDeviceInfo.TYPE_WIRED_HEADPHONES" in jo
   and "ROLE_OUTPUT" in jo and jo.count("setWiredDeviceConnectionState(") == 1 and "setWiredDeviceConnectionState(jack(), 1)" in jo)
ok("JackOutput listed covers headphones and headset", "TYPE_WIRED_HEADSET" in jo)

# ---- the path switch and the levels are lumen-audio PathSwitch / StockVolume (one implementation for every model):
# this reporter is only the HB vendor adapter, the jack's path and key come from the HB chip volume table
ok("reporter installs the shared path switch with itself as the vendor", "PathSwitch.install(sApp, VENDOR)" in apr
   and "implements VendorAudio" in apr)
ok("reporter: boot through PathSwitch.onBootCompleted", "PathSwitch.onBootCompleted(ctx, VENDOR)" in apr)
ok("reporter: the device callback at once on the HB family (its recipe drops AudioPorts)", "if (am != null) am.registerAudioDeviceCallback(cb, h);" in fam)
ok("reporter keeps no own level or sink code", all(w not in apr for w in
   ("setParameters", "switch_end", "HalSink", "JackVolume", "BtVolume", "DspOwned", "HdmiVolume")))
ok("no HB HdmiVolume fork left", not os.path.exists(src_of("org/lumen/projector/source/HdmiVolume.java")))
ps = core("PathSwitch.java")
ok("the shared switch ends every path with its level stored first",
   0 <= body(ps, "private static void sink(int path, String why)").find("StockVolume.store(path")
   < body(ps, "private static void sink(int path, String why)").find("HalSink.end(am, ticket, path, path, why)"))
TB = os.path.join(HB, "../../devices/goat_elegant/audio/chip_volume.tsv")
if not os.path.exists(TB):                    # the assembled tree of build.sh
    TB = os.path.join(os.environ["LUMEN_GSI"], "devices/goat_elegant/audio/chip_volume.tsv")
tb = open(TB, encoding="utf-8").read()
ok("HB table: the jack types on headphone_volume, path 4",
   re.search(r"^out\s+3\s+headphone_volume\s+-\s+4", tb, re.M) and re.search(r"^out\s+4\s+headphone_volume\s+-\s+4", tb, re.M))
ok("HB recipe ships its table", "file      devices/goat_elegant/audio/chip_volume.tsv assets/lumen_audio/chip_volume.tsv" in
   open(os.path.join(HB, "../../devices/goat_elegant/app/HbProjector.layers"), encoding="utf-8").read()
   if os.path.exists(os.path.join(HB, "../../devices/goat_elegant/app/HbProjector.layers")) else True)
RC = os.path.join(HB, "../../devices/goat_elegant/image/hb_bt.rc")
if not os.path.exists(RC):                    # the assembled tree of build.sh
    RC = os.path.join(os.environ["LUMEN_GSI"], "devices/goat_elegant/image/hb_bt.rc")
rc = open(RC, encoding="utf-8").read()
ok("hb_bt.rc keeps the stock 'no AVRCP absolute volume' at early-boot, off only by the user's stock switch",
   re.search(r"on early-boot\n    setprop persist\.bluetooth\.disableabsvol true\n", rc) is not None
   and rc.count("disableabsvol false") == 1
   and "on early-boot && property:persist.lumen.bt_absvol=1\n    setprop persist.bluetooth.disableabsvol false\n" in rc
   and rc.index("disableabsvol true") < rc.index("disableabsvol false"))

if bad:
    print("check_wiring (audio): %d of %d FAILED" % (bad, n)); sys.exit(1)
print("check_wiring (audio): %d checks passed" % n)
