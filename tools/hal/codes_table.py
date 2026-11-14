#!/usr/bin/env python3
"""codes_table.py: the per-device HAL code tables devices/<codename>/hal/codes.tsv (library of tools/hal/gen_codes.py
and tools/hal/check_codes.py, no main).

A table holds every HAL code the Lumen apps of one device send, one named constant per row:

  java<TAB>APP<TAB>PATH[<TAB>public]     the generated class of APP (package from PATH after src/, class = file name;
                                         package-private unless 'public': only for an app whose other packages
                                         name the codes, e.g. a read schedule)
  stock<TAB>INTERFACE|*<TAB>TABLE[<TAB>PROXY]   the stock code table the rows of INTERFACE (* = every AIDL service) are
                                         proven against; several lines for one interface = every table must agree
  # ...                                  comments
  INTERFACE NAME CODE METHOD ARGS KIND APPS SOURCE   (tab separated)

INTERFACE: AIDL service name of the stock table (DisplayManager, ...), '-' for the AIDL meta calls, or the HIDL
interface IGmpf / IGmpf2 / IBase. NAME = PREFIXES[INTERFACE] + '_' + the stock method in capitals, underscores only
for reading (NAME without its prefix and without '_' equals the method in capitals without '_'). CODE decimal or 0x hex.
KIND get / set / call / meta. APPS: comma list of apps that send it (each named on a java line). SOURCE: evidence.
"""
import os
import re

HEADER = "# interface\tNAME\tcode\tmethod\targs\tkind\tapps\tsource"
PREFIXES = {
    # stable AIDL xgimi.hardware.gmpf V1 (HB, X50): one prefix per vendor service
    "ConfigManager": "CM", "DataBaseManager": "DB", "DisplayManager": "DM", "GmAudioManager": "AM",
    "GmFactoryManager": "FM", "GmTvManager": "TV", "MotionDetectionManager": "MD", "MusicPlayerManager": "MP",
    "PowerManager": "PM", "ProjectorFocusManager": "PF", "SystemManager": "SM", "TemperatureManager": "TM",
    "Gmpf": "AGMPF", "Gmpf2": "AGMPF2", "-": "META",
    # HIDL xgimi.hardware.gmpf@1.0 (Z9X, Play 6) and the HIDL base interface
    "IGmpf": "GMPF", "IGmpf2": "GMPF2", "IBase": "IBASE",
}
HIDL = {"IGmpf", "IGmpf2", "IBase"}
KINDS = {"get", "set", "call", "meta"}
# AIDL meta transactions (android.os.IBinder, every stable AIDL interface)
AIDL_META = {0x00FFFFFF: ("getInterfaceVersion", "int()"), 0x00FFFFFE: ("getInterfaceHash", "String()")}
# HIDL reserved transactions of android.hidl.base@1.0::IBase ('\x0f' + three letters)
IBASE = {0x0f43484e: "interfaceChain", 0x0f444247: "debug", 0x0f445343: "interfaceDescriptor",
         0x0f485348: "getHashChain", 0x0f494e54: "setHALInstrumentation", 0x0f504e47: "ping",
         0x0f524546: "getDebugInfo", 0x0f535953: "notifySyspropsChanged"}
NAME_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
INT_RE = re.compile(r"^(0x[0-9a-fA-F]+|\d+)$")


class TableError(Exception):
    pass


def tables(root):
    """[(codename, path)] of every devices/<codename>/hal/codes.tsv, sorted."""
    out = []
    d = os.path.join(root, "devices")
    for c in sorted(os.listdir(d)):
        p = os.path.join(d, c, "hal", "codes.tsv")
        if os.path.isfile(p):
            out.append((c, p))
    return out


