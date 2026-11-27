#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""services.py: framework fixes carried in the code of the base services.jar (README.md "services.jar").

  services.py build BASE --sys DIR --hostout DIR --boot DIR --profiles DIR [--out DIR]
        the patched services.jar of base BASE (framework.tsv) and its profile, odex, vdex and app image
        -> OUT (default build/base_patch/services/BASE) with the image paths below it
  services.py check BASE ROOT       ROOT (an image tree, or the out dir) holds exactly the pinned patched files
  services.py files BASE            the image paths and pinned sha256 of the patched files

build (builder-llm: docker, the base build's host tools and boot image):
  1. every input is the pinned one (framework.tsv: the six shipped files, the class loader jar, the two profile
     listings of frameworks/base/services, the LineageOS 21 sources of the replaced classes);
  2. dexpreopt proof: profman and dex2oat with the soong command of the base build (services_dexpreopt.sh) on the
     SHIPPED jar must give the shipped .prof, .bprof, odex, vdex and art byte for byte;
  3. source proof: the LineageOS 21 sources compiled here have the methods, references and strings of the shipped
     classes (src_cmp.py; the shipped jar is R8-shrunk, so not byte equal);
  4. the fix: lineage/patches/hdmi/*.patch on those sources, javac + d8, DexSwap into the dex that holds the classes,
     every other class of that dex unchanged (dexdump), the other dex files byte equal, JarLink over the whole jar
     (no reference into the jar dangles), every new platform reference of the fixed classes resolves in the headers;
  5. the jar with only that dex entry changed (stored, 4-byte aligned like the base), then step 2's commands on it;
     every output against its pin.
Tools: JAVAC = the javac of the LineageOS tree (prebuilts/jdk/jdk17, what soong compiled the base with; another one
changes the pins), JAVA_HOME for java, BUILD_TOOLS 36.0.0 (d8, dexdump), docker with ubuntu:22.04.
Exit 0 = OK, 1 = stopped.
"""
import argparse
import glob
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib

import base_patch as bp

HERE = bp.HERE
FIX = "services_hdmi"
OUTERS = ["Lcom/android/server/hdmi/HdmiCecLocalDeviceTv;", "Lcom/android/server/hdmi/RequestArcInitiationAction;"]
NEW = ["Lcom/android/server/hdmi/VendorEarc;"]
PATCH = os.path.join(bp.G, "lineage", "patches", "hdmi", "0001-hdmi-vendor-earc-link.patch")
OUT_DIR = os.path.join(bp.G, "build", "base_patch", "services")
JAR = "system/framework/services.jar"
LOC_JAR = "system/framework/com.android.location.provider.jar"
DOCKER_IMAGE = "ubuntu:22.04"


def bases():
    """{base: {"isa", "gc", "dex2oat", "outroot", "files": {path: (base sha, patched sha)}, "inputs": {name: sha}}}"""
    out = {}
    for r in bp.table("framework.tsv"):
        if r[0] == "base":
            out[r[1]] = {"isa": r[2], "gc": r[3], "dex2oat": r[4], "outroot": r[5], "files": {}, "inputs": {}}
        elif r[0] == "file":
            out[r[1]]["files"][r[2]] = (r[3], r[4])
        elif r[0] == "input":
            out[r[1]]["inputs"][r[2]] = r[3]
        else:
            raise bp.Stop(f"framework.tsv: unknown row {r[0]}")
    return out


def base(name):
    b = bases().get(name)
    if not b:
        raise bp.Stop(f"no base {name} in framework.tsv")
    return b


def need(path, want, what):
    if not os.path.isfile(path):
        raise bp.Stop(f"missing {what}: {path}")
    got = bp.sha256_file(path)
    if want != bp.PENDING and got != want:
        raise bp.Stop(f"{what} {path}: sha256 {got}, pinned {want}")
    return got


def aligned_swap(src, dst, name, data):
    """dst = src with entry name holding data; every entry keeps its bytes, order and attributes, stored entries
    start on a 4-byte boundary (zipalign -p 4: zero padding in the local extra field, as the base build's jar)."""
    with zipfile.ZipFile(src) as z:
        infos = z.infolist()
    if name not in [i.filename for i in infos]:
        raise bp.Stop(f"{src}: no {name}")
    central = []
    with open(src, "rb") as f, open(dst, "wb") as o:
        for i in infos:
            f.seek(i.header_offset)
            h = f.read(30)
            n, m = struct.unpack("<HH", h[26:30])
            fname = f.read(n)
            f.seek(m, 1)
            if i.filename == name:
                if i.compress_type != zipfile.ZIP_STORED:
                    raise bp.Stop(f"{src}: {name} is compressed")
                payload, crc, usize = data, zlib.crc32(data) & 0xFFFFFFFF, len(data)
            else:
                payload, crc, usize = f.read(i.compress_size), i.CRC, i.file_size
            off = o.tell()
            pad = (-(off + 30 + len(fname))) % 4 if i.compress_type == zipfile.ZIP_STORED else 0
            t, d = bp.dos_time(i.date_time)
            flag = i.flag_bits & ~0x08
            o.write(struct.pack("<4sHHHHHIIIHH", b"PK\x03\x04", i.extract_version, flag, i.compress_type, t, d, crc,
                                len(payload), usize, len(fname), pad) + fname + b"\0" * pad + payload)
            central.append(struct.pack("<4sHHHHHHIIIHHHHHII", b"PK\x01\x02", (i.create_system << 8) | i.create_version,
                                       i.extract_version, flag, i.compress_type, t, d, crc, len(payload), usize,
                                       len(fname), 0, 0, 0, i.internal_attr, i.external_attr, off) + fname)
        start = o.tell()
        for c in central:
            o.write(c)
        o.write(struct.pack("<4sHHHHIIH", b"PK\x05\x06", 0, 0, len(central), len(central), o.tell() - start, start, 0))
    with zipfile.ZipFile(src) as a, zipfile.ZipFile(dst) as b:
        if [i.filename for i in a.infolist()] != [i.filename for i in b.infolist()]:
            raise bp.Stop(f"{dst}: entries differ from {src}")
        for x, y in zip(a.infolist(), b.infolist()):
            if x.filename != name and (x.CRC, x.file_size, x.compress_type) != (y.CRC, y.file_size, y.compress_type):
                raise bp.Stop(f"{dst}: {x.filename} changed")
            if y.compress_type == zipfile.ZIP_STORED:
                with open(dst, "rb") as f:
                    f.seek(y.header_offset + 26)
                    n, m = struct.unpack("<HH", f.read(4))
                if (y.header_offset + 30 + n + m) % 4:
                    raise bp.Stop(f"{dst}: {y.filename} not 4-byte aligned")
        if b.read(name) != data:
            raise bp.Stop(f"{dst}: {name} not written as asked")


def dexpreopt(b, sysdir, hostout, boot, profiles, jar, out):
    """services_dexpreopt.sh in the container: out/<the four image files below system/framework>"""
    os.makedirs(out, exist_ok=True)
    loc = os.path.join(sysdir, LOC_JAR.split("/", 1)[1])
    # root in the container: the soong paths below /out are made there; the outputs go back to the caller's user
    cmd = ["docker", "run", "--rm",
           "-v", f"{hostout}:/hostout:ro", "-v", f"{boot}:/boot:ro", "-v", f"{profiles}:/prof:ro",
           "-v", f"{os.path.dirname(os.path.abspath(jar))}:/jar:ro", "-v", f"{loc}:/loc/com.android.location.provider.jar:ro",
           "-v", f"{HERE}:/tools:ro", "-v", f"{os.path.abspath(out)}:/work", DOCKER_IMAGE, "bash", "-c",
           'bash /tools/services_dexpreopt.sh "$@" && chown -R %d:%d /work' % (os.getuid(), os.getgid()), "sh",
           b["isa"], b["gc"], b["dex2oat"], b["outroot"], "/jar/" + os.path.basename(jar), "/work"]
    print(bp.run(cmd).strip().splitlines()[-1])
    return {"system/framework/services.jar.prof": os.path.join(out, "services.jar.prof"),
            "system/framework/services.jar.bprof": os.path.join(out, "services.jar.bprof"),
            f"system/framework/oat/{b['isa']}/services.odex": os.path.join(out, "oat", b["isa"], "services.odex"),
            f"system/framework/oat/{b['isa']}/services.vdex": os.path.join(out, "oat", b["isa"], "services.vdex"),
            f"system/framework/oat/{b['isa']}/services.art": os.path.join(out, "oat", b["isa"], "services.art")}


def compile_dex(srcdir, cp, work, tag):
    t = bp.sdk_tools()
    cls, dx = os.path.join(work, tag + "_cls"), os.path.join(work, tag + "_dex")
    os.makedirs(cls)
    os.makedirs(dx)
    # soong's javac: JDK 17 of the tree (JAVAC=prebuilts/jdk/jdk17/linux-x86/bin/javac), inline string concatenation
    bp.run([os.environ.get("JAVAC") or bp.jdk("javac"), "--release", "17", "-encoding", "UTF-8", "-nowarn", "-proc:none",
            "-XDskipDuplicateBridges=true", "-XDstringConcat=inline", "-cp",
            os.pathsep.join(cp + [t["android.jar"]]), "-d", cls] + sorted(glob.glob(os.path.join(srcdir, "*.java"))))
    args = [t["d8"], "--release", "--min-api", str(bp.API), "--lib", t["android.jar"]]
    for c in cp:
        args += ["--classpath", c]
    bp.run(args + ["--output", dx] + sorted(glob.glob(os.path.join(cls, "**", "*.class"), recursive=True)))
    return os.path.join(dx, "classes.dex")


def d2j(jar, work):
    """the base jar as class files: only the compile class path of the fix (javac inlines its constants)"""
    z = bp.pinned_jar("dex-tools-v2.4.zip")
    tdir = os.path.join(bp.cache_dir(), "dex-tools-v2.4")
    if not os.path.isdir(tdir):
        with zipfile.ZipFile(z) as zz:
            zz.extractall(bp.cache_dir())
    out = os.path.join(work, "services-d2j.jar")
    libs = os.pathsep.join(sorted(glob.glob(os.path.join(tdir, "lib", "*.jar"))))
    bp.run([bp.jdk("java"), "-cp", libs, "com.googlecode.dex2jar.tools.Dex2jarCmd", "-f", "-o", out, jar])
    return out


def dex_classes(dexdump, dex):
    """bp.dex_classes with each call site written out (dexlib2 numbers call sites and method handles again, so
    call_site@N becomes the call site's arguments with every method handle replaced by its target) and const-string
    for const-string/jumbo"""
    text = bp.run([dexdump, "-d", dex])
    handles = {int(m.group(1)): " ".join(m.group(2).split())
               for m in re.finditer(r"^Method handle #(\d+):\n((?:  .*\n)+)", text, re.M)}
    sites = {}
    for m in re.finditer(r"^Call site #(\d+):[^\n]*\n((?:  .*\n)+)", text, re.M):
        body = re.sub(r"(\d+) \(MethodHandle\)", lambda h: "{%s} (MethodHandle)" % handles.get(int(h.group(1)), "?"),
                      m.group(2))
        sites[int(m.group(1))] = " ".join(body.split())
    cut = re.search(r"^(Method handle|Call site) #", text, re.M)
    text = text[:cut.start()] if cut else text  # the tables after the last class, now written into the code
    text = re.sub(r"call_site@([0-9a-f]+)", lambda c: "call_site{%s}" % sites.get(int(c.group(1), 16), "?"), text)
    out = {}
    for block in bp.CLASS_HEAD.split(text)[1:]:
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        for rx, rep in bp.NORMAL:
            block = rx.sub(rep, block)
        # a string whose index the new strings push past 65535 needs the jumbo form: the same string
        out[m.group(1)] = block.replace("|const-string/jumbo ", "|const-string ")
    return out


def build(name, sysdir, hostout, boot, profiles, out_dir):
    b = base(name)
    t = bp.sdk_tools()
    java, javac = bp.jdk("java"), bp.jdk("javac")
    shipped = {p: os.path.join(sysdir, p.split("/", 1)[1]) for p in b["files"]}
    for p, f in shipped.items():
        need(f, b["files"][p][0], "shipped " + p)
    need(os.path.join(sysdir, LOC_JAR.split("/", 1)[1]), b["inputs"]["class_loader_jar"], "class loader jar")
    for f in ("art-profile", "art-profile-boot"):
        need(os.path.join(profiles, f), b["inputs"][f], "profile listing")
    src = os.path.join(HERE, FIX, "lineage21")
    for f in sorted(glob.glob(os.path.join(src, "*.java"))):
        need(f, b["inputs"][os.path.basename(f)], "LineageOS 21 source")
    work = tempfile.mkdtemp(prefix="svcjar-")
    try:
        w = lambda *p: os.path.join(work, *p)  # noqa: E731
        # 2. the dexpreopt commands reproduce the shipped files
        made = dexpreopt(b, sysdir, hostout, boot, profiles, shipped[JAR], w("proof"))
        for p, f in made.items():
            if bp.sha256_file(f) != b["files"][p][0]:
                raise bp.Stop(f"dexpreopt proof: {p} of the shipped jar {bp.sha256_file(f)}, shipped {b['files'][p][0]}")
        print(f"{name}: dexpreopt proof: the soong commands on the shipped jar give the shipped {len(made)} files byte for byte")
        # 3. source = shipped
        with zipfile.ZipFile(shipped[JAR]) as z:
            dexes = sorted((n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")),
                           key=lambda n: int(n[7:-4] or 1))
            for n in dexes:
                open(w(n), "wb").write(z.read(n))
        holder = None
        for n in dexes:
            have = bp.dex_classes(t["dexdump"], w(n))
            if all(o in have for o in OUTERS):
                holder = n
            elif any(o in have for o in OUTERS):
                raise bp.Stop(f"the replaced classes are split over the dex files ({n})")
        if not holder:
            raise bp.Stop("the replaced classes are in no dex of the jar")
        hj = d2j(shipped[JAR], work)
        cp = [hj, bp.FRAMEWORK]
        orig = compile_dex(src, cp, work, "orig")
        outers = [o[len("Lcom/android/server/hdmi/"):-1] for o in OUTERS]
        print(bp.run([sys.executable, os.path.join(HERE, FIX, "src_cmp.py"), t["dexdump"], orig, w(holder)] + outers).strip())
        # 4. the fix
        os.makedirs(w("fixsrc"))
        for f in glob.glob(os.path.join(src, "*.java")):
            shutil.copy(f, w("fixsrc"))
        lay = w("tree", "frameworks", "base", "services", "core", "java", "com", "android", "server", "hdmi")
        os.makedirs(lay)
        for f in glob.glob(os.path.join(src, "*.java")):
            shutil.copy(f, lay)
        bp.run(["patch", "-p1", "-s", "--no-backup-if-mismatch", "-d", w("tree"), "-i", PATCH])
        fixed = compile_dex(lay, cp, work, "fix")
        lib = bp.pinned_jar("smali-dexlib2-3.0.10.jar") + os.pathsep + bp.pinned_jar("guava-31.1-android.jar")
        os.makedirs(w("tool"))
        bp.run([javac, "--release", "17", "-nowarn", "-cp", lib, "-d", w("tool"), os.path.join(HERE, "DexSwap.java"),
                os.path.join(HERE, "JarLink.java")])
        spec = ",".join(OUTERS + ["+" + n for n in NEW])
        print(bp.run([java, "-cp", w("tool") + os.pathsep + lib, "DexSwap", w(holder), fixed, w("new.dex"), spec,
                      str(bp.API)]).strip())
        a, c = dex_classes(t["dexdump"], w(holder)), dex_classes(t["dexdump"], w("new.dex"))
        mine = lambda n: any(bp.swapped(o, n) for o in OUTERS + NEW)  # noqa: E731
        kept = [n for n in a if not mine(n)]
        changed = [n for n in kept if c.get(n) != a[n]]
        extra = [n for n in c if n not in a and not mine(n)]
        if changed or extra:
            first = ""
            if changed:
                import difflib
                first = "\n".join(list(difflib.unified_diff(a[changed[0]].splitlines(), (c.get(changed[0]) or "").splitlines(),
                                                            lineterm="", n=1))[:30])
            raise bp.Stop(f"{holder}: {len(changed)} classes outside the fix changed ({changed[:5]}), {len(extra)} new "
                          f"({extra[:5]})\n{first}")
        print(f"{name}: {holder}: {len(kept)} classes unchanged (dexdump), "
              f"{len([n for n in c if mine(n)])} classes of the fix")
        newdexes = [w("new.dex") if n == holder else w(n) for n in dexes]
        prefixes = ",".join(o[:-1] for o in OUTERS + NEW)
        bp.run([java, "-cp", w("tool") + os.pathsep + lib, "JarLink", str(bp.API), w("out_base.txt"), prefixes]
               + [w(n) for n in dexes])
        print(bp.run([java, "-cp", w("tool") + os.pathsep + lib, "JarLink", str(bp.API), w("out_new.txt"), prefixes]
                     + newdexes).strip())
        before = set(open(w("out_base.txt")).read().splitlines())
        plat = bp.platform_classes((bp.FRAMEWORK, t["android.jar"]))
        bad = []
        for line in sorted(set(open(w("out_new.txt")).read().splitlines()) - before):
            h, nm, ty = line.split(" ")
            if bp.resolve({}, plat, h, (nm, ty)) is None:
                bad.append(line)
        if bad:
            raise bp.Stop(f"{len(bad)} new platform references do not resolve, first {bad[0]}")
        print(f"{name}: {len(set(open(w('out_new.txt')).read().splitlines()) - before)} new platform references resolve")
        # 5. the jar and its dexpreopt files
        dst = os.path.join(out_dir, name)
        shutil.rmtree(dst, ignore_errors=True)
        os.makedirs(os.path.join(dst, "system", "framework"))
        aligned_swap(shipped[JAR], os.path.join(dst, JAR), holder, open(w("new.dex"), "rb").read())
        made = dexpreopt(b, sysdir, hostout, boot, profiles, os.path.join(dst, JAR), w("new"))
        for p, f in made.items():
            os.makedirs(os.path.dirname(os.path.join(dst, p)), exist_ok=True)
            shutil.copyfile(f, os.path.join(dst, p))
        bad = []
        for p, (_, want) in sorted(b["files"].items()):
            got = bp.sha256_file(os.path.join(dst, p))
            print(f"{got}  {p}")
            if want != bp.PENDING and got != want:
                bad.append(f"{p}: {got}, pinned {want}")
        if bad:
            raise bp.Stop("patched files differ from framework.tsv: " + "; ".join(bad))
        print(f"{name}: services.jar fix {FIX} built into {dst}")
    finally:
        shutil.rmtree(work, ignore_errors=True)


def check(name, root):
    b = base(name)
    bad = []
    for p, (_, want) in sorted(b["files"].items()):
        f = os.path.join(root, p)
        got = bp.sha256_file(f) if os.path.isfile(f) else "missing"
        if got != want:
            bad.append(f"{p}: {got}, pinned {want}")
    if bad:
        raise bp.Stop(f"{root}: " + "; ".join(bad))
    print(f"services.py check {name}: {len(b['files'])} files pinned in {root}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = ap.add_subparsers(dest="cmd", required=True)
    b = sp.add_parser("build")
    b.add_argument("base")
    for k in ("sys", "hostout", "boot", "profiles"):
        b.add_argument("--" + k, required=True)
    b.add_argument("--out", default=OUT_DIR)
    c = sp.add_parser("check")
    c.add_argument("base")
    c.add_argument("root")
    f = sp.add_parser("files")
    f.add_argument("base")
    a = ap.parse_args()
    try:
        if a.cmd == "build":
            build(a.base, a.sys, a.hostout, a.boot, a.profiles, a.out)
        elif a.cmd == "check":
            check(a.base, a.root)
        else:
            for p, (_, want) in sorted(base(a.base)["files"].items()):
                print(f"{p}\t{want}")
    except bp.Stop as e:
        print(f"services.py: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
