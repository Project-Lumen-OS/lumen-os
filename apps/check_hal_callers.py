#!/usr/bin/env python3
"""check_hal_callers.py: build-time guard for the typed HAL whitelist of org.lumen.projector.

Fails (exit 1) when a method of hal/GmpfClient.java or hal/Gmpf2Client.java that performs a HAL
transaction has no caller outside the hal/ package, directly or through another hal method that does.
Such a method would put a transaction code into the dex that no reviewed UI path needs; park it in a
comment block until a caller (and, where its arguments are UNVERIFIED, a device test) exists.

A client of the shared gmpf call layer (it extends GmpfBase / Gmpf2Base, apps/core/lumen-gmpf) is checked
together with its shared body and GmpfCaller (taken from the app tree, else from apps/core/lumen-gmpf/src):
its transactions are wire.<call>(...) / hidl.send(...), each naming an operation as key(OP) (resolved through
the device code table the client's constructor passes, e.g. Z9xCodes.GMPF) or a device code directly. A method
of that group transacts when it has such a call and names an operation that is a row of the device table, or a
device code; an operation without a row is not on that projector (it throws, nothing is sent).

Source-level check (javac does not shrink and d8 runs without R8, so every compiled method ends up
in classes.dex): a method that is not compiled is not in the dex.

Also fails when a transact site sends a banned code (BANNED below: the hard rules of the GmpfClient /
Gmpf2Client headers and the optical zoom, kept here as data so a new method cannot bring one back): the
code is resolved through the generated hal/HalCodes.java ("HalCodes.NAME", tools/hal/gen_codes.py from
devices/horse_popular/hal/codes.tsv), the client's own "static final int NAME = <code>;" constants, an
integer literal, or an operation's row of the device table. A site whose code is a parameter is checked at
its callers only when its helper is itself named call* (each call of it is then a transact site); a helper
with another name (e.g. zh(457)) and an enum field are not checked: keep such helpers out of the clients,
or review their callers by hand.

EXCEPTIONS (below): a code that is banned everywhere except at one reviewed transact site, the stock caller's own
rule enforced by a guard: the site must sit in the named method of that client group, and that method's body must
call the named guard before the transaction (one such site per code, every other site of the code fails as banned).

Usage: check_hal_callers.py SRC_ROOT        (SRC_ROOT = Z9xProjector/src, or an assembled app's src)
"""
import os
import re
import sys

CLIENTS = ("GmpfClient.java", "Gmpf2Client.java")
# codes that never leave this app, per interface (IGmpf = GmpfClient, IGmpf2 = Gmpf2Client)
BANNED = {
    "GmpfClient.java": {
        24: "newAutoKst", 80: "no sensor", 155: "no sensor",
        94: "factory white balance", 95: "factory white balance", 533: "factory white balance",
        556: "factory white balance", 99: "auto-colour calibration", 101: "auto-colour calibration",
        102: "auto-colour calibration", 229: "power", 233: "power", 241: "power", 422: "power", 543: "power",
        299: "autofocus", 301: "autoFocus", 400: "reset", 421: "reset", 453: "factoryControlMotor",
        583: "gyroscopeCorrection",
        297: "controlZoomMotor (large zoom move)", 283: "zoom (factory move)", 457: "zoom (factory move)",
    },
    "Gmpf2Client.java": {76: "cleanAllCalibrationData"},
}
# Guarded exceptions: {client: {code: (method, guard call, rule)}}.
# IGmpf2 14 SetOpticalZoomLimitStep (BANNED as "zoom (large move)" until Lumen OS 1.0.8; 2026-10-30, zoom
# audit F1 in the design notes, the HB 189 EXCEPTION row of devices/goat_elegant/research/banned.tsv):
# only as stock ZoomUtils.setOpticalZoomLimitStep, the current IGmpf2 13 value +- 1 inside [-count, 0] of 153(12,10),
# only at optical index 0 (a fresh 116 read = 0), only inside the 18(true,2) + 121 zoom session; enforced by
# ZoomGuard.checkLimitStep, which GmpfCaller.writeZoomLimitStep (Gmpf2Client.setOpticalZoomLimitStep) calls before the
# transact.
EXCEPTIONS = {
    "Gmpf2Client.java": {
        14: ("writeZoomLimitStep", "ZoomGuard.checkLimitStep(",
             "zoom limit position: 13 value +-1 within [-count, 0], 116 = 0, inside 18(true,2) + 121"),
    },
}
# 153 zoomControl: only the stock home walk (mode 5, steps 10: stock's "restore default picture" and auto keystone's
# zoomReset, GmpfClient.zoomHome, Lumen OS 1.0.4) and the limit count read (mode 12, steps 10: stock
# ZoomStateView.getLimitStep, GmpfClient.getZoomLimitTotal, Lumen OS 1.0.8 zoom audit F1); every other mode stays a
# large / factory move
ONLY_ARGS = {"GmpfClient.java": {153: {(5, 10), (12, 10)}}}
CONST = re.compile(r"\bstatic\s+final\s+int\s+(\w+)\s*=\s*(0x[0-9a-fA-F]+|\d+)\s*;")
CODE_ARG = re.compile(r"\bcall(?:[A-Z]\w*)?\s*\(\s*([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)?|0x[0-9a-fA-F]+|\d+)\s*[,)]")
GENERATED = "HalCodes.java"   # the generated code table of the app (hal/HalCodes.java), constants used as HalCodes.NAME
METHOD = re.compile(r"^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?"
                    r"(?:synchronized\s+)?[\w.<>\[\], ?]+\s+(\w+)\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\s*\{")
