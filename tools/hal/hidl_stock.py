#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""hidl_stock.py: the stock code table of a HIDL proxy library, read offline from the vendor file (no device).

  hidl_stock.py LIB [--nm NM] [--label TEXT] [--iface I ...] > TABLE.tsv
  hidl_stock.py --compare A.tsv B.tsv        what B renumbers against A, by method name (report, exit 0)

hidl-gen numbers the methods of an interface 1, 2, ... in declaration order and emits the proxy's static
BpHw<I>::_hidl_<method> functions in that same order, so their address order in the library is the code of every
method (proven on the Z9X proxy: all 702 IGmpf and 333 IGmpf2 codes of research/play6/z9x_gmpf_codes.txt). The stub's
BnHw<I>::_hidl_<method> functions are a second witness when the library exports them: their order must be the same,
else the tool refuses. Default interfaces: IGmpf and IGmpf2 of xgimi.hardware.gmpf@1.0.

Output: comment lines, then PROXY<TAB>CODE<TAB>_hidl_METHOD(ARGS), ARGS = the wire arguments of the proxy function
without its IInterface* and HidlInstrumentor* (the return value is not in the symbol). tools/hal/codes_table.py and
tools/hal/gen_codes.py read this format. Exit 0, 1 on a refusal, 2 usage.
"""
import hashlib
import os
import re
import subprocess
import sys

COMMON = ("android::hardware::IInterface*", "android::hardware::details::HidlInstrumentor*")
SYM = re.compile(r"^([0-9a-fA-F]+)\s+[TtWw]\s+(?:[\w:]+::)?(BpHw|BnHw)(\w+)::_hidl_(\w+)\((.*)\)\s*$")


def split_args(s):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def read_lib(lib, nm="nm", ifaces=("Gmpf", "Gmpf2")):
    """{iface: [(method, args)]} in code order, from the proxy; the stub order is checked when present."""
    text = subprocess.run([nm, "-D", "--defined-only", "-C", "-n", lib], capture_output=True, text=True, check=True).stdout
    order = {}
    for line in text.splitlines():
        m = SYM.match(line.strip())
        if not m or m.group(3) not in ifaces:
            continue
        side, iface, method, args = m.group(2), m.group(3), m.group(4), split_args(m.group(5))
        lst = order.setdefault((side, iface), [])
        if method not in [x for x, _ in lst]:
            lst.append((method, args))
    out = {}
    for iface in ifaces:
        bp = order.get(("BpHw", iface), [])
        if not bp:
            raise ValueError("%s: no BpHw%s::_hidl_* functions" % (lib, iface))
        if tuple(bp[0][1][:2]) != COMMON:
            raise ValueError("%s: BpHw%s::_hidl_%s does not start with %s" % (lib, iface, bp[0][0], ", ".join(COMMON)))
        bn = order.get(("BnHw", iface))
        if bn and [x for x, _ in bn] != [x for x, _ in bp]:
            raise ValueError("%s: the stub order of %s differs from the proxy order: refused" % (lib, iface))
        out[iface] = [(mth, [a for a in args[2:]]) for mth, args in bp]
    return out


def render(lib, table, label):
    sha = hashlib.sha256(open(lib, "rb").read()).hexdigest()
    head = ["# %s: stock HIDL code table read by tools/hal/hidl_stock.py (code = position of the proxy function)" % label,
            "# library %s sha256 %s" % (os.path.basename(lib), sha)]
    head += ["# BpHw%s: %d methods%s" % (i, len(table[i]), "") for i in table]
    head.append("# proxy\tcode\tmethod(wire arguments)")
    rows = ["BpHw%s\t%d\t_hidl_%s(%s)" % (i, n, m, ", ".join(a)) for i in table for n, (m, a) in enumerate(table[i], 1)]
    return "\n".join(head + rows) + "\n"


def parse(path):
    """{proxy: {method: (code, args)}} of a table this tool wrote."""
    out = {}
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        proxy, code, sig = line.rstrip("\n").split("\t")
        m = re.match(r"_hidl_(\w+)\((.*)\)$", sig)
        out.setdefault(proxy, {})[m.group(1)] = (int(code), m.group(2))
    return out


def compare(a, b):
    ta, tb = parse(a), parse(b)
    for proxy in sorted(set(ta) | set(tb)):
        x, y = ta.get(proxy, {}), tb.get(proxy, {})
        moved = sum(1 for m in x if m in y and x[m][0] != y[m][0])
        sig = [m for m in x if m in y and x[m][1] != y[m][1]]
        print("%s: %d / %d methods, %d renumbered, %d only in %s, %d only in %s, %d other arguments %s"
              % (proxy, len(x), len(y), moved, len(set(x) - set(y)), a, len(set(y) - set(x)), b, len(sig), " ".join(sig)))
        for m in sorted(set(x) - set(y), key=lambda k: x[k][0]):
            print("  only %s: %d %s" % (os.path.basename(a), x[m][0], m))
        for m in sorted(set(y) - set(x), key=lambda k: y[k][0]):
            print("  only %s: %d %s" % (os.path.basename(b), y[m][0], m))
    return 0


def main(argv):
    if argv[:1] == ["--compare"] and len(argv) == 3:
        return compare(argv[1], argv[2])
    if not argv or argv[0].startswith("-"):
        print(__doc__)
        return 2
    lib, nm, label, ifaces = argv[0], "nm", os.path.basename(argv[0]), []
    rest = argv[1:]
    while rest:
        if rest[0] == "--nm" and len(rest) > 1:
            nm, rest = rest[1], rest[2:]
        elif rest[0] == "--label" and len(rest) > 1:
            label, rest = rest[1], rest[2:]
        elif rest[0] == "--iface" and len(rest) > 1:
            ifaces.append(rest[1].lstrip("I")), rest.__delitem__(slice(0, 2))
        else:
            print(__doc__)
            return 2
    try:
        table = read_lib(lib, nm, tuple(ifaces) or ("Gmpf", "Gmpf2"))
    except (ValueError, subprocess.CalledProcessError, OSError) as e:
        print("hidl_stock: REFUSED %s" % e, file=sys.stderr)
        return 1
    sys.stdout.write(render(lib, table, label))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