def read(path):
    """{'java': [(app, rel path)], 'stock': [(iface, rel table, proxy)], 'rows': [dict]} or TableError."""
    java, stock, rows, errs, public = [], [], [], [], set()
    seen_header = False
    for n, line in enumerate(open(path, encoding="utf-8").read().split("\n"), 1):
        if not line:
            continue
        if line == HEADER:
            seen_header = True
            continue
        if line.startswith("#"):
            continue
        f = line.split("\t")
        if f[0] == "java":
            if len(f) not in (3, 4) or not f[2].endswith(".java") or "/src/" not in f[2] or f[3:] not in ([], ["public"]):
                errs.append("%s:%d: java line needs APP and PATH .../src/<package>/<Class>.java [public]" % (path, n))
            else:
                java.append((f[1], f[2]))
                if f[3:] == ["public"]:
                    public.add(f[2])
            continue
        if f[0] == "stock":
            if len(f) not in (3, 4):
                errs.append("%s:%d: stock line needs INTERFACE|* and TABLE [PROXY]" % (path, n))
            else:
                stock.append((f[1], f[2], f[3] if len(f) == 4 else ""))
            continue
        if not seen_header:
            errs.append("%s:%d: a row before the header line %r" % (path, n, HEADER))
            continue
        if len(f) != 8:
            errs.append("%s:%d: %d columns, 8 expected (interface NAME code method args kind apps source)" % (path, n, len(f)))
            continue
        iface, name, code, method, args, kind, apps, source = f
        if not INT_RE.match(code):
            errs.append("%s:%d: code %r is not an integer" % (path, n, code))
            continue
        rows.append({"line": n, "iface": iface, "name": name, "code": int(code, 0), "code_text": code,
                     "method": method, "args": args, "kind": kind, "apps": apps.split(","), "source": source})
    if not seen_header:
        errs.append("%s: no header line %r" % (path, HEADER))
    if errs:
        raise TableError("\n".join(errs))
    return {"java": java, "stock": stock, "rows": rows, "public": public}


def name_ok(iface, name, method):
    pre = PREFIXES.get(iface)
    return (pre is not None and name.startswith(pre + "_") and NAME_RE.match(name) is not None
            and name[len(pre) + 1:].replace("_", "") == method.upper().replace("_", ""))


