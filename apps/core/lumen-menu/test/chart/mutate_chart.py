#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the focus chart and edge frame: each must turn ChartTest or chart_wiring.py red. Works on a scratch
copy of the files it needs (lumen-menu, the screens, the profiles), never in place.
  python3 apps/core/lumen-menu/test/chart/mutate_chart.py"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), *[".."] * 5))
M = "apps/core/lumen-menu/src/org/lumen/menu/"
CORE = "apps/LumenProjector/core/src/org/lumen/projector/"
X50 = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
MP = "apps/core/lumen-menu/src/org/lumen/projector/panel/"
MUTANTS = [
    ("full spokes every 3 degrees", M + "FocusChart.java", "{123f, 230f, 1, 3.5f, 1f, 4f}", "{123f, 230f, 1, 3.5f, 1f, 3f}"),
    ("full star smaller", M + "FocusChart.java", "{123f, 230f, 1,", "{123f, 200f, 1,"),
    ("full bars every 10 degrees", M + "FocusChart.java", "{70.5f, 108.5f, 1, 10.5f, 0f, 15f}", "{70.5f, 108.5f, 1, 10.5f, 0f, 10f}"),
    ("line in the middle", M + "FocusChart.java", "{56f, 848f, 1864f, 856f}", "{56f, 540f, 1864f, 548f}"),
    ("short page white", M + "FocusChart.java", "return SHORT.equals(drawn) ? CLEAR : WHITE;", "return WHITE;"),
    ("short chart has the line", M + "FocusChart.java", "return SHORT.equals(drawn) ? null : FULL_LINE;", "return FULL_LINE;"),
    ("off opens with the chart", M + "FocusChart.java", "return !OFF.equals(style);", "return true;"),
    ("short ignored", M + "FocusChart.java", "return SHORT.equals(s) || OFF.equals(s) ? s : FULL;", "return OFF.equals(s) ? s : FULL;"),
    ("72 ticks", M + "FocusChart.java", "TICK_STEP = 3.75f", "TICK_STEP = 5f"),
    ("rings turn 5 degrees", M + "FocusChart.java", "TURN_STEP = 10f", "TURN_STEP = 5f"),
    ("frame without grey", M + "EdgeFrame.java", "{{2f, 4f}, {4f, 8f}}", "{{4f, 8f}}"),
    ("frame white band 6 px", M + "EdgeFrame.java", "{4f, 8f}}", "{4f, 10f}}"),
    ("guides edge 6 px", M + "EdgeFrame.java", "GUIDES_BANDS = {{0f, 4f}}", "GUIDES_BANDS = {{0f, 6f}}"),
    ("grey page", M + "EdgeFrame.java", "PAGE = 0xFF000000", "PAGE = 0xFF8A8A8A"),
    ("dashes 8 px", M + "EdgeFrame.java", "DASH = 6f", "DASH = 8f"),
    ("dash phase lost", M + "EdgeFrame.java", "float s = phase + (float) Math.floor((from - phase) / period) * period;", "float s = from;"),
    ("frame shows guides", M + "EdgeFrame.java", "return GUIDES.equals(kind) ? GUIDE_LINES : new float[0][];", "return GUIDE_LINES;"),
    ("MIRA chart full", "devices/_family/mira/mira.menu.json", '"focus.manual": {"chart": "short"},', ""),
    ("Play 6 chart on", "devices/dragon_healthy/menu.json", '"focus.manual": {"chart": "off"},', ""),
    ("core: OK closes", CORE + "ManualFocusActivity.java", "            case KeyEvent.KEYCODE_DPAD_CENTER:\n", ""),
    ("core: OK repeat toggles", CORE + "ManualFocusActivity.java", "ev.getRepeatCount() == 0 && chart != null", "chart != null"),
    ("core: chart always at open", CORE + "ManualFocusActivity.java", "chart.setVisibility(FocusChart.shownAtOpen(style) ? View.VISIBLE : View.GONE);", ""),
    ("core: no dial turn", CORE + "ManualFocusActivity.java", "if (chart != null) chart.turn(code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1);", ""),
    ("X50: OK closes", X50 + "FocusActivity.java", "            case KeyEvent.KEYCODE_DPAD_CENTER:\n", ""),
    ("X50: no OK hint", X50 + "FocusActivity.java", "hint.setText(R.string.lumen_focus_chart_hint);", ""),
    ("X50: chart under the menu padding", X50 + "FocusActivity.java", "lp.rightMargin = dp(MenuActivity.PANEL_DP);", "root.setPadding(0, 0, dp(MenuActivity.PANEL_DP), 0);"),
    ("zoom shift: frame only", CORE + "panel/ZoomShiftPanel.java", "EdgeFrame.GUIDES)", "EdgeFrame.FRAME)"),
    ("optical zoom: frame only", CORE + "panel/OpticalZoomPanel.java", "EdgeFrame.GUIDES)", "EdgeFrame.FRAME)"),
    ("rotate: guides", CORE + "panel/RotatePanel.java", "EdgeFrame.FRAME)", "EdgeFrame.GUIDES)"),
    ("Play 6 zoom: guides", CORE + "panel/Play6ZoomShiftPanel.java", "EdgeFrame.FRAME)", "EdgeFrame.GUIDES)"),
    ("X50 lens: no frame", X50 + "LensActivity.java", "new EdgeFrameView(this, EdgeFrame.GUIDES)", "new EdgeFrameView(this, null)"),
    ("X50 move: guides", X50 + "MoveActivity.java", "EdgeFrame.FRAME)", "EdgeFrame.GUIDES)"),
    ("X50 rotate: no frame", X50 + "RotateActivity.java", "new EdgeFrameView(this, EdgeFrame.FRAME)", "new EdgeFrameView(this, null)"),
    # the geometry screens' test chart (zoom-pattern-1128)
    ("chart shown at open", MP + "EdgeFrameView.java", "private boolean chartShown;", "private boolean chartShown = true;"),
    ("chart never drawn", MP + "EdgeFrameView.java", "        if (chartShown) chart.draw(c, (int) w, (int) h);\n", ""),
    ("chart not the model's", MP + "EdgeFrameView.java", "FocusChart.geometry(MenuController.profile(getContext()))", "FocusChart.FULL"),
    ("geometry chart off", M + "FocusChart.java", "public static String geometry(MenuProfile p) { return drawn(style(p)); }",
     "public static String geometry(MenuProfile p) { return style(p); }"),
    ("hold at once", M + "OkPress.java", "eventTime - downTime >= HOLD_MS", "eventTime - downTime >= 0"),
    ("short after a hold", M + "OkPress.java", "boolean isShort = down && !held && !canceled;", "boolean isShort = down && !canceled;"),
    ("lone UP counts", M + "OkPress.java", "boolean isShort = down && !held && !canceled;", "boolean isShort = !held && !canceled;"),
    ("cancelled UP counts", M + "OkPress.java", "boolean isShort = down && !held && !canceled;", "boolean isShort = down && !held;"),
    ("hold twice", M + "OkPress.java", "if (down && !held && eventTime", "if (down && eventTime"),
    ("no numpad enter", M + "OkPress.java", "{23, 66, 160}", "{23, 66}"),
    ("zoom shift: OK still switches", CORE + "panel/ZoomShiftPanel.java", "press == OkPress.SHORT && frame != null) frame.toggleChart()",
     "press == OkPress.HOLD && frame != null) frame.toggleChart()"),
    ("zoom shift: no chart hint", CORE + "panel/ZoomShiftPanel.java", "R.string.lumen_focus_chart_hint", "R.string.zs_title"),
    ("optical zoom: no hold switch", CORE + "panel/OpticalZoomPanel.java", "else if (press == OkPress.HOLD && Family.ZOOM_SHIFT",
     "else if (false && Family.ZOOM_SHIFT"),
    ("Play 6 zoom: no chart", CORE + "panel/Play6ZoomShiftPanel.java", "EdgeFrame.FRAME).withChart()", "EdgeFrame.FRAME)"),
    ("X50 lens: hold does nothing", X50 + "LensActivity.java", "                else if (press == OkPress.HOLD) switchPage();\n", ""),
    ("X50 move: OK does nothing", X50 + "MoveActivity.java", ") == OkPress.SHORT) frame.toggleChart();", ") == OkPress.SHORT) { }"),
    ("rotate: OK still undoes", CORE + "panel/RotatePanel.java", "boolean ok = code == KeyEvent.KEYCODE_MENU;", "boolean ok = OkPress.isOk(code);"),
    ("X50 rotate: OK resets", X50 + "RotateActivity.java", ") == OkPress.SHORT) frame.toggleChart();", ") == OkPress.SHORT) key(this::reset);"),
    ("keystone: three views", CORE + "panel/ManualKeystonePanel.java", "VIEW_QUAD = 3, VIEWS = 4", "VIEW_QUAD = 3, VIEWS = 3"),
    ("keystone: OK no corner", CORE + "panel/ManualKeystonePanel.java", "else if (press == OkPress.SHORT) nextCorner();", "else if (press == OkPress.SHORT) { }"),
    ("Play 6 keystone: chart not drawn", CORE + "panel/Play6KeystonePanel.java", "chart.draw(c, (int) w, (int) h);", "c.save();"),
    ("ru size hint old", "apps/core/lumen-common-strings/res/values-ru/lumen_strings.xml", "Удерживайте OK: положение", "OK: сдвиг картинки"),
    ("X50 corners: short gear nothing", X50 + "CornerActivity.java", "        else toggleChart();\n", ""),
    ("X50 corners: the grey page hides the chart", MP + "CornerView.java", "if (page) c.drawRect(0, 0, w, h, fill);", "c.drawRect(0, 0, w, h, fill);"),
    ("X50 corners: chart shown at open", X50 + "CornerActivity.java", "chart.setVisibility(View.GONE);", "chart.setVisibility(View.VISIBLE);"),
    ("ja grid without chart", "apps/core/lumen-common-strings/res/values-ja/lumen_strings.xml", "グリッド / テストチャート / ", "グリッド / "),
]