TRANSACT = re.compile(r"\bcall(?:[A-Z]\w*)?\s*\(")
G = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULE_SRC = os.path.join(G, "apps", "core", "lumen-gmpf", "src")
# the shared body of each client and the call layer under both (apps/core/lumen-gmpf)
SHARED = {"GmpfClient.java": ("org/lumen/projector/hal/GmpfBase.java", "org/lumen/hal/gmpf/GmpfCaller.java"),
          "Gmpf2Client.java": ("org/lumen/projector/hal/Gmpf2Base.java", "org/lumen/hal/gmpf/GmpfCaller.java")}
GMPF_OP = "org/lumen/hal/gmpf/GmpfOp.java"
SHARED_CLIENT = re.compile(r"\bextends\s+Gmpf2?Base\s*<")
# a transaction of the shared call layer (wire.code / name / ready / connected / connect / drop send nothing)
WIRE = re.compile(r"\b(?:wire|hidl)\s*\.\s*(?!(?:code|name|ready|connected|connect|drop|request)\s*\()\w+\s*\(")
TABLE_REF = re.compile(r"\bsuper\s*\([^;]*?\b(\w+Codes)\.(\w+)\b")
DEVICE_CODE = re.compile(r"\b(?:\w*HalCodes|\w*Call)\.[A-Z][A-Z0-9_]*\b")


def strip_comments(s):
    s = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


def methods(src, ranges=None):
    """name -> list of bodies (overloads), top-level methods of the class only (brace depth 1).
    ranges (list) receives (first_line, last_line) of every method found."""
    out = {}
    lines = src.split("\n")
    depth = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        m = METHOD.match(line) if depth == 1 else None
        if m and m.group(1) not in ("if", "for", "while", "switch", "catch", "synchronized"):
            body = []
            d = 0
            started = False
            while i < len(lines):
                body.append(lines[i])
                for ch in lines[i]:
                    if ch == "{":
                        d += 1
                        started = True
                    elif ch == "}":
                        d -= 1
                if started and d == 0:
                    break
                i += 1
            out.setdefault(m.group(1), []).append("\n".join(body))
            if ranges is not None:
                ranges.append((i - len(body) + 1, i))
            i += 1
            continue
        depth += line.count("{") - line.count("}")
        i += 1
    return out


def shared_file(root, hal_dir, rel):
    """A file of apps/core/lumen-gmpf: the app tree's copy (assembled), else the module's."""
    for base in (root, MODULE_SRC):
        p = os.path.join(base, rel)
        if os.path.isfile(p):
            return p
    return None


def code_table(hal_dir, client_src):
    """{OP: code expression} of the device table the client's constructor names (Z9xCodes.GMPF), {} if none."""
    m = TABLE_REF.search(client_src)
    if not m:
        return {}
    path = os.path.join(hal_dir, m.group(1) + ".java")
    if not os.path.isfile(path):
        return {}
    t = strip_comments(open(path, encoding="utf-8").read())
    body = re.search(r"\b%s\s*=\s*new\s+GmpfCodes\s*<[^>]*>\s*\(\s*\)(.*?);" % re.escape(m.group(2)), t, re.S)
    return dict(re.findall(r"\.\s*put\s*\(\s*([A-Z][A-Z0-9_]*)\s*,\s*([\w.]+)\s*\)", body.group(1))) if body else {}


