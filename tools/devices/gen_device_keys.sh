#!/bin/bash
# gen_device_keys.sh: generate the platform key and the OTA key of a device of the registry on the Mac (maintainer
# only, once per device). the design notes; rule S1 of the design notes for every
# device: no image of one projector, and no APK in it, is signed with a certificate another projector trusts.
#
#   bash tools/devices/gen_device_keys.sh [--root DIR] CODENAME [KEYS_DIR]
#
#   CODENAME   a device of devices/<CODENAME>/device.conf; KEYS_DIR defaults to its keys_dir
#   --root     the tree whose registry is read and where the public certificates go (default: this tree;
#              the tests use a scratch copy)
#
# Modeled on tools/play6/gen_play6_keys.sh (its checks, layout and subjects), limited to the platform key and the
# OTA keys. APEX keys come with the device's base re-sign, not here. Writes, with umask 077:
#   KEYS_DIR/platform.pk8            RSA-2048 PKCS#8 DER private key, mode 600
#   KEYS_DIR/platform.x509.pem       its certificate, CN=Lumen OS <codename> platform                [public]
#   KEYS_DIR/ota/ota.pk8             RSA-4096 PKCS#8 DER private key, mode 600
#   KEYS_DIR/ota/ota.x509.pem        its certificate, sha256WithRSAEncryption, CN=Lumen OS <codename> OTA  [public]
#   KEYS_DIR/ota/ota_next.pk8        the spare OTA key, RSA-4096, mode 600
#   KEYS_DIR/ota/ota_next.x509.pem   its certificate, CN=Lumen OS <codename> OTA next             [public]
#   KEYS_DIR/FINGERPRINTS.txt, KEYS_DIR/README.txt; KEYS_DIR and KEYS_DIR/ota mode 700
# Only the public certificates enter the tree: devices/<codename>/certs/{platform,ota,ota_next}.x509.pem
# (make_otacerts.py makes otacerts.zip of ota and ota_next) and installer/certs/<installer_id>/ota.x509.pem. The
# private keys stay on the Mac: never in the project tree, on a build server, in git or a cloud folder. Prints
# paths and certificate fingerprints only, never a key.
#
# Refuses: a KEYS_DIR inside the project tree, ~/.lumen-keys itself (the Lumen release key set), the keys_dir
# of any other device of the registry (~/.lumen-keys/play6, ~/.lumen-keys/goat_elegant, ...: one key set per
# projector), any existing key or certificate in KEYS_DIR (it never overwrites: replacing a platform key means a
# data wipe on every projector that ran an image signed with the old one), and a certificate equal to one of
# tools/sign/release_certs, tools/sign/testcerts, apps/sdk/platform.x509.pem, tools/play6/certs, the
# devices/<other>/certs of every other device, or any *.x509.pem under ~/.lumen-keys outside KEYS_DIR.
set -euo pipefail

H=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$H/../.." && pwd)
ROOT=$G
if [ "${1:-}" = "--root" ]; then ROOT=$(cd "${2:?--root needs a dir}" && pwd); shift 2; fi
C=${1:?usage: gen_device_keys.sh [--root DIR] CODENAME [KEYS_DIR]}
DAYS=${DAYS:-10000}
KEY_OU=${KEY_OU:-kmuradoff}
case $KEY_OU in *[!A-Za-z0-9\ ._-]*|'') echo "gen_device_keys: KEY_OU may hold only letters, digits, space . _ -" >&2; exit 1 ;; esac
sha256in() { if command -v sha256sum >/dev/null; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi; }
OPENSSL=${OPENSSL:-$(command -v openssl)}
die() { echo "gen_device_keys: $*" >&2; exit 1; }
spki() { "$OPENSSL" x509 -in "$1" -pubkey -noout | "$OPENSSL" pkey -pubin -outform DER | sha256in; }   # cert -> key digest
reg() { python3 -B "$H/devices.py" --root "$ROOT" get "$C" "$1"; }

