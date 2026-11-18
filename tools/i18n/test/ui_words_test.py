#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""ui_words_test.py: tools/i18n/ui_words.py finds every forbidden word in every place a text can sit (string, plural
item, array item, any res/values* folder of apps/, overlay/ or devices/), lets plain texts, references and build output
pass, and the real tree is clean.

    python3 tools/i18n/test/ui_words_test.py [GSI_DIR]      exit 0 green, 1 red
"""
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import ui_words  # noqa: E402

FAILS = []


def ok(name, cond):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        FAILS.append(name)


def tree(files):
    d = tempfile.mkdtemp(prefix="ui_words_")
    for rel, body in files.items():
        p = os.path.join(d, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + body + "\n</resources>\n")
    return d


def hits(files):
    d = tree(files)
    try:
        return ui_words.check(d)
    finally:
        shutil.rmtree(d)


def one(text, folder="values"):
    return hits({"apps/A/res/%s/strings.xml" % folder: '<string name="t">%s</string>' % text})


def main(argv):
    gsi = argv[1] if len(argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
    # every forbidden word, in the language that uses it
    bad = [
        ("DSU", "Start the DSU trial", "values"),
        ("dsu lower case", "dsu image", "values"),
        ("vbmeta", "vbmeta is never written", "values"),
        ("vbmeta in a step name", "(step vbmeta)", "values-ru"),
        ("slot", "the other system slot (A)", "values"),
        ("slots", "both slots", "values"),
        ("Slot capital", "Slot B", "values-it"),
        ("German compound", "des anderen Systemslots", "values-de"),
        ("Spanish", "la otra ranura del sistema", "values-es"),
        ("French", "l’autre emplacement système", "values-fr"),
        ("Turkish", "Diğer sistem yuvasının durumu", "values-tr"),
        ("Arabic updater", "خانة النظام الأخرى", "values-ar"),
        ("Arabic report", "وفتحة النظام", "values-ar"),
        ("Russian", "второй системный слот", "values-ru"),
        ("Ukrainian", "другий системний слот", "values-uk"),
        ("Kazakh", "екінші жүйелік слоттың", "values-kk"),
        ("Japanese", "もう一方のシステムスロット", "values-ja"),
        ("Korean", "다른 시스템 슬롯", "values-ko"),
        ("Chinese simplified", "另一个系统槽位", "values-zh-rCN"),
        ("Chinese traditional", "系統槽位", "values-zh-rTW"),
    ]
    for name, text, folder in bad:
        ok("caught: " + name, len(one(text, folder)) == 1)
    # every place a text can sit
    ok("caught: plural item", len(hits({"apps/A/res/values/strings.xml":
            '<plurals name="p"><item quantity="one">one slot</item><item quantity="other">%d slots</item></plurals>'})) == 2)
    ok("caught: array item", len(hits({"apps/A/res/values/arrays.xml":
            '<string-array name="a"><item>Fine</item><item>Use DSU</item></string-array>'})) == 1)
    ok("caught: translatable=false too", len(hits({"apps/A/res/values/strings.xml":
            '<string name="t" translatable="false">vbmeta</string>'})) == 1)
    ok("caught: an overlay", len(hits({"overlay/v1/X/res/values/strings.xml": '<string name="t">slot A</string>'})) == 1)
    ok("caught: a device recipe folder", len(hits({"devices/g0111/app/X/res/values-ru/s.xml":
            '<string name="t">слот</string>'})) == 1)
    ok("caught: a shared module", len(hits({"apps/core/lumen-x/res/values-ja/s.xml":
            '<string name="t">スロット</string>'})) == 1)
    ok("hit names file, key and word", one("Slot B")[0].startswith("apps/A/res/values/strings.xml: t: slot: "))
    # what must pass
    good = [
        ("plain update text", "Lumen OS will copy the projector’s own XGIMI system files to the update."),
        ("Afrikaans caps lock (slot = lock)", "Hoofletterslot"),
        ("words that only contain the letters", "slothful dsux"),
        ("Turkish round", "Ortadaki daire yuvarlak olana kadar ayarlayın"),
        ("trial run", "Try Lumen OS once, the current system stays as it is"),
    ]
    for name, text in good:
        ok("passes: " + name, one(text) == [])
    ok("passes: a reference", one("@string/lumen_off") == [] and one("@android:string/ok") == [])
    ok("caught: a text that only starts like a reference", len(one("@string/x and the other slot")) == 1)
    ok("passes: build output", hits({"apps/A/build/res/values/strings.xml": '<string name="t">slot</string>'}) == [])
    ok("passes: a non-values folder", hits({"apps/A/res/xml/prefs.xml": '<string name="t">slot</string>'}) == [])
    ok("passes: a values folder outside res", hits({"apps/A/test/values/x.xml": '<string name="t">slot</string>'}) == [])
    ok("unparsable file is a hit", hits({"apps/A/res/values/bad.xml": "<string name='x'>"}) != [])
    # the real tree
    real = ui_words.check(gsi)
    for h in real:
        print("     " + h)
    n = sum(1 for _ in ui_words.files(gsi))
    ok("the tree has values files (%d)" % n, n > 100)
    ok("the tree: no forbidden word in any user-visible string (%d found)" % len(real), real == [])
    print("ui_words_test: %d failed" % len(FAILS))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
