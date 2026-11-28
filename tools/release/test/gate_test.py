#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""gate_test.py: the one gate (tools/release/gate.py) names every change in every model it reaches, the same way for
each model. A scratch tree of hard links (a mutated file is replaced, never written through the link) gets one change
per mutant; gate.py check must name exactly the expected models:

  a core file of the one projector app   -> the models whose projector app takes it (Z9X, HB), no X50 model
  an X50 projector file                  -> the MT9681 models (X50, TITAN, RS30), no core-based model
  one model's menu layer                 -> that model only
  a shared build tool                    -> every model
  a released pin's PINS.sha256 row       -> the Z9X only
  a base APK fix of tools/base_patch     -> every model, named as that fix (its source, then its result pin)
  the services.jar fix of one base       -> the models of that base (build.conf framework=), named per base
and a recorded, unchanged tree is green for every model (records are made in the scratch tree: ignored inputs
differ between checkouts).
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
bad = []


def gate(root, cmd):
    return subprocess.run([sys.executable, "-B", os.path.join(root, "tools/release/gate.py"), "--root", root, cmd],
                          cwd=root, capture_output=True, text=True)


def check(root):
    r = gate(root, "check")
    changed = set(re.findall(r"^gate: (\S+) changed", r.stdout, re.M))
    return r.returncode, changed, r.stdout


def ok(cond, what, out=""):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        bad.append(what)
        print(out[-2500:])


def tree(dst):
    def link(src, d):
        try:
            os.link(src, d)
        except OSError:
            shutil.copy2(src, d)
    for e in ("apps", "devices", "tools", "overlay"):
        shutil.copytree(os.path.join(G, e), os.path.join(dst, e), symlinks=True, copy_function=link,
                        ignore=lambda d, names: [n for n in names if n == "__pycache__" or
                                                 (n == "build" and os.path.relpath(d, G).startswith("apps"))])
    for e in os.listdir(G):
        if not os.path.exists(os.path.join(dst, e)) and e not in (".git",):
            os.symlink(os.path.join(G, e), os.path.join(dst, e))
    rec = os.path.join(dst, "devices/_baseline/gate")   # gate.py record writes here: own files, never links
    for f in os.listdir(rec):
        p = os.path.join(rec, f)
        data = open(p, "rb").read()
        os.remove(p)
        open(p, "wb").write(data)
    subprocess.run(["git", "init", "-q", dst])


def replace(root, rel, fn):
    p = os.path.join(root, rel)
    s = open(p, encoding="utf-8").read()
    os.remove(p)                      # a new file: the hard link to the real tree is never written
    with open(p, "w", encoding="utf-8") as f:
        f.write(fn(s))


def members(*fams):
    out = set()
    for f in fams:
        r = subprocess.run([sys.executable, "-B", os.path.join(G, "tools/devices/layered.py"), "--members", G, f],
                           capture_output=True, text=True)
        out |= set(r.stdout.split())
    return out


def framework_of(root, models):
    """{model: the base of its services.jar fix (build.conf framework=)}"""
    sys.path.insert(0, os.path.join(G, "tools", "devices"))
    import layered  # noqa: E402
    return {c: layered.effective(os.path.join(root, "devices", c, "build.conf")).get("framework") for c in models}


def main():
    # the records of this host (ignored inputs such as pins and the boot animation differ between checkouts): every
    # case records its scratch tree first, then changes one file
    with tempfile.TemporaryDirectory(prefix="gate_test_") as t:
        tree(t)
        gate(t, "record")
        rc, ch, out = check(t)
        ok(rc == 0 and not ch, "a recorded tree is green for every model", out)
    all_models = set(re.findall(r"^gate: (\S+) GREEN", out, re.M))
    x50 = members("mt9681") & all_models
    rs30 = {c for c, b in framework_of(G, all_models).items() if b == "rs30"}
    core_models = {"horse_popular", "goat_elegant"}   # Play 6 takes only listed core files (not AfAfter) yet
    cases = [
        ("a core file", "apps/LumenProjector/core/src/org/lumen/projector/AfAfter.java", lambda s: s + "\n",
         lambda c: core_models <= c and not (c & x50)),
        ("an X50 projector file", "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/X50Features.java", lambda s: s + "\n",
         lambda c: c == x50),
        ("one model's menu layer", "devices/h0062/menu.json", lambda s: s.replace('"note": "', '"note": "x', 1),
         lambda c: c == {"h0062"}),
        ("a shared build tool", "apps/build_apk.sh", lambda s: s + "\n", lambda c: c == all_models),
        ("a pin row", "overlay/apps_v1/PINS.sha256",
         lambda s: re.sub(r"^[0-9a-f]{64}(  Z9xHome\.apk)$", "0" * 64 + r"\1", s, count=1, flags=re.M),
         lambda c: c == {"horse_popular"}),
        ("a base APK fix", "tools/base_patch/tvsettings_focus/SettingsPreferenceFragment.java", lambda s: s + "\n",
         lambda c: c == all_models, r"base_patch tvsettings_focus: rule \w+ -> \w+, same result pin"),
        ("a base APK fix's result pin", "tools/base_patch/patches.tsv",
         lambda s: s.replace("\t23cea268", "\t03cea268"), lambda c: c == all_models,
         r"base_patch tvsettings_focus: result 23cea268eea6 -> 03cea268eea6"),
        ("the services.jar fix of the rs30 base", "tools/base_patch/framework.tsv",
         lambda s: s.replace("\tcd3df384", "\t0d3df384"), lambda c: c == rs30,
         r"base_patch services_hdmi:rs30: result \w+ -> \w+"),
    ]
    for name, rel, fn, want, *msg in cases:
        with tempfile.TemporaryDirectory(prefix="gate_test_") as t:
            tree(t)
            gate(t, "record")
            replace(t, rel, fn)
            rc, ch, out = check(t)
            ok(rc == 1 and want(ch) and all(re.search(m, out) for m in msg),
               "%s names %s%s" % (name, " ".join(sorted(ch)) or "nothing", "".join(" (%s)" % m for m in msg)), out)
    print("gate_test: %s" % ("RED (%d)" % len(bad) if bad else "GREEN"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
