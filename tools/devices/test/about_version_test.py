#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""about_version_test.py: Settings > About shows each image's own release name (devices/_image/checks/about_version.py).

Every prop file a model descriptor names (var.PROPF, var.PROPF.pub of devices/*/build.conf and the family confs) goes
through x50_props.py apply onto a base that carries the Z9X 1.0.9 base's row (\"1.0.9 (kmuradoff)\", what X50 beta13
showed), then about_version.py check and x50_props.py check read the result. The Z9X and HB recipes take the row's text
from the same module. Mutants: a prop file that writes the row itself, a file without ro.lumen.version, a stale row.

  python3 tools/devices/test/about_version_test.py
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CHECKS = os.path.join(G, "devices", "_image", "checks")
XP = os.path.join(G, "devices", "g0111", "image", "x50_props.py")
AV = os.path.join(CHECKS, "about_version.py")
sys.path.insert(0, CHECKS)
import about_version  # noqa: E402

bad = []


def ok(name, cond, info=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  | " + str(info)[-600:]))
    if not cond:
        bad.append(name)


def run(*a):
    p = subprocess.run([sys.executable, "-I"] + list(a), capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def prop_files():
    """(descriptor, prop file) of every X50-family descriptor: the PROPF values of its build.conf and includes"""
    out = set()
    for conf in sorted(glob.glob(os.path.join(G, "devices", "*", "build.conf"))):
        dev = os.path.basename(os.path.dirname(conf))
        text, todo = "", [conf]
        while todo:
            t = open(todo.pop()).read()
            text += t
            todo += [os.path.join(G, m) for m in re.findall(r"^include=(\S+)$", t, re.M)]
        for name in re.findall(r"^var\.PROPF(?:\.\w+)?=(\S+)$", text, re.M):
            name = name.replace("{codename}", dev)
            hits = glob.glob(os.path.join(G, "devices", "*", "image", name)) + glob.glob(os.path.join(G, "devices", "_family", "*", "image", name))
            ok("prop_file_found_%s_%s" % (dev, name), len(hits) == 1, hits)
            out.update((dev, h) for h in hits)
    return sorted(out)


BASE_SYS = ("ro.product.system.model=Z9X\nro.build.id=AP2A.240905.003\nro.build.display.id=Lumen OS 1.0.9 (x)\n"
            "ro.build.version.incremental=eng.root.20261003.104216\nro.build.date=x\nro.build.date.utc=1\n"
            "ro.system.build.version.incremental=x\nro.system.build.fingerprint=x\nro.system.build.date=x\n"
            "ro.system.build.date.utc=1\nro.lineage.display.version=1.0.9 (kmuradoff)\nro.z9x.version=1.0.9\n")


def base_of(edits, sec, text=""):
    """a base file with one line of every key the edits of sec set or delete (the base has each exactly once)"""
    keys = [k for op, k, _ in edits[sec] if op in ("set", "del") and not re.search("^%s=" % re.escape(k), text, re.M)]
    return text + "".join("%s=base\n" % k for k in keys) + ("# end of file\n" if sec == "product" else "")


W = tempfile.mkdtemp(prefix="about_version_test.")
try:
    sys.path.insert(0, os.path.dirname(XP))
    import x50_props
    files = prop_files()
    ok("x50_family_prop_files", len(files) >= 8, files)
    sys.path.insert(0, os.path.join(G, "tools", "devices"))
    import layered

    def subs_of(dev):
        """the KEY=VALUE words the recipe gives x50_props.py for this model (a family prop file names the model so)"""
        b = layered.effective(os.path.join(G, "devices", dev, "build.conf"))
        return {"NAME": b.get("var.NAME", ""), "TAGP": b.get("var.TAGP", ""), "CODENAME": dev, "MODEL": b.get("var.MODEL", "")}

    for dev, pf in files:
        name = "%s_%s" % (dev, os.path.basename(pf))
        sw = ["%s=%s" % kv for kv in sorted(subs_of(dev).items())] if "@TAGP@" in open(pf).read() else []
        e = x50_props.read_edits(pf, "1.0.10-beta13", "202611230051", dict(w.split("=", 1) for w in sw))
        d = os.path.join(W, name)
        for sub in ("base", "new"):
            os.makedirs(os.path.join(d, sub, "product", "etc"))
            open(os.path.join(d, sub, "build.prop"), "w").write(base_of(e, "system", BASE_SYS))
            open(os.path.join(d, sub, "product", "etc", "build.prop"), "w").write(base_of(e, "product"))
        model = os.path.join(d, "model.txt")
        open(model, "w").write("ro.xgimi.product.name=x\n")
        ns, np_ = os.path.join(d, "new", "build.prop"), os.path.join(d, "new", "product", "etc", "build.prop")
        rc, out = run(XP, "apply", pf, model, ns, np_, "1.0.10-beta13", "202611230051", *sw)
        ok("apply_" + name, rc == 0, out)
        rc, out = run(AV, "check", ns, np_)
        props = about_version.props(open(ns).read(), open(np_).read())
        ok("about_is_release_" + name, rc == 0 and props.get(about_version.KEY) == props.get("ro.lumen.version") + " (kmuradoff)"
           and "1.0.10-beta13" in props.get(about_version.KEY, ""), (out, props.get(about_version.KEY)))
        rc, out = run(XP, "check", pf, model, os.path.join(d, "base", "build.prop"),
                      os.path.join(d, "base", "product", "etc", "build.prop"), ns, np_, "1.0.10-beta13", "202611230051", *sw)
        ok("x50_check_" + name, rc == 0, out)
        # mutant: the row left as the base has it (beta13): the image check refuses
        open(ns, "w").write(base_of(e, "system", BASE_SYS))
        rc, out = run(AV, "check", ns, np_)
        ok("stale_row_refused_" + name, rc == 1 and "1.0.9 (kmuradoff)" in out, out)

    # a prop file may not write the row, and needs exactly one release name
    pf = os.path.join(W, "own_row.txt")
    open(pf, "w").write("[system]\nro.lineage.display.version=x (kmuradoff)\n[product]\nro.lumen.version=@V@\n")
    rc, out = run(XP, "check", pf, pf, pf, pf, pf, pf, "t1", "202611230051")
    ok("prop_file_own_row_refused", rc != 0 and "about_version.py" in out, out)
    open(pf, "w").write("[system]\nro.product.system.model=X\n[product]\n+ro.lumen.device=x\n")
    rc, out = run(XP, "check", pf, pf, pf, pf, pf, pf, "t1", "202611230051")
    ok("prop_file_without_version_refused", rc != 0 and "About row" in out, out)
    try:
        about_version.value("")
        ok("value_rejects_empty", False)
    except ValueError:
        ok("value_rejects_empty_name", True)

    # the other recipes take the row's text from the same module, none writes its own
    for rel in ("devices/horse_popular/image/z9x_props.py", "devices/goat_elegant/image/hb_props.py"):
        s = open(os.path.join(G, rel)).read()
        ok("uses_about_version_" + os.path.basename(rel), "about_version.line(" in s and "(kmuradoff)" not in s, rel)
    for rel in ("devices/_family/x50/x50.build.conf", "devices/_family/rs30/rs30.build.conf", "devices/goat_elegant/build.conf"):
        ok("stages_about_version_" + rel.split("/")[-1], "devices/_image/checks/about_version.py" in open(os.path.join(G, rel)).read())
    for rel in ("devices/g0111/image/steps.tsv", "devices/g0155/image/steps.tsv"):
        ok("image_check_step_" + rel.split("/")[1], re.search(r"^\*\trun_root\tabout_version\.py\tcheck\t\{mn\}/system/build\.prop\t", open(os.path.join(G, rel)).read(), re.M) is not None)
finally:
    shutil.rmtree(W, ignore_errors=True)

print("about_version_test: %d failed%s" % (len(bad), (": " + " ".join(bad)) if bad else ""))
print("about_version_test: " + ("PASS" if not bad else "FAIL"))
sys.exit(1 if bad else 0)
