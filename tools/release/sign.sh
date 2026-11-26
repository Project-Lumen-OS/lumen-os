#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# sign.sh CODENAME VERSION DIR (Mac, the only host with ~/.lumen-keys): sign the small files of one model's release in
# DIR with that model's OTA key (tools/release/model.py KEYS), never an image: DIR/SHA256SUMS and, for a model with
# updates, DIR/channel/update-<wire>-beta.json and update-<wire>-test.json (openssl dgst -sha256, PKCS#1 v1.5: the
# same input gives the same signature bytes) plus the payload digests DIR/payload.hash and DIR/metadata.hash that the
# builder computed (openssl pkeyutl, 512-byte signatures payload.sig / metadata.sig for make_ota.py finish). Every
# signature is verified with the model's certificate and must be refused by a sibling model's certificate (a build
# signed with the wrong model's key stops here). Ends SIGN_OK.
set -euo pipefail
C=${1:?CODENAME}; V=${2:?VERSION}; DIR=${3:?DIR}
H=$(cd "$(dirname "$0")" && pwd)
OSSL=${OPENSSL:-$(command -v /opt/homebrew/bin/openssl || command -v openssl)}
eval "$(python3 -B "$H/model.py" "$C" "$V")"
KEY=$KEYS/ota/ota.pk8
[ -f "$KEY" ] || { echo "sign: no key $KEY (sign on the Mac only)" >&2; exit 1; }
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_sign.XXXXXX"); trap 'rm -rf "$W"' EXIT
"$OSSL" x509 -in "$CERT" -pubkey -noout > "$W/own.pem"
[ -z "$SIBLING_CERT" ] || "$OSSL" x509 -in "$SIBLING_CERT" -pubkey -noout > "$W/sib.pem"
dgst() {  # FILE: FILE.sig, verified, refused by the sibling
  "$OSSL" dgst -sha256 -sign "$KEY" -keyform DER -out "$1.sig" "$1"
  "$OSSL" dgst -sha256 -verify "$W/own.pem" -signature "$1.sig" "$1" > /dev/null
  if [ -f "$W/sib.pem" ] && "$OSSL" dgst -sha256 -verify "$W/sib.pem" -signature "$1.sig" "$1" > /dev/null 2>&1; then
    echo "sign: $1 verifies with the sibling certificate $SIBLING_CERT" >&2; exit 1
  fi
  echo "signed $(basename "$1") $(shasum -a 256 "$1.sig" | cut -c1-64)"
}
[ -f "$DIR/SHA256SUMS" ] || { echo "sign: no $DIR/SHA256SUMS" >&2; exit 1; }
dgst "$DIR/SHA256SUMS"
if [ "$KIND" = ota ]; then
  for ch in beta test; do
    f=$DIR/channel/update-$WIRE-$ch.json
    [ -f "$f" ] || { echo "sign: no $f" >&2; exit 1; }
    python3 - "$f" "$WIRE" "$V" "$VCODE" "$ch" <<'PY' || { echo "sign: $f is not this release" >&2; exit 1; }
import json, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
assert m["device"] == sys.argv[2] and m["version"] == sys.argv[3] and m["version_code"] == int(sys.argv[4]), m
assert m["channel"] == "%s-%s" % (sys.argv[2], sys.argv[5]), m["channel"]
PY
    dgst "$f"
  done
  for n in payload metadata; do
    [ -f "$DIR/$n.hash" ] || continue
    [ "$(wc -c < "$DIR/$n.hash" | tr -d ' ')" = 32 ] || { echo "sign: $n.hash is not a sha256 digest" >&2; exit 1; }
    "$OSSL" pkeyutl -sign -keyform DER -inkey "$KEY" -pkeyopt digest:sha256 -in "$DIR/$n.hash" -out "$DIR/$n.sig"
    [ "$(wc -c < "$DIR/$n.sig" | tr -d ' ')" = 512 ] || { echo "sign: $n.sig size" >&2; exit 1; }
    "$OSSL" pkeyutl -verify -pubin -inkey "$W/own.pem" -pkeyopt digest:sha256 -in "$DIR/$n.hash" -sigfile "$DIR/$n.sig" > /dev/null
    echo "signed $n.hash"
  done
fi
echo "SIGN_OK $C $TAG"
