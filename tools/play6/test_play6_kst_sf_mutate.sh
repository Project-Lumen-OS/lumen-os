#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Mutation check of tools/play6/test_play6_kst_sf.sh: each edit below would let a Play 6 image ship without the keystone
# SurfaceFlinger with stock sampling (or swap it over a base it was not built for, or let check_public_play6.py pass
# such an image). The test must turn red on each, and pass unmutated.
#   sh tools/play6/test_play6_kst_sf_mutate.sh
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/p6sfmut.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0
FAIL=0
C=devices/dragon_healthy/build.conf
S=devices/dragon_healthy/image/steps.tsv
K=tools/play6/check_public_play6.py
P=lineage/patches/play6/0002-sf-play6-keystone-warp.patch
setup() {
  rm -rf "$W/t"; mkdir -p "$W/t/lineage/patches" "$W/t/build/play6/sf"
  cp -R "$G/devices" "$G/tools" "$G/overlay" "$W/t/"
  cp -R "$G/lineage/patches/play6" "$W/t/lineage/patches/"
  if [ -f "$G/build/play6/sf/surfaceflinger" ]; then ln -s "$G/build/play6/sf/surfaceflinger" "$W/t/build/play6/sf/surfaceflinger"; fi
}
suite() { bash "$W/t/tools/play6/test_play6_kst_sf.sh" > "$W/out" 2>&1; }
# name file old new
mut() {
  setup
  name=$1; f=$2; old=$3; new=$4
  if ! python3 -B - "$W/t/$f" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1:4]
old, new = old.replace("\\t", "\t").replace("\\n", "\n"), new.replace("\\t", "\t").replace("\\n", "\n")
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit("edit: %r found %d times" % (old, s.count(old)))
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  then FAIL=$((FAIL + 1)); echo "FAIL $name (edit not applied)"; return; fi
  if suite; then FAIL=$((FAIL + 1)); echo "FAIL $name (test still green)"; else PASS=$((PASS + 1)); echo "ok   $name"; fi
}
setup
if ! suite; then echo "FAIL unmutated test is red"; tail -20 "$W/out"; exit 1; fi
I='*\tinstall\t0755\tsurfaceflinger\t{sys}/bin/surfaceflinger'
mut no_install "$S" "$I\n" ''
mut install_not_gms "$S" "$I" '!gms\tinstall\t0755\tsurfaceflinger\t{sys}/bin/surfaceflinger'
mut install_diag_only "$S" "$I" 'diag\tinstall\t0755\tsurfaceflinger\t{sys}/bin/surfaceflinger'
mut install_wrong_path "$S" "$I" '*\tinstall\t0755\tsurfaceflinger\t{sys}/bin/surfaceflinger.lumen'
mut install_mode_0644 "$S" "$I" '*\tinstall\t0644\tsurfaceflinger\t{sys}/bin/surfaceflinger'
mut pack_before_install "$S" '*\tinstall\t0644\tplay6_early_mik.rc' '*\tpack\t{sys}\n*\tinstall\t0644\tplay6_early_mik.rc'
mut no_need "$S" '*\tneed\tsurfaceflinger\n' ''
mut no_input_pin "$S" '*\tsha\tsurfaceflinger\t{SF_SHA}\n' ''
mut no_type_check "$S" '*\tfile_type\tsurfaceflinger\t^ELF 32-bit LSB pie executable, ARM\n' ''
mut no_base_pin "$S" '*\tsha\t{BASE}/bin/surfaceflinger\t{SF_BASE_SHA}\n' ''
mut base_pin_is_input_pin "$S" '{BASE}/bin/surfaceflinger\t{SF_BASE_SHA}' '{BASE}/bin/surfaceflinger\t{SF_SHA}'
mut cleanup_after_install "$S" '# 1, 1b. the framework config RRO' '*\tcleanup\t{sys}/bin/surfaceflinger\n# 1, 1b. the framework config RRO'
mut pin_wrong "$C" 'var.SF_SHA=7c2cd71bcc7a23adda8b10818cc4f62045b4c90b7d9ec7ca49e56b4c7a921f3b' 'var.SF_SHA=7c2cd71bcc7a23adda8b10818cc4f62045b4c90b7d9ec7ca49e56b4c7a921f3c'
mut pin_is_base "$C" 'var.SF_SHA=7c2cd71bcc7a23adda8b10818cc4f62045b4c90b7d9ec7ca49e56b4c7a921f3b' 'var.SF_SHA=f51b92de1d9996d8ee03fae651b71a3cd7283c6028770dccf4d60615e8720b49'
mut pin_twice "$C" 'var.SF_SHA=7c2cd71b' 'var.SF_SHA=7c2cd71bcc7a23adda8b10818cc4f62045b4c90b7d9ec7ca49e56b4c7a921f3b\nvar.SF_SHA=7c2cd71b'
mut not_staged "$C" ' gms build/play6/sf/surfaceflinger' ' gms'
mut check_skips_base "$K" '    if got == base_sf:\n' '    if False:\n'
mut check_passes_any "$K" '    if got != want:\n' '    if False:\n'
mut check_other_conf "$K" 'BUILD_CONF = os.path.join(GSI, "devices", "dragon_healthy", "build.conf")' 'BUILD_CONF = os.path.join(GSI, "devices", "goat_elegant", "build.conf")'
mut patch_no_msaa "$P" '== 1 ? 4 : 0;' '== 1 ? 0 : 0;'
mut patch_nearest "$P" 'if (layer.source.buffer.useTextureFiltering || CC_UNLIKELY(display.keystone)) {' 'if (layer.source.buffer.useTextureFiltering) {'
mut patch_no_edge_aa "$P" '+            if (CC_UNLIKELY(display.keystone)) paint.setAntiAlias(true);\n' ''
echo "test_play6_kst_sf_mutate: $PASS killed, $FAIL survived"
[ "$FAIL" = 0 ]
