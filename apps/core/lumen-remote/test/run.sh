#!/bin/sh
# lumen-remote host tests (no device): the remote table, RemoteMatch, and the parity of the shared engine with the two
# implementations it replaced (traces recorded by parity/record_old.sh from the old code, before the move).
#   sh apps/core/lumen-remote/test/run.sh        (JDK 17+: JAVA_HOME or javac on PATH; python3)
set -eu
T=$(cd "$(dirname "$0")" && pwd)
M=$(cd "$T/.." && pwd)
G=$(cd "$M/../../.." && pwd)
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 "$G/tools/remotes/gen_catalog.py" --check
# 1. the table rules
mkdir -p "$OUT/match"
"$JAVAC" -encoding UTF-8 -nowarn -d "$OUT/match" "$M/src/org/lumen/remote/RemoteCatalog.java" \
  "$M/src/org/lumen/remote/RemoteKind.java" "$M/src/org/lumen/remote/RemoteMatch.java" "$M/src/org/lumen/remote/RemoteAdvert.java" \
  "$T/RemoteMatchTest.java"
"$JAVA" -cp "$OUT/match" RemoteMatchTest
# 2. the shared engine on the fake Bluetooth world, compared with the goldens of the old code
mkdir -p "$OUT/base" "$OUT/shared"
find "$T/stubs" "$T/harness" -name '*.java' > "$OUT/base.list"
"$JAVAC" -encoding UTF-8 -nowarn -d "$OUT/base" @"$OUT/base.list"
find "$M/src" -name '*.java' ! -name RemotePrompt.java ! -name RemoteArt.java ! -path '*/keys/*' > "$OUT/shared.list"
find "$T/shared" "$T/shim" "$T/subjects/shared" -name '*.java' >> "$OUT/shared.list"
"$JAVAC" -encoding UTF-8 -nowarn -cp "$OUT/base" -d "$OUT/shared" @"$OUT/shared.list"
"$JAVA" -cp "$OUT/base" lumen.fake.Scenarios "$OUT/shared" > "$OUT/trace_shared.txt"
[ -n "${KEEP_TRACE:-}" ] && cp "$OUT/trace_shared.txt" "$KEEP_TRACE"
python3 "$T/parity/parity.py" "$T/parity/golden_z9xold.txt" "$T/parity/golden_x50old.txt" "$OUT/trace_shared.txt"
# 3. resources: every text the module code names exists in every locale folder of the module
python3 - "$M" <<'EOF'
import os, re, sys
m = sys.argv[1]
used = set()
for root, _, files in os.walk(os.path.join(m, "src")):
    for f in files:
        used |= set(re.findall(r"R\.string\.(lumen_remote_[a-z0-9_]+)", open(os.path.join(root, f), encoding="utf-8").read()))
bad = 0
res = os.path.join(m, "res")
for d in sorted(os.listdir(res)):
    if not d.startswith("values"):
        continue
    have = set()
    for f in os.listdir(os.path.join(res, d)):
        have |= set(re.findall(r'name="(lumen_[a-z0-9_]+)"', open(os.path.join(res, d, f), encoding="utf-8").read()))
    missing = sorted(used - have) if d == "values" else []
    stray = sorted(n for n in have if not n.startswith("lumen_"))
    if missing or stray:
        bad += 1
        print("res %s: missing %s stray %s" % (d, missing, stray))
print("resources: %d texts used, %d folders checked, %d problems" % (len(used), len([d for d in os.listdir(res) if d.startswith("values")]), bad))
sys.exit(1 if bad else 0)
EOF
# 4. the timeouts of the Z9X reference (Lumen OS 1.0 RemoteAutoPair), pinned value for value
python3 - "$M/src/org/lumen/remote/RemotePairing.java" <<'EOF'
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
pin = {"FIRST_CHECK_MS": "3_000", "TICK_MS": "30_000", "MANUAL_MS": "120_000", "BOOT_GRACE_MS": "10_000",
       "BOOT_WINDOW_MS": "120_000", "BOOT_MAX_UPTIME_MS": "10 * 60_000", "NEW_FAST_MS": "2 * 60_000",
       "NEW_MAX_MS": "60 * 60_000", "REPAIR_CONFIRM_MS": "3_000", "REPAIR_FRESH_MS": "1_500",
       "REPAIR_CONFIRM_SLOW_MS": "11_000", "REPAIR_FRESH_SLOW_MS": "5_500", "SCAN_REFRESH_MS": "25 * 60_000",
       "SCAN_RETRY_MS": "30_000", "BOND_TIMEOUT_MS": "45_000", "MIN_START_GAP_MS": "7_000",
       "FAIL_COOLDOWN_MS": "10 * 60_000", "PERM_RETRY_MS": "5 * 60_000", "JUST_BONDED_MS": "60_000",
       "LOST_AFTER_MS": "60_000", "UNBOND_GRACE_MS": "5_000", "LOST_RETRY_MS": "30_000", "CONNECT_RETRY_MS": "10_000"}
bad = 0
for k, v in pin.items():
    m = re.search(r"\b%s = ([^,;]+)[,;]" % k, src)
    if not m or m.group(1).strip() != v:
        bad += 1
        print("timing %s: %s, pinned %s" % (k, m.group(1).strip() if m else "missing", v))
print("timings: %d pinned, %d differ" % (len(pin), bad))
sys.exit(1 if bad else 0)
EOF
echo "lumen-remote: ALL PASSED"
