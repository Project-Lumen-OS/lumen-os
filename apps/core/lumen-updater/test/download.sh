#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host check of the updater's PackageDownload (the network wait, retries, the whole-file sha256 with one more pass,
# 404 ends it) over the shared download of apps/core/lumen-download, against that module's GitHub-like test server.
# Loopback only, writes only a temp dir: sh apps/core/lumen-updater/test/download.sh
# The download itself (ranges, resume, the CDN link, If-Range) is tested in the module: apps/core/lumen-download/test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
D=$G/apps/core/lumen-download
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_updater_dl.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device horse_popular --app Z9xUpdater --out "$OUT/app" --quiet
# the assembled updater holds the module's copy: the test server comes from the module's own test folder
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" -cp "$HJ:$AJ" -sourcepath "$OUT/app/src" \
  "$OUT/app/src/org/lumen/updater/PackageDownload.java" "$D/test/org/lumen/download/DownloadTest.java" \
  "$HERE/test/org/lumen/updater/PackageDownloadTest.java"
# no Android classes at run time: the download is plain Java. Its files stay in the temp dir above.
"$JAVA_HOME/bin/java" -Djava.io.tmpdir="$OUT" -cp "$OUT/c" org.lumen.updater.PackageDownloadTest
