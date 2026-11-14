#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""hb_audio_mutate.py: mutation check of the HB slider flicker fix (panel/SlideCoalescer and its wiring in ProPicture /
HbMenuFeatures / SlideMain) and the 3.5 mm output (HbAudioRules jack rules, HbOutputRows jack row, HbWiredJack,
AudioOutputChoice.apply order, AudioPathReporter switch_end=4, HbHeadphoneVolume, HbFeatures WIRED_HEADPHONE). Each
mutant breaks one rule in a scratch copy of apps/HbProjector; test/slider, test/audio and test/features must turn red,
and the unmutated copy must pass.  python3 tools/devices/test/hb_audio_mutate.py   (MUT_ONLY="sc_ ar_" to filter)"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
P = "src/org/lumen/projector/panel/"
A = "src/org/lumen/projector/audio/"
SC, SM, PP, QP = P + "SlideCoalescer.java", P + "SlideMain.java", P + "ProPicture.java", P + "HbMenuFeatures.java"
QP_HbPictureRows = 'src/org/lumen/projector/panel/HbPictureRows.java'
AR, ROWS, OPT, AOC = A + "HbAudioRules.java", A + "HbOutputRows.java", A + "HbOutputOptions.java", A + "AudioOutputChoice.java"
APR, HP, JACK = A + "AudioPathReporter.java", A + "HbHeadphoneVolume.java", A + "HbWiredJack.java"
FEAT = "src/org/lumen/projector/hal/HbFeatures.java"
MUT = [
    # ---- SlideCoalescer (flicker on a fast slide)
    ("sc_stock_gap_50", SC, "STOCK_GAP_MS = 100L;", "STOCK_GAP_MS = 50L;"),
    ("sc_no_gap", SC, "Math.max(0, lastStart + minGapMs - clock.now())", "0"),
    ("sc_oldest_wins", SC, "        pending = value;\n", "        if (!hasPending) pending = value;\n"),
    # both guards of the one write in flight (each alone is redundant with the other)
    ("sc_two_in_flight", SC, [("if (inFlight || waiting) return;            // the newest value waits",
                               "if (waiting) return;            // the newest value waits"),
                              ("        if (inFlight || !hasPending) return;", "        if (!hasPending) return;")], None),
    ("sc_idle_never", SC, "        sch.cancel(idle);\n        sch.post(idle, settleMs);\n    }", "        sch.cancel(idle);\n    }"),
    ("sc_rewrite_same", SC, "!inFlight && !waiting && !hasPending && lastKnown && lastWritten == value", "false"),
    ("sc_forget_noop", SC, "    public void forget() {\n        lastKnown = false;", "    public void forget() {\n        lastKnown = lastKnown;"),
    ("sc_failed_kept", SC, "if (!ok || timedOut) lastKnown = false;", ""),
    ("sc_no_stuck", SC, "        sch.post(stuck, stuckMs);\n", ""),
    ("sc_late_answer", SC, "if (my != gen || !inFlight) return;", "if (!inFlight) return;"),
    # ---- slider wiring
    ("pp_commit_only", PP, "                row.setTag(android.os.SystemClock.uptimeMillis());\n                safe(\"slider\", () -> c.submit(value)).run();",
     "                row.setTag(android.os.SystemClock.uptimeMillis());"),
    ("pp_read_while_sliding", PP, "        if (sliding()) return;                                  // HB fork", "        // HB fork"),
    ("pp_no_forget", PP, "        for (SlideCoalescer c : slides) c.forget();", ""),
    ("pp_no_register", PP, "        slides.add(c);\n", ""),
    ("mf_pq_no_range", QP_HbPictureRows, "        if (v < PQ_MIN || v > PQ_MAX) { refuse(cb, null); return; }\n", ""),
    ("mf_pq_wrong_item", QP_HbPictureRows, 'case "contrast": it = PqItem.CONTRAST; break;', 'case "contrast": it = PqItem.BRIGHTNESS; break;'),
    ("sm_fire_and_forget", SM, "        Hal.query((g, g2) -> {", "        Hal.run((g, g2) -> {"),
    ("sm_ok_on_null", SM, "boolean ok = Boolean.TRUE.equals(r);", "boolean ok = !Boolean.FALSE.equals(r);"),
    # ---- the 3.5 mm rules
    ("ar_connect_when_listed", AR, "return wiredListed ? WiredOutputStep.NONE : WiredOutputStep.CONNECT;", "return WiredOutputStep.CONNECT;"),
    ("ar_remove_real_jack", AR, "return ours && wiredListed ? WiredOutputStep.DISCONNECT", "return wiredListed ? WiredOutputStep.DISCONNECT"),
    ("ar_never_remove", AR, "return ours && wiredListed ? WiredOutputStep.DISCONNECT : WiredOutputStep.NONE;", "return WiredOutputStep.NONE;"),
    ("ar_no_interim", AR, "(\"AUTO\".equals(chosen) || chosen == null)", "(chosen == null)"),
    ("ar_hp_unconfirmed", AR, "return target == PATH_WIRED && confirmed == PATH_WIRED;", "return target == PATH_WIRED;"),
    ("ar_store_bt_too", AR, "    public static boolean storeHeadphoneBeforeEnd(int path) {\n        return path == PATH_WIRED;",
     "    public static boolean storeHeadphoneBeforeEnd(int path) {\n        return path >= PATH_BT;"),
    ("ar_sink_bt_only", AR, "(halSink == PATH_BT || halSink == PATH_WIRED) && target != halSink", "halSink == PATH_BT && target != halSink"),
    ("ar_wire_5", AR, "PATH_WIRED = 4;", "PATH_WIRED = 5;"),
    ("ar_hp_resend", AR, "        return speakerVolume(wired, music, sent, force);", "        return speakerVolume(wired, music, -1, force);"),
    ("rows_no_jack", ROWS, "(WIRED.equals(k) && wiredJack)", "false"),
    ("rows_jack_everywhere", ROWS, "(WIRED.equals(k) && wiredJack)", "wiredJack"),
    ("rows_3arg_jack", ROWS, "return kinds(connected, soundbarKnown, chosen, false);", "return kinds(connected, soundbarKnown, chosen, true);"),
    ("opt_no_jack", OPT, "HbOutputRows.kinds(connected, known, chosen, HbWiredJack.offered())", "HbOutputRows.kinds(connected, known, chosen, false)"),
    # ---- the 3.5 mm wiring
    ("aoc_no_wait", AOC, "                HbWiredJack.connect(am, true, why);\n                return;", "                HbWiredJack.connect(am, true, why);"),
    ("aoc_no_interim", AOC, "                d = find(am, AudioOutputKind.SPEAKER, \"\");", ""),
    ("aoc_remove_first", AOC, "            boolean ok = am.setPreferredDeviceForStrategy(media, want);",
     "            if (jack == WiredOutputStep.DISCONNECT) HbWiredJack.connect(am, false, why);\n            boolean ok = am.setPreferredDeviceForStrategy(media, want);"),
    ("aoc_never_remove_unlisted", AOC, "                if (jack == WiredOutputStep.DISCONNECT) HbWiredJack.connect(am, false, why);\n                return;\n            }\n            AudioDeviceAttributes want",
     "                return;\n            }\n            AudioDeviceAttributes want"),
    ("jack_headset_type", JACK, "                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, \"\");", "                    AudioDeviceInfo.TYPE_WIRED_HEADSET, \"\");"),
    ("jack_not_ours", JACK, "            sOurs = on;\n", ""),
    ("jack_ungated", JACK, "return HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE);", "return true;"),
    ("apr_no_hp_branch", APR, "} else if (HbAudioRules.storeHeadphoneBeforeEnd(target)) {", "} else if (false) {"),
    ("apr_sink_bt_only", APR, "} else if (HbAudioRules.speakerSinkAfter(sHalSink, target)) {", "} else if (sHalSink == AudioPath.BT_A2DP.wire) {"),
    ("apr_no_hp_install", APR, "            org.lumen.projector.Ui.main().post(() -> HbHeadphoneVolume.install(sApp));   // HB fork 2026-11-10\n", ""),
    ("hp_end_without_level", HP, "        if (!send(am, v)) {", "        if (false) {"),
    ("hp_unconfirmed", HP, "HbAudioRules.headphoneConfirmed(AudioPathReporter.targetPath(), AudioPathReporter.confirmedPath())",
     "AudioPathReporter.targetPath() == HbAudioRules.PATH_WIRED"),
    ("hp_wrong_key", HP, "am.setParameters(\"headphone_volume=\" + v);", "am.setParameters(\"spk_volume=\" + v);"),
    ("feat_off", FEAT, "WIRED_HEADPHONE(State.ON, \"support_wired_headphone\", \"false\"),", "WIRED_HEADPHONE(State.OFF, \"support_wired_headphone\", \"false\"),"),
    ("feat_key", FEAT, "WIRED_HEADPHONE(State.ON, \"support_wired_headphone\", \"false\"),", "WIRED_HEADPHONE(State.ON, \"support_wired_headphones\", \"false\"),"),
]
SUITES = ["test/slider/run.sh", "test/audio/run.sh", "test/features/run.sh"]


