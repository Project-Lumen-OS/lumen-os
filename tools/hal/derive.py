#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""derive.py: HIDL code tables derived BY METHOD NAME (library of tools/hal/gen_codes.py, no main).

One projector family on the HIDL gmpf HAL (Z9X, RS 20, T10 ...) sends the same calls, but a vendor build may number
an interface differently (RS 20 v1.6.5x IGmpf2: 330 methods against the Z9X's 333, every code from 12 on moved).
So no model writes code numbers: the family's call list is the reference table (the Z9X's devices/horse_popular/hal/
codes.tsv, proven against the Z9X stock), and every other model's table is derived from it, method by method, from
that model's stock tables (tools/hal/hidl_stock.py, read from the vendor library of the official package).

A derived table is described by hal/codes.from next to its codes.tsv (devices/<codename>/hal/ or a family folder
devices/_family/<f>/hal/), tab separated, '#' comments:

  calls<TAB>TABLE                           the reference table (its rows, java app, interfaces)
  ref<TAB>IFACE<TAB>STOCK<TAB>PROXY         the stock table the reference codes come from (one per interface)
  stock<TAB>IFACE<TAB>STOCK<TAB>PROXY       a stock table of this model (several per interface: every firmware the
                                            table serves; a method must have one code and one argument list in all)
  java<TAB>APP<TAB>PATH                     the generated HalCodes class of this table (as in codes.tsv)
  map<TAB>SRC<TAB>DEST                      the shared GmpfCodes map SRC (Z9xCodes.java: GmpfOp -> HalCodes NAME) as DEST
                                            for this table: rows of methods the model lacks are left out
  menu<TAB>DEST<TAB>PARENT                  the menu layer DEST (tools/devices/menu_layers.py) on PARENT: every row of
                                            devices/_hal/gmpf-hidl/needs.tsv that needs a method this table lacks is
                                            off there with the reason, unless PARENT's effective profile has it off
                                            already (so a capability the HAL lacks is never shown, with no hand edit)
  client<TAB>SRC<TAB>DEST<TAB>FORKS         the reference app's client SRC as DEST for this table, line for line, with
                                            every HalCodes.NAME of a method this table lacks routed through the map
                                            (key(GmpfOp.OP), the op of NAME's map row): the call is refused as absent
                                            and nothing is sent. FORKS is the generated fork table of the model
                                            chain (forks= of its device.conf, tools/devices/check_forks.py): one row
                                            per DEST with SRC's sha256, so a change of SRC is red until regenerated.
                                            A stopgap while SRC names that code directly (it goes when SRC calls
                                            key(OP) itself, at a Z9X pin round)

Rules (each one refuses the derivation, nothing is written):
  - every reference row's code is the reference stock code of its method (so the reference is what the names say);
  - a method the model has takes the model's code; its wire arguments must equal the reference's (a changed
    signature needs a person, not a renumbering);
  - a method the model lacks is left out and listed as '# absent' (its feature is off: GmpfCodes.has() is false and
    nothing is ever sent);
  - IBase rows (fixed HIDL codes) are copied.
"""
import hashlib
import os
import re
import sys

import codes_table
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ota"))
import licences  # noqa: E402

HEADER = codes_table.HEADER
PUT = re.compile(r"^\s*\.put\((\w+), HalCodes\.(\w+)\)(;?)\s*$")


class DeriveError(Exception):
    pass


def specs(root):
    """[(label, spec path, table path)] of every hal/codes.from (models, then families), sorted."""
    out = []
    d = os.path.join(root, "devices")
    for c in sorted(os.listdir(d)):
        p = os.path.join(d, c, "hal", "codes.from")
        if os.path.isfile(p):
            out.append((c, p))
    fam = os.path.join(d, "_family")
    if os.path.isdir(fam):
        for f in sorted(os.listdir(fam)):
            p = os.path.join(fam, f, "hal", "codes.from")
            if os.path.isfile(p):
                out.append(("_family/" + f, p))
    return [(c, p, os.path.join(os.path.dirname(p), "codes.tsv")) for c, p in out]


def read_spec(path):
    s = {"calls": None, "ref": {}, "stock": [], "java": [], "map": [], "menu": [], "client": [], "forks": None}
    for n, line in enumerate(open(path, encoding="utf-8").read().split("\n"), 1):
        if not line or line.startswith("#"):
            continue
        f = line.split("\t")
        if f[0] == "calls" and len(f) == 2:
            s["calls"] = f[1]
        elif f[0] == "ref" and len(f) == 4:
            s["ref"][f[1]] = (f[2], f[3])
        elif f[0] == "stock" and len(f) == 4:
            s["stock"].append((f[1], f[2], f[3]))
        elif f[0] == "java" and len(f) == 3:
            s["java"].append((f[1], f[2]))
        elif f[0] == "map" and len(f) == 3:
            s["map"].append((f[1], f[2]))
        elif f[0] == "menu" and len(f) == 3:
            s["menu"].append((f[1], f[2]))
        elif f[0] == "client" and len(f) == 4 and s["forks"] in (None, f[3]):
            s["client"].append((f[1], f[2]))
            s["forks"] = f[3]
        else:
            raise DeriveError("%s:%d: cannot read %r" % (path, n, line))
    if not s["calls"] or not s["stock"] or len(s["java"]) != 1:
        raise DeriveError("%s: needs one calls line, stock lines and one java line" % path)
    return s


def stock_table(root, rel, proxy):
    """{method: (code, args)} of one proxy of a hidl_stock.py table."""
    p = os.path.join(root, rel)
    if not os.path.isfile(p):
        raise DeriveError("stock table %s is missing" % rel)
    out = {}
    for line in open(p, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        m = re.match(r"_hidl_(\w+)\((.*)\)$", f[2]) if len(f) == 3 else None
        if not m or not f[1].isdigit():
            raise DeriveError("%s: cannot read %r" % (rel, line))
        if f[0] == proxy:
            if m.group(1) in out:
                raise DeriveError("%s: %s %s twice" % (rel, proxy, m.group(1)))
            out[m.group(1)] = (int(f[1]), m.group(2))
    if not out:
        raise DeriveError("%s: no %s rows" % (rel, proxy))
    return out


def derive(root, label, spec_path):
    """(codes.tsv text, {DEST: java text}) of one spec, or DeriveError."""
    s = read_spec(spec_path)
    calls = codes_table.read(os.path.join(root, s["calls"]))
    ref = {i: stock_table(root, t, p) for i, (t, p) in s["ref"].items()}
    tgt = {}
    for i, t, p in s["stock"]:
        tgt.setdefault(i, []).append((t, stock_table(root, t, p)))
    (app, jpath), = s["java"]
    ref_app = {a for a, _ in calls["java"]}
    if len(ref_app) != 1:
        raise DeriveError("%s: the reference table %s needs exactly one java line" % (spec_path, s["calls"]))
    ref_app = ref_app.pop()
    rows, absent, errs = [], [], []
    for r in calls["rows"]:
        if r["iface"] == "IBase":
            rows.append((r, r["code_text"], r["source"]))
            continue
        if r["iface"] not in ref or r["iface"] not in tgt:
            errs.append("%s: no ref or stock line for interface %s" % (r["name"], r["iface"]))
            continue
        got = ref[r["iface"]].get(r["method"])
        if got is None or got[0] != r["code"]:
            errs.append("%s: the reference code %d is not the reference stock code of %s (%s)"
                        % (r["name"], r["code"], r["method"], got and got[0]))
            continue
        codes = set()
        for t, st in tgt[r["iface"]]:
            have = st.get(r["method"])
            if have is None:
                codes.add(None)
            else:
                if have[1] != got[1]:
                    errs.append("%s: %s takes (%s) in %s, the reference (%s): a changed call needs a person"
                                % (r["name"], r["method"], have[1], t, got[1]))
                codes.add(have[0])
        if codes == {None}:
            absent.append(r)
        elif None in codes or len(codes) != 1:
            errs.append("%s: %s has codes %s across the stock tables of this table (split the table)"
                        % (r["name"], r["method"], sorted(codes, key=str)))
        else:
            code = codes.pop()
            src = "stock %s by name (%s %d)" % (" + ".join(t for t, _ in tgt[r["iface"]]), s["calls"], r["code"])
            rows.append((r, str(code), src))
    if errs:
        raise DeriveError("%s:\n  %s" % (spec_path, "\n  ".join(errs)))
    rel_spec = os.path.relpath(spec_path, root)
    out = ["# GENERATED by tools/hal/gen_codes.py from %s (tools/hal/derive.py): do not edit. The rows of %s with each"
           % (rel_spec, s["calls"]),
           "# code taken BY METHOD NAME from the stock tables below; methods this model lacks are listed as absent.",
           "java\t%s\t%s" % (app, jpath)]
    out += ["stock\t%s\t%s\t%s" % x for x in s["stock"]]
    out.append(HEADER)
    for r, code, src in rows:
        apps = ",".join(app if a == ref_app else a for a in r["apps"])
        out.append("\t".join([r["iface"], r["name"], code, r["method"], r["args"], r["kind"], apps, src]))
    for r in absent:
        out.append("# absent\t%s\t%s\t%s\t(%s %d)" % (r["iface"], r["name"], r["method"], s["calls"], r["code"]))
    routed = [src for src, _ in s["map"]] + [src for src, _ in s["client"]]
    for name, where in direct_uses(root, calls, {r["name"] for r in absent}, routed):
        out.append("# blocked\t%s\t%s\t(named outside the GmpfCodes map: the app cannot be built for this table until"
                   " that call goes through key(OP))" % (name, where))
    table = "\n".join(out) + "\n"
    kept = {r["name"] for r, _, _ in rows}
    gone = {r["name"] for r in absent}
    maps = {}
    for src, dest in s["map"]:
        maps[os.path.join(root, dest)] = map_copy(root, src, kept, gone, rel_spec)
    for dest, parent in s["menu"]:
        maps[os.path.join(root, dest)] = menu_layer(root, parent, {(r["iface"], r["method"]) for r in absent}, rel_spec)
    if s["client"]:
        ops = {}
        for src, _ in s["map"]:
            for line in open(os.path.join(root, src), encoding="utf-8"):
                m = PUT.match(line)
                if m:
                    ops[m.group(2)] = m.group(1)
        rows = ["# GENERATED by tools/hal/gen_codes.py from %s (tools/hal/derive.py): do not edit. The client copies of"
                " this table (copy, origin, origin sha256, note), checked by tools/devices/check_forks.py." % rel_spec]
        for src, dest in s["client"]:
            maps[os.path.join(root, dest)] = client_copy(root, src, gone, ops, rel_spec)
            rows.append("\t".join([dest, src, hashlib.sha256(open(os.path.join(root, src), "rb").read()).hexdigest(),
                                   "generated: the absent calls %s refused through the map" % src.rsplit("/", 1)[-1]]))
        maps[os.path.join(root, s["forks"])] = "\n".join(rows) + "\n"
    return table, maps


def client_copy(root, src, gone, ops, rel_spec):
    """SRC line for line with every HalCodes.NAME of an absent NAME as key(GmpfOp.OP) (refused as absent, nothing
    sent), the generated mark on its last line so every other line keeps its number."""
    lines = open(os.path.join(root, src), encoding="utf-8").read().split("\n")
    hits, unknown = [], []

    def route(m):
        name = m.group(1)
        if name not in gone:
            return m.group(0)
        if name not in ops:
            unknown.append(name)
            return m.group(0)
        hits.append(name)
        return "key(org.lumen.hal.gmpf.GmpfOp.%s)" % ops[name]
    out = [re.sub(r"\bHalCodes\.([A-Z][A-Z0-9_]*)\b", route, line) for line in lines]
    if unknown:
        raise DeriveError("%s names the absent %s, which no map row names: route it by hand" % (src, " ".join(unknown)))
    if not hits:
        raise DeriveError("%s names no absent code of %s: a pointless client line, delete it" % (src, rel_spec))
    while out and out[-1] == "":
        out.pop()
    out[-1] += "  // GENERATED by tools/hal/gen_codes.py from %s and %s (tools/hal/derive.py): do not edit." % (
        src, rel_spec)
    return "\n".join(out) + "\n"


NEEDS = "devices/_hal/gmpf-hidl/needs.tsv"


def needs(root):
    """[(menu row, iface, [methods], evidence)] of the needs table."""
    out = []
    for n, line in enumerate(open(os.path.join(root, NEEDS), encoding="utf-8").read().split("\n"), 1):
        if not line or line.startswith("#"):
            continue
        f = line.split("\t")
        if len(f) != 4 or not f[2].split():
            raise DeriveError("%s:%d: needs row, interface, methods, evidence" % (NEEDS, n))
        out.append((f[0], f[1], f[2].split(), f[3]))
    return out


def menu_layer(root, parent, absent, rel_spec):
    """The generated menu layer: the needs rows whose methods the table lacks, off, unless the parent has them off."""
    import json
    import sys
    sys.path.insert(0, os.path.join(root, "tools", "devices"))
    import menu_layers  # noqa: E402
    eff = menu_layers.effective(os.path.join(root, parent))
    shown = set(eff.get("features", {}))
    off = {}
    for row, iface, methods, ev in needs(root):
        miss = [m for m in methods if (iface, m) in absent]
        if miss and row in shown:
            off[row] = "the stock HAL has no %s %s (%s; %s)" % (iface, " / ".join(miss), rel_spec, ev)
    layer = {"include": parent,
             "note": "GENERATED by tools/hal/gen_codes.py from %s and %s: the rows this firmware's HAL cannot serve, off."
                     % (rel_spec, NEEDS)}
    if off:
        layer["off"] = off
    return json.dumps(layer, indent=2, ensure_ascii=False) + "\n"


def direct_uses(root, calls, gone, maps):
    """[(NAME, file:line)] of absent NAMEs the reference app's sources name as HalCodes.NAME outside the map files."""
    out = []
    for app, rel in calls["java"]:
        src = rel.split("/src/", 1)[0] + "/src"
        for d, _, names in sorted(os.walk(os.path.join(root, src))):
            for n in sorted(names):
                p = os.path.join(d, n)
                r = os.path.relpath(p, root)
                if not n.endswith(".java") or r == rel or r in maps:
                    continue
                for i, line in enumerate(open(p, encoding="utf-8"), 1):
                    for m in re.finditer(r"\bHalCodes\.([A-Z][A-Z0-9_]*)\b", line):
                        if m.group(1) in gone:
                            out.append((m.group(1), "%s:%d" % (r, i)))
    return out


def map_copy(root, src, kept, gone, rel_spec):
    """SRC without the .put rows of absent NAMEs (the statement end moves to the row before) and without its licence
    header: the generated mark is the first line, the header goes on top when the copy is written."""
    lines = licences.unheaded(open(os.path.join(root, src), encoding="utf-8").read()).split("\n")
    out, unknown = [], []
    for line in lines:
        m = PUT.match(line)
        if not m:
            out.append(line)
            continue
        name = m.group(2)
        if name in gone:
            if m.group(3):
                j = len(out) - 1
                if not PUT.match(out[j]):
                    raise DeriveError("%s: the first row of a map is absent, cannot end it" % src)
                out[j] += ";"
            continue
        if name not in kept:
            unknown.append(name)
        out.append(line)
    if unknown:
        raise DeriveError("%s names %s, not rows of the reference table" % (src, " ".join(unknown)))
    head = "// GENERATED by tools/hal/gen_codes.py from %s and %s (tools/hal/derive.py): do not edit." % (src, rel_spec)
    return head + "\n" + "\n".join(out)
