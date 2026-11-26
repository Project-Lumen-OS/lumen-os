#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""verify_expect.py TREE CODENAME: what verify_image.sh expects of one RS30 family model's trial image, read from the
tree the image was built from (never a value typed into the check). Prints shell assignments:

  MODEL NAME TAGP OUT PRODUCT   the model's descriptor (devices/<c>/build.conf through layered.py)
  HASHES                        the gmpf hashes of the model's rule (GmpfModel row -> GmpfHash rule constants)
  PROFILE                       a file with the model's effective menu profile (menu_layers.py), the APK must ship it

Exit 1 when the descriptor and the model's stock prop file disagree on the product (ro.xgimi.product.name)."""
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile

TREE, C = sys.argv[1], sys.argv[2]


def run(*a):
    return subprocess.run([sys.executable, "-B"] + list(a), cwd=TREE, check=True, capture_output=True, text=True).stdout


def descriptor(rel):
    out = {}
    for ln in run("tools/devices/layered.py", rel).splitlines():
        k, _, v = ln.partition("=")
        out[k] = v
    return out


b = descriptor("devices/%s/build.conf" % C)
d = descriptor("devices/%s/device.conf" % C)
model = d["models"].split()[0]
mprop = [p for p in b["props"].split() if p.endswith(b["var.MPROPF"])]
prod = [ln.split("=", 1)[1].strip() for ln in open(os.path.join(TREE, mprop[0]), encoding="utf-8")
        if ln.startswith("ro.xgimi.product.name=")]
if prod != [b["var.PRODUCT"]]:
    sys.exit("verify_expect: %s var.PRODUCT %s, its stock prop file %s says %s" % (C, b["var.PRODUCT"], mprop[0], prod))

# the hash rule of the model's GmpfModel row, its hashes from GmpfHash
hal = os.path.join(TREE, "apps/core/lumen-hal/src/org/lumen/hal")
rows = open(os.path.join(hal, "GmpfModel.java"), encoding="utf-8").read()
m = re.search(r'\w+\("%s", "%s", GmpfHash\.(\w+)' % (C, model), rows)
if not m:
    sys.exit("verify_expect: no GmpfModel row for %s %s" % (C, model))
consts = dict(re.findall(r'static final String (\w+) = "([^"]+)"', open(os.path.join(hal, "GmpfHash.java"), encoding="utf-8").read()))
rule = re.search(r"HashRule %s = new HashRule\(([^)]*)\)" % m.group(1), open(os.path.join(hal, "GmpfHash.java"), encoding="utf-8").read())
if not rule:
    sys.exit("verify_expect: no HashRule %s in GmpfHash" % m.group(1))
hashes = [consts[a.strip()] for a in rule.group(1).split(",") if re.fullmatch(r"[0-9a-f]{40}", consts.get(a.strip(), ""))]

prof = tempfile.NamedTemporaryFile("w", suffix=".menu.json", delete=False, encoding="utf-8")
if os.path.exists(os.path.join(TREE, "tools/devices/menu_layers.py")):
    prof.write(run("tools/devices/menu_layers.py", "devices/%s/menu.json" % C))
else:
    prof.write(open(os.path.join(TREE, "devices/%s/menu.json" % C), encoding="utf-8").read())
prof.close()
json.load(open(prof.name, encoding="utf-8"))

for k, v in (("MODEL", model), ("NAME", d["name"]), ("TAGP", b["var.TAGP"]), ("OUT", b["remote_dir"]),
             ("PRODUCT", b["var.PRODUCT"]), ("HASHES", " ".join(hashes)), ("PROFILE", prof.name)):
    print("%s=%s" % (k, shlex.quote(v)))
