#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""first_start.py: the one first-start setting of every Lumen OS image: language, time and time zone.

The setting is devices/_image/first_start_prop.txt (2026-11-13: English, time and time zone from the network,
no fixed zone, the same on every model). The image builder stages it and this script for every device
(tools/build/build.py COMMON_FILES); every recipe applies it and checks the image (tools/build/README.md, "First start").

  first_start.py apply PROP SYSDIR             SYSDIR/product/etc/build.prop without any line of a first-start key,
                                               then the lines of PROP at its end
  first_start.py check PROP SYSDIR             the image check of a tree or a mounted image (SYSDIR = its system/)
  first_start.py check PROP --tar TAR          the same on a system tar (members system/...)
  first_start.py lint ROOT                     the repository check: no other file sets a first-start key

The image check resolves the boot properties as Android 14 init does (system/core/init/property_service.cpp,
PropertyLoadBootDefaults): system/build.prop, system_ext/etc/build.prop, then vendor and odm, then
product/etc/build.prop, a later line of a key replacing an earlier one. Vendor and odm are not part of a system image,
so they are taken as hostile: every first-start key set to another value (the stock XGIMI vendor sets
persist.sys.language=zh, persist.sys.country=CN, persist.sys.timezone=Asia/Shanghai, ro.product.locale=zh-CN). The
image passes only when:
  - the product build.prop holds the lines of PROP once, as one block, and sets no first-start key outside it
    (no import line either: the order would be unknown);
  - with that order every key of PROP resolves to its PROP value, and zygote's locale (AndroidRuntime.cpp readLocale)
    is PROP's ro.product.locale;
  - no init rc or shell script of the image sets a first-start key to a value of its own (clearing it, setprop KEY "",
    is allowed) or writes the automatic time settings (settings put ... auto_time, auto_time_zone, time_zone);
  - no overlay APK of the image (product, system_ext and system overlay folders) carries the SettingsProvider
    defaults def_auto_time or def_auto_time_zone (so the AOSP defaults, both true, stay).
