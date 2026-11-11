#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# x50_verify_negtest.sh: verify_tree_x50.py's HAL-app exception, end to end on a REAL staged X50 tree on the build
# host (image.conf host; Linux: the xattrs and labels are real there). Run on the Mac after
# 'bash tools/image/compose.sh g0111 tN stage':
#   bash devices/g0111/test/x50_verify_negtest.sh tN
# On the Mac: two APKs of apps/X50EyeGuard signed with the AOSP test key (apps/sdk), one unchanged, one with a
# HAL call more (DisplayManager 61, not allowed). On the host, in ~/<remote_dir>/negtest-tN (removed at the end), each case is a
# hard-link copy of ~/<remote_dir>/x50-tN-net/root (cp -al: the staged tree itself is never changed) and a copy of
# ~/<remote_dir>/in, with one change, checked by the staged verify_tree_x50.py against the mounted base image:
#   unchanged    no change: OK
#   testkey      the tree's and the staged APK = the test-key build: refused, its v2/v3 signer
#   fifth_call   both = the build with one call more (61): refused, the calls (and the signer)
#   not_staged   only the tree's APK = the test-key build: refused, not the staged file
#   missing      the APK removed from the tree: refused, missing and "not in the image"
#   elsewhere    the real APK also at system/app/Other/Other.apk: refused, a path not allowed and a gmpf name
#   z9x_framework (t12) the tree's framework-res.apk is the base's, still on the Z9X release platform key: refused (not
#                the signed file, not on the g0111 platform certificate, a Z9X-signed file in the image)
#   z9x_app      (t12) a Z9X-platform-signed APK of the base (SettingsProvider) put back over its signed copy: refused
#   uffd_false   the product build.prop says ro.dalvik.vm.enable_uffd_gc=false (the stock X50's value): refused, an
#                ART property differs from the base and the boot image is compiled for the userfaultfd GC
#   logd_reinit  (t17) x50_netadb.rc (tree and IN) gets t16's size properties + logd --reinit back: refused (Android
#                14 logd ignores the properties and a reinit sets 256 KiB again)
#   tvinput_base (t17) the base's re-signed Z9xTvInput.apk instead of the X50 build: refused, not IN/stage/X50TvInput.apk
# No key is read (test key only), nothing signed with the X50 key leaves the Mac, nothing of the X50 image changes.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
V=${1:?usage: x50_verify_negtest.sh tN}
case $V in t[0-9]|t[0-9][0-9]) ;; *) echo "VARIANT must be tN"; exit 2 ;; esac
img() { python3 -B "$G/tools/devices/devices.py" image g0111 "$1"; }
SRV=$(img host); RD=$(img remote_dir)
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_negtest.XXXXXX") && W=$(cd "$W" && pwd) || exit 1
trap 'rm -rf "$W"' EXIT
retry() { local i; for i in 1 2 3 4 5 6; do "$@" && return 0; sleep 10; done; return 1; }
for n in testkey fifth; do
  cp -R "$G/apps/X50EyeGuard" "$W/app_$n"; rm -rf "$W/app_$n/build" "$W/app_$n/test"
  # the source modules of devices/g0111/app/X50EyeGuard.layers (their src/ only)
  cp -R "$G/apps/core/lumen-ui/src/." "$G/apps/core/lumen-hal/src/." "$W/app_$n/src/"
done
python3 -B - "$W/app_fifth/src/org/lumen/x50/eyeguard/Gmpf.java" <<'PY' || exit 1
import sys
p = sys.argv[1]; s = open(p).read()
old = 'GO_TO_BOOT_COMPLETED(Iface.POWER, 2, "goToBootCompleted");'
assert s.count(old) == 1
open(p, "w").write(s.replace(old, 'GO_TO_BOOT_COMPLETED(Iface.POWER, 2, "goToBootCompleted"),\n'
                                  '        SET_ON_OFF(Iface.DISPLAY, 61, "setHumanDetectOnOff");'))
