#!/usr/bin/env python3
"""x50_props.py: the property edits of the X50 test image (compose_x50.sh step 5).

  x50_props.py apply  EDITS MODEL SYSTEM_PROP PRODUCT_PROP V BID      edit both files in place
  x50_props.py check  EDITS MODEL BASE_SYSTEM BASE_PRODUCT NEW_SYSTEM NEW_PRODUCT V BID
                                                                       the new files are exactly the base files
                                                                       with the edits (verify_tree_x50.py runs it)
  x50_props.py model  MODEL STOCK_MODEL_PROP                           MODEL is exactly the rule's subset of the
                                                                       stock file (on the build server, the dump)

EDITS is x50_prop.txt (format in its header), MODEL x50_model_prop.txt (appended to the product file under one
comment line). V is the variant (t1), BID the date part of the build id. Standard library only.
"""
import re
import sys

sys.dont_write_bytecode = True
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


def model_rule(line):
    k = keyof(line)
    return k is not None and k not in IDENTITY and not k.startswith("persist.sys.")


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
    if len(a) == 9 and a[0] == "check":
        _, ed, mo, bs, bp, ns, np_, v, bid = a
        e = read_edits(ed, v, bid)
        m = read_model(mo)
        bad = 0
        for b, n, sec, mod in ((bs, ns, "system", None), (bp, np_, "product", m)):
            base = load(b)
            if base and base[-1] == "":
                base = base[:-1]
            want = dump(edited(base, e[sec], mod))
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
