#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# build_play6.sh: build the APKs of the Play 6 port with the Play 6 platform key (the design notes
# 2.1 rule S1, section 5 check B). Mac only, no device, no network.
#
#   tools/play6/build_play6.sh
#
# 1. assembles the projector app from its recipe devices/dragon_healthy/app/Play6Projector.layers
#    (tools/devices/assemble_app.py: apps/common/projector by its FILES and HidlCaller, then
#    apps/Play6Projector on top) into OUT_DIR/tree/Play6Projector; a recipe error stops the build
# 2. checks H (check_play6_hal.py) and K (check_handshake.py) on that assembled tree, and F (check_forks.py);
#    any red stops the build
# 3. apps/build_apk.sh for the assembled Play6Projector, apps/Play6FrameworkOverlay, apps/Play6Updater and, once it
#    exists, apps/Play6TvSettingsOverlay, signed with the Play 6 platform key -> build/play6/<App>.apk
# 4. the shared screen mirroring receiver apps/LumenMiracast (Java only, the version of its 1.0.11 line: its manifest
#    names none) and its Wi-Fi Direct device type overlay apps/LumenWifiP2pOverlay, the same sources as the Z9X image,
#    on the Play 6 platform key -> build/play6/LumenMiracast.apk, build/play6/LumenWifiP2pOverlay.apk
#
# Key (S1: never a certificate a Z9X image trusts):
#   KEY_PK8  default ~/.lumen-keys/play6/platform.pk8 (made by tools/play6/gen_play6_keys.sh; the private key
#            stays on the Mac and outside this tree)
#   KEY_PEM  default tools/play6/certs/platform.x509.pem (the public cert, the only part in the tree)
# Refused: a key file inside the project tree, the public AOSP test certs (apps/sdk/platform.x509.pem,
# tools/sign/testcerts) and every Lumen release cert of tools/sign/release_certs/FINGERPRINTS.txt.
# build_apk.sh verifies after signing that the APK's signer is exactly KEY_PEM.
# OUT_DIR (default build/play6) takes the APKs; each app builds in OUT_DIR/apk/<App> (wiped by build_apk.sh),
# so apps/Play6*/build is not used. OUT_DIR/tree/Play6Projector is the assembled projector tree (rewritten each run).
# HOME_SRC=1 also builds Lumen Home from its source (apps/LumenHome) with the Play 6 key -> OUT_DIR/Z9xHome.apk,
# for a test image that carries a Home newer than the Z9X pin (build_test.sh --home-src); TVINPUT_SRC=1 the same
# for the TV input app (apps/LumenTvInput -> OUT_DIR/Z9xTvInput.apk, build_test.sh --tvinput-src), SETUP_SRC=1 for
# Lumen Setup (apps/LumenSetup -> OUT_DIR/Z9xSetup.apk, build_test.sh --setup-src). Such a source
# build keeps the version of its pin: a manifest without versionCode / versionName (Z9xTvInput) gets the pin's
# (aapt2 dump badging of overlay/apps_v1/<App>.apk, as z9x_gate.sh rebuilds it); a manifest that has them keeps
# them. The Z9X pins in overlay/apps_v1 and the Z9X image are not touched.
set -euo pipefail
die() { echo "build_play6: $*" >&2; exit 1; }
G=$(cd "$(dirname "$0")/../.." && pwd)
P="$G/tools/play6"
OUT=${OUT_DIR:-$G/build/play6}
KEY_PK8=${KEY_PK8:-$HOME/.lumen-keys/play6/platform.pk8}
KEY_PEM=${KEY_PEM:-$P/certs/platform.x509.pem}

# the projector app is built from its recipe: the shared files are never copied into apps/Play6Projector
TREE=$OUT/tree/Play6Projector
python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app Play6Projector --out "$TREE" \
  || die "devices/dragon_healthy/app/Play6Projector.layers does not assemble"
python3 -B "$P/check_play6_hal.py" "$TREE" || die "check H failed"
python3 -B "$P/check_handshake.py" "$TREE" || die "check K failed"
python3 -B "$P/check_forks.py" || die "check F failed"

