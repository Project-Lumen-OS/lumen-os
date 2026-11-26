#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Emulator mutants of the quick menu tiles (2026-11-24, any row of the full menu as a tile): each mutant breaks
one rule of the Android side (the tile works its row of the full menu, the strip, the greyed tile, the editor's All
settings list) and test/ui/quick_walk.py on the X50 Ultra (or, where named, the Z9X) test APK must turn red. The host side has its mutants in
test/mutate.py. Edits the files IN PLACE and puts each one back: run it only on a scratch clone, with an emulator.

    python3 apps/core/lumen-menu/test/ui/mutate_quick.py SERIAL [OUT]
    MUT_ONLY=REGEX ...   only the mutants whose name matches
"""
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(os.path.dirname(HERE))
P = os.path.join(MOD, "src/org/lumen/projector/")
MC, UI = P + "panel/MenuController.java", P + "ui/PageAccess.java"
MODELS = {"x50": ("g0111", "X50Projector", ["picture.memc", "picture.dynamic_black", "--via", "Professional settings"]),
          "z9x": ("horse_popular", "Z9xProjector", ["picture.memc", "picture.ai_contrast"])}
MUTANTS = [
    ("choice tile: OK no longer steps", MC, "PageAccess.cycle((ChoiceRow) row);\n", "PageAccess.activate((Row) row);\n"),
    ("choice tile: no round to the first option", UI, "        if (r.getSelected() + 1 >= choiceCount(r)) r.setSelected(-1);", ""),
    ("greyed tile: still acts", MC, "            if (!rowOpen(row)) { host.toast(R.string.lumen_qe_unavailable); return true; }\n", ""),
    ("greyed tile: not dimmed", MC, "        t.setUsable(rowOpen(row));\n", ""),
    ("switch tile: not lit", MC, "            t.setActive(Boolean.TRUE.equals(on));\n", ""),
    ("slider tile: no strip", MC, "            if (row instanceof SliderRow) { adjust().startOn(row, adjustWalk(row)); return true; }\n", ""),
    ("strip: LEFT and RIGHT not handed to the row", P + "panel/AdjustMode.java", "        if (step) row.dispatchKeyEvent(ev);", ""),
    ("strip: walks only the row itself (a row of a section page: Z9X contrast)", MC,
     "        for (Page p : pages.values()) if (p.rows().contains(row)) return p.rows();\n", "", "z9x"),
    ("strip: walks only the row itself (a row of a sub page: X50 contrast)", MC,
     "        for (Page p : subPages.values()) if (p.rows().contains(row)) return p.rows();\n", ""),
    ("tile value: not from the full menu's row", MC, "            else if (row instanceof Row) shown = rowValue(t, row);   // what the full menu's row shows now\n", ""),
    ("editor: the full menu rows not in the list", P + "panel/QuickEditMode.java",
     "for (QuickItem it : ed.catalog().group(g)) sheet.add(", "for (QuickItem it : ed.catalog().group(g)) if (it.section == null) sheet.add("),
    ("editor: the search result dropped", P + "panel/QuickEditMode.java", "            else showRows(null);\n", ""),
    ("editor: the add from the search goes back to the catalogue", P + "panel/QuickEditMode.java",
     "        if (list == ROWS) showRows(key);\n        else showCatalogue(key);", "        showCatalogue(key);"),
    ("editor: reset keeps the user's menu", os.path.join(MOD, "src/org/lumen/menu/quick/QuickEditor.java"), "    public void reset() {\n", "    public void reset() { if (true) return;\n"),
]


def main():
    serial = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else tempfile.mkdtemp()
    adb = os.environ.get("ADB", "adb")
    env = dict(os.environ, WALK="QuickWalkActivity", QUICK_WALK_FIRST_FAIL="1")
    killed, survived = 0, []
    only = os.environ.get("MUT_ONLY", "")
    for m in MUTANTS:
        name, path, old, new = m[:4]
        if only and not re.search(only, name):
            continue
        dev, app, walk_args = MODELS[m[4] if len(m) > 4 else "x50"]
        src = open(path, encoding="utf-8").read()
        if src.count(old) != 1:
            print("BROKEN  " + name + " (anchor found %d times)" % src.count(old))
            survived.append(name)
            continue
        open(path, "w", encoding="utf-8").write(src.replace(old, new))
        try:
            b = subprocess.run(["bash", os.path.join(HERE, "build.sh"), out, dev, app], env=env,
                               capture_output=True, text=True)
            if b.returncode != 0:
                print("KILLED  " + name + " (does not compile)")
                killed += 1
                continue
            pkg = open(os.path.join(out, "package")).read().strip()
            subprocess.run([adb, "-s", serial, "install", "-r", "-g", os.path.join(out, "walk.apk")], capture_output=True)
            subprocess.run([adb, "-s", serial, "shell", "cmd", "locale", "set-app-locales", pkg, "--locales", "en-US"])
            w = subprocess.run([sys.executable, os.path.join(HERE, "quick_walk.py"), serial, pkg] + walk_args, env=env,
                               capture_output=True, text=True)
            fail = [l for l in w.stdout.splitlines() if l.startswith("FAIL ")]
            if w.returncode != 0 and fail:
                print("KILLED  " + name + "  <- " + fail[0][5:])
                killed += 1
            else:
                print("SURVIVED " + name)
                survived.append(name)
        finally:
            open(path, "w", encoding="utf-8").write(src)
    print("quick tile emulator mutants: %d killed, %d survived %s" % (killed, len(survived), survived))
    return 1 if survived else 0


if __name__ == "__main__":
    sys.exit(main())