def run(root):
    out = tempfile.mkdtemp()
    try:
        view = subprocess.run(["python3", "-B", os.path.join(root, "tools/devices/menu_layers.py"), "--view", root, out + "/v"],
                              capture_output=True, text=True).stdout.strip()
        mod = os.path.join(root, "apps/core/lumen-menu")
        srcs = [os.path.join(mod, "src/org/lumen/menu", f) for f in os.listdir(os.path.join(mod, "src/org/lumen/menu")) if f.endswith(".java")]
        srcs += [os.path.join(mod, "src/org/lumen/menu/utils", f) for f in os.listdir(os.path.join(mod, "src/org/lumen/menu/utils"))]
        srcs += [os.path.join(mod, "src/org/lumen/menu/quick", f) for f in os.listdir(os.path.join(mod, "src/org/lumen/menu/quick"))]
        if subprocess.run(["javac", "-nowarn", "-proc:none", "-encoding", "UTF-8", "-d", out + "/c"] + srcs
                          + [os.path.join(mod, "test/chart/ChartTest.java")], capture_output=True).returncode:
            return False
        a = subprocess.run(["java", "-cp", out + "/c", "org.lumen.menu.ChartTest", view], capture_output=True).returncode
        b = subprocess.run(["python3", os.path.join(G, "apps/core/lumen-menu/test/chart/chart_wiring.py"), root], capture_output=True).returncode
        return a == 0 and b == 0
    finally:
        shutil.rmtree(out, ignore_errors=True)


