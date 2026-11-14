#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""check_public_play6.py: the check of a public XGIMI Play 6 system image before its SHA256SUMS is signed
(the Play 6 counterpart of devices/goat_elegant/image/check_public_hb.py).

  python3 -B tools/play6/check_public_play6.py IMAGE [--room BYTES] [--no-signers]

IMAGE is lumen-os-play6-<version>-nogms-system.img as it will be published (tools/play6/release_image.sh output).
Reads the ext4 image with debugfs (E2FS=<dir>, Homebrew e2fsprogs on the Mac, or the PATH one on a Linux build
host): one rdump of the whole image into a temp folder (about the image size), never mounts or writes it.
Checks, every failure listed, exit 1 if any:
  - the ext4 magic at 1080 and the size: at most --room bytes (default 1,601,175,552 = 1527 MiB, the least room
    the installer finds for system in super on stock V1.8.127);
  - effective props (system, system_ext, then product build.prop; the last line of a key wins):
    ro.adb.secure=1, persist.sys.usb.config=none, service.adb.tcp.port=5555 (design decision),
    ro.lumen.device=play6, ro.lumen.version = the version of the file name, ro.lumen.version_code =
    major*10000+minor*100+patch of it, ro.lumen.build_id = lumen-play6-<version>-<8 digits>, no ro.lumen.gms;
  - no test-build file (the boot logger, lumen_escape, boot timing, D's experiments), no file named adb_keys, no
    Google file, no OTA updater (Play 6 updates over the cable only: 2026-10-13), no Wi-Fi or Bluetooth
    driver of our own (no AIC file: V1.8.127 drives both chips itself, 2026-11-12);
  - the Lumen system files byte-identical to their repo copies (overlay/play6, overlay/v1), among them the early
    projector driver chain play6_early_mik.rc (every image: the design notes), mode 0644;
  - /system/bin/surfaceflinger is the keystone SurfaceFlinger pinned in devices/dragon_healthy/build.conf (SF_SHA:
    stock sampling), not the base build's own (SF_BASE_SHA, nearest sampling);
  - the screen mirroring receiver (priv-app), its Wi-Fi Direct TV overlay and the AirPlay receiver, whose
    armeabi-v7a library must be stored and page-aligned in the APK (devices/_image/checks/airplay_libs.py);
  - build-time AOT of Lumen Home, the projector app, Lumen Setup and the two receivers: odex + vdex next to each
    APK, compiled 'speed' against this very boot image (bootclasspath-checksums equal to the base's webview.odex);
  - no file with the sha256 of a MediaTek blob of tools/sign/check_image.py (MTK_SHA);
  - rule S1 on every APK of the image: tools/play6/check_signers.py (no Lumen release or AOSP test platform
    certificate left; org.lumen.* only on the Play 6 platform certificate). --no-signers skips it (no SDK).
"""
import hashlib
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GSI = os.path.dirname(os.path.dirname(HERE))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(GSI, "tools", "sign"))
import check_image  # noqa: E402  read only: its MTK_SHA table
sys.path.insert(0, os.path.join(GSI, "tools", "lumen"))
import lumen_checks  # noqa: E402  read only: oat_keys

ROOM = 1601175552
NAME_RE = re.compile(r"^lumen-os-play6-([0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9]+)?)-nogms-system\.img$")
# image path -> repo copy (compose.sh installs each as it is)
SAME = {
    "system/etc/init/play6_compat.rc": "overlay/play6/play6_compat.rc",
    "system/etc/init/play6_base.rc": "overlay/play6/play6_base.rc",
    "system/etc/init/play6_early_mik.rc": "overlay/play6/play6_early_mik.rc",
    "system/etc/init/lumen_gapps.rc": "overlay/play6/lumen_gapps.rc",
    "system/etc/xgimi/lumen_gapps.sh": "overlay/play6/lumen_gapps.sh",
    "system/etc/init/z9x_nui.rc": "overlay/v1/z9x_nui.rc",
    "system/etc/xgimi/z9x_nui.sh": "overlay/v1/z9x_nui.sh",
    "system/product/usr/keylayout/Vendor_000d_Product_3841.kl": "overlay/play6/keylayout/Vendor_000d_Product_3841.kl",
    "system/product/usr/keylayout/Vendor_3697_Product_0001.kl": "overlay/v1/keylayout/Vendor_3697_Product_0001.kl",
    "system/product/usr/keylayout/Vendor_3697_Product_0002.kl": "overlay/v1/keylayout/Vendor_3697_Product_0002.kl",
    "system/product/usr/idc/Vendor_000d_Product_3841.idc": "overlay/v1/idc/Vendor_000d_Product_3841.idc",
    "system/etc/permissions/privapp-permissions-z9xhome.xml": "overlay/v1/privapp-permissions-z9xhome.xml",
    "system/etc/permissions/privapp-permissions-z9xsetup.xml": "overlay/v1/privapp-permissions-z9xsetup.xml",
    "system/etc/default-permissions/z9x-home-default-permissions.xml": "overlay/v1/z9x-home-default-permissions.xml",
    "system/product/etc/default-permissions/z9x-projector.xml": "overlay/v1/z9x-projector-default-permissions.xml",
    "system/etc/sysconfig/z9x.xml": "overlay/v1/z9x-sysconfig-nogms.xml",
    "system/etc/permissions/privapp-permissions-lumen-miracast.xml": "overlay/v1/privapp-permissions-lumen-miracast.xml",
    "system/product/etc/default-permissions/lumen-miracast.xml": "overlay/v1/lumen-miracast-default-permissions.xml",
    "system/etc/default-permissions/z9x-airplay-default-permissions.xml": "overlay/v1/z9x-airplay-default-permissions.xml",
}
MIRACAST = "system/priv-app/LumenMiracast/LumenMiracast.apk"
AIRPLAY = "system/app/Z9xAirPlay/Z9xAirPlay.apk"
RECEIVERS = [MIRACAST, AIRPLAY, "system/product/overlay/LumenWifiP2pOverlay.apk"]
AIRPLAY_LIBS = os.path.join(GSI, "devices", "_image", "checks", "airplay_libs.py")
ABSENT = [
    "system/etc/init/z9x_earlylog.rc", "system/etc/xgimi/z9x_earlylog.sh", "system/etc/xgimi/earlylog_usb.sh",
    "system/etc/init/lumen_escape.rc", "system/etc/xgimi/lumen_escape.sh",
    "system/etc/init/play6_boottiming.rc", "system/etc/xgimi/play6_boottiming.sh",
    "system/product/etc/init/play6_perf.rc",
    "system/app/Play6Updater", "system/app/LumenBridgeUpdater", "system/etc/init/lumen_recovery.rc",
    "system/etc/sysconfig/lumen-gapps.xml",
    "system/product/priv-app/PrebuiltGmsCorePano", "system/system_ext/priv-app/GoogleServicesFramework",
    "system/product/priv-app/Phonesky", "system/product/priv-app/Tubesky",
    "system/etc/aic8800", "system/etc/init/play6_aic.rc", "system/etc/xgimi/play6_aic.sh",
    "system/lib/libbt-vendor-aic.so", "system/etc/bluetooth/aicbt.conf",
]
MODE_0644 = ["system/etc/init/play6_early_mik.rc"]
AOT = ["system/priv-app/Z9xHome/Z9xHome.apk", "system/app/Play6Projector/Play6Projector.apk",
       "system/priv-app/Z9xSetup/Z9xSetup.apk", MIRACAST, AIRPLAY]
REF_ODEX = "system/product/app/webview/oat/arm/webview.odex"
SF = "system/bin/surfaceflinger"
BUILD_CONF = os.path.join(GSI, "devices", "dragon_healthy", "build.conf")

errors = []


def conf_var(name):
    """var.NAME of the Play 6 build descriptor (the one source of the pins the recipe checks too)."""
    for line in open(BUILD_CONF, encoding="utf-8"):
        if line.startswith("var.%s=" % name):
            return line.split("=", 1)[1].strip()
    sys.exit("check_public_play6: no var.%s in %s" % (name, BUILD_CONF))


def sf_problem(got):
    """None when GOT (sha256 of the image's surfaceflinger) is the pinned keystone binary, else the problem."""
    want, base_sf = conf_var("SF_SHA"), conf_var("SF_BASE_SHA")
    if got == base_sf:
        return "/%s is the base build's own (nearest-sampling keystone), not the pinned %s" % (SF, want)
    if got != want:
        return "/%s sha256 %s, want the pinned keystone SurfaceFlinger %s" % (SF, got, want)
    return None


def err(m):
    errors.append(m)
    print("FAIL " + m)


def ok(m):
    print("ok   " + m)


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def debugfs():
    e2 = os.environ.get("E2FS")
    for c in ([os.path.join(e2, "debugfs")] if e2 else []) + ["/opt/homebrew/opt/e2fsprogs/sbin/debugfs"]:
        if os.access(c, os.X_OK):
            return c
    return shutil.which("debugfs") or sys.exit("check_public_play6: needs debugfs (e2fsprogs, or E2FS=<dir>)")


def props(root):
    out = {}
    for f in ("system/build.prop", "system/system_ext/etc/build.prop", "system/product/etc/build.prop"):
        p = os.path.join(root, f)
        if not os.path.isfile(p):
            err("no /" + f)
            continue
        for line in open(p, encoding="utf-8", errors="replace"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k] = v
    return out


def main():
    a = sys.argv[1:]
    room, signers = ROOM, True
    if "--no-signers" in a:
        a.remove("--no-signers")
        signers = False
    if "--room" in a:
        i = a.index("--room")
        room = int(a[i + 1])
        del a[i:i + 2]
    if len(a) != 1:
        sys.exit(__doc__.split("\n\n")[1])
    img = a[0]
    m = NAME_RE.match(os.path.basename(img))
    if not m:
        sys.exit("check_public_play6: %s is not named lumen-os-play6-<version>-nogms-system.img" % os.path.basename(img))
    ver = m.group(1)
    with open(img, "rb") as f:
        f.seek(1080)
        magic = struct.unpack("<H", f.read(2))[0]
    (ok if magic == 0xEF53 else err)("ext4 magic" if magic == 0xEF53 else "no ext4 magic at 1080")
    size = os.path.getsize(img)
    if size > room:
        err("image is %d bytes, more than the %d bytes of room for system" % (size, room))
    else:
        ok("size %d bytes, %d MiB left of %d" % (size, (room - size) // 1048576, room))

    tmp = tempfile.mkdtemp(prefix="p6pub.", dir=os.environ.get("TMPDIR"))
    try:
        root = os.path.join(tmp, "root")
        os.mkdir(root)
        r = subprocess.run([debugfs(), "-R", "rdump / " + root, img], capture_output=True, text=True)
        if not os.path.isfile(os.path.join(root, "system", "build.prop")):
            sys.exit("check_public_play6: debugfs rdump failed: " + r.stderr[-500:])
        # props
        p = props(root)
        base = ver.split("-")[0]
        ma, mi, pa = (int(x) for x in base.split("."))
        want = {"ro.adb.secure": "1", "persist.sys.usb.config": "none", "service.adb.tcp.port": "5555",
                "ro.lumen.device": "play6", "ro.lumen.version": ver,
                "ro.lumen.version_code": str(ma * 10000 + mi * 100 + pa)}
        for k, v in want.items():
            if p.get(k) != v:
                err("prop %s=%r, expected %r" % (k, p.get(k), v))
        if not re.fullmatch(r"lumen-play6-%s-[0-9]{8}" % re.escape(ver), p.get("ro.lumen.build_id", "")):
            err("ro.lumen.build_id=%r is not lumen-play6-%s-<date>" % (p.get("ro.lumen.build_id"), ver))
        if "ro.lumen.gms" in p:
            err("ro.lumen.gms is set: a Google mark in the no-Google image")
        ok("props: %s" % ", ".join("%s=%s" % (k, p.get(k)) for k in list(want) + ["ro.lumen.build_id"]))
        # absent files
        for x in ABSENT:
            if os.path.lexists(os.path.join(root, x)):
                err("/%s is in the image (test build, OTA, Google or AIC file)" % x)
        keys = []
        for d, dirs, files in os.walk(root):
            keys += [os.path.relpath(os.path.join(d, n), root) for n in files if n == "adb_keys"]
        if keys:
            err("adb_keys in the image: " + ", ".join(keys))
        if "ro.lumen.p6.aic" in p:
            err("ro.lumen.p6.aic is set: an AIC mark in the image")
        ok("no test-build, OTA, Google, AIC or adb_keys file (%d paths checked)" % len(ABSENT))
        # Lumen files equal to the repo
        for ip, rp in SAME.items():
            f, g = os.path.join(root, ip), os.path.join(GSI, rp)
            if not os.path.isfile(f):
                err("/%s missing" % ip)
            elif sha(f) != sha(g):
                err("/%s differs from %s" % (ip, rp))
        ok("%d Lumen system files equal to their repo copies" % len(SAME))
        # metadata of the files init reads (compose installs them 0644)
        dbg = debugfs()
        for ip in MODE_0644:
            st = subprocess.run([dbg, "-R", "stat /" + ip, img], capture_output=True, text=True).stdout
            if "Mode:  0644" not in st:
                err("/%s is not mode 0644" % ip)
        ok("mode 0644: " + ", ".join("/" + x for x in MODE_0644))
        # the keystone SurfaceFlinger
        f = os.path.join(root, SF)
        bad = sf_problem(sha(f) if os.path.isfile(f) else "none")
        if bad:
            err(bad)
        else:
            ok("/%s is the pinned keystone SurfaceFlinger (stock sampling)" % SF)
        # screen mirroring and AirPlay
        before = len(errors)
        for x in RECEIVERS:
            if not os.path.isfile(os.path.join(root, x)):
                err("/%s missing (screen mirroring / AirPlay)" % x)
        if os.path.isfile(os.path.join(root, AIRPLAY)):
            r = subprocess.run([sys.executable, "-B", AIRPLAY_LIBS, os.path.join(root, AIRPLAY)], capture_output=True, text=True)
            if r.returncode != 0:
                err("/%s: %s" % (AIRPLAY, (r.stdout + r.stderr).strip()))
        if len(errors) == before:
            ok("receivers: %s, the AirPlay armeabi-v7a library stored and page-aligned" % ", ".join("/" + x for x in RECEIVERS))
        # AOT
        ref = lumen_checks.oat_keys(os.path.join(root, REF_ODEX))
        for apk in AOT:
            d, nm = os.path.dirname(apk), os.path.basename(apk)[:-4]
            od = os.path.join(root, d, "oat", "arm", nm + ".odex")
            vd = os.path.join(root, d, "oat", "arm", nm + ".vdex")
            if not (os.path.isfile(od) and os.path.isfile(vd)):
                err("no odex/vdex for /%s" % apk)
                continue
            k = lumen_checks.oat_keys(od)
            if k.get("compiler-filter") != "speed" or k.get("bootclasspath-checksums") != ref.get("bootclasspath-checksums") \
                    or "dex2oat-cmdline" in k:
                err("/%s odex: filter %r, checksums %r (base %r)" % (apk, k.get("compiler-filter"),
                    k.get("bootclasspath-checksums"), ref.get("bootclasspath-checksums")))
        ok("AOT odex for %d apps against the base boot image" % len(AOT))
        # MTK blobs, file count
        mtk = set(check_image.MTK_SHA)
        nf = 0
        for d, dirs, files in os.walk(root):
            for fn in files:
                f = os.path.join(d, fn)
                if os.path.islink(f):
                    continue
                nf += 1
                if sha(f) in mtk:
                    err("MediaTek blob in the image: /" + os.path.relpath(f, root))
        ok("%d files hashed, no MediaTek blob" % nf)
        # rule S1 on every APK
        if signers:
            apks = []
            for d, dirs, files in os.walk(root):
                apks += [os.path.join(d, fn) for fn in files if fn.endswith(".apk") and not os.path.islink(os.path.join(d, fn))]
            r = subprocess.run([sys.executable, "-B", os.path.join(HERE, "check_signers.py")] + sorted(apks),
                               capture_output=True, text=True)
            tail = (r.stdout + r.stderr).strip().splitlines()[-2:]
            if r.returncode != 0:
                for line in (r.stdout + r.stderr).splitlines():
                    if "FAIL" in line or "ERROR" in line or "RED" in line:
                        print("     " + line)
                err("check_signers.py on %d APKs: exit %d" % (len(apks), r.returncode))
            else:
                ok("rule S1 on %d APKs: %s" % (len(apks), " / ".join(tail)))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print()
    if errors:
        print("check_public_play6: %d problem(s)" % len(errors))
        sys.exit(1)
    print("check_public_play6: OK (%s)" % os.path.basename(img))


if __name__ == "__main__":
    main()
