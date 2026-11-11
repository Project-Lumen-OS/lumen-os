#!/usr/bin/env python3
"""verify_tree_x50.py: the X50 test tree (or image, mounted) against the Z9X 1.0.9 base image (mounted), entry by
entry: type, owner, group, mode (not of symlinks), file content (sha256), link target, SELinux label, file capability. Run as root
by compose_x50.sh (step 7 on the staged tree, step 9 on the mounted image):

  python3 -I verify_tree_x50.py BASE NEW IN V BID

IN is the composer's input folder (remove.txt, x50_prop.txt, x50_model_prop.txt, x50_props.py, x50_netadb.rc,
x50_blobs.sh, x50_blobs.rc, x50_blobs_allow.txt, x50_nui.rc, x50_apk_check.py and stage/, the APKs built and signed
on the Mac).
Allowed differences, and nothing else:
  removed   every path of remove.txt with everything below it;
  changed   system/build.prop and system/product/etc/build.prop (exactly the base files with the edits of
            x50_prop.txt and the lines of x50_model_prop.txt, x50_props.py check), system/etc/security/otacerts.zip
            (a zip without any certificate: no OTA payload verifies on the X50 test image); owner, mode and
            label of all three as in the base;
  added     system/etc/init/x50_netadb.rc, x50_blobs.rc, x50_nui.rc and system/etc/z9x/x50_blobs.sh,
            x50_blobs_allow.txt, each byte-identical to its IN file (root:root, 0644, 0755 for the script,
            system_file), and two 0-byte placeholders the X50 blobs reader binds the user's own files over:
            system/bin/nativeui (root:shell 0755 nativeui_main_exec, the stock vendor_file_contexts label) and
            system/lib64/xgimi.hardware.gmpf-V1-ndk.so (root:root 0644 system_lib_file); the eye-follow fail-safe
            system/app/X50EyeGuard/ (root:root 0755 system_file) with X50EyeGuard.apk (root:root 0644
            system_file), byte-identical to IN/stage/X50EyeGuard.apk.
Then, on NEW: no file has the sha256 of a file of the blob sets (the base's system/etc/z9x/blobs_allow.txt and
IN/x50_blobs_allow.txt, the eye-protection drawer included: the MediaTek / XGIMI files an image never carries); no
APK, jar, dex or ELF names the X50's AIDL gmpf
(xgimi.hardware.gmpf.I..., Lxgimi/hardware/gmpf/I...) or the Z9X HIDL gmpf (xgimi.hardware.gmpf@), with one
exception, HAL_APP below: the X50EyeGuard APK, and only when it is the staged file, signed (v2/v3) by the g0111
platform certificate, names no gmpf interface but DisplayManager, has no native code and no Method.invoke, one
IBinder.transact site, and its call table is exactly the ten calls of HAL_APP (x50_apk_check.py reads the dex);
no Z9X updater APK; no reader of the vendor's native-UI fifo (/data/vendor/tmp/nuififo): the Z9X z9x_nui ACKs without
drawing, and on the X50 the human-shape eye-protection mask is drawn by the stock nativeui, so an ACK-only reader
would tell the vendor the person is masked. The only /system/etc files that may
name the fifo are x50_blobs.sh and x50_nui.rc (checked byte for byte above), which only remove it while no reader
runs. Every ART, dexopt and zygote property of the two build.prop files is the base's, and
ro.dalvik.vm.enable_uffd_gc matches the GC the boot image and services.odex were compiled for (oat key
concurrent-copying: false = the userfaultfd GC, which needs enable_uffd_gc=true). Directory sizes and times are
ignored.
Prints a summary and every violation; exit 0 = OK, 1 = violations.
"""
import hashlib
import os
import re
import stat
import subprocess
import sys
import zipfile

base, new, IN, V, BID = sys.argv[1:6]
bad = []
sys.path.insert(0, IN)
sys.dont_write_bytecode = True   # IN belongs to the build user; this script runs as root
import x50_apk_check  # noqa: E402  (IN/x50_apk_check.py, staged with this script)
import x50_power_keys  # noqa: E402  (IN/x50_power_keys.py, staged with this script)

