#!/bin/sh
# libs_from_pin.sh PIN_APK [APP_DIR]: puts the native receiver library of a released AirPlay APK into APP_DIR/lib/
# (default apps/LumenAirPlay, git-ignored), so the app (Java, resources, the casting screen) builds from this tree with
# the same native code on any build host. tools/devices/build_apps.sh runs it on the assembled tree when there is no
# lib/. Every library must match jni/LIBS.sha256 and nothing else may be in lib/, else nothing is written (exit 1).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
PIN=${1:?usage: libs_from_pin.sh PIN_APK [APP_DIR]}
DEST=${2:-$HERE}
SUMS=$HERE/jni/LIBS.sha256
[ -f "$PIN" ] || { echo "libs_from_pin: no $PIN" >&2; exit 1; }
W=$(mktemp -d "${TMPDIR:-/tmp}/libs_from_pin.XXXXXX")
trap 'rm -rf "$W"' EXIT
unzip -q -o "$PIN" 'lib/*' -d "$W" 2>/dev/null || { echo "libs_from_pin: $PIN has no lib/" >&2; exit 1; }
cd "$W"
if command -v shasum >/dev/null 2>&1; then CHECK="shasum -a 256 -c"; else CHECK="sha256sum -c"; fi
grep -v '^#' "$SUMS" | $CHECK > /dev/null 2>&1 \
  || { echo "libs_from_pin: $PIN does not carry the libraries of jni/LIBS.sha256" >&2; exit 1; }
want=$(grep -vc '^#' "$SUMS")
[ "$(find lib -type f | wc -l | tr -d ' ')" = "$want" ] || { echo "libs_from_pin: other files in lib/ of $PIN" >&2; exit 1; }
rm -rf "$DEST/lib"
cp -R lib "$DEST/lib"
echo "libs_from_pin: $want libraries from $PIN into $DEST/lib"
