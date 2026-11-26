#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""text_style_test.py: text_style.py passes on the tree and fails on each kind of mixed text (its mutants).

    python3 tools/i18n/test/text_style_test.py [GSI_DIR]
"""
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import text_style  # noqa: E402

GOOD = {
    "a": "Color vision", "b": "Projection brightness", "c": "Dolby Vision Bright", "d": "HDMI-CEC control",
    "e": "Not available in IMAX Enhanced", "f": "Hold OK: reset · Back: done", "g": "3D to 2D (side by side)",
    "h": "Turn on “Dynamic black level enhancement” first", "i": "Iris",
}
BAD = [
    ("British spelling", "Wall colour adaptation"), ("British spelling", "Licences"), ("British spelling", "Centre"),
    ("Title Case label", "Projection Brightness"), ("Title Case label", "Power On Auto Boot"),
    ("Title Case label", "Side by Side"), ("capitals", "IRIS"), ("Title Case label", "Professional Settings"),
]
fails = 0


def check(name, ok):
    global fails
    print(("ok   " if ok else "FAIL ") + name)
    fails += 0 if ok else 1


def tree(strings, other=None, labels=None):
    d = tempfile.mkdtemp(prefix="text_style.")
    v = os.path.join(d, "apps", "X", "res", "values")
    os.makedirs(v)
    with open(os.path.join(v, "s.xml"), "w", encoding="utf-8") as f:
        f.write("<resources>\n" + "".join('<string name="%s">%s</string>\n' % kv for kv in strings.items())
                + '<string name="code" translatable="false">Lumen Colour Code</string>\n</resources>\n')
    if other:
        o = os.path.join(d, "apps", "X", "res", "values-de")
        os.makedirs(o)
        with open(os.path.join(o, "s.xml"), "w", encoding="utf-8") as f:
            f.write('<resources><string name="a">%s</string></resources>' % other)
    if labels:
        lp = os.path.join(d, text_style.LABELS)
        os.makedirs(os.path.dirname(lp))
        with open(lp, "w", encoding="utf-8") as f:
            f.write(labels)
    return d


def main(argv):
    root = argv[1] if len(argv) > 1 else os.path.join(HERE, "..", "..", "..")
    check("the tree follows the rules", not text_style.check(root))
    d = tree(GOOD, other="Projektionshelligkeit Farbe Colour")
    check("sentence case, US spelling, names, abbreviations, quotes, other languages, untranslatable names pass",
          not text_style.check(d))
    shutil.rmtree(d)
    for kind, text in BAD:
        d = tree(dict(GOOD, z=text))
        check("%s fails: %r" % (kind, text), bool(text_style.check(d)))
        shutil.rmtree(d)
    sub = ('static int summary(String id) {\n switch (id) {\n case "lamp": return 1;\n case "%s": return 2;\n'
           ' default: return 0;\n }\n}\nstatic int tipText(String hint) {\n switch (hint) {\n'
           ' case "focus_guide_side": return 3;\n default: return 0;\n }\n}\n')
    d = tree(GOOD, labels=sub % "boost")
    check("the allowed lines under rows pass", not text_style.check(d))
    shutil.rmtree(d)
    d = tree(GOOD, labels=sub % "eye")
    check("an explanation line under a row fails (the eye switch)", bool(text_style.check(d)))
    shutil.rmtree(d)
    d = tree(GOOD, labels=sub.replace("focus_guide_side", "picture_mode") % "boost")
    check("a text under a hint picture that explains the row fails", bool(text_style.check(d)))
    shutil.rmtree(d)
    print("text_style_test: %d failed" % fails)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