# The one APK of the X50 image that may name the gmpf HAL (maintainer's decision 2026-11-07, X50 work approved;
# the design notes): the eye-follow fail-safe apps/X50EyeGuard, built and signed on the
# Mac with the g0111 platform key (tools/devices/build_apps.sh g0111 build/g0111/stage) and staged by
# tools/image/compose.sh (stage_dir). "calls" = devices/g0111/research/allowed_x50eyeguard.tsv (service, code,
# method; '-' for the version and hash guard), "cert" = devices/g0111/certs/platform.x509.pem (sha256 of the DER):
# devices/g0111/test/x50_apk_check_test.sh holds both to their sources.
HAL_APP = {
    "path": "system/app/X50EyeGuard/X50EyeGuard.apk",
    "staged": "stage/X50EyeGuard.apk",
    "cert": "3a741e26026295ba510b6135b98294198013e271b8916db50a80a45be6fd76d2",
    "ifaces": ["xgimi.hardware.gmpf.IDisplayManager", "xgimi.hardware.gmpf.IPowerManager",
               "xgimi.hardware.gmpf.ISystemManager"],
    "calls": [["-", 0x00FFFFFF, "getInterfaceVersion"],
              ["-", 0x00FFFFFE, "getInterfaceHash"],
              ["DisplayManager", 127, "getHumanDetectMode"],
              ["DisplayManager", 126, "setHumanDetectMode"],
              ["DisplayManager", 66, "setHumanDetectScreenOnOff"],
              ["DisplayManager", 67, "getHumanDetectScreenOnOff"],
              ["SystemManager", 32, "setBootAnimState"],
              ["SystemManager", 33, "getBootAnimState"],
              ["SystemManager", 36, "setSystemUIState"],
              ["PowerManager", 2, "goToBootCompleted"]],
}


def rel_walk(top):
    out = {}
    for d, dirs, files in os.walk(top):
        for n in dirs + files:
            p = os.path.join(d, n)
            out[os.path.relpath(p, top)] = p
    return out


def xattr(p, name):
    try:
        return os.getxattr(p, name, follow_symlinks=False)
    except OSError:
        return None


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def meta(p, content=True):
    st = os.lstat(p)
    m = {"type": stat.S_IFMT(st.st_mode), "uid": st.st_uid, "gid": st.st_gid, "mode": stat.S_IMODE(st.st_mode),
         "label": xattr(p, "security.selinux"), "cap": xattr(p, "security.capability")}
    if stat.S_ISLNK(st.st_mode):
        # Linux ignores a symlink's permission bits and cannot set them (rsync, ext4 staging): links are compared by
        # target, owner and label only
        del m["mode"]
        m["link"] = os.readlink(p)
    elif stat.S_ISREG(st.st_mode) and content:
        m["sha"] = sha(p)
    return m


def lines(path):
    out = []
    for raw in open(path, encoding="utf-8"):
        s = raw.rstrip("\n")
        if s.strip() and not s.startswith("#"):
            out.append(s.split("\t", 1)[0])
    return out


B = rel_walk(base)
N = rel_walk(new)
REMOVE = lines(os.path.join(IN, "remove.txt"))
removed = set()
for r in REMOVE:
    if r not in B:
        bad.append("remove.txt names %s, which is not in the base" % r)
    removed |= {k for k in B if k == r or k.startswith(r + "/")}
# the X50 power keys: these key layouts are the base's with exactly the edit of x50_power_keys.py (checked below)
KL = "system/product/usr/keylayout"
CHANGED = {"system/build.prop", "system/product/etc/build.prop", "system/etc/security/otacerts.zip"} \
    | {KL + "/" + n for n in x50_power_keys.TABLE}
SYSF = b"u:object_r:system_file:s0\x00"
# path: (uid, gid, mode, label, the IN file it is a copy of, None for a 0-byte placeholder, or DIR for a folder)
DIR = "/"
ADDED = {"system/etc/init/x50_netadb.rc": (0, 0, 0o644, SYSF, "x50_netadb.rc"),
         "system/etc/init/x50_blobs.rc": (0, 0, 0o644, SYSF, "x50_blobs.rc"),
         "system/etc/init/x50_nui.rc": (0, 0, 0o644, SYSF, "x50_nui.rc"),
         "system/etc/z9x/x50_blobs.sh": (0, 0, 0o755, SYSF, "x50_blobs.sh"),
         "system/etc/z9x/x50_blobs_allow.txt": (0, 0, 0o644, SYSF, "x50_blobs_allow.txt"),
         "system/bin/nativeui": (0, 2000, 0o755, b"u:object_r:nativeui_main_exec:s0\x00", None),
         "system/lib64/xgimi.hardware.gmpf-V1-ndk.so": (0, 0, 0o644, b"u:object_r:system_lib_file:s0\x00", None),
         os.path.dirname(HAL_APP["path"]): (0, 0, 0o755, SYSF, DIR),
         HAL_APP["path"]: (0, 0, 0o644, SYSF, HAL_APP["staged"]),
         # the X50 power-key RRO (apps/X50PowerOverlay: resources only, no dex), the staged bytes
         "system/product/overlay/X50PowerOverlay.apk": (0, 0, 0o644, SYSF, "stage/X50PowerOverlay.apk")}
