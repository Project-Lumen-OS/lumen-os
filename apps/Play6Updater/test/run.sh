#!/bin/sh
# Updater host checks of the Play 6: sh test/run.sh. apps/Play6Updater is only the Play 6 layer of
# devices/dragon_healthy/app/Play6Updater.layers (over apps/Z9xUpdater), so the checks run on the assembled tree:
# Outcome (the result after a restart), the edition's manifest file and no beta channel (ChannelTest, Play 6 fork),
# the statistics body (StatsTest) and the one-time statistics notice (NoticeTest) of the shared updater code.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/devices/assemble_app.py" --device dragon_healthy --app Play6Updater --out "$OUT/tree" --quiet
A=$OUT/tree
S=$A/src/org/lumen/updater
grep -q 'DEVICE = "play6"' "$S/Ota.java" || { echo "FAIL the assembled Ota.java is not the Play 6 one"; exit 1; }
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/o" "$S/Outcome.java" "$S/BootVerdict.java" "$S/GateVerdict.java" "$S/GateReason.java" \
  "$S/InstallError.java" "$A/test/org/lumen/updater/OutcomeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/o" org.lumen.updater.OutcomeTest
# (hidden APIs: our framework header jar to compile, android.jar first to run)
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
mkdir -p "$OUT/ch"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/ch" -cp "$HJ:$AJ" \
  "$A/test/stubs/android/os/SystemProperties.java" "$S/Ota.java" "$S/Channels.java" "$A/test/org/lumen/updater/ChannelTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/ch:$AJ:$HJ" org.lumen.updater.ChannelTest
mkdir -p "$OUT/stats"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/stats" -cp "$HJ:$AJ" -sourcepath "$A/src" \
  "$S/Stats.java" "$A/test/org/lumen/updater/StatsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/stats:$AJ:$HJ" org.lumen.updater.StatsTest
mkdir -p "$OUT/notice"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/notice" "$S/NoticeRule.java" "$A/test/org/lumen/updater/NoticeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/notice" org.lumen.updater.NoticeTest
# the shared worker of UpdateService (JobRunner, ServiceJob): a second "Install now" is dropped, only the newest job
# ends the service (a test Z9X 2026-11-12)
mkdir -p "$OUT/jobs"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/jobs" "$S/ServiceJob.java" "$S/JobRunner.java" \
  "$A/test/org/lumen/updater/JobRunnerTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/jobs" org.lumen.updater.JobRunnerTest
