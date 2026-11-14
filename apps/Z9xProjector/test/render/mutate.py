#!/usr/bin/env python3
"""Mutants of the Z9X render parity walk: each one changes what the Z9X shows on the shared menu (a rule, a label, a
string, the profile, the section layout); test/render/run.sh must turn red for every one. Works on a scratch copy of
the assembled app tree, never in place.

  python3 apps/Z9xProjector/test/render/mutate.py
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "../../../.."))
P = "src/org/lumen/projector/panel/"
R = "src/org/lumen/menu/"
MUTANTS = [
    ("game hint shown on HDMI too", P + "Z9xMenuRules.java", 'if (ok && !hdmi) b.note(MenuSpec.GAME', 'if (ok) b.note(MenuSpec.GAME'),
    ("HDMI range kept off HDMI", P + "Z9xMenuRules.java", ".hideIf(FeatureId.HDMI_RANGE, !showRange)", ".hideIf(FeatureId.HDMI_RANGE, false)"),
    ("HDR Vivid offered on SDR", P + "Z9xMenuRules.java", 'show = hdr == HDR_VIVID;', 'show = true;'),
    ("AI sound without its sub-mode", P + "Z9xMenuRules.java", 'b.label(FeatureId.SOUND_MODE, "ai",', 'if (false) b.label(FeatureId.SOUND_MODE, "ai",'),
    ("game picture rule dropped", P + "Z9xMenuRules.java",
     "return rule(PanelRules.pictureMode(in.rules(), in.gameModeOn()), true);", "return null;"),
    ("MEMC note without the frame-rate rule", P + "Z9xMenuRules.java",
     'return Boolean.TRUE.equals(in.nativeFps) ? Note.titled("pro_memc_blocked") : null;', "return null;"),
    ("failed read keeps Ultra 120", P + "Z9xMenuRules.java", "!ok ? null : in.ultra120 != null", "in.ultra120 != null"),
    ("failed read hides the sliders", P + "Z9xMenuRules.java", "b.hideIf(id, ok && in.pq[i] == null);", "b.hideIf(id, in.pq[i] == null);"),
    ("Office placeholder lost", P + "Z9xMenuRules.java", 'b.placeholder(FeatureId.PICTURE_COLOR_TEMP, t.get("panel_ct_3"));', ";"),
    ("equalizer usable on a failed read", P + "Z9xMenuRules.java", "if (!ok) b.disable(FeatureId.SOUND_EQ);", ";"),
    ("lamp lock note without the mode name", P + "Z9xMenuRules.java",
     'return Note.titledBy("panel_lamp", "panel_lamp_mode_locked", name);', 'return Note.titledBy("panel_lamp", "panel_boost_3d");'),
    ("General page titled General", P + "MenuLabels.java", 'case "general": return R.string.lumen_m_panel_all_settings;\n            default: return section(id);',
     'default: return section(id);'),
    ("All settings says Projection mode", P + "MenuLabels.java", 'case "projection": return R.string.lumen_m_panel_tile_projection;\n            default: return section(id);',
     'default: return section(id);'),
    ("tile value label swapped", P + "MenuLabels.java", 'case "picture.mode|ai": return R.string.lumen_m_panel_tileval_pm_ai;',
     'case "picture.mode|ai": return R.string.lumen_m_panel_pm_ai;'),
    ("image correction header lost", R + "MenuSpec.java", '"image_correction"', '"image_correction_x"'),
    ("Russian off word changed", "res/values-ru/lumen_strings_menu.xml", '<string name="lumen_m_panel_off">', '<string name="lumen_m_panel_off">x'),
    ("Kazakh game speed changed", "res/values-kk/lumen_strings_menu.xml", '<string name="lumen_m_panel_speed_top">', '<string name="lumen_m_panel_speed_top">x'),
    ("profile: sound modes reordered", "@profile", '"ai", "movie", "music", "sports", "karaoke"', '"ai", "music", "movie", "sports", "karaoke"'),
    ("profile: real-time keystone dropped", "@profile", '"keystone.realtime": {},', ''),
]


def main():
    tmp = tempfile.mkdtemp(prefix="z9x-render-mut-")
    try:
        base = os.path.join(tmp, "tree")
        subprocess.run([sys.executable, "-B", os.path.join(G, "tools/devices/assemble_app.py"), "--root", G, "--device",
                        "horse_popular", "--app", "Z9xProjector", "--out", base], check=True, stdout=subprocess.DEVNULL)
        prof0 = os.path.join(G, "devices/horse_popular/menu.json")
        env0 = dict(os.environ, RENDER_TREE=base, RENDER_PROFILE=prof0)
        r = subprocess.run(["sh", os.path.join(HERE, "run.sh")], env=env0, capture_output=True, text=True)
        if r.returncode != 0:
            print("FAIL the unmutated walk is red:\n" + r.stdout[-2000:]); return 1
        bad = 0
        for name, rel, old, new in MUTANTS:
            tree = os.path.join(tmp, "m")
            shutil.rmtree(tree, ignore_errors=True)
            shutil.copytree(base, tree)
            prof = os.path.join(tmp, "menu.json")
            shutil.copy(prof0, prof)
            path = prof if rel == "@profile" else os.path.join(tree, rel)
            text = open(path, encoding="utf-8").read()
            if old not in text:
                print("FAIL mutant '%s': the text to change is gone from %s" % (name, rel)); bad += 1; continue
            open(path, "w", encoding="utf-8").write(text.replace(old, new, 1))
            env = dict(os.environ, RENDER_TREE=tree, RENDER_PROFILE=prof)
            r = subprocess.run(["sh", os.path.join(HERE, "run.sh")], env=env, capture_output=True, text=True)
            if r.returncode == 0:
                print("FAIL mutant survived: " + name); bad += 1
            else:
                print("killed: " + name)
        print("render mutants: %d of %d killed" % (len(MUTANTS) - bad, len(MUTANTS)))
        return 1 if bad else 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
