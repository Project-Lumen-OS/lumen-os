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

  python3 -B check_public_x50.py IMAGE --dump dump.erofs

The image file itself (the OTA tools' scanner, devices/<codename>/ota.conf scanner=; tools/devices/ota/ota_conf.py
check_image runs it before a payload or a manifest is made). Reads the erofs image with dump.erofs, never mounts or
writes it. The stock-file scan above needs the dump and ran at compose time; here: the model (ro.lumen.device), the
placeholders, network adb as above, and the update client of a model with updates (2026-11-14): props
ro.lumen.ota.gate=1, ro.lumen.ota.device = its ota.conf wire id, ro.product.ab_ota_partitions = its list,
ro.lumen.ota.manifest_url = update-<wire id>-test.json or -nogms.json on the tag x50 (never the installer's
update-<wire id>-beta.json), ro.lumen.version with its O1 ro.lumen.version_code; otacerts.zip = what
tools/devices/ota/make_otacerts.py builds for the model; the gate files byte-identical to tools/devices/ota/image and
devices/g0111/image/ota_gate.conf; no Z9X gate; the updater APK.
"""
import hashlib
import os
import re
import subprocess
import sys
import tempfile
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


# ---- image mode (the OTA tools' scanner)
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
URL = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/x50/update-%s-%s.json"
GATE = {"/system/etc/lumen/lumen_ota.sh": "tools/devices/ota/image/lumen_ota.sh",
        "/system/etc/init/lumen_ota.rc": "tools/devices/ota/image/lumen_ota.rc",
        "/system/etc/init/update_verifier.rc": "tools/devices/ota/image/lumen_update_verifier.rc",
        "/system/etc/lumen/ota_gate.conf": "devices/g0111/image/ota_gate.conf"}
ADB = ("/system/etc/init/x50_netadb.rc", "/adb_keys", "/system/etc/adb_keys", "/system/product/etc/security/adb_keys")


def cat(dump, img, path):
    """the bytes of PATH in the erofs image, None when it is not there"""
    r = subprocess.run([dump, "--cat", "--path=" + path, img], capture_output=True)
    return r.stdout if r.returncode == 0 and not r.stderr.strip().startswith(b"<E>") else None


def props_of(*texts):
    out = {}
    for t in texts:
        for ln in (t or b"").decode("utf-8", "replace").splitlines():
            ln = ln.strip()
            if ln and not ln.startswith("#") and "=" in ln:
                k, v = ln.split("=", 1)
                out[k] = v   # the last line of a key wins, as in init
    return out


def image_mode(img, dump):
    sys.path.insert(0, os.path.join(REPO, "tools", "devices", "ota"))
    import ota_conf  # noqa: E402
    bad = []
    sp, pp = cat(dump, img, "/system/build.prop"), cat(dump, img, "/system/product/etc/build.prop")
    if sp is None or pp is None:
        print("check_public_x50: BLOCK %s: no /system/build.prop or /system/product/etc/build.prop" % img)
        return 1
    p = props_of(sp, cat(dump, img, "/system/system_ext/etc/build.prop"), pp)
    c = p.get("ro.lumen.device", "")
    if not re.fullmatch(r"[gh][0-9]{4}", c) or not os.path.isfile(os.path.join(REPO, "devices", c, "build.conf")):
        bad.append("ro.lumen.device=%r is no X50 family model" % c)
    for ph in PLACEHOLDERS:
        b = cat(dump, img, "/" + ph)
        if b is None:
            bad.append("placeholder %s missing" % ph)
        elif b:
            bad.append("%s is not a 0-byte placeholder (%d bytes)" % (ph, len(b)))
    for a in ADB:
        if cat(dump, img, a) is not None:
            bad.append("network / preset adb: " + a)
    for k in ("service.adb.tcp.port", "persist.adb.tcp.port"):
        if k in p:
            bad.append("%s=%s" % (k, p[k]))
    if p.get("ro.adb.secure") == "0":
        bad.append("ro.adb.secure=0")
    if p.get("ro.lumen.ota.gate") == "1" and not bad:
        try:
            dev = ota_conf.device(REPO, c)
        except ota_conf.OtaError as e:
            bad.append("ota: %s" % e)
            dev = None
        if dev:
            o = dev["ota"]
            for k, v in (("ro.lumen.ota.device", o["wire_id"]), ("ro.product.ab_ota_partitions", ",".join(o["ab_ota_partitions"]))):
                if p.get(k) != v:
                    bad.append("ota: %s=%r, not %r" % (k, p.get(k), v))
            if p.get("ro.lumen.ota.manifest_url") not in (URL % (o["wire_id"], "test"), URL % (o["wire_id"], "nogms")):
                bad.append("ota: ro.lumen.ota.manifest_url=%r is not the updater's pair on the tag x50"
                           % p.get("ro.lumen.ota.manifest_url"))
            try:
                vc = ota_conf.version_code(p.get("ro.lumen.version", ""))
                if p.get("ro.lumen.version_code") != str(vc):
                    bad.append("ota: ro.lumen.version_code=%r, %s needs %d" % (p.get("ro.lumen.version_code"), p.get("ro.lumen.version"), vc))
            except ota_conf.OtaError as e:
                bad.append("ota: %s" % e)
            for path, rel in sorted(GATE.items()):
                b = cat(dump, img, path)
                if b is None or b != open(os.path.join(REPO, rel), "rb").read():
                    bad.append("ota: %s is not %s" % (path, rel))
            for path in ("/system/etc/init/z9x_ota.rc", "/system/etc/z9x/z9x_ota.sh"):
                if cat(dump, img, path) is not None:
                    bad.append("ota: the Z9X gate %s is in the image" % path)
            apk = cat(dump, img, "/system/app/LumenUpdater/LumenUpdater.apk")
            if not apk or apk[:2] != b"PK":
                bad.append("ota: no updater APK /system/app/LumenUpdater/LumenUpdater.apk")
            z = cat(dump, img, "/system/etc/security/otacerts.zip")
            with tempfile.NamedTemporaryFile(suffix=".zip") as t:
                t.write(z or b"")
                t.flush()
                r = subprocess.run([sys.executable, "-B", os.path.join(REPO, "tools", "devices", "ota", "make_otacerts.py"),
                                    "--root", REPO, "--device", c, "--check", t.name], capture_output=True, text=True)
            if r.returncode:
                bad.append("ota: otacerts.zip is not what make_otacerts.py builds for %s" % c)
            if not bad:
                print("check_public_x50: updates of %s: wire id %s, %s, version code %s, gate and otacerts as the repo"
                      % (c, o["wire_id"], p.get("ro.lumen.ota.manifest_url"), p.get("ro.lumen.version_code")))
    elif p.get("ro.lumen.ota.gate") != "1":
        for k in [k for k in p if k.startswith("ro.lumen.ota.")]:
            bad.append("an image without the gate has %s" % k)
    for b in bad:
        print("check_public_x50: BLOCK " + b)
    print("check_public_x50: image %s: %s" % (os.path.basename(img), "OK" if not bad else "BLOCKED"))
    return 1 if bad else 0


def main(a):
    if len(a) == 3 and a[1] == "--dump":
        return image_mode(a[0], a[2])
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
