#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Files (apps/LumenFiles) host checks, no device and no emulator: sh apps/LumenFiles/test/run.sh
#   1. test/model: the pure rules (org.lumen.files.model, plain JDK): kinds, names, order, search, walks, picking,
#      copy / move / delete with free space, cancel and clashes, zip / tar / tar.gz with the escape guard, DLNA parsing
#   2. test/net: the SMB 2/3 client moved from Home
#   3. ApkFilesTest: which file names are APKs (android.jar only to link)
#   4. test/wiring.py: manifest, installer hand-off, Lumen Play first, Home's link and hand-off, strings, icons, recipes
# Mutants: python3 apps/LumenFiles/test/mutate.py
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/files-test.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/m" "$OUT/a" "$OUT/tmp"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/m" "$HERE"/src/org/lumen/files/model/*.java "$HERE/test/model/ModelTest.java"
"$JAVA_HOME/bin/java" -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/m" org.lumen.files.model.ModelTest
sh "$HERE/test/net/run.sh"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/a" -cp "$AJ" "$HERE/src/org/lumen/files/open/ApkFiles.java" \
  "$HERE/test/model/ApkFilesTest.java"
"$JAVA_HOME/bin/java" -Djava.io.tmpdir="$OUT/tmp" -cp "$OUT/a:$AJ" org.lumen.files.open.ApkFilesTest
python3 -B "$HERE/test/wiring.py" "$ROOT"
echo "files tests: ok"
