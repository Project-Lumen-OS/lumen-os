#!/bin/sh
# Records the remote pairing traces of the OLD implementations, read from git at BASE (default: the integ commit
# remote-core started from), into test/parity/golden_<subject>.txt: the parity pin taken BEFORE the move.
#   sh apps/core/lumen-remote/test/parity/record_old.sh [BASE]
# Subjects: z9xold = apps/common/projector RemoteAutoPair (Z9X, HB, Play 6), x50old = apps/X50Projector
# org.lumen.x50.remote (X50 Ultra, Ultra Max, RS30). Needs a JDK 17+ (JAVA_HOME or javac on PATH). Host only.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(cd "$HERE/.." && pwd)
G=$(cd "$T/../../../.." && pwd)
BASE=${1:-389dc716}
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/base" "$OUT/src/z9x" "$OUT/src/x50" "$OUT/z9xold" "$OUT/x50old"
find "$T/stubs" "$T/harness" -name '*.java' > "$OUT/base.list"
"$JAVAC" -nowarn -d "$OUT/base" @"$OUT/base.list"
show() { git -C "$G" show "$BASE:$1" > "$2"; }
show apps/common/projector/src/org/lumen/projector/remote/RemoteAutoPair.java "$OUT/src/z9x/RemoteAutoPair.java"
find "$T/legacy" "$T/shim" -name '*.java' > "$OUT/z9x.list"
echo "$OUT/src/z9x/RemoteAutoPair.java" >> "$OUT/z9x.list"
echo "$T/subjects/z9xold/lumen/subject/Impl.java" >> "$OUT/z9x.list"
"$JAVAC" -nowarn -cp "$OUT/base" -d "$OUT/z9xold" @"$OUT/z9x.list"
for f in Match MatchVerdict Policy PolicyMode LinkSpeed RemoteBridge RemotePair; do
  show "apps/X50Projector/src/org/lumen/x50/remote/$f.java" "$OUT/src/x50/$f.java"
done
show apps/core/lumen-hal/src/org/lumen/hal/GmpfModel.java "$OUT/src/x50/GmpfModel.java"
show apps/core/lumen-hal/src/org/lumen/hal/GmpfHash.java "$OUT/src/x50/GmpfHash.java"
show apps/core/lumen-hal/src/org/lumen/hal/HashRule.java "$OUT/src/x50/HashRule.java"
"$JAVAC" -nowarn -cp "$OUT/base" -d "$OUT/x50old" "$OUT"/src/x50/*.java "$T/subjects/x50old/lumen/subject/Impl.java"
for s in z9xold x50old; do
  "$JAVA" -cp "$OUT/base" lumen.fake.Scenarios "$OUT/$s" > "$HERE/golden_$s.txt"
  echo "recorded $HERE/golden_$s.txt ($(grep -c '^==' "$HERE/golden_$s.txt") scenarios, base $BASE)"
done
