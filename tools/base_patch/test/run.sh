#!/bin/sh
# base_patch host tests (README.md). JDK 17+ (JAVA_HOME or javac on PATH), python3.
#   sh tools/base_patch/test/run.sh                  the A2DP race on the shipped and the fixed connector, the About
#                                                    rows, base_patch.py
#   BASE_APK=.../TvSettingsTwoPanel.apk sh ...       also builds the real patched dex and checks it against its pin
#                                                    (needs BUILD_TOOLS or an SDK with build-tools 36.0.0)
set -eu
T=$(cd "$(dirname "$0")" && pwd)
P=$(cd "$T/.." && pwd)
G=$(cd "$P/../.." && pwd)
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
rc=0
# 1. Add accessory, A2DP: the LineageOS 21 connector the images ship says Couldn't pair when the speaker is up
#    before it listens (S1, the HB report) or connects straight from Disconnected (S2); the fix reports Paired and
#    keeps every real failure (S4 to S7)
# javac reads an @file split at blanks: every path quoted (the tree lives under "XGIMI PLAY 6")
{ find "$T/stubs" "$T/harness" -name '*.java'
  echo "$P/tvsettings_a2dp/stubs/com/android/tv/settings/accessories/BluetoothDevicePairer.java"; } |
  sed 's/.*/"&"/' > "$OUT/base.list"
for s in lineage21 fixed; do
  src="$P/tvsettings_a2dp/BluetoothA2dpConnector.java"
  [ "$s" = lineage21 ] && src="$T/lineage21/BluetoothA2dpConnector.java"
  mkdir -p "$OUT/$s"
  "$JAVAC" -encoding UTF-8 -nowarn -d "$OUT/$s" @"$OUT/base.list" "$src" "$T/A2dpRaceTest.java"
  "$JAVA" -cp "$OUT/$s" A2dpRaceTest > "$OUT/$s.txt"
  if diff -u "$T/expected_$s.txt" "$OUT/$s.txt"; then
    echo "PASS race $s ($(wc -l < "$OUT/$s.txt" | tr -d ' ') scenarios)"
  else
    echo "FAIL race $s"
    rc=1
  fi
done
# 1b. About: the rows of the projector's provider org.lumen.about go right after Model, only from a system app, a
#     failing or missing provider leaves the page as it is (the Lumen part of the fix, test/about_extract.py)
mkdir -p "$OUT/about"
python3 "$T/about_extract.py" "$P/tvsettings_about/AboutFragment.java" "$OUT/about/AboutFragment.java"
{ find "$T/about_stubs" -name '*.java'
  for c in Preference PreferenceGroup PreferenceScreen; do echo "$P/tvsettings_about/stubs/androidx/preference/$c.java"; done
  echo "$OUT/about/AboutFragment.java"; echo "$T/AboutRowsTest.java"; } | sed 's/.*/"&"/' > "$OUT/about.list"
if "$JAVAC" -encoding UTF-8 -nowarn -d "$OUT/about/cls" @"$OUT/about.list" &&
   "$JAVA" -cp "$OUT/about/cls" com.android.tv.settings.about.AboutRowsTest > "$OUT/about.txt"; then
  echo "PASS about rows ($(grep -c '^PASS' "$OUT/about.txt") checks)"
else
  cat "$OUT/about.txt" 2>/dev/null || true
  echo "FAIL about rows"
  rc=1
fi
# 2. base_patch.py: zip swap, pins, the dexdump comparison, the chain of fixes of one file, the reference check
python3 "$T/base_patch_test.py" || rc=1
# 3. the signer of HB, RS30 and Titan signs TvSettings from the patched copy
python3 "$T/signer_hook_test.py" || rc=1
# 4. the real dex (only with BASE_APK): every fix of the chain built, every other class unchanged, each reference
#    of the new classes resolves, each result equal to its pin
if [ -n "${BASE_APK:-}" ]; then
  for f in tvsettings_a2dp tvsettings_about; do
    if python3 "$P/base_patch.py" build $f "$BASE_APK" --out "$OUT/dex"; then
      echo "PASS real dex build $f"
    else
      echo "FAIL real dex build $f"
      rc=1
    fi
  done
fi
[ $rc = 0 ] && echo "base_patch tests: OK" || echo "base_patch tests: FAILED"
exit $rc
