#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""share_test.py: the guard and the fold of tools/i18n/share.py on a small tree (one text in several apps).

Each case is a rule of share.py: a text defined twice turns check red; apply folds only the places with the same
translation in all 17 locales built through a recipe; a different translation, an app with English and Russian only
and an app without a recipe stay copies, listed in known_dups.tsv; a stale or widened row is red. Exit 0 green.
"""
import os
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
GSI = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SHARE = os.path.join(GSI, "tools/i18n/share.py")
LOCS = ["ar", "be", "de", "es", "fr", "it", "ja", "kk", "ko", "pl", "pt", "pt-rBR", "ru", "tr", "uk", "zh-rCN", "zh-rTW"]
fails = []


def xml(rows):
    return "<resources>\n" + "".join('    <string name="%s">%s</string>\n' % r for r in rows) + "</resources>\n"


def app(root, name, key, en, tr, recipe=True, locs=LOCS):
    res = os.path.join(root, "apps", name, "res")
    os.makedirs(os.path.join(res, "values"))
    open(os.path.join(res, "values", "strings.xml"), "w").write(xml([(key, en)]))
    for loc in locs:
        os.makedirs(os.path.join(res, "values-" + loc))
        open(os.path.join(res, "values-" + loc, "strings.xml"), "w").write(xml([(key, tr(loc))]))
    if recipe:
        open(os.path.join(root, "apps", name, "app.layers"), "w").write("app %s\nmodule lumen-ui\n" % name)


def tree():
    root = tempfile.mkdtemp(prefix="share_test.")
    os.makedirs(os.path.join(root, "apps/i18n/langs"))
    shutil.copy(os.path.join(GSI, "apps/i18n/gen.py"), os.path.join(root, "apps/i18n/gen.py"))
    for loc in ["ar", "be", "de", "es", "fr", "it", "ja", "kk", "ko", "pl", "pt", "ru", "tr", "uk", "zh-rCN", "zh-rTW"]:
        open(os.path.join(root, "apps/i18n/langs", loc + ".txt"), "w").write("@strings/lumen_strings.xml\n")
    open(os.path.join(root, "apps/app_dirs.tsv"), "w").write("")
    m = os.path.join(root, "apps/core/lumen-common-strings")
    for d in ["values"] + ["values-" + l for l in LOCS]:
        os.makedirs(os.path.join(m, "res", d))
        open(os.path.join(m, "res", d, "lumen_strings.xml"), "w").write("<resources>\n</resources>\n")
    os.makedirs(os.path.join(root, "apps/core/lumen-ui"))
    open(os.path.join(root, "apps/core/lumen-ui/module.conf"), "w").write("name=lumen-ui\npackage=org.lumen.ui\n")
    same = lambda loc: "Off-" + loc.replace("-rBR", "")
    app(root, "A", "a_off", "Off", same)
    app(root, "B", "b_off", "Off", same)
    app(root, "C", "c_off", "Off", lambda loc: ("Other-" if loc == "ru" else "Off-") + loc.replace("-rBR", ""))
    app(root, "D", "d_off", "Off", same, locs=["ru"])
    app(root, "E", "e_off", "Off", same, recipe=False)
    return root


def run(root, *args):
    subprocess.run(["git", "add", "-A"], cwd=root, check=True, capture_output=True)
    p = subprocess.run([sys.executable, "-B", SHARE] + list(args), cwd=root, capture_output=True, text=True,
                       env=dict(os.environ, SHARE_ROOT=root))
    return p.returncode, p.stdout + p.stderr


def ok(name, cond, out=""):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)
        print(out[-600:])


root = tree()
subprocess.run(["git", "init", "-q"], cwd=root, check=True)
rc, out = run(root, "check")
ok("a text defined in five apps is red", rc == 1 and "'Off' is defined 5 times" in out, out)
rc, out = run(root, "apply")
ok("apply folds the two apps with the same translations", rc == 0 and "1 new module texts, 2 places" in out, out)
a = open(os.path.join(root, "apps/A/res/values/strings.xml")).read()
b = open(os.path.join(root, "apps/B/res/values/strings.xml")).read()
ok("both places are aliases of the module text", "@string/lumen_off" in a and "@string/lumen_off" in b, a + b)
mod = open(os.path.join(root, "apps/core/lumen-common-strings/res/values-ru/lumen_strings.xml")).read()
ok("the module has the text in Russian", '<string name="lumen_off">Off-ru</string>' in mod, mod)
ok("the alias keeps no translation", not os.path.exists(os.path.join(root, "apps/B/res/values-ru/strings.xml")))
ok("the recipe names the module", "lumen-common-strings" in open(os.path.join(root, "apps/B/app.layers")).read())
ok("the langs source has the text", "lumen_off=Off-ru" in open(os.path.join(root, "apps/i18n/langs/ru.txt")).read())
for name, word in (("another translation", "C"), ("English and Russian only", "D"), ("no recipe", "E")):
    t = open(os.path.join(root, "apps/%s/res/values/strings.xml" % word)).read()
    ok("%s stays a copy" % name, "@string/" not in t, t)
rc, out = run(root, "check")
ok("the copies left are red until listed", rc == 1, out)
rc, out = run(root, "known")
known = open(os.path.join(root, "apps/core/lumen-common-strings/known_dups.tsv")).read()
ok("known lists the reasons", "translations differ: ru" in known and "built without a recipe: apps/E" in known, known)
rc, out = run(root, "check")
ok("listed copies are green", rc == 0, out)
app(root, "F", "f_off", "Off", lambda loc: "Off-" + loc.replace("-rBR", ""))
rc, out = run(root, "check")
ok("one more place of a listed text is red", rc == 1 and "the row says" in out, out)
shutil.rmtree(os.path.join(root, "apps/F"))
for w in "CDE":
    shutil.rmtree(os.path.join(root, "apps", w))
rc, out = run(root, "check")
ok("a row that is no copy any more is red", rc == 1 and "drop the row" in out, out)
shutil.rmtree(root)
print("share_test: %d failed" % len(fails) if fails else "share_test: GREEN")
sys.exit(1 if fails else 0)
