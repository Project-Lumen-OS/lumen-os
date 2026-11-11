#!/usr/bin/env python3
"""check_aidl_hal.py: build-time guard for an app that talks to a stable AIDL gmpf HAL through one hand-written
transport. Run through tools/devices/check_hal.py for devices with hal_check=aidl-table.

  check_aidl_hal.py --app APP --codes CODES_TSV [--codes CODES_TSV ...] --banned BANNED_TSV --allowed ALLOWED_TSV
                    [--module MODULE] [--unbanned UNBANNED_TSV] [--dex DEX]

--module MODULE: the transport is the shared source module MODULE (apps/core/lumen-hal: org.lumen.hal.AidlTransport, as
the app's recipe assembles it): its src/ is checked together with APP/src. Then the transport file (rule 1) holds the
one transact site, whose code is '<c>.code()' of a parameter typed with the type variable C, and the allow list (rules
2 to 6) is the one app file that declares 'enum Iface implements AidlTransport.Service' and 'enum Call implements
AidlTransport.Method' and extends AidlTransport<...Call>; that file may name Parcel (the argument lambdas), never
IBinder or ServiceManager, and no other file implements AidlTransport.Method.

Fail-closed: every option but --dex is required, and a call that no rule names is refused. Source-level check
over APP/src (javac does not shrink and d8 runs without R8, so every compiled call is in the dex). Comments and
string contents are masked first (tools/play6/javascan.py), descriptors are read from the unmasked text at the
same place.
1. Exactly one file contains '.transact(' and it holds exactly one such site; its code argument is '<c>.code'
   where <c> is a Call parameter of the enclosing method. ServiceManager (by name or by reflection) only in that
   file. Outside it, none of IBinder, Parcel, HwBinder, HwParcel, BinderProxy, getMethod(, getDeclaredMethod(,
   Class.forName(, .invoke( (the ways around the transport).
2. That file declares 'enum Iface { NAME("<pkg>.IXxx"), ... }' and 'enum Call { NAME(Iface.X | null, CODE,
   "method"), ... }'. Service = the descriptor's last segment without the leading I.
3. Every Call with an interface is a row of CODES_TSV (service, code, method; '#' lines skipped) with the same
   service, code and method. A Call with null is a meta call: getInterfaceVersion 0x00FFFFFF or
   getInterfaceHash 0x00FFFFFE, nothing else. With several --codes (one table per vendor firmware the app accepts,
   e.g. the HB V6.16.24 and V6.17.12 tables), every Call must be a row of EVERY table and that row must be equal in
   all of them (service, code, method, signature and every further column, e.g. the NDK proxy arguments): a method
   whose code or signature changed between firmwares (HB FOCUS 102 doProImgFusionOverlap) can never be sent.
4. No Call matches a BANNED or REVIEW row of BANNED_TSV (service, code, method, signature, status) by
   (service, code) or by (service, method): a call still under review counts as refused.
5. Every call of a transport method (a method of that file with a Call parameter) passes Call.X, a declared
   constant, or a Call parameter of its own method: no integer code anywhere.
6. The Call list equals ALLOWED_TSV (service, code, method, signature, [args:SPEC,] reason; '-' as service for a meta
   call): every Call is a row, every row is a Call.
8. Argument ranges (t20): a Call may carry a fourth enum argument, its argument spec (org.lumen.hal.ArgSpec grammar:
   alternatives ';', words ',', INT | INT..INT | f:FLOAT | '-' for no word), which the shared transport enforces on
   the device before the transact. The spec must be well formed and equal the row's 'args:' column (a row with the
   column needs the spec, a Call with a spec needs the column).
--unbanned UNBANNED_TSV (rows: app, service, code, method, args:SPEC, reason): the maintainer's per-app exceptions to rule 4.
   A BANNED row (never a REVIEW row) is accepted only for a Call of the app named in the row (the basename of --app must
   equal it, and every row must name that one app: a list for another app fails as a whole) whose service, code and
   method equal the row and whose spec (mandatory) equals the row's; every row must be such a Call (exact list), must
   be BANNED in BANNED_TSV and must also be a row of ALLOWED_TSV with the same args column. tools/devices/check_hal.py
   passes it only to the app its device.conf names in hal_unbanned.
7. With --dex (classes.dex or the APK, read with dexdump: $DEXDUMP, else $BUILD_TOOLS/dexdump, else
   $ANDROID_SDK/build-tools/36.0.0/dexdump), the compiled code: exactly one invoke of Landroid/os/IBinder;.transact,
   inside the transport method of rule 1; every other invoke of a method named transact targets the transport class
   from inside it; no invoke of Ljava/lang/reflect/Method;.invoke; ServiceManager invoked only from the transport
   class (and its inner classes); no const-string containing "transact" or "ServiceManager" outside it.
Output 'OK: N calls on M interfaces', exit 0; otherwise one line per violation, exit 1.
"""
import os
import re
import subprocess
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "play6"))
import javascan  # noqa: E402

