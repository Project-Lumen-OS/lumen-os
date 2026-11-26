#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the shared updater (apps/core/lumen-updater): sh apps/core/lumen-updater/test/run.sh
#   1. the model-free parts on the module sources: Outcome (the result after a restart), the one-time statistics
#      notice, UpdateService's worker (JobRunner: a second "Install now" is dropped, only the newest job ends the
#      service), the gate block strings in every locale (strings_parity.py);
#   2. per updater recipe (Z9X, HB, Play 6), assembled: the statistics adapter (StatsTest), the facts of the two
#      install ways (KindTest), the model table of README.md (TargetFactsTest), the service wiring (one UpdateService, the install drop of each InstallWay);
#   3. PackageDownload over the shared download (test/download.sh; the download itself: apps/core/lumen-download/test/run.sh);
#   4. parity with the three updaters it replaced (test/parity/run.sh: behaviour transcript + static face).
# Host only, no device, no network (the download test talks to its own server on loopback). Writes only a temp dir. The model checks stay with each model's layer
# (apps/Z9xUpdater/test/run.sh, apps/LumenUpdater/test/ota/run.sh, apps/Play6Updater/test/run.sh).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_updater_core.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/updater
TS=$HERE/test/org/lumen/updater

"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/o" "$S/Outcome.java" "$S/BootVerdict.java" \
  "$S/GateVerdict.java" "$S/GateReason.java" "$S/InstallError.java" "$TS/OutcomeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/o" org.lumen.updater.OutcomeTest
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/notice" "$S/NoticeRule.java" "$TS/NoticeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/notice" org.lumen.updater.NoticeTest
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/jobs" "$S/ServiceJob.java" "$S/JobRunner.java" \
  "$TS/JobRunnerTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/jobs" org.lumen.updater.JobRunnerTest
python3 -B "$HERE/test/strings_parity.py" "$HERE" "$HERE/res"
# the texts people see: no DSU, vbmeta or slot in any language of any app (the updater's consent and block texts)
python3 -B "$G/tools/i18n/test/ui_words_test.py" "$G"
# a javac argument file, one quoted path per line: the checkout path may hold blanks ("XGIMI PLAY 6")
find "$HERE/test/parity/json" -name '*.java' | sed 's/.*/"&"/' > "$OUT/json.files"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/json" @"$OUT/json.files"

# the model table (README.md): the facts of each recipe's Target
D=https://github.com/Project-Lumen-OS/lumen-os/releases/download/
GD=https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/
facts() {
  case $1 in
    Z9xUpdater) echo "props=ro.z9x device=z9x versions=LEGACY channels=FIXED default=${D}z9x/update-public-nogms.json" \
      "beta=${D}z9x/update-public-beta.json guide=${GD}z9x/ dir=APP_DATA install=UPDATE_ENGINE checks=Z9X_GATE" \
      "dsu=false import=true hide=false vabc=" ;;
    LumenUpdater) echo "props=ro.lumen device=prop:ro.lumen.ota.device versions=O1 channels=PAIR default= beta=null" \
      "guide=null dir=APP_DATA install=UPDATE_ENGINE checks=prop:ro.lumen.ota.boot_gate:LUMEN_GATE dsu=true" \
      "import=false hide=true vabc=x50,x50max" ;;
    Play6Updater) echo "props=ro.lumen device=play6 versions=LEGACY channels=FIXED default=${D}play6/update-play6.json" \
      "beta=null guide=${GD} dir=CACHE install=RECOVERY checks=RECOVERY dsu=false import=true hide=false vabc=" ;;
  esac
}

for spec in "horse_popular Z9xUpdater" "goat_elegant LumenUpdater" "dragon_healthy Play6Updater"; do
  set -- $spec
  A=$OUT/$2
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device "$1" --app "$2" --out "$A" --quiet
  mkdir -p "$OUT/stats-$2"
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/stats-$2" -cp "$HJ:$AJ" -sourcepath "$A/src" \
    "$A/src/org/lumen/updater/Stats.java" "$TS/StatsTest.java"
  "$JAVA_HOME/bin/java" -cp "$OUT/stats-$2:$AJ:$HJ" org.lumen.updater.StatsTest
  mkdir -p "$OUT/kind-$2"
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/kind-$2" -cp "$OUT/json:$HJ:$AJ" -sourcepath "$A/src" \
    "$TS/KindTest.java"
  "$JAVA_HOME/bin/java" -cp "$OUT/kind-$2:$OUT/json:$AJ:$HJ" org.lumen.updater.KindTest
  mkdir -p "$OUT/facts-$2"
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/facts-$2" -cp "$HJ:$AJ" -sourcepath "$A/src" \
    "$TS/TargetFactsTest.java"
  # shellcheck disable=SC2046
  "$JAVA_HOME/bin/java" -cp "$OUT/facts-$2:$AJ:$HJ" org.lumen.updater.TargetFactsTest $(facts "$2")
done

# the one UpdateService goes through JobRunner, and each way drops a second install its own way (the Z9X and the HB:
# the running INSTALL job repeats it; the Play 6: any running job, so the USB copy and the signature check never race)
svc=$S/UpdateService.java
for want in 'private final JobRunner jobs = new JobRunner();' 'if (!jobs.mayEnd()) return;' 'jobs.start(kind, () -> {' \
    'if (InstallWay.current().dropsInstall(this)) break;'; do
  grep -qF "$want" "$svc" || { echo "FAIL UpdateService: missing: $want"; exit 1; }
done
if grep -qE 'Thread (prev|w) = worker' "$svc"; then echo "FAIL UpdateService: a worker outside JobRunner"; exit 1; fi
grep -qF 'public boolean dropsInstall(ServiceHost h) { return h.repeat(ServiceJob.INSTALL); }' "$S/ViaUpdateEngine.java" \
  || { echo "FAIL update_engine way: second Install now not dropped"; exit 1; }
grep -qF 'public boolean dropsInstall(ServiceHost h) { return h.busy(); }' "$S/ViaRecovery.java" \
  || { echo "FAIL recovery way: an install races a running job"; exit 1; }
# every model is data: no model name in the module's code
if grep -nE '"(z9x|hb|play6|x50|x50max|G0[0-9]{3}|horse_popular|goat_elegant|dragon_healthy)"' "$S"/*.java; then
  echo "FAIL a model name in the shared updater code (it belongs to a Target)"; exit 1
fi
echo "service wiring passed=3 failed=0"

sh "$HERE/test/download.sh"
sh "$HERE/test/parity/run.sh"
echo "lumen-updater host tests: green"
