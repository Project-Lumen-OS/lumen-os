#!/bin/bash
# Builds the TEST APKs of the casting page key walk (test/ui/cast_walk.py, never shipped): Lumen AirPlay (Z9xAirPlay,
# assembled from apps/LumenAirPlay/app.layers, one APK for every model) and the Miracast sink of this tree, both
# signed with the public test platform key of apps/sdk, so the sink's CONTROL permission holds between them. An
# emulator cannot carry them as system apps, so the test copy of the casting page takes a sink that is not one: the
# FLAG_SYSTEM condition of CastActivity.query is the one line changed (the build fails when it is gone). No native
# AirPlay libraries: the AirPlay card shows its error state, the rows work. Needs the Android SDK and a JDK 17+.
#   bash apps/LumenAirPlay/test/ui/build.sh OUT        -> OUT/airplay.apk OUT/miracast.apk
set -eu
OUT=$1
G=${TREE:-$(cd "$(dirname "$0")/../../../.." && pwd)}   # TREE: another tree (the before screenshots of the base)
rm -rf "$OUT/tree" "$OUT/b"; mkdir -p "$OUT/b"
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device horse_popular --app Z9xAirPlay --out "$OUT/tree/airplay" --quiet
python3 - "$OUT/tree/airplay/src/org/lumen/airplay/CastActivity.java" <<'EOP'
import sys
p = sys.argv[1]
t = open(p, encoding="utf-8").read()
old = "\n                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;"
assert t.count(old) == 1, "the sink check of CastActivity.query changed: update the walk build"
open(p, "w", encoding="utf-8").write(t.replace(old, ";"))
EOP
cp -R "$G/apps/LumenMiracast" "$OUT/tree/miracast"
for a in airplay:LumenAirPlay miracast:LumenMiracast; do
  n=${a%%:*}; d=${a#*:}; VC=; VN=
  [ -f "$G/apps/$d/BUILD_VERSION" ] && read -r VC VN < "$G/apps/$d/BUILD_VERSION"
  VERSION_CODE=$VC VERSION_NAME=$VN KEY_PK8="$G/apps/sdk/platform.pk8" KEY_PEM="$G/apps/sdk/platform.x509.pem" \
    BUILD_DIR="$OUT/b/$n" sh "$G/apps/build_apk.sh" "$OUT/tree/$n" "$OUT/$n.apk" > "$OUT/b/$n.log" 2>&1 \
    || { tail -30 "$OUT/b/$n.log"; exit 1; }
done
echo "built $OUT/airplay.apk $OUT/miracast.apk"
