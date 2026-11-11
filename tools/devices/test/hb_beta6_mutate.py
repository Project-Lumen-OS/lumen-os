#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""hb_beta6_mutate.py: mutation check of the HB beta6 changes (vendor-held Boost in Performance / Office in the HB
adapter of the shared menu, the stock HB Boost texts, the optical zoom tile, ZoomScale, one-way fit / obstacle coupling, the manual-focus chart). Each mutant breaks one rule in a scratch
copy of apps/HbProjector (+ the Z9X Hal.java the HB app takes from its recipe); apps/HbProjector/test/beta6/run.sh must
turn red, and the unmutated copy must pass.  python3 tools/devices/test/hb_beta6_mutate.py"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
P = "src/org/lumen/projector/panel/"
BR, ZS, QP, OZ = P + "BoostRules.java", P + "ZoomScale.java", P + "HbMenuFeatures.java", P + "OpticalZoomPanel.java"
GT = P + "HbMenuGates.java"
HAL = "@Z9X/src/org/lumen/projector/Hal.java"
MUT = [
    ("br_perf_only", BR, "pm == PM_PERFORMANCE || pm == PM_OFFICE", "pm == PM_PERFORMANCE"),
    ("br_office_only", BR, "pm == PM_PERFORMANCE || pm == PM_OFFICE", "pm == PM_OFFICE"),
    ("br_perf_wire", BR, "PM_PERFORMANCE = 5;", "PM_PERFORMANCE = 6;"),
    ("br_trust_boolean", BR, "if (lensAfter == want) return Outcome.DONE;", ""),
    ("br_lock_on_enter", BR, "if (!wantOn && lockedByPictureMode(pictureMode))", "if (lockedByPictureMode(pictureMode))"),
    ("br_unreadable_done", BR, "return ret ? Outcome.DONE : Outcome.FAILED;", "return Outcome.DONE;"),
    ("br_explain_without_boost", BR, "Boolean.TRUE.equals(boost) && lockedByPictureMode", "lockedByPictureMode"),
    ("br_ca_always", BR, "Boolean.TRUE.equals(boost) && !lockedByPictureMode(pictureModeWire)", "Boolean.TRUE.equals(boost)"),
    ("mf_leave_no_explain", QP, "        if (!on && BoostRules.onLeavePress(cur, pm) == BoostRules.Leave.EXPLAIN_PICTURE_MODE) return boostLocked(pm);\n", ""),
    ("mf_setboost_boolean", QP, "BoostRules.judge(on, ret, lensAfter, pmAfter)", "BoostRules.judge(on, true, lensAfter, pmAfter)"),
    ("mf_setboost_throw_old", QP, "boolean ret = g2.setBoost(on);", "boolean ret = g2.setBoost(on); if (!ret) throw new IllegalStateException(\"151 returned false\");"),
    ("mf_ca_raw_boost", QP, "BoostRules.blocksColorAccuracy(safeBoost(g2), raw)", "Boolean.TRUE.equals(safeBoost(g2))"),
    ("mf_lock_no_mode", QP, "R.string.hb_boost_locked, mode", "R.string.hb_boost_locked"),
    ("mf_lamp_in_boost_kept", QP, "String r = setBoost(false, g, g2);", "String r = null;"),
    ("gt_zoom_ungated", GT, '{"zoom", "OPTICAL_ZOOM"}', '{"zoom", null}'),
    ("mf_zoom_no_game_refusal", QP, "                if (topSpeed) { Notify.show(app, app.getString(R.string.panel_kst_game_mode)); return false; }\n", ""),
    ("mf_obstacle_uncoupled", QP, "Hal.setObstacleAvoidCoupled(g, on)", "g.setObstacleAvoid(on)"),
    ("mf_fit_uncoupled", QP, "Hal.setCurtainFitCoupled(g, on)", "g.setCurtainFit(on)"),
    ("hal_fit_off_sets_obstacle", HAL, "if (on) g.setObstacleAvoid(true);", "g.setObstacleAvoid(true);"),
    ("hal_fit_on_no_obstacle", HAL, "if (on) g.setObstacleAvoid(true);", ""),
    ("hal_obstacle_on_sets_fit", HAL, "if (!on) g.setCurtainFit(false);", "g.setCurtainFit(on);"),
    ("hal_obstacle_off_keeps_fit", HAL, "if (!on) g.setCurtainFit(false);", ""),
    ("str_ru_old_claim", "res/values-ru/strings_hb.xml", "<string name=\"hb_boost_effect\">", "<string name=\"hb_boost_effect\">Цвета бледнее. "),
    ("str_de_no_mode", "res/values-de/strings_hb.xml", "Im Bildmodus „%1$s“", "Im Bildmodus"),
    ("fp_corner_r", "src/org/lumen/projector/FocusPattern.java", "CORNER_R = 230f / 1920f;", "CORNER_R = 200f / 1920f;"),
    ("fp_no_centre", "src/org/lumen/projector/FocusPattern.java", "{w, h, rc}, {w / 2f, h / 2f, rm},", "{w, h, rc},"),
    ("fp_line_y", "src/org/lumen/projector/FocusPattern.java", "LINE_Y = 852f / 1080f;", "LINE_Y = 540f / 1080f;"),
    ("fp_sweep_full", "src/org/lumen/projector/FocusPattern.java", "return 180f / SPOKES;", "return 360f / SPOKES;"),
    ("fp_inverted", "src/org/lumen/projector/FocusPattern.java", "BACKGROUND = 0xFFFFFFFF;", "BACKGROUND = 0xFF000000;"),
    ("mf_no_chart", "src/org/lumen/projector/ManualFocusActivity.java", "addView(new FocusPatternView(this), 0,", "addView(new android.view.View(this), 0,"),
    ("mf_chart_on_top", "src/org/lumen/projector/ManualFocusActivity.java", "addView(new FocusPatternView(this), 0,", "addView(new FocusPatternView(this), 1,"),
    ("fv_no_line", "src/org/lumen/projector/FocusPatternView.java", "c.drawRect(l[0], l[1], l[2], l[3], ink);", "c.drawRect(0, 0, 0, 0, ink);"),
    ("sn_usb_any", "src/org/lumen/projector/power/StrNoticeRules.java", "return now == Kind.USB && !shownForThisConnection;", "return now == Kind.USB;"),
    ("sn_usb_kind", "src/org/lumen/projector/power/StrNoticeRules.java", "if (blocker.startsWith(StrGate.B_USB)) return Kind.USB;", ""),
    ("sn_short_press", "src/org/lumen/projector/power/StrNoticeRules.java", "heldMs >= longPressMs", "heldMs >= 0"),
    ("sn_every_repeat", "src/org/lumen/projector/power/StrNoticeRules.java", " && downTime != notedFor;", ";"),
    ("sn_stb_code", "src/org/lumen/projector/power/StrNoticeRules.java", "KEYCODE_STB_POWER = 179", "KEYCODE_STB_POWER = 178"),
    ("sn_in_standby", "src/org/lumen/projector/power/StrNotice.java", "            if (StandbyController.isActive()) return;", ""),
    ("sn_wrong_text", "src/org/lumen/projector/power/StrNotice.java", "case UPDATE: return R.string.hb_str_blocked_update;", "case UPDATE: return R.string.hb_str_blocked_usb;"),
    ("sn_no_hook", "src/org/lumen/projector/KeyReceiver.java", "        org.lumen.projector.power.StrNotice.onGlobalKey(ctx, ev);\n", ""),
    ("sn_no_install", "src/org/lumen/projector/App.java", "org.lumen.projector.power.StrNotice.install(this);", "Log.i(TAG, \"x\");"),
    ("sn_kk_missing", "res/values-kk/strings_hb.xml", "<string name=\"hb_str_blocked_usb\">", "<string name=\"hb_str_blocked_usbX\">"),
    ("str_ja_missing", "res/values-ja/strings_hb.xml", "<string name=\"hb_boost_locked\">", "<string name=\"hb_boost_lockedX\">"),
]

