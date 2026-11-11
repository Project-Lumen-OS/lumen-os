#!/bin/sh
# Lumen Miracast core host checks (plain Java, no android.jar): RTSP framing, WFD parameters and
# formats, the sink session state machine, RTP reorder, MPEG-TS demux, audio payloads, the media
# pipeline, the source locator, and one end-to-end run against a scripted source over real
# localhost sockets; then the control API decisions (ControlTest). sh test/run_core.sh ; exit 0 = all passed.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror -proc:none -d "$OUT" \
  "$HERE"/src/org/lumen/miracast/core/*.java "$HERE"/test/org/lumen/miracast/core/*.java
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.CoreTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.miracast.core.ControlTest
# the control API wiring (StateProvider, manifest): CONTROL enforced in call(), the provider guarded by it too,
# a set call only with a valid switch value, the status line from the one shared rule
P="$HERE/src/org/lumen/miracast/StateProvider.java"
M="$HERE/AndroidManifest.xml"
bad=0
grep -A1 '        Context c = getContext();' "$P" | grep -qx '        c.enforceCallingOrSelfPermission(PERM_CONTROL, "screen mirroring control");' \
  || { echo "FAIL provider: CONTROL not enforced first in call()"; bad=1; }
grep -q 'PERM_CONTROL = "org.lumen.miracast.permission.CONTROL"' "$P" || { echo "FAIL provider: wrong permission"; bad=1; }
grep -q 'if (on == null) return null;' "$P" || { echo "FAIL provider: a set call without a valid value changes something"; bad=1; }
grep -q 'Control.Status st = StatusText.status(c);' "$P" || { echo "FAIL provider: status not from StatusText"; bad=1; }
grep -A5 'android:name=".StateProvider"' "$M" | grep -q 'android:readPermission="org.lumen.miracast.permission.CONTROL"' \
  || { echo "FAIL manifest: provider read not guarded by CONTROL"; bad=1; }
grep -A5 'android:name=".StateProvider"' "$M" | grep -q 'android:writePermission="org.lumen.miracast.permission.CONTROL"' \
  || { echo "FAIL manifest: provider write not guarded by CONTROL"; bad=1; }
grep -B2 -A2 'android:name="org.lumen.miracast.permission.CONTROL"' "$M" | grep -q 'android:protectionLevel="signature"' \
  || { echo "FAIL manifest: CONTROL is not a signature permission"; bad=1; }
grep -q 'String st = StatusText.of(this, StatusText.status(this));' "$HERE/src/org/lumen/miracast/SettingsActivity.java" \
  || { echo "FAIL settings: status line not the shared rule"; bad=1; }
[ $bad = 0 ] && echo "control wiring: ok"
exit $bad
