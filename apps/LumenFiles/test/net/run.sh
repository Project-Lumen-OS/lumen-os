#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Network folders host checks (org.lumen.files.net SMB2 client, moved from Home: plain Java, no android.jar): sh test/net/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/files-net.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
N=$HERE/src/org/lumen/files/net
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$N/Le.java" "$N/SmbCrypto.java" "$N/Ntlm.java" "$N/Spnego.java" "$N/Srvsvc.java" "$N/SmbException.java" \
  "$N/Smb2.java" "$HERE/test/net/SmbTest.java" "$HERE/test/net/SmbLoopTest.java"
"$JAVA_HOME/bin/java" -Djava.io.tmpdir="$OUT" -cp "$OUT" org.lumen.files.net.SmbTest
