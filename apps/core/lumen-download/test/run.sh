#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host check of the shared parallel download (ParallelDownload, Segments, ConnectionScaler, Rate) against a local
# server that behaves like GitHub releases (redirect to a signed link that expires, Range with ETag and If-Range, a
# throttle per connection, cut connections, a server that ignores Range). Loopback only, writes only a temp dir:
#   sh apps/core/lumen-download/test/run.sh
# The apps' own use of it is tested with them: apps/core/lumen-updater/test/download.sh (PackageDownload) and
# apps/LumenInstaller/test/run.sh (the image and app update downloads of Engine).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_download.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
# no Android at all: the module must compile with the JDK alone
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" "$HERE"/src/org/lumen/download/*.java \
  "$HERE/test/org/lumen/download/DownloadTest.java"
if grep -n '^import android\.\|org\.lumen\.updater\|org\.lumen\.installer' "$HERE"/src/org/lumen/download/*.java; then
  echo "FAIL the shared download names an app or Android"; exit 1
fi
# its files (about 60 MB) stay in the temp dir above
"$JAVA_HOME/bin/java" -Djava.io.tmpdir="$OUT" -cp "$OUT/c" org.lumen.download.DownloadTest