PY
for n in testkey fifth; do
  BUILD_DIR="$W/b_$n" sh "$G/apps/build_apk.sh" "$W/app_$n" "$W/$n.apk" > "$W/b_$n.log" 2>&1 \
    || { tail -n 5 "$W/b_$n.log"; echo "x50_verify_negtest: the $n build failed"; exit 1; }
done
cat > "$W/host.sh" <<'EOF'
set -u
RD=$1; V=$2
O=$HOME/$RD; T=$O/x50-$V-net; N=$O/negtest-$V
[ -f "$T/STAGE_INFO.txt" ] && [ -d "$T/root" ] || { echo "negtest: no staged tree $T (compose.sh g0111 $V stage first)"; exit 1; }
BID=$(sed -n 's/^bid=//p' "$T/STAGE_INFO.txt")
LBL=u:object_r:system_file:s0
APP=system/app/X50EyeGuard/X50EyeGuard.apk
cleanup() { mountpoint -q "$N/mnt_base" && sudo umount "$N/mnt_base"; sudo rm -rf "$N"; }
trap cleanup EXIT
mkdir -p "$N/mnt_base"; mv "$O/negtest-$V.in/"*.apk "$N/" && rmdir "$O/negtest-$V.in"
sudo mount -t erofs -o loop,ro "$O/base/z9x-1.0.9-anp-system.img" "$N/mnt_base" || exit 1
PASS=0; FAIL=0
setfile() {  # tree path src: one file replaced (a new inode) with the owner, mode and label of compose_x50.sh
  sudo rm -f "$1/$2"; sudo mkdir -p "$(dirname "$1/$2")"; sudo cp "$3" "$1/$2"; sudo chown 0:0 "$1/$2"; sudo chmod 0644 "$1/$2"
  sudo python3 -I -c 'import os, sys; os.setxattr(sys.argv[1], "security.selinux", sys.argv[2].encode() + b"\0")' "$1/$2" "$LBL"
}
prep() { sudo rm -rf "$N/c_$1"; mkdir -p "$N/c_$1"; cp -a "$O/in" "$N/c_$1/in"; sudo cp -al "$T/root" "$N/c_$1/root"; }
check() {  # name want-rc text...
  local n=$1 want=$2 ok=1 t; shift 2
  sudo python3 -I "$N/c_$n/in/verify_tree_x50.py" "$N/mnt_base" "$N/c_$n/root" "$N/c_$n/in" "$V" "$BID" > "$N/$n.log" 2>&1
  local rc=$?
  [ "$rc" = "$want" ] || ok=0
  for t in "$@"; do grep -qF -- "$t" "$N/$n.log" || ok=0; done
  if [ $ok = 1 ]; then PASS=$((PASS + 1)); echo "ok   $n (exit $rc)"; else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $rc)"; tail -n 8 "$N/$n.log" | sed 's/^/     | /'; fi
  sudo rm -rf "$N/c_$n"
}
TK=$N/testkey.apk; FIVE=$N/fifth.apk; REAL=$O/in/stage/X50EyeGuard.apk
prep unchanged; check unchanged 0 "verify_tree_x50: OK" "HAL app $APP" "verify_tree_x50: platform set g0111" "0 on the Z9X platform key" \
  "verify_tree_x50: ART ro.dalvik.vm.enable_uffd_gc=true, boot image and services.odex concurrent-copying=false"
# the stock X50's value in the image: refused (a property differs from the base; the boot image is the uffd one)
prep uffd_false; P=$N/c_uffd_false/root/system/product/etc/build.prop
sudo cp --preserve=all "$P" "$P.new"
sudo python3 -I -c 'import sys
p = sys.argv[1]; s = open(p).read(); o = "ro.dalvik.vm.enable_uffd_gc=true\n"
assert s.count(o) == 1
f = open(p, "r+"); f.truncate(0); f.write(s.replace(o, "ro.dalvik.vm.enable_uffd_gc=false\n")); f.close()' "$P.new"
sudo mv "$P.new" "$P"
check uffd_false 1 "ART / dexopt / zygote properties differ from the base: ro.dalvik.vm.enable_uffd_gc 'true' -> 'false'" \
  "system/framework/arm64/boot.oat is compiled for the userfaultfd GC (concurrent-copying=false), but ro.dalvik.vm.enable_uffd_gc=false"
