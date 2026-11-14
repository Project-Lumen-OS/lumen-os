#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# x50_apk_check_test.sh: the one exception of the X50 image check (devices/g0111/image/verify_tree_x50.py HAL_APP:
# apps/X50EyeGuard may name the gmpf HAL) and its reader devices/g0111/image/x50_apk_check.py. On the Mac (Android
# SDK and JDK as apps/build_apk.sh), no projector, no network, never reads a private key:
#   bash devices/g0111/test/x50_apk_check_test.sh [APK]
# APK: the image's X50EyeGuard.apk signed with the g0111 platform key (default build/g0111/stage/X50EyeGuard.apk,
# tools/devices/build_apps.sh g0111 build/g0111/stage); without it the real-APK cases are skipped and said so.
# Cases:
#   sources     HAL_APP holds to its sources: calls = research/allowed_x50eyeguard.tsv (service, code, method), the
#               interface = GmpfIface of the app, cert = devices/g0111/certs/platform.x509.pem, which is none of
#               the Z9X release, the AOSP test key or the HB platform certificates; the path is
#               /system/app/<name>/<name>.apk of the registry's apps; the composer stages it and the image check
#               imports x50_apk_check from IN
#   real        the g0111-signed APK passes, with exactly 8 calls, and fails with another expected certificate
#   testkey     the same sources signed with the AOSP test key: refused for its signer; with --cert of the test
#               key it passes (the dex is read the same way on a second build)
#   mutants     (test key, checked with --cert of the test key, so only the mutation can fail) an eleventh call (61);
#               33 turned into 37; a fourth gmpf interface; a second transact site; Method.invoke; a HIDL gmpf name;
#               native code: each refused with its own reason
#   unsigned    a zip without a signing block; a truncated APK: refused
# Exit 0 only when every case passes.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
CHK=$G/devices/g0111/image/x50_apk_check.py
VER=$G/devices/g0111/image/verify_tree_x50.py
APK=${1:-$G/build/g0111/stage/X50EyeGuard.apk}
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_apk_check.XXXXXX") && W=$(cd "$W" && pwd) || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0; SKIP=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; [ -f "$W/out" ] && sed 's/^/     | /' "$W/out" | tail -6; }
certsha() { openssl x509 -in "$1" -outform DER | shasum -a 256 | cut -d' ' -f1; }
run() { python3 -B "$CHK" "$@" > "$W/out" 2>&1; RC=$?; }
# expect NAME RC [TEXT]: the last run exited RC and (when given) printed TEXT
expect() {
  local rc=$RC
  if [ "$rc" = "$2" ] && { [ -z "${3:-}" ] || grep -qF -- "$3" "$W/out"; }; then ok "$1"; else bad "$1 (exit $rc, want $2${3:+, '$3'})"; fi
}

