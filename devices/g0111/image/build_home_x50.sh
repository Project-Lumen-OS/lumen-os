#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# build_home_x50.sh: Lumen Home (apps/LumenHome) for an X50 family image, built from this tree and signed with that
# model's platform key on the Mac (t12, t11 review P0.3). The Z9X 1.0.9 base's Home talks only to org.lumen.projector,
# which the X50 image does not have; this tree's Home picks org.lumen.x50.projector on an X50 family image
# (ro.z9x.device=x50, proj/ProjectorTarget). The
# Z9X image is not touched: its Home stays the pinned overlay/apps_v1 APK.
#   sh devices/g0111/image/build_home_x50.sh [OUT_DIR]      default build/g0111/stage -> OUT_DIR/Z9xHome.apk
# compose_x50.sh installs it over the base's /system/priv-app/Z9xHome/Z9xHome.apk and drops the base's oat files
# (they were compiled for the base APK); verify_tree_x50.py HOME_APP checks it. The private key is read here only.
set -eu
H=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$H/../../.." && pwd)
# X50_CODENAME: the X50 family model (g0111 X50 Ultra by default; any devices/<c>/build.conf with the X50 recipe,
# x50_family.py), signed with that model's own platform key
C=${X50_CODENAME:-g0111}
python3 -B "$H/x50_family.py" check "$C" || { echo "build_home_x50: X50_CODENAME $C is not an X50 family model" >&2; exit 1; }
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
grep -q 'X50_IMAGE = "x50"' "$G/apps/LumenHome/src/org/lumen/home/proj/ProjectorTarget.java" \
  || die "this tree's Home has no X50 family projector target (proj/ProjectorTarget, ro.z9x.device=x50)"
[ -f "$G/apps/core/lumen-menu/assets/fonts/InterVariable.ttf" ] || die "apps/core/lumen-menu/assets/fonts missing (Home's recipe takes the font there)"
mkdir -p "$OUT"
B=$(mktemp -d "${TMPDIR:-/tmp}/home_x50.XXXXXX")
trap 'rm -rf "$B"' EXIT
# Home's recipe (apps/LumenHome/app.layers: the shared icon set apps/core/lumen-icons) assembled first
python3 -B "$G/tools/devices/assemble_app.py" --device "$C" --app Z9xHome --out "$B/tree" --quiet \
  || die "the Z9xHome recipe does not assemble"
env -u TMDB_KEY_FILE KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" BUILD_DIR="$B/build" \
  sh "$G/apps/build_apk.sh" "$B/tree" "$OUT/Z9xHome.apk" > "$B/log" 2>&1 || { tail -20 "$B/log"; die "build failed"; }
APKSIGNER=${BUILD_TOOLS:-${ANDROID_SDK:-$HOME/Library/Android/sdk}/build-tools/36.0.0}/apksigner
got=$("$APKSIGNER" verify --print-certs "$OUT/Z9xHome.apk" | sed -n 's/.*certificate SHA-256 digest: //p' | sort -u)
[ "$got" = "$(certsha "$KEY_PEM")" ] || die "Z9xHome.apk is not signed by the $C platform certificate alone ($got)"
echo "build_home_x50: Z9xHome -> $OUT/Z9xHome.apk ($(sha256in < "$OUT/Z9xHome.apk"))"
