#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Targeted mutants of the Z9X rows of the shared picture settings rule (Z9xMenuRules over lumen-menu
PictureSettingsRule): each mutant must turn sh apps/LumenProjector/core/test/rules/run.sh red. Works on a scratch copy.

  python3 apps/LumenProjector/core/test/rules/picture_mutate.py
"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", ".."))
R = "apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xMenuRules.java"
CALL = "PictureSettingsRule.blocked(PICTURE_PAGES, f, in.gameActive55, in.pictureMode, in.hdr)"
MUTANTS = [
    ("the Z9X tree as one page", R, "PICTURE_PAGES = PictureSettingsRule.BASIC_AND_PRO;", "PICTURE_PAGES = PictureSettingsRule.ONE_PAGE;"),
    ("no page rule at all", R, "        return " + CALL + ";", "        return null;"),
    ("the game state not passed", R, CALL, CALL.replace("in.gameActive55", "null")),
    ("the content not passed", R, CALL, CALL.replace("in.hdr)", "null)")),
    ("every AI scene read as 10", R, CALL, CALL.replace("in.pictureMode,", "in.pictureMode == null ? null : in.pictureMode >= 10 && in.pictureMode <= 17 ? 10 : in.pictureMode,")),
    ("the sliders free", R, "            return in == null ? null : pictureSettings(f, in);\n        }\n        switch (f) {",
     "            return null;\n        }\n        switch (f) {"),
    ("the sliders with the page of the professional entry", R, "            return in == null ? null : pictureSettings(f, in);\n        }\n        switch (f) {",
     "            return in == null ? null : pictureSettings(FeatureId.PICTURE_PRO, in);\n        }\n        switch (f) {"),
    ("colour temperature free", R, "                if (in == null || in.lumensMode == null) return SILENT;\n                Note n = pictureSettings(f, in);",
     "                if (in == null || in.lumensMode == null) return SILENT;\n                Note n = null;"),
    ("colour temperature: the rule before the missing read", R,
     "                if (in == null || in.lumensMode == null) return SILENT;\n                Note n = pictureSettings(f, in);",
     "                Note n = in == null ? null : pictureSettings(f, in);\n                if (n != null) return n;\n                if (in == null || in.lumensMode == null) return SILENT;"),
    ("motion compensation free", R, "                if (in == null || in.memc == null) return SILENT;\n                Note n = pictureSettings(f, in);",
     "                if (in == null || in.memc == null) return SILENT;\n                Note n = null;"),
    ("AI contrast, super resolution, the professional page and reset free", R,
     "            case PICTURE_RESET:                               // QPC:1079 / 1084 / 1101 / 1105\n                return in == null ? null : pictureSettings(f, in);",
     "            case PICTURE_RESET:                               // QPC:1079 / 1084 / 1101 / 1105\n                return null;"),
    ("the rows drawn open in AI", R, "    static Note locked(FeatureId f, In in) {\n        return in == null ? null : pictureSettings(f, in);",
     "    static Note locked(FeatureId f, In in) {\n        return null;"),
    ("the closed look of the professional page only", R, "    static Note locked(FeatureId f, In in) {\n        return in == null ? null : pictureSettings(f, in);",
     "    static Note locked(FeatureId f, In in) {\n        return in == null ? null : pictureSettings(FeatureId.PICTURE_PRO, in);"),
    ("the adapter draws nothing closed", "apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xFeatures.java",
     "{ return Z9xMenuRules.locked(f, last); }", "{ return null; }"),
    ("Ultra 120 free", R, "                if (in == null) return null;\n                Note n = pictureSettings(f, in);\n                if (n != null || !\"on\".equals(value)) return n;",
     "                if (in == null) return null;\n                Note n = null;\n                if (n != null || !\"on\".equals(value)) return n;"),
]


def main():
    only = os.environ.get("MUT_ONLY", "")
    tmp = tempfile.mkdtemp(prefix="z9x-picture-mut-")
    killed = survived = 0
    try:
        def copy():
            w = os.path.join(tmp, "g")
            shutil.rmtree(w, ignore_errors=True)
            for d in ("apps/LumenProjector/core/src", "apps/LumenProjector/core/test/rules", "apps/core/lumen-menu/src"):  # what run.sh reads
                shutil.copytree(os.path.join(G, d), os.path.join(w, d))
            return w

        def run(w):
            return subprocess.run(["sh", os.path.join(w, "apps/LumenProjector/core/test/rules/run.sh")],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT).returncode

        if run(copy()) != 0:
            print("FAIL unmutated")
            return 1
        print("ok       unmutated")
        for name, path, old, new in MUTANTS:
            if only and only not in name:
                continue
            w = copy()
            p = os.path.join(w, path)
            s = open(p, encoding="utf-8").read()
            if old not in s:
                print("BROKEN   " + name)
                survived += 1
                continue
            open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
            if run(w) == 0:
                print("SURVIVED " + name)
                survived += 1
            else:
                print("killed   " + name)
                killed += 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print("z9x picture mutants: %d killed, %d survived" % (killed, survived))
    return 1 if survived else 0


if __name__ == "__main__":
    sys.exit(main())
