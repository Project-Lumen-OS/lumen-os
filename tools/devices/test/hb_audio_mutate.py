#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""hb_audio_mutate.py: mutation check of the HB slider flicker fix (panel/SlideCoalescer and its wiring in ProPicture /
HbMenuFeatures / SlideMain) and the 3.5 mm output (HbAudioRules jack rules, HbOutputRows jack row, HbWiredJack
over lumen-audio JackOutput, AudioOutputChoice.apply order, the AudioPathReporter adapter of the shared switch, HbFeatures WIRED_HEADPHONE; the
path switch and the chip levels are lumen-audio's, mutated by apps/core/lumen-audio/test/mutate.py). Each
mutant breaks one rule in a scratch copy of apps/HbProjector; test/slider, test/audio and test/features must turn red,
and the unmutated copy must pass.  python3 tools/devices/test/hb_audio_mutate.py   (MUT_ONLY="sc_ ar_" to filter)"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
P = "src/org/lumen/projector/panel/"
A = "src/org/lumen/projector/audio/"
SC, SM, PP, QP = P + "SlideCoalescer.java", P + "SlideMain.java", P + "ProPicture.java", P + "HbMenuFeatures.java"
QP_HbPictureRows = 'src/org/lumen/projector/panel/HbPictureRows.java'
AR, ROWS, OPT, AOC = A + "HbAudioRules.java", A + "HbOutputRows.java", A + "HbOutputOptions.java", A + "AudioOutputChoice.java"
APR, JACK = A + "AudioPathReporter.java", A + "HbWiredJack.java"
FEAT = "src/org/lumen/projector/hal/HbFeatures.java"
BTRC = "../../devices/goat_elegant/image/hb_bt.rc"
MUT = [
    # ---- the stock split lives in lumen-audio (StockVolume / PathSwitch, mutated by its own mutate.py); the HB adapter
    ("apr_own_switch", APR, "        PathSwitch.install(sApp, VENDOR);\n", ""),
    ("apr_policy_wait", APR, "        if (am != null) am.registerAudioDeviceCallback(cb, h);\n", ""),
    ("rc_absvol_on", BTRC, "    setprop persist.bluetooth.disableabsvol true\n", "    setprop persist.bluetooth.disableabsvol false\n"),
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
    # ---- the 3.5 mm rows
    ("rows_no_jack", ROWS, "(WIRED.equals(k) && wiredJack)", "false"),
    ("rows_jack_everywhere", ROWS, "(WIRED.equals(k) && wiredJack)", "wiredJack"),
    ("rows_3arg_jack", ROWS, "return kinds(connected, soundbarKnown, chosen, false);", "return kinds(connected, soundbarKnown, chosen, true);"),
    ("opt_no_jack", OPT, "HbOutputRows.kinds(connected, known, chosen, HbWiredJack.offered())", "HbOutputRows.kinds(connected, known, chosen, false)"),
    # ---- the 3.5 mm wiring (the jack rule is lumen-audio JackOutput, mutated by its own mutate.py)
    ("aoc_no_wait", AOC, "            if (HbWiredJack.keep(am, media, k, why)) return;", "            HbWiredJack.keep(am, media, k, why);"),
    ("aoc_jack_after_device", AOC, [("            if (HbWiredJack.keep(am, media, k, why)) return;      // \"devices added\" evaluates again with the output listed\n", ""),
                                    ("            if (route(am, media, app, k, why)) HbWiredJack.settle(am, media, k, why);\n",
                                     "            if (route(am, media, app, k, why)) HbWiredJack.settle(am, media, k, why);\n            if (HbWiredJack.keep(am, media, k, why)) return;\n")], None),
    ("aoc_no_settle", AOC, "if (route(am, media, app, k, why)) HbWiredJack.settle(am, media, k, why);", "route(am, media, app, k, why);"),
    ("aoc_auto_with_jack", AOC, "AutoOutput.device(am, HbWiredJack.SENSED)", "AutoOutput.device(am, true)"),
    ("jack_sensed", JACK, "static final boolean SENSED = false;", "static final boolean SENSED = true;"),
    ("jack_always_chosen", JACK, "return offered() && JackOutput.keep(am, media, chosen == AudioOutputKind.WIRED, why);",
     "return offered() && JackOutput.keep(am, media, true, why);"),
    ("jack_settle_chosen", JACK, "if (offered()) JackOutput.settle(am, media, chosen == AudioOutputKind.WIRED, why);",
     "if (offered()) JackOutput.settle(am, media, true, why);"),
    ("jack_ungated", JACK, "return HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE);", "return true;"),
    ("feat_off", FEAT, "WIRED_HEADPHONE(State.ON, \"support_wired_headphone\", \"false\"),", "WIRED_HEADPHONE(State.OFF, \"support_wired_headphone\", \"false\"),"),
    ("feat_key", FEAT, "WIRED_HEADPHONE(State.ON, \"support_wired_headphone\", \"false\"),", "WIRED_HEADPHONE(State.ON, \"support_wired_headphones\", \"false\"),"),
]
SUITES = ["test/slider/run.sh", "test/audio/run.sh", "test/features/run.sh"]


def tree(dst):
    shutil.copytree(os.path.join(G, "apps/HbProjector"), os.path.join(dst, "apps/HbProjector"),
                    ignore=shutil.ignore_patterns("build"))
    # HbAudioRules asks lumen-audio SoundbarMode, check_wiring reads PathSwitch
    shutil.copytree(os.path.join(G, "apps/core/lumen-audio"), os.path.join(dst, "apps/core/lumen-audio"))
    # test/audio/check_wiring.py reads the stock Bluetooth absolute volume line of hb_bt.rc
    os.makedirs(os.path.join(dst, "devices/goat_elegant/image"))
    shutil.copy(os.path.join(G, "devices/goat_elegant/image/hb_bt.rc"), os.path.join(dst, "devices/goat_elegant/image/hb_bt.rc"))
    # and the HB chip volume table and the recipe that ships it
    for rel in ("devices/goat_elegant/audio/chip_volume.tsv", "devices/goat_elegant/app/HbProjector.layers"):
        os.makedirs(os.path.dirname(os.path.join(dst, rel)), exist_ok=True)
        shutil.copy(os.path.join(G, rel), os.path.join(dst, rel))


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
