#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""extract.py: stock facts from an official firmware, through the vendor's own extractor (docs/architecture/
the design notes). One front door for every vendor: the kinds and their output formats are Lumen's, the commands that
read a vendor's packages are data in devices/_vendor/<vendor>/vendor.conf (extract.<kind>=COMMAND, or none).

  extract.py [--root GSI] KIND --vendor V --in PATH --out FILE    run the vendor's extractor, then check FILE
  extract.py [--root GSI] --check KIND FILE                       check a file of that kind (any vendor)
  extract.py [--root GSI] --list --vendor V                       which kinds the vendor has a tool for

Kinds and their formats (tab-separated, '#' comment lines), each read by the generic tools named:
  hal-codes    INTERFACE  CODE  METHOD(ARGS)            tools/hal/codes_table.py, gen_codes.py, derive.py (by name)
  stock-facts  KIND  KEY  VALUE  EVIDENCE               devices/<c>/stock.tsv, tools/devices/test/stock_facts.py
  menu-tree    NODE  PARENT  TYPE  OPTIONS              the stock settings tree a menu profile cites (one row a node)
  remotes      NAME  VENDOR_ID  PRODUCT_ID  KEYMAP      devices/_remotes/remotes.tsv rows
  restore      PARTITION  SIZE  SHA256  SOURCE          the restore rows of the installer and the USB stick
{in}, {out} and {root} in a command are the input, the output file and the tree. Exit 0 green, 1 red, 2 usage.
"""
import argparse
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KINDS = {"hal-codes": 3, "stock-facts": 4, "menu-tree": 4, "remotes": 4, "restore": 4}


def vendor_conf(root, vendor):
    p = os.path.join(root, "devices", "_vendor", vendor, "vendor.conf")
    if not os.path.isfile(p):
        raise SystemExit("extract: no vendor %s (%s)" % (vendor, os.path.relpath(p, root)))
    out = {}
    for ln in open(p, encoding="utf-8"):
        if ln.strip() and not ln.startswith("#") and "=" in ln:
            k, v = ln.rstrip("\n").split("=", 1)
            out[k.strip()] = v.strip()
    return out


def check(kind, path):
    """-> problems of a file of KIND (empty = good)."""
    cols, bad, keys, rows = KINDS[kind], [], set(), 0
    for n, ln in enumerate(open(path, encoding="utf-8", errors="replace"), 1):
        if not ln.strip() or ln.startswith("#"):
            continue
        c = ln.rstrip("\n").split("\t")
        rows += 1
        if len(c) < cols or any(not x.strip() for x in c[:cols]):
            bad.append("%s:%d: %d columns wanted, got %d" % (path, n, cols, len(c)))
            continue
        if kind == "hal-codes":
            if not re.match(r"^\d+$", c[1]):
                bad.append("%s:%d: code %r is not a number" % (path, n, c[1]))
            if not re.match(r"^\w+\(.*\)$", c[2].replace("_hidl_", "")):
                bad.append("%s:%d: method %r is not NAME(ARGS)" % (path, n, c[2]))
            key = (c[0], c[1])
        elif kind == "restore":
            if not re.match(r"^[0-9a-f]{64}$", c[2]):
                bad.append("%s:%d: sha256 %r" % (path, n, c[2]))
            key = (c[0],)
        else:
            key = tuple(c[:2])
        if key in keys:
            bad.append("%s:%d: %s twice" % (path, n, " ".join(key)))
        keys.add(key)
    if not rows:
        bad.append("%s: no rows" % path)
    return bad


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(HERE)))
    ap.add_argument("kind", nargs="?", choices=sorted(KINDS))
    ap.add_argument("file", nargs="?")
    ap.add_argument("--vendor")
    ap.add_argument("--in", dest="inp")
    ap.add_argument("--out")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args(argv)
    root = os.path.abspath(a.root)
    if a.list:
        conf = vendor_conf(root, a.vendor)
        for k in sorted(KINDS):
            print("%s\t%s" % (k, conf.get("extract." + k, "none")))
        return 0
    if a.check:
        if not a.kind or not a.file:
            return 2
        bad = check(a.kind, a.file)
        for b in bad:
            print("extract: RED " + b)
        print("extract: %s %s %s" % (a.kind, a.file, "RED" if bad else "OK"))
        return 1 if bad else 0
    if not (a.kind and a.vendor and a.inp and a.out):
        ap.print_usage()
        return 2
    cmd = vendor_conf(root, a.vendor).get("extract." + a.kind, "none")
    if cmd == "none":
        print("extract: vendor %s has no %s tool yet (devices/_vendor/%s/vendor.conf)" % (a.vendor, a.kind, a.vendor))
        return 1
    cmd = cmd.replace("{in}", '"%s"' % a.inp).replace("{out}", '"%s"' % a.out).replace("{root}", '"%s"' % root)
    r = subprocess.call(cmd, shell=True, cwd=root)
    if r != 0:
        print("extract: %s exited %d" % (cmd, r))
        return 1
    return main(["--root", root, "--check", a.kind, a.out])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