# ---------------------------------------------------------------- sources
python3 -B - "$G" "$VER" > "$W/out" 2>&1 <<'PY'
import os, re, subprocess, sys
sys.dont_write_bytecode = True
G, ver = sys.argv[1:3]
sys.path.insert(0, os.path.join(G, "devices", "g0111", "image"))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import x50_apk_check as x, devices as d
h = x.expect_from(ver)
bad = []
rows = []
for line in open(os.path.join(G, "devices/g0111/research/allowed_x50eyeguard.tsv"), encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    s, c, m = line.rstrip("\n").split("\t")[:3]
    rows.append((s, int(c, 0), m))
if sorted(tuple(r) for r in h["calls"]) != sorted(rows):
    bad.append("HAL_APP calls %s != allowed_x50eyeguard.tsv %s" % (h["calls"], rows))
src = "".join(open(os.path.join(G, "apps/X50EyeGuard/src/org/lumen/x50/eyeguard", f), encoding="utf-8").read()
              for f in ("Gmpf.java", "GmpfIface.java") if os.path.isfile(os.path.join(G, "apps/X50EyeGuard/src/org/lumen/x50/eyeguard", f)))
ifs = re.findall(r'^\s+[A-Z_]+\("(xgimi\.hardware\.gmpf\.I\w+)"\)', src, re.M)
if sorted(ifs) != sorted(h["ifaces"]):
    bad.append("HAL_APP ifaces %s != GmpfIface %s" % (h["ifaces"], ifs))
def cert(p):
    der = subprocess.run(["openssl", "x509", "-in", p, "-outform", "DER"], capture_output=True, check=True).stdout
    import hashlib
    return hashlib.sha256(der).hexdigest()
if h["cert"] != cert(os.path.join(G, "devices/g0111/certs/platform.x509.pem")):
    bad.append("HAL_APP cert is not devices/g0111/certs/platform.x509.pem")
others = [os.path.join(G, p) for p in ("tools/sign/release_certs/platform.x509.pem", "apps/sdk/platform.x509.pem",
                                       "devices/goat_elegant/certs/platform.x509.pem", "tools/play6/platform.x509.pem")]
for p in others:
    if os.path.isfile(p) and cert(p) == h["cert"]:
        bad.append("HAL_APP cert is also %s" % p)
reg = d.get(G, "g0111")
name = h["path"].split("/")[2]
if h["path"] != "system/app/%s/%s.apk" % (name, name) or name not in reg.get("apps", []):
    bad.append("HAL_APP path %s is not /system/app/<an app of the registry's apps %s>" % (h["path"], reg.get("apps")))
if reg["hal_app"] != "apps/" + name or reg["hal_check"] != "aidl-table" \
        or reg["hal_allowed"] != "devices/g0111/research/allowed_x50eyeguard.tsv":
    bad.append("registry: hal_app %s hal_check %s hal_allowed %s" % (reg["hal_app"], reg["hal_check"], reg.get("hal_allowed")))
if h["staged"] != "stage/%s.apk" % name:
    bad.append("HAL_APP staged %s" % h["staged"])
conf = dict(l.rstrip("\n").split("=", 1) for l in open(os.path.join(G, "devices/g0111/build.conf")) if "=" in l and not l.startswith("#"))
if conf.get("stage_dir") != "build/g0111/stage" or "devices/g0111/image/x50_apk_check.py" not in conf.get("tools", "").split():
    bad.append("build.conf does not stage x50_apk_check.py and build/g0111/stage")
comp = open(os.path.join(G, "devices/g0111/image/steps.tsv"), encoding="utf-8").read()
if "\tplace\t%s\t{sys}/app/%s/%s.apk\t" % (h["staged"], name, name) not in comp:
    bad.append("steps.tsv does not install %s at %s" % (h["staged"], h["path"]))
vsrc = open(ver, encoding="utf-8").read()
if "import x50_apk_check" not in vsrc or "x50_apk_check.problems(rep, HAL_APP)" not in vsrc:
    bad.append("verify_tree_x50.py does not check the HAL app with x50_apk_check")
print("\n".join(bad) or "sources: OK")
sys.exit(1 if bad else 0)
PY
RC=$?; expect sources 0 "sources: OK"

# ---------------------------------------------------------------- real
if [ -f "$APK" ]; then
  run "$APK" --expect "$VER"; expect real_g0111_apk 0 "OK: 8 calls"
  grep -qF "call DisplayManager	0x0000003E	getHumanDetectOnOff" "$W/out" || bad "real_reads_62"
  grep -qF "call SystemManager	0x00000020	setBootAnimState" "$W/out" || bad "real_reads_32"
  grep -qF "call PowerManager	0x00000002	goToBootCompleted" "$W/out" || bad "real_reads_2"
  run "$APK" --expect "$VER" --cert 0000000000000000000000000000000000000000000000000000000000000000
  expect real_other_cert_refused 1 "signer certificate"
else
  SKIP=$((SKIP + 1)); echo "skip real (no $APK: tools/devices/build_apps.sh g0111 build/g0111/stage)"
fi

# ---------------------------------------------------------------- test key and mutants
TK=$(certsha "$G/apps/sdk/platform.x509.pem")
build() {  # name: apps/X50EyeGuard copied to $W/app_<name>, Gmpf.java edited by $W/edit_<name>.py, built with the test key
  rm -rf "$W/app_$1"; cp -R "$G/apps/X50EyeGuard" "$W/app_$1"; rm -rf "$W/app_$1/build" "$W/app_$1/test"
  if [ -f "$W/edit_$1.py" ]; then python3 -B "$W/edit_$1.py" "$W/app_$1" || return 1; fi
  # the lumen-ui module, as devices/g0111/app/X50EyeGuard.layers assembles it (its src/ only)
  cp -R "$G/apps/core/lumen-ui/src/." "$W/app_$1/src/"
  cp -R "$G/apps/core/lumen-hal/src/." "$W/app_$1/src/"
  BUILD_DIR="$W/b_$1" sh "$G/apps/build_apk.sh" "$W/app_$1" "$W/$1.apk" > "$W/b_$1.log" 2>&1
}
edit() {  # name old new: replace old (must be there once) by new in the HAL client of that mutant (Gmpf.java and
          # its top-level allow list GmpfCall.java / GmpfIface.java)
  cat > "$W/edit_$1.py" <<PY
import os, sys
old, new = $(python3 -c 'import sys; print(repr(sys.argv[1]))' "$2"), $(python3 -c 'import sys; print(repr(sys.argv[1]))' "$3")
d = sys.argv[1] + "/src/org/lumen/x50/eyeguard/"
ps = [d + f for f in ("Gmpf.java", "GmpfCall.java", "GmpfIface.java") if os.path.isfile(d + f)]
hits = [p for p in ps for _ in range(open(p).read().count(old))]
assert len(hits) == 1, "edit: %r not found once" % old
s = open(hits[0]).read()
open(hits[0], "w").write(s.replace(old, new))
PY
}
mutant() {  # name reason
  if build "$1"; then run "$W/$1.apk" --expect "$VER" --cert "$TK"; expect "mutant_$1" 1 "$2"
  else bad "mutant_$1 (build failed: $(tail -n 3 "$W/b_$1.log" | tr '\n' ' '))"; fi
}
if build testkey; then
  run "$W/testkey.apk" --expect "$VER"; expect testkey_refused 1 "v3 signer certificate $TK"
  run "$W/testkey.apk" --expect "$VER" --cert "$TK"; expect testkey_same_dex_ok 0 "OK: 8 calls"
else
  bad "testkey (build failed: $(tail -n 3 "$W/b_testkey.log" | tr '\n' ' '))"
fi
edit extra_call 'GO_TO_BOOT_COMPLETED(GmpfIface.POWER, HalCodes.PM_GO_TO_BOOT_COMPLETED, "goToBootCompleted");' \
  'GO_TO_BOOT_COMPLETED(GmpfIface.POWER, HalCodes.PM_GO_TO_BOOT_COMPLETED, "goToBootCompleted"),
    SET_ON_OFF(GmpfIface.DISPLAY, 61, "setHumanDetectOnOff");'
mutant extra_call "the calls are"
edit code_37 'GET_BOOT_ANIM_STATE(GmpfIface.SYSTEM, HalCodes.SM_GET_BOOT_ANIM_STATE, ' 'GET_BOOT_ANIM_STATE(GmpfIface.SYSTEM, 37, '
mutant code_37 "the calls are"
edit code_61 'GET_HUMAN_DETECT_ON_OFF(GmpfIface.DISPLAY, HalCodes.DM_GET_HUMAN_DETECT_ON_OFF, ' 'GET_HUMAN_DETECT_ON_OFF(GmpfIface.DISPLAY, 61, '
mutant code_61 "the calls are"
edit fourth_iface 'POWER("xgimi.hardware.gmpf.IPowerManager");' \
  'POWER("xgimi.hardware.gmpf.IPowerManager"), TV("xgimi.hardware.gmpf.IGmTvManager");'
mutant fourth_iface "names the gmpf interfaces"
edit second_transact '    boolean connect() {' \
  '    static boolean raw(android.os.IBinder b) throws RemoteException { return b.transact(61, Parcel.obtain(), null, 0); }

    boolean connect() {'
mutant second_transact "2 IBinder.transact sites"
edit reflect '    boolean connect() {' \
  '    static Object refl(Object o) throws Exception { return o.getClass().getMethod("toString").invoke(o); }

    boolean connect() {'
mutant reflect "calls Method.invoke"
edit hidl '    boolean connect() {' \
  '    static String hidl() { return "xgimi.hardware.gmpf@1.0::IGmpf"; }

    boolean connect() {'
mutant hidl "names the HIDL gmpf"
# native code: a lib/ entry added to the test-key APK (zip only; the signer check sees the signing block broken or
# not, the native rule refuses it either way)
if [ -f "$W/testkey.apk" ]; then
  cp "$W/testkey.apk" "$W/native.apk"
  python3 -B -c 'import sys, zipfile; z = zipfile.ZipFile(sys.argv[1], "a"); z.writestr("lib/arm64-v8a/libx.so", b"\x7fELF"); z.close()' "$W/native.apk"
  run "$W/native.apk" --expect "$VER" --cert "$TK"; expect mutant_native 1 "carries native code"
fi

# ---------------------------------------------------------------- unsigned, damaged
python3 -B -c 'import sys, zipfile
src = zipfile.ZipFile(sys.argv[1]); dst = zipfile.ZipFile(sys.argv[2], "w")
for i in src.infolist(): dst.writestr(i, src.read(i))
dst.close()' "$W/testkey.apk" "$W/unsigned.apk" 2>/dev/null
run "$W/unsigned.apk" --expect "$VER" --cert "$TK"; expect unsigned_refused 1 "not signed with APK Signature Scheme v2 or v3"
head -c 20000 "$W/testkey.apk" > "$W/cut.apk"
run "$W/cut.apk" --expect "$VER" --cert "$TK"; RC=$([ $RC -ne 0 ] && echo 1 || echo 0); expect truncated_refused 1

echo "x50_apk_check_test: $PASS passed, $FAIL failed${SKIP:+, $SKIP skipped}"
[ "$FAIL" = 0 ]
