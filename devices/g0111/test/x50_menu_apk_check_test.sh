#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# x50_menu_apk_check_test.sh: the second HAL exception of the X50 image (t11): MENU_APP of
# devices/g0111/image/verify_tree_x50.py, the projector menu apps/X50Projector.
#   sh devices/g0111/test/x50_menu_apk_check_test.sh [X50Projector.apk] [X50EyeGuard.apk]
#   sources   MENU_APP holds to its sources: calls = research/allowed_x50projector.tsv, ifaces = the Iface list of
#             apps/X50Projector GmpfIface.java, cert = devices/g0111/certs/platform.x509.pem, path = /system/priv-app/
#             X50Projector, an app of the registry, installed by devices/g0111/image/steps.tsv, checked by verify_tree_x50.py
#   real      the built APK passes x50_apk_check against MENU_APP (250 calls; t17: 70 - power-on AF 4 / 5 - MD 5 = 67;
#             t18: + MD 9 getAutoReverse for the projection Auto radio; t20: + 21 with their argument ranges = 89; core-menu-x50game: + 12 game menu calls = 101; rs30-power: + 5 light calls = 106; rs30-menu: + the RS30 rows 12 = 118; x50-iris: + 199 / 200 = 120; quick-3d-x50: + the nine 3D calls = 129; rs30-parity-picture: + 18 picture rows less 78 of quick-3d-x50 = 146; rs30-parity-system: + 6 sound = 152; rs30-parity-system2: + TemperatureManager 6 = 153; cec-core: + GmTvManager 52 / 53 = 155; rs30-parity-projection: + rotation 2, lens memory 6 = 163; x50-parity-gaps: + aspect ratio 35 / 36 = 165; rs30-parity-picture2: + 25 colour and screen calls = 190; x50-feedback1: + the shift position 98 = 191; x50-picture-stock: + the Boost switch 151, its IMAX read 127, the tuning 192 / 193 = 195; ai-mode-stock: + GmTvManager 1 = 196; x50-parity-core: + GmTvManager 106 = 197; x50-live-1124: + DisplayManager 37 for the About FPGA row = 245 as built; x50-game-1128: + GmTvManager 191 setHGiGMode = 246)
#   crossed   the menu APK against HAL_APP and the eye guard APK against MENU_APP: both refused
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
CHK=$G/devices/g0111/image/x50_apk_check.py
VER=$G/devices/g0111/image/verify_tree_x50.py
APK=${1:-$G/build/g0111/stage/X50Projector.apk}
EYE=${2:-$G/build/g0111/stage/X50EyeGuard.apk}
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_menu_check.XXXXXX") || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; [ -f "$W/out" ] && sed 's/^/     | /' "$W/out" | tail -6; }
run() { python3 -B "$CHK" "$@" > "$W/out" 2>&1; RC=$?; }
expect() {
  rc=$RC
  if [ "$rc" = "$2" ] && { [ -z "${3:-}" ] || grep -qF -- "$3" "$W/out"; }; then ok "$1"; else bad "$1 (exit $rc, want $2${3:+, '$3'})"; fi
}

