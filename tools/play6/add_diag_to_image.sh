#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# add_diag_to_image.sh: make a Play 6 DIAGNOSTIC image from a published release or pre-release image (play6-1.0.7
# and later), the way the AIC tool (removed with the AIC files) made play6-1.0.6-aic2 with --diag: debugfs of Homebrew
# e2fsprogs on the Mac (E2FS=/usr/sbin on a Linux build host), nothing else in the image changes (the vendor fstab
# mounts system without verity, so the stale AVB footer is never read). Never for a regular release.
#
#   tools/play6/add_diag_to_image.sh IN.img OUT.img VERSION
#     IN.img   the release image, e.g. build/release/play6-1.0.7/lumen-os-play6-1.0.7-nogms-system.img
#     VERSION  x.y.z-diagN, e.g. 1.0.7-diag1 or 1.0.10-diag1 of 1.0.10-beta1: x.y.z is the image's own
#              (tools/play6/image_version.sh), it becomes ro.lumen.version (the installer's release.conf VERSION
#              and image name carry the same string)
#     OUT.img  e.g. build/release/play6-1.0.7-diag1/lumen-os-play6-1.0.7-diag1-nogms-system.img
#
# Adds (root, label system_file), the same files and modes as aic2 had, plus the timing:
#   /system/etc/init/z9x_earlylog.rc 0644, /system/etc/xgimi/z9x_earlylog.sh 0755   USB-stick boot log from 'on init'
#   /system/etc/xgimi/earlylog_usb.sh 0644                                          Play 6 USB host modules for it
#   /system/etc/init/lumen_escape.rc 0644, /system/etc/xgimi/lumen_escape.sh 0755   fastbootd after a failed boot
#   /system/etc/init/play6_boottiming.rc 0644, /system/etc/xgimi/play6_boottiming.sh 0755   boot timing record
#                                                (since diag2 also the early DrvWdt / MI3 log, drvwdt.txt)
#   /system/etc/init/play6_early_mik.rc 0644      (diag2) the vendor's projector driver chain at late-fs, so gmpf's
#                                                DrvWdt finds /dev/mik!wdt,
#                                                unless IN has it already (every image of the recipe since
#                                                play6-boot-fix): then it must equal the repository file
# product/etc/build.prop: ro.lumen.version=VERSION, ro.lumen.build_id=<old>-diagN, ro.lumen.diag=1, and the test
# builds' log props of tools/play6/compose.sh --diag (ro.logd.auditd=false, persist.logd.size=2097152: the boot's
# own errors stay in the log instead of the SELinux audit flood). ro.lumen.version_code stays (the installer and
# the updater compare builds by it; a diag image is not newer than its release).
# Checks afterwards: every file read back equal, modes, owner, labels, the AIC files of IN (when it has them)
# untouched, e2fsck clean.
# Writes OUT.img and OUT.img.sha256.
set -euo pipefail
die() { echo "add_diag_to_image: ERROR: $*" >&2; exit 1; }
IN=${1:?usage: add_diag_to_image.sh IN.img OUT.img VERSION}; OUT=${2:?OUT.img}; VER=${3:?VERSION}
echo "$VER" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+-diag[0-9]+$' || die "VERSION must be x.y.z-diagN, not $VER"
. "$(dirname "$0")/image_version.sh"
TAG=${VER##*-}
G=$(cd "$(dirname "$0")/../.." && pwd)
E2=${E2FS:-/opt/homebrew/opt/e2fsprogs/sbin}
D=$E2/debugfs
[ -x "$D" ] || die "needs e2fsprogs (brew install e2fsprogs)"
[ -s "$IN" ] || die "no $IN"

T=$(mktemp -d "${TMPDIR:-/tmp}/p6diag.XXXXXX")
trap 'rm -rf "$T"' EXIT
PROD=/system/product/etc/build.prop
"$D" -R "cat $PROD" "$IN" > "$T/product.prop" 2>/dev/null
grep -qx 'ro.lumen.device=play6' "$T/product.prop" || die "$IN is not a Play 6 image"
grep -q '^ro.lumen.diag=' "$T/product.prop" && die "$IN is a diag image already"
grep -qx 'ro.adb.secure=1' "$T/product.prop" || die "$IN is not a release image (no ro.adb.secure=1)"
OLD_VER=$(sed -n 's/^ro\.lumen\.version=//p' "$T/product.prop" | tail -n 1)
OLD_ID=$(sed -n 's/^ro\.lumen\.build_id=//p' "$T/product.prop" | tail -n 1)
[ -n "$OLD_ID" ] || die "no ro.lumen.build_id in $IN"
iv_new_version "$OLD_VER" "$VER" >/dev/null || die "$IN is Lumen OS $OLD_VER, VERSION $VER is not a diag of it"
for f in /system/etc/init/z9x_earlylog.rc /system/etc/init/lumen_escape.rc /system/etc/init/play6_boottiming.rc; do
  "$D" -R "stat $f" "$IN" 2>/dev/null | grep -q '^Inode' && die "$IN already has $f"
done
MIK=/system/etc/init/play6_early_mik.rc
HAS_MIK=0
if "$D" -R "stat $MIK" "$IN" 2>/dev/null | grep -q '^Inode'; then
  "$D" -R "cat $MIK" "$IN" > "$T/mik.in" 2>/dev/null
  cmp -s "$T/mik.in" "$G/overlay/play6/play6_early_mik.rc" || die "$IN has another $MIK than overlay/play6/play6_early_mik.rc"
  HAS_MIK=1
fi
grep -v -E '^(ro\.lumen\.version|ro\.lumen\.build_id|ro\.logd\.auditd|persist\.logd\.size)=' "$T/product.prop" > "$T/product.new"
{ echo "ro.lumen.version=$VER"; echo "ro.lumen.build_id=$OLD_ID-$TAG"; echo "ro.lumen.diag=1"
  echo "ro.logd.auditd=false"; echo "persist.logd.size=2097152"; } >> "$T/product.new"

# the AIC files before, to prove they are untouched
for d in modules firmware; do
  "$D" -R "ls -p /system/etc/aic8800/$d" "$IN" 2>/dev/null | awk -F/ -v d=$d 'NF>5 && $6!="." && $6!=".." {print d "/" $6}'
done > "$T/aic_names"
aic_sums() {  # aic_sums IMG
  { while read -r m; do echo "/system/etc/aic8800/$m"; done < "$T/aic_names"
    echo /system/etc/init/play6_aic.rc; echo /system/etc/xgimi/play6_aic.sh; echo /system/lib/libbt-vendor-aic.so; } |
  while read -r p; do printf '%s ' "$p"; "$D" -R "cat $p" "$1" 2>/dev/null | shasum -a 256 | cut -d' ' -f1; done
}
aic_sums "$IN" > "$T/aic_before"

mkdir -p "$(dirname "$OUT")"
cp "$IN" "$OUT.part"
CMDS=$T/cmds
: > "$CMDS"
printf '%s\0' u:object_r:system_file:s0 > "$T/l_sys"
LIST=$T/list
: > "$LIST"
NF=0
add() {  # add IMAGE_PATH LOCAL MODE; debugfs splits paths at spaces, so write from $T
  NF=$((NF + 1)); cp "$2" "$T/f$NF"
  { echo "cd ${1%/*}"; echo "rm ${1##*/}"; echo "write $T/f$NF ${1##*/}"; echo "set_inode_field $1 mode 0100$3"
    echo "set_inode_field $1 uid 0"; echo "set_inode_field $1 gid 0"; echo "ea_set -f $T/l_sys $1 security.selinux"; } >> "$CMDS"
  echo "$1 $T/f$NF $3" >> "$LIST"
}
add /system/etc/init/z9x_earlylog.rc "$G/overlay/debug/z9x_earlylog.rc" 644
add /system/etc/xgimi/z9x_earlylog.sh "$G/overlay/debug/z9x_earlylog.sh" 755
add /system/etc/xgimi/earlylog_usb.sh "$G/overlay/play6/earlylog_usb.sh" 644
add /system/etc/init/lumen_escape.rc "$G/overlay/debug/lumen_escape.rc" 644
add /system/etc/xgimi/lumen_escape.sh "$G/overlay/debug/lumen_escape.sh" 755
add /system/etc/init/play6_boottiming.rc "$G/overlay/play6/play6_boottiming.rc" 644
add /system/etc/xgimi/play6_boottiming.sh "$G/overlay/play6/play6_boottiming.sh" 755
[ "$HAS_MIK" = 1 ] || add $MIK "$G/overlay/play6/play6_early_mik.rc" 644
add $PROD "$T/product.new" 644

"$D" -w -f "$CMDS" "$OUT.part" >/dev/null 2>"$T/dbg.err" || true
grep -v '^debugfs ' "$T/dbg.err" | grep -vE 'rm: File not found by ext2_lookup' | grep . && die "debugfs: see above"

while read -r p l m; do
  "$D" -R "cat $p" "$OUT.part" > "$T/rb" 2>/dev/null
  cmp -s "$T/rb" "$l" || die "$p read back differs"
  st=$("$D" -R "stat $p" "$OUT.part" 2>/dev/null)
  echo "$st" | grep -q "Mode:  0$m" || die "$p mode is not 0$m"
  echo "$st" | grep -q "User:     0   Group:     0" || die "$p owner is not root"
  "$D" -R "ea_get $p security.selinux" "$OUT.part" 2>/dev/null | grep -q system_file || die "$p label is not system_file"
done < "$LIST"
aic_sums "$OUT.part" > "$T/aic_after"
cmp -s "$T/aic_before" "$T/aic_after" || die "the AIC files changed"
"$E2/e2fsck" -fn "$OUT.part" >/dev/null 2>&1 || die "e2fsck found problems"
mv "$OUT.part" "$OUT"
{ shasum -a 256 "$OUT" 2>/dev/null || sha256sum "$OUT"; } | sed "s#  .*#  $(basename "$OUT")#" > "$OUT.sha256"
echo "add_diag_to_image: $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes), Lumen OS $VER, build id $OLD_ID-$TAG, $(wc -l < "$LIST" | tr -d ' ') files, early driver chain $([ "$HAS_MIK" = 1 ] && echo 'already in the input' || echo added), AIC files untouched ($(grep -c /aic8800/ "$T/aic_before" | tr -d ' ') of them)"
cat "$OUT.sha256"