def groups(root, hal_dir):
    """{client: [(name, path)]}: the client file, and for a client of the shared call layer its shared files."""
    out = {}
    for f in CLIENTS:
        paths = [(f, os.path.join(hal_dir, f))]
        src = open(paths[0][1], encoding="utf-8").read()
        if SHARED_CLIENT.search(src):
            for rel in SHARED.get(f, ()):
                p = shared_file(root, hal_dir, rel)
                if p is None:
                    raise SystemExit("check_hal_callers: %s extends the shared client but %s is missing" % (f, rel))
                paths.append((os.path.basename(rel), p))
        out[f] = paths
    return out


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    root = sys.argv[1]
    hal_dir = None
    others = []
    shared_dirs = {os.path.normpath(os.path.join(root, os.path.dirname(r))) for rels in SHARED.values() for r in rels
                   if not r.startswith("org/lumen/projector/hal/")}
    for d, _, files in os.walk(root):
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(d, f)
            if os.path.basename(d) == "hal" and f in CLIENTS:
                hal_dir = d
            elif os.path.basename(d) != "hal" and os.path.normpath(d) not in shared_dirs:
                others.append(p)
    if hal_dir is None:
        print("check_hal_callers: no hal/GmpfClient.java under " + root)
        return 2
    outside = "\n".join(strip_comments(open(p, encoding="utf-8").read()) for p in others)
    grp = groups(root, hal_dir)
    op_file = shared_file(root, hal_dir, GMPF_OP)
    ops = set(re.findall(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*\"", strip_comments(open(op_file, encoding="utf-8").read()))) \
        if op_file and any(len(v) > 1 for v in grp.values()) else set()

    meths = {}                          # (client, name) -> bodies
    shared = {}                         # (client, name) -> the shared files declaring it (one body for every client)
    unparsed = []
    for f in CLIENTS:
        for name, path in grp[f]:
            src = strip_comments(open(path, encoding="utf-8").read())
            ranges = []
            for n, bodies in methods(src, ranges).items():
                meths.setdefault((f, n), []).extend(bodies)
                if name != f:
                    shared.setdefault((f, n), set()).add(name)
            # every transact call site must sit inside a method this script understood
            for n, line in enumerate(src.split("\n")):
                if (TRANSACT.search(line) or WIRE.search(line)) and not any(a <= n <= b for a, b in ranges):
                    unparsed.append("%s:%d" % (name, n + 1))
    if unparsed:
        print("check_hal_callers: transact call outside a recognised method (fix the parser or the "
              "code layout): " + ", ".join(sorted(set(unparsed))))
        return 1
    banned = []
    exception_sites = {}
    gen = os.path.join(hal_dir, GENERATED)
    gen_consts = {}
    if os.path.isfile(gen):
        gen_consts = {"HalCodes." + n: int(v, 0) for n, v in CONST.findall(strip_comments(open(gen, encoding="utf-8").read()))}
    present = {}                        # client -> {OP: code or None} of its device table
    for f in CLIENTS:
        table = code_table(hal_dir, strip_comments(open(grp[f][0][1], encoding="utf-8").read())) if len(grp[f]) > 1 else {}
        present[f] = {op: gen_consts.get(c) for op, c in table.items()}
        for name, path in grp[f]:
            src = strip_comments(open(path, encoding="utf-8").read())
            consts = dict(gen_consts)
            consts.update({n: int(v, 0) for n, v in CONST.findall(src)})
            ranges = []
            bodies = methods(src, ranges)
            lines = src.split("\n")
            names = {}
            for mname, bs in bodies.items():
                for b in bs:
                    first = b.split("\n", 1)[0]
                    for a, z in ranges:
                        if lines[a] == first and a not in names:
                            names[a] = (mname, z, b)
                            break

            def value(arg):
                arg = arg.strip()
                return int(arg, 0) if re.match(r"^(0x[0-9a-fA-F]+|\d+)$", arg) else consts.get(arg)

            def site(n, code, args):
                """One transact site at line n sending code; args: the two arguments after the code, if any."""
                if code is None:
                    return
                if code in BANNED.get(f, {}):
                    banned.append("%s:%d sends %d (%s)" % (name, n + 1, code, BANNED[f][code]))
                exc = EXCEPTIONS.get(f, {}).get(code)
                if exc is not None:
                    meth, guard, rule = exc
                    at = [v for a, v in names.items() if a <= n <= v[1]]
                    body = at[0][2] if at else ""
                    tx = [m.start() for m in WIRE.finditer(body)] + [m.start() for m in TRANSACT.finditer(body)]
                    pre = body[:max(tx)] if tx else ""
                    if not at or at[0][0] != meth or guard not in pre:
                        banned.append("%s:%d sends %d outside %s after %s (EXCEPTION: %s)" % (name, n + 1, code, meth, guard, rule))
                    exception_sites.setdefault((f, code), []).append("%s:%d" % (name, n + 1))
                only = ONLY_ARGS.get(f, {}).get(code)
                if only is not None:
                    got = tuple(value(a) for a in args) if args and len(args) == 2 else None
                    if got not in only:
                        allowed = " or ".join("(%s)" % ", ".join(map(str, o)) for o in sorted(only))
                        banned.append("%s:%d sends %d with arguments other than %s" % (name, n + 1, code, allowed))

            for n, line in enumerate(lines):
                for arg in CODE_ARG.findall(line):
                    code = int(arg, 0) if arg[0].isdigit() else consts.get(arg)
                    m = re.search(r"\bcall\w*\s*\(\s*" + re.escape(arg) + r"\s*,\s*([^,)]+)\s*,\s*([^,)]+)\s*\)", line)
                    site(n, code, m.groups() if m else None)
                for m in re.finditer(r"\b(?:wire|hidl)\s*\.\s*\w+\s*\(\s*(\w*HalCodes\.[A-Z][A-Z0-9_]*|0x[0-9a-fA-F]+|\d+)\s*[,)]", line):
                    site(n, value(m.group(1)), None)
                for m in re.finditer(r"\b(?:key|has)\s*\(\s*([A-Z][A-Z0-9_]*)\s*\)(\s*,\s*([^,)]+)\s*,\s*([^,)]+)\s*\))?", line):
                    if m.group(1) in ops and m.group(1) in present[f]:
                        site(n, present[f][m.group(1)], (m.group(3), m.group(4)) if m.group(2) else None)
                for m in re.finditer(r"\b(?:key|has)\s*\(\s*has\s*\(\s*([A-Z][A-Z0-9_]*)\s*\)\s*\?\s*([A-Z][A-Z0-9_]*)\s*:\s*([A-Z][A-Z0-9_]*)", line):
                    for op in m.groups():
                        if op in present[f]:
                            site(n, present[f][op], None)
                for m in re.finditer(r"\b\w+WindowValue\s*\(\s*([A-Z][A-Z0-9_]*)\b", line):
                    if m.group(1) in present[f]:
                        site(n, present[f][m.group(1)], None)
    for (f, code), sites in sorted(exception_sites.items()):
        if len(set(sites)) > 1:
            banned.append("%s sends %d at %d sites (%s): one guarded site only"
                          % (f, code, len(set(sites)), ", ".join(sorted(set(sites)))))
    if banned:
        for b in banned:
            print("check_hal_callers: banned code: " + b)
        print("check_hal_callers: FAILED (%d banned code(s) in a transact site)." % len(banned))
        return 1

    def transacts(f, body):
        inner = body.split("{", 1)[1]
        if TRANSACT.search(inner):
            return True
        if not WIRE.search(inner):
            return False
        named = set(re.findall(r"\b([A-Z][A-Z0-9_]*)\b", inner)) & ops
        locals_of_key = set(re.findall(r"\b([a-z]\w*)\s*=\s*key\s*\(", inner))
        forwarded = set(re.findall(r"\b(?:wire|hidl)\s*\.\s*\w+\s*\(\s*([a-z]\w*)\s*[,)]", inner)) - locals_of_key
        return bool(named & set(present[f])) or DEVICE_CODE.search(inner) is not None \
            or re.search(r"\bkey\s*\(\s*[a-z]\w*\s*\)", inner) is not None or bool(forwarded)

    transact = {k for k, bodies in meths.items() if any(transacts(k[0], b) for b in bodies)}
    # the transports' own helpers are not whitelist entries
    transact = {k for k in transact if not re.match(r"call\w*$", k[1]) and k[1] not in ("request", "send", "need")}

    # a call (x.name(...)) or a method reference (x::name)
    used = {k for k in meths if re.search(r"(?:\.\s*" + re.escape(k[1]) + r"\s*\(|::\s*" + re.escape(k[1]) + r"\b)", outside)}
    changed = True
    while changed:                      # a hal method called by a used hal method is used too
        changed = False
        for k in list(meths):
            if k in used:
                continue
            if any(u[1] == k[1] and shared.get(u, set()) & shared.get(k, set()) for u in used):
                used.add(k)             # one shared body: used by any client is used
                changed = True
                continue
            pat = re.compile(r"(?<![\w.])" + re.escape(k[1]) + r"\s*\(|\b(?:this|super)\s*\.\s*" + re.escape(k[1]) + r"\s*\(")
            for u in used:
                if u == k or u[0] != k[0]:
                    continue
                if any(pat.search(b.split("{", 1)[1]) for b in meths[u]):
                    used.add(k)
                    changed = True
                    break
    bad = sorted(transact - used)
    if bad:
        for f, n in bad:
            print("check_hal_callers: %s.%s transacts but has no caller outside hal/" % (f[:-5], n))
        print("check_hal_callers: FAILED (%d). Park unused HAL methods in a comment block." % len(bad))
        return 1
    print("check_hal_callers: OK (%d transacting methods, all with a caller outside hal/)" % len(transact))
    return 0


if __name__ == "__main__":
    sys.exit(main())
