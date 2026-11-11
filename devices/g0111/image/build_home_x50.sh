#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# build_home_x50.sh: Lumen Home (apps/Z9xHome) for the X50 image, built from this tree and signed with the g0111
# platform key on the Mac (t12, t11 review P0.3). The Z9X 1.0.9 base's Home talks only to org.lumen.projector, which
# the X50 image does not have; this tree's Home picks org.lumen.x50.projector on G0111 (proj/ProjectorTarget). The
# Z9X image is not touched: its Home stays the pinned overlay/apps_v1 APK.
#   sh devices/g0111/image/build_home_x50.sh [OUT_DIR]      default build/g0111/stage -> OUT_DIR/Z9xHome.apk
# compose_x50.sh installs it over the base's /system/priv-app/Z9xHome/Z9xHome.apk and drops the base's oat files
# (they were compiled for the base APK); verify_tree_x50.py HOME_APP checks it. The private key is read here only.
set -eu
H=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$H/../../.." && pwd)
# X50_CODENAME: g0111 X50 Ultra (default) or g0100 X50 Ultra Max (its own platform key, 2026-11-11)
C=${X50_CODENAME:-g0111}
case $C in g0111|g0100) ;; *) echo "build_home_x50: X50_CODENAME $C is not an X50 model" >&2; exit 1 ;; esac
OUT=${1:-$G/build/$C/stage}
die() { echo "build_home_x50: $*" >&2; exit 1; }
sha256in() { if command -v sha256sum >/dev/null; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi; }
certsha() { openssl x509 -in "$1" -outform DER | sha256in; }
KD=$(python3 -B "$G/tools/devices/devices.py" get "$C" keys_dir)
case $KD in "~/"*) KD=$HOME/${KD#\~/} ;; esac
KEY_PK8=$KD/platform.pk8
KEY_PEM=$G/devices/$C/certs/platform.x509.pem
[ -f "$KEY_PK8" ] || die "no $C platform key (owner, on the Mac)"
[ "$(certsha "$KEY_PEM")" = "$(certsha "$KD/platform.x509.pem")" ] || die "$KEY_PEM is not the certificate of the $C key"
grep -q 'MODEL_X50 = "G0111"' "$G/apps/Z9xHome/src/org/lumen/home/proj/ProjectorTarget.java" \
  && grep -q 'MODEL_X50_MAX = "G0100"' "$G/apps/Z9xHome/src/org/lumen/home/proj/ProjectorTarget.java" \
  || die "this tree's Home has no X50 projector target (proj/ProjectorTarget)"
[ -f "$G/apps/Z9xHome/assets/fonts/Onest-Variable.ttf" ] || die "apps/Z9xHome/assets/fonts missing (fetched, not in git)"
mkdir -p "$OUT"
B=$(mktemp -d "${TMPDIR:-/tmp}/home_x50.XXXXXX")
trap 'rm -rf "$B"' EXIT
env -u TMDB_KEY_FILE KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" BUILD_DIR="$B/build" \
  sh "$G/apps/build_apk.sh" "$G/apps/Z9xHome" "$OUT/Z9xHome.apk" > "$B/log" 2>&1 || { tail -20 "$B/log"; die "build failed"; }
APKSIGNER=${BUILD_TOOLS:-${ANDROID_SDK:-$HOME/Library/Android/sdk}/build-tools/36.0.0}/apksigner
got=$("$APKSIGNER" verify --print-certs "$OUT/Z9xHome.apk" | sed -n 's/.*certificate SHA-256 digest: //p' | sort -u)
[ "$got" = "$(certsha "$KEY_PEM")" ] || die "Z9xHome.apk is not signed by the $C platform certificate alone ($got)"
echo "build_home_x50: Z9xHome -> $OUT/Z9xHome.apk ($(sha256in < "$OUT/Z9xHome.apk"))"
