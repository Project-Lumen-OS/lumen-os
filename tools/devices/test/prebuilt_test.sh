#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# prebuilt_test.sh: apps that enter the images as their published APK (tools/build/prebuilt.py,
# devices/_image/prebuilt.tsv): the table, the folder an app gets (tree and tar), the staging and the image checks'
# view of it, and that every model's recipe ships Lumen Play. With the real APKs in build/prebuilt (prebuilt.py
# fetch) also their pins and libraries.
#   bash tools/devices/test/prebuilt_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/prebuilt_test.XXXXXX") || exit 2
trap 'rm -rf "$W"' EXIT
python3 -B - "$G" "$W" <<'PY'
import hashlib, io, os, shutil, subprocess, sys, tarfile, zipfile
G, W = sys.argv[1], sys.argv[2]
sys.path.insert(0, os.path.join(G, "tools", "build"))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import prebuilt, engine, layered
bad = 0


def ok(cond, what):
    global bad
    print(("ok   " if cond else "FAIL ") + what)
    bad += 0 if cond else 1


def refused(fn, what):
    try:
        fn()
    except (prebuilt.PrebuiltError, engine.BuildError):
        ok(True, what)
        return
    ok(False, what + " (accepted)")


# ---- the real table
rows = prebuilt.load(os.path.join(G, "devices", "_image", "prebuilt.tsv"))
ok(sorted((r["name"], r["abi"]) for r in rows) == [("LumenPlay", "arm32"), ("LumenPlay", "arm64")],
   "the table holds Lumen Play for arm32 and arm64")
ok(all(r["package"] == "com.xgimiru.player" and r["signer"] == "39ab3b9e6436929ef3663681a8e10e78b429cbac53e93bd9e904cb74be4772a0"
       for r in rows), "Lumen Play is pinned to its release key 39ab3b9e (the lumen-files-access pin)")
pol = open(os.path.join(G, "apps/core/lumen-files-access/src/org/lumen/filesaccess/FilesAccessPolicy.java")).read()
ok(all('{"%s", "%s"}' % (r["package"], r["signer"]) in pol for r in rows), "FilesAccessPolicy pins the same key")
ok(all(r["url"].startswith("https://github.com/kmuradoff/kino-player/releases/download/") for r in rows),
   "the files come from the Lumen Play releases")
ok(subprocess.run([sys.executable, "-B", os.path.join(G, "tools/build/prebuilt.py"), "check"]).returncode == 0,
   "prebuilt.py check: every notice is in the tree")
notice = open(os.path.join(G, rows[0]["notice"])).read()
ok(all(w in notice for w in ("https://github.com/kmuradoff/kino-player/tree/v2.0", "torrserver-MatriX.146-src.tar.gz",
                             "GNU GENERAL PUBLIC LICENSE", "GNU LESSER GENERAL PUBLIC LICENSE", "Apache License")),
   "the notice names the source of this build and carries the GPL-3.0, LGPL-2.1 and Apache-2.0 texts")

# ---- every model ships it: build.conf prebuilt= and a step with the device's abi (build.py check refuses less)
abi = {}
for c in sorted(os.listdir(os.path.join(G, "devices"))):
    dc = os.path.join(G, "devices", c, "device.conf")
    if os.path.isfile(dc) and layered.effective(dc).get("image") != "none":   # image=none: no recipe yet
        abi[c] = layered.effective(dc)["abi"]
for c, a in sorted(abi.items()):
    conf = layered.text(os.path.join(G, "devices", c, "build.conf")).split("\n")
    steps = next(l.split("=", 1)[1] for l in conf if l.startswith("steps="))
    rows_ = [l.split("\t") for l in open(os.path.join(G, steps)).read().split("\n") if l and not l.startswith("#")]
    has = [r for r in rows_ if len(r) > 3 and r[1] in ("prebuilt", "tar_prebuilt") and r[2] == "LumenPlay" and r[3] == a]
    ok("prebuilt=LumenPlay" in conf and len(has) == 1, "%s (%s): build.conf prebuilt=LumenPlay and one %s step" % (c, a, steps))

# ---- a fake app: table, APK with libraries, notice, staging folder
def apk(path, members):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"m")
        for n, b in members.items():
            z.writestr(n, b)
    return path


