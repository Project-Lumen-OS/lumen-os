#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-parental host tests: the state format, the rules, the time limits, the password (plain Java, no Android).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/parental"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -d "$OUT" "$S/ControlRange.java" "$S/LimitState.java" "$S/PinCode.java" \
  "$S/Quiz.java" "$S/ParentalState.java" "$S/ParentalRules.java" "$S/ParentalSettings.java" "$HERE/test/org/lumen/parental/ParentalTest.java" \
  "$HERE/test/ParentalStub.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.parental.ParentalTest
