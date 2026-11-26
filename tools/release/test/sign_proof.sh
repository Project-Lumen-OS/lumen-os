#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# sign_proof.sh (Mac): tools/release/sign.sh reproduces the published signatures byte for byte. For each live release
# the small signed files are downloaded (SHA256SUMS and, for a model with updates, its two channel manifests), signed
# again with tools/release/sign.sh and compared with the published .sig files (PKCS#1 v1.5 is deterministic).
set -u
G=${G:?G}; OUT=${OUT:?OUT}
B=https://github.com/Project-Lumen-OS/lumen-os/releases/download
rm -rf "$OUT"; mkdir -p "$OUT"
FAIL=0
while read -r C V; do
  eval "$(python3 -B "$G/tools/release/model.py" "$C" "$V")"
  D=$OUT/$C; L=$OUT/$C.live; mkdir -p "$D/channel" "$L/channel"
  for f in SHA256SUMS SHA256SUMS.sig; do curl -sfL -m 60 --retry 3 -o "$L/$f" "$B/$TAG/$f" || { echo "FAIL $C: no live $TAG/$f"; FAIL=1; continue 2; }; done
  cp "$L/SHA256SUMS" "$D/SHA256SUMS"
  if [ "$KIND" = ota ]; then
    for ch in beta test; do for s in "" .sig; do
      curl -sfL -m 60 --retry 3 -o "$L/channel/update-$WIRE-$ch.json$s" "$B/$MANIFEST_TAG/update-$WIRE-$ch.json$s" || { echo "FAIL $C: live channel"; FAIL=1; }
    done; cp "$L/channel/update-$WIRE-$ch.json" "$D/channel/"; done
  fi
  bash "$G/tools/release/sign.sh" "$C" "$V" "$D" > "$OUT/$C.log" 2>&1 || { echo "FAIL $C: sign.sh $(tail -1 "$OUT/$C.log")"; FAIL=1; continue; }
  n=0; same=0
  for s in $(cd "$D" && find . -name '*.sig' | sort); do
    n=$((n + 1)); cmp -s "$D/$s" "$L/$s" && same=$((same + 1)) || echo "DIFF $C $s"
  done
  [ $n -gt 0 ] && [ $same = $n ] && echo "SAME $C $TAG: $same of $n signatures byte-identical to the published ones" || { echo "FAIL $C: $same of $n"; FAIL=1; }
done << 'L'
g0111 1.0.10-beta12
g0100 1.0.10-beta12
h0061 1.0.10-beta4
h0062 1.0.10-beta5
h0063 1.0.10-beta5
g0170 1.0.10-beta1
L
[ $FAIL = 0 ] && echo SIGN_PROOF_GREEN || echo SIGN_PROOF_RED
