#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Wiring of the shared tool key rule (org.lumen.menu.KeyTools) in every projector app (Z9X, HB, Play 6, X50 / RS30).

KeyToolsTest proves the rule and the window hosts; this checks the device glue it relies on, by exact source lines:
  - every KeyReceiver asks the rule first: the gear's short press toggles the quick menu only when no tool took it, its
    long press and the remote's own gear hold (gearLong) run only then; the X50 never over the eye-protection cover;
  - every manual focus screen and the X50 corner and lens screens are tool screens (ToolActivity: in front at onResume,
    gone at onPause) with their menu key; manual focus closes on it as on every other key;
  - every panel whose hint names Menu (fine corner adjustment, zoom card, Play 6 zoom and shift card) is a tool panel;
  - no per-screen copy of the rule is left (Play6ZoomShiftPanel.onMenuKey, the X50 EXTRA_RESET path).

  python3 apps/core/lumen-menu/test/keytools/wiring.py [GSI_ROOT]
"""
import os, sys

Z, HB, P6, X = ("apps/LumenProjector/core/src/org/lumen/projector/", "apps/HbProjector/src/org/lumen/projector/",
                "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/", "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/")
GEAR_SHORT = ("        if (action == KeyEvent.ACTION_UP && GEAR.up(ev.getDownTime(), ev.isCanceled()) && !KeyTools.menuKey(false)) {\n"
              "            QuickPanel.toggle(ctx);")
GEAR_LONG = "    private static void gearLong(Context ctx) {\n        if (KeyTools.menuKey(true)) return;"
MF_TOOL = "public final class ManualFocusActivity extends ToolActivity {"
MF_MENU = ("    public void onMenuKey(boolean held) {\n        if (motor != null) motor.stop();\n"
           "        if (!isFinishing()) finish();\n    }")
TOOL_PANEL = "    protected boolean takesMenuKey() { return true; }"
# (file, what, lines that must be there, lines that must not)
CHECKS = []
# one core KeyReceiver for every gmpf family and the Play 6 (step 3b part 4)
CHECKS.append((Z + "KeyReceiver.java", "the gear asks the tool rule before the quick menu and the long action",
               [GEAR_SHORT, GEAR_LONG, "import org.lumen.menu.KeyTools;"], ["Play6ZoomShiftPanel.onMenuKey"]))
# one ManualFocusActivity in the core for every gmpf family (HB since step 2c, Play 6 since step 3b); the Play 6 corner
# card is the core feature Play6KeystonePanel (step 3b part 7)
CHECKS.append((Z + "ManualFocusActivity.java", "manual focus is a tool screen the gear closes", [MF_TOOL, MF_MENU], []))
for kst in ("panel/ManualKeystonePanel.java", "panel/Play6KeystonePanel.java"):
    CHECKS.append((Z + kst, "the fine corner adjustment is a tool panel (Menu: view)",
                   [TOOL_PANEL, "            case KeyEvent.KEYCODE_MENU:"], []))
for app in (Z,):
    CHECKS.append((app + "panel/OpticalZoomPanel.java", "the zoom card is a tool panel (Menu: zoom reset)",
                   [TOOL_PANEL, "        if (code == KeyEvent.KEYCODE_MENU) {"], []))
CHECKS += [
    (Z + "panel/Play6ZoomShiftPanel.java", "the Play 6 zoom and shift card is a tool panel (Menu: side menu)",
     [TOOL_PANEL, "        if (code == KeyEvent.KEYCODE_MENU) {"], ["public static boolean onMenuKey("]),
    (X + "KeyReceiver.java", "the X50 gear asks the tool rule before any menu, never over the cover",
     ["        if (a != GearAction.NOTHING && a != GearAction.COVER_MENU && KeyTools.menuKey(longPress)) return;\n"
      "        switch (a) {"], ["resetCorners(", "EXTRA_RESET"]),
    (X + "FocusActivity.java", "X50 manual focus is a tool screen the gear closes",
     ["public final class FocusActivity extends ToolActivity {", "    @Override public void onMenuKey(boolean held) {\n"
      "        Focuser.stop();\n        finish();"], []),
    (X + "SubtitleMaskActivity.java", "the X50 subtitle mask editor is a tool screen that hides the menu as stock, the gear closes it",
     ["public final class SubtitleMaskActivity extends ToolActivity {", "    @Override public void onMenuKey(boolean held) { back(); }",
      "    @Override public boolean hidesMenu() { return true; }"], []),
    ("apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java",
     "BACK from a screen of a row inside a page keeps the menu on that page and row (the subtitle masks)",
     ["        final Runnable back = section != null ? backToRow(section) : () -> { if (!ours()) show(null, null); };"],
     ["        final Runnable back = backToRow(lastSectionOf(f));"]),
    ("apps/core/lumen-menu/src/org/lumen/projector/ui/ActivityPanelHost.java", "a tool that hides the menu has the panel unseen under it",
     ["        boolean hide = on && KeyTools.frontHidesMenu();",
      "        if (p.root != null) p.root.setAlpha(hide ? 0f : 1f);"], ["p.root.setVisibility(hide"]),
    (X + "CornerActivity.java", "the X50 corner screen is a tool screen (held gear: reset)",
     ["public final class CornerActivity extends ToolActivity {", "        if (held) reset();"], ["EXTRA_RESET"]),
    (X + "LensActivity.java", "the X50 lens screen is a tool screen (held gear: stock MENU)",
     ["public final class LensActivity extends ToolActivity {", "        if (!held || closed) return;"], []),
    ("apps/core/lumen-menu/src/org/lumen/projector/ui/ToolActivity.java", "a tool screen joins at onResume, leaves at onPause",
     ["        super.onResume();\n        KeyTools.shown(this);", "        KeyTools.gone(this);\n        super.onPause();"], []),
    ("apps/core/lumen-menu/src/org/lumen/projector/ui/ActivityPanelHost.java", "the activity host joins the rule",
     ["        KeyTools.addHost(this);", "public final class ActivityPanelHost implements PanelHost, KeyTools.Host {"], []),
    ("apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java", "the overlay host joins the rule",
     ["        KeyTools.addHost(this);", "public final class OverlayHost implements KeyTools.Host {"], []),
]
FILES = sorted({c[0] for c in CHECKS})


def main(g):
    bad = 0
    for f, what, need, never in CHECKS:
        try:
            s = open(os.path.join(g, f), encoding="utf-8").read()
        except OSError as e:
            print("FAIL %s: %s" % (f, e)); bad += 1; continue
        miss = [n.splitlines()[0].strip() for n in need if n not in s]
        left = [n for n in never if n in s]
        if miss or left:
            bad += 1
            print("FAIL %s: %s (missing %s%s)" % (f, what, miss, (", still there %s" % left) if left else ""))
        else:
            print("ok   %s: %s" % (f.split("/src/")[0].split("/")[-1] + "/" + os.path.basename(f), what))
    print("keytools wiring: %d checks, %s" % (len(CHECKS), "all green" if not bad else "%d RED" % bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../../.."))))
