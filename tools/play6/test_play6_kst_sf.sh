#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host test of the keystone SurfaceFlinger in every Play 6 image (devices/dragon_healthy/image/steps.tsv step 1d,
# the design notes). The base build in /out predates lineage/patches/play6/0002 with stock sampling
# (GL_LINEAR on every layer, edge AA, 4x MSAA), so its own /system/bin/surfaceflinger warps the UI with nearest
# sampling. The images after play6-1.0.7-kst1 lost the kst1 binary that fixed it. Checks:
#   - build.conf pins SF_SHA (the binary) and SF_BASE_SHA (the base's own binary it replaces): 64 hex, different
#   - built= stages build/play6/sf/surfaceflinger
#   - every variant of the recipe (test, diag, ota, gms, no-dexpreopt, usb-adb): the input is needed, pinned and an ARM
#     executable, the base's binary is pinned, all before copy_dir. One install 0755 of it at system/bin/surfaceflinger
#     after copy_dir and before pack, no other step touches that path
#   - check_public_play6.py checks the same two pins from build.conf (one source)
#   - patch 0002 still carries the stock sampling the pinned binary was built with
#   - the private binary, when it is here (build/play6/sf/surfaceflinger): sha256 = SF_SHA, a 32-bit ARM executable
#     with both log strings of that patch
#   - with IMG=<system.img>: its /system/bin/surfaceflinger is the pinned binary (debugfs, read only)
# Run: bash tools/play6/test_play6_kst_sf.sh   (IMG=lumen-os-play6-<v>-nogms-system.img for an image)
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
CONF=$G/devices/dragon_healthy/build.conf
STEPS=$G/devices/dragon_healthy/image/steps.tsv
PATCH=$G/lineage/patches/play6/0002-sf-play6-keystone-warp.patch
BIN=$G/build/play6/sf/surfaceflinger
fails=0; n=0
check() { n=$((n + 1)); if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails + 1)); fi; }
sha() { if command -v sha256sum > /dev/null; then sha256sum "$1" | cut -c1-64; else shasum -a 256 "$1" | cut -c1-64; fi; }

SF_SHA=$(sed -n 's/^var\.SF_SHA=//p' "$CONF")
SF_BASE_SHA=$(sed -n 's/^var\.SF_BASE_SHA=//p' "$CONF")
check "build.conf pins SF_SHA once, 64 hex" '[ "$(grep -c "^var\.SF_SHA=" "$CONF")" = 1 ] && echo "$SF_SHA" | grep -qx "[0-9a-f]\{64\}"'
check "build.conf pins SF_BASE_SHA once, 64 hex" '[ "$(grep -c "^var\.SF_BASE_SHA=" "$CONF")" = 1 ] && echo "$SF_BASE_SHA" | grep -qx "[0-9a-f]\{64\}"'
check "the two pins differ" '[ "$SF_SHA" != "$SF_BASE_SHA" ]'
check "built= stages build/play6/sf/surfaceflinger" 'sed -n "s/^built=//p" "$CONF" | tr " " "\n" | grep -qx "build/play6/sf/surfaceflinger"'

P6PLAN() { python3 -B "$G/tools/build/build.py" plan dragon_healthy t41 "$@" 2>&1; }
line_of() { printf '%s\n' "$plan" | grep -E "$1" | awk '{print $1}' | head -1; }
count_of() { printf '%s\n' "$plan" | grep -c -E "$1"; }
for o in none --diag --ota --gms --no-dexpreopt --usb-adb; do
  if [ "$o" = none ]; then plan=$(P6PLAN); else plan=$(P6PLAN "$o"); fi
  nNeed=$(printf '%s\n' "$plan" | grep -E '^ *[0-9]+ need ' | grep -c -E ' surfaceflinger( |$)')
  nPin=$(count_of "^ *[0-9]+ sha surfaceflinger $SF_SHA\$")
  nType=$(count_of '^ *[0-9]+ file_type surfaceflinger \^ELF 32-bit LSB pie executable, ARM$')
  nBase=$(count_of "^ *[0-9]+ sha /out/target/product/generic/system/bin/surfaceflinger $SF_BASE_SHA\$")
  nInst=$(count_of '^ *[0-9]+ install 0755 surfaceflinger [^ ]*/system/bin/surfaceflinger$')
  nTouch=$(count_of '/system/bin/surfaceflinger( |$)')
  check "variant $o: need, pin, type, base pin and install once each" '[ "$nNeed$nPin$nType$nBase$nInst" = 11111 ]'
  check "variant $o: nothing else names system/bin/surfaceflinger" '[ "$nTouch" = 2 ]'
  lPin=$(line_of "^ *[0-9]+ sha surfaceflinger "); lBase=$(line_of "^ *[0-9]+ sha /out/.*/bin/surfaceflinger ")
  lCopy=$(line_of '^ *[0-9]+ copy_dir '); lInst=$(line_of '^ *[0-9]+ install 0755 surfaceflinger ')
  lPack=$(line_of '^ *[0-9]+ pack ')
  check "variant $o: pins before copy_dir, install between copy_dir and pack" \
    '[ -n "$lPin$lBase$lCopy$lInst$lPack" ] && [ "${lPin:-0}" -lt "${lCopy:-0}" ] && [ "${lBase:-0}" -lt "${lCopy:-0}" ] && [ "${lCopy:-0}" -lt "${lInst:-0}" ] && [ "${lInst:-0}" -lt "${lPack:-0}" ]'
