#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""fwset.py: the XGIMI set of a model for the Python tools, read from the one place it is kept, the Java module
apps/core/lumen-fwcopy (FwModel rows, FwSet.NEVER and FwSet.DYNAMIC). The installer and the updater compile that
module; this reader lets ota_conf.py check devices/<codename>/ota.conf ab_ota_partitions against the same data instead
of a second copy (apps/core/lumen-fwcopy/test FwListsTest pins both readers to one answer).

  fwset.py CODE...      print the update list of the model codes (one family, comma-separated) or exit 1
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.realpath(__file__))   # through a linked tools/ (the test fixtures) to the tree
SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(HERE))), "apps", "core", "lumen-fwcopy", "src", "org",
                   "lumen", "fwcopy")


def _strings(expr):
    """the text of a Java expression made of string literals joined by +"""
    return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', expr))


def _csv(expr):
    t = _strings(expr)
    return [x for x in t.split(",") if x] if t else []


def load(src=SRC):
    """{family: {"codes": [...], "stock": [...], "pairs": [...], "keep": [...], "refusal": bool, "install_only": [...],
    "post_writes": [PostWrite constant names]}},
    NEVER, DYNAMIC"""
    m = open(os.path.join(src, "FwModel.java"), encoding="utf-8").read()
    s = open(os.path.join(src, "FwSet.java"), encoding="utf-8").read()
    consts = {}
    for name, expr in re.findall(r"static final List<String> (\w+) = list\((.*?)\);", m, re.S):
        consts[name] = _csv(expr)
    rows = {}
    for body in re.findall(r"public static final FwModel \w+ = new FwModel\((.*?)\);", m, re.S):
        first, rest = body.split(",", 1)
        fam = _strings(first)
        args = [a.strip() for a in re.findall(r"list\(\"[^)]*\"\)|PostWrite\.\w+|[A-Z0-9]+_[A-Z]+|null|\"(?:[^\"\\]|\\.)*\"(?:\s*\+\s*\"(?:[^\"\\]|\\.)*\")*", rest)]
        # after the seven values: the partitions the vendor's post-install step writes (PostWrite rows), never in the
        # update list (update_engine cannot write them)
        posts = [a for a in args[7:] if a.startswith("PostWrite.")]
        args = args[:7] + [a for a in args[7:] if not a.startswith("PostWrite.")]
        def val(a):
            return _csv(a[5:-1]) if a.startswith("list(") else consts.get(a, [])
        if len(args) != 7:
            raise ValueError("FwModel.java: the row of " + fam + " has " + str(len(args)) + " values, 7 wanted")
        rows[fam] = {"codes": val(args[0]), "stock": val(args[1]), "pairs": val(args[2]), "keep": val(args[3]),
                     "refusal": args[4] != "null", "install_only": val(args[6]), "post_writes": posts}
    def arr(name):
        g = re.search(r"List<String> " + name + r" = Collections\.unmodifiableList\(Arrays\.asList\((.*?)\)\);", s, re.S)
        if g is None:
            raise ValueError("FwSet.java has no list " + name)
        return re.findall(r'"([a-z0-9_]+)"', g.group(1))
    return rows, arr("NEVER") + arr("POST_ONLY"), arr("DYNAMIC")


def update_list(codes, src=SRC):
    """the update list (FwSet.updateList) of the family of these model codes; None when they name no family or
    several, or the family never copies"""
    rows, never, dyn = load(src)
    fams = {f for f, r in rows.items() for c in codes if c in r["codes"]}
    if len(fams) != 1:
        return None
    r = rows[fams.pop()]
    if r["refusal"]:
        return None
    return [n for n in r["stock"] if n not in never and n not in dyn and n in r["pairs"] and n not in r["keep"]
            and n not in r["install_only"]]


if __name__ == "__main__":
    out = update_list(sys.argv[1:])
    if out is None:
        sys.exit("no XGIMI set for " + " ".join(sys.argv[1:]))
    print(",".join(out))
