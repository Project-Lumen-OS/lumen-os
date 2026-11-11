#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Test of tools/devices/gen_device_keys.sh (one key set per projector, rule S1), on a scratch registry and a scratch
# HOME: never the maintainer's ~/.lumen-keys, never the project tree. On the Mac or Linux:
#   bash tools/devices/test/gen_device_keys_test.sh
# The scratch registry is a copy of every devices/<codename>/{device,image}.conf of this tree and the public
# certificates of the other devices (devices/<other>/certs); the scratch HOME's .lumen-keys holds copies of those
# public certificates under the other devices' keys_dir (as on the maintainer's Mac). Cases, for the X50 Ultra
# (g0111, its registry keys_dir and installer_id):
#   fresh: the platform key (RSA-2048) and the OTA keys ota and ota_next (RSA-4096, sha256WithRSAEncryption), each
#     .pk8 matching its certificate, modes 700 / 600 / 644, three different certificates and keys, FINGERPRINTS.txt
#     with the three SHA-256, and in the tree only the public certificates (devices/g0111/certs/{platform,ota,
#     ota_next}.x509.pem, FINGERPRINTS.txt, installer/certs/<id>/ota.x509.pem = ota), never a private key in the
#     tree or in the output
#   again: refused (never overwrites)
#   another device's keys_dir (goat_elegant's, play6), ~/.lumen-keys itself, a folder inside the project tree: refused
#   S1: a key whose public key another projector's certificate already carries (a fake openssl hands out that
#     projector's key) is refused, also when the certificate itself is new
# Exit status 0 only when every case passes. Never touches anything outside its temp dir.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
GEN=$G/tools/devices/gen_device_keys.sh
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_genkeys.XXXXXX") && W=$(cd "$W" && pwd -P) || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0; CFAIL=0; CASE=
C=g0111
dc() { sed -n "s/^$1=//p" "$G/devices/$C/device.conf" | head -n 1; }
IID=$(dc installer_id)
OSSL=${OPENSSL:-$(command -v openssl)}
sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi; }
der() { "$OSSL" x509 -in "$1" -outform DER | sha; }
spk() { "$OSSL" x509 -in "$1" -pubkey -noout | "$OSSL" pkey -pubin -outform DER | sha; }
mode() { stat -f '%Lp' "$1" 2>/dev/null || stat -c '%a' "$1"; }

