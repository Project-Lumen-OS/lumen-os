#!/bin/bash
# gen_x50_apex_keys.sh: the X50 (g0111) APEX keys, one payload key and one container key per APEX module that
# devices/g0111/image/sign_x50_base.py re-signs because its payload holds an APK on the platform certificate
# (devices/g0111/image/x50_apex_modules.txt). Mac only. Modeled on gen_apex() of tools/play6/gen_play6_keys.sh.
#
#   OPENSSL=/opt/homebrew/bin/openssl [X50_CODENAME=g0100] bash devices/g0111/image/gen_x50_apex_keys.sh
# X50_CODENAME: the X50 model whose key set this is, g0111 (X50 Ultra, the default) or g0100 (X50 Ultra Max, its own
# keys since 2026-11-11); every path below names that codename.
#
# Private files go to ~/.lumen-keys/g0111/apex (dir mode 700, pem/pk8 mode 600) and never leave the Mac; the public
# files (<m>.x509.pem, <m>.avbpubkey, <m>.pubkey.pem, FINGERPRINTS.txt) are copied to devices/g0111/certs/apex.
#   <m>.pem         payload key, RSA-4096 PEM (signs the AVB hashtree footer of apex_payload.img)
#   <m>.avbpubkey   its public key in AVB format (= the apex_pubkey entry of the APEX)        [public]
#   <m>.pubkey.pem  the same public key as PEM                                                [public]
#   <m>.pk8         container key, RSA-4096 PKCS#8 DER (APK-style signature of the .apex)
#   <m>.x509.pem    its self-signed certificate                                               [public]
# Existing keys are kept (only the checks run again). Refuses: a key dir that is not g0111's, any key equal to a
# Lumen release (Z9X) APEX key, a Play 6 key or another device's certificate (rule S1).
set -euo pipefail
die() { echo "gen_x50_apex_keys: ERROR $*" >&2; exit 1; }
H=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$H/../../.." && pwd)
OPENSSL=${OPENSSL:-/opt/homebrew/bin/openssl}
[ -x "$OPENSSL" ] || die "no $OPENSSL (OpenSSL 3, not LibreSSL)"
C=${X50_CODENAME:-g0111}
case $C in g0111|g0100) ;; *) die "X50_CODENAME $C is not an X50 model (g0111, g0100)" ;; esac
KD=$HOME/.lumen-keys/$C
[ -f "$KD/platform.pk8" ] && [ -f "$KD/FINGERPRINTS.txt" ] || die "$KD is not the $C key dir (tools/devices/gen_device_keys.sh $C)"
cmp -s <("$OPENSSL" x509 -in "$KD/platform.x509.pem" -outform DER) <("$OPENSSL" x509 -in "$G/devices/$C/certs/platform.x509.pem" -outform DER) \
  || die "$KD/platform.x509.pem is not devices/$C/certs/platform.x509.pem"
AVBTOOL=(python3 "$G/tools/sign/third_party/avbtool.py")
sha256in() { shasum -a 256 | cut -d' ' -f1; }
[ "$(sha256in < "$G/tools/sign/third_party/avbtool.py")" = f8e82d9eb64093972cc2e04fbcec5c86a10cb2cac9f871c7a55490bb3b6f48eb ] \
  || die "tools/sign/third_party/avbtool.py is not the pinned AOSP avbtool"
