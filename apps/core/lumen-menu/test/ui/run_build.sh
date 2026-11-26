#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The menu of EVERY model profile built and opened on an EMULATOR (never a user's projector): for each devices/<c>
# with a menu.json the test APK of its real projector app (test/ui/build.sh, WALK=MenuBuildActivity: the model's
# merged profile, strings and modules) is built, installed and started. It fails when building the MenuController
# throws (module rows of every section registered first, as the screensaver rows of General are on the Z9X and the
# HB), when a module added after the build throws, or when the card, a tile's popup or a section's show throws.
#   sh apps/core/lumen-menu/test/ui/run_build.sh SERIAL [OUT [CODENAME ...]]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
ADB=${ADB:-adb}
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
mkdir -p "$OUT"
python3 -B - "$G" "$@" > "$OUT/models.tsv" <<'EOP'
import glob, os, sys
g, only = sys.argv[1], set(sys.argv[2:])
sys.path.insert(0, os.path.join(g, "tools", "devices"))
import devices
known = devices.load_all(g)

def includes(conf, seen):
    """The device.conf of a model, then every conf it includes (its families), in order."""
    if conf in seen or not os.path.isfile(conf):
        return
    seen.append(conf)
    for line in open(conf, encoding="utf-8"):
        if line.startswith("include="):
            includes(os.path.join(g, line.split("=", 1)[1].strip()), seen)

def projector_app(c):
    apps = (known.get(c) or {}).get("apps") or ""
    apps = apps.split() if isinstance(apps, str) else list(apps)
    for a in apps:
        if a.endswith("Projector"):
            return a
    chain = []
    includes(os.path.join(g, "devices", c, "device.conf"), chain)
    for conf in chain:
        for layers in sorted(glob.glob(os.path.join(os.path.dirname(conf), "app", "*Projector.layers"))):
            return os.path.basename(layers)[:-len(".layers")]
    return None

for c in sorted(os.listdir(os.path.join(g, "devices"))):
    if c.startswith("_") or not os.path.isfile(os.path.join(g, "devices", c, "menu.json")):
        continue
    if only and c not in only:
        continue
    print("%s\t%s" % (c, projector_app(c) or "-"))
EOP
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
# the test APKs in parallel, then one model after the other on the emulator
awk -F'	' '$2 != "-" {print $1, $2}' "$OUT/models.tsv" | \
  xargs -P "${JOBS:-6}" -n 2 sh -c 'WALK=MenuBuildActivity bash "$0/build.sh" "$1/$2" "$2" "$3" > "$1/$2.build.log" 2>&1 || echo "BUILD_FAIL $2" > "$1/$2.buildfail"' "$HERE" "$OUT"
bad=0
: > "$OUT/SUMMARY.txt"
while read -r c a <&3; do
  if [ "$a" = "-" ]; then echo "FAIL $c: no projector app found for its menu profile" >> "$OUT/SUMMARY.txt"; bad=1; continue; fi
  if [ -f "$OUT/$c.buildfail" ] || [ ! -f "$OUT/$c/walk.apk" ]; then
    echo "FAIL $c ($a): test APK not built, $OUT/$c.build.log" >> "$OUT/SUMMARY.txt"; bad=1; continue
  fi
  pkg=$(cat "$OUT/$c/package")
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$c/walk.apk" >/dev/null
  "$ADB" -s "$SERIAL" logcat -c
  "$ADB" -s "$SERIAL" shell am start -S -W -n "$pkg/org.lumen.menu.walk.MenuBuildActivity" >/dev/null
  res=""
  for i in $(seq 1 40); do
    sleep 1
    "$ADB" -s "$SERIAL" shell dumpsys activity "$pkg/org.lumen.menu.walk.MenuBuildActivity" > "$OUT/$c.dump" 2>&1 || true
    grep -q 'build.finished true' "$OUT/$c.dump" && { res=$(grep 'build.result' "$OUT/$c.dump" | head -1); break; }
  done
  "$ADB" -s "$SERIAL" logcat -d > "$OUT/$c.logcat" 2>&1 || true
  if grep -q "FATAL EXCEPTION" "$OUT/$c.logcat"; then res="build.result FAIL crash: $(grep -A2 'FATAL EXCEPTION' "$OUT/$c.logcat" | tail -1)"; fi
  case "$res" in
    "build.result OK"*) echo "OK   $c ($a) ${res#build.result OK }" >> "$OUT/SUMMARY.txt" ;;
    *) echo "FAIL $c ($a) ${res:-no result in 40 s}" >> "$OUT/SUMMARY.txt"
       grep 'build.FAIL' "$OUT/$c.dump" | head -5 | sed 's/^/       /' >> "$OUT/SUMMARY.txt"; bad=1 ;;
  esac
  "$ADB" -s "$SERIAL" shell am force-stop "$pkg"
done 3< "$OUT/models.tsv"
cat "$OUT/SUMMARY.txt"
echo "$(grep -c '^OK' "$OUT/SUMMARY.txt") of $(wc -l < "$OUT/models.tsv" | tr -d ' ') model menus built and opened"
exit $bad