META = {"getInterfaceVersion": 0x00FFFFFF, "getInterfaceHash": 0x00FFFFFE}
METHOD = re.compile(r"\b(\w+)\s*\(([^()]*)\)\s*(?:throws\s+[\w.,\s]+)?\{")
KEYWORDS = {"if", "for", "while", "switch", "catch", "synchronized", "return", "new"}
# outside the transport file: the Binder types and the reflection that would reach them without the transport
BYPASS = ((r"\bIBinder\b", "IBinder"), (r"\bParcel\b", "Parcel"), (r"\bHwBinder\b", "HwBinder"),
          (r"\bHwParcel\b", "HwParcel"), (r"\bBinderProxy\b", "BinderProxy"), (r"\bgetMethod\s*\(", "getMethod("),
          (r"\bgetDeclaredMethod\s*\(", "getDeclaredMethod("), (r"\bClass\s*\.\s*forName\s*\(", "Class.forName("),
          (r"\.\s*invoke\s*\(", ".invoke("))
DEX_METHOD = re.compile(r"\|\[[0-9a-f]+\] ([\w.$]+)\.([\w$<>]+):\(")
DEX_INVOKE = re.compile(r"\|[0-9a-f]+: invoke-[\w/-]+ \{[^}]*\}, (L[\w/$]+;)\.([\w$<>]+):")
DEX_STRING = re.compile(r'\|[0-9a-f]+: const-string(?:/jumbo)? v\d+, "(.*)" // string@')


