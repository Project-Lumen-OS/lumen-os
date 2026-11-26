#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""x50_props.py: the property edits of the X50 test image (compose_x50.sh step 5).

  x50_props.py apply  EDITS MODEL SYSTEM_PROP PRODUCT_PROP V BID      edit both files in place
  x50_props.py check  EDITS MODEL BASE_SYSTEM BASE_PRODUCT NEW_SYSTEM NEW_PRODUCT V BID [FIRST_START]
                                                                       the new files are exactly the base files
                                                                       with the edits (verify_tree_x50.py runs it);
                                                                       with FIRST_START (first_start_prop.txt) the
                                                                       product file then went through
                                                                       first_start.py apply (the one first-start
                                                                       setting of every image)
  x50_props.py model  MODEL STOCK_MODEL_PROP                           MODEL is exactly the rule's subset of the
                                                                       stock file (on the build server, the dump)

EDITS is x50_prop.txt (format in its header), MODEL x50_model_prop.txt (appended to the product file under one
comment line). V is the variant (t1), BID the date part of the build id; @VC@ in an edit is the O1 version code of a
release V (the public image of a model with updates). Standard library only.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
# first_start.py: next to this file when staged (flat), devices/_image/checks in the repository
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path[:0] = [HERE, os.path.join(HERE, "..", "..", "_image", "checks")]
KEY = re.compile(r"^[A-Za-z0-9_.\-]+$")
MARK = "# ---- XGIMI X50 Ultra (G0111) test image: devices/g0111/image/x50_prop.txt, x50_model_prop.txt"


def die(msg):
    print("x50_props: ERROR " + msg, file=sys.stderr)
    sys.exit(1)


def bid_time(bid):
    """(epoch seconds, build.prop date text) of the build id's leading UTC minute yyyymmddHHMM"""
    import calendar
    import time
    m = re.match(r"^(\d{12})", bid)
    if not m:
        die("build id %r does not start with a UTC minute yyyymmddHHMM (@UTC@ / @DATE@ need it)" % bid)
    try:
        tm = time.strptime(m.group(1), "%Y%m%d%H%M")
    except ValueError:
        die("build id %r does not start with a valid UTC minute" % bid)
    utc = calendar.timegm(tm)
    g = time.gmtime(utc)
    text = "%s %s %2d %02d:%02d:%02d UTC %d" % (("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[g.tm_wday],
                                                ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
                                                 "Nov", "Dec")[g.tm_mon - 1], g.tm_mday, g.tm_hour, g.tm_min,
                                                g.tm_sec, g.tm_year)
    return str(utc), text


VERSION = re.compile(r"(\d+)\.(\d{1,2})\.(\d{1,2})(?:-(beta|rc)(\d{1,2}))?")


def version_code(v):
    """@VC@: the O1 version code of a release version (x*1000000 + y*10000 + z*100 + n; betaN n = N, rcN n = 50 + N,
    the release 99), as tools/devices/ota/ota_conf.py version_code and the shared updater read it"""
    m = VERSION.fullmatch(v)
    if not m:
        die("@VC@ needs a release version (x.y.z, x.y.z-betaN, x.y.z-rcN), not %r" % v)
    x, y, z, kind, n = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
    if kind == "beta" and not 1 <= int(n) <= 49 or kind == "rc" and not 1 <= int(n) <= 48:
        die("@VC@: %s has no O1 code (beta 1..49, rc 1..48)" % v)
    k = int(n) if kind == "beta" else 50 + int(n) if kind == "rc" else 99
    return int(x) * 1000000 + int(y) * 10000 + int(z) * 100 + k


def read_edits(path, v, bid):
    out = {"system": [], "product": []}
    sec = None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line in ("[system]", "[product]"):
            sec = line[1:-1]
            continue
        if sec is None:
            die("%s:%d: an edit before [system] or [product]" % (path, n))
        if "@VC@" in line:
            line = line.replace("@VC@", str(version_code(v)))
        line = line.replace("@V@", v).replace("@BID@", bid)
        if "@UTC@" in line or "@DATE@" in line:
            utc, text = bid_time(bid)
            line = line.replace("@UTC@", utc).replace("@DATE@", text)
        if "@" in line:
            die("%s:%d: unknown @...@ placeholder" % (path, n))
        op = "set"
        if line[0] in "+-":
            op, line = ("add" if line[0] == "+" else "del"), line[1:]
        k, eq, val = line.partition("=")
        if not KEY.match(k) or (op == "del") == bool(eq):
            die("%s:%d: bad edit %r" % (path, n, raw.rstrip()))
        out[sec].append((op, k, val))
    return out


