#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Mutants of apps/LumenSystemUIOverlay: each one breaks the overlay or one place that ships it, in a throwaway copy of
# the tree, and test/run.sh must fail on every one of them (and pass on the unchanged copy).
# Usage: bash apps/LumenSystemUIOverlay/test/mutants.sh   (needs the SDK that run.sh uses)
set -eu
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/sysui_mut.XXXXXX"); trap 'rm -rf "$W"' EXIT
: "${W:?}"
git -C "$G" ls-files -z | (cd "$G" && xargs -0 cp --parents -t "$W" 2>/dev/null || true)
cp -r "$G/apps/LumenSystemUIOverlay" "$W/apps/"
O=apps/LumenSystemUIOverlay
V=$O/res/values/integers.xml M=$O/AndroidManifest.xml
MUTANTS=(
  "end, the TvSystemUI value|$V|s/>21</>8388629</"
  "left|$V|s/>21</>19</"
  "start|$V|s/>21</>8388627</"
  "right without center_vertical|$V|s/>21</>5</"
  "not static|$M|s/isStatic=\"true\"/isStatic=\"false\"/"
  "other target|$M|s/\"com.android.systemui\"/\"com.android.settings\"/"
  "a second resource|$V|s#</resources>#<bool name=\"x\">true</bool></resources>#"
  "X50 family apps=|devices/_family/x50ultra/x50ultra.build.conf|s/ LumenSystemUIOverlay//"
  "HB device.conf apps=|devices/goat_elegant/device.conf|s/ LumenSystemUIOverlay//"
  "X50 steps place|devices/g0111/image/steps.tsv|/place\tstage\/LumenSystemUIOverlay/d"
  "RS 20 steps place|devices/_family/rs20/image/steps.tsv|/place\tstage\/LumenSystemUIOverlay/d"
  "RS30 steps place|devices/g0155/image/steps.tsv|/place\tsigned_stage\/apps\/LumenSystemUIOverlay/d"
  "RS30 image compare|devices/g0155/image/steps.tsv|/same\t.*LumenSystemUIOverlay/d"
  "HB steps install|devices/goat_elegant/image/steps.tsv|/install\t0644\tsigned_stage\/apps\/LumenSystemUIOverlay/d"
  "Z9X tar_add|devices/horse_popular/image/steps.tsv|/tar_add\tsystem\/product\/overlay\/LumenSystemUIOverlay/d"
  "Play 6 steps install|devices/dragon_healthy/image/steps.tsv|/install\t0644\tLumenSystemUIOverlay/d"
  "X50 verifier|devices/g0111/image/verify_tree_x50.py|/overlay\/LumenSystemUIOverlay.apk/d"
  "HB verifier|devices/goat_elegant/image/verify_tree.py|/overlay\/LumenSystemUIOverlay.apk/d"
  "Play 6 build|tools/play6/build_play6.sh|/apps\/LumenSystemUIOverlay\" /d"
)
bash "$W/$O/test/run.sh" > "$W/base.out" 2>&1 || { tail -5 "$W/base.out"; echo "the unchanged copy fails"; exit 1; }
k=0 n=0
for m in "${MUTANTS[@]}"; do
  IFS='|' read -r name file expr <<< "$m"
  n=$((n + 1))
  cp "$W/$file" "$W/$file.orig"
  sed -i "$expr" "$W/$file"
  if cmp -s "$W/$file" "$W/$file.orig"; then echo "NOOP    $name"; mv "$W/$file.orig" "$W/$file"; continue; fi
  if bash "$W/$O/test/run.sh" > "$W/m.out" 2>&1; then echo "SURVIVED $name"; else echo "killed  $name"; k=$((k + 1)); fi
  mv "$W/$file.orig" "$W/$file"
done
echo "mutants $k/$n killed"
[ "$k" = "$n" ]
