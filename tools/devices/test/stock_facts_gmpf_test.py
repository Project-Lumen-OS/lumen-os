#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""stock_facts_gmpf_test.py: the mutants of tools/devices/test/stock_facts_gmpf.py. For every model the rule checks it makes the
same kinds of change in a scratch copy of the files the rule reads (a firmware the registry does not have, another HAL
hash, a feature.xml value, the tree order of a menu row, the wall colour pattern, a remote, the phone remote version,
the CEC dump, the restore file) and each one must turn the rule red. One mutant set for every model, none named here.
Run from anywhere; it reads only this repository. Exit 0 when the rule is green and every mutant is killed.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
RULE = os.path.join(G, "tools", "devices", "test", "stock_facts_gmpf.py")
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import layered  # noqa: E402

COPY = ["devices", "tools/devices/layered.py", "tools/devices/menu_layers.py", "apps/core/lumen-hal/src/org/lumen/hal"]
bad = 0


def run(root):
    return subprocess.run([sys.executable, "-B", RULE, root], capture_output=True, text=True)


r = run(G)
print(r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr)
if r.returncode:
    print("FAIL the rule is red on the tree")
    sys.exit(1)
T = tempfile.mkdtemp(prefix="stock_facts.")


def mutant(name, rel, old, new, count=1):
    global bad
    root = os.path.join(T, "r")
    shutil.rmtree(root, ignore_errors=True)
    for p in COPY:
        s, d = os.path.join(G, p), os.path.join(root, p)
        os.makedirs(os.path.dirname(d), exist_ok=True)
        (shutil.copytree if os.path.isdir(s) else shutil.copy2)(s, d)
    p = os.path.join(root, rel)
    s = open(p, encoding="utf-8").read()
    if old not in s:
        print("FAIL mutant %s: %r not in %s" % (name, old[:60], rel))
        bad += 1
        return
    open(p, "w", encoding="utf-8").write(s.replace(old, new, count))
    if run(root).returncode:
        print("ok   mutant %s killed" % name)
    else:
        print("FAIL mutant %s survived" % name)
        bad += 1


def rel(p):
    return os.path.relpath(p, G)


try:
    for dc in sorted(glob.glob(os.path.join(G, "devices", "*", "device.conf"))):
        c = os.path.basename(os.path.dirname(dc))
        files = sorted(f for d in layered.dirs(dc) for f in glob.glob(os.path.join(d, "research", "stock_v*.tsv")))
        if not files:
            continue
        own = [f for f in files if os.path.dirname(os.path.dirname(f)) == os.path.join(G, "devices", c)][0]
        model = layered.effective(dc)["models"].split()[0]
        ownt = open(own, encoding="utf-8").read()
        # the registry's firmware list, in the file that gives it
        src = [f for f in layered.files(dc) if re.search(r"^vendor_firmware=", open(f).read(), re.M)][-1]
        line = re.search(r"^vendor_firmware=.*$", open(src).read(), re.M).group(0)
        mutant(c + "_firmware", rel(src), line, line.replace("=v", "=v9.9.9 v", 1))
        # the gmpf hash of its newest firmware in GmpfHash.java
        h = [l.split("\t")[2] for f in files for l in open(f) if l.startswith("fact\tgmpf_hash\t")][0]
        mutant(c + "_hash", "apps/core/lumen-hal/src/org/lumen/hal/GmpfHash.java", '"%s"' % h, '"%s"' % ("0" * 40))
        for k in ("support_filmmaker", "support_dolby_vision", "support_elder_color"):
            m = re.search(r"^feature\t%s\t(true|false)" % k, ownt, re.M)
            if m:
                flip = "false" if m.group(1) == "true" else "true"
                mutant("%s_%s" % (c, k), rel(own), m.group(0), m.group(0)[:-len(m.group(1))] + flip)
        m = re.search(r"^tree\t(iris|color_temp|picture_modes)\t([^\t]+)\t(\S+) (\S+)", ownt, re.M)
        if m:
            mutant(c + "_tree_order", rel(own), m.group(0), "tree\t%s\t%s\t%s %s" % (m.group(1), m.group(2), m.group(4), m.group(3)))
        m = re.search(r"^tree\tabsent\t(\S+)", ownt, re.M)
        if m:
            mutant(c + "_absent_" + m.group(1), rel(own), m.group(0), "tree\tabsent\tlamp")
        wc = layered.find(dc, "wallcolor.conf")
        if wc:
            t = re.search(r"^type=(\d+)$", open(wc).read(), re.M)
            mutant(c + "_wall_type", rel(wc), t.group(0), "type=%d" % (int(t.group(1)) + 1))
        rem = open(os.path.join(G, "devices/_remotes/remotes.tsv"), encoding="utf-8").read()
        rr = [l for l in rem.splitlines() if "\t000d:3838\t" in l and model in l.split("\t")[9].split(",")]
        if rr:
            cut = rr[0].split("\t")
            cut[9] = ",".join(x for x in cut[9].split(",") if x != model)
            mutant(c + "_remote_3838", "devices/_remotes/remotes.tsv", rr[0], "\t".join(cut))
        vc = re.search(r"^%s\t(\d+)\t" % model, open(os.path.join(G, "devices/_control/vcontrol.tsv")).read(), re.M)
        if vc:
            mutant(c + "_vcontrol", "devices/_control/vcontrol.tsv", vc.group(0), "%s\t%d\t" % (model, int(vc.group(1)) + 1))
        cec = [l for l in open(os.path.join(G, "devices/_image/cec_stock.tsv")) if l.startswith("dump ") and c in l.split()[2].split(",")]
        if cec:
            mutant(c + "_cec_sha", "devices/_image/cec_stock.tsv", cec[0], cec[0].replace(cec[0].split()[3], "f" * 64))
        cat = os.path.join(G, "devices", c, "catalog.conf")
        if os.path.isfile(cat) and "fact\trestore_bin\t" in ownt:
            rf = re.search(r"^restore_file=(\S+)$", open(cat).read(), re.M)
            mutant(c + "_restore_file", rel(cat), rf.group(0), "restore_file=XGIMI_HORSEPOPULAR_XR99C.bin")
finally:
    shutil.rmtree(T, ignore_errors=True)
print("stock facts mutants: %s" % ("GREEN" if bad == 0 else "RED %d" % bad))
sys.exit(1 if bad else 0)