def tree(dst):
    shutil.copytree(os.path.join(G, "apps/HbProjector"), os.path.join(dst, "apps/HbProjector"),
                    ignore=shutil.ignore_patterns("build"))


def run(root):
    for s in SUITES:
        r = subprocess.run(["sh", os.path.join(root, "apps/HbProjector", s)], capture_output=True, text=True)
        if r.returncode != 0: return False
    return True


only = os.environ.get("MUT_ONLY", "").split()
w = tempfile.mkdtemp(prefix="hb_audiomut.")
try:
    base = os.path.join(w, "base"); tree(base)
    if not run(base):
        print("BROKEN: the unmutated copy fails"); sys.exit(2)
    killed = survived = 0
    for name, rel, old, new in MUT:
        if only and not any(name.startswith(o) for o in only): continue
        t = os.path.join(w, name); tree(t)
        f = os.path.join(t, "apps/HbProjector", rel); s = open(f, encoding="utf-8").read()
        for o, nw in (old if isinstance(old, list) else [(old, new)]):
            if s.count(o) != 1:
                print("BROKEN mutant %s: anchor found %d times" % (name, s.count(o))); sys.exit(2)
            s = s.replace(o, nw)
        open(f, "w", encoding="utf-8").write(s)
        if run(t): survived += 1; print("SURVIVED " + name)
        else: killed += 1; print("killed   " + name)
        shutil.rmtree(t)
    print("hb_audio_mutate: %d killed, %d survived" % (killed, survived))
    sys.exit(1 if survived else 0)
finally:
    shutil.rmtree(w, ignore_errors=True)
