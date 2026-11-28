#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""verify_rs20.py: an RS 20 trial tree (or image, mounted) against the Z9X 1.0.9 base image (mounted), entry by entry.
Run as root by the recipe (devices/_family/rs20/image/steps.tsv) on the staged tree and on the mounted image:

  python3 -I verify_rs20.py BASE NEW IN V BID CODENAME MODEL NAME TAGP

No model is named here: the model is its build descriptor. IN is the composer's input folder (the recipe's files flat,
stage/ with the APKs built and signed on the Mac, stage/base_signed with the base's platform set on the model's keys).
Content-addressed: every file that is new or differs from the base must be byte-identical to a file of IN (so it came
from the recipe), with these exceptions only: the two build.prop files (exactly the base files with the edits of the
prop file and the model prop lines, then the one first-start setting: x50_props.py check), system/app/<prebuilt>
(tools/build/prebuilt.py pins each file) and the emptied otacerts.zip. Removed: the paths of remove.txt, the oat folders
of the replaced apps and the two Wi-Fi jar dexopt files, the base files base_signed.tsv replaces. Signatures (rule S1):
every base file of base_signed.tsv (the base's platform set on the Z9X release certificate) is gone or replaced, so no
APK or APEX on the Z9X platform certificate stays. Never: an updater, the install-from-trial app, a test adb key or an
OTA prop; the identity props name this model. Owner, mode and SELinux label of a file kept at its base path stay the
base's. Prints a summary and every violation; exit 0 = OK, 1 = violations.
"""
import hashlib
import os
import stat
import subprocess
import sys

BASE, NEW, IN, V, BID, CODENAME, MODEL, NAME, TAGP = sys.argv[1:10]
PROPS = ("system/build.prop", "system/product/etc/build.prop")
BANNED = ("Z9xUpdater", "LumenUpdater", "LumenDsuInstall", "dsu_test", "testadb", "adb_keys", "lumen_dsu_install",
          "lumen_ota")
bad = []


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def label(p):
    try:
        return os.getxattr(p, "security.selinux", follow_symlinks=False).rstrip(b"\0").decode()
    except OSError:
        return ""


def walk(root):
    out = {}
    for d, dirs, files in os.walk(os.path.join(root, "system")):
        for n in dirs + files:
            p = os.path.join(d, n)
            out[os.path.relpath(p, root)] = os.lstat(p)
    return out


b, n = walk(BASE), walk(NEW)
ins = set()
for d, _, files in os.walk(IN):
    for f in files:
        p = os.path.join(d, f)
        if os.path.isfile(p) and not os.path.islink(p):
            ins.add(sha(p))
remove = [ln.split("\t", 1)[0].strip() for ln in open(os.path.join(IN, "remove.txt"), encoding="utf-8")
          if ln.strip() and not ln.startswith("#")]
signed = [ln.rstrip("\n").split("\t") for ln in open(os.path.join(IN, "stage/base_signed/base_signed.tsv"), encoding="utf-8")
          if ln.strip() and not ln.startswith("#")]
replaced = {r[4] for r in signed if len(r) > 4}
prebuilt = {"system/app/LumenPlay"}


def under(p, roots):
    return any(p == r or p.startswith(r + "/") for r in roots)


removed = sorted(p for p in b if p not in n)
for p in removed:
    app = p.split("/oat", 1)[0] if "/oat" in p else None
    if under(p, remove) or p in replaced or app and app in n and (p.endswith("/oat") or "/oat/" in p):
        continue
    bad.append("removed but not allowed: " + p)
for p in remove:
    if p in n:
        bad.append("remove.txt path still there: " + p)
changed = added = 0
for p, st in sorted(n.items()):
    base = b.get(p)
    if any(w in p for w in BANNED):
        bad.append("banned name: " + p)
    if stat.S_ISDIR(st.st_mode):
        if base is None:
            added += 1
            if (st.st_uid, st.st_gid) != (0, 0) or not label(os.path.join(NEW, p)):
                bad.append("new folder owner or label: " + p)
        continue
    if stat.S_ISLNK(st.st_mode):
        if base is None or not stat.S_ISLNK(base.st_mode) or os.readlink(os.path.join(NEW, p)) != os.readlink(os.path.join(BASE, p)):
            bad.append("symlink new or changed: " + p)
        continue
    np = os.path.join(NEW, p)
    if base is not None and stat.S_ISREG(base.st_mode) and base.st_size == st.st_size and sha(np) == sha(os.path.join(BASE, p)):
        continue
    if base is None:
        added += 1
    else:
        changed += 1
        if (st.st_uid, st.st_gid, stat.S_IMODE(st.st_mode)) != (base.st_uid, base.st_gid, stat.S_IMODE(base.st_mode)) \
                and not p.endswith(".sh"):
            bad.append("owner or mode differs from the base: " + p)
        if label(np) != label(os.path.join(BASE, p)):
            bad.append("label differs from the base: " + p)
    if p in PROPS or under(p, prebuilt) or p == "system/etc/security/otacerts.zip":
        continue
    if sha(np) not in ins:
        bad.append("file not from the inputs: " + p)
    if (st.st_uid, st.st_gid) != (0, 0) or not label(np):
        bad.append("owner or label: " + p)
for r in signed:
    path, base_sha, new_sha = r[0], r[2], r[3]
    if path in n and stat.S_ISREG(n[path].st_mode) and sha(os.path.join(NEW, path)) == base_sha:
        bad.append("still on the Z9X platform certificate: " + path)
z = os.path.join(NEW, "system/etc/security/otacerts.zip")
if os.path.exists(z) and os.path.getsize(z) > 22:
    bad.append("otacerts.zip is not empty")
pp = open(os.path.join(NEW, PROPS[1]), encoding="utf-8").read().splitlines()
sp = open(os.path.join(NEW, PROPS[0]), encoding="utf-8").read().splitlines()
for want in ("ro.lumen.device=" + CODENAME, "ro.lumen.model=" + MODEL, "ro.lumen.models=" + MODEL,
             "ro.lumen.version=%s-%s" % (TAGP, V), "ro.lumen.dsu_install=0", "ro.product.product.model=" + NAME):
    if want not in pp:
        bad.append("product prop missing: " + want)
for ln in pp + sp:
    k = ln.split("=", 1)[0]
    if k.startswith("ro.lumen.ota.") or k in ("ro.z9x.ota.manifest_url", "persist.adb.tcp.port", "service.adb.tcp.port") \
            or ln in ("ro.adb.secure=0",) or ln.startswith("persist.sys.usb.config=") and "adb" in ln:
        bad.append("prop not allowed: " + ln)
r = subprocess.run([sys.executable, "-I", os.path.join(IN, "x50_props.py"), "check", os.path.join(IN, "rs20_prop.txt"),
                    os.path.join(IN, CODENAME + "_model_prop.txt"), os.path.join(BASE, PROPS[0]), os.path.join(BASE, PROPS[1]),
                    os.path.join(NEW, PROPS[0]), os.path.join(NEW, PROPS[1]), V, BID, os.path.join(IN, "first_start_prop.txt"),
                    "NAME=" + NAME, "TAGP=" + TAGP, "CODENAME=" + CODENAME, "MODEL=" + MODEL], capture_output=True, text=True)
if r.returncode != 0:
    bad.append("x50_props.py check: " + (r.stdout + r.stderr).strip()[-600:])
print("verify_rs20 %s %s: base %d entries, new %d, removed %d, changed %d, added %d, re-signed rows %d" %
      (CODENAME, MODEL, len(b), len(n), len(removed), changed, added, len(signed)))
for x in bad:
    print("FAIL " + x)
print("VERIFY_RS20_OK" if not bad else "VERIFY_RS20_FAILED %d" % len(bad))
sys.exit(1 if bad else 0)