[ -f "$KEY_PK8" ] || die "no Play 6 private key $KEY_PK8 (owner decision O1: tools/play6/gen_play6_keys.sh)"
[ -f "$KEY_PEM" ] || die "no Play 6 certificate $KEY_PEM"
KD=$(cd "$(dirname "$KEY_PK8")" && pwd -P)
case $KD in   # the same rule as tools/sign/gen_keys.sh
  *"XGIMI PLAY 6"*|*/gsi/*|*/gsi) die "the private key $KEY_PK8 lies inside the project tree" ;;
esac
FP=$(openssl x509 -in "$KEY_PEM" -outform DER | shasum -a 256 | cut -d' ' -f1)
for c in "$G/apps/sdk/platform.x509.pem" "$G"/tools/sign/testcerts/*.x509.pem; do
  [ -f "$c" ] || continue
  [ "$(openssl x509 -in "$c" -outform DER | shasum -a 256 | cut -d' ' -f1)" != "$FP" ] \
    || die "$KEY_PEM is the public AOSP test cert $(basename "$c") (rule S1)"
done
if grep -qi "$FP" "$G/tools/sign/release_certs/FINGERPRINTS.txt"; then
  die "$KEY_PEM is a Lumen release cert (rule S1)"
fi
echo "build_play6: Play 6 platform cert SHA-256 $FP"

mkdir -p "$OUT"
for app in Play6Projector Play6FrameworkOverlay Play6TvSettingsOverlay Play6Updater; do
  [ -d "$G/apps/$app" ] || continue
  src=$G/apps/$app
  [ "$app" = Play6Projector ] && src=$TREE
  # the updater is assembled from its recipe too (devices/dragon_healthy/app/Play6Updater.layers, the shared module
  # apps/core/lumen-updater plus the Play 6 Target and manifest)
  if [ "$app" = Play6Updater ]; then
    src=$OUT/tree/Play6Updater
    python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app Play6Updater --out "$src" --quiet \
      || die "the Play6Updater recipe does not assemble"
  fi
  BUILD_DIR="$OUT/apk/$app" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" \
    sh "$G/apps/build_apk.sh" "$src" "$OUT/$app.apk"
done
# the receivers of every Lumen projector, no per-model copy: AirPlay with the casting screen from its recipe
# (apps/LumenAirPlay/app.layers, its native library from the released pin, checked against jni/LIBS.sha256, screen
# casting on every model 2026-11-21); the receiver's version line is apps/LumenMiracast/BUILD_VERSION, as
# tools/devices/build_apps.sh reads it
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" "$G/apps/LumenAirPlay/app.layers" --out "$OUT/tree/Z9xAirPlay" --quiet \
  || die "the Z9xAirPlay recipe does not assemble"
[ -d "$OUT/tree/Z9xAirPlay/lib" ] || sh "$G/apps/LumenAirPlay/tools/libs_from_pin.sh" "$G/overlay/apps_v1/Z9xAirPlay.apk" "$OUT/tree/Z9xAirPlay" \
  || die "no AirPlay native library: overlay/apps_v1/Z9xAirPlay.apk does not match apps/LumenAirPlay/jni/LIBS.sha256"
read -r AP_VC AP_VN < "$G/apps/LumenAirPlay/BUILD_VERSION" || die "no apps/LumenAirPlay/BUILD_VERSION"
BUILD_DIR="$OUT/apk/Z9xAirPlay" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" VERSION_CODE=$AP_VC VERSION_NAME=$AP_VN \
  sh "$G/apps/build_apk.sh" "$OUT/tree/Z9xAirPlay" "$OUT/Z9xAirPlay.apk"
python3 -B "$G/devices/_image/checks/airplay_libs.py" "$OUT/Z9xAirPlay.apk" || die "Z9xAirPlay: armeabi-v7a library not stored and aligned"
read -r MC_VC MC_VN < "$G/apps/LumenMiracast/BUILD_VERSION" || die "no apps/LumenMiracast/BUILD_VERSION"
BUILD_DIR="$OUT/apk/LumenMiracast" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" VERSION_CODE=$MC_VC VERSION_NAME=$MC_VN \
  sh "$G/apps/build_apk.sh" "$G/apps/LumenMiracast" "$OUT/LumenMiracast.apk"
BUILD_DIR="$OUT/apk/LumenWifiP2pOverlay" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" \
  sh "$G/apps/build_apk.sh" "$G/apps/LumenWifiP2pOverlay" "$OUT/LumenWifiP2pOverlay.apk"
# the package installer in the Lumen look (apps/LumenPackageInstallerOverlay/app.layers adds the shared Inter font)
python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app LumenPackageInstallerOverlay --out "$OUT/tree/LumenPackageInstallerOverlay" --quiet \
  || die "the LumenPackageInstallerOverlay recipe does not assemble"
BUILD_DIR="$OUT/apk/LumenPackageInstallerOverlay" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" \
  sh "$G/apps/build_apk.sh" "$OUT/tree/LumenPackageInstallerOverlay" "$OUT/LumenPackageInstallerOverlay.apk"
# Files, the file manager of every model (apps/LumenFiles/app.layers: the shared icons and look)
python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app LumenFiles --out "$OUT/tree/LumenFiles" --quiet \
  || die "the LumenFiles recipe does not assemble"
BUILD_DIR="$OUT/apk/LumenFiles" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" \
  sh "$G/apps/build_apk.sh" "$OUT/tree/LumenFiles" "$OUT/LumenFiles.apk"
# the on-screen keyboard of every model (apps/LumenKeyboard: keyboard languages, the language key), in LeanbackIME's place
BUILD_DIR="$OUT/apk/LumenKeyboard" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" \
  sh "$G/apps/build_apk.sh" "$G/apps/LumenKeyboard" "$OUT/LumenKeyboard.apk"
SRC=
[ "${HOME_SRC:-0}" = 1 ] && SRC="$SRC Z9xHome"
[ "${TVINPUT_SRC:-0}" = 1 ] && SRC="$SRC Z9xTvInput"
[ "${SETUP_SRC:-0}" = 1 ] && SRC="$SRC Z9xSetup"
AAPT2=${BUILD_TOOLS:-${ANDROID_SDK:-$HOME/Library/Android/sdk}/build-tools/36.0.0}/aapt2
for app in $SRC; do
  pin=$G/overlay/apps_v1/$app.apk
  badge=$("$AAPT2" dump badging "$pin" 2>/dev/null | head -1) || true
  vc=$(printf '%s' "$badge" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")
  vn=$(printf '%s' "$badge" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")
  [ -n "$vc" ] && [ -n "$vn" ] || die "cannot read the version of $pin"
  key=   # Lumen Home: the TMDB key file when the pin carries one (z9x_gate.sh does the same)
  if [ "$app" = Z9xHome ] && unzip -l "$pin" assets/tmdb_key >/dev/null 2>&1; then key=${TMDB_KEY_FILE:-$HOME/.lumen-keys/tmdb.txt}; fi
  # an app with a recipe (Lumen Home: apps/LumenHome/app.layers, the shared icon set) is assembled first
  src=$G/$(python3 -B "$G/tools/devices/assemble_app.py" --app-dir "$app")   # apps/app_dirs.tsv: a renamed folder
  if [ -f "$src/app.layers" ]; then
    src=$OUT/tree/$app
    python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app "$app" --out "$src" --quiet \
      || die "the $app recipe does not assemble"
  fi
  BUILD_DIR="$OUT/apk/$app" KEY_PK8="$KEY_PK8" KEY_PEM="$KEY_PEM" VERSION_CODE="$vc" VERSION_NAME="$vn" TMDB_KEY_FILE="$key" \
    sh "$G/apps/build_apk.sh" "$src" "$OUT/$app.apk"
done
echo "build_play6: OK ($OUT)"
