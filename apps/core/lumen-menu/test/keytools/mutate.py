#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the shared tool key rule (org.lumen.menu.KeyTools): each one weakens the rule, a window host or the
glue of one projector app; test/keytools/run.sh must turn red for every one. Works on a scratch copy, never in place.

  python3 apps/core/lumen-menu/test/keytools/mutate.py
"""
import glob, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "../../../../.."))
sys.path.insert(0, HERE)
import wiring  # noqa: E402

KT = "apps/core/lumen-menu/src/org/lumen/menu/KeyTools.java"
UI = "apps/core/lumen-menu/src/org/lumen/projector/ui/"
OH = "apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java"
PN = "apps/common/projector/src/org/lumen/projector/ui/Panel.java"
AH = UI + "ActivityPanelHost.java"
TA = UI + "ToolActivity.java"
Z, HB, P6, X = wiring.Z, wiring.HB, wiring.P6, wiring.X
ASIDE_ON = "        if (on) {\n            main.removeCallbacks(autoHide);\n        } else {"
MUTANTS = [
    # ---- the rule
    ("a tool screen never gets the gear", KT, "        if (t == null) return false;\n        t.onMenuKey(held);\n        return true;", "        return false;"),
    ("the held gear reaches the tool as a short press", KT, "        t.onMenuKey(held);", "        t.onMenuKey(false);"),
    ("a tool panel never gets the gear", KT, "        for (Host h : HOSTS) if (h.menuKey(held)) return true;\n", ""),
    ("a menu opened over a tool screen loses the gear", KT, "        for (Host h : HOSTS) if (h.panelOnScreen()) return false;\n", ""),
    ("the menu never steps aside for a tool screen", KT, "        if (first) for (Host h : HOSTS) h.aside(true);", ""),
    ("the menu never comes back after the tool", KT, "        for (Host h : HOSTS) h.aside(false);", ""),
    ("the menu comes back while a tool is still in front", KT, "if (!FRONT.remove(t) || !FRONT.isEmpty()) return;", "if (!FRONT.remove(t)) return;"),
    # ---- overlay host (Z9X, HB, Play 6)
    ("overlay: no aside at all", OH, "        if (!onMain(\"aside\")) { main.post(() -> aside(on)); return; }", "        if (true) return;"),
    ("overlay: the aside menu stays focusable", OH, "lp.flags = on ? lp.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE : asideFlags;",
     "lp.flags = on ? lp.flags : asideFlags;"),
    ("overlay: the aside menu stays drawn", OH, "p.root.setVisibility(on ? View.INVISIBLE : View.VISIBLE);", "p.root.setVisibility(View.VISIBLE);"),
    ("overlay: the window is never updated", OH, "                wm.updateViewLayout(p.root, lp);\n", ""),
    ("overlay: the focus loss closes the menu under the tool", OH, "if (panel.showing && panel != aside && !hasWindowFocus()) {",
     "if (panel.showing && !hasWindowFocus()) {"),
    ("overlay: the auto-hide runs under the tool", OH, ASIDE_ON, "        if (on) {\n        } else {"),
    ("overlay: no fresh timer after the tool", OH, "            resetAutoHide(p);\n            if (p.root instanceof PanelRoot)",
     "            if (p.root instanceof PanelRoot)"),
    ("overlay: back without the focus never closes", OH, "            if (p.root instanceof PanelRoot) ((PanelRoot) p.root).graceAfterAside();\n", ""),
    ("overlay: the aside menu counts as on screen", OH, "        return p != null && p.showing && p != aside;", "        return p != null && p.showing;"),
    ("overlay: a tool panel never gets the gear", OH, "        return p != null && p != aside && p.menuKey(held);", "        return false;"),
    ("overlay: never joins the rule", OH, "        KeyTools.addHost(this);\n", ""),
    # ---- the panel side (common Panel)
    ("a short gear is no MENU press of the tool panel", PN, "        if (!held) {\n            long t", "        if (false) {\n            long t"),
    ("a held gear is a MENU press of the tool panel", PN, "        if (!held) {\n            long t", "        if (true) {\n            long t"),
    ("every panel takes the gear", PN, "    protected boolean takesMenuKey() { return false; }", "    protected boolean takesMenuKey() { return true; }"),
    # ---- a tool that hides the menu (the X50 subtitle mask editor, stock showOrHideSettingMenu(false))
    ("dbm: no tool ever hides the menu", KT, "        return t != null && t.hidesMenu();", "        return false;"),
    ("dbm: every tool hides the menu", KT, "        return t != null && t.hidesMenu();", "        return t != null;"),
    ("dbm: the panel stays seen under the mask editor", AH, "        boolean hide = on && KeyTools.frontHidesMenu();", "        boolean hide = false;"),
    ("dbm: the panel stays unseen after the mask editor", AH, "        boolean hide = on && KeyTools.frontHidesMenu();", "        boolean hide = KeyTools.frontHidesMenu() || !on && aside == null;"),
    ("dbm: the panel alpha never set", AH, "        if (p.root != null) p.root.setAlpha(hide ? 0f : 1f);\n", ""),
    ("dbm: the panel hidden by INVISIBLE (the row loses its focus)", AH, "        if (p.root != null) p.root.setAlpha(hide ? 0f : 1f);",
     "        if (p.root != null) p.root.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);"),
    ("dbm: the mask editor does not hide the menu", X + "SubtitleMaskActivity.java", "    @Override public boolean hidesMenu() { return true; }\n", ""),
    ("dbm: the gear does nothing in the mask editor", X + "SubtitleMaskActivity.java", "    @Override public void onMenuKey(boolean held) { back(); }", "    @Override public void onMenuKey(boolean held) { }"),
    ("dbm: BACK from a page row screen goes to the card", "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java",
     "        final Runnable back = section != null ? backToRow(section) : () -> { if (!ours()) show(null, null); };",
     "        final Runnable back = section != null ? backToRow(section) : () -> show(null, null);"),
    # ---- activity host (X50)
    ("activity host: no aside at all", AH, "        if (!onMain(\"aside\")) { main.post(() -> aside(on)); return; }", "        if (true) return;"),
    ("activity host: the focus loss closes the menu under the tool", AH, "if (panel.showing && panel != aside && !hasWindowFocus()) {",
     "if (panel.showing && !hasWindowFocus()) {"),
    ("activity host: the auto-hide runs under the tool", AH, ASIDE_ON, "        if (on) {\n        } else {"),
    ("activity host: a tool panel never gets the gear", AH, "        return p != null && p != aside && p.menuKey(held);", "        return false;"),
    ("activity host: never joins the rule", AH, "        KeyTools.addHost(this);\n", ""),
    # ---- tool screens join and leave
    ("a tool screen never joins", TA, "        KeyTools.shown(this);\n", ""),
    ("a tool screen never leaves", TA, "        KeyTools.gone(this);\n", ""),
]
# ---- the glue of every app (wiring.py)
# one core KeyReceiver for every gmpf family and the Play 6 (step 3b part 4)
MUTANTS += [
    ("the gear short opens the quick menu over a tool", Z + "KeyReceiver.java", " && !KeyTools.menuKey(false)) {", ") {"),
    ("the held gear opens the game menu / settings over a tool", Z + "KeyReceiver.java",
     "        if (KeyTools.menuKey(true)) return;", ""),
]
for kst, name in (("panel/ManualKeystonePanel.java", "Z9X"), ("panel/Play6KeystonePanel.java", "Play 6")):
    MUTANTS += [
        (name + ": the fine corner adjustment is no tool panel", Z + kst, wiring.TOOL_PANEL,
         "    protected boolean takesMenuKey() { return false; }"),
    ]
# manual focus is one core class for every family since step 3b part 3 (the Play 6 copy is gone)
MUTANTS += [
    ("manual focus is no tool screen", Z + "ManualFocusActivity.java", wiring.MF_TOOL,
     "public final class ManualFocusActivity extends android.app.Activity {"),
    ("the gear leaves manual focus open", Z + "ManualFocusActivity.java", "        if (motor != null) motor.stop();\n        if (!isFinishing()) finish();\n    }\n", "        if (motor != null) motor.stop();\n    }\n"),
]
for app, name in ((Z, "Z9X"),):
    MUTANTS.append((name + ": the zoom card is no tool panel", app + "panel/OpticalZoomPanel.java", wiring.TOOL_PANEL,
                    "    protected boolean takesMenuKey() { return false; }"))
MUTANTS += [
    ("Play 6: the zoom and shift card is no tool panel", Z + "panel/Play6ZoomShiftPanel.java", wiring.TOOL_PANEL,
     "    protected boolean takesMenuKey() { return false; }"),
    ("X50: the gear opens the menu over a tool", X + "KeyReceiver.java", " && KeyTools.menuKey(longPress)) return;", " && false) return;"),
    ("X50: manual focus is no tool screen", X + "FocusActivity.java", "extends ToolActivity {", "extends android.app.Activity {"),
    ("X50: the corner screen is no tool screen", X + "CornerActivity.java", "extends ToolActivity {", "extends android.app.Activity {"),
    ("X50: the lens screen is no tool screen", X + "LensActivity.java", "extends ToolActivity {", "extends android.app.Activity {"),
]


def copy_tree(dst):
    files = set(wiring.FILES) | {KT, OH, PN, AH, TA, UI + "PanelHost.java", UI + "PanelActivity.java",
                                 "apps/core/lumen-menu/src/org/lumen/menu/Rehost.java",
                                 "apps/core/lumen-remote/src/org/lumen/remote/keys/KeyHook.java"}
    for f in files:
        os.makedirs(os.path.join(dst, os.path.dirname(f)), exist_ok=True)
        shutil.copy(os.path.join(G, f), os.path.join(dst, f))
    t = "apps/core/lumen-menu/test/keytools"
    shutil.copytree(HERE, os.path.join(dst, t), ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))


def main():
    killed, survived = 0, []
    base = tempfile.mkdtemp(prefix="keytools_mut.")
    try:
        copy_tree(os.path.join(base, "clean"))
        r = subprocess.run(["sh", os.path.join(base, "clean/apps/core/lumen-menu/test/keytools/run.sh")], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stdout[-3000:] + r.stderr[-2000:])
            print("the unmutated suite is red: no mutant run")
            sys.exit(2)
        for i, (name, rel, old, new) in enumerate(MUTANTS):
            dst = os.path.join(base, "m%d" % i)
            copy_tree(dst)
            p = os.path.join(dst, rel)
            src = open(p, encoding="utf-8").read()
            if src.count(old) != 1:
                print("BROKEN mutant (pattern %d times): %s" % (src.count(old), name))
                survived.append(name)
                continue
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            r = subprocess.run(["sh", os.path.join(dst, "apps/core/lumen-menu/test/keytools/run.sh")], capture_output=True, text=True)
            if r.returncode != 0:
                killed += 1
                out = r.stdout.splitlines() + r.stderr.splitlines()
                why = next((l for l in out if l.startswith("FAIL")), next((l for l in out if "error:" in l), "red"))
                print("killed   %-62s %s" % (name, why[:100]))
            else:
                survived.append(name)
                print("SURVIVED %s" % name)
            shutil.rmtree(dst, ignore_errors=True)
    finally:
        shutil.rmtree(base, ignore_errors=True)
    print("keytools mutants: %d killed, %d survived" % (killed, len(survived)))
    sys.exit(0 if not survived else 1)


if __name__ == "__main__":
    main()