def read_model(path):
    lines = []
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        k, eq, _ = line.partition("=")
        if not eq or not KEY.match(k):
            die("%s:%d: not KEY=VALUE" % (path, n))
        lines.append(line)
    return lines


def keyof(line):
    s = line.strip()
    if not s or s.startswith("#") or "=" not in s:
        return None
    return s.split("=", 1)[0].strip()


def edited(base_lines, edits, model=None):
    lines = list(base_lines)
    for op, k, val in edits:
        idx = [i for i, l in enumerate(lines) if keyof(l) == k]
        if op == "add":
            if idx:
                die("+%s: the key is already in the file" % k)
            lines.append("%s=%s" % (k, val))
            continue
        if len(idx) != 1:
            die("%s%s: the key is %d times in the file, not once" % ("-" if op == "del" else "", k, len(idx)))
        if op == "del":
            del lines[idx[0]]
        else:
            lines[idx[0]] = "%s=%s" % (k, val)
    if model is not None:
        have = {keyof(l) for l in lines}
        lines.append(MARK)
        for m in model:
            if keyof(m) in have:
                die("model prop %s is already in the product file" % keyof(m))
            lines.append(m)
    return lines


def load(path):
    return open(path, encoding="utf-8").read().split("\n")


def dump(lines):
    s = "\n".join(lines)
    return s if s.endswith("\n") else s + "\n"


# the identity keys of the stock file: Lumen keeps its own (ro.product.unique is an XGIMI codec id, not identity)
IDENTITY = {"ro.product.device", "ro.product.model", "ro.product.brand", "ro.product.manufacturer",
            "ro.product.board", "ro.product.name", "ro.build.product"}


# the first-start keys (devices/_image/first_start_prop.txt) never come from a stock model file: the language and time
# zone are one setting of every image (the overseas TITAN files and a stock file such as H0046's set ro.product.locale)
FIRST_START = ("ro.product.locale", "ro.localization.")


def model_rule(line):
    k = keyof(line)
    return k is not None and k not in IDENTITY and not k.startswith(("persist.sys.",) + FIRST_START)


def main(a):
    if len(a) == 7 and a[0] == "apply":
        _, ed, mo, sp, pp, v, bid = a
        e = read_edits(ed, v, bid)
        m = read_model(mo)
        for path, sec, mod in ((sp, "system", None), (pp, "product", m)):
            base = load(path)
            if base and base[-1] == "":
                base = base[:-1]
            open(path, "w", encoding="utf-8").write(dump(edited(base, e[sec], mod)))
        print("x50_props: applied %d system and %d product edits and %d model props"
              % (len(e["system"]), len(e["product"]), len(m)))
        return 0
    if len(a) in (9, 10) and a[0] == "check":
        _, ed, mo, bs, bp, ns, np_, v, bid = a[:9]
        e = read_edits(ed, v, bid)
        m = read_model(mo)
        bad = 0
        for b, n, sec, mod in ((bs, ns, "system", None), (bp, np_, "product", m)):
            base = load(b)
            if base and base[-1] == "":
                base = base[:-1]
            lines = edited(base, e[sec], mod)
            if sec == "product" and len(a) == 10:
                import first_start
                prop = first_start.read_prop(a[9])
                lines = first_start.transform(lines, first_start.block_lines(a[9]), first_start.all_keys(prop))
            want = dump(lines)
            got = open(n, encoding="utf-8").read()
            if want != got:
                bad = 1
                print("x50_props: ERROR %s is not the base %s with the %s edits" % (n, b, sec))
        if not bad:
            print("x50_props: both build.prop files are exactly the base files with the edits")
        return bad
    if len(a) == 3 and a[0] == "model":
        _, mo, stock = a
        m = read_model(mo)
        st = [l.strip() for l in open(stock, encoding="utf-8", errors="replace")]
        want = [l for l in st if model_rule(l)]
        if m != want:
            for l in sorted(set(want) - set(m)):
                print("x50_props: ERROR stock line not in %s: %s" % (mo, l))
            for l in sorted(set(m) - set(want)):
                print("x50_props: ERROR %s line not in the stock file: %s" % (mo, l))
            if set(m) == set(want):
                print("x50_props: ERROR same lines, other order")
            return 1
        print("x50_props: %d model props = the stock file's lines by the rule, in its order" % len(m))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