st = os.path.join(W, "in")
os.makedirs(st)
A = apk(os.path.join(st, "fake-arm64.apk"), {"lib/arm64-v8a/liba.so": b"A" * 100, "lib/arm64-v8a/libb.so": b"B" * 7})
open(os.path.join(st, "Fake.NOTICE.txt"), "w").write("notice\n")
sha = prebuilt.sha256(A)
row = "Fake\tarm64\tcom.example.fake\t3\tfake-arm64.apk\t%d\t%s\t%s\thttps://example.org/r/fake-arm64.apk\tdevices/_image/prebuilt/Fake.NOTICE.txt" % (
    os.path.getsize(A), sha, "0" * 64)
open(os.path.join(st, "prebuilt.tsv"), "w").write("# test\n" + row + "\n")
open(os.path.join(st, "build.conf"), "w").write("codename=x\nprebuilt=Fake\n")
r = prebuilt.load(os.path.join(st, "prebuilt.tsv"))[0]
lay = prebuilt.layout(r, A, os.path.join(st, "Fake.NOTICE.txt"))
ok([e[0] for e in lay] == [".", "Fake.apk", "NOTICE.txt", "lib", "lib/arm64", "lib/arm64/liba.so", "lib/arm64/libb.so"],
   "layout: the APK, its notice, lib/<isa> with every library of the APK's ABI")
ok(dict((e[0], e[3]) for e in lay)["lib/arm64/liba.so"] == hashlib.sha256(b"A" * 100).hexdigest(),
   "layout: an unpacked library is pinned by the sha256 of its bytes in the APK")
ok({e[0]: e[2] for e in lay if e[0].startswith("lib/") and e[1] == "file"} == {"lib/arm64/liba.so": 0o755, "lib/arm64/libb.so": 0o755}
   and {e[0]: e[2] for e in lay if e[0] in ("Fake.apk", "NOTICE.txt")} == {"Fake.apk": 0o644, "NOTICE.txt": 0o644},
   "layout: libraries are 0755 as Android unpacks them into /data (Lumen Play runs lib/<isa>/libtorrserver.so), the APK and notice 0644")
exp = prebuilt.expected(st)
ok(set(exp) == {"system/app/Fake" + ("" if e[0] == "." else "/" + e[0]) for e in lay} and exp["system/app/Fake/Fake.apk"][2] == sha,
   "expected(): the image checks' view of the staged app below system/app")
refused(lambda: prebuilt.libs(r, apk(os.path.join(W, "x.apk"), {"lib/armeabi-v7a/liba.so": b"x"})),
        "an APK with libraries of another ABI is refused")
refused(lambda: prebuilt.libs(r, apk(os.path.join(W, "y.apk"), {})), "an APK without libraries is refused")
for what, line in (("a short sha256", row.replace(sha, sha[:63])), ("an http url", row.replace("https://", "http://")),
                   ("an unknown abi", row.replace("\tarm64\t", "\tx86\t", 1)), ("a url of another file", row.replace("/fake-arm64.apk\t", "/other.apk\t"))):
    open(os.path.join(W, "bad.tsv"), "w").write(line + "\n")
    refused(lambda: prebuilt.load(os.path.join(W, "bad.tsv")), "table: %s is refused" % what)
open(os.path.join(W, "dup.tsv"), "w").write(row + "\n" + row + "\n")
refused(lambda: prebuilt.load(os.path.join(W, "dup.tsv")), "table: two rows of one name and abi are refused")

# ---- the tree folder (as a normal user: no owner, no label)
dst = os.path.join(W, "sys", "app", "Fake")
os.makedirs(os.path.dirname(dst))
prebuilt.install(r, A, os.path.join(st, "Fake.NOTICE.txt"), dst, owner=None, label=None)
got = {}
for d, ds, fs in os.walk(dst):
    for n in ds + fs:
        p = os.path.join(d, n)
        got[os.path.relpath(p, dst)] = (oct(os.stat(p).st_mode & 0o777), None if os.path.isdir(p) else prebuilt.sha256(p))
want = {e[0]: (oct(e[2]), e[3]) for e in lay if e[0] != "."}
ok(got == want, "install: the folder is exactly the layout (modes, bytes)")
refused(lambda: prebuilt.install(r, A, os.path.join(st, "Fake.NOTICE.txt"), dst, owner=None, label=None),
        "install: an existing folder is never overwritten")

