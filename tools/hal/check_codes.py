#!/usr/bin/env python3
"""check_codes.py: no HAL call of any Lumen app carries a number of its own; every code is a named constant of the
generated HalCodes class of that app, and every table those classes come from is proven against the stock tables.

  check_codes.py [--root DIR] [--tables | --sources]     (both parts by default)

Tables (devices/<codename>/hal/codes.tsv, tools/hal/codes_table.py): every row well formed, its name the interface
prefix + the stock method, unique names and codes, the row equal to its stock table (interface, code, method; AIDL also
the signature; several stock tables of one interface must all agree), and every generated class byte-equal to what
tools/hal/gen_codes.py writes from the table.

Sources: every .java under apps/<app>/src and apps/core/<module>/src (no test or build folders).
 1. A HAL transport site is: every '.transact(' call; every call of a method named call / call<Upper>... in a file
    that declares HidlCaller or a class extending HidlCaller (the HIDL transport and its typed helpers); and every
    call of a method that hands one of its own parameters on as the code of a transport site (a forwarder, such as
    tx(iface, code, ...)), found per file until nothing new is found. Method declarations are not sites.
 2. The code argument of a site (the first one; a forwarder's own parameter position) is one of:
      <Prefix>HalCodes.NAME        a constant of a generated HalCodes class of this app (the class and the name exist)
      X.NAME where X ends in Call  an allow-list enum constant (its code is checked by rule 3)
      a parameter of the enclosing method (the method is then a forwarder, rule 1)
      p.code / p.code()            the code of a Call parameter p (the AIDL transports)
      p.f                          a field of a parameter p whose type is a code-carrying enum of the same file (Z9X
                                   PqItem / WbItem), every constant of which passes only HalCodes constants
      c(<one of these>)            the Play 6 IGmpf2 table map
    Anything else fails: an integer literal, arithmetic, a local or foreign constant.
 3. An allow-list enum constant NAME(<interface>, <code>, "method"...) (the AIDL Call enums) takes its code as
    <Prefix>HalCodes.NAME, never as a number, and the stock method of that row equals "method".
 4. The shared gmpf clients (apps/core/lumen-gmpf: GmpfCaller and every class extending GmpfCaller, GmpfBase or
    Gmpf2Base) transact through wire.<call>(...) / hidl.send(...) and the call* helpers: each of those is a site of
    rule 1, and its code is also allowed as key(OP) (OP a constant of enum GmpfOp, the conditional key(has(A) ? A : B)
    included) or a local assigned from key(...). A row put(OP, CODE) of a gmpf code table (new GmpfCodes<...>) names a
    constant of enum GmpfOp and a code of rule 2 (a HalCodes constant or an allow-list Call of the app) whose stock
    method is the operation's own.
Exit 0 green, 1 red (one line per finding), 2 usage.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "play6"))
import codes_table  # noqa: E402
import javascan  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SITE = re.compile(r"(?<![\w$])(call(?:[A-Z]\w*)?)\s*\(")
TRANSACT = re.compile(r"\.\s*(transact)\s*\(")
DECL_AFTER = re.compile(r"\s*(?:throws\s+[\w.,\s]+)?\{")
METHOD = re.compile(r"\b(\w+)\s*\(([^()]*)\)\s*(?:throws\s+[\w.,\s]+)?\{")
KEYWORDS = {"if", "for", "while", "switch", "catch", "synchronized", "return", "new", "else", "try", "do"}
HALCODES_REF = re.compile(r"^(\w*HalCodes)\.([A-Z][A-Z0-9_]*)$")
CALL_REF = re.compile(r"^(?:[\w.]*\.)?\w*Call\.[A-Z][A-Z0-9_]*$")
CODE_OF = re.compile(r"^(\w+)\.code(?:\(\))?$")
ENUM_ENTRY = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*(null|(?:\w+\.)?\w*Iface\.[A-Z][A-Z0-9_]*)\s*,\s*([^,()]+?)\s*,\s*\"(\w+)\"")
CODE_ENUM_ENTRY = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*\(([^()]*)\)")
GMPF_CLIENT = re.compile(r"\bclass\s+GmpfCaller\b|\bextends\s+(?:GmpfBase|Gmpf2Base|GmpfCaller)\s*<")
WIRE_SITE = re.compile(r"\b(?:wire|hidl)\s*\.\s*(\w+)\s*\(")
NOT_SENT = {"code", "name", "ready", "connect", "connected", "drop", "request"}
KEY_OF = re.compile(r"^key\((?:has\(([A-Z][A-Z0-9_]*)\)\?)?([A-Z][A-Z0-9_]*)(?::([A-Z][A-Z0-9_]*))?\)$")
OP_ENTRY = re.compile(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*\"(\w+)\"\s*\)")
TABLE_ROW = re.compile(r"\.\s*put\s*\(\s*([A-Z][A-Z0-9_]*)\s*,\s*([^(),]+?)\s*\)")


def java_files(root):
    out = []
    apps = os.path.join(root, "apps")
    for top in sorted(os.listdir(apps)):
        bases = [os.path.join(apps, top)]
        if top == "core":
            bases = [os.path.join(apps, top, m) for m in sorted(os.listdir(os.path.join(apps, top)))]
        for b in bases:
            src = os.path.join(b, "src")
            for d, dirs, names in os.walk(src):
                dirs[:] = sorted(x for x in dirs if x not in ("test", "build"))
                for n in sorted(names):
                    if n.endswith(".java"):
                        out.append((os.path.basename(b), os.path.join(d, n)))
    return out


def methods(mk):
    out = []
    for m in METHOD.finditer(mk):
        if m.group(1) in KEYWORDS:
            continue
        try:
            out.append((m.end() - 1, javascan.matching(mk, m.end() - 1), m.group(1), m.group(2)))
        except ValueError:
            pass
    return out


def enclosing(meths, pos):
    inside = [x for x in meths if x[0] < pos < x[1]]
    return min(inside, key=lambda x: x[1] - x[0]) if inside else None


def params(ptext):
    out = []
    for p in javascan.split_top(ptext) if ptext.strip() else []:
        w = p.strip().split()
        if w:
            out.append(w[-1])
    return out


def args_at(mk, open_paren):
    close = javascan.matching(mk, open_paren, "(", ")")
    inner = mk[open_paren + 1:close]
    return ([a.strip() for a in javascan.split_top(inner)] if inner.strip() else []), close


def check_tables(root):
    errs = []
    try:
        gens = codes_table.generated(root)
        for c, p in codes_table.tables(root):
            errs += codes_table.validate(root, c, codes_table.read(p))
    except codes_table.TableError as e:
        return [str(e)]
    for c, app, path, text in gens:
        rel = os.path.relpath(path, root)
        old = open(path, encoding="utf-8").read() if os.path.isfile(path) else None
        if old != text:
            errs.append("%s: %s is %s: run python3 tools/hal/gen_codes.py --write"
                        % (c, rel, "missing" if old is None else "not what devices/%s/hal/codes.tsv generates" % c))
    return errs


def halcodes_of(root):
    """{app: {class name: {NAME: row}}} from every java line of every table."""
    out = {}
    for c, p in codes_table.tables(root):
        t = codes_table.read(p)
        for app, rel in t["java"]:
            cls = os.path.basename(rel)[:-len(".java")]
            out.setdefault(app, {})[cls] = {r["name"]: r for r in t["rows"] if app in r["apps"]}
    return out


def gmpf_names(root):
    """({GmpfOp NAME: stock method}, {app: {Call enum.NAME: stock method}}) for rule 4."""
    ops, calls = {}, {}
    for app, path in java_files(root):
        raw = open(path, encoding="utf-8").read()
        mk = javascan.mask(raw)
        for em in re.finditer(r"\benum\s+(\w+)\s*(?:implements\s+[\w.,\s]+)?\{", mk):
            try:
                end = javascan.matching(mk, em.end() - 1)
            except ValueError:
                continue
            semi = mk[em.end():end].find(";")
            body = raw[em.end():em.end() + (semi if semi >= 0 else end - em.end())]
            if em.group(1) == "GmpfOp":
                ops.update(OP_ENTRY.findall(body))
            elif em.group(1).endswith("Call"):
                for e in ENUM_ENTRY.finditer(body):
                    calls.setdefault(app, {})["%s.%s" % (em.group(1), e.group(1))] = e.group(4)
    return ops, calls


def check_sources(root):
    errs = []
    try:
        consts = halcodes_of(root)
    except codes_table.TableError as e:
        return [str(e)], 0, 0, 0
    ops, call_methods = gmpf_names(root)
    nsites = nenum = nrows = 0
    for app, path in java_files(root):
        raw = open(path, encoding="utf-8").read()
        mk = javascan.mask(raw)
        rel = os.path.relpath(path, root)
        mine = consts.get(app, {})

        def code_ok(expr, meth):
            """None when expr is an allowed code expression, else why not; 'param:<name>' for a forwarded parameter."""
            e = re.sub(r"\s+", "", expr)
            m = HALCODES_REF.match(e)
            if m:
                if m.group(1) not in mine:
                    return "%s is no generated class of %s" % (m.group(1), app)
                if m.group(2) not in mine[m.group(1)]:
                    return "%s.%s is not a row for %s" % (m.group(1), m.group(2), app)
                return None
            if CALL_REF.match(e):
                return None
            m = CODE_OF.match(e)
            if m and meth and m.group(1) in params(meth[3]):
                return None
            m = re.match(r"^(\w+)\.\w+$", e)
            if m and meth:
                for prm in javascan.split_top(meth[3]) if meth[3].strip() else []:
                    w = prm.strip().split()
                    if len(w) >= 2 and w[-1] == m.group(1) and w[-2] in code_enums:
                        return None
            m = re.match(r"^c\((.*)\)$", e)
            if m:
                return code_ok(m.group(1), meth)
            m = re.match(r"^key\((\w+)\)$", e)
            if m and gmpf and meth and m.group(1) in params(meth[3]):
                return "param:" + m.group(1)
            if gmpf and e in ops:
                return None
            m = KEY_OF.match(e)
            if m and gmpf:
                bad = [o for o in m.groups() if o is not None and o not in ops]
                return "key(%s): no operation of GmpfOp" % ", ".join(bad) if bad else None
            local = re.search(r"\b%s\s*=\s*(key\s*\([^;]*\))\s*;" % re.escape(e), mk[meth[0]:meth[1]]) \
                if gmpf and meth and re.match(r"^\w+$", e) else None
            if local:
                return code_ok(local.group(1), meth)
            if meth and e in params(meth[3]):
                return "param:" + e
            return "%r is not a HalCodes constant" % expr.strip()

        # code-carrying enums of this file: every constant's arguments are HalCodes constants
        code_enums = set()
        for em in re.finditer(r"\benum\s+(\w+)\s*\{", mk):
            try:
                body = mk[em.end():javascan.matching(mk, em.end() - 1)]
            except ValueError:
                continue
            head = body.split(";", 1)[0]
            ents = list(CODE_ENUM_ENTRY.finditer(head))
            if ents and all(a.strip() and HALCODES_REF.match(re.sub(r"\s+", "", a)) for e in ents
                            for a in javascan.split_top(e.group(2))):
                code_enums.add(em.group(1))

        gmpf = GMPF_CLIENT.search(mk) is not None
        hidl = gmpf or re.search(r"\bclass\s+HidlCaller\b|\bextends\s+HidlCaller\b", mk) is not None
        meths = methods(mk)
        forward = {}   # method name -> index of the code parameter
        sites = []
        for m in TRANSACT.finditer(mk):
            sites.append((m.start(1), mk.index("(", m.end() - 1), 0))
        if gmpf:
            for m in WIRE_SITE.finditer(mk):
                if m.group(1) not in NOT_SENT:
                    sites.append((m.start(1), mk.index("(", m.end() - 1), 0))
        if "new GmpfCodes<" in mk:
            for m in TABLE_ROW.finditer(mk):
                line = mk.count("\n", 0, m.start()) + 1
                op, code = m.group(1), re.sub(r"\s+", "", m.group(2))
                nrows += 1
                why = code_ok(code, None)
                if op not in ops:
                    errs.append("%s:%d: code row %s: no operation of GmpfOp" % (rel, line, op))
                elif why:
                    errs.append("%s:%d: code row %s: %s" % (rel, line, op, why))
                else:
                    h = HALCODES_REF.match(code)
                    stock = mine[h.group(1)][h.group(2)]["method"] if h else call_methods.get(app, {}).get(code)
                    if stock != ops[op]:
                        errs.append("%s:%d: code row %s names %s, the stock method %s, not %s"
                                    % (rel, line, op, code, stock, ops[op]))
        if hidl:
            for m in SITE.finditer(mk):
                if m.group(1) == "callback":
                    continue
                op = mk.index("(", m.end() - 1)
                _, close = args_at(mk, op)
                if DECL_AFTER.match(mk, close + 1) and not re.search(r"[=(,!?:]\s*$|\breturn\s*$|->\s*$|\.\s*$", mk[:m.start()]):
                    continue   # a declaration of a call* method
                sites.append((m.start(1), op, 0))
        changed, rounds = True, 0
        while changed and rounds < 10:
            changed, rounds = False, rounds + 1
            found = []
            for pos, op, idx in sites:
                a, _ = args_at(mk, op)
                if idx >= len(a):
                    continue
                meth = enclosing(meths, pos)
                why = code_ok(a[idx], meth)
                if why and why.startswith("param:") and meth:
                    pi = params(meth[3]).index(why[6:])
                    if forward.get(meth[2]) != pi:
                        forward[meth[2]] = pi
                        changed = True
            for name, pi in forward.items():
                for m in re.finditer(r"(?<![\w$])%s\s*\(" % re.escape(name), mk):
                    op = mk.index("(", m.end() - 1)
                    _, close = args_at(mk, op)
                    if DECL_AFTER.match(mk, close + 1):
                        continue
                    found.append((m.start(), op, pi))
            for s in found:
                if s not in sites:
                    sites.append(s)
                    changed = True
        for pos, op, idx in sites:
            a, _ = args_at(mk, op)
            line = mk.count("\n", 0, pos) + 1
            if idx >= len(a):
                errs.append("%s:%d: a HAL call without a code argument" % (rel, line))
                continue
            nsites += 1
            why = code_ok(a[idx], enclosing(meths, pos))
            if why and not why.startswith("param:"):
                errs.append("%s:%d: HAL code %s" % (rel, line, why))
        # 3. allow-list enums
        for em in re.finditer(r"\benum\s+(\w+)\s*(?:implements\s+[\w.,\s]+)?\{", mk):
            try:
                end = javascan.matching(mk, em.end() - 1)
            except ValueError:
                continue
            body = mk[em.end():end]
            semi = body.find(";")
            body_raw = raw[em.end():em.end() + (semi if semi >= 0 else len(body))]
            body_mk = body[:semi if semi >= 0 else len(body)]
            for e in ENUM_ENTRY.finditer(body_raw):
                if body_mk[e.start():e.start() + len(e.group(1))] != e.group(1):
                    continue   # inside a comment or string
                nenum += 1
                line = mk.count("\n", 0, em.end() + e.start()) + 1
                code = e.group(3).strip()
                m = HALCODES_REF.match(code)
                if not m:
                    errs.append("%s:%d: %s.%s: the code %r is not a HalCodes constant" % (rel, line, em.group(1), e.group(1), code))
                    continue
                row = mine.get(m.group(1), {}).get(m.group(2))
                if row is None:
                    errs.append("%s:%d: %s.%s: %s.%s is not a row for %s" % (rel, line, em.group(1), e.group(1), m.group(1), m.group(2), app))
                elif row["method"] != e.group(4):
                    errs.append("%s:%d: %s.%s: %s is the stock method %s, the enum says %s"
                                % (rel, line, em.group(1), e.group(1), m.group(2), row["method"], e.group(4)))
    return errs, nsites, nenum, nrows


def main(argv):
    root = ROOT
    if argv[:1] == ["--root"] and len(argv) > 1:
        root, argv = os.path.abspath(argv[1]), argv[2:]
    if argv not in ([], ["--tables"], ["--sources"]):
        print(__doc__)
        return 2
    errs = []
    if argv != ["--sources"]:
        errs += check_tables(root)
    n = m = r = 0
    if argv != ["--tables"]:
        e, n, m, r = check_sources(root)
        errs += e
    for e in errs:
        print("check_codes: " + e)
    if errs:
        print("check_codes: FAILED (%d)" % len(errs))
        return 1
    rows = sum(len(codes_table.read(p)["rows"]) for _, p in codes_table.tables(root))
    print("check_codes: OK (%d table rows; %d HAL call sites, %d allow-list calls and %d gmpf code rows, every code a "
          "HalCodes constant)" % (rows, n, m, r))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