MODS=$(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$H/x50_apex_modules.txt" | grep -v '^$')
AK=$KD/apex
PUB=$G/devices/$C/certs/apex
umask 077
mkdir -p "$AK"; chmod 700 "$AK"
mkdir -p "$PUB"
# every certificate and payload key another projector trusts
FOREIGN=$(mktemp); trap 'rm -f "$FOREIGN"' EXIT
for c in "$G"/tools/sign/release_certs/*.x509.pem "$G"/tools/sign/release_certs/apex/*.x509.pem "$G"/tools/play6/certs/*.x509.pem \
         "$G"/tools/play6/certs/apex/*.x509.pem "$G"/devices/*/certs/*.x509.pem "$G"/tools/sign/testcerts/*.x509.pem; do
  [ -f "$c" ] && "$OPENSSL" x509 -in "$c" -outform DER | sha256in >> "$FOREIGN"
done
for a in "$G"/tools/sign/release_certs/apex/*.avbpubkey "$G"/tools/play6/certs/apex/*.avbpubkey "$HOME"/.lumen-keys/apex/*.avbpubkey; do
  [ -f "$a" ] && sha256in < "$a" >> "$FOREIGN"
done
for m in $MODS; do
  case $m in *[!A-Za-z0-9._]*|.*) die "bad module name $m" ;; esac
  if [ -e "$AK/$m.pem" ] || [ -e "$AK/$m.pk8" ]; then
    [ -e "$AK/$m.pem" ] && [ -e "$AK/$m.pk8" ] && [ -e "$AK/$m.x509.pem" ] || die "incomplete APEX key set for $m in $AK"
    echo "keep existing APEX keys for $m"
  else
    tmp=$(mktemp -d "$AK/.tmp.XXXXXX")
    "$OPENSSL" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -pkeyopt rsa_keygen_pubexp:65537 -out "$tmp/payload.pem" 2>/dev/null
    "$OPENSSL" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -pkeyopt rsa_keygen_pubexp:65537 -out "$tmp/container.pem" 2>/dev/null
    "$OPENSSL" req -new -x509 -sha256 -key "$tmp/container.pem" -days 10950 \
      -subj "/O=Lumen OS/OU=$C/CN=Lumen OS X50 APEX $m" -out "$tmp/cert.pem"
    "$OPENSSL" pkcs8 -in "$tmp/container.pem" -topk8 -outform DER -nocrypt -out "$tmp/container.pk8"
    mv "$tmp/payload.pem" "$AK/$m.pem"; mv "$tmp/container.pk8" "$AK/$m.pk8"; mv "$tmp/cert.pem" "$AK/$m.x509.pem"
    rm -rf "$tmp"
    echo "generated APEX keys for $m (payload + container, RSA-4096)"
  fi
  chmod 600 "$AK/$m.pem" "$AK/$m.pk8"; chmod 644 "$AK/$m.x509.pem"
  "${AVBTOOL[@]}" extract_public_key --key "$AK/$m.pem" --output "$AK/$m.avbpubkey"
  "$OPENSSL" pkey -in "$AK/$m.pem" -pubout -out "$AK/$m.pubkey.pem"
  chmod 644 "$AK/$m.avbpubkey" "$AK/$m.pubkey.pem"
  a=$("$OPENSSL" pkey -inform DER -in "$AK/$m.pk8" -pubout -outform DER | sha256in)
  b=$("$OPENSSL" x509 -in "$AK/$m.x509.pem" -pubkey -noout | "$OPENSSL" pkey -pubin -outform DER | sha256in)
  [ "$a" = "$b" ] || die "apex/$m.pk8 does not match apex/$m.x509.pem"
  [ "$("$OPENSSL" pkey -in "$AK/$m.pem" -noout -text 2>/dev/null | head -n1)" = "Private-Key: (4096 bit, 2 primes)" ] || die "apex/$m.pem is not RSA-4096"
  [ "$("$OPENSSL" pkey -inform DER -in "$AK/$m.pk8" -noout -text 2>/dev/null | head -n1)" = "Private-Key: (4096 bit, 2 primes)" ] || die "apex/$m.pk8 is not RSA-4096"
  for d in "$("$OPENSSL" x509 -in "$AK/$m.x509.pem" -outform DER | sha256in)" "$(sha256in < "$AK/$m.avbpubkey")"; do
    ! grep -qx "$d" "$FOREIGN" || die "an APEX key of $m is trusted by another projector or is an AOSP test key (rule S1)"
  done
  for e in x509.pem avbpubkey pubkey.pem; do cp "$AK/$m.$e" "$PUB/$m.$e"; chmod 644 "$PUB/$m.$e"; done
done
{
  echo "# Lumen OS X50 ($C) APEX keys (public). Per module: SHA-256 of the container certificate (DER) and of the"
  echo "# payload public key (AVB format = the apex_pubkey entry). Private keys: ~/.lumen-keys/$C/apex (Mac only)."
  for m in $MODS; do
    printf '%-40s container %s  payload %s\n' "$m" "$("$OPENSSL" x509 -in "$AK/$m.x509.pem" -outform DER | sha256in)" "$(sha256in < "$AK/$m.avbpubkey")"
  done
} > "$AK/FINGERPRINTS.txt"
chmod 644 "$AK/FINGERPRINTS.txt"
cp "$AK/FINGERPRINTS.txt" "$PUB/FINGERPRINTS.txt"
echo "gen_x50_apex_keys: OK ($(echo $MODS | wc -w | tr -d ' ') modules; public files in devices/$C/certs/apex)"
