#!/bin/sh
# Updater host checks (Outcome: the result after a restart, plain Java; 1.0.1: the edition's manifest file; the
# Beta updates switch): sh test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/updater/Outcome.java" "$HERE/src/org/lumen/updater/BootVerdict.java" \
  "$HERE/src/org/lumen/updater/GateVerdict.java" "$HERE/src/org/lumen/updater/GateReason.java" \
  "$HERE/src/org/lumen/updater/InstallError.java" "$HERE/test/org/lumen/updater/OutcomeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.updater.OutcomeTest
# 1.0.1: the edition's manifest file (Ota.channelFile; Ota links android classes: android.jar stubs)
# (hidden APIs: our framework header jar to compile, android.jar first to run)
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$HERE/../sdk/framework-full-headers.jar
mkdir -p "$OUT/ch"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/ch" -cp "$HJ:$AJ" \
  "$HERE/src/org/lumen/updater/Ota.java" "$HERE/test/org/lumen/updater/ChannelTest.java"
"$JAVA_HOME/bin/java" -Dlumen.gsi="$HERE/../.." -cp "$OUT/ch:$AJ:$HJ" org.lumen.updater.ChannelTest
# Beta updates switch: the channels read, the higher one offered, and no downgrade when it is turned off (Ota, Preflight with the
# SystemProperties stand-in of test/stubs, which comes first on the class path)
mkdir -p "$OUT/beta"
S=$HERE/src/org/lumen/updater
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/beta" -cp "$HJ:$AJ" \
  "$HERE/test/stubs/android/os/SystemProperties.java" "$S/Ota.java" "$S/Channels.java" "$S/Preflight.java" "$S/Engine.java" \
  "$S/UpdateManifest.java" "$HERE/test/org/lumen/updater/BetaTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/beta:$AJ:$HJ" org.lumen.updater.BetaTest
# Anonymous statistics: the adapter over the shared client (apps/core/lumen-stats, the module of the recipe
# devices/horse_popular/app/Z9xUpdater.layers; its own checks: apps/core/lumen-stats/test/run.sh)
mkdir -p "$OUT/stats"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/stats" -cp "$HJ:$AJ" \
  -sourcepath "$HERE/src:$HERE/../core/lumen-stats/src" \
  "$S/Stats.java" "$HERE/test/org/lumen/updater/StatsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/stats:$AJ:$HJ" org.lumen.updater.StatsTest
# One-time statistics notice after an update: due at boot, may show only on the home screen
mkdir -p "$OUT/notice"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/notice" \
  "$S/NoticeRule.java" "$HERE/test/org/lumen/updater/NoticeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/notice" org.lumen.updater.NoticeTest
# UpdateService's worker: a second "Install now" press is dropped, only the newest job ends the service (a test Z9X
# 2026-11-12: the replaced install job stopped the service, the screen stayed at 0 %)
mkdir -p "$OUT/jobs"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/jobs" \
  "$S/ServiceJob.java" "$S/JobRunner.java" "$HERE/test/org/lumen/updater/JobRunnerTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/jobs" org.lumen.updater.JobRunnerTest
# ... and every UpdateService copy (Z9X, the neutral LumenUpdater, Play 6) goes through it
for svc in "$S/UpdateService.java" "$HERE/../LumenUpdater/src/org/lumen/updater/UpdateService.java" \
    "$HERE/../Play6Updater/src/org/lumen/updater/UpdateService.java"; do
  for want in 'private final JobRunner jobs = new JobRunner();' 'if (!jobs.mayEnd()) return;' 'jobs.start(kind, () -> {'; do
    grep -qF "$want" "$svc" || { echo "FAIL $svc: missing: $want"; exit 1; }
  done
  if grep -qE 'Thread (prev|w) = worker' "$svc"; then echo "FAIL $svc: a worker outside JobRunner"; exit 1; fi
done
grep -qF 'if (repeat(ServiceJob.INSTALL)) break;' "$S/UpdateService.java" || { echo "FAIL Z9X: second Install now not dropped"; exit 1; }
grep -qF 'if (repeat(ServiceJob.INSTALL)) break;' "$HERE/../LumenUpdater/src/org/lumen/updater/UpdateService.java" \
  || { echo "FAIL LumenUpdater: second Install now not dropped"; exit 1; }
echo "service wiring passed=3 failed=0"
