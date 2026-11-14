#!/usr/bin/env python3
"""HB 3.5 mm output wiring (source level, 2026-11-10, 4PDA: HDMI sound only on the speaker, no Headphones row):
the jack row in the panel rows, the connect / remove order in AudioOutputChoice.apply, the stock switch_end=4 with
headphone_volume stored first after 59(4), the speaker sink after the jack, and the headphone level following
STREAM_MUSIC on the confirmed path 4."""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
HB = os.path.abspath(os.path.join(HERE, "..", ".."))
A = os.path.join(HB, "src/org/lumen/projector/audio")
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


aoc, opt, apr, hp, jack = rd("AudioOutputChoice.java"), rd("HbOutputOptions.java"), rd("AudioPathReporter.java"), \
    rd("HbHeadphoneVolume.java"), rd("HbWiredJack.java")

# ---- rows
ok("panel rows ask for the jack row", "HbOutputRows.kinds(connected, known, chosen, HbWiredJack.offered())" in opt)
ok("jack row behind HbFeatures WIRED_HEADPHONE", "HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE)" in body(jack, "static boolean offered()"))

# ---- AudioOutputChoice.apply: connect first and wait, interim speaker for Auto, remove after the new route
ap = body(aoc, "private static void apply(String why)")
i_step = ap.find("HbWiredJack.step(am, k)")
i_conn = ap.find("HbWiredJack.connect(am, true, why);")
i_find = ap.find("find(am, k, prefs(app)")
ok("apply asks the jack step before resolving the device", 0 <= i_step < i_conn < i_find)
ok("apply returns after connecting (devices added evaluates again)",
   re.search(r"HbWiredJack\.connect\(am, true, why\);\s*return;", ap) is not None)
ok("apply: Auto leaves the jack via the speaker", "HbAudioRules.speakerBeforeDisconnect(k.name(), jack)" in ap
   and 'find(am, AudioOutputKind.SPEAKER, "")' in ap)
i_set = ap.find("am.setPreferredDeviceForStrategy(media, want)")
i_rm = ap.rfind("HbWiredJack.connect(am, false, why)")
ok("apply removes the jack after the new preferred device", 0 <= i_set < i_rm)
dnull = body(ap, "if (d == null) {")
ok("apply removes the jack also when nothing is preferred (choice not connected, role removed)",
   re.search(r"WiredOutputStep\.DISCONNECT\) HbWiredJack\.connect\(am, false, why\);\s*return;\s*\}$", dnull) is not None)
seg = ap[ap.find("AudioDeviceAttributes want = new AudioDeviceAttributes(d);"):i_set]
ok("apply removes the jack before the new route only in the already-set branch",
   seg.count("HbWiredJack.connect(am, false, why)") == 1 and seg.count("return;") == 1)
ok("apply removes the jack when the preferred device was already set",
   re.search(r"already set\s*if \(jack == WiredOutputStep\.DISCONNECT\) HbWiredJack\.connect\(am, false, why\);", ap) is not None)

# ---- HbWiredJack: the AOSP wired connection of a headphone output
cn = body(jack, "static boolean connect(")
ok("jack connects TYPE_WIRED_HEADPHONES as an output", "AudioDeviceInfo.TYPE_WIRED_HEADPHONES" in cn and "ROLE_OUTPUT" in cn)
ok("jack uses setWiredDeviceConnectionState(on ? 1 : 0)", "am.setWiredDeviceConnectionState(jack, on ? 1 : 0)" in cn)
ok("jack remembers its own connection", "sOurs = on;" in cn)
st = body(jack, "static WiredOutputStep step(")
ok("jack step from the rules with listed + ours", "HbAudioRules.wiredStep(chosen.name(), listed(am), sOurs)" in st)
ok("jack listed covers headphones and headset", "TYPE_WIRED_HEADPHONES" in jack and "TYPE_WIRED_HEADSET" in body(jack, "static boolean listed("))

# ---- AudioPathReporter.halSink: switch_end=4 with headphone_volume first, speaker sink after the jack
hs = body(apr, "private static void halSink(int target)")
ok("halSink: path 4 -> HbHeadphoneVolume.selectSink", "HbAudioRules.storeHeadphoneBeforeEnd(target)" in hs
   and "HbHeadphoneVolume.selectSink(sApp" in hs)
ok("halSink: speaker sink after the jack or Bluetooth", "HbAudioRules.speakerSinkAfter(sHalSink, target)" in hs
   and "HdmiVolume.selectSpeakerSink(" in hs)
ok("halSink: Bluetooth branch kept first", hs.find("BT_A2DP.wire) {") < hs.find("storeHeadphoneBeforeEnd"))
ok("install posts HbHeadphoneVolume.install", "HbHeadphoneVolume.install(sApp)" in apr)
ok("WIRED still maps to path 4", re.search(r"TYPE_WIRED_HEADPHONES:\s*return AudioPath\.WIRED;", apr) is not None)

# ---- HbHeadphoneVolume
sel = body(hp, "static void selectSink(")
i_v = sel.find("send(am, v)")
i_e = sel.find('"xgimi_audiopath_switch_end=" + HbAudioRules.PATH_WIRED')
ok("selectSink stores headphone_volume before switch_end=4", 0 <= i_v < i_e)
ok("selectSink never ends without a stored level", re.search(r"if \(!send\(am, v\)\) \{[^}]*return;", sel, re.S) is not None)
fo = body(hp, "private static void follow(")
ok("headphone level follows only on the confirmed path 4",
   "HbAudioRules.headphoneConfirmed(AudioPathReporter.targetPath(), AudioPathReporter.confirmedPath())" in fo)
ok("headphone level from the rules", "HbAudioRules.headphoneVolume(true, musicLevel(am), sSent, force || entering)" in fo)
ok("headphone_volume key (stock setDspVolumesToHal)", 'am.setParameters("headphone_volume=" + v);' in body(hp, "private static boolean send("))
ok("music volume broadcast filtered to STREAM_MUSIC", "AudioManager.STREAM_MUSIC) return;" in hp)

if bad:
    print("check_wiring (audio): %d of %d FAILED" % (bad, n)); sys.exit(1)
print("check_wiring (audio): %d checks passed" % n)