def tree(dst):
    shutil.copytree(os.path.join(G, "apps/HbProjector"), os.path.join(dst, "apps/HbProjector"),
                    ignore=shutil.ignore_patterns("build"))
    hal = os.path.join(dst, "apps/Z9xProjector/src/org/lumen/projector")
    os.makedirs(hal)
    shutil.copy2(os.path.join(G, "apps/Z9xProjector/src/org/lumen/projector/Hal.java"), hal)
    os.makedirs(os.path.join(hal, "power"))
    shutil.copy2(os.path.join(G, "apps/Z9xProjector/src/org/lumen/projector/power/StrGate.java"), os.path.join(hal, "power"))

def path(root, rel):
    if rel.startswith("@Z9X/"): return os.path.join(root, "apps/Z9xProjector", rel[5:])
    return os.path.join(root, "apps/HbProjector", rel)

def run(root):
    r = subprocess.run(["sh", os.path.join(root, "apps/HbProjector/test/beta6/run.sh")], capture_output=True, text=True)
    return r.returncode == 0

only = os.environ.get("MUT_ONLY", "").split()
w = tempfile.mkdtemp(prefix="hb_b6mut.")
try:
    base = os.path.join(w, "base"); tree(base)
    if not run(base):
        print("BROKEN: the unmutated copy fails"); sys.exit(2)
    killed = survived = 0
    for name, rel, old, new in MUT:
        if only and not any(name.startswith(o) for o in only): continue
        t = os.path.join(w, name); tree(t)
        f = path(t, rel); s = open(f, encoding="utf-8").read()
        if s.count(old) != 1:
            print("BROKEN mutant %s: anchor found %d times" % (name, s.count(old))); sys.exit(2)
        open(f, "w", encoding="utf-8").write(s.replace(old, new))
        if run(t): survived += 1; print("SURVIVED " + name)
        else: killed += 1; print("killed   " + name)
        shutil.rmtree(t)
    print("hb_beta6_mutate: %d killed, %d survived" % (killed, survived))
    sys.exit(1 if survived else 0)
finally:
    shutil.rmtree(w, ignore_errors=True)
