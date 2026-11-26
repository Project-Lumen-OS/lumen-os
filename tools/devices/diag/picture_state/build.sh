#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# build.sh: compiles PictureState.java into picture_state.jar (a dex jar for app_process) next to it.
#   sh tools/devices/diag/picture_state/build.sh [OUT.jar]
# Needs a JDK 17+ (javac on PATH or JAVA_HOME) and d8 of the Android SDK build tools
# (BUILD_TOOLS, default $ANDROID_SDK/build-tools/36.0.0). The jar is a build product and is not in git.
set -eu
H=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$H/../../../.." && pwd)
OUT=${1:-$H/picture_state.jar}
ANDROID_SDK=${ANDROID_SDK:-$HOME/Library/Android/sdk}
BUILD_TOOLS=${BUILD_TOOLS:-$ANDROID_SDK/build-tools/36.0.0}
[ -n "${JAVA_HOME:-}" ] && PATH=$JAVA_HOME/bin:$PATH
HEADERS=$G/apps/sdk/framework-full-headers.jar
[ -f "$HEADERS" ] || { echo "build.sh: no $HEADERS" >&2; exit 1; }
[ -x "$BUILD_TOOLS/d8" ] || { echo "build.sh: no d8 in $BUILD_TOOLS (set BUILD_TOOLS)" >&2; exit 1; }
W=$(mktemp -d "${TMPDIR:-/tmp}/picture_state.XXXXXX")
trap 'rm -rf "$W"' EXIT
mkdir "$W/cls" "$W/dex"
javac -nowarn -source 17 -target 17 -cp "$HEADERS" -d "$W/cls" "$H/PictureState.java" 2>&1 | grep -v 'system modules path' || true
[ -f "$W/cls/PictureState.class" ] || { echo "build.sh: javac failed" >&2; exit 1; }
"$BUILD_TOOLS/d8" --min-api 34 --classpath "$HEADERS" --output "$W/dex" "$W"/cls/*.class
rm -f "$OUT"
(cd "$W/dex" && zip -q -X "$OUT" classes.dex)
echo "built $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes)"
