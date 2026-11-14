#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host test: the Play 6 image carries no Wi-Fi or Bluetooth driver of our own (decision 2026-11-12).
# XGIMI's V1.8.127 drives both chips itself (MediaTek MT7663 and the AIC8800 of the newer batches, its own
# aic8800 modules, dongle.cfg and libbt-vendor-aic.so), and the installer refuses an AIC unit on V1.7.116
# (installer/lumen-install.sh p6_aic_vendor_check, tools/ota/test/installer_play6.sh). So:
#   - no step of any variant of the recipe (test, diag, ota, gms, no-dexpreopt, usb-adb) names an AIC file
#   - no repository file the device stages is an AIC file, no image property marks AIC
#   - with an image (IMG=<system.img>, ext4, debugfs of e2fsprogs: E2FS=<dir> or the PATH one) none of the
#     files the earlier images installed is in it (/system/etc/aic8800, play6_aic.rc / .sh, libbt-vendor-aic.so,
#     aicbt.conf, ro.lumen.p6.aic)
# Run: bash tools/play6/test_play6_no_aic.sh   or   IMG=<image> bash tools/play6/test_play6_no_aic.sh
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
fails=0; n=0
check() { n=$((n + 1)); if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails + 1)); fi; }
AICRE='aic8800|play6_aic|libbt-vendor-aic|aicbt|(^|[ /	])aic/|[ 	]sums[ 	]+aic|p6\.aic'

for o in none --diag --ota --gms --no-dexpreopt --usb-adb; do
  if [ "$o" = none ]; then plan=$(python3 -B "$G/tools/build/build.py" plan dragon_healthy t41 2>&1)
  else plan=$(python3 -B "$G/tools/build/build.py" plan dragon_healthy t41 "$o" 2>&1); fi
  check "variant t41 $o plans" 'printf "%s\n" "$plan" | head -1 | grep -q "^plan: dragon_healthy t41"'
  check "variant t41 $o has no AIC step" '! printf "%s\n" "$plan" | grep -q -E "$AICRE"'
done
files=$(python3 -B "$G/tools/build/build.py" files dragon_healthy 2>&1)
check "the staged repository files hold no AIC file" '[ -n "$files" ] && ! printf "%s\n" "$files" | grep -q -E "$AICRE"'
check "steps and descriptor name no AIC file outside comments" '! grep -h -v "^#" "$G/devices/dragon_healthy/image/steps.tsv" "$G/devices/dragon_healthy/build.conf" | grep -q -E "$AICRE"'
check "no AIC property in the image props" '! grep -q -E "^[^#]*p6\.aic" "$G/overlay/play6/product_prop.txt"'
check "the AIC init files are gone from the tree" '[ ! -e "$G/overlay/play6/play6_aic.rc" ] && [ ! -e "$G/overlay/play6/play6_aic.sh" ] && [ ! -e "$G/tools/play6/aic" ]'

if [ -n "${IMG:-}" ]; then
  D=${E2FS:+$E2FS/debugfs}; [ -n "$D" ] && [ -x "$D" ] || D=$(command -v debugfs || echo /opt/homebrew/opt/e2fsprogs/sbin/debugfs)
  check "debugfs is there ($D)" '[ -x "$D" ]'
  check "the image is a Play 6 image" '"$D" -R "cat /system/product/etc/build.prop" "$IMG" 2>/dev/null | grep -qx ro.lumen.device=play6'
  for p in /system/etc/aic8800 /system/etc/init/play6_aic.rc /system/etc/xgimi/play6_aic.sh /system/lib/libbt-vendor-aic.so \
           /system/etc/bluetooth/aicbt.conf; do
    check "image has no $p" '! "$D" -R "stat $p" "$IMG" 2>/dev/null | grep -q "^Inode"'
  done
  check "image props have no ro.lumen.p6.aic" '! "$D" -R "cat /system/product/etc/build.prop" "$IMG" 2>/dev/null | grep -q "^ro\.lumen\.p6\.aic="'
fi
echo "$((n - fails))/$n passed"
[ $fails = 0 ]
