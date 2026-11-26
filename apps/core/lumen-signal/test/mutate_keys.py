#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""mutate_keys.py: mutants of the developer card key routing (2026-11-21: OK on the gear of the quick menu over
the signal panel switched the panel's option). Each mutant puts one rule back the old or a wrong way in a scratch copy
of the files it touches, runs test/run.sh on that copy, and must turn it red. Run: python3 test/mutate_keys.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SIG = "apps/core/lumen-signal/src/org/lumen/signal/"
SEN = "apps/core/lumen-sensors/src/org/lumen/sensors/"
MUTANTS = [
    ("panel takes OK without its focus", SIG + "OkHold.java",
     "return panelHasKeys ? Shown.SHEET : Shown.NONE;", "return Shown.SHEET;"),
    ("BACK taken in the full panel", SIG + "OkHold.java",
     "owned = k == Key.OK && shown == Shown.SHEET;", "owned = shown == Shown.SHEET;"),
    ("BACK armed in the full panel", SIG + "OkHold.java",
     "armed = k == Key.OK ? shown != Shown.NONE : shown == Shown.CARD;", "armed = shown != Shown.NONE;"),
    ("held BACK hides nothing", SIG + "OkHold.java",
     "return shown == Shown.CARD ? Act.HIDE : Act.NONE;", "return Act.NONE;"),
    ("timer due in the past", SIG + "OkHold.java", "return Math.max(due, now);", "return due;"),
    ("CardHold ignores the panel's focus", SIG + "CardHold.java", "card.hasKeys(), KeyHook", "true, KeyHook"),
    ("CardHold ignores the app's windows", SIG + "CardHold.java", "KeyHook.appHasKeys())", "false)"),
    ("CardHold timer at due", SIG + "CardHold.java",
     "OkHold.timerAt(s.due, SystemClock.uptimeMillis())", "s.due"),
    ("CardHold BACK as any key", SIG + "CardHold.java",
     "KeyEvent.KEYCODE_BACK ? OkHold.Key.BACK", "KeyEvent.KEYCODE_BACK + 1 == 0 ? OkHold.Key.BACK"),
    ("overlay window focus not asked", SIG + "OverlayWindow.java", " && root.hasWindowFocus(); }", "; }"),
    ("card window stays not focusable", SIG + "CardKeys.java",
     "p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;", ""),
    ("pinned card back at once", SIG + "PanelToggle.java",
     "        held = false;\n        if (pinned) dismissed = true;\n        return mode;\n    }\n\n    /** The full panel on screen",
     "        held = false;\n        return mode;\n    }\n\n    /** The full panel on screen"),
    ("menu windows not reported", "apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java",
     "KeyHook.windowFocus(this, hasFocus);", ""),
    ("a stopped activity keeps the keys", SIG + "CardHold.java",
     "@Override public void onActivityStopped(Activity a) { KeyHook.windowFocus(a, false); }",
     "@Override public void onActivityStopped(Activity a) {}"),
    ("activities not followed", SIG + "CardHold.java", "        followActivities(sApp);\n", ""),
    ("sensor card without the hint", SEN + "SensorWindow.java", "box.addView(w.cardHint(look), w.margins(0, 14, 0, 0));", ""),
    ("signal card without the pin mark", SIG + "SignalWindow.java", "if (pinned) top.addView(", "if (false) top.addView("),
    # 2026-11-23, one rule for every overlay: nothing is an overlay's while a Lumen panel is in front
    ("an overlay acts under a Lumen panel in front", SIG + "OkHold.java",
     "if (appHasKeys || !mode.visible()) return Shown.NONE;", "if (!mode.visible()) return Shown.NONE;"),
    ("overlay takes keys under a panel in front", SIG + "OverlayWindow.java",
     "&& !folding && !KeyHook.appHasKeys(); }", "&& !folding; }"),
    ("overlay added focusable whatever is in front", SIG + "OverlayWindow.java",
     "        else flags = focusFlags(flags, takesKeys());\n", ""),
    ("overlay never takes the keys back", SIG + "OverlayWindow.java",
     "            okDown = false;\n            if (takesKeys()) root.requestFocus();\n", "            okDown = false;\n"),
    ("overlays not told of panels", SIG + "OverlayWindow.java", "        KeyHook.watch(OverlayWindow::panelsChanged);\n", ""),
    ("panels in front not counted", "apps/core/lumen-remote/src/org/lumen/remote/keys/KeyHook.java",
     "return !FOCUSED.isEmpty() || !FRONT.isEmpty();", "return !FOCUSED.isEmpty();"),
    ("focus change not told", "apps/core/lumen-remote/src/org/lumen/remote/keys/KeyHook.java",
     "if (put(FOCUSED, window, focused)) changed();", "put(FOCUSED, window, focused);"),
    ("menu panel not in front", "apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java",
     "== 0) KeyHook.front(p, true);", "== 0) { }"),
    ("menu panel stays in front after dismiss", "apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java",
     "        p.showing = false;\n        KeyHook.front(p, false);\n", "        p.showing = false;\n"),
    ("resumed screen not in front", SIG + "CardHold.java",
     "onActivityResumed(Activity a) { KeyHook.front(a, true); }", "onActivityResumed(Activity a) {}"),
    ("wall colour screen not in front", "apps/core/lumen-wallcolor/src/org/lumen/wallcolor/WallColorWindows.java",
     "            if (focusable) KeyHook.front(host, true);\n", ""),
]


COPIED = ("apps/core/lumen-signal", "apps/core/lumen-sensors", "apps/core/lumen-remote", "apps/common/projector",
          "apps/core/lumen-wallcolor")


def scratch():
    """A tree that links everything but the copied modules, so the suite reads the real recipes, devices and apps."""
    tmp = tempfile.mkdtemp(prefix="mut_keys_")

    def mirror(rel):
        os.makedirs(os.path.join(tmp, rel), exist_ok=True)
        for e in os.listdir(os.path.join(G, rel)):
            sub = os.path.join(rel, e) if rel else e
            if sub in COPIED:
                shutil.copytree(os.path.join(G, sub), os.path.join(tmp, sub), symlinks=True)
            elif any(c.startswith(sub + "/") for c in COPIED):
                mirror(sub)
            else:
                os.symlink(os.path.join(G, sub), os.path.join(tmp, sub))
    mirror("")
    return tmp


def suite(tmp):
    r = subprocess.run(["bash", os.path.join(tmp, "apps/core/lumen-signal/test/run.sh")],
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    out = r.stdout.decode("utf-8", "replace").splitlines()
    return r.returncode, [l for l in out if l.startswith("FAIL") or "error:" in l]


def main():
    tmp = scratch()
    try:
        rc, why = suite(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print("BROKEN scratch: the unmutated copy is red: %s" % why[:3])
        return 2
    print("ok   unmutated scratch green")
    killed, alive = 0, []
    for name, rel, old, new in MUTANTS:
        tmp = scratch()
        try:
            p = os.path.join(tmp, rel)
            text = open(p, encoding="utf-8").read()
            if text.count(old) != 1:
                alive.append(name + " (anchor not found once)")
                print("ALIVE   %s (anchor not found once)" % name)
                continue
            open(p, "w", encoding="utf-8").write(text.replace(old, new))
            rc, why = suite(tmp)
            if rc != 0:
                killed += 1
                print("killed  %-40s %s" % (name, why[0][:90] if why else ""))
            else:
                alive.append(name)
                print("ALIVE   " + name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print("mutate_keys: %d killed, %d alive" % (killed, len(alive)))
    return 1 if alive else 0


if __name__ == "__main__":
    sys.exit(main())