EMPTY = hashlib.sha256(b"").hexdigest()

for k in sorted(removed):
    if k in N:
        bad.append("removed path still there: " + k)
for k in sorted(set(N) - set(B)):
    if k not in ADDED:
        bad.append("added path not allowed: " + k)
for k, (uid, gid, mode, label, src) in ADDED.items():
    if k in B:
        bad.append("added %s is already in the base" % k)
    if k not in N:
        bad.append("missing added file " + k)
        continue
    m = meta(N[k])
    kind = stat.S_IFDIR if src == DIR else stat.S_IFREG
    if (m["type"], m["uid"], m["gid"], m["mode"], m["label"]) != (kind, uid, gid, mode, label):
        bad.append("added %s has %s, not %d:%d %o %r" % (k, m, uid, gid, mode, label))
    if src == DIR:
        continue
    if m.get("sha") != (sha(os.path.join(IN, src)) if src else EMPTY):
        bad.append("added %s is not %s" % (k, "the staged " + src if src else "a 0-byte placeholder"))
same = 0
for k in sorted(set(B) - removed):
    if k not in N:
        bad.append("base path missing: " + k)
        continue
    mb, mn = meta(B[k], k not in CHANGED), meta(N[k], k not in CHANGED)
    if mb != mn:
        diff = sorted(x for x in set(mb) | set(mn) if mb.get(x) != mn.get(x))
        bad.append("differs from the base: %s (%s)" % (k, ", ".join(diff)))
    elif k not in CHANGED:
        same += 1
for k in sorted(CHANGED):
    if k in N and k in B and stat.S_ISREG(os.lstat(N[k]).st_mode) and sha(N[k]) == sha(B[k]):
        bad.append("expected a change, but the file is the base's: " + k)

# the two build.prop files: exactly the edits
r = subprocess.run([sys.executable, "-I", os.path.join(IN, "x50_props.py"), "check", os.path.join(IN, "x50_prop.txt"),
                    os.path.join(IN, "x50_model_prop.txt"), B["system/build.prop"],
                    B["system/product/etc/build.prop"], N["system/build.prop"], N["system/product/etc/build.prop"],
                    V, BID], capture_output=True, text=True)
print(r.stdout.strip())
if r.returncode:
    bad.append("build.prop edits: " + (r.stdout + r.stderr).strip())
props = {}
for raw in open(N["system/product/etc/build.prop"], encoding="utf-8"):
    if "=" in raw and not raw.startswith("#"):
        props.setdefault(raw.split("=", 1)[0], raw.rstrip("\n").split("=", 1)[1])
want = {"ro.lumen.device": "g0111", "ro.lumen.model": "G0111", "ro.lumen.family": "horse_popular",
        "ro.z9x.device": "x50", "ro.z9x.ota.gate": "0", "ro.z9x.rescue": "0", "ro.z9x.version_code": "0",
        "persist.vendor.xgimi.hd_server": "2"}
for k, v in want.items():
    if props.get(k) != v:
        bad.append("product build.prop: %s=%r, not %r" % (k, props.get(k), v))
for k in ("ro.z9x.ota.manifest_url", "ro.product.ab_ota_partitions"):
    if k in props:
        bad.append("product build.prop still has " + k)
if not props.get("ro.z9x.build_id", "").startswith("lumen-x50-"):
    bad.append("ro.z9x.build_id %r is not an X50 build id" % props.get("ro.z9x.build_id"))

# ART (t4): the image keeps the base's ART boot image and system_server odex, compiled
# for the userfaultfd GC (oat key concurrent-copying=false), and ro.dalvik.vm.enable_uffd_gc must say so: on Android
# 14, odrefresh takes the boot image on /system only while that property equals what the kernel offers
# (odr_common.cc CheckBuildUserfaultFdGc; the X50 kernel has CONFIG_USERFAULTFD=y, compose_x50.sh step 0), else it
# compiles the whole boot classpath before zygote at the first boot (Play 6 t4; the vendor's PWM wait). The stock
# X50's false belongs to its own boot image (concurrent-copying=true). So no ART, dexopt or zygote property of the
# two build.prop files may differ from the base (x50_prop.txt and x50_model_prop.txt name none), and the property
# must match the GC every boot image file was compiled for.
ART_KEY = re.compile(r"^(ro\.)?dalvik\.vm\.|^pm\.dexopt\.|^ro\.zygote$|^persist\.sys\.dalvik\.")


