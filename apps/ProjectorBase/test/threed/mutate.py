#!/usr/bin/env python3
"""Targeted mutants of Quick 3D (the 3D tile, the network round button, the 3D popup, the stock calls, the remote's
3D request): every mutant must turn test/threed, lumen-menu test/run.sh, test/qpd or test/render red. Works on a
hard-linked scratch copy of the tree, never in place (a mutated file is written as a new file, its link broken).

  python3 apps/ProjectorBase/test/threed/mutate.py
"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
M = "apps/core/lumen-menu/src/org/lumen/menu/"
P = "apps/core/lumen-menu/src/org/lumen/projector/panel/"
Z = "apps/ProjectorBase/src/org/lumen/projector/"
TESTS = ["apps/ProjectorBase/test/threed/run.sh", "apps/core/lumen-menu/test/run.sh",
         "apps/core/lumen-menu/test/qpd/run.sh", "apps/ProjectorBase/test/render/run.sh"]
MUTANTS = [
    # ---- the stock call of each mode (ThreeDCall) and the mode table (ThreeDMode)
    ("as 2D sent as enable3D", Z + "display/ThreeDCall.java", "if (m == null || m.to2d) return null;", "if (m == null) return null;"),
    ("as 2D top and bottom swapped", Z + "display/ThreeDCall.java", "return m.format == Mode3D.TOP_BOTTOM.wire;",
     "return m.format != Mode3D.TOP_BOTTOM.wire;"),
    ("off is never sent", Z + "display/ThreeDCall.java", "if (x.wire == m.format) return x;",
     "if (x.wire == m.format && x != Mode3D.OFF) return x;"),
    ("frame packing sent on the Z9X", Z + "display/ThreeDCall.java", "if (x.wire == m.format) return x;\n        return null;",
     "if (x.wire == m.format) return x;\n        return Mode3D.SIDE_BY_SIDE;"),
    ("ThreeD sends enable3D for every mode", Z + "display/ThreeD.java", "boolean ok = m != null ? g.set3DMode(m) : g.set3DTo2D(tb);",
     "boolean ok = g.set3DMode(m != null ? m : Mode3D.SIDE_BY_SIDE);"),
    ("top and bottom as 2D has the side by side byte", M + "ThreeDMode.java", 'TB_2D("tb_2d", 3, true)', 'TB_2D("tb_2d", 2, true)'),
    ("side by side is not a 2D mode flag", M + "ThreeDMode.java", 'SBS_2D("sbs_2d", 2, true)', 'SBS_2D("sbs_2d", 2, false)'),
    ("read-back: 3D to 2D format swapped", M + "ThreeDMode.java", "case 2: return to2d ? SBS_2D : SBS;",
     "case 2: return to2d ? TB_2D : SBS;"),
    ("read-back: 3D to 2D ignored on side by side", M + "ThreeDMode.java", "case 2: return to2d ? SBS_2D : SBS;",
     "case 2: return SBS;"),
    ("read-back: 3D to 2D ignored on top and bottom", M + "ThreeDMode.java", "case 3: return to2d ? TB_2D : TB;",
     "case 3: return TB;"),
    # stock get3dModeToResult reads the flag only for 2 and 3 (z9xfb and x50fb PictureRepository, the same code)
    ("read-back: the 3D to 2D flag decides first (quick-3d c963639e)", M + "ThreeDMode.java", "        switch (format) {",
     "        if (to2d) return format == 3 ? TB_2D : SBS_2D;\n        switch (format) {"),
    ("read-back: frame packing reads as off", M + "ThreeDMode.java", "case 4: return FRAME_PACKING;", "case 4: return OFF;"),
    ("read-back: an unread flag guessed", M + "ThreeDMode.java", "if (format == null || to2d == null) return null;",
     "if (format == null) return null;\n        if (to2d == null) to2d = false;"),
    ("read-back: an unknown format reads as side by side", M + "ThreeDMode.java", "default: return OFF;", "default: return SBS;"),
    # ---- the card: 3D in the network place, network up
    ("network never goes up", M + "MenuSpec.java", "PUSHES_UP = {{FeatureId.PICTURE_3D, FeatureId.NETWORK}};",
     "PUSHES_UP = {};"),
    ("network on top without 3D", M + "MenuSpec.java", "if (p[1] == f && there.test(p[0]) && there.test(f)) return true;",
     "if (p[1] == f && there.test(f)) return true;"),
    ("round network stays while 3D is not usable", M + "MenuSpec.java",
     "            if (t.feature != null && !pushedUp(t.feature, o -> usable.test(String.valueOf(o)))) continue;\n", ""),
    ("network keeps its card place before 3D", M + "MenuSpec.java", "{SMALL, FeatureId.PICTURE_3D, FeatureId.NETWORK}",
     "{SMALL, FeatureId.NETWORK, FeatureId.PICTURE_3D}"),
    ("network right of the gear", M + "MenuSpec.java", "ROUND_TILES = {FeatureId.NETWORK, VOICE, GENERAL};",
     "ROUND_TILES = {VOICE, GENERAL, FeatureId.NETWORK};"),
    ("3D not a Picture row", M + "MenuSpec.java", "FeatureId.PICTURE_ANTI_RBE, FeatureId.PICTURE_3D}", "FeatureId.PICTURE_ANTI_RBE}"),
    ("the controller ignores the runtime round row", P + "MenuController.java",
     "for (MenuSpec.Tile t : spec.roundRow(this::tilePresent)) tile(t.id, QpdTile.ROUND);",
     "for (MenuSpec.Tile t : spec.round) if (tilePresent(t.id)) tile(t.id, QpdTile.ROUND);"),
    # ---- the popup and the tile state
    ("no 3D popup", P + "MenuController.java", "        if (has(FeatureId.PICTURE_3D)) threeDPopup();\n", ""),
    ("the popup marks nothing", P + "MenuController.java", "td.get(i).setChecked(opts.get(i).equals(now));",
     "td.get(i).setChecked(false);"),
    ("the mode on now is sent again", P + "MenuController.java", "if (state != null && o.equals(state.get(f))) return;", ""),
    ("the tile shows no mode", P + "MenuController.java",
     "td.setState(m == null || !m.on() ? null : optionLabel(FeatureId.PICTURE_3D, m.token));", "td.setState(null);"),
    ("the dot also while off", P + "MenuController.java", "td.setActive(m != null && m.on());", "td.setActive(m != null);"),
    ("a small tile shows its title in 3D", P + "QpdTile.java",
     "CharSequence title = state != null && state.length() > 0 ? state : this.title;", "CharSequence title = this.title;"),
    ("motion while animations are off", P + "QpdStyle.java", "            if (!motion()) return;", "            if (false) return;"),
    ("off labelled with the menu's Off", P + "MenuLabels.java", 'case "picture.3d|off": return R.string.lumen_m_3d_off;',
     'case "picture.3d|off": return R.string.lumen_m_panel_off;'),
    ("the 2D labels swapped", P + "MenuLabels.java", 'case "picture.3d|tb_2d": return R.string.lumen_m_3d_tb_2d;',
     'case "picture.3d|tb_2d": return R.string.lumen_m_3d_sbs_2d;'),
    # ---- the Z9X adapter
    ("Z9X never reads 3D", Z + "panel/Z9xMenuRules.java", "if (m3d != null) b.put(FeatureId.PICTURE_3D, m3d.token);", ""),
    ("Z9X reads at once (inside the black frame)", Z + "panel/Z9xPictureRows.java",
     "ThreeD.READBACK_DELAY_MS, in -> ThreeD.watchIfOn", "0, in -> ThreeD.watchIfOn"),
    ("no watch for the end of the video", Z + "panel/Z9xPictureRows.java",
     "in -> ThreeD.watchIfOn(ThreeDMode.read(in.fmt3d, in.is3dTo2d))", "in -> { }"),
    # ---- the remote's 3D request
    ("the 3D key opens the card", M + "MenuRequest.java", "        if (QUICK_3D.equals(action)) return FeatureId.PICTURE_3D.id;\n", ""),
    ("the receiver ignores the 3D key", M + "MenuRequest.java", "public static boolean ours(String action) { return QUICK_3D.equals(action); }",
     "public static boolean ours(String action) { return false; }"),
    ("the 3D popup is no panel place", Z + "panel/QuickPanelController.java",
     "        if (FeatureId.PICTURE_3D.id.equals(section)) return section;     // the 3D popup (MenuRequest.QUICK_3D)\n", ""),
    ("the Z9X receiver does not take the 3D key", "apps/ProjectorBase/AndroidManifest.xml",
     '                <action android:name="org.lumen.projector.action.QUICK_3D" />\n', ""),
    # ---- the profiles: the stock list per model
    ("Z9X modes out of stock order", "devices/horse_popular/menu.json", '"off", "sbs", "tb", "sbs_2d", "tb_2d"',
     '"off", "tb", "sbs", "sbs_2d", "tb_2d"'),
    ("Z9X without 3D to 2D", "devices/horse_popular/menu.json", '"off", "sbs", "tb", "sbs_2d", "tb_2d"', '"off", "sbs", "tb"'),
    ("3D on the HB (no stock 3D)", "devices/goat_elegant/menu.json", '"features": {', '"features": {"picture.3d": {"options": ["off", "sbs"]}, '),
]


def files():
    out = subprocess.run(["git", "-C", G, "ls-files", "-z", "-co", "--exclude-standard"], capture_output=True, check=True).stdout
    return [f for f in out.decode().split("\0") if f and os.path.isfile(os.path.join(G, f))]


def red(root):
    for t in TESTS:
        r = subprocess.run(["sh", os.path.join(root, t)], capture_output=True, text=True, cwd=root)
        if r.returncode != 0:
            why = next((l for l in (r.stdout + r.stderr).splitlines() if "FAIL" in l or "error" in l), "red")
            return "%s: %s" % (os.path.basename(os.path.dirname(t)), why.strip()[:100])
    return None


def main():
    tmp = tempfile.mkdtemp(prefix="threed_mut.")
    try:
        base = os.path.join(tmp, "base")
        for f in files():
            d = os.path.join(base, f)
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.copy2(os.path.join(G, f), d)
        if os.path.isdir(os.path.join(G, ".git")) or os.path.isfile(os.path.join(G, ".git")):
            # test/render reads the 1.0.10 reference with git archive: the scratch tree borrows the repo's objects
            subprocess.run(["git", "init", "-q", base], check=True)
            alt = subprocess.run(["git", "-C", G, "rev-parse", "--git-common-dir"], capture_output=True, text=True).stdout.strip()
            alt = os.path.abspath(os.path.join(G, alt))
            open(os.path.join(base, ".git", "objects", "info", "alternates"), "w").write(os.path.join(alt, "objects") + "\n")
            ref = os.environ.get("Z9X_REF", "origin/release/z9x-1.0.10")
            sha = subprocess.run(["git", "-C", G, "rev-parse", ref], capture_output=True, text=True).stdout.strip()
            if sha:
                name = "refs/remotes/" + ref if ref.startswith("origin/") else "refs/heads/" + ref
                subprocess.run(["git", "-C", base, "update-ref", name, sha], check=True)
        why = red(base)
        if why:
            print("the unmutated tree is red already: " + why)
            sys.exit(2)
        killed, survived = 0, []
        for name, rel, old, new in MUTANTS:
            mut = os.path.join(tmp, "m")
            shutil.rmtree(mut, ignore_errors=True)
            subprocess.run(["cp", "-al", base, mut], check=True) if sys.platform != "darwin" else \
                subprocess.run(["cp", "-Rc", base, mut], check=True)
            p = os.path.join(mut, rel)
            src = open(p, encoding="utf-8").read()
            if src.count(old) != 1:
                print("BROKEN mutant (pattern %d times): %s" % (src.count(old), name))
                survived.append(name)
                continue
            os.remove(p)
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            why = red(mut)
            if why:
                killed += 1
                print("killed   %-50s %s" % (name, why))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        print("threed mutants: %d killed, %d survived" % (killed, len(survived)))
        sys.exit(0 if not survived else 1)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
