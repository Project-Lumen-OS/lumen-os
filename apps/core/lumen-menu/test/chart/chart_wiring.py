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
for d, _, fs in os.walk(os.path.join(G, "apps")):
    for f in fs:
        if re.match(r"(cp01_focus_bk|bg_manuel_focus_short|keystone_zoom_bk|keystone_zoom_move_bk|img_logo_focus3)\.", f):
            ok("no stock drawable copied: " + os.path.join(d, f), False)
print("chart wiring: " + ("RED" if bad else "all green"))
sys.exit(1 if bad else 0)
