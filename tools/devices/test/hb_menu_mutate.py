#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""hb_menu_mutate.py: mutation check of the HB side of the shared projector menu (apps/core/lumen-menu): the gates of
panel/HbMenuGates (per model, per feature.xml key, the game menu items), the HB wire tables, the light range without
the Boost step, the adapter's entry gates and refusals (panel/HbMenuFeatures), the game menu io (panel/HbGameIo), the
owner (panel/HbMenu), the keys, App and the HB profile. Each mutant breaks one rule in a scratch copy (apps/HbProjector,
the module's profile parser, devices/goat_elegant/{menu.json, recipe, forks.tsv}, the Z9X and module strings);
apps/HbProjector/test/menu/run.sh must turn red, and the unmutated copy must pass.
  python3 tools/devices/test/hb_menu_mutate.py   (MUT_ONLY="gt_ mf_" to filter)"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
P = "src/org/lumen/projector/panel/"
GT, MF, GI, HM, QP = P + "HbMenuGates.java", P + "HbMenuFeatures.java", P + "HbGameIo.java", P + "HbMenu.java", \
    P + "QuickPanelController.java"
MF_HbGeneralRows = 'src/org/lumen/projector/panel/HbGeneralRows.java'
MF_HbHdmiRows = 'src/org/lumen/projector/panel/HbHdmiRows.java'
MF_HbPictureRows = 'src/org/lumen/projector/panel/HbPictureRows.java'
KR, APP = "src/org/lumen/projector/KeyReceiver.java", "src/org/lumen/projector/App.java"
PROF = "@G/devices/goat_elegant/menu.json"
MUT = [
    # ---- gates
    ("gt_boost_ungated", GT, '{FeatureId.BOOST, "BOOST"}', '{FeatureId.BOOST, null}'),
    ("gt_color_temp_ungated", GT, '{FeatureId.PICTURE_COLOR_TEMP, "COLOR_TEMP"}', '{FeatureId.PICTURE_COLOR_TEMP, null}'),
    ("gt_eye_ungated", GT, '{FeatureId.EYE, "EYE_PROTECTION"}', '{FeatureId.EYE, null}'),
    ("gt_earc_ungated", GT, '{FeatureId.SOUND_EARC, "HDMI"}', '{FeatureId.SOUND_EARC, null}'),
    ("gt_ak_wrong_key", GT, '{FeatureId.KEYSTONE_AK_NOW, "AUTO_KEYSTONE"}', '{FeatureId.KEYSTONE_AK_NOW, "CURTAIN_FIT"}'),
    ("gt_implemented_all", GT, "if (g[1] == null || on.test((String) g[1])) out.add((FeatureId) g[0]);", "out.add((FeatureId) g[0]);"),
    ("gt_row_missing", GT, '            {FeatureId.USB_FILES, null},\n', ""),
    ("gt_game_sr", GT, '            {"lat", "GAME_MODE"},\n', '            {"lat", "GAME_MODE"},\n            {"sr", "GAME_MODE"},\n'),
    ("gt_game_no_bar_gate", GT, 'if (!on.test("GAME_MODE") || !on.test("GAME_BAR")) return', 'if (!on.test("GAME_MODE")) return'),
    ("gt_vrr_ungated", GT, '{"vrr", "GAME_VRR"}', '{"vrr", "GAME_MODE"}'),
    # ---- wires and ranges
    ("gt_lamp_boost_step", GT, "return v >= LAMP_MIN && v <= LAMP_MAX;", "return v >= LAMP_MIN && v <= BOOST_LEVEL;"),
    ("gt_picture_wire", GT, '"ai", 10, "standard", 1,', '"ai", 10, "standard", 2,'),
    ("gt_edid_game", GT, '"compat", 0, "enhanced", 1);', '"compat", 0, "enhanced", 2);'),
    ("gt_sound_standard", GT, '"sports", 12);', '"sports", 20);'),
    ("gt_rear_keeps_table", GT, "return rear ? (table ? 2 : 3) : (table ? 0 : 1);", "return rear ? 2 : 0;"),
    ("gt_token_order", GT, '"off", 0, "low", 1, "medium", 2, "high", 3);', '"low", 1, "off", 0, "medium", 2, "high", 3);'),
    # ---- adapter
    ("mf_set_ungated", MF, "        if (!implemented().contains(feature)) { refuse(cb, null); return; }\n", ""),
    ("mf_open_ungated", MF, "        if (!implemented().contains(screen)) return false;\n", ""),
    ("mf_boost_no_explain", MF_HbPictureRows, "BoostLeave.EXPLAIN_PICTURE_MODE) return boostLocked(pm);", "BoostLeave.EXPLAIN_PICTURE_MODE) { }"),
    ("mf_range_no_hdmi", MF_HbHdmiRows, 'throw new IllegalArgumentException("range " + value);\n                if (!hdmiNow(g)) return "panel_game_hdmi_only";',
     'throw new IllegalArgumentException("range " + value);'),
    ("mf_reason_unknown", MF_HbPictureRows, 'return "blocked_game";', 'return "blocked_game_x";'),
    ("mf_eye_no_nui", MF_HbGeneralRows, '                if (!GmpfClient.nuiReaderReady()) return "eye_unavailable";   // no NUI fifo reader: nothing sent\n', ""),
    ("mf_items_ungated", MF, "return HbMenuGates.gameItems(f -> HbFeatures.on(HbFeatures.F.valueOf(f)));", "return HbMenuGates.gameItems(f -> true);"),
    # ---- game menu io
    ("gi_sr_written", GI, "            default:\n                // sr / snd",
     "            case GmModel.SR: return g2.getSuperResolution() ? 1 : 0;\n            default:\n                // sr / snd"),
    ("gi_vrr_ungated", GI, '                if (!HbFeatures.on(HbFeatures.F.GAME_VRR)) throw new IllegalStateException("GAME_VRR off");\n', ""),
    ("gi_no_port_check", GI, "        onPort(g, port);\n        switch (key) {", "        switch (key) {"),
    ("gi_vrr_kept_leaving_top", GI, 'if (g2.getVrr(port)) Log.i(TAG, "145(0) before leaving top speed -> " + g2.setVrr(false, port));',
     'Log.i(TAG, "no 145(0)");'),
    # ---- owner, keys, App, the profile
    ("hm_game_always", HM, "GameMenu.install(c, on ? new HbGameIo(c) : null)", "GameMenu.install(c, new HbGameIo(c))"),
    ("hm_no_items_check", HM, " && !HbMenuFeatures.gameItems().isEmpty()", ""),
    ("kr_game_bar", KR, "org.lumen.projector.panel.HbMenu.gearLong(ctx,", "org.lumen.projector.game.GameBar.gearLong(ctx,"),
    ("app_no_install", APP, "try { org.lumen.projector.panel.HbMenu.install(this); }", "try { }"),
    ("qp_hal_call", QP, "void toggle() { HbMenu.get(app).toggle(); }", "void toggle() { org.lumen.projector.Hal.isReady(); HbMenu.get(app).toggle(); }"),
    ("prof_sr_item", PROF, '"items": ["lat", "vrr", "hfr", "pic", "dark", "ar"', '"items": ["lat", "vrr", "hfr", "pic", "dark", "sr", "ar"'),
    ("prof_eye_hint", PROF, '"hints": {"picture.mode": "picture_mode",', '"hints": {"eye": "eye_follow", "picture.mode": "picture_mode",'),
    ("prof_lamp_11", PROF, '"lamp": {"min": 1, "max": 10}', '"lamp": {"min": 1, "max": 11}'),
]


def tree(dst):
    shutil.copytree(os.path.join(G, "apps/HbProjector"), os.path.join(dst, "apps/HbProjector"),
                    ignore=shutil.ignore_patterns("build"))
    shutil.copytree(os.path.join(G, "apps/core/lumen-menu/src"), os.path.join(dst, "apps/core/lumen-menu/src"))
    shutil.copytree(os.path.join(G, "apps/core/lumen-menu/res/values"), os.path.join(dst, "apps/core/lumen-menu/res/values"))
    shutil.copytree(os.path.join(G, "apps/Z9xProjector/res/values"), os.path.join(dst, "apps/Z9xProjector/res/values"))
    os.makedirs(os.path.join(dst, "devices/goat_elegant/app"))
    for f in ("menu.json", "forks.tsv", "app/HbProjector.layers"):
        shutil.copy2(os.path.join(G, "devices/goat_elegant", f), os.path.join(dst, "devices/goat_elegant", f))


def path(root, rel):
    if rel.startswith("@G/"): return os.path.join(root, rel[3:])
    return os.path.join(root, "apps/HbProjector", rel)


def run(root):
    env = dict(os.environ, LUMEN_GSI=root)
    r = subprocess.run(["sh", os.path.join(root, "apps/HbProjector/test/menu/run.sh")], capture_output=True, text=True, env=env)
    return r.returncode == 0


only = os.environ.get("MUT_ONLY", "").split()
w = tempfile.mkdtemp(prefix="hb_menumut.")
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
    print("hb_menu_mutate: %d killed, %d survived" % (killed, survived))
    sys.exit(1 if survived else 0)
finally:
    shutil.rmtree(w, ignore_errors=True)