def prop_map(path):
    out = {}
    for raw in open(path, encoding="utf-8", errors="replace"):
        s = raw.strip()
        if s and not s.startswith("#") and "=" in s:
            k, v = s.split("=", 1)
            out[k.strip()] = v.strip()   # a later line wins, as in init
    return out


def oat_value(path, key):
    with open(path, "rb") as f:
        data = f.read(1 << 20)   # the oat header and its key-value store are at the start of .rodata
    i = data.find(key + b"\x00")
    if i < 0:
        return None
    j = data.find(b"\x00", i + len(key) + 1)
    return data[i + len(key) + 1:j].decode("ascii", "replace") if j > 0 else None


for f in ("system/build.prop", "system/product/etc/build.prop"):
    pb = {k: v for k, v in prop_map(B[f]).items() if ART_KEY.search(k)}
    pn = {k: v for k, v in prop_map(N[f]).items() if ART_KEY.search(k)}
    if pb != pn:
        bad.append("%s: ART / dexopt / zygote properties differ from the base: %s"
                   % (f, ", ".join("%s %r -> %r" % (k, pb.get(k), pn.get(k)) for k in sorted(set(pb) | set(pn))
                                    if pb.get(k) != pn.get(k))))
uffd = prop_map(N["system/product/etc/build.prop"]).get("ro.dalvik.vm.enable_uffd_gc",
                                                        prop_map(N["system/build.prop"]).get("ro.dalvik.vm.enable_uffd_gc"))
ccs = {}
for f in ("system/framework/arm64/boot.oat", "system/framework/arm/boot.oat", "system/framework/oat/arm64/services.odex"):
    cc = oat_value(N[f], b"concurrent-copying") if f in N else None
    ccs[f] = cc
    if cc not in ("true", "false"):
        bad.append("%s: no concurrent-copying key in its oat header (%r)" % (f, cc))
    elif (cc == "false") != (uffd == "true"):
        bad.append("%s is compiled for the %s GC (concurrent-copying=%s), but ro.dalvik.vm.enable_uffd_gc=%s: odrefresh "
                   "would compile the boot classpath at the first boot" % (f, "userfaultfd" if cc == "false" else
                                                                         "read-barrier", cc, uffd))
print("verify_tree_x50: ART ro.dalvik.vm.enable_uffd_gc=%s, boot image and services.odex concurrent-copying=%s"
      % (uffd, "/".join(sorted({str(v) for v in ccs.values()}))))

# the power keys (devices/g0111/README.md, "Power key"): every user power key is the system POWER again, nothing else
# in the key layouts changed
r = subprocess.run([sys.executable, "-I", os.path.join(IN, "x50_power_keys.py"), "check", B[KL], N[KL]],
                   capture_output=True, text=True)
print(r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "x50_power_keys: no output")
if r.returncode:
    bad.append("key layouts: " + (r.stdout + r.stderr).strip())
try:
    with zipfile.ZipFile(N["system/product/overlay/X50PowerOverlay.apk"]) as z:
        if any(n.endswith(".dex") or n.endswith(".so") for n in z.namelist()):
            bad.append("X50PowerOverlay.apk carries code")
except Exception as e:
    bad.append("X50PowerOverlay.apk: %s" % e)

# otacerts.zip without a certificate
try:
    z = zipfile.ZipFile(N["system/etc/security/otacerts.zip"])
    if z.namelist():
        bad.append("otacerts.zip holds %s" % z.namelist())
except Exception as e:
    bad.append("otacerts.zip: %s" % e)

# the blob sets never in the image: the Z9X one of the base (the X50 stock copies of those files have the same
# sha256, README.md) and the X50 one (the same nine plus the eye-protection drawer and its library)
blob_sha = set()
for allow in (B["system/etc/z9x/blobs_allow.txt"], os.path.join(IN, "x50_blobs_allow.txt")):
    for raw in open(allow, encoding="utf-8"):
        m = re.match(r"^([0-9a-f]{64}) ", raw)
        if m:
            blob_sha.add(m.group(1))
        blob_sha |= set(re.findall(r"from=([0-9a-f]{64})", raw))
if len(blob_sha) < 11:
    bad.append("the two blobs allow-lists give only %d sha256" % len(blob_sha))
