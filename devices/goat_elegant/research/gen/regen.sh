#!/bin/bash
# Regenerate the HB (goat_elegant) reference tables in ../ from a stock dump.
# Run on the Mac from anywhere. Reads the dump on xgimi-builder (read-only), never touches a device.
#   gen/regen.sh            all tables from the G0130 V6.16.24 dump (aidl_codes.tsv, name_map.tsv, banned.tsv)
#   gen/regen.sh v61712     only aidl_codes_v61712.tsv, the code table of the V6.17.12 vendor (G0148 V6.17.12 dump,
#                           the design notes); name_map.tsv and banned.tsv stay the
#                           6.16.24 ones: every allowed row must be equal in both code tables (check_aidl_hal.py with
#                           several --codes), so a row that differs on 6.17.12 can never be sent
# Needs on the server: ~/devices/<dump>/work/fs (unpacked stock partitions), a LineageOS clang
# (llvm-readelf, llvm-nm, llvm-objdump, llvm-cxxfilt) and prebuilts/sdk dexdump in ~/lineage.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(dirname "$HERE")"
GSI="$(cd "$OUT/../../.." && pwd)"
SRV=xgimi-builder
TMP=/tmp/hb_research_regen
FW=${1:-v61624}
case "$FW" in
  v61624) DUMP=g0130_mt9660 TABLE=aidl_codes.tsv
          NDK_SHA=2d391908355f240813494e58f56bfbd16a6682a37af2a7326a58c7596e603d72 ;;
  v61712) DUMP=g0148_mt9660 TABLE=aidl_codes_v61712.tsv
          NDK_SHA=b008632e3a0e1f305b03ff01ad5ab5dc7c1726864d3ebf2523b9fab400cc3cfb ;;
  *) echo "usage: regen.sh [v61624|v61712]" >&2; exit 2 ;;
esac

ssh "$SRV" "rm -rf $TMP && mkdir -p $TMP"
scp -q "$HERE/ndk_codes.py" "$HERE/java_codes.py" "$SRV:$TMP/"
ssh "$SRV" bash -s <<REMOTE
set -euo pipefail
W=\$HOME/devices/$DUMP/work/fs
B=\$(ls -d \$HOME/lineage/prebuilts/clang/host/linux-x86/clang-r*/bin | tail -1)
cd $TMP
sha256sum \$W/vendor/lib/xgimi.hardware.gmpf-V1-ndk.so \$W/system_ext/framework/com.xgimi.middle.jar
echo "$NDK_SHA  \$W/vendor/lib/xgimi.hardware.gmpf-V1-ndk.so" | sha256sum -c --quiet
python3 ndk_codes.py "\$B" \$W/vendor/lib/xgimi.hardware.gmpf-V1-ndk.so ndk_codes.tsv
unzip -q -o \$W/system_ext/framework/com.xgimi.middle.jar classes.dex
\$HOME/lineage/prebuilts/sdk/tools/linux/bin/dexdump -f classes.dex > middle.dump 2>/dev/null
python3 java_codes.py middle.dump java_codes.tsv
REMOTE
L="$(mktemp -d)"
scp -q "$SRV:$TMP/ndk_codes.tsv" "$SRV:$TMP/java_codes.tsv" "$L/"
python3 "$HERE/merge_codes.py" "$L/ndk_codes.tsv" "$L/java_codes.tsv" "$OUT/$TABLE"
if [ "$FW" = v61624 ]; then
  python3 "$HERE/name_map.py" "$GSI" "$OUT/aidl_codes.tsv" "$HERE/replacements.tsv" "$OUT/name_map.tsv"
  python3 "$HERE/banned.py" "$GSI" "$OUT/aidl_codes.tsv" "$OUT/name_map.tsv" "$OUT/banned.tsv"
fi
rm -rf "$L"
