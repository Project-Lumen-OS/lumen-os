#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# CEC System Audio decisions (audio/HbAudioRules) and the sound output rows (audio/HbOutputRows), plain Java, no stubs,
# 2026-11-01; the 3.5 mm output wiring (lumen-audio JackOutput through HbWiredJack, check_wiring.py): sh test/audio/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/audio
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
# HbAudioRules asks lumen-audio SoundbarMode: next to apps/HbProjector, else in LUMEN_GSI (the assembled tree of build.sh)
SBM=$HERE/../core/lumen-audio/src/org/lumen/audio/SoundbarMode.java
[ -f "$SBM" ] || SBM=${LUMEN_GSI:?LUMEN_GSI}/apps/core/lumen-audio/src/org/lumen/audio/SoundbarMode.java
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/audio/HbAudioRules.java" "$SBM" "$T/HbAudioRulesTest.java" \
  "$HERE/src/org/lumen/projector/audio/HbOutputRows.java" "$T/HbOutputRowsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.audio.HbAudioRulesTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.audio.HbOutputRowsTest
python3 "$T/check_wiring.py"