python3 -B - "$G" "$VER" > "$W/out" 2>&1 <<'PY'
import hashlib, os, re, subprocess, sys
sys.dont_write_bytecode = True
G, ver = sys.argv[1:3]
sys.path.insert(0, os.path.join(G, "devices", "g0111", "image"))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import x50_apk_check as x, devices as d
h = x.expect_from(ver, "MENU_APP")
e = x.expect_from(ver)
bad = []
rows = []
for line in open(os.path.join(G, "devices/g0111/research/allowed_x50projector.tsv"), encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    f = line.rstrip("\n").split("\t")
    s, c, m = f[:3]
    rows.append((s, int(c, 0), m, f[4][len("args:"):] if len(f) >= 6 and f[4].startswith("args:") else None))
# t20: a 4th field = the row's args column (the Call's argument range)
if sorted(((r + [None])[:4] for r in map(list, h["calls"])), key=str) != sorted(map(list, rows), key=str):
    bad.append("MENU_APP calls %s != allowed_x50projector.tsv %s" % (h["calls"], rows))
src = "".join(open(os.path.join(G, "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector", f), encoding="utf-8").read()
              for f in ("Gmpf.java", "GmpfIface.java") if os.path.isfile(os.path.join(G, "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector", f)))
ifs = re.findall(r'^\s+[A-Z_]+\("(xgimi\.hardware\.gmpf\.I\w+)"\)', src, re.M)
if sorted(ifs) != sorted(h["ifaces"]):
    bad.append("MENU_APP ifaces %s != GmpfIface %s" % (h["ifaces"], ifs))
# ai-mode-stock: the vendor callback interfaces the app implements (AidlTransport.registerCallback descriptors in Gmpf)
cbs = re.findall(r'^\s+static final String \w+ = "(xgimi\.hardware\.gmpf\.I\w+Callback)";', src, re.M)
if sorted(cbs) != sorted(h.get("callbacks", [])):
    bad.append("MENU_APP callbacks %s != the callback descriptors of Gmpf %s" % (h.get("callbacks"), cbs))
der = subprocess.run(["openssl", "x509", "-in", os.path.join(G, "devices/g0111/certs/platform.x509.pem"), "-outform", "DER"],
                     capture_output=True, check=True).stdout
if h["cert"] != hashlib.sha256(der).hexdigest() or h["cert"] != e["cert"]:
    bad.append("MENU_APP cert is not devices/g0111/certs/platform.x509.pem")
reg = d.get(G, "g0111")
if h["path"] != "system/priv-app/X50Projector/X50Projector.apk" or "X50Projector" not in reg.get("apps", []):
    bad.append("MENU_APP path %s / registry apps %s" % (h["path"], reg.get("apps")))
if h["staged"] != "stage/X50Projector.apk":
    bad.append("MENU_APP staged %s" % h["staged"])
comp = open(os.path.join(G, "devices/g0111/image/steps.tsv"), encoding="utf-8").read()
if "\tplace\t%s\t{sys}/priv-app/X50Projector/X50Projector.apk\t" % h["staged"] not in comp:
    bad.append("steps.tsv does not install %s at %s" % (h["staged"], h["path"]))
if "X50Remote.apk" in comp:
    bad.append("steps.tsv still installs the separate X50Remote app (merged into X50Projector)")
vsrc = open(ver, encoding="utf-8").read()
if "x50_apk_check.problems(rep, MENU_APP)" not in vsrc or 'the menu HAL app %s is not in the image' not in vsrc:
    bad.append("verify_tree_x50.py does not check the menu app with x50_apk_check")
# t12: 61 / 126 are the stock eye-protection user rows of the menu (newsettings MoreRepository); 101, the vendor cover
# 66 with false, the heartbeat and the ambient light switch / getter stay forbidden, and the eye guard (HAL_APP) may still not set
# any of them. t20: the maintainer's three unbans (11, 88, 3) exist for the menu only, each with exactly the args of
# unbanned_x50projector.tsv; the eye guard has none of them.
forbidden = {("DisplayManager", 101), ("DisplayManager", 59), ("DisplayManager", 60),
             ("DisplayManager", 99), ("DisplayManager", 100)}
# rs30-power: 66 only as the stock power key's relight (services.jar kPPowerPress), with true only: never the cover
for r in h["calls"]:
    if (r[0], r[1]) == ("DisplayManager", 66) and (len(r) < 4 or r[3] != "1"):
        bad.append("MENU_APP sends 66 with more than true: %r" % (r,))
eye_set = {("DisplayManager", 61), ("DisplayManager", 101), ("DisplayManager", 126), ("DisplayManager", 66)}
mine = {(r[0], r[1]) for r in h["calls"]}
theirs = {(r[0], r[1]) for r in e["calls"]}
if eye_set & theirs:
    bad.append("HAL_APP (eye guard) sets the user's eye protection: %s" % sorted(eye_set & theirs))
if forbidden & mine:
    bad.append("MENU_APP has a forbidden call: %s" % sorted(forbidden & mine))
unb = {}; unm = {}
for line in open(os.path.join(G, "devices/g0111/research/unbanned_x50projector.tsv"), encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    f = line.rstrip("\n").split("\t")
    if f[0] != "X50Projector":
        bad.append("unbanned_x50projector.tsv names another app: %s" % f[0])
    unb[(f[1], int(f[2]))] = f[4][len("args:"):]
    unm[(f[1], int(f[2]))] = f[3]
if sorted(unb) != [("DisplayManager", 11), ("PowerManager", 6), ("PowerManager", 12), ("ProjectorFocusManager", 3), ("ProjectorFocusManager", 88),
                   ("ProjectorFocusManager", 107), ("ProjectorFocusManager", 108), ("ProjectorFocusManager", 109),
                   ("ProjectorFocusManager", 110), ("SystemManager", 122)]:
    bad.append("unbanned rows %s, not exactly 11 / power 6, 12 / 88 / 3 / 107-110 / 122" % sorted(unb))
for r in h["calls"]:
    # a code another firmware table gives another method (107 getUitiltShiftSwithStatus of v1.2.5) is its own row
    if (r[0], r[1]) in unb and r[2] == unm[(r[0], r[1])] and (len(r) < 4 or r[3] != unb[(r[0], r[1])]):
        bad.append("MENU_APP %s %d has args %r, the unban list %r" % (r[0], r[1], r[3] if len(r) > 3 else None, unb[(r[0], r[1])]))
if set(unb) & theirs:
    bad.append("HAL_APP (eye guard) has an unbanned call: %s" % sorted(set(unb) & theirs))
print("\n".join(bad) or "sources: OK")
sys.exit(1 if bad else 0)
PY
RC=$?; expect sources 0 "sources: OK"

if [ -f "$APK" ]; then
  run "$APK" --expect "$VER" --name MENU_APP; expect real_menu_apk 0 "OK: 254 calls"
  run "$APK" --expect "$VER"; expect menu_apk_is_not_the_eye_guard 1 "FAILED"
else
  bad "no menu APK $APK (build it: sh tools/devices/build_apps.sh g0111 build/g0111/stage)"
fi
if [ -f "$EYE" ]; then
  run "$EYE" --expect "$VER" --name MENU_APP; expect eye_guard_apk_is_not_the_menu 1 "FAILED"
fi
echo "x50_menu_apk_check_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