reg codename > /dev/null || die "unknown device $C"
IID=$(reg installer_id)
KD=${2:-$(reg keys_dir)}
case $KD in "~/"*) KD=$HOME/${KD#\~/} ;; esac
case $KD in /*) ;; *) die "KEYS_DIR must be an absolute path or start with ~/: $KD" ;; esac
case $KD in
  *"XGIMI PLAY 6"*|*/gsi/*|*/gsi|"$G"|"$G"/*|"$ROOT"|"$ROOT"/*) die "refusing to create keys inside the project tree: $KD" ;;
esac
p=$KD; while [ ! -d "$p" ]; do p=$(dirname "$p"); done
case $(cd "$p" && pwd -P)/ in
  *"XGIMI PLAY 6"*|*/gsi/*) die "refusing to create keys inside the project tree: $KD" ;;
esac
[ -n "$OPENSSL" ] || die "no openssl"
same_dir() { [ -d "$1" ] && [ -d "$2" ] && [ "$(cd "$1" && pwd -P)" = "$(cd "$2" && pwd -P)" ]; }
real_kd=$KD
[ -d "$KD" ] && real_kd=$(cd "$KD" && pwd -P)
# every other device's key folder (one key set per projector), and the Lumen release set itself
others=$(python3 -B "$H/devices.py" --root "$ROOT" list | cut -f1 | grep -vx -- "$C" || true)
okd="$HOME/.lumen-keys $HOME/.lumen-keys/play6"
for o in $others; do
  x=$(python3 -B "$H/devices.py" --root "$ROOT" get "$o" keys_dir)
  case $x in "~/"*) x=$HOME/${x#\~/} ;; esac
  okd="$okd $x"
done
for x in $okd; do
  if same_dir "$KD" "$x" || [ "${real_kd%/}" = "${x%/}" ]; then
    die "$KD is the key dir of another device ($x); each device has its own (keys_dir in devices/$C/device.conf)"
  fi
done
for f in platform.pk8 platform.x509.pem ota/ota.pk8 ota/ota.x509.pem ota/ota_next.pk8 ota/ota_next.x509.pem releasekey.pk8 ota.pk8 shared.pk8; do
  [ ! -e "$KD/$f" ] || die "$KD already holds $f: refusing to overwrite or mix key sets"
done

s1_cert() {  # cert-file [also-refuse-these-digests...]: prints the SHA-256 of the DER certificate
  local fp pk c d f="$1"; shift
  "$OPENSSL" x509 -in "$f" -noout 2>/dev/null || die "$f is not a certificate"
  fp=$("$OPENSSL" x509 -in "$f" -outform DER | sha256in)
  pk=$(spki "$f")
  { printf '%s\n' "$G/apps/sdk/platform.x509.pem" "$G"/tools/sign/testcerts/*.x509.pem
    find "$G/tools/sign/release_certs" "$G/tools/play6/certs" -name '*.x509.pem'
    for o in $others; do
      for r in "$G" "$ROOT"; do [ ! -d "$r/devices/$o/certs" ] || find "$r/devices/$o/certs" -name '*.x509.pem'; done
    done
    # every certificate of the maintainer's key folders but this device's own (the Z9X set, play6/, goat_elegant/, ...)
    [ ! -d "$HOME/.lumen-keys" ] || find "$(cd "$HOME/.lumen-keys" && pwd -P)" -path "$real_kd" -prune -o -name '*.x509.pem' -print; } | while IFS= read -r c; do
    [ -f "$c" ] || continue
    # the certificate, and its key: a new certificate for a key another projector trusts is no new key
    [ "$("$OPENSSL" x509 -in "$c" -outform DER | sha256in)" != "$fp" ] || die "$f equals $c (rule S1)"
    [ "$(spki "$c")" != "$pk" ] || die "$f has the public key of $c (rule S1)"
  done || exit 1
  if grep -rqi "$fp" "$G/tools/sign/release_certs" "$G/tools/play6/certs" --include=FINGERPRINTS.txt; then
    die "$f is listed in a FINGERPRINTS.txt of another key set (rule S1)"
  fi
  for o in $others; do
    [ ! -f "$ROOT/devices/$o/certs/FINGERPRINTS.txt" ] || ! grep -qi "$fp" "$ROOT/devices/$o/certs/FINGERPRINTS.txt" \
      || die "$f is listed in devices/$o/certs/FINGERPRINTS.txt (rule S1)"
  done
  for d in "$@"; do
    [ "$fp" != "$d" ] || die "$f is another key of this device (each key needs its own certificate)"
  done
  echo "$fp"
}
gen() { # dir name bits cn: one key pair, private key never printed
  local tmp
  tmp=$(mktemp -d "$1/.tmp.XXXXXX")
  "$OPENSSL" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:"$3" -pkeyopt rsa_keygen_pubexp:65537 \
    -out "$tmp/key.pem" 2>/dev/null
  "$OPENSSL" req -new -x509 -sha256 -key "$tmp/key.pem" -days "$DAYS" \
    -subj "/O=Lumen OS/OU=$KEY_OU/CN=$4" -out "$tmp/cert.pem"
  "$OPENSSL" pkcs8 -in "$tmp/key.pem" -topk8 -outform DER -nocrypt -out "$tmp/key.pk8"
  mv "$tmp/key.pk8" "$1/$2.pk8"
  mv "$tmp/cert.pem" "$1/$2.x509.pem"
  rm -rf "$tmp"
  chmod 600 "$1/$2.pk8"; chmod 644 "$1/$2.x509.pem"
  local a b
  a=$("$OPENSSL" pkey -inform DER -in "$1/$2.pk8" -pubout -outform DER | sha256in)
  b=$("$OPENSSL" x509 -in "$1/$2.x509.pem" -pubkey -noout | "$OPENSSL" pkey -pubin -outform DER | sha256in)
  [ "$a" = "$b" ] || die "$2.pk8 does not match $2.x509.pem"
  # "Private-Key: (N bit, 2 primes)" (OpenSSL), "RSA Private-Key: (N bit)" (LibreSSL of macOS)
  case "$("$OPENSSL" pkey -inform DER -in "$1/$2.pk8" -noout -text 2>/dev/null | head -n1)" in
    "Private-Key: ($3 bit, 2 primes)"|"RSA Private-Key: ($3 bit)") ;;
    *) die "$2.pk8 is not an RSA-$3 key" ;;
  esac
  echo "generated $2 (RSA-$3)"
}

umask 077
mkdir -p "$KD/ota"
chmod 700 "$KD" "$KD/ota"
real_kd=$(cd "$KD" && pwd -P)   # the folder just made: s1_cert skips only it under ~/.lumen-keys
gen "$KD" platform 2048 "Lumen OS $C platform"
FP=$(s1_cert "$KD/platform.x509.pem")
ota_cert_ok() {  # name: an OTA certificate as update_engine and the OTA tools need it
  local txt
  txt=$("$OPENSSL" x509 -in "$KD/ota/$1.x509.pem" -noout -text)
  grep -q 'Signature Algorithm: sha256WithRSAEncryption' <<<"$txt" || die "ota/$1.x509.pem is not sha256WithRSAEncryption"
  grep -q 'Exponent: 65537 (0x10001)' <<<"$txt" || die "ota/$1.x509.pem: public exponent is not 65537"
}
gen "$KD/ota" ota 4096 "Lumen OS $C OTA"
ota_cert_ok ota
OFP=$(s1_cert "$KD/ota/ota.x509.pem" "$FP")
gen "$KD/ota" ota_next 4096 "Lumen OS $C OTA next"
ota_cert_ok ota_next
NFP=$(s1_cert "$KD/ota/ota_next.x509.pem" "$FP" "$OFP")

{
  echo "# Lumen OS $C certificates (public). SHA-256 of the DER certificate."
  printf '%-13s %s\n' platform "$FP"
  printf '%-13s %s\n' ota/ota "$OFP"
  printf '%-13s %s\n' ota/ota_next "$NFP"
} > "$KD/FINGERPRINTS.txt"
cat > "$KD/README.txt" <<EOF
Lumen OS keys of $C (tools/devices/gen_device_keys.sh). Private: platform.pk8, ota/ota.pk8 and ota/ota_next.pk8
(the spare OTA key) never leave this Mac (not the project tree, not a build server, not git, not a cloud folder).
Only the .x509.pem certificates are public; copies are in devices/$C/certs/ and installer/certs/$IID/.
EOF
chmod 644 "$KD/FINGERPRINTS.txt" "$KD/README.txt"

# the public certificates into the tree (never a .pk8)
( umask 022
  mkdir -p "$ROOT/devices/$C/certs" "$ROOT/installer/certs/$IID"
  cp "$KD/platform.x509.pem" "$ROOT/devices/$C/certs/platform.x509.pem"
  cp "$KD/ota/ota.x509.pem" "$ROOT/devices/$C/certs/ota.x509.pem"
  cp "$KD/ota/ota_next.x509.pem" "$ROOT/devices/$C/certs/ota_next.x509.pem"
  cp "$KD/ota/ota.x509.pem" "$ROOT/installer/certs/$IID/ota.x509.pem"
  cp "$KD/FINGERPRINTS.txt" "$ROOT/devices/$C/certs/FINGERPRINTS.txt" )
echo "keys: $KD/platform.pk8, $KD/ota/ota.pk8, $KD/ota/ota_next.pk8 (private, mode 600)"
echo "certificates: devices/$C/certs/{platform,ota,ota_next}.x509.pem, installer/certs/$IID/ota.x509.pem"
echo "platform     $FP"
echo "ota/ota      $OFP"
echo "ota/ota_next $NFP"