def _stock_table(root, rel, proxy):
    """{code: (method, args)} of a HIDL proxy in a z9x_gmpf_codes.txt / gmpf_codes_play6.tsv style file, or
    {(service, code): (method, signature)} of an AIDL aidl_codes.tsv."""
    p = os.path.join(root, rel)
    if not os.path.isfile(p):
        raise TableError("stock table %s is missing (research/ is a symlink to the main checkout's research folder)" % rel)
    out = {}
    for line in open(p, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        if proxy:
            f = line.rstrip("\n").split("\t") if rel.endswith(".tsv") else line.rstrip("\n").split(None, 2)
            if len(f) < 3 or f[0] != proxy or not f[1].isdigit():
                continue
            m = re.match(r"_hidl_(\w+)\(", f[2])
            out[int(f[1])] = (m.group(1) if m else f[2], "")
        else:
            f = line.rstrip("\n").split("\t")
            if len(f) >= 4 and INT_RE.match(f[1]):
                out[(f[0], int(f[1], 0))] = (f[2], f[3])
    return out


def validate(root, codename, table):
    """List of problems of one parsed table: row rules, unique names and codes, java lines, stock proof."""
    errs = []
    rel = "devices/%s/hal/codes.tsv" % codename
    apps = {a for a, _ in table["java"]}
    if len(apps) != len(table["java"]):
        errs.append("%s: an app has two java lines" % rel)
    names, keys = {}, {}
    stocks = {}
    for iface, t, proxy in table["stock"]:
        try:
            stocks.setdefault(iface, []).append((t, _stock_table(root, t, proxy)))
        except TableError as e:
            errs.append("%s: %s" % (rel, e))
    for r in table["rows"]:
        where = "%s:%d %s" % (rel, r["line"], r["name"])
        if r["iface"] not in PREFIXES:
            errs.append("%s: unknown interface %r" % (where, r["iface"]))
            continue
        if not name_ok(r["iface"], r["name"], r["method"]):
            errs.append("%s: the name must be %s_ + %s in capitals (underscores only for reading)"
                        % (where, PREFIXES[r["iface"]], r["method"]))
        if r["name"] in names:
            errs.append("%s: name used twice (line %d)" % (where, names[r["name"]]))
        names[r["name"]] = r["line"]
        k = (r["iface"], r["code"])
        if k in keys:
            errs.append("%s: %s %d is already line %d" % (where, r["iface"], r["code"], keys[k]))
        keys[k] = r["line"]
        if r["kind"] not in KINDS or (r["kind"] == "meta") != (r["iface"] == "-"):
            errs.append("%s: kind %r (get / set / call; meta only for interface -)" % (where, r["kind"]))
        for a in r["apps"]:
            if a not in apps:
                errs.append("%s: app %r has no java line" % (where, a))
        if not r["source"].strip():
            errs.append("%s: no source evidence" % where)
        # the stock proof
        if r["iface"] == "-":
            if AIDL_META.get(r["code"], (None,))[0] != r["method"]:
                errs.append("%s: AIDL meta code 0x%08X is not %s" % (where, r["code"], r["method"]))
            continue
        if r["iface"] == "IBase":
            if IBASE.get(r["code"]) != r["method"]:
                errs.append("%s: IBase code 0x%08x is not %s (it is %s)" % (where, r["code"], r["method"], IBASE.get(r["code"])))
            continue
        cands = stocks.get(r["iface"]) or stocks.get("*") or []
        if not cands:
            errs.append("%s: no stock line for interface %s" % (where, r["iface"]))
        for t, st in cands:
            got = st.get(r["code"]) if r["iface"] in HIDL else st.get((r["iface"], r["code"]))
            if got is None or got[0] != r["method"]:
                errs.append("%s: %s %d is %r in %s, not %s" % (where, r["iface"], r["code"], got and got[0], t, r["method"]))
            elif r["iface"] not in HIDL and got[1] != r["args"]:
                errs.append("%s: signature %r, %s has %r" % (where, r["args"], t, got[1]))
    return errs


def render(codename, table, app, path):
    """The Java source of the HalCodes class of app (rows listing it), deterministic."""
    pkg = os.path.dirname(path.split("/src/", 1)[1]).replace("/", ".")
    cls = os.path.basename(path)[:-len(".java")]
    rows = [r for r in table["rows"] if app in r["apps"]]
    out = ["/*",
           " * GENERATED by tools/hal/gen_codes.py from devices/%s/hal/codes.tsv: do not edit. Change the table, then run" % codename,
           " * python3 tools/hal/gen_codes.py --write.",
           " * The HAL transaction codes %s sends: one constant per call, named by the interface and the stock method." % app,
           " * Each value is the stock code, proven against the stock table by tools/hal/check_codes.py; if a call does not",
           " * work, the table row of its constant is the place to look. No HAL call of this app takes a number of its own.",
           " */",
           "package %s;" % pkg,
           "",
           "%sfinal class %s {" % ("public " if path in table.get("public", ()) else "", cls),
           "    private %s() {}" % cls]
    last = None
    width = max([len(r["name"]) + len(r["code_text"]) for r in rows] or [0])
    for r in rows:
        if r["iface"] != last:
            out.append("")
            out.append("    // %s" % ({"-": "AIDL meta calls (every interface)", "IBase": "android.hidl.base@1.0::IBase"}
                                       .get(r["iface"], r["iface"])))
            last = r["iface"]
        decl = "    %sstatic final int %s = %s;" % ("public " if path in table.get("public", ()) else "", r["name"], r["code_text"])
        pad = " " * (width - len(r["name"]) - len(r["code_text"]) + 1)
        args = "" if r["args"] in ("", "-") else "  " + r["args"]
        out.append("%s%s// %s%s" % (decl, pad, r["method"], args))
    out.append("}")
    return "\n".join(out) + "\n"


def generated(root):
    """[(codename, app, abs path, java text)] of every java line of every table (TableError on a bad table)."""
    out = []
    for c, p in tables(root):
        t = read(p)
        for app, rel in t["java"]:
            out.append((c, app, os.path.join(root, rel), render(c, t, app, rel)))
    return out