prep testkey; setfile "$N/c_testkey/root" "$APP" "$TK"; cp "$TK" "$N/c_testkey/in/stage/X50EyeGuard.apk"
check testkey 1 "(the one HAL app): v2 signer certificate" "(the one HAL app): v3 signer certificate"
prep fifth_call; setfile "$N/c_fifth_call/root" "$APP" "$FIVE"; cp "$FIVE" "$N/c_fifth_call/in/stage/X50EyeGuard.apk"
check fifth_call 1 "(the one HAL app): the calls are" "'DisplayManager', 61, 'setHumanDetectOnOff'"
prep not_staged; setfile "$N/c_not_staged/root" "$APP" "$TK"
check not_staged 1 "added $APP is not the staged stage/X50EyeGuard.apk"
prep missing; sudo rm -f "$N/c_missing/root/$APP"
check missing 1 "missing added file $APP" "the HAL app $APP is not in the image"
prep elsewhere; setfile "$N/c_elsewhere/root" system/app/Other/Other.apk "$REAL"
check elsewhere 1 "added path not allowed: system/app/Other/Other.apk" "names the gmpf HAL: system/app/Other/Other.apk!classes.dex"
FW=system/framework/framework-res.apk
prep z9x_framework; setfile "$N/c_z9x_framework/root" "$FW" "$N/mnt_base/$FW"
check z9x_framework 1 "$FW is not the file sign_x50_base.py signed" "$FW is not signed by the g0111 platform certificate alone" \
  "signed by the Z9X release platform certificate: $FW"
SP=system/priv-app/SettingsProvider/SettingsProvider.apk
prep z9x_app; setfile "$N/c_z9x_app/root" "$SP" "$N/mnt_base/$SP"
check z9x_app 1 "signed by the Z9X release platform certificate: $SP"
# t17: t16's log size block back in x50_netadb.rc (tree and IN): refused by the logd checks
prep logd_reinit; NRC=$N/c_logd_reinit/in/x50_netadb.rc
printf '%s\n' '' 'on property:sys.boot_completed=1' '    setprop persist.logd.size.main 16777216' '    start x50-logd-reinit' '' \
  'service x50-logd-reinit /system/bin/logd --reinit' '    oneshot' '    disabled' '    user logd' '    group logd' >> "$NRC"
setfile "$N/c_logd_reinit/root" system/etc/init/x50_netadb.rc "$NRC"
check logd_reinit 1 "no logd size property (Android 14 logd ignores them" "no logd --reinit (it sets the 256 KiB default again"
# t17: the base's TV input (re-signed, without the X50 source picker look) instead of the staged X50 build: refused
TVA=system/app/Z9xTvInput/Z9xTvInput.apk
prep tvinput_base; setfile "$N/c_tvinput_base/root" "$TVA" "$O/in/stage/base_signed/$TVA"
check tvinput_base 1 "$TVA is not IN/stage/X50TvInput.apk"
echo "x50_verify_negtest: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
EOF
retry ssh "$SRV" "rm -rf ~/$RD/negtest-$V.in && mkdir -p ~/$RD/negtest-$V.in; exit 0" || { echo "cannot reach $SRV"; exit 1; }
retry scp -q "$W/testkey.apk" "$W/fifth.apk" "$SRV:$RD/negtest-$V.in/" || { echo "cannot copy to $SRV"; exit 1; }
ssh "$SRV" "bash -s $RD $V" < "$W/host.sh"
