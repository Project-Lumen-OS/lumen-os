#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# The CEC page parity pins (lumen-cec): every device app that shows the HDMI-CEC page is assembled from its recipe,
# its page code is compiled against the stubs and driven by CecParity in every scenario and locale; the transcript
# must equal golden/<codename>.txt byte for byte. The Z9X and HB goldens were pinned BEFORE the page moved into this
# module, so they kept exactly what they did; the X50 family (g0111, g0155: its driver X50Cec over a fake vendor HAL)
# was recorded when it got the page.
# Usage: run.sh [--record] [CODENAME...]   (--record writes the goldens; only for a change that is meant to change them)
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../../../.." && pwd)
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}
# codename:app:driver  (driver = the projector's CecDriver, the Entry built for it)
DEVICES="horse_popular:Z9xProjector:policy goat_elegant:HbProjector:policy g0111:X50Projector:x50 g0155:X50Projector:x50"
record=0
[ "${1:-}" = "--record" ] && { record=1; shift; }
want="$*"
T=$(mktemp -d "${TMPDIR:-/tmp}/cecparity.XXXXXX")
trap 'rm -rf "$T"' EXIT
fail=0
n=0
for d in $DEVICES; do
    c=${d%%:*}; rest=${d#*:}; app=${rest%%:*}; kind=${rest#*:}
    if [ -n "$want" ]; then case " $want " in *" $c "*) ;; *) continue ;; esac; fi
    W="$T/$c"
    mkdir -p "$W/gen" "$W/cls"
    python3 -B "$ROOT/tools/devices/assemble_app.py" "$ROOT/devices/$c/app/$app.layers" --out "$W/tree" --quiet
    python3 -B "$HERE/gen_res.py" "$W/tree" "$W/gen"
    P="$W/tree/src/org/lumen/projector/cec"
    srcs="$P/CecPage.java $P/CecSettings.java $P/CecDevices.java"
    stubs=$(find "$HERE/stubs" -name '*.java' ! -path '*/stubs/policy/*' ! -path '*/stubs/x50/*')
    case $kind in
        policy) stubs="$stubs $(find "$HERE/stubs/policy" -name '*.java')"; driver="new org.lumen.projector.cec.CecPolicyDriver()";
                dsrc="$P/CecPolicyDriver.java" ;;
        x50) X="$W/tree/src/org/lumen/x50/projector"
             stubs="$stubs $(find "$HERE/stubs/x50" -name '*.java')"
             driver="new org.lumen.x50.projector.X50Cec(r -> parity.World.post(\"x50-menu-d\", r, 0))"
             dsrc="$X/X50Cec.java $X/CecSequences.java $X/CecHal.java" ;;
        *) echo "run.sh: unknown driver $kind"; exit 2 ;;
    esac
    C="$W/tree/src/org/lumen/cec"
    if [ -f "$C/CecDriver.java" ]; then
        srcs="$srcs $C/CecDriver.java $C/CecRow.java $dsrc"
        call="CecPage.build(app, host, $driver)"
    else
        call="CecPage.build(app, host)"
    fi
    cat > "$W/gen/Entry.java" <<EOF
package parity;

import android.content.Context;
import org.lumen.projector.cec.CecPage;
import org.lumen.projector.ui.Page;

final class Entry {
    private Entry() { }
    static Page build(Context app, CecPage.Host host) { return $call; }
}
EOF
    # shellcheck disable=SC2086
    "$JAVAC" -nowarn -encoding UTF-8 -d "$W/cls" $stubs $srcs "$HERE/World.java" "$HERE/CecParity.java" "$W/gen/Entry.java" \
        "$W/gen/org/lumen/projector/R.java" > "$W/javac.log" 2>&1 || { cat "$W/javac.log"; echo "FAIL $c: compile"; fail=1; continue; }
    "$JAVA" -cp "$W/cls" parity.CecParity "$W/gen/strings" > "$W/out.txt"
    n=$((n + 1))
    if [ $record = 1 ]; then
        mkdir -p "$HERE/golden"
        cp "$W/out.txt" "$HERE/golden/$c.txt"
        echo "recorded $c ($(wc -l < "$W/out.txt" | tr -d ' ') lines)"
    elif cmp -s "$W/out.txt" "$HERE/golden/$c.txt"; then
        echo "ok $c ($(wc -l < "$W/out.txt" | tr -d ' ') lines)"
    else
        echo "FAIL $c: transcript differs from golden/$c.txt"
        diff "$HERE/golden/$c.txt" "$W/out.txt" | head -40 || true
        fail=1
    fi
done
[ $n -gt 0 ] || { echo "cec parity: no device run"; exit 2; }
[ $fail = 0 ] && echo "cec parity: all green" || echo "cec parity: RED"
exit $fail
