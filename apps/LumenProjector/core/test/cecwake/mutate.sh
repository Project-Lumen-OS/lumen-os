#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Mutants of cec/WakeRules against the wake replay: every one must make test/cecwake/run.sh fail.
# sh test/cecwake/mutate.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
W=$HERE/src/org/lumen/projector/cec/WakeRules.java
T=$(mktemp -d "${TMPDIR:-/tmp}/cecwakemut.XXXXXX")
: "${T:?}"
cp "$W" "$T/WakeRules.java.orig"
trap 'cp "$T/WakeRules.java.orig" "$W"; rm -rf "$T"' EXIT
killed=0; alive=0
mutant() {
    cp "$T/WakeRules.java.orig" "$W"
    python3 - "$W" "$1" "$2" <<'EOF'
import sys
p, old, new = sys.argv[1:]
t = open(p).read()
assert t.count(old) == 1, "mutant anchor not unique: " + old
open(p, "w").write(t.replace(old, new))
EOF
    if sh "$HERE/test/cecwake/run.sh" >/dev/null 2>&1; then alive=$((alive + 1)); echo "ALIVE $3"; else killed=$((killed + 1)); fi
}
mutant 'return !androidWoke;' 'return true;' "app request after every wake"
mutant 'return !androidWoke;' 'return false;' "no app request after the standby exit"
mutant 'if (cecWakeAt < 0 || byHdmiDevice) return null;' 'if (cecWakeAt < 0) return null;' "HDMI device sleeps count"
mutant 'if (since < 0 || since >= QUICK_SLEEP_MS) return null;' 'if (since < 0 || since > QUICK_SLEEP_MS) return null;' "quick sleep bound"
mutant 'if (bad.size() > STORM_MAX) paused = true;' 'if (bad.size() >= STORM_MAX) paused = true;' "storm limit off by one"
mutant 'now - bad.peekFirst() > STORM_WINDOW_MS' 'now - bad.peekFirst() > STORM_WINDOW_MS * 4' "storm window"
mutant 'paused = false;' '' "a remote key does not end the pause"
echo "cec wake mutants: $killed killed, $alive alive"
[ "$alive" -eq 0 ]
