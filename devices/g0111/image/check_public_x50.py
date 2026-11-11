#!/usr/bin/env python3
"""check_public_x50.py: may this X50 family system image be published? (2026-11-11, the public X50 beta)

  sudo python3 -I check_public_x50.py IMAGE_ROOT BASE_ROOT DUMP_FS [CACHE]

IMAGE_ROOT  the composed image, mounted read-only (or its staged tree)
BASE_ROOT   the pinned base (the published Lumen OS 1.0.9 Z9X image, already public), mounted read-only
DUMP_FS     the stock X50 dump's file trees (~/devices/g0111_mt9681/work/fs: system_root, vendor, odm, product,
            system_ext, mnt): every XGIMI file and every entry of the stock apps there is "XGIMI"
CACHE       optional file for the sha256 list of DUMP_FS (made once, reused when it exists)

Blocks (exit 1):
  - a file of the image that the base does not hold byte for byte whose sha256 is a stock file or a stock app entry
    of at least 64 bytes (an XGIMI binary, library, config, picture or sound copied in), or an entry of such size
    inside an added or changed APK / JAR / zip with such a sha256; bytes the public base already carries anywhere (a
    file or a zip entry of Lumen OS 1.0.9: AOSP files that XGIMI's own build also has) are already public and pass
  - the eye-protection drawer and the gmpf NDK library not being 0-byte placeholders (the user's own projector gives
    them at run time)
  - network adb: x50_netadb.rc, any adb_keys, service.adb.tcp.port / persist.adb.tcp.port, ro.adb.secure=0
Read only. Prints one line per finding and a summary.
"""
import hashlib
import os
import sys
import zipfile

MIN = 64
PLACEHOLDERS = ("system/bin/nativeui", "system/lib64/xgimi.hardware.gmpf-V1-ndk.so")


def sha_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def files(root):
    out = {}
    for d, dirs, fs in os.walk(root):
        for f in fs:
            p = os.path.join(d, f)
            if os.path.islink(p) or not os.path.isfile(p):
                continue
            out[os.path.relpath(p, root)] = p
    return out


def zip_entries(p):
    try:
        with zipfile.ZipFile(p) as z:
            for i in z.infolist():
                if i.file_size >= MIN and not i.is_dir():
                    yield i.filename, sha_bytes(z.read(i))
    except (zipfile.BadZipFile, OSError, RuntimeError, NotImplementedError):
        return


def stock_set(dump, cache):
    if cache and os.path.isfile(cache):
        return {l.split()[0] for l in open(cache) if l.strip()}
    s, lines = set(), []
    for sub in ("system_root", "vendor", "odm", "product", "system_ext", "mnt"):
        top = os.path.join(dump, sub)
        if not os.path.isdir(top):
            continue
        for rel, p in files(top).items():
            if os.path.getsize(p) >= MIN:
                h = sha_file(p)
                s.add(h)
                lines.append(h + " " + sub + "/" + rel)
            if p.endswith((".apk", ".jar", ".apex", ".zip")):
                for n, h in zip_entries(p):
                    s.add(h)
                    lines.append(h + " " + sub + "/" + rel + "!" + n)
    if cache:
        with open(cache, "w") as o:
            o.write("\n".join(lines) + "\n")
    return s


def main(a):
    if len(a) < 3:
        print(__doc__)
        return 2
    img, base, dump = a[0], a[1], a[2]
    cache = a[3] if len(a) > 3 else None
    stock = stock_set(dump, cache)
    I, B = files(img), files(base)
    public = set()
    for rel, p in B.items():
        public.add(sha_file(p))
        if rel.endswith((".apk", ".jar", ".apex", ".zip")):
            public.update(h for _, h in zip_entries(p))
    stock_new = stock - public
    bad, new = [], []
    for rel, p in sorted(I.items()):
        h = sha_file(p)
        if rel in B and sha_file(B[rel]) == h:
            continue
        new.append(rel)
        if os.path.getsize(p) >= MIN and h in stock_new:
            bad.append("XGIMI file: %s (sha256 %s is a stock file)" % (rel, h[:16]))
        if rel.endswith((".apk", ".jar", ".apex", ".zip")):
            for n, eh in zip_entries(p):
                if eh in stock_new:
                    bad.append("XGIMI file inside %s: %s (sha256 %s)" % (rel, n, eh[:16]))
    for ph in PLACEHOLDERS:
        if ph not in I:
            bad.append("placeholder %s missing" % ph)
        elif os.path.getsize(I[ph]) != 0:
            bad.append("%s is not a 0-byte placeholder (%d bytes)" % (ph, os.path.getsize(I[ph])))
    for rel in I:
        if rel.endswith("adb_keys") or rel.endswith("x50_netadb.rc"):
            bad.append("network / preset adb: " + rel)
    for bp in ("system/build.prop", "system/product/etc/build.prop"):
        if bp in I:
            for ln in open(I[bp], encoding="utf-8", errors="replace"):
                ln = ln.strip()
                if ln.startswith(("service.adb.tcp.port=", "persist.adb.tcp.port=")) or ln == "ro.adb.secure=0":
                    bad.append("%s: %s" % (bp, ln))
    for b in bad:
        print("check_public_x50: BLOCK " + b)
    print("check_public_x50: %d files, %d not byte-equal to the public base, %d stock hashes (%d not in the public base), "
          "%d blocked" % (len(I), len(new), len(stock), len(stock_new), len(bad)))
    print("check_public_x50: " + ("OK" if not bad else "BLOCKED"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
