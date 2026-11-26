#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# verify.sh CODENAME VERSION DIR [CHANNEL_DIR] (any host with openssl): the checks every release of one model passes,
# on the files in DIR (the kit before upload, or the files downloaded back from GitHub after it):
#   SHA256SUMS lists exactly the image (and trial.sh for a trial) and they match; SHA256SUMS.sig verifies with the
#   model's certificate and NOT with a sibling's; a trial.sh names this image's size and sha256 and this model;
#   for a model with updates: both channel manifests (CHANNEL_DIR, default DIR/channel) verify the same way and point
#   at this release's files, and when delta_generator is on PATH the payload of the OTA zip is signed by the model.
# Ends VERIFY_OK, or VERIFY_RED with every failed check.
set -uo pipefail
C=${1:?CODENAME}; V=${2:?VERSION}; DIR=${3:?DIR}; CH=${4:-$3/channel}
H=$(cd "$(dirname "$0")" && pwd)
eval "$(python3 -B "$H/model.py" "$C" "$V")"
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_verify.XXXXXX"); trap 'rm -rf "$W"' EXIT
RED=""; bad() { echo "FAIL $*"; RED="$RED|$*"; }; ok() { echo "ok   $*"; }
sha() { if command -v sha256sum > /dev/null; then sha256sum < "$1" | cut -c1-64; else shasum -a 256 < "$1" | cut -c1-64; fi; }
size() { wc -c < "$1" | tr -d ' '; }
openssl x509 -in "$CERT" -pubkey -noout > "$W/own.pem"
[ -n "$SIBLING_CERT" ] && openssl x509 -in "$SIBLING_CERT" -pubkey -noout > "$W/sib.pem"
sigok() {  # FILE
  openssl dgst -sha256 -verify "$W/own.pem" -signature "$1.sig" "$1" > /dev/null 2>&1 || { bad "$(basename "$1").sig does not verify with $CODENAME"; return; }
  if [ -f "$W/sib.pem" ] && openssl dgst -sha256 -verify "$W/sib.pem" -signature "$1.sig" "$1" > /dev/null 2>&1; then
    bad "$(basename "$1").sig verifies with the sibling certificate too"; return
  fi
  ok "$(basename "$1").sig: $CODENAME key, the sibling refuses"
}
if [ "$KIND" = ota ]; then want="$IMAGE"; else want="system_dsu.img trial.sh"; fi
got=$(awk '{print $2}' "$DIR/SHA256SUMS" | sort | tr '\n' ' ')
[ "$got" = "$(echo $want | tr ' ' '\n' | sort | tr '\n' ' ')" ] && ok "SHA256SUMS lists $want" || bad "SHA256SUMS lists '$got', want '$want'"
while read -r h n; do
  if [ -f "$DIR/$n" ]; then [ "$(sha "$DIR/$n")" = "$h" ] && ok "$n = SHA256SUMS" || bad "$n differs from SHA256SUMS"
  else bad "no $n"; fi
done < "$DIR/SHA256SUMS"
sigok "$DIR/SHA256SUMS"
if [ "$KIND" = trial ] && [ -f "$DIR/trial.sh" ] && [ -f "$DIR/system_dsu.img" ]; then
  s=$(size "$DIR/system_dsu.img"); i=$(sha "$DIR/system_dsu.img")
  grep -q "$i" "$DIR/trial.sh" && grep -q "$s" "$DIR/trial.sh" && grep -q "$MODEL" "$DIR/trial.sh" \
    && ok "trial.sh checks this image ($s bytes) on $MODEL" || bad "trial.sh does not name this image or $MODEL"
fi
if [ "$KIND" = ota ]; then
  zip=${IMAGE%-system.img}-full-ota.zip
  for ch in beta test; do
    f=$CH/update-$WIRE-$ch.json
    [ -f "$f" ] || { bad "no $f"; continue; }
    sigok "$f"
    python3 - "$f" "$WIRE" "$ch" "$V" "$VCODE" "https://github.com/$REPO/releases/download/$TAG" "$IMAGE" "$zip" "$DIR" <<'PY' \
      && ok "update-$WIRE-$ch.json points at $TAG" || bad "update-$WIRE-$ch.json fields"
import hashlib, json, os, sys
f, wire, ch, v, vc, url, img, z, d = sys.argv[1:10]
m = json.load(open(f, encoding="utf-8"))
assert m["device"] == wire and m["channel"] == "%s-%s" % (wire, ch) and m["version"] == v and m["version_code"] == int(vc), m
def same(name, rec):
    p = os.path.join(d, name)
    assert rec["url"] == url + "/" + name, rec["url"]
    if os.path.isfile(p):
        assert rec["size"] == os.path.getsize(p) and rec["sha256"] == hashlib.sha256(open(p, "rb").read()).hexdigest(), name
if ch == "beta":
    same(img, m["image"])
else:
    p = m["packages"]
    assert len(p) == 1 and p[0]["type"] == "full", p
    same(z, p[0])
PY
  done
  if command -v delta_generator > /dev/null && [ -f "$DIR/$zip" ]; then
    (cd "$W" && unzip -q "$DIR/$zip" payload.bin) && delta_generator --in_file="$W/payload.bin" --public_key="$W/own.pem" > /dev/null 2>&1 \
      && ok "payload signed with the $CODENAME key" || bad "payload signature"
    [ -f "$W/sib.pem" ] && delta_generator --in_file="$W/payload.bin" --public_key="$W/sib.pem" > /dev/null 2>&1 && bad "payload verifies with the sibling key"
  else
    echo "skip payload signature (no delta_generator or no $zip here)"
  fi
fi
[ -z "$RED" ] && echo "VERIFY_OK $C $TAG" || { echo "VERIFY_RED $C $TAG$RED"; exit 1; }
