#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The report screen key walk on an EMULATOR (never a user's projector): a throwaway certificate for 10.0.2.2, the fake
# report server (fake_server.py) on the host, then per model the test APK of its real assembled app (build.sh) is
# installed and walked (report_walk.py) in Russian and English, light and dark; screenshots of every state into OUT.
#   sh apps/core/lumen-report/test/ui/run_walk.sh SERIAL OUT [DEVICE:APP ...]   (default horse_popular:Z9xProjector)
# PORT (default 18443) must be free on the host; the emulator reaches the host's 127.0.0.1 as 10.0.2.2.
set -eu
SERIAL=${1:?serial}; OUT=${2:?out}; shift 2
ADB=${ADB:-adb}; export ADB
PORT=${PORT:-18443}
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
[ $# -gt 0 ] || set -- horse_popular:Z9xProjector
mkdir -p "$OUT/tls"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 -subj /CN=10.0.2.2 \
  -addext "subjectAltName=IP:10.0.2.2" -keyout "$OUT/tls/key.pem" -out "$OUT/tls/ca.pem" >/dev/null 2>&1
echo ok > "$OUT/mode"
python3 "$HERE/fake_server.py" "$PORT" "$OUT/tls/ca.pem" "$OUT/tls/key.pem" "$OUT/mode" 8 > "$OUT/server.log" 2>&1 &
SRV=$!
trap 'kill $SRV 2>/dev/null || true' EXIT
GOOD="https://10.0.2.2:$PORT/lumen/v1/report"
DOWN="https://10.0.2.2:$((PORT + 1))/lumen/v1/report"
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" "$OUT/tls/ca.pem" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/report_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" "$OUT/png/$d" "$OUT/mode" "$GOOD" "$DOWN" \
    ${COMBOS:-} || bad=1
  "$ADB" -s "$SERIAL" uninstall "$(cat "$OUT/$d/package")" >/dev/null 2>&1 || true
done
exit $bad
