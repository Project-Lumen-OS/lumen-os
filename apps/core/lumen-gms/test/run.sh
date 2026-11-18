#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Host checks of the Google sign-in helper switch (apps/core/lumen-gms) against the init script and library it
# drives (devices/_image/gmsprops, lineage/gmsprops). Needs a JDK 17+ (JAVA_HOME or javac on PATH); no Android
# SDK: the only Android class it uses is the SystemProperties stub. sh apps/core/lumen-gms/test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JR=${JAVA_HOME:+$JAVA_HOME/bin/}java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/gms
"$JC" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$HERE/test/stubs/android/os/SystemProperties.java" \
  "$S/GmsHelperRow.java" "$S/GmsHelperPolicy.java" "$S/GmsHelperSwitch.java" "$HERE/test/org/lumen/gms/GmsHelperTest.java"
"$JR" -cp "$OUT" org.lumen.gms.GmsHelperTest "$G"