begin() {  # case: a fresh scratch registry ($R) and HOME ($HM)
  CASE=$1; CFAIL=0
  R=$W/$1/root; HM=$W/$1/home; rm -rf "$W/$1"; mkdir -p "$R" "$HM/.lumen-keys"
  (cd "$G" && for f in devices/*/device.conf devices/*/image.conf; do mkdir -p "$R/$(dirname "$f")"; cp "$f" "$R/$f"; done)
  local d kd
  for d in "$G"/devices/*/certs; do
    [ -d "$d" ] || continue
    case "$d" in */devices/$C/certs) continue ;; esac
    mkdir -p "$R/${d#"$G"/}"; cp "$d"/*.x509.pem "$R/${d#"$G"/}/"
    # the same public certificates in that device's key folder under the scratch HOME
    kd=$(sed -n 's/^keys_dir=~\///p' "$(dirname "$d")/device.conf")
    mkdir -p "$HM/$kd"; cp "$d"/*.x509.pem "$HM/$kd/"
  done
  rm -rf "$R/devices/$C/certs"
}
gen() {  # args...: the generator on the scratch registry and HOME; status in RC, output in $W/$CASE/out
  env HOME="$HM" OPENSSL="${GEN_OPENSSL:-$OSSL}" bash "$GEN" --root "$R" "$@" > "$W/$CASE/out" 2>&1
  RC=$?
}
fail() { echo "  FAIL [$CASE] $*"; CFAIL=1; }
expect_rc() { [ "$RC" = "$1" ] || fail "exit $RC, want $1"; }
expect_out() { grep -qF -- "$1" "$W/$CASE/out" || fail "output lacks '$1'"; }
no_private_out() { if grep -qE 'PRIVATE KEY|privateExponent|prime1' "$W/$CASE/out"; then fail "a private key in the output"; fi; }
end() {
  no_private_out
  if find "$R" -name '*.pk8' -o -name '*.key' | grep -q .; then fail "a private key in the tree: $(find "$R" -name '*.pk8')"; fi
  if grep -rlE 'PRIVATE KEY' "$R" 2>/dev/null | grep -q .; then fail "a PEM private key in the tree"; fi
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $CASE"; sed 's/^/     | /' "$W/$CASE/out" | tail -8; fi
}

begin fresh
gen "$C"
expect_rc 0
KD=$HM/.lumen-keys/$C
for f in platform ota/ota ota/ota_next; do
  [ -f "$KD/$f.pk8" ] && [ -f "$KD/$f.x509.pem" ] || { fail "no $f"; continue; }
  [ "$(mode "$KD/$f.pk8")" = 600 ] || fail "$f.pk8 mode $(mode "$KD/$f.pk8")"
  [ "$(mode "$KD/$f.x509.pem")" = 644 ] || fail "$f.x509.pem mode $(mode "$KD/$f.x509.pem")"
  [ "$("$OSSL" pkey -inform DER -in "$KD/$f.pk8" -pubout -outform DER | sha)" = "$(spk "$KD/$f.x509.pem")" ] || fail "$f.pk8 does not match its certificate"
  grep -qx "$(printf '%-13s %s' "$f" "$(der "$KD/$f.x509.pem")")" "$KD/FINGERPRINTS.txt" || fail "FINGERPRINTS.txt lacks $f"
done
for d in "$KD" "$KD/ota"; do [ "$(mode "$d")" = 700 ] || fail "$d mode $(mode "$d")"; done
"$OSSL" x509 -in "$KD/platform.x509.pem" -noout -subject | grep -q "Lumen OS $C platform" || fail "platform subject"
"$OSSL" x509 -in "$KD/ota/ota_next.x509.pem" -noout -subject | grep -q "Lumen OS $C OTA next" || fail "ota_next subject"
for f in ota ota_next; do
  "$OSSL" x509 -in "$KD/ota/$f.x509.pem" -noout -text | grep -q 'Public-Key: (4096 bit)' || fail "$f is not RSA-4096"
  "$OSSL" x509 -in "$KD/ota/$f.x509.pem" -noout -text | grep -q 'sha256WithRSAEncryption' || fail "$f is not sha256WithRSAEncryption"
done
[ "$(for f in platform ota/ota ota/ota_next; do spk "$KD/$f.x509.pem"; done | sort -u | wc -l | tr -d ' ')" = 3 ] || fail "two keys are the same"
for f in platform ota ota_next; do
  s=$KD/$f.x509.pem; [ "$f" = platform ] || s=$KD/ota/$f.x509.pem
  cmp -s "$s" "$R/devices/$C/certs/$f.x509.pem" || fail "devices/$C/certs/$f.x509.pem is not the certificate"
done
cmp -s "$KD/ota/ota.x509.pem" "$R/installer/certs/$IID/ota.x509.pem" || fail "installer/certs/$IID/ota.x509.pem is not the OTA certificate"
cmp -s "$KD/FINGERPRINTS.txt" "$R/devices/$C/certs/FINGERPRINTS.txt" || fail "devices/$C/certs/FINGERPRINTS.txt"
[ "$(ls "$R/devices/$C/certs" | LC_ALL=C sort | tr '\n' ' ')" = "FINGERPRINTS.txt ota.x509.pem ota_next.x509.pem platform.x509.pem " ] || fail "certs: $(ls "$R/devices/$C/certs")"
expect_out "ota/ota_next $(der "$KD/ota/ota_next.x509.pem")"
end
FRESH_ROOT=$R; FRESH_HOME=$HM

CASE=again; CFAIL=0; R=$FRESH_ROOT; HM=$FRESH_HOME; mkdir -p "$W/$CASE"
before=$(cd "$HM/.lumen-keys/$C" && find . -type f -exec shasum -a 256 {} + | sort)
gen "$C"
expect_rc 1; expect_out "already holds platform.pk8: refusing to overwrite"
[ "$(cd "$HM/.lumen-keys/$C" && find . -type f -exec shasum -a 256 {} + | sort)" = "$before" ] || fail "the key folder changed"
end

for o in goat_elegant dragon_healthy; do
  begin "other_keys_dir_$o"
  kd=$(sed -n 's/^keys_dir=//p' "$R/devices/$o/device.conf"); kd=$HM/${kd#\~/}
  gen "$C" "$kd"
  expect_rc 1; expect_out "is the key dir of another device"
  [ ! -e "$kd/platform.pk8" ] || fail "wrote into $kd"
  end
done

begin release_keys_dir
gen "$C" "$HM/.lumen-keys"
expect_rc 1; expect_out "is the key dir of another device"
end

begin inside_tree
gen "$C" "$G/build/keys_$C"
expect_rc 1; expect_out "refusing to create keys inside the project tree"
[ ! -e "$G/build/keys_$C" ] || fail "made $G/build/keys_$C"
end

# S1 by key, not only by certificate: a fake openssl that hands out the HB platform key whenever a 2048-bit key is
# made. The HB certificate is in the scratch registry and the scratch key folder; the generator's own new
# certificate for that key differs from it byte for byte, so only the key compare can catch it.
begin s1_same_key
mkdir -p "$W/$CASE/fake"
"$OSSL" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$W/$CASE/fake/other.key" 2>/dev/null
"$OSSL" req -new -x509 -sha256 -key "$W/$CASE/fake/other.key" -days 2 -subj "/CN=another projector" -out "$W/$CASE/fake/other.pem" 2>/dev/null
mkdir -p "$R/devices/goat_elegant/certs" "$HM/.lumen-keys/goat_elegant"
cp "$W/$CASE/fake/other.pem" "$R/devices/goat_elegant/certs/other.x509.pem"
cat > "$W/$CASE/fake/openssl" <<EOF
#!/bin/sh
# genpkey of a 2048-bit key: the other projector's key (test only); everything else: the real openssl
if [ "\$1" = genpkey ]; then
  case "\$*" in *rsa_keygen_bits:2048*) while [ \$# -gt 0 ]; do [ "\$1" = -out ] && cp "$W/$CASE/fake/other.key" "\$2"; shift; done; exit 0 ;; esac
fi
exec "$OSSL" "\$@"
EOF
chmod 755 "$W/$CASE/fake/openssl"
GEN_OPENSSL=$W/$CASE/fake/openssl gen "$C"
expect_rc 1; expect_out "has the public key of"; expect_out "(rule S1)"
[ ! -e "$HM/.lumen-keys/$C/ota/ota.pk8" ] || fail "went on to the OTA key"
end

echo "gen_device_keys: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
