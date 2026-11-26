#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# tools/devices/compile_recipes.sh TREE OUT (build host): every app recipe of every model of TREE assembled and built into an APK with the
# throwaway key, one line "<codename>.<App> <sha256 of the APK>" each in OUT/APKS (sorted). A tree with family recipes
# lists a model's recipes by assemble_app.py --recipes, an older one by devices/<c>/app/*.layers: the same
# (model, app) pairs either way, so OUT/APKS of two trees compare line for line.
set -u
SRC=$1; OUT=$2
# The proof of a refactor of recipes or model data: run it on a worktree of the base and on the branch, then
# diff BASE_OUT/APKS HEAD_OUT/APKS (apps/build_apk.sh with a fixed SOURCE_DATE_EPOCH and one throwaway key gives the
# same bytes for the same inputs). JAVA_HOME, ANDROID_SDK, KEY_PK8 and KEY_PEM default to the xgimi-llm paths.
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64} ANDROID_SDK=${ANDROID_SDK:-$HOME/lumen-tools/android-sdk}
export KEY_PK8=${KEY_PK8:-$HOME/fixreds/testkey.pk8} KEY_PEM=${KEY_PEM:-$HOME/fixreds/testkey.x509.pem}
rm -rf "$OUT"; mkdir -p "$OUT/log" "$OUT/apk" "$OUT/tree"
cd "$SRC" || exit 2
pairs() {
  for c in $(ls devices); do
    [ -f "devices/$c/device.conf" ] || continue
    if grep -q -- "--recipes" tools/devices/assemble_app.py; then
      python3 -B tools/devices/assemble_app.py --root . --recipes "$c" | while IFS="$(printf '\t')" read -r a p; do echo "$c $a"; done
    else
      for L in devices/$c/app/*.layers; do [ -f "$L" ] && echo "$c $(python3 -c 'import sys
for l in open(sys.argv[1]):
    w = l.split("#")[0].split()
    if len(w) == 2 and w[0] == "app": print(w[1]); break' "$L") $L"; done
    fi
  done
}
one() {
  c=$1 a=$2 n=$1.$2
  if [ -n "${3:-}" ]; then set -- "$3"; else set -- --root . --device "$c" --app "$a"; fi
  python3 -B tools/devices/assemble_app.py "$@" --out "$OUT/tree/$n" > "$OUT/log/$n.asm" 2>&1 \
    || { echo "$n ASM_FAIL" >> "$OUT/RES"; return; }
  case $a in *LumenInstaller*) set -- MIN_SDK=29 TARGET_SDK=29 ;; *) set -- ;; esac
  env "$@" SOURCE_DATE_EPOCH=1700000000 BUILD_DIR="$OUT/b/$n" sh apps/build_apk.sh "$OUT/tree/$n" "$OUT/apk/$n.apk" > "$OUT/log/$n.log" 2>&1 \
    && echo "$n $(sha256sum < "$OUT/apk/$n.apk" | cut -c1-64)" >> "$OUT/RES" || echo "$n BUILD_FAIL" >> "$OUT/RES"
}
pairs > "$OUT/PAIRS"
while read -r c a l; do
  one "$c" "$a" "$l" &
  while [ "$(jobs -r | wc -l)" -ge 12 ]; do sleep 1; done
done < "$OUT/PAIRS"
wait
sort "$OUT/RES" > "$OUT/APKS"
echo "compile_recipes: $(wc -l < "$OUT/PAIRS") recipes, $(grep -c ' [0-9a-f]\{64\}$' "$OUT/APKS") built, $(grep -vc ' [0-9a-f]\{64\}$' "$OUT/APKS") failed"
