#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# HbFeatures host check (hal/HbFeatures is plain Java but for android.util.Log, android.os.SystemProperties and
# NotOnHbException, all stubbed here, android.content.Context for its asset): the table against the stock G0130 and G0148 feature.xml keys, the
# model rule (the rows of devices/goat_elegant/hal/model_features.tsv: G0148, unknown model, no file), off wins,
# require(). Runs in the aidl-gmpf adapter folder and in the assembled HB app (build: the rows as its asset).
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/features
M=$HERE/assets/lumen_hal/model_features.tsv
[ -f "$M" ] || M=${LUMEN_GSI:-$HERE/../../../../..}/devices/goat_elegant/hal/model_features.tsv
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/hal/HbFeatures.java" \
  "$T/stubs/android/util/Log.java" "$T/stubs/android/os/RemoteException.java" \
  "$T/stubs/android/content/Context.java" "$T/stubs/android/content/res/AssetManager.java" \
  "$T/stubs/org/lumen/projector/hal/NotOnHbException.java" "$T/HbFeaturesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.hal.HbFeaturesTest "$T/feature_g0130.xml" "$T/feature_g0148.xml" "$M"
