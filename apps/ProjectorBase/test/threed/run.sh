#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Quick 3D host checks: the stock call of each mode (display/ThreeDCall), the read-back (lumen-menu ThreeDMode), the
# remote's 3D request (MenuRequest), the profiles, labels and the receiver: sh test/threed/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
M="$G/apps/core/lumen-menu/src/org/lumen/menu"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$M"/*.java "$M"/utils/*.java \
  "$HERE/src/org/lumen/projector/hal/Mode3D.java" "$HERE/src/org/lumen/projector/display/ThreeDCall.java" \
  "$HERE/test/threed/ThreeDTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.display.ThreeDTest "$G"
