#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Targeted mutants of x50-feedback1 (focus picture per remote, the corner page, the lens shift range): each weakens one
rule; the suite named with it must turn red. Scratch copies of the repo parts the suites read, never in place.

  python3 apps/core/lumen-menu/test/screens/mutate.py            (MUT_ONLY=REGEX: only the matching names)
Suites: screens = test/screens/run.sh, menu = FocusGuideTest of test/run.sh (the remote table against the profiles).
"""
import glob, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import menu_layers  # noqa: E402  (menu profiles have one reader, devices/README.md)
M = "apps/core/lumen-menu/"
P = M + "src/org/lumen/projector/panel/"
SR = M + "src/org/lumen/menu/ShiftRange.java"
X = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
FG = "devices/_remotes/focus_guide.tsv"
MUTANTS = [
    # the focus picture per remote
    ("fg_x50_side_token", "menu", "devices/g0111/menu.json", '"focus.guide": "focus_guide_front"', '"focus.guide": "focus_guide_side"'),
    ("fg_rs30_side_token", "menu", "devices/g0155/menu.json", '"focus.guide": "focus_guide_front"', '"focus.guide": "focus_guide_side"'),
    ("fg_x50_remote_b990c", "menu", FG, "model\tG0111\tB970C_CY07_CY_V1.0.4", "model\tG0111\tB990C_CY07_CY_V1.0.4"),
    ("fg_titan_no_remote", "menu", FG, "model\tH0062\tB970C\t", "model\tH0099\tB970C\t"),
    ("fg_b970_rule_side", "menu", FG, "rule\tB940|B970|B972|B870|B872|B975\tfocus_guide_front", "rule\tB940|B970|B972|B870|B872|B975\tfocus_guide_side"),
    ("fg_b990c_rule_front", "menu", FG, "rule\tB990C\tfocus_guide_side", "rule\tB990C\tfocus_guide_front"),
    ("fg_hint_swap", "screens", P + "HintPicture.java", 'if ("focus_guide_front".equals(hint)) return FOCUS_FRONT;',
     'if ("focus_guide_front".equals(hint)) return FOCUS_SIDE;'),
    ("fg_tip_side_picture", "screens", P + "QpdTip.java",
     "drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_front);", "drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_side);"),
    ("fg_label_side_text", "screens", P + "MenuLabels.java",
     'case "focus_guide_front": return R.string.lumen_qpd_tip_focus_guide_front;', 'case "focus_guide_front": return R.string.lumen_qpd_tip_focus_guide;'),
    ("fg_front_key_grey", "screens", M + "res/drawable/lumen_focus_guide_remote_front.xml",
     'android:fillColor="#4D8EFF"\n        android:pathData="M150,127', 'android:fillColor="#ffffff"\n        android:pathData="M150,127'),
    ("fg_ru_front_says_side", "screens", M + "res/values-ru/lumen_strings_projection.xml",
     "Нажмите кнопку фокуса на пульте,", "Нажмите кнопку фокуса сбоку пульта,"),
    # the corner page
    ("cv_up_draws_down", "screens", P + "CornerView.java", "if (up) arrow(c, x, y - d, 0, -1, a);", "if (down) arrow(c, x, y - d, 0, -1, a);"),
    ("cv_no_right_arrow", "screens", P + "CornerView.java", "if (right) arrow(c, x + d, y, 1, 0, a);", ""),
    ("cv_all_corners_accent", "screens", P + "CornerView.java", "fill.setColor(i == corner ? QpdStyle.ACCENT : DOT);", "fill.setColor(QpdStyle.ACCENT);"),
    ("cv_transparent_page", "screens", P + "CornerView.java", "static final int PAGE = 0xFF4A4A4A;", "static final int PAGE = 0x00000000;"),
    ("cv_no_circle", "screens", P + "CornerView.java", "c.drawCircle(w / 2, h / 2, 160 * s, line);", ""),
    ("cv_no_keys", "screens", P + "CornerView.java", "centred(c, moreText, keys, w, b - 52 * s);", ""),
    ("cv_corner_no_wrap", "screens", P + "CornerView.java", "this.corner = Math.floorMod(corner, 4);", "this.corner = Math.min(corner, 3);"),
    ("cv_x50_text_overlay", "screens", X + "CornerActivity.java", "        view = new CornerView(this);", "        view = new CornerView(this); // TextView"),
    # the shift range
    ("sr_desk_flip", "screens", SR, "boolean desk = putMode == 0 || putMode == 2;", "boolean desk = putMode == 0 || putMode == 1;"),
    ("sr_y_swap", "screens", SR, "float yPlus = v[desk ? 0 : 1], yMinus = v[desk ? 1 : 0];", "float yPlus = v[desk ? 1 : 0], yMinus = v[desk ? 0 : 1];"),
    ("sr_end_early", "screens", SR, "static final float END = 0.99f;", "static final float END = 0.9f;"),
    ("sr_no_clamp", "screens", SR, "f[i] = Math.min(1f, f[i]);", ""),
    ("sr_no_abs", "screens", SR, "f[i] = Math.abs(Float.parseFloat(p[i].trim()));", "f[i] = Float.parseFloat(p[i].trim());"),
    ("sr_nan_ok", "screens", SR, "if (Float.isNaN(f[i]) || Float.isInfinite(f[i])) return null;", ""),
    ("sr_three_values", "screens", SR, "if (p.length != 2) return null;", "if (p.length < 2) return null;"),
    ("srv_spent_ignored", "screens", P + "ShiftRangeView.java", "fill.setColor(spent ? ARROW_SPENT : ARROW);", "fill.setColor(ARROW);"),
    ("srv_frame_not_moved", "screens", P + "ShiftRangeView.java", "float cx = m + x * REACH_X * s, cy = m - y * REACH_Y * s;", "float cx = m, cy = m - y * REACH_Y * s;"),
    ("srv_ticks_all_bright", "screens", P + "ShiftRangeView.java", "fill.setColor(i <= 4 ? TICK : TICK_DIM);", "fill.setColor(TICK);"),
    ("srv_left_right_swap", "screens", P + "ShiftRangeView.java", "arrow(c, m + d, m, 1, 0, a, at.atRight());", "arrow(c, m + d, m, 1, 0, a, at.atLeft());"),
    ("lens_no_read", "screens", X + "LensActivity.java", "            if (!isFinishing()) range.show(now);", "            if (!isFinishing()) range.show(ShiftRange.UNKNOWN);"),
    ("lens_axes_swapped", "screens", X + "LensActivity.java",
     "ShiftRange.of(HAL.shiftPosition(0), HAL.shiftPosition(1), HAL.putMode())", "ShiftRange.of(HAL.shiftPosition(1), HAL.shiftPosition(0), HAL.putMode())"),
]


def scratch():
    tmp = tempfile.mkdtemp(prefix="x50fb1_mut.")
    root = os.path.join(tmp, "gsi")
    shutil.copytree(os.path.join(G, M), os.path.join(root, M))
    # the suite compiles the shared test kit and reads the X50 zoom texts (ScreensTest)
    for m in ("lumen-testkit", "lumen-common-strings"):
        shutil.copytree(os.path.join(G, "apps/core", m), os.path.join(root, "apps/core", m))
    shutil.copytree(os.path.join(G, X, "../../../../../res"), os.path.join(root, X, "../../../../../res"))
    for c in sorted(os.listdir(os.path.join(G, "devices"))):
        j = os.path.join(G, "devices", c, "menu.json")
        if os.path.isfile(j):
            os.makedirs(os.path.join(root, "devices", c))
            menu_layers.copy_profile(j, os.path.join(root, "devices", c))
    os.makedirs(os.path.join(root, "devices", "_remotes"))
    shutil.copy(os.path.join(G, FG), os.path.join(root, FG))
    for f in ["CornerActivity.java", "LensActivity.java"]:
        os.makedirs(os.path.join(root, X), exist_ok=True)
        shutil.copy(os.path.join(G, X, f), os.path.join(root, X, f))
    return tmp, root


def suite(root, which):
    if which == "screens":
        r = subprocess.run(["sh", os.path.join(root, M, "test/screens/run.sh")], capture_output=True, text=True)
        return r.returncode != 0, r.stdout
    out = os.path.join(root, "fgc")
    os.makedirs(out)
    src = glob.glob(os.path.join(root, M, "src/org/lumen/menu/*.java")) + glob.glob(os.path.join(root, M, "src/org/lumen/menu/utils/*.java"))
    c = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out] + src + [os.path.join(root, M, "test/FocusGuideTest.java")],
                       capture_output=True, text=True)
    if c.returncode != 0:
        return True, "compile: " + c.stderr[-300:]
    r = subprocess.run(["java", "-cp", out, "org.lumen.menu.FocusGuideTest", root], capture_output=True, text=True)
    return r.returncode != 0, r.stdout


def main():
    only = os.environ.get("MUT_ONLY", "")
    tmp, root = scratch()
    try:
        for which in ("screens", "menu"):
            red, out = suite(root, which)
            if red:
                print("the unmutated %s suite is red already:\n%s" % (which, out[-1500:]))
                sys.exit(2)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    killed, survived = 0, []
    for name, which, rel, old, new in MUTANTS:
        if only and not re.search(only, name):
            continue
        tmp, root = scratch()
        try:
            p = os.path.join(root, rel)
            s = open(p, encoding="utf-8").read()
            if s.count(old) != 1:
                print("BROKEN   %s (pattern %d times)" % (name, s.count(old)))
                survived.append(name)
                continue
            open(p, "w", encoding="utf-8").write(s.replace(old, new))
            red, out = suite(root, which)
            if red:
                killed += 1
                why = next((l for l in out.splitlines() if "FAIL" in l), out.strip().splitlines()[-1] if out.strip() else "red")
                print("killed   %-24s %s" % (name, why[:110]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print("x50-feedback1 mutants: %d killed, %d survived" % (killed, len(survived)))
    sys.exit(0 if not survived else 1)


if __name__ == "__main__":
    main()