for raw in open(os.path.join(IN, "x50_blobs_allow.txt"), encoding="utf-8"):
    if raw.startswith("set=") and not raw.startswith("set=x50-"):
        bad.append("x50_blobs_allow.txt has the set id %s" % raw.strip())
if sum(1 for raw in open(os.path.join(IN, "x50_blobs_allow.txt"), encoding="utf-8")
       if re.match(r"^[0-9a-f]{64} ", raw) and " need=eye" in raw) != 2:
    bad.append("x50_blobs_allow.txt does not have exactly the two need=eye lines (nativeui and its library)")

PAT = [re.compile(rb"xgimi\.hardware\.gmpf\.I[A-Z]"), re.compile(rb"Lxgimi/hardware/gmpf/I[A-Z]"),
       re.compile(rb"xgimi\.hardware\.gmpf@")]
hits = []
nfiles = 0
hal_app_ok = None
for k, p in sorted(N.items()):
    st = os.lstat(p)
    if not stat.S_ISREG(st.st_mode):
        continue
    nfiles += 1
    if st.st_size and sha(p) in blob_sha:
        bad.append("a file of the blob set is in the image: " + k)
    name = k.lower()
    if k == HAL_APP["path"]:
        # the exception: this APK may name the HAL, with exactly HAL_APP's interface, signer and calls (its bytes are
        # the staged file's, checked with ADDED above)
        try:
            rep = x50_apk_check.report(p)
            errs = x50_apk_check.problems(rep, HAL_APP)
        except Exception as e:  # a damaged APK is refused, never skipped
            rep, errs = None, ["cannot be read: %s" % e]
        for e in errs:
            bad.append("%s (the one HAL app): %s" % (k, e))
        hal_app_ok = not errs and rep
        continue
    if name.endswith((".apk", ".jar", ".apex", ".capex")):
        try:
            with zipfile.ZipFile(p) as z:
                for zi in z.infolist():
                    if zi.filename.endswith(".dex") or zi.filename.endswith(".so"):
                        data = z.read(zi)
                        if any(x.search(data) for x in PAT):
                            hits.append("%s!%s" % (k, zi.filename))
        except zipfile.BadZipFile:
            pass
    if st.st_size < (64 << 20):
        with open(p, "rb") as f:
            head = f.read(4)
            if head == b"\x7fELF" or name.endswith((".dex", ".odex", ".vdex", ".oat", ".art", ".so")):
                f.seek(0)
                data = f.read()
                if any(x.search(data) for x in PAT):
                    hits.append(k)
    if os.path.basename(k).startswith("Z9xUpdater"):
        bad.append("a Z9X updater file is in the image: " + k)
for h in hits:
    bad.append("names the gmpf HAL: " + h)
if hal_app_ok:
    print("verify_tree_x50: HAL app %s sha256 %s, signer %s, calls %s" % (
        HAL_APP["path"], hal_app_ok["sha256"], HAL_APP["cert"][:16],
        ", ".join("%s %d %s" % r for r in sorted(hal_app_ok["rows"]))))
elif hal_app_ok is None:
    bad.append("the HAL app %s is not in the image" % HAL_APP["path"])

# the X50 human-shape mask is drawn by the stock nativeui: no reader may answer the vendor's native-UI fifo without
# drawing, so neither the Z9X ACK reader nor anything else that names the
# fifo, except the X50 blobs reader and x50_nui.rc (byte-identical to their inputs, checked above), which only
# remove it while no reader runs
FIFO_OK = {"system/etc/z9x/x50_blobs.sh", "system/etc/init/x50_nui.rc"}
for k in ("system/etc/init/z9x_nui.rc", "system/etc/xgimi/z9x_nui.sh"):
    if k in N:
        bad.append("the Z9X ACK-only fifo reader %s is in the image" % k)
for k, p in sorted(N.items()):
    if k.startswith("system/etc/") and k not in FIFO_OK and stat.S_ISREG(os.lstat(p).st_mode) \
            and os.lstat(p).st_size < (1 << 20):
        if b"nuififo" in open(p, "rb").read():
            bad.append("%s names the native-UI fifo" % k)

print("verify_tree_x50: base %d entries, new %d; removed %d, changed %d, added %d, identical %d; %d files scanned"
      % (len(B), len(N), len(removed), len(CHANGED), len(ADDED), same, nfiles))
for b in bad:
    print("verify_tree_x50: ERROR " + b)
print("verify_tree_x50: %s" % ("OK" if not bad else "FAILED (%d)" % len(bad)))
sys.exit(1 if bad else 0)
