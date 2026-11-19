#!/bin/sh
# Real GitHub through a per-connection throttle (throttle_proxy.py, RATE bytes/s per connection, default 1.1 MB/s as
# measured on a projector in Russia): one connection for ONE_S seconds, then the shared parallel download of the whole
# asset with a spoiled CDN link after 5 s (+403), then its sha256 against the manifest. Downloads the asset about
# once, writes only a temp dir. Run on a builder, never on the Mac:
#   [ONE_S=0] [RATE=bytes/s] sh apps/core/lumen-download/test/bench/run.sh URL SIZE SHA256
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
URL=$1 SIZE=$2 SHA=$3
RATE=${RATE:-1153434} ONE_S=${ONE_S:-90} PORT=${PORT:-18899}
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_dl_bench.XXXXXX")
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" "$HERE"/src/org/lumen/download/*.java \
  "$HERE/test/bench/Bench.java"
python3 -B "$HERE/test/bench/throttle_proxy.py" "$PORT" "$RATE" > "$OUT/proxy.log" 2>&1 &
PROXY=$!
trap 'kill $PROXY 2>/dev/null || true
rm -rf "$OUT"' EXIT
sleep 1
J="$JAVA_HOME/bin/java -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=$PORT -cp $OUT/c org.lumen.download.Bench"
echo "throttle: $RATE bytes/s per connection"
open_max() { tail -n +"$1" "$OUT/proxy.log" | grep -o 'open=[0-9]*' | cut -d= -f2 | sort -n | tail -1; }
if [ "$ONE_S" -gt 0 ]; then
  $J one "$URL" "$SIZE" "$SHA" "$OUT/one.zip" "$ONE_S"
  echo "tunnels open at once, the redirect included (one): $(open_max 1)"
fi
L=$(($(wc -l < "$OUT/proxy.log") + 1))
rm -f "$OUT/one.zip" "$OUT/one.zip.part" "$OUT/one.zip.part.state"
$J parallel+403 "$URL" "$SIZE" "$SHA" "$OUT/par.zip"
echo "tunnels open at once, the redirect included (parallel): $(open_max "$L")"