def scratch():
    d = tempfile.mkdtemp()
    for p in ("apps/core/lumen-menu", "devices", "tools/devices", "apps/core/lumen-common-strings"):
        shutil.copytree(os.path.join(G, p), os.path.join(d, p), ignore=shutil.ignore_patterns("research", "image", "*.img", "*.zip", "test"))
    shutil.copytree(os.path.join(G, "apps/core/lumen-menu/test/chart"), os.path.join(d, "apps/core/lumen-menu/test/chart"), dirs_exist_ok=True)
    for p in ((CORE + "ManualFocusActivity.java", X50 + "FocusActivity.java", X50 + "LensActivity.java", X50 + "MoveActivity.java",
              X50 + "RotateActivity.java", CORE + "panel/ZoomShiftPanel.java", CORE + "panel/OpticalZoomPanel.java",
              CORE + "panel/RotatePanel.java", CORE + "panel/Play6ZoomShiftPanel.java", CORE + "panel/EdgeGrid.java",
              CORE + "panel/ManualKeystonePanel.java", CORE + "panel/Play6KeystonePanel.java", X50 + "CornerActivity.java")
              + tuple("apps/LumenProjector/core/res/%s/strings_kst.xml" % l for l in os.listdir(os.path.join(G, "apps/LumenProjector/core/res"))
                      if os.path.exists(os.path.join(G, "apps/LumenProjector/core/res", l, "strings_kst.xml")))):
        os.makedirs(os.path.dirname(os.path.join(d, p)), exist_ok=True)
        shutil.copy(os.path.join(G, p), os.path.join(d, p))
    return d


base = scratch()
if not run(base):
    print("base not green: no mutant run"); sys.exit(2)
survived = []
for name, p, old, new in MUTANTS:
    f = os.path.join(base, p)
    s = open(f, encoding="utf-8").read()
    if old not in s:
        print("STALE " + name); survived.append(name); continue
    open(f, "w", encoding="utf-8").write(s.replace(old, new, 1))
    killed = not run(base)
    open(f, "w", encoding="utf-8").write(s)
    print(("killed   " if killed else "SURVIVED ") + name)
    if not killed: survived.append(name)
shutil.rmtree(base, ignore_errors=True)
print("chart mutants: %d of %d killed" % (len(MUTANTS) - len(survived), len(MUTANTS)))
sys.exit(1 if survived else 0)
