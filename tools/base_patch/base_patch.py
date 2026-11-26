#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""base_patch.py: fixes carried in the code of a base APK (README.md), one row per file in patches.tsv.

  base_patch.py build ID BASE.apk [--out DIR]     the patched classes.dex of fix ID made from the base APK
                                                  -> DIR/ID/classes.dex (default build/base_patch); prints its sha256
  base_patch.py apply PATH APK OUT.apk [--dex-dir DIR]
                                                  APK with the patched classes.dex (what a signer does before it signs)
  base_patch.py list

A fix is the source of the replaced class (with its nested classes) in the folder named by its id. build compiles
it against apps/sdk/framework-full-headers.jar and android.jar (stubs/ for the app's own types), dexes it with d8 of
the build tools and puts it into the base dex in place of the old class (DexSwap.java, dexlib2 of the pinned
tools.tsv). It refuses a base dex other than the pinned one, proves with dexdump that every other class is unchanged
and that every reference of the new classes resolves in the dex or the framework (link), and checks the result
against its pin. apply keeps every other zip entry byte for byte.

One file may carry several fixes: its rows apply in the order of patches.tsv, each on the result of the one before
(the base pin of a row is the result pin of the row above it).

Signers call patched_apk(path, apk, work): None for a file without a fix, else the patched copy to sign.
Tools: JAVA_HOME (JDK 17+) or javac/java on PATH, BUILD_TOOLS or an SDK with build-tools/36.0.0 (d8, dexdump) and
platforms/android-34. Exit 0 = OK, 1 = stopped.
"""
import argparse
import glob
import hashlib
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(HERE))
DEX_DIR = os.path.join(G, "build", "base_patch")
FRAMEWORK = os.path.join(G, "apps", "sdk", "framework-full-headers.jar")
API = 34
PENDING = "PENDING"
DEX = "classes.dex"


class Stop(Exception):
    pass


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(p):
    with open(p, "rb") as f:
        return sha256(f.read())


def table(name):
    out = []
    for line in open(os.path.join(HERE, name), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            out.append(line.rstrip("\n").split("\t"))
    return out


def rows():
    return [dict(zip(("path", "id", "cls", "base", "out"), r)) for r in table("patches.tsv")]


def row(key):
    for r in rows():
        if key in (r["path"], r["id"]):
            return r
    return None


def chain(path):
    """the fixes of one base file in the order they apply"""
    return [r for r in rows() if r["path"] == path]


def run(cmd, **kw):
    # latin-1: dexdump prints string constants as raw bytes
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="latin-1", **kw)
    if p.returncode != 0:
        raise Stop(f"{os.path.basename(cmd[0])} failed ({p.returncode}):\n{p.stdout[-3000:]}")
    return p.stdout


# ------------------------------------------------------------------ tools
def cache_dir():
    return os.environ.get("BASE_PATCH_CACHE") or os.path.join(os.path.expanduser("~"), ".cache", "lumen", "base_patch")


def pinned_jar(name):
    for f, url, want in table("tools.tsv"):
        if f != name:
            continue
        p = os.path.join(cache_dir(), f)
        if not os.path.isfile(p):
            os.makedirs(cache_dir(), exist_ok=True)
            with urllib.request.urlopen(url, timeout=120) as r:
                data = r.read()
            if sha256(data) != want:
                raise Stop(f"{url}: sha256 {sha256(data)}, pinned {want}")
            open(p + ".part", "wb").write(data)
            os.replace(p + ".part", p)
        if sha256_file(p) != want:
            raise Stop(f"{p}: sha256 differs from tools.tsv")
        return p
    raise Stop(f"{name} not in tools.tsv")


def sdk_tools():
    bt = os.environ.get("BUILD_TOOLS")
    sdks = [os.environ.get(k) for k in ("ANDROID_HOME", "ANDROID_SDK_ROOT")]
    sdks += [os.path.expanduser("~/Library/Android/sdk"), os.path.expanduser("~/lumen-tools/android-sdk")]
    if not bt:
        bt = next((os.path.join(s, "build-tools", "36.0.0") for s in sdks
                   if s and os.path.isdir(os.path.join(s, "build-tools", "36.0.0"))), None)
    if not bt:
        raise Stop("no build-tools 36.0.0 (set BUILD_TOOLS)")
    sdk = os.path.dirname(os.path.dirname(bt))
    t = {"d8": os.path.join(bt, "d8"), "dexdump": os.path.join(bt, "dexdump"),
         "android.jar": os.path.join(sdk, "platforms", f"android-{API}", "android.jar")}
    for k, p in t.items():
        if not os.path.isfile(p):
            raise Stop(f"missing {p}")
    return t


def jdk(name):
    home = os.environ.get("JAVA_HOME")
    p = os.path.join(home, "bin", name) if home else shutil.which(name)
    if not p or not os.path.isfile(p):
        raise Stop(f"no {name} (set JAVA_HOME to a JDK 17+)")
    return p


def jar_dir(src, dst):
    with zipfile.ZipFile(dst, "w") as z:
        for f in sorted(glob.glob(os.path.join(src, "**", "*.class"), recursive=True)):
            z.write(f, os.path.relpath(f, src))


# ------------------------------------------------------------------ dex
def apk_dex(apk):
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        if [n for n in names if re.fullmatch(r"classes\d+\.dex", n)]:
            raise Stop(f"{apk}: more than one dex, the swap handles classes.dex only")
        return z.read(DEX)


CLASS_HEAD = re.compile(r"^Class #\d+\s+-\s*$", re.M)
# dexlib2 lays the code out again: other file and code offsets (payload alignment nops), its own outs count and
# no explicit default static values; names, opcodes, registers and every reference stay compared
NORMAL = [
    (re.compile(r"^[0-9a-f]{6}: [0-9a-f .]*\|", re.M), "|"),          # file address and raw code units
    (re.compile(r"\|\[[0-9a-f]+\] "), "|[] "),
    (re.compile(r"// (method|field|type|string|call_site|method_handle|proto)@[0-9a-f]+"), r"// \1@"),
    (re.compile(r"(source_file_idx\s*:\s*)\d+"), r"\1"),
    (re.compile(r"^\|[0-9a-f]{4}: nop // spacer\n", re.M), ""),
    (re.compile(r"^\|[0-9a-f]{4}: ", re.M), "|"),
    (re.compile(r"[0-9a-f]{4,8} // [+-][0-9a-f]+"), "// branch"),
    (re.compile(r"0x[0-9a-f]{4}"), "0x"),
    (re.compile(r"^\s+outs\s+:\s+\d+\n", re.M), ""),
    (re.compile(r"(insns size\s+:\s+)\d+"), r"\1"),
    (re.compile(r"^\s+value\s+:\s+(false|null|0|0\.0)\n", re.M), ""),
]


def dex_classes(dexdump, dex):
    """{descriptor: normalised dexdump -d text} of one dex."""
    text = run([dexdump, "-d", dex])
    out = {}
    for block in CLASS_HEAD.split(text)[1:]:
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        if not m:
            raise Stop(f"{dex}: a class block without a descriptor")
        for rx, rep in NORMAL:
            block = rx.sub(rep, block)
        out[m.group(1)] = block
    return out


def swapped(cls, name):
    return name == cls or name.startswith(cls[:-1] + "$")


def compare(dexdump, base, out, cls):
    """Every class of base but cls (and its nested ones) is in out with the same code; returns (kept, added)."""
    a, b = dex_classes(dexdump, base), dex_classes(dexdump, out)
    kept = [n for n in a if not swapped(cls, n)]
    missing = [n for n in kept if n not in b]
    if missing:
        raise Stop(f"{len(missing)} classes lost, first {missing[0]}")
    diff = [n for n in kept if a[n] != b[n]]
    if diff:
        raise Stop(f"{len(diff)} classes outside {cls} changed, first {diff[0]}")
    extra = [n for n in b if n not in a and not swapped(cls, n)]
    if extra:
        raise Stop(f"new classes outside {cls}: {extra[:3]}")
    added = sorted(n for n in b if swapped(cls, n))
    if cls not in added:
        raise Stop(f"{cls} missing in the result")
    return len(kept), added


# references: |0012: invoke-virtual {v0, v1}, La/B;.name:(I)V // method@0042 (fields: ...La/B;.name:I // field@)
REF = re.compile(r"\|[0-9a-f]{4}: ([a-z0-9/-]+) [^\n]*?(L[^;\s]+;|\[\S+?)\.([^:\s]+):(\S+) // (method|field)@")
STATIC_OPS = ("invoke-static", "sget", "sput")


def dex_members(text):
    """{class: (superclass, [interfaces], {(name, type): static})} of a dexdump text"""
    out = {}
    for block in CLASS_HEAD.split(text)[1:]:
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        sup = re.search(r"Superclass\s*:\s*'([^']+)'", block)
        ifs = re.findall(r"#\d+\s+:\s+'(L[^']+)'", block.split("Static fields", 1)[0])
        mem = {}
        for n, ty, acc in re.findall(r"name\s*:\s*'([^']+)'\s*\n\s*type\s*:\s*'([^']+)'\s*\n\s*access\s*:\s*0x[0-9a-f]+ "
                                     r"\(([A-Z_ ]*)\)", block):
            mem[(n, ty)] = "STATIC" in acc.split()
        out[m.group(1)] = (sup.group(1) if sup else None, ifs, mem)
    return out


def class_file(data):
    """(superclass, [interfaces], {(name, descriptor): static}) of a Java class file, descriptors as dexdump writes"""
    pos, utf, cls = 10, {}, {}
    count = struct.unpack_from(">H", data, 8)[0]
    i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack_from(">H", data, pos + 1)[0]
            utf[i] = data[pos + 3:pos + 3 + n].decode("utf-8", "replace")
            pos += 3 + n
        elif tag == 7:
            cls[i] = struct.unpack_from(">H", data, pos + 1)[0]
            pos += 3
        elif tag in (5, 6):
            pos += 9
            i += 1
        else:
            pos += {3: 5, 4: 5, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}[tag]
        i += 1
    name = lambda k: "L" + utf[cls[k]] + ";" if k else None  # noqa: E731
    sup, nif = struct.unpack_from(">HH", data, pos + 4)
    pos += 8
    ifs = [name(k) for k in struct.unpack_from(">%dH" % nif, data, pos)]
    pos += 2 * nif
    mem = {}
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(n):
            acc, nm, desc, na = struct.unpack_from(">HHHH", data, pos)
            pos += 8
            for _ in range(na):
                pos += 6 + struct.unpack_from(">I", data, pos + 2)[0]
            mem[(utf[nm], utf[desc])] = bool(acc & 0x0008)
    return name(sup), ifs, mem


def platform_classes(jars):
    """lookup(descriptor) of the classes of the platform jars (the headers the fix compiled against), None if absent"""
    zips = [zipfile.ZipFile(j) for j in jars]
    memo = {}

    def lookup(desc):
        if desc not in memo:
            memo[desc] = None
            entry = desc[1:-1] + ".class"
            for z in zips:
                if entry in z.NameToInfo:
                    memo[desc] = class_file(z.read(entry))
                    break
        return memo[desc]
    return lookup


def resolve(classes, platform, holder, key):
    """True / False for a static / instance member key of holder declared on it, its superclasses or interfaces (in
    the dex, then the platform); None when it is nowhere"""
    if holder.startswith("["):
        return False  # arrays: clone and the members of Object
    todo, seen = [holder], set()
    while todo:
        c = todo.pop()
        if c in seen:
            continue
        seen.add(c)
        info = classes.get(c) or platform(c)
        if info is None:
            continue
        sup, ifs, mem = info
        if key in mem:
            return mem[key]
        todo.extend(([sup] if sup else []) + ifs)
    return None


def link(dexdump, dex, cls, platform):
    """Every member the classes of fix cls use resolves, in the dex or in the platform (lookup of platform_classes),
    static where the opcode says static. Returns the number of references checked."""
    text = run([dexdump, "-d", dex])
    classes = dex_members(text)
    bad, n = [], 0
    for block in CLASS_HEAD.split(text)[1:]:
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        if not m or not swapped(cls, m.group(1)):
            continue
        for op, holder, name, ty, kind in REF.findall(block):
            n += 1
            where = resolve(classes, platform, holder, (name, ty))
            if where is None:
                bad.append(f"{holder}.{name}:{ty}")
            elif where != op.startswith(STATIC_OPS) and not holder.startswith("["):
                bad.append(f"{holder}.{name}:{ty} ({op})")
    if bad:
        raise Stop(f"{len(bad)} references of {cls} do not resolve, first {bad[0]}")
    return n


def build(fid, base_apk, out_dir=DEX_DIR):
    """the patched classes.dex of fix fid made from the base APK (a later fix of a chain on the ones before it)"""
    r = row(fid)
    if not r or r["id"] != fid:
        raise Stop(f"no fix {fid} in patches.tsv")
    dex = apk_dex(base_apk)
    for prev in chain(r["path"]):
        if prev["id"] == fid:
            break
        dex = open(fixed_dex(prev, dex, out_dir), "rb").read()
    return build_dex(r, dex, out_dir)


def build_dex(r, dex, out_dir=DEX_DIR):
    """fix r on the base dex bytes: compile, dex, swap, prove, pin"""
    fid = r["id"]
    if sha256(dex) != r["base"]:
        raise Stop(f"classes.dex {sha256(dex)}, the fix {fid} is written for {r['base']}")
    t = sdk_tools()
    javac, java = jdk("javac"), jdk("java")
    src = os.path.join(HERE, fid)
    work = tempfile.mkdtemp(prefix="base-patch-")
    try:
        w = lambda *p: os.path.join(work, *p)  # noqa: E731
        os.makedirs(w("stubs"))
        os.makedirs(w("cls"))
        stubs = sorted(glob.glob(os.path.join(src, "stubs", "**", "*.java"), recursive=True))
        fix = sorted(glob.glob(os.path.join(src, "*.java")))
        if stubs:
            run([javac, "--release", "17", "-encoding", "UTF-8", "-nowarn", "-cp", FRAMEWORK + os.pathsep + t["android.jar"],
                 "-d", w("stubs")] + stubs)
        run([javac, "--release", "17", "-encoding", "UTF-8", "-nowarn", "-cp",
             os.pathsep.join((FRAMEWORK, t["android.jar"], w("stubs"))), "-d", w("cls")] + fix)
        jar_dir(w("stubs"), w("stubs.jar"))
        classes = sorted(glob.glob(w("cls", "**", "*.class"), recursive=True))
        os.makedirs(w("fix"))
        run([t["d8"], "--release", "--min-api", str(API), "--lib", t["android.jar"], "--classpath", FRAMEWORK,
             "--classpath", w("stubs.jar"), "--output", w("fix")] + classes)
        open(w("base.dex"), "wb").write(dex)
        lib = pinned_jar("smali-dexlib2-3.0.10.jar") + os.pathsep + pinned_jar("guava-31.1-android.jar")
        os.makedirs(w("swap"))
        run([javac, "--release", "17", "-nowarn", "-cp", lib, "-d", w("swap"), os.path.join(HERE, "DexSwap.java")])
        print(run([java, "-cp", w("swap") + os.pathsep + lib, "DexSwap", w("base.dex"), w("fix", DEX), w("out.dex"),
                   r["cls"], str(API)]).strip())
        kept, added = compare(t["dexdump"], w("base.dex"), w("out.dex"), r["cls"])
        refs = link(t["dexdump"], w("out.dex"), r["cls"], platform_classes((FRAMEWORK, t["android.jar"])))
        got = sha256_file(w("out.dex"))
        print(f"{fid}: {kept} classes unchanged (dexdump), {len(added)} replaced, {refs} references resolve, "
              f"patched classes.dex {got}")
        if r["out"] != PENDING and got != r["out"]:
            raise Stop(f"patched classes.dex {got}, patches.tsv pins {r['out']} (another toolchain? see README)")
        dst = os.path.join(out_dir, fid)
        os.makedirs(dst, exist_ok=True)
        shutil.copyfile(w("out.dex"), os.path.join(dst, DEX + ".part"))
        os.replace(os.path.join(dst, DEX + ".part"), os.path.join(dst, DEX))
        return os.path.join(dst, DEX), got
    finally:
        shutil.rmtree(work, ignore_errors=True)


# ------------------------------------------------------------------ apk
def dos_time(dt):
    y, mo, d, h, mi, s = dt
    return (h << 11) | (mi << 5) | (s // 2), ((y - 1980) << 9) | (mo << 5) | d


def swap_entry(src, dst, name, data):
    """dst = src with entry name holding data; every other entry is copied raw (same bytes, order, attributes)."""
    with zipfile.ZipFile(src) as z:
        infos = z.infolist()
    if name not in [i.filename for i in infos]:
        raise Stop(f"{src}: no {name}")
    central = []
    with open(src, "rb") as f, open(dst, "wb") as o:
        for i in infos:
            f.seek(i.header_offset)
            h = f.read(30)
            if h[:4] != b"PK\x03\x04":
                raise Stop(f"{src}: bad local header of {i.filename}")
            n, m = struct.unpack("<HH", h[26:30])
            fname = f.read(n)
            f.seek(m, 1)
            flag = i.flag_bits & ~0x08
            if i.filename == name:
                crc, usize = zlib.crc32(data) & 0xFFFFFFFF, len(data)
                if i.compress_type == zipfile.ZIP_STORED:
                    payload = data
                else:
                    c = zlib.compressobj(9, zlib.DEFLATED, -15)
                    payload = c.compress(data) + c.flush()
            else:
                payload = f.read(i.compress_size)
                crc, usize = i.CRC, i.file_size
            t, d = dos_time(i.date_time)
            off = o.tell()
            o.write(struct.pack("<4sHHHHHIIIHH", b"PK\x03\x04", i.extract_version, flag, i.compress_type, t, d, crc,
                                len(payload), usize, len(fname), 0) + fname + payload)
            central.append(struct.pack("<4sHHHHHHIIIHHHHHII", b"PK\x01\x02", (i.create_system << 8) | i.create_version,
                                       i.extract_version, flag, i.compress_type, t, d, crc, len(payload), usize,
                                       len(fname), 0, 0, 0, i.internal_attr, i.external_attr, off) + fname)
        start = o.tell()
        for c in central:
            o.write(c)
        o.write(struct.pack("<4sHHHHIIH", b"PK\x05\x06", 0, 0, len(central), len(central), o.tell() - start, start, 0))
    with zipfile.ZipFile(src) as a, zipfile.ZipFile(dst) as b:
        if [i.filename for i in a.infolist()] != [i.filename for i in b.infolist()]:
            raise Stop(f"{dst}: entries differ from {src}")
        for x, y in zip(a.infolist(), b.infolist()):
            if x.filename != name and (x.CRC, x.file_size, x.compress_type) != (y.CRC, y.file_size, y.compress_type):
                raise Stop(f"{dst}: {x.filename} changed")
        if b.read(name) != data or b.getinfo(name).compress_type != a.getinfo(name).compress_type:
            raise Stop(f"{dst}: {name} not written as asked")


def fixed_dex(r, dex, out_dir):
    """the file of fix r's pinned result on the dex bytes (built when missing)"""
    have = sha256(dex)
    if have != r["base"]:
        raise Stop(f"{r['path']}: classes.dex {have}, the fix {r['id']} is written for {r['base']} (a new base: port the "
                   "fix again or drop the row)")
    p = os.path.join(out_dir, r["id"], DEX)
    if not os.path.isfile(p):
        build_dex(r, dex, out_dir)
    data = open(p, "rb").read()
    if sha256(data) != r["out"]:
        raise Stop(f"{p}: sha256 {sha256(data)}, patches.tsv pins {r['out']} (PENDING: run base_patch.py build)")
    return p


def patched_apk(path, apk, work, dex_dir=None):
    """The base file path (image path) of apk: None without a fix, else the copy with every fix of its chain in work."""
    fixes = chain(path)
    if not fixes:
        return None
    data = apk_dex(apk)
    for r in fixes:
        data = open(fixed_dex(r, data, dex_dir or DEX_DIR), "rb").read()
    out = os.path.join(work, fixes[-1]["id"] + "-" + os.path.basename(path))
    swap_entry(apk, out, DEX, data)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = ap.add_subparsers(dest="cmd", required=True)
    b = sp.add_parser("build")
    b.add_argument("id")
    b.add_argument("apk")
    b.add_argument("--out", default=DEX_DIR)
    a = sp.add_parser("apply")
    a.add_argument("path")
    a.add_argument("apk")
    a.add_argument("out")
    a.add_argument("--dex-dir")
    sp.add_parser("list")
    x = ap.parse_args()
    try:
        if x.cmd == "build":
            build(x.id, x.apk, x.out)
        elif x.cmd == "apply":
            work = tempfile.mkdtemp(prefix="base-patch-")
            try:
                p = patched_apk(x.path, x.apk, work, x.dex_dir)
                if p is None:
                    raise Stop(f"{x.path}: no fix in patches.tsv")
                shutil.move(p, x.out)
            finally:
                shutil.rmtree(work, ignore_errors=True)
            print(f"{x.out}: {x.path} with the patched classes.dex")
        else:
            for r in rows():
                print("\t".join((r["id"], r["path"], r["cls"], r["base"][:16], r["out"][:16])))
    except Stop as e:
        print(f"base_patch: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
