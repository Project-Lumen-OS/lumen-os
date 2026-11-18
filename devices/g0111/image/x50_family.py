#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""x50_family.py: the X50 family of the registry, read from the device descriptors. No model is named here.

A device is a member when its build descriptor devices/<codename>/build.conf takes the X50 recipe
(steps=devices/g0111/image/steps.tsv). Its fields come from that descriptor: model, keys, remote_dir, var.PREFIX (the
image token: x50, x50max, titanpro, ...) and var.PCERT (the sha256 of the DER of devices/<codename>/certs/
platform.x509.pem). The Mac-side tools (sign_x50_base.py, gen_x50_apex_keys.sh, build_home_x50.sh) ask it whether a
codename is an X50 family model, so a new model of the family is a new descriptor and no code change.

    python3 x50_family.py list               codename, model, prefix, remote_dir per member (tab separated)
    python3 x50_family.py check CODENAME     exit 0 when CODENAME is a member, 1 (with the members) when not
    python3 x50_family.py get CODENAME KEY   one field: model, keys, prefix, remote_dir, pcert
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
STEPS = "devices/g0111/image/steps.tsv"
FIELDS = {"model": "model", "keys": "keys", "remote_dir": "remote_dir", "prefix": "var.PREFIX", "pcert": "var.PCERT"}


def read_conf(path):
    """key=value lines of a descriptor (comments and blank lines skipped); KEY.COND lines are kept under their name."""
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if s and not s.startswith("#") and "=" in s:
                k, v = s.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def members(root=G):
    """{codename: {model, keys, remote_dir, prefix, pcert}} of every descriptor that takes the X50 recipe."""
    out = {}
    d = os.path.join(root, "devices")
    for c in sorted(os.listdir(d)):
        p = os.path.join(d, c, "build.conf")
        if c.startswith("_") or not os.path.isfile(p):
            continue
        conf = read_conf(p)
        if conf.get("steps") != STEPS:
            continue
        out[c] = {k: conf.get(v, "") for k, v in FIELDS.items()}
    return out


def main(a):
    m = members()
    if a == ["list"]:
        for c, f in m.items():
            print("\t".join((c, f["model"], f["prefix"], f["remote_dir"])))
        return 0
    if len(a) == 2 and a[0] == "check":
        if a[1] in m:
            return 0
        print("x50_family: %s is not an X50 family model (%s)" % (a[1], ", ".join(m)), file=sys.stderr)
        return 1
    if len(a) == 3 and a[0] == "get" and a[2] in FIELDS:
        if a[1] not in m or not m[a[1]][a[2]]:
            print("x50_family: no %s for %s" % (a[2], a[1]), file=sys.stderr)
            return 1
        print(m[a[1]][a[2]])
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