# ---- the engine: a tampered APK stops both ops, tar_prebuilt adds the folder through patch_tar.py
class D: raw = {"root": "user", "pack": "erofs"}
class V: d = D(); vals = {"t": W}
e = engine.Engine(V(), st)
e.op_tar_prebuilt("Fake", "arm64", "system/app/Fake")
base = os.path.join(W, "base.tar")
with tarfile.open(base, "w", format=tarfile.PAX_FORMAT) as t:
    for n in ("system", "system/app"):
        i = tarfile.TarInfo(n); i.type = tarfile.DIRTYPE; i.mode = 0o755
        i.pax_headers = {"SCHILY.xattr.security.selinux": "u:object_r:system_file:s0\x00"}
        t.addfile(i)
out = os.path.join(W, "out.tar")
rc = subprocess.run([sys.executable, os.path.join(G, "tools/patch_tar.py"), base, out] + e.tar_args).returncode
mem = {}
if rc == 0:
    with tarfile.open(out) as t:
        for m in t.getmembers():
            if m.name.startswith("system/app/Fake"):
                b = t.extractfile(m).read() if m.isfile() else None
                mem[m.name[len("system/app/Fake"):].lstrip("/") or "."] = (oct(m.mode), hashlib.sha256(b).hexdigest() if b is not None else None,
                                                                         m.pax_headers.get("SCHILY.xattr.security.selinux", "").rstrip("\x00"), m.uid)
ok(rc == 0 and {k: v[:2] for k, v in mem.items()} == {e_[0]: (oct(e_[2]), e_[3]) for e_ in lay}
   and {v[2] for v in mem.values()} == {"u:object_r:system_file:s0"} and {v[3] for v in mem.values()} == {0},
   "tar_prebuilt: patch_tar.py writes exactly the layout, root-owned, system_file")
ok(set(e.tar_paths) == {"system/app/Fake/" + e_[0] for e_ in lay if e_[1] == "file"}, "tar_prebuilt: every file is a changed member")
open(A, "ab").write(b"x")
refused(lambda: e.op_tar_prebuilt("Fake", "arm64", "system/app/Fake"), "tar_prebuilt: an APK that is not the pin stops the build")
refused(lambda: e.op_prebuilt("Fake", "arm64", os.path.join(W, "sys2")), "prebuilt: an APK that is not the pin stops the build")
refused(lambda: e.op_prebuilt("Fake", "arm32", os.path.join(W, "sys2")), "prebuilt: an ABI without a row stops the build")

# ---- the Z9X image check (tools/sign/check_image.py): the pinned signer on exactly that path, nothing else
sys.path.insert(0, os.path.join(G, "tools", "sign"))
import check_image
pins = check_image.prebuilt_pins(G)
ok(pins == {"system/app/LumenPlay/LumenPlay.apk": rows[0]["signer"]}, "check_image: the Lumen Play APK path and its signer pin")
tsv = os.path.join(W, "all_signers.tsv")


def signer_errs(lines):
    open(tsv, "w").write("# kind\tpath\tsigner_sha256\tsigner\tDN\n" + "".join(l + "\n" for l in lines))
    return [e for e in check_image.check_nogms(W, {}, {}, {}, signers_tsv=tsv, nogms=[]) if "signed by" in e]


K = rows[0]["signer"]
ok(not signer_errs(["apk\tsystem/app/LumenPlay/LumenPlay.apk\t%s\tother\tCN=Kino" % K]), "check_image: Lumen Play on its own key passes")
ok(signer_errs(["apk\tsystem/app/LumenPlay/LumenPlay.apk\t%s\tother\tCN=x" % ("1" * 64)]) != [],
   "check_image: the Lumen Play path on another key is refused")
ok(signer_errs(["apk\tsystem/app/Other/Other.apk\t%s\tother\tCN=Kino" % K]) != [],
   "check_image: the Lumen Play key on another path is refused")

# ---- the real files, when fetched (build/prebuilt): pins, one ABI of libraries each
apks = os.path.join(G, "build", "prebuilt")
real = [r for r in rows if os.path.isfile(os.path.join(apks, r["file"]))]
if real:
    ok(subprocess.run([sys.executable, "-B", os.path.join(G, "tools/build/prebuilt.py"), "check", "--apks", apks]).returncode == 0,
       "prebuilt.py check --apks: the fetched files are the pins and unpack")
else:
    print("skip the real APKs (not fetched: python3 tools/build/prebuilt.py fetch)")
print("prebuilt_test: %s" % ("all green" if not bad else "%d failed" % bad))
sys.exit(1 if bad else 0)
PY