def tsv(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        rows.append(line.rstrip("\n").split("\t"))
    return rows


def enum_body(raw, masked, name):
    m = re.search(r"\benum\s+%s\s*(?:implements\s+[\w.]+\s*)?\{" % name, masked)
    if not m:
        return None
    end = javascan.matching(masked, m.end() - 1)
    body_m = masked[m.end():end]
    semi = body_m.find(";")
    stop = m.end() + (semi if semi >= 0 else len(body_m))
    return raw[m.end():stop], masked[m.end():stop]


def methods(masked):
    """[(start of body, end of body, name, parameter text)] of every method-like block."""
    out = []
    for m in METHOD.finditer(masked):
        if m.group(1) in KEYWORDS:
            continue
        out.append((m.end() - 1, javascan.matching(masked, m.end() - 1), m.group(1), m.group(2)))
    return out


def enclosing(meths, pos):
    inside = [x for x in meths if x[0] < pos < x[1]]
    return min(inside, key=lambda x: x[1] - x[0]) if inside else None


CALL_TYPES = {"Call"}


def call_params(params):
    return {p.split()[-1] for p in params.split(",") if p.strip() and p.split()[0] in CALL_TYPES}


SPEC_WORD = re.compile(r"^(?:f:[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?|-?\d+(?:\.\.-?\d+)?)$")


def spec_error(spec):
    """None when spec follows the ArgSpec grammar (and every INT..INT has min <= max), else why not."""
    if not spec:
        return "empty argument spec"
    for alt in spec.split(";"):
        if alt == "-":
            continue
        if not alt:
            return "empty alternative in %r" % spec
        for w in alt.split(","):
            if not SPEC_WORD.match(w):
                return "bad word %r in %r" % (w, spec)
            if ".." in w and not w.startswith("f:"):
                a, b = w.split("..")
                if int(a) > int(b) or not (-2**31 <= int(a) and int(b) < 2**31):
                    return "bad range %r in %r" % (w, spec)
            elif not w.startswith("f:") and not (-2**31 <= int(w) < 2**31):
                return "bad int %r in %r" % (w, spec)
    return None


def row_args(r):
    """The args:SPEC column of an allow / unban row (index 4), or None."""
    return r[4][len("args:"):] if len(r) >= 6 and r[4].startswith("args:") else None


def check(app, codes, banned, allowed, module=None, unbanned=None):
    """codes: one code table path or a list of them (the first one names the rows in messages)."""
    codes_list = [codes] if isinstance(codes, str) else list(codes)
    codes = codes_list[0]
    errs = []
    src = os.path.join(app, "src")
    files = {}
    roots = [app] + ([module] if module else [])
    for root in roots:
        for d, dirs, names in os.walk(os.path.join(root, "src")):
            dirs.sort()
            for n in sorted(names):
                if n.endswith(".java"):
                    p = os.path.join(d, n)
                    raw = open(p, encoding="utf-8").read()
                    rel = os.path.relpath(p, root)
                    if rel in files:
                        errs.append("rule 1: %s is in the app and in the module" % rel)
                    files[rel] = (raw, javascan.mask(raw))
    if not files:
        return ["no java files under %s" % src], 0, 0, None

    # 1. one transport file, one transact site, its code from a Call parameter
    sites = [(f, m.start()) for f, (_, mk) in files.items() for m in re.finditer(r"\.\s*transact\s*\(", mk)]
    tfiles = sorted({f for f, _ in sites})
    if len(tfiles) != 1:
        errs.append("rule 1: '.transact(' must be in exactly one file, found in %s" % (tfiles or "none"))
    if len(sites) != 1:
        errs.append("rule 1: exactly one '.transact(' site expected, found %d: %s"
                    % (len(sites), ", ".join("%s:%d" % (f, files[f][1].count("\n", 0, i) + 1) for f, i in sites)))
    if not tfiles:
        return errs, 0, 0, None
    tf = tfiles[0]
    raw, mk = files[tf]
    # the module layout (--module, or an assembled tree that holds apps/core/lumen-hal): the transport is the shared
    # AidlTransport, the allow list another file
    module = module or tf.replace(os.sep, "/").endswith("src/org/lumen/hal/AidlTransport.java")
    CALL_TYPES.clear()
    CALL_TYPES.update({"Call", "C"} if module else {"Call"})
    meths = methods(mk)
    for f, i in sites:
        if f != tf:
            continue
        line = mk.count("\n", 0, i) + 1
        args = mk[mk.index("(", i) + 1:javascan.matching(mk, mk.index("(", i), "(", ")")]
        first = javascan.split_top(args)[0].strip() if args.strip() else ""
        em = enclosing(meths, i)
        cm = re.match(r"^(\w+)\.code\(\)$" if module else r"^(\w+)\.code$", first)
        if not cm or not em or cm.group(1) not in call_params(em[3]):
            errs.append("rule 1: %s:%d: the transact code must be <Call parameter>.code, found %r" % (f, line, first))
    # module layout: the allow list is the one app file with the two enums that extends the transport
    af = tf
    if module:
        cands = [f for f, (r, m) in files.items() if f != tf and re.search(r"\benum\s+Call\s+implements\s+AidlTransport\.Method\b", m)]
        if len(cands) != 1:
            errs.append("rule 2: exactly one file must declare 'enum Call implements AidlTransport.Method', found %s" % (cands or "none"))
            return errs, 0, 0, None
        af = cands[0]
        if not re.search(r"\bextends\s+AidlTransport\s*<\s*\w+\.Call\s*>", files[af][1]):
            errs.append("rule 2: %s does not extend AidlTransport<...Call>" % af)
        for f, (r, m) in files.items():
            if f != af and re.search(r"\bimplements\b[^{]*\bMethod\b", m) and f != tf:
                errs.append("rule 2: %s implements AidlTransport.Method outside the allow list %s" % (f, af))
    for f, (r, m) in files.items():
        if f != tf and (re.search(r"\bServiceManager\b", m) or "android.os.ServiceManager" in r):
            errs.append("rule 1: %s: ServiceManager outside the transport file %s" % (f, tf))
        if f != tf:
            for rx, what in BYPASS:
                if f == af and what == "Parcel":
                    continue   # the allow list writes the arguments and reads the reply (no Binder without IBinder)
                for bm in re.finditer(rx, m):
                    errs.append("rule 1: %s:%d: %s outside the transport file %s"
                                % (f, m.count("\n", 0, bm.start()) + 1, what, tf))
    tsite = [i for f, i in sites if f == tf]
    tmeth = enclosing(meths, tsite[0]) if tsite else None
    tport = (tf, tmeth[2] if tmeth else None)

    # 2. the two enums
    araw, amk = files[af]
    ib, cb = enum_body(araw, amk, "Iface"), enum_body(araw, amk, "Call")
    if not ib or not cb:
        errs.append("rule 2: %s: enum Iface and enum Call expected" % af)
        return errs, 0, 0, tport
    ifaces = {}
    for m in re.finditer(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*\"([\w.$]+)\"\s*\)", ib[0]):
        ifaces[m.group(1)] = m.group(2).replace("$", ".").split(".")[-1]
    ifaces = {k: (v[1:] if v.startswith("I") else v) for k, v in ifaces.items()}
    calls = {}
    specs = {}
    for m in re.finditer(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*(null|Iface\.([A-Z][A-Z0-9_]*))\s*,\s*(0[xX][0-9a-fA-F]+|\d+)\s*,"
                         r"\s*\"(\w+)\"\s*(?:,\s*\"([^\"\\]*)\"\s*)?\)", cb[0]):
        calls[m.group(1)] = (m.group(3), int(m.group(4), 0), m.group(5))
        if m.group(6) is not None:
            specs[m.group(1)] = m.group(6)
    declared = len(javascan.split_top(cb[1].strip().rstrip(","))) if cb[1].strip() else 0
    if declared != len(calls):
        errs.append("rule 2: %s: enum Call has %d entries, %d in the form NAME(Iface.X | null, CODE, \"method\"[, \"args\"])"
                    % (af, declared, len(calls)))

    # 3. every call is a row of the code table; 4. none is banned
    full = []
    for path in codes_list:
        full.append({(r[0], int(r[1], 0)): r for r in tsv(path) if len(r) >= 3 and re.match(r"^(0x[0-9a-fA-F]+|\d+)$", r[1])})
    table = {k: r[2] for k, r in full[0].items()}
    ban = [(r[0], int(r[1], 0), r[2], r[4]) for r in tsv(banned) if len(r) >= 5 and r[4] in ("BANNED", "REVIEW")]
    allow = set()
    allow_args = {}
    for r in tsv(allowed):
        if len(r) < 3 or not re.match(r"^(0x[0-9a-fA-F]+|\d+)$", r[1]):
            errs.append("rule 6: %s: bad row %r" % (os.path.basename(allowed), "\t".join(r)))
            continue
        k = (None if r[0] == "-" else r[0], int(r[1], 0), r[2])
        allow.add(k)
        if row_args(r) is not None:
            allow_args[k] = row_args(r)
    # the maintainer's per-app exceptions to rule 4 (--unbanned)
    unban = {}
    if unbanned:
        me = os.path.basename(os.path.normpath(app))
        ban_rows = {(r[0], int(r[1], 0), r[2]): r[4] for r in tsv(banned) if len(r) >= 5 and re.match(r"^\d+$", r[1])}
        for r in tsv(unbanned):
            if len(r) < 6 or not re.match(r"^\d+$", r[2]) or not r[4].startswith("args:"):
                errs.append("unbanned: %s: bad row %r (app, service, code, method, args:SPEC, reason)"
                            % (os.path.basename(unbanned), "\t".join(r)))
                continue
            if os.path.basename(r[0].rstrip("/")) != me:
                errs.append("unbanned: %s is for %s, not for %s: nothing of it applies"
                            % (os.path.basename(unbanned), r[0], me))
                unban = None
                break
            k = (r[1], int(r[2]), r[3])
            if ban_rows.get(k) != "BANNED":
                errs.append("unbanned: %s row %s %d %s is no BANNED row of %s (status %s)"
                            % (os.path.basename(unbanned), k[0], k[1], k[2], os.path.basename(banned), ban_rows.get(k)))
            sp = r[4][len("args:"):]
            if spec_error(sp):
                errs.append("unbanned: %s row %s %d %s: %s" % (os.path.basename(unbanned), k[0], k[1], k[2], spec_error(sp)))
            if allow_args.get(k) != sp:
                errs.append("unbanned: %s row %s %d %s args %r is not the allow list's args %r"
                            % (os.path.basename(unbanned), k[0], k[1], k[2], sp, allow_args.get(k)))
            unban[k] = sp
        unban = unban or {}
    used = set()
    used_unban = set()
    for name, (iface, code, method) in sorted(calls.items()):
        key = (None if iface is None else ifaces.get(iface), code, method)
        used.add(key)
        if (iface is None or key[0] is not None) and key not in allow:
            errs.append("rule 6: Call.%s: %s %d %s is not a row of %s"
                        % (name, key[0] or "-", code, method, os.path.basename(allowed)))
        if iface is None:
            if META.get(method) != code:
                errs.append("rule 3: Call.%s: null interface is only for getInterfaceVersion 0x00FFFFFF and "
                            "getInterfaceHash 0x00FFFFFE, not %s %d" % (name, method, code))
            continue
        svc = ifaces.get(iface)
        if svc is None:
            errs.append("rule 2: Call.%s: Iface.%s is not declared" % (name, iface))
            continue
        if table.get((svc, code)) != method:
            errs.append("rule 3: Call.%s: %s %d %s is not a row of %s (the table has %r)"
                        % (name, svc, code, method, os.path.basename(codes), table.get((svc, code))))
        for path, other in zip(codes_list[1:], full[1:]):
            mine, theirs = full[0].get((svc, code)), other.get((svc, code))
            if theirs is None or theirs[2] != method:
                errs.append("rule 3: Call.%s: %s %d %s is not a row of %s (the table has %r)"
                            % (name, svc, code, method, os.path.basename(path), theirs and theirs[2]))
            elif mine is not None and mine != theirs:
                errs.append("rule 3: Call.%s: %s %d %s differs between %s and %s (%s / %s): never sent"
                            % (name, svc, code, method, os.path.basename(codes), os.path.basename(path),
                               " | ".join(mine[3:]), " | ".join(theirs[3:])))
        for s, c, mth, st in ban:
            if s == svc and (c == code or mth == method):
                ub = unban.get((svc, code, method))
                if st == "BANNED" and (s, c, mth) == (svc, code, method) and ub is not None and specs.get(name) == ub:
                    used_unban.add((svc, code, method))
                    continue   # the maintainer's exception for this app, with its argument range (rule 8 holds it equal)
                errs.append("rule 4: Call.%s: %s %d %s is %s (%s %d %s)%s"
                            % (name, svc, code, method, "banned" if st == "BANNED" else "under review", s, c, mth,
                               "" if ub is None else ": unbanned only with args %r, the Call has %r" % (ub, specs.get(name))))
    for s, c, mth in sorted(allow - used, key=str):
        errs.append("rule 6: %s row %s %d %s is no Call (the list must be exact)" % (os.path.basename(allowed), s or "-", c, mth))
    for k in sorted(set(unban) - used_unban, key=str):
        errs.append("unbanned: %s row %s %d %s is no Call of the app with its args (the list must be exact)"
                    % (os.path.basename(unbanned), k[0], k[1], k[2]))

    # 8. argument ranges: the Call's spec equals the row's args column, both well formed
    for name, (iface, code, method) in sorted(calls.items()):
        key = (None if iface is None else ifaces.get(iface), code, method)
        sp, col = specs.get(name), allow_args.get(key)
        if sp is not None and spec_error(sp):
            errs.append("rule 8: Call.%s: %s" % (name, spec_error(sp)))
        if col is not None and spec_error(col):
            errs.append("rule 8: %s row %s %d %s: %s" % (os.path.basename(allowed), key[0] or "-", code, method, spec_error(col)))
        if sp != col:
            errs.append("rule 8: Call.%s: args %r, the %s row has %r" % (name, sp, os.path.basename(allowed), col))

    # 5. the transport methods are only called with a Call constant or a Call parameter (module layout: in the
    # transport and the allow list only, the transport's methods are private or protected; also its final C fields,
    # the two meta calls the constructor checked)
    transport = {x[2]: x for x in meths if call_params(x[3])}
    cfields = set(re.findall(r"\bprivate\s+final\s+C\s+(\w+)\s*,\s*(\w+)\s*;", mk)[0]) if module and \
        re.search(r"\bprivate\s+final\s+C\s+(\w+)\s*,\s*(\w+)\s*;", mk) else set()
    tclass = os.path.splitext(os.path.basename(tf))[0] if tf else None
    for f, (r, m) in files.items():
        if module and f not in (tf, af):
            continue
        fm = methods(m)
        # a class outside the transport's hierarchy that declares its own method of a transport name (the shared
        # menu's QpdMirror: call(method, arg) on a content provider) calls its own method there, never the transport
        # (private or protected); a subclass of the transport is always checked, its own overload included
        sub = tclass is not None and re.search(r"\bextends\s+%s\b" % re.escape(tclass), m)
        local = set() if f == tf or sub else {x[2] for x in fm}
        for name in transport:
            if name in local:
                continue
            for cm in re.finditer(r"(?<![\w.])%s\s*\(" % re.escape(name), m):
                op = cm.end() - 1
                own = enclosing(fm, op)
                if own is None:
                    continue   # the declaration itself
                args = [a.strip() for a in javascan.split_top(m[op + 1:javascan.matching(m, op, "(", ")")])]
                okc = [a for a in args if re.match(r"^Call\.([A-Z][A-Z0-9_]*)$", a) and a[5:] in calls] \
                    + [a for a in args if a in call_params(own[3]) or (f == tf and a in cfields)]
                if not okc:
                    errs.append("rule 5: %s:%d: %s(%s) passes no Call constant or Call parameter"
                                % (f, m.count("\n", 0, op) + 1, name, ", ".join(args)))
    return errs, len(calls), len(ifaces), tport


def dexdump_tool():
    if os.environ.get("DEXDUMP"):
        return os.environ["DEXDUMP"]
    bt = os.environ.get("BUILD_TOOLS") or os.path.join(
        os.environ.get("ANDROID_SDK", os.path.expanduser("~/Library/Android/sdk")), "build-tools", "36.0.0")
    return os.path.join(bt, "dexdump")


def check_dex(dex, transport):
    """Rule 7 over the dexdump of DEX; transport = (source file of the transport, method holding the transact)."""
    if not transport or not transport[1]:
        return ["rule 7: no transport method from the source check, the dex cannot be checked"]
    rel = transport[0][len("src/"):] if transport[0].startswith("src/") else transport[0]
    tcls = "L%s;" % rel[:-len(".java")].replace(os.sep, "/")
    tdot = tcls[1:-1].replace("/", ".")
    try:
        # dexdump prints string constants as they are (not always valid UTF-8): undecodable bytes become U+FFFD,
        # which no rule pattern contains
        out = subprocess.run([dexdump_tool(), "-d", dex], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             universal_newlines=True, encoding="utf-8", errors="replace")
    except OSError as e:
        return ["rule 7: dexdump not run: %s" % e]
    if out.returncode != 0 or "Class descriptor" not in out.stdout:
        return ["rule 7: dexdump failed on %s (exit %d): %s" % (dex, out.returncode, out.stderr.strip()[:200])]
    errs, binder, cur = [], 0, None
    for line in out.stdout.splitlines():
        mm = DEX_METHOD.search(line)
        if mm:
            cur = (mm.group(1), mm.group(2))
            continue
        if cur is None:
            continue
        inside = cur[0] == tdot or cur[0].startswith(tdot + "$")
        where = "%s.%s" % cur
        im = DEX_INVOKE.search(line)
        if im:
            cls, name = im.group(1), im.group(2)
            if name == "transact":
                if cls == "Landroid/os/IBinder;" and cur == (tdot, transport[1]):
                    binder += 1
                elif not (cls == tcls and inside):
                    errs.append("rule 7: %s invokes %s.transact" % (where, cls))
            if cls == "Ljava/lang/reflect/Method;" and name == "invoke":
                errs.append("rule 7: %s invokes java.lang.reflect.Method.invoke" % where)
            if cls == "Landroid/os/ServiceManager;" and not inside:
                errs.append("rule 7: %s invokes ServiceManager.%s outside %s" % (where, name, tdot))
            continue
        sm = DEX_STRING.search(line)
        if sm and not inside and ("transact" in sm.group(1) or "ServiceManager" in sm.group(1)):
            errs.append("rule 7: %s: const-string %r outside %s" % (where, sm.group(1), tdot))
    if binder != 1:
        errs.append("rule 7: %d invokes of Landroid/os/IBinder;.transact in %s.%s, exactly 1 expected"
                    % (binder, tdot, transport[1]))
    return errs


def main(argv):
    pairs = list(zip(argv[::2], argv[1::2])) if len(argv) % 2 == 0 else None
    need = {"--app", "--codes", "--banned", "--allowed"}
    opts = dict(pairs or [])
    names = [k for k, _ in pairs or []]
    if pairs is None or not need <= set(opts) or not set(opts) <= need | {"--dex", "--module", "--unbanned"} \
            or any(names.count(k) > 1 for k in set(names) - {"--codes"}):
        print(__doc__)
        return 2
    codes = [v for k, v in pairs if k == "--codes"]
    errs, n, m, transport = check(opts["--app"], codes, opts["--banned"], opts["--allowed"], opts.get("--module"),
                                  opts.get("--unbanned"))
    if "--dex" in opts:
        errs += check_dex(opts["--dex"], transport)
    for e in errs:
        print("check_aidl_hal: " + e)
    if errs:
        print("check_aidl_hal: FAILED")
        return 1
    print("check_aidl_hal: OK: %d calls on %d interfaces" % (n, m))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
