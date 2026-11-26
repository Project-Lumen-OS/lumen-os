#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Live check of the real upload path (ReportUploader.uploadAny) from this host: one test zip of KB kilobytes to the
# given addresses in turn, as the projector sends it. Not part of run.sh. Run it on a host in Russia to see what
# Russian networks let through:
#     sh apps/core/lumen-report/test/live_upload.sh 100 https://stats.lumen-os.net/lumen/v1/report https://prompt.az/lumen/v1/report
# The server stores what arrives (kind manual): delete the printed code (tools/stats/server/deploy/reports.sh rm CODE).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_live.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/report
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S/ReportUploader.java" "$S/ReportFailure.java" \
  "$S/ReportKind.java" "$S/CanceledException.java" "$HERE/test/org/lumen/report/LiveUpload.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.report.LiveUpload "$@"
