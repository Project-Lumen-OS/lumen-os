#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# HDMI-CEC wake replay (cec/WakeRules, EarcRules, lumen-cec NightGuard, lumen-hdmi HdmiLink, the CEC defaults table): sh test/cecwake/run.sh [TABLE]
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
TABLE=${1:-$G/devices/_image/cec_defaults.tsv}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/cecwake.XXXXXX")
: "${OUT:?}"
trap 'rm -rf "$OUT"' EXIT
C=$HERE/src/org/lumen/projector/cec
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$C/WakeRules.java" "$C/EarcRules.java" "$C/EarcStep.java" \
  "$G/apps/core/lumen-cec/src/org/lumen/projector/cec/NightGuard.java" \
  "$G/apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiLink.java" "$HERE/test/cecwake/WakeReplayTest.java"
# the second argument: the wake lines of evleo790's LR-9MEQZP journal (night guard replay)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.cec.WakeReplayTest "$TABLE" "$HERE/test/cecwake/lr_9meqzp_wake.txt"
