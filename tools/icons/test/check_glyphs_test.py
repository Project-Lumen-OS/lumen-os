#!/usr/bin/env python3
"""check_glyphs_test.py: tools/icons/check_glyphs.py on small fixture trees, each rule both ways, then the real tree
green, the real tree of the commit before the icon set red, and gen_icons.py check --offline.

  python3 tools/icons/test/check_glyphs_test.py [--base REF]     REF: a commit with glyph icons (default 8c7db995)
"""
import os
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
GSI = os.path.dirname(os.path.dirname(TOOLS))
sys.path.insert(0, TOOLS)
import check_glyphs  # noqa: E402

FAILS = []


def ok(cond, what):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        FAILS.append(what)


def tree(files):
    root = tempfile.mkdtemp(prefix="glyphs.")
    for rel, text in files.items():
        p = os.path.join(root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(text)
    return root


def rules(files):
    root = tree(files)
    try:
        return sorted((w.split("  ")[0], ch, r) for w, ch, r in check_glyphs.scan(root))
    finally:
        shutil.rmtree(root)


def strings(body):
    return {"apps/A/res/values/strings.xml": "<resources>\n%s\n</resources>\n" % body}


def java(line):
    return {"apps/A/src/a/A.java": "package a;\nclass A {\n    void f() {\n        %s\n    }\n}\n" % line}


R = {"icon", "key", "decor"}

# strings
ok(rules(strings('<string name="done">\u2713 Done</string>'))[0][1:] == ("\u2713", "icon"), "a tick in a string is red")
ok(rules(strings('<string name="lang">Russian \u2713</string>'))[0][2] == "icon", "a tick after a word is red")
ok(rules(strings('<string name="hint">Press \u2192 again</string>'))[0][1:] == ("\u2192", "key"),
   "a key arrow in a string that key_hints.tsv does not name is red")
ok(rules(strings('<string name="more">Licences \u203a</string>'))[0][1:] == ("\u203a", "decor"), "a chevron at the end is red")
ok(rules(strings('<string name="x">\u00d7</string>'))[0][1:] == ("\u00d7", "decor"), "a lone x sign (close) is red")
ok(rules(strings('<string name="b">\u2022 one</string>'))[0][1:] == ("\u2022", "decor"), "a leading bullet is red")
ok(rules(strings('<string name="e">\U0001F50A Sound</string>'))[0][2] == "icon", "an emoji is red")
ok(rules(strings('<string name="left">"\u2190"</string> <!-- lumen-icons: not an icon (spoken key name) -->')) == [],
   "a marked string line is skipped")
ok(rules(strings('<string name="keyboardview_keycode_left">"\u2190"</string>')) == [],
   "a spoken key name of the keyboard (screen reader text) may hold its key arrow")
ok(rules(strings('<string name="keyboardview_keycode_ok">\u2713</string>'))[0][2] == "icon",
   "a spoken key name with an icon glyph is still red")
ok(rules(strings('<string name="dim">Screen: %1$s \u00d7 %2$s cm</string>')) == [], "a dimension x sign is typography")
ok(rules(strings('<string name="q">\u00abFit to screen\u00bb now</string>')) == [], "quotes are typography")
ok(rules(strings('<string name="p">Settings \u203a Home screen</string>')) == [], "a path chevron inside a sentence is typography")
ok(rules(strings('<string name="s">HDMI 1 \u00b7 HDR10 \u2026</string>')) == [], "a middle dot and an ellipsis in a sentence are typography")
ok(rules(strings('<string name="m">\u22125 dB</string>')) == [], "a minus sign is typography")
# key hints
hint = dict(strings('<string name="eq">\u25c0 \u25b6 band</string>'))
hint["tools/icons/key_hints.tsv"] = "# name\tfile\tanchor\neq\tapps/A/src/a/Row.java\tIconText.keys(c, hint)\n"
hint["apps/A/src/a/Row.java"] = "class Row { void f() { t = IconText.keys(c, hint); } }\n"
ok(rules(hint) == [], "a listed key hint drawn through IconText is green")
gone = dict(hint)
gone["apps/A/src/a/Row.java"] = "class Row { void f() { t = hint; } }\n"
ok([r[2] for r in rules(gone)] == ["key hint not drawn: apps/A/src/a/Row.java has no 'IconText.keys(c, hint)'"],
   "a listed key hint whose file lost the IconText call is red")
stale = dict(hint)
stale["apps/A/res/values/strings.xml"] = '<resources>\n<string name="eq">band</string>\n</resources>\n'
ok([r[2] for r in rules(stale)] == ["key hint names no string with a key arrow"], "a stale key hint row is red")
ok(rules(dict(hint, **{"apps/A/res/values/strings.xml": '<resources>\n<string name="eq">\u2713 band</string>\n'
                                                          '</resources>\n'}))[0][2] == "icon",
   "a listed key hint still may not carry a tick")
# translation sources and layouts
ok(rules({"apps/i18n/langs/ru.txt": "@updater/strings.xml\nok=\u2713 \u0413\u043e\u0442\u043e\u0432\u043e\n"})[0][2] == "icon",
   "a tick in a translation source is red")
ok(rules({"apps/A/res/layout/a.xml": '<TextView android:text="\u25b6" />'})[0][1:] == ("\u25b6", "key"),
   "a key arrow written into a layout is red")
ok(rules({"apps/A/res/layout/a.xml": '<TextView android:text="@string/x" android:contentDescription="Play" />'}) == [],
   "a layout that names its strings is green")
# code
ok(rules(java('mark.setText("\\u2713");'))[0][1:] == ("\u2713", "icon"), "an escaped tick literal in code is red")
ok(rules(java('mark.setText(on ? "\u2713" : "");'))[0][2] == "icon", "a tick literal in code is red")
ok(rules(java('value.setText("\u203a");'))[0][1:] == ("\u203a", "decor"), "a chevron literal in code is red")
ok(rules(java('t.setText("\\u2022  " + s);'))[0][1:] == ("\u2022", "decor"), "a bullet literal in code is red")
ok(rules(java("a.append('\u25b2');"))[0][1:] == ("\u25b2", "key"), "a key arrow char literal in code is red")
ok(rules(java('// \u25c0 \u25b6 band, OK adjust')) == [], "a comment is not read")
ok(rules(java('/* "Custom  \u2039 45 min \u203a" */')) == [], "a block comment is not read")
ok(rules(java('Log.i(TAG, "a \u2192 b");')) == [], "a log line is not UI")
ok(rules(java('m = "\u2022\u2022\u2022\u2022";  // lumen-icons: not an icon (a password mask)')) == [],
   "a marked line is skipped")
ok(rules(java('return w + " \u00d7 " + h;')) == [], "a dimension x sign in code is typography")
ok(rules(java('s = "a \\" \u2713";'))[0][2] == "icon", "a literal with an escaped quote is read to its end")
# folders that are not read
ok(rules({"apps/A/test/T.java": 'class T { String s = "\u2713"; }\n',
          "apps/core/lumen-icons/src/I.java": 'class I { char L = \'\u25c0\'; }\n',
          "apps/third_party/x/X.java": 'class X { String s = "\u2713"; }\n'}) == [],
   "tests, the icon module itself and third party code are not read")


def git(*a):
    return subprocess.run(["git", "-C", GSI] + list(a), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)


real = check_glyphs.scan(GSI)
ok(real == [], "the real tree has no glyph icon (%d finding(s))" % len(real))
base = sys.argv[sys.argv.index("--base") + 1] if "--base" in sys.argv else "8c7db995"
if git("cat-file", "-e", base + "^{commit}").returncode == 0:
    w = tempfile.mkdtemp(prefix="glyphs_base.")
    try:
        arc = subprocess.run("git -C '%s' archive %s apps | tar -x -C '%s'" % (GSI, base, w),
                             shell=True)
        old = check_glyphs.scan(w)
        kinds = {r for _, _, r in old}
        ok(arc.returncode == 0 and len(old) >= 20 and {"icon", "key", "decor"} <= kinds,
           "the tree before the icon set (%s) is red: %d finding(s), rules %s" % (base, len(old), sorted(kinds)))
    finally:
        shutil.rmtree(w)
else:
    print("skip the tree before the icon set: no commit %s here" % base)
r = subprocess.run([sys.executable, "-B", os.path.join(TOOLS, "gen_icons.py"), "check", "--offline"],
                   stdout=subprocess.PIPE, text=True)
ok(r.returncode == 0, "gen_icons.py check --offline: " + r.stdout.strip().splitlines()[-1])
print("check_glyphs_test: %s (%d failed)" % ("RED" if FAILS else "OK", len(FAILS)))
sys.exit(1 if FAILS else 0)
