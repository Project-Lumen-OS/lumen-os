#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""gen_res.py TREE OUT: the string resources of an assembled app tree for the CEC parity harness.

Writes OUT/org/lumen/projector/R.java (every string name, ids in name order) and OUT/strings/<locale>.tsv (name TAB
text, the text as Android shows it: aliases followed, quotes and escapes resolved, \\n \\t \\\\ escaped back for the
file); 'default' = res/values, the rest one file per language folder (values-ru, values-pt-rBR ...).
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

LANG = re.compile(r"^values(?:-([a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z+]+))?$")


def android_text(raw):
    s = raw.strip()
    out, quoted, i = [], False, 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u" and i + 5 < len(s):
                out.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({"n": "\n", "t": "\t"}.get(n, n))
            i += 2
            continue
        if ch == '"':
            quoted = not quoted
            i += 1
            continue
        if ch.isspace() and not quoted:
            if not out or out[-1] != " ":
                out.append(" ")
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out).strip()


def read(tree):
    by_locale = {}
    res = os.path.join(tree, "res")
    for d in sorted(os.listdir(res)):
        m = LANG.match(d)
        if not m or not os.path.isdir(os.path.join(res, d)):
            continue
        loc = m.group(1) or "default"
        table = by_locale.setdefault(loc, {})
        for f in sorted(os.listdir(os.path.join(res, d))):
            if not f.endswith(".xml"):
                continue
            try:
                root = ET.parse(os.path.join(res, d, f)).getroot()
            except ET.ParseError:
                continue
            for e in root.findall("string"):
                table[e.get("name")] = android_text("".join(e.itertext()))
    return by_locale


def resolve(by_locale):
    base = by_locale.get("default", {})
    for loc, table in by_locale.items():
        for k in list(table):
            v, seen = table[k], set()
            while v.startswith("@string/") and v not in seen:
                seen.add(v)
                n = v[len("@string/"):]
                v = table.get(n, base.get(n, v))
            table[k] = v


def main(argv):
    tree, out = argv[1], argv[2]
    by_locale = read(tree)
    resolve(by_locale)
    names = sorted(set().union(*[set(t) for t in by_locale.values()]))
    pkg = os.path.join(out, "org", "lumen", "projector")
    os.makedirs(pkg, exist_ok=True)
    with open(os.path.join(pkg, "R.java"), "w", encoding="utf-8") as f:
        f.write("package org.lumen.projector;\n\npublic final class R {\n    public static final class string {\n")
        for i, n in enumerate(names):
            f.write("        public static final int %s = 0x%x;\n" % (n, 0x7f0f0000 + i))
        f.write("    }\n}\n")
    os.makedirs(os.path.join(out, "strings"), exist_ok=True)
    for loc, table in sorted(by_locale.items()):
        with open(os.path.join(out, "strings", loc + ".tsv"), "w", encoding="utf-8") as f:
            for n in sorted(table):
                t = table[n].replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
                f.write("%s\t%s\n" % (n, t))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
