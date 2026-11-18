#!/bin/sh
# Host tests of the shared firmware copy (apps/core/lumen-fwcopy): sh apps/core/lumen-fwcopy/test/run.sh
#   1. FwCopyTests: the per-model sets, the plan, every branch of a copy on fake partitions (power cuts included);
#   2. FwListsTest: every list of the XGIMI set in the tree (the images' ro.product.ab_ota_partitions, ota.conf, the
#      image checks) is the module's set of that model, and the updater and the installer use the module.
# Host only. Writes only a temp dir. Exit 0 only when all pass.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(cd "$HERE/.." && pwd)
GSI=$(cd "$MOD/../../.." && pwd)
JH=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)}
W=$(mktemp -d "${TMPDIR:-/tmp}/fwcopy.XXXXXX")
trap 'rm -rf "$W"' EXIT
"$JH/bin/javac" -encoding UTF-8 -Xlint:all,-serial -Werror -d "$W" "$MOD"/src/org/lumen/fwcopy/*.java "$HERE"/*.java
RC=0
"$JH/bin/java" -cp "$W" org.lumen.fwcopy.FwCopyTests || RC=1
"$JH/bin/java" -Dgsi="$GSI" -cp "$W" org.lumen.fwcopy.FwListsTest || RC=1
[ $RC = 0 ] && echo "ALL LUMEN-FWCOPY TESTS PASSED" || echo "LUMEN-FWCOPY TESTS FAILED"
exit $RC