Standard library only. As root on a tree or a mounted image (some folders are root only).
"""
import io
import os
import re
import sys
import tarfile
import zipfile

sys.dont_write_bytecode = True
# keys that decide a first-start language or time zone besides the ones PROP sets: the legacy product pair is read
# by zygote only without ro.product.locale, still nothing else may set it
EXTRA_KEYS = ("ro.product.locale.language", "ro.product.locale.region")
# what the stock XGIMI vendor build.prop of every model sets (G0082, G0130, G0148, G0111, G0155, H0062 dumps): the
# hostile vendor of the check, any other first-start key gets VENDOR_OTHER
STOCK_VENDOR = {"persist.sys.language": "zh", "persist.sys.country": "CN", "persist.sys.timezone": "Asia/Shanghai",
                "ro.product.locale": "zh-CN"}
VENDOR_OTHER = "vendor-value"
PRODUCT = "product/etc/build.prop"
LAYERS = ("build.prop", "system_ext/etc/build.prop")       # read before vendor and odm
OVERLAY_DIRS = ("product/overlay", "system_ext/overlay", "overlay")
SETTINGS_RX = re.compile(r"\bsettings\s+put\s+\S+\s+(auto_time|auto_time_zone|time_zone)\b")
AUTO_TIME_RES = (b"def_auto_time", "def_auto_time".encode("utf-16-le"))
EMPTY = ('""', "''")


def key_of(line):
    """The key of a build.prop line (init's parser: blanks around, '#' comments), else None."""
    s = line.strip()
    if not s or s.startswith("#") or s.startswith("import ") or "=" not in s:
        return None
    return s.split("=", 1)[0].strip()


def read_prop(path):
    """[(key, value)] of a PROP file, in order. Every line is KEY=VALUE or a comment."""
    out = []
    for n, raw in enumerate(open(path, encoding="utf-8").read().split("\n"), 1):
        s = raw.strip()
        if not s or s.startswith("#"):
            continue
        if "=" not in s:
            raise SystemExit("first_start: %s:%d: not KEY=VALUE" % (path, n))
        k, v = s.split("=", 1)
        out.append((k.strip(), v.strip()))
    if len({k for k, _ in out}) != len(out):
        raise SystemExit("first_start: %s sets a key twice" % path)
    return out


def block_lines(path):
    """The lines of PROP as they go into a product build.prop (trailing blank lines dropped)."""
    lines = open(path, encoding="utf-8").read().split("\n")
    while lines and lines[-1] == "":
        lines.pop()
    return lines


def all_keys(prop):
    return [k for k, _ in prop] + [k for k in EXTRA_KEYS if k not in dict(prop)]


def transform(lines, block, keys):
    """A product build.prop (lines) without any line of KEYS, then BLOCK."""
    keep = [l for l in lines if key_of(l) not in keys]
    while keep and keep[-1] == "":
        keep.pop()
    return keep + block


def apply(prop_path, sysdir):
    prop, block = read_prop(prop_path), block_lines(prop_path)
    path = os.path.join(sysdir, PRODUCT)
    lines = open(path, encoding="utf-8").read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    if block[0] in lines:
        raise SystemExit("first_start: %s already holds the first-start block (%s)" % (path, block[0]))
    dropped = [l for l in lines if key_of(l) in all_keys(prop)]
    open(path, "w", encoding="utf-8").write("\n".join(transform(lines, block, all_keys(prop))) + "\n")
    print("first_start: %s: %d first-start lines at the end%s" % (
        path, len(prop), (", dropped the earlier " + "; ".join(dropped)) if dropped else ""))
    return 0


# ---- the image
class Tree:
    """A system tree (a folder or a mounted image): rel paths under SYSDIR."""

    def __init__(self, sysdir):
        self.top = sysdir

    def read(self, rel):
        p = os.path.join(self.top, rel)
        return open(p, "rb").read() if os.path.isfile(p) else None

    def files(self):
        for d, dirs, fs in os.walk(self.top):
            dirs.sort()
            for f in sorted(fs):
                p = os.path.join(d, f)
                if os.path.isfile(p) and not os.path.islink(p):
                    yield os.path.relpath(p, self.top)


class Tar:
    """A system tar (members system/...): rel paths under system/."""

    def __init__(self, path):
        self.t = tarfile.open(path)
        self.members = {m.name[len("system/"):]: m for m in self.t.getmembers()
                        if m.isfile() and m.name.startswith("system/")}

    def read(self, rel):
        m = self.members.get(rel)
        f = self.t.extractfile(m) if m else None
        return f.read() if f else None

    def files(self):
        return sorted(self.members)


def text(b):
    return b.decode("utf-8", "replace").split("\n") if b is not None else []


def load(props, lines):
    for l in lines:
        k = key_of(l)
        if k is not None:
            props[k] = l.strip().split("=", 1)[1].strip()


def read_locale(p):
    """AndroidRuntime.cpp readLocale (Android 14)."""
    if p.get("persist.sys.locale"):
        return p["persist.sys.locale"]
    if p.get("persist.sys.language"):
        out = p["persist.sys.language"]
        for k in ("persist.sys.country", "persist.sys.localevar"):
            if p.get(k):
                out += "-" + p[k]
        return out
    if p.get("ro.product.locale"):
        return p["ro.product.locale"]
    return (p.get("ro.product.locale.language") or "en") + "-" + (p.get("ro.product.locale.region") or "US")


def find_block(lines, block):
    """Index of BLOCK as consecutive lines of LINES, -1 when it is not there, -2 when it is there more than once."""
    hits = [i for i in range(len(lines) - len(block) + 1) if lines[i:i + len(block)] == block]
    return hits[0] if len(hits) == 1 else (-2 if hits else -1)


def setprop_rx(keys):
    return re.compile(r"\bsetprop\s+(%s)(?=\s|$)\s*(\S*)" % "|".join(re.escape(k) for k in keys))


def check(prop_path, src):
    prop, block = read_prop(prop_path), block_lines(prop_path)
    want, keys = dict(prop), all_keys(prop)
    img = Tar(src[1]) if src[0] == "--tar" else Tree(src[0])
    where = src[-1]
    bad = []
    prod = text(img.read(PRODUCT))
    if not prod:
        return ["%s: no %s" % (where, PRODUCT)]
    if prod[-1] == "":
        prod = prod[:-1]
    at = find_block(prod, block)
    if at < 0:
        bad.append("%s: the lines of %s are %s" % (PRODUCT, os.path.basename(prop_path),
                                                    "there more than once" if at == -2 else "not there as one block"))
    inside = range(at, at + len(block)) if at >= 0 else range(0)
    for i, l in enumerate(prod):
        if key_of(l) in keys and i not in inside:
            bad.append("%s:%d sets %s outside the first-start block: %s" % (PRODUCT, i + 1, key_of(l), l.strip()))
        if l.strip().startswith("import ") and i > (at if at >= 0 else -1):
            bad.append("%s:%d imports another file after the first-start block: %s" % (PRODUCT, i + 1, l.strip()))
    # Android 14 order: the image's system and system_ext, then a vendor and odm, then the image's product
    def resolve(vendor):
        props = {}
        for rel in LAYERS:
            load(props, text(img.read(rel)))
        props.update(vendor)
        load(props, prod)
        return props
    hostile = resolve({k: STOCK_VENDOR.get(k, VENDOR_OTHER) for k in keys})
    for k, v in prop:
        if hostile.get(k) != v:
            bad.append("%s resolves to %r, not %r (a vendor or odm value wins)" % (k, hostile.get(k), v))
    loc = read_locale(resolve(STOCK_VENDOR))
    if loc != want.get("ro.product.locale"):
        bad.append("zygote's locale with the stock XGIMI vendor is %r, not %r" % (loc, want.get("ro.product.locale")))
    elif read_locale(hostile) != loc:
        bad.append("zygote's locale with a vendor that sets every key is %r, not %r" % (read_locale(hostile), loc))
    # init rc files, shell scripts, overlays
    rx, nrc, nov = setprop_rx(keys), 0, 0
    for rel in img.files():
        if rel.endswith((".rc", ".sh")):
            nrc += 1
            for n, l in enumerate(text(img.read(rel)), 1):
                s = l.strip()
                if s.startswith("#"):
                    continue
                for m in rx.finditer(s):
                    if m.group(2) not in EMPTY:
                        bad.append("%s:%d sets %s to its own value: %s" % (rel, n, m.group(1), s))
                if SETTINGS_RX.search(s):
                    bad.append("%s:%d writes the automatic time settings: %s" % (rel, n, s))
        elif rel.endswith(".apk") and rel.startswith(tuple(d + "/" for d in OVERLAY_DIRS)):
            nov += 1
            try:
                z = zipfile.ZipFile(io.BytesIO(img.read(rel) or b""))
                arsc = z.read("resources.arsc") if "resources.arsc" in z.namelist() else b""
            except (zipfile.BadZipFile, KeyError) as e:
                bad.append("%s: not a readable APK (%s)" % (rel, e))
                continue
            if any(r in arsc for r in AUTO_TIME_RES):
                bad.append("%s: an overlay with def_auto_time or def_auto_time_zone (the AOSP defaults must stay)" % rel)
    if not bad:
        print("first_start: OK %s: product build.prop lines %d-%d, locale %s with a stock vendor, time zone %s, "
              "%d rc and sh files, %d overlays" % (where, at + 1, at + len(block), loc,
                                                    want.get("persist.sys.timezone") or "none (from the network)",
                                                    nrc, nov))
    return bad


# ---- the repository
# folders no image is built from: proof records, stock analysis and survey dumps, notes, the frozen v6.5 setup rc (read by no
# recipe since the first-start setting), the tests (their fixtures set first-start keys on purpose)
LINT_SKIP = re.compile(r"^(devices/_baseline/|devices/[^/]+/(research|survey[^/]*|live|offline|probe|telegram)/|"
                       r"overlay/v65/|tools/[^/]+/test/|tools/[^/]+/[^/]+/test/|devices/[^/]+/test/)")
LINT_TOPS = ("devices", "overlay", "tools", "apps")
SELF = ("devices/_image/first_start_prop.txt", "devices/_image/checks/first_start.py")


def lint_file(rel, data, keys):
    """Lines of the repository file REL that set a first-start key or the automatic time defaults."""
    out = []
    alt = "|".join(re.escape(k) for k in keys)
    prop_rx = re.compile(r"^\s*[+-]?(%s)\s*(=|$)" % alt)
    lit_rx = re.compile(r"[\"'](%s)=" % alt)
    set_rx = setprop_rx(keys)
    res_rx = re.compile(r"name=\"def_auto_time(_zone)?\"")
    for n, l in enumerate(data.split("\n"), 1):
        s = l.strip()
        hit = None
        if rel.endswith((".rc", ".sh")):
            if not s.startswith("#"):
                m = set_rx.search(s)
                if m and m.group(2) not in EMPTY:
                    hit = "sets %s" % m.group(1)
                elif SETTINGS_RX.search(s):
                    hit = "writes the automatic time settings"
        elif rel.endswith(".tsv"):
            if not l.startswith("#"):
                ks = [c for c in l.split("\t")[2:] if re.match(r"^[+-]?(%s)=" % alt, c)]
                if ks:
                    hit = "names %s" % ks[0]
        elif rel.endswith(".py"):
            m = lit_rx.search(l)
            if m:
                hit = "writes %s" % m.group(1)
        elif rel.endswith(".xml"):
            m = res_rx.search(l)
            if m:
                hit = "changes the SettingsProvider default %s" % m.group(0)
        else:
            m = prop_rx.match(l)
            if m:
                hit = "sets %s" % m.group(1)
        if hit:
            out.append("%s:%d %s: %s (the first-start setting is %s only)" % (rel, n, hit, s[:120], SELF[0]))
    return out


def lint(root, prop_path=None):
    prop = read_prop(prop_path or os.path.join(root, SELF[0]))
    keys = all_keys(prop)
    bad, n = [], 0
    for top in LINT_TOPS:
        for d, dirs, fs in os.walk(os.path.join(root, top)):
            dirs[:] = sorted(x for x in dirs if not x.startswith(".") and x != "build")
            for f in sorted(fs):
                rel = os.path.relpath(os.path.join(d, f), root).replace(os.sep, "/")
                if rel in SELF or LINT_SKIP.match(rel) or rel.endswith(".md"):
                    continue
                if top == "apps" and not (("/res/" in rel and rel.endswith(".xml")) or "/image/" in rel):
                    continue
                if not rel.endswith((".txt", ".prop", ".conf", ".rc", ".sh", ".tsv", ".py", ".xml")):
                    continue
                if rel.endswith(".py") and not rel.startswith(("devices/", "overlay/", "tools/build/")):
                    continue
                try:
                    data = open(os.path.join(root, rel), encoding="utf-8").read()
                except (UnicodeDecodeError, OSError):
                    continue
                n += 1
                bad += lint_file(rel, data, keys)
    return bad, n


def main(a):
    if len(a) == 3 and a[0] == "apply":
        return apply(a[1], a[2])
    if a[:1] == ["check"] and (len(a) == 3 or (len(a) == 4 and a[2] == "--tar")):
        bad = check(a[1], a[2:])
        for b in bad:
            print("first_start: ERROR " + b)
        return 1 if bad else 0
    if len(a) == 2 and a[0] == "lint":
        bad, n = lint(a[1])
        for b in bad:
            print("first_start: ERROR " + b)
        if not bad:
            print("first_start: lint OK, %d files: only %s sets the first-start keys" % (n, SELF[0]))
        return 1 if bad else 0
    sys.stdout.write(__doc__ or "")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
