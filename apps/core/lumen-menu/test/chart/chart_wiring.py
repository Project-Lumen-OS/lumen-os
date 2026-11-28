#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Wiring of the stock focus chart and edge frame (org.lumen.menu.FocusChart, EdgeFrame) in every family's screens:
the chart style from the model profile, OK toggles it at its first DOWN, the lens step turns the dial, the edge kind of
each geometry screen as its stock window, no grey grid left, no stock drawable copied.
  python3 apps/core/lumen-menu/test/chart/chart_wiring.py [REPO_ROOT]"""
import os, re, sys

G = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), *[".."] * 5))
bad = 0


def ok(what, good):
    global bad
    print(("ok   " if good else "FAIL ") + what)
    bad |= not good


def read(p):
    return open(os.path.join(G, p), encoding="utf-8").read()


CORE = "apps/LumenProjector/core/src/org/lumen/projector/"
X50 = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
for name, p, key in (("core manual focus", CORE + "ManualFocusActivity.java", "ev"), ("X50 manual focus", X50 + "FocusActivity.java", "e")):
    s = read(p)
    ok(name + ": style from the model profile", "FocusChart.style(MenuController.profile(this))" in s)
    ok(name + ": chart shown at open only where stock has one", "chart.setVisibility(FocusChart.shownAtOpen(style) ? View.VISIBLE : View.GONE)" in s)
    ok(name + ": OK keys toggle", all(k in s for k in ("case KeyEvent.KEYCODE_DPAD_CENTER:", "case KeyEvent.KEYCODE_ENTER:", "case KeyEvent.KEYCODE_NUMPAD_ENTER:")))
    ok(name + ": at the first DOWN only", key + ".getRepeatCount() == 0 && chart != null" in s)
    ok(name + ": toggles visibility", "chart.setVisibility(chart.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)" in s)
    ok(name + ": the lens step turns the dial", re.search(r"chart\.turn\((code == KeyEvent\.KEYCODE_DPAD_LEFT|near) \? 1 : -1\)", s) is not None)
    ok(name + ": the OK hint shown", "R.string.lumen_focus_chart_hint" in s)
ok("X50 manual focus: over the menu the chart stays full screen", "root.setPadding" not in read(X50 + "FocusActivity.java"))
P = CORE + "panel/"
for p, kind in ((P + "ZoomShiftPanel.java", "GUIDES"), (P + "OpticalZoomPanel.java", "GUIDES"), (P + "RotatePanel.java", "FRAME"),
                (P + "Play6ZoomShiftPanel.java", "FRAME"), (X50 + "LensActivity.java", "GUIDES"), (X50 + "MoveActivity.java", "FRAME"),
                (X50 + "RotateActivity.java", "FRAME")):
    s = read(p)
    ok(os.path.basename(p) + ": stock edge " + kind, "new EdgeFrameView(" in s and "EdgeFrame." + kind + ")" in s
       and len(re.findall(r"new EdgeFrameView\(", s)) == 1)
    ok(os.path.basename(p) + ": no grey grid", "0xFF8A8A8A" not in s and "new EdgeGrid(" not in s)
ok("EdgeGrid draws nothing", "onDraw" not in read(P + "EdgeGrid.java"))
# ---- the test chart of the geometry screens (tester 2026-11-28: OK shows / hides it, as on the focus screen; stock
# shows none there, so it is hidden at open; the screen's own second key moves to hold OK or MENU)
MP = "apps/core/lumen-menu/src/org/lumen/projector/panel/"
ef = read(MP + "EdgeFrameView.java")
ok("edge frame: the model's focus chart", "new FocusChartPainter(FocusChart.geometry(MenuController.profile(getContext())))" in ef)
ok("edge frame: chart hidden at open", "private boolean chartShown;" in ef)
ok("edge frame: chart drawn on the page, under the bands", "chart.draw(c" in ef and "c.drawColor(EdgeFrame.PAGE)" in ef
   and ef.index("c.drawColor(EdgeFrame.PAGE)") < ef.index("chart.draw(c") < ef.index("EdgeFrame.bands(kind)"))
ok("focus chart view: the shared painter", "chart.draw(c, getWidth(), getHeight())" in read(MP + "FocusChartView.java"))
for p, key, hold in ((P + "ZoomShiftPanel.java", "ev", True), (P + "OpticalZoomPanel.java", "ev", True),
                     (P + "Play6ZoomShiftPanel.java", "ev", True), (P + "RotatePanel.java", "ev", False),
                     (X50 + "LensActivity.java", "e", True), (X50 + "MoveActivity.java", "e", False),
                     (X50 + "RotateActivity.java", "e", False)):
    s, name = read(p), os.path.basename(p)
    ok(name + ": the test chart on the edge frame", ".withChart()" in s)
    ok(name + ": every OK event fed to OkPress", "okPress.feed(%s.getAction(), %s.getRepeatCount(), %s.getDownTime(), "
       "%s.getEventTime(), %s.isCanceled())" % ((key,) * 5) in s)
    ok(name + ": a short OK toggles the chart", re.search(r"== OkPress\.SHORT( && frame != null)?\) frame\.toggleChart\(\)", s) is not None)
    ok(name + ": the OK hint line", "R.string.lumen_focus_chart_hint" in s)
    h = s.find("press == OkPress.HOLD")
    if hold:
        ok(name + ": hold OK switches Size / Position", h >= 0 and re.search(r"shiftMode = !shiftMode|switchPage\(\)", s[h:h + 260]) is not None)
    else:
        ok(name + ": no hold use", h < 0)
s = read(P + "RotatePanel.java")
ok("rotate: MENU = back to the start", "boolean ok = code == KeyEvent.KEYCODE_MENU;" in s and "protected boolean takesMenuKey() { return true; }" in s)
s = read(X50 + "RotateActivity.java")
ok("X50 rotate: MENU = reset, OK no longer", "if (Menu.cornerResetKey(code, e.getAction())) key(this::reset);" in s and s.count("key(this::reset)") == 2)   # MENU and the held-key path
for p in (P + "ManualKeystonePanel.java", P + "Play6KeystonePanel.java"):
    s, name = read(p), os.path.basename(p)
    ok(name + ": MENU views grid, chart, preview, quad", "VIEW_FRAME = 0, VIEW_CHART = 1, VIEW_PREVIEW = 2, VIEW_QUAD = 3, VIEWS = 4" in s
       and "viewMode = (viewMode + 1) % VIEWS;" in s)
    ok(name + ": the chart view draws the model's chart", "new FocusChartPainter(FocusChart.geometry(MenuController.profile(c)))" in s
       and "chart.draw(c, (int) w, (int) h);" in s)
s = read(X50 + "CornerActivity.java")
ok("X50 corners: the model's chart under the corner page, hidden at open", "new FocusChartView(this, FocusChart.geometry(MenuController.profile(this)))" in s
   and "chart.setVisibility(View.GONE);" in s and s.index("root.addView(chart") < s.index("root.addView(view"))
ok("X50 corners: short gear = chart, held gear = reset", "if (held) reset();\n        else toggleChart();" in s
   and "view.setPage(!on);" in s)
ok("X50 corners: the grey page can step aside", "if (page) c.drawRect(0, 0, w, h, fill);" in read(MP + "CornerView.java"))
s = read(P + "ManualKeystonePanel.java")
ok("keystone: OK next corner, hold OK reset (stock order)", "if (press == OkPress.HOLD) requestReset();" in s
   and "else if (press == OkPress.SHORT) nextCorner();" in s)
CS = "apps/core/lumen-common-strings/res/"
KS = "apps/LumenProjector/core/res/"
def string(path, name):
    m = re.search(r'<string name="%s"[^>]*>(.*?)</string>' % name, read(path))
    return m.group(1) if m else None
locs = sorted(d for d in os.listdir(os.path.join(G, CS)) if d.startswith("values"))
ok("18 languages", len(locs) == 18)
for l in locs:
    cs = CS + l + "/lumen_strings.xml"
    hold_k = re.split(r" ?[:：]", string(KS + l + "/strings_kst.xml", "kst_hint_more"))[0]
    big, mv = string(cs, "lumen_right_bigger_left_smaller_ok"), string(cs, "lumen_arrows_move_the_picture_ok")
    rot, grid = string(cs, "lumen_right_left_rotate_ok_back"), string(cs, "lumen_menu_grid_over_the_picture")
    chart = re.split(r"OK[\s\u00a0\u202f]?[:：][\s\u00a0\u202f]?", string(cs, "lumen_focus_chart_hint"))[-1]
    menu_k = re.split(r" ?[:：]", grid)[0]
    ok(l + ": size hint says hold OK", big is not None and big.split(" · ")[2].startswith(hold_k))
    ok(l + ": position hint says hold OK", mv is not None and mv.split(" · ")[1].startswith(hold_k))
    ok(l + ": rotate hint says MENU", rot is not None and rot.split(" · ")[1].startswith(menu_k))
    cm = string("apps/core/lumen-menu/res/%s/lumen_strings_corner.xml" % l, "lumen_corner_keys_more")
    ok(l + ": X50 corner keys name the chart first", cm is not None and chart in cm.split(" · ")[0])
    ok(l + ": keystone MENU views name the chart second", grid.count(" / ") == 3 and chart in grid.split(" / ")[1])
for d, _, fs in os.walk(os.path.join(G, "apps")):
    for f in fs:
        if re.match(r"(cp01_focus_bk|bg_manuel_focus_short|keystone_zoom_bk|keystone_zoom_move_bk|img_logo_focus3)\.", f):
            ok("no stock drawable copied: " + os.path.join(d, f), False)
print("chart wiring: " + ("RED" if bad else "all green"))
sys.exit(1 if bad else 0)