done
check "no step of the recipe is limited to some variants for surfaceflinger" \
  '[ -z "$(grep -v "^#" "$STEPS" | grep surfaceflinger | grep -v "^\*	")" ]'

# check_public_play6.py: the same pins, read from build.conf, and its verdict on the three cases
cp_out=$(cd "$G/tools/play6" && python3 -B -c '
import sys
sys.argv = ["x"]
import check_public_play6 as c
w, b = c.conf_var("SF_SHA"), c.conf_var("SF_BASE_SHA")
print(c.SF, w, b, c.sf_problem(w) is None, c.sf_problem(b) is not None and "base build" in c.sf_problem(b),
      c.sf_problem("0" * 64) is not None, c.sf_problem("none") is not None)' 2>&1)
check "check_public_play6.py reads both pins from build.conf" \
  '[ "$(echo "$cp_out" | cut -d" " -f1-3)" = "system/bin/surfaceflinger $SF_SHA $SF_BASE_SHA" ]'
check "check_public_play6.py: pinned binary ok, the base's binary, another one and none fail" \
  '[ "$(echo "$cp_out" | cut -d" " -f4-)" = "True True True True" ]'

# patch 0002: the stock sampling the pinned binary carries
check "0002: 4x MSAA client target when the vendor keystone exists" \
  'grep -q "vendor.mstar.trapezoid.enable\", 0) == 1 ? 4 : 0;" "$PATCH" && grep -q "kTopLeft_GrSurfaceOrigin, kStockSamples, mColorType" "$PATCH"'
check "0002: linear sampling of every layer under the keystone" \
  'grep -q "if (layer.source.buffer.useTextureFiltering || CC_UNLIKELY(display.keystone)) {" "$PATCH"'
check "0002: edge anti-aliasing of rectangles under the keystone" \
  'grep -q "if (CC_UNLIKELY(display.keystone)) paint.setAntiAlias(true);" "$PATCH"'

# the private binary
if [ -f "$BIN" ]; then
  check "build/play6/sf/surfaceflinger is the pinned binary" '[ "$(sha "$BIN")" = "$SF_SHA" ]'
  check "it is a 32-bit ARM executable" 'file -b -L "$BIN" | grep -q "^ELF 32-bit LSB pie executable, ARM"'
  check "it carries the keystone log and the MSAA fallback log of 0002" \
    'LC_ALL=C grep -a -q "Lumen keystone %s: lt %d:%d" "$BIN" && LC_ALL=C grep -a -q "Lumen keystone: no %dx MSAA output surface" "$BIN"'
else
  echo "skip the private binary checks: no $BIN (gsi/build/play6/kst1/surfaceflinger, lineage/play6_sf_build.sh)"
fi

# an image
if [ -n "${IMG:-}" ]; then
  D=${E2FS:+$E2FS/debugfs}; [ -n "$D" ] || D=/opt/homebrew/opt/e2fsprogs/sbin/debugfs
  [ -x "$D" ] || D=$(command -v debugfs || echo /sbin/debugfs)
  T=$(mktemp -d "${TMPDIR:-/tmp}/p6sf.XXXXXX")
  "$D" -R "dump /system/bin/surfaceflinger $T/sf" "$IMG" > /dev/null 2>&1
  got=$( [ -s "$T/sf" ] && sha "$T/sf" || echo none)
  rm -rf "$T"
  check "$(basename "$IMG"): /system/bin/surfaceflinger is the pinned binary (got $got)" '[ "$got" = "$SF_SHA" ]'
fi

echo
if [ "$fails" -gt 0 ]; then echo "test_play6_kst_sf: $fails of $n FAILED"; exit 1; fi
echo "test_play6_kst_sf: all $n passed"
