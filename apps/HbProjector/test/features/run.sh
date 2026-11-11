#!/bin/sh
# HbFeatures host check (hal/HbFeatures is plain Java but for android.util.Log, android.os.SystemProperties and
# AidlCaller.NotOnHbException, all stubbed here): the table against the stock G0130 and G0148 feature.xml keys, the
# model rule (G0148, unknown model, no file), off wins, require(). sh test/features/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/features
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/hal/HbFeatures.java" \
  "$T/stubs/android/util/Log.java" "$T/stubs/android/os/RemoteException.java" \
  "$T/stubs/android/os/SystemProperties.java" \
  "$T/stubs/org/lumen/projector/hal/AidlCaller.java" "$T/HbFeaturesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.hal.HbFeaturesTest "$T/feature_g0130.xml" "$T/feature_g0148.xml"
