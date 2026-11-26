#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of problem reports (apps/core/lumen-report): sh apps/core/lumen-report/test/run.sh
#  1. ReportTest: redaction (Scrubber, LogcatFilter, PropFilter), what counts as an incident, consent and limits of
#     automatic reports, the queue, the address, the zip, the answer parsing, the unexpected stop (every normal way
#     out of a start against a freeze and a pulled plug);
#  2. every module source compiles against Android 34 (android.jar + the framework headers, a stub R of the module
#     resources), so the Android side is checked without a device;
#  3. report_helper_test.sh: the image's root log helper (tokens, refusals, scrub, caps, file modes, cleanup);
#  4. check_repo.py: texts in every language, the wiring of every projector app, the menu row, the consent lines, the
#     log helper wiring (no log access dialog path, every report app may read it, staged for every image).
#  5. ui/guard_test.py: the key walk sends only to a running fake server on this host and stops on anything else.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_report.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/report
PURE="Scrubber LogcatFilter ReportZip ReportUploader PartUpload ReportFailure ReportKind CanceledException OwnApps PropFilter Incident
IncidentScan AutoPolicy ReportQueue ReportUrl Alive AliveState AliveFile HdmiDump CecJournal RootLogs PowerDump ReportState ReportView"
set --
for c in $PURE; do set -- "$@" "$S/$c.java"; done
mkdir -p "$OUT/pure" "$OUT/android/org/lumen/projector" "$OUT/android/org/lumen/icons"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/pure" "$@" "$HERE/test/org/lumen/report/ReportTest.java"
# a throwaway TLS key for the frozen-upload test (a local server that stops reading the body)
"$JAVA_HOME/bin/keytool" -genkeypair -keyalg EC -groupname secp256r1 -alias t -dname CN=localhost \
  -ext SAN=dns:localhost,ip:127.0.0.1 -validity 2 -keystore "$OUT/tls.p12" -storetype PKCS12 \
  -storepass lumen-test -keypass lumen-test >/dev/null 2>&1
"$JAVA_HOME/bin/java" -Dlumen.report.tls="$OUT/tls.p12" -cp "$OUT/pure" org.lumen.report.ReportTest
# the stub R: every string of the base file and every drawable of the module
{
  echo "package org.lumen.projector;"
  echo "public final class R {"
  echo "  public static final class string {"
  sed -n 's/.*<string name="\([a-z0-9_]*\)">.*/    public static final int \1 = 1;/p' "$HERE/res/values/lumen_strings_report.xml"
  echo "  }"
  echo "  public static final class drawable {"
  for d in "$HERE"/res/drawable/*.xml; do echo "    public static final int $(basename "$d" .xml) = 2;"; done
  echo "  }"
  echo "}"
} > "$OUT/android/org/lumen/projector/R.java"
# the shared look the screen is drawn in (apps/core/lumen-ui: palette, theme follow, the two-pane page)
U=$G/apps/core/lumen-ui/src/org/lumen/ui
# the shared icon set the screen draws its list bullets with (apps/core/lumen-icons): its sources and a stub R of them
I=$G/apps/core/lumen-icons
{
  echo "package org.lumen.icons;"
  echo "public final class R {"
  for k in drawable color; do
    echo "  public static final class $k {"
    for d in "$I/res/$k"/*.xml; do echo "    public static final int $(basename "$d" .xml) = 3;"; done
    echo "  }"
  done
  echo "}"
} > "$OUT/android/org/lumen/icons/R.java"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -d "$OUT/android" -cp "$HJ:$AJ" \
  "$OUT/android/org/lumen/projector/R.java" "$OUT/android/org/lumen/icons/R.java" "$S"/*.java "$I"/src/org/lumen/icons/*.java \
  "$U"/*.java "$U"/page/*.java
echo "ok   android: $(ls "$S"/*.java | wc -l | tr -d ' ') module sources compile against android-34"
bash "$HERE/test/report_helper_test.sh"
python3 -B "$HERE/tools/report_icons.py" check
python3 -B "$HERE/test/check_repo.py" "$G"
python3 -B "$HERE/test/ui/guard_test.py"
