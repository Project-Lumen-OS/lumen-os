#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Parity of every updater with the app it replaced (2026-11-13: one shared updater, exact parity per model).
#   sh apps/core/lumen-updater/test/parity/run.sh            compare with golden/
#   sh apps/core/lumen-updater/test/parity/run.sh --record   write golden/ (only from the trees BEFORE the move)
# For each recipe below: assemble the app (tools/devices/assemble_app.py), then
#   1. Probe.java: the behaviour transcript (channels, versions, slots, manifest parsing, applicable, blobs,
#      install checks with a fake boot gate, download folder) must equal golden/<app>.txt line for line;
#   2. static.py: manifest and non-value resources unchanged, value entries (strings) only added, never changed.
# Host only (javac, python3), no device, no network. Writes only a temp dir.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
G=${LUMEN_GSI:-$(cd "$HERE/../../../../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
RECORD=0
[ "${1:-}" = "--record" ] && RECORD=1
# no // in the path (a macOS TMPDIR ends in /): the probe prints canonical paths and masks this one
TD=${TMPDIR:-/tmp}
OUT=$(mktemp -d "${TD%/}/lumen_updater_parity.XXXXXX")
trap 'rm -rf "$OUT"' EXIT

# javac argument files, one quoted path per line: the checkout path may hold blanks ("XGIMI PLAY 6")
find "$HERE/json" -name '*.java' | sed 's/.*/"&"/' > "$OUT/json.files"
find "$HERE/stubs" -name '*.java' | sed 's/.*/"&"/' > "$OUT/stubs.files"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/json" @"$OUT/json.files"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/stubs" -cp "$HJ:$AJ" @"$OUT/stubs.files"

fail=0
# RECIPES: "<codename> <app> <golden name>"; a model added later gets its own golden from its first run (--record)
RECIPES=${PARITY_RECIPES:-"horse_popular Z9xUpdater z9x
goat_elegant LumenUpdater hb
dragon_healthy Play6Updater play6"}
echo "$RECIPES" | while read -r dev app name; do
  [ -n "$dev" ] || continue
  T=$OUT/$name
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device "$dev" --app "$app" --out "$T/tree" --quiet
  mkdir -p "$T/c"
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -implicit:class -d "$T/c" \
    -cp "$OUT/stubs:$OUT/json:$HJ:$AJ" -sourcepath "$T/tree/src" "$HERE/Probe.java"
  "$JAVA_HOME/bin/java" -cp "$T/c:$OUT/stubs:$OUT/json:$AJ:$HJ" org.lumen.updater.Probe "$T/tmp" > "$T/probe.txt"
  python3 -B "$HERE/static.py" "$T/tree" > "$T/static.txt"
  if [ $RECORD = 1 ]; then
    mkdir -p "$HERE/golden"
    cp "$T/probe.txt" "$HERE/golden/$name.txt"
    cp "$T/static.txt" "$HERE/golden/$name.static"
    echo "recorded $name: $(wc -l < "$T/probe.txt") behaviour lines, $(wc -l < "$T/static.txt") static lines"
    continue
  fi
  if ! cmp -s "$HERE/golden/$name.txt" "$T/probe.txt"; then
    echo "FAIL $name: behaviour differs from golden/$name.txt"
    diff "$HERE/golden/$name.txt" "$T/probe.txt" | head -40
    echo FAIL > "$OUT/failed"
  fi
  # static: golden lines must all be there, except value lines may be joined by new ones
  missing=$(LC_ALL=C comm -23 "$HERE/golden/$name.static" "$T/static.txt")
  # a text rewritten on purpose (rewritten.txt) may differ, but only while the key still has a text in that folder
  missing=$(printf '%s\n' "$missing" | python3 -B -c '
import re, sys
keys = {l.strip() for l in open(sys.argv[1], encoding="utf-8") if l.strip() and not l.startswith("#")}
now = {m.group(1) for m in (re.match(r"(value \S+ string \S+) = ", l) for l in open(sys.argv[2], encoding="utf-8")) if m}
for l in sys.stdin.read().split("\n"):
    m = re.match(r"(value \S+ string (\S+)) = ", l)
    if l and not (m and m.group(2) in keys and m.group(1) in now):
        print(l)
' "$HERE/rewritten.txt" "$T/static.txt")
  # the shared icon set (apps/core/lumen-icons) joins every updater: its drawables and tints are additions, any other
  # new resource is still a change of the face
  added=$(LC_ALL=C comm -13 "$HERE/golden/$name.static" "$T/static.txt" | grep -v '^value ' \
    | grep -Ev '^res (drawable/lumen_ic_[a-z0-9_]+|color/lumen_icon_tint(_on)?)\.xml ' || true)
  if [ -n "$missing" ] || [ -n "$added" ]; then
    echo "FAIL $name: static face differs (manifest, resources or a changed text)"
    printf '%s\n' "$missing" | head -20 | sed 's/^/  gone: /'
    printf '%s\n' "$added" | head -20 | sed 's/^/  new:  /'
    echo FAIL > "$OUT/failed"
  fi
  [ -f "$OUT/failed" ] || echo "parity $name: $(wc -l < "$T/probe.txt") behaviour lines equal, static face kept ($(LC_ALL=C comm -13 "$HERE/golden/$name.static" "$T/static.txt" | wc -l) value lines added)"
done
[ ! -f "$OUT/failed" ] || exit 1
[ $RECORD = 1 ] || echo "updater parity: green"
