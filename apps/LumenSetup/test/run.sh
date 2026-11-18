#!/bin/sh
# Lumen Setup fast host tests (no device, no emulator): the 2.0 flow (test/flow: step order, step counter, the remote
# step's 60 s not found, Wi-Fi list states and the row each state focuses, the language order, the QR grid, the kept
# wrong password, the install offer gate, icons and colours from the shared modules), every
# string key in every locale with no semicolon or long dash in the 2.0 texts, and the pointer checks (test/pointer).
#   sh apps/LumenSetup/test/run.sh        (JAVA_HOME, ANDROID_SDK; run on a builder, never on the Mac)
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
AJ=${ANDROID_SDK:-$HOME/lumen-tools/android-sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
RC=0
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" -cp "$HJ:$AJ" \
  "$HERE/src/org/lumen/setup/Progress.java" "$HERE/src/org/lumen/setup/RemoteSearch.java" \
  "$HERE/src/org/lumen/setup/net/ListState.java" "$HERE/src/org/lumen/setup/InstallOffer.java" \
  "$HERE/src/org/lumen/setup/Langs.java" "$G/apps/core/lumen-qr/src/org/lumen/qr/QrCode.java" \
  "$G/apps/core/lumen-qr/src/org/lumen/qr/QrView.java" \
  "$HERE/test/flow/FlowTest.java" 2>&1 | grep -v "^Note:" || true
"$JAVA_HOME/bin/java" -Dsetup.dir="$HERE" -cp "$OUT:$AJ:$HJ" org.lumen.setup.FlowTest || RC=1
python3 - "$HERE/res" <<'PY' || RC=1
import os, re, sys
res = sys.argv[1]
def keys(d):
    out = {}
    for f in ("strings.xml", "strings_pair.xml"):
        p = os.path.join(res, d, f)
        if os.path.isfile(p):
            for k, v in re.findall(r'<string name="([a-z_0-9]+)"[^>]*>(.*?)</string>', open(p, encoding="utf-8").read(), re.S):
                out[k] = v
    return out
base = keys("values")
NEW = ["setup_step_of", "remote_not_found", "remote_not_found_hint", "action_search_again", "remote_other", "net_nearby",
       "net_secured", "net_open", "net_none_hint", "net_wrong_password", "net_password_rule", "google_skip_hint",
       "google_sign_in_sub", "google_help_sub", "done_stats_short", "alice_code_label", "alice_note", "remote_found",
       "remote_press_ok"]
bad = n = 0
locs = sorted(d for d in os.listdir(res) if d.startswith("values"))
for d in locs:
    k = keys(d)
    miss = sorted(set(base) - set(k))
    n += 1
    if miss:
        bad += 1
        print("FAIL setup strings: %s misses %s" % (d, miss))
    for key in NEW:
        v = k.get(key, "")
        n += 1
        if not v or ";" in v or "\u2014" in v or "\u2013" in v or "\u203a" in v:
            bad += 1
            print("FAIL setup strings: %s %s empty or has a semicolon, a long dash or a chevron: %r" % (d, key, v))
    n += 1
    if "%1$d" not in k.get("setup_step_of", "") or "%2$d" not in k.get("setup_step_of", ""):
        bad += 1
        print("FAIL setup strings: %s setup_step_of without both numbers" % d)
print("setup strings: %d locales, %d checks, %d failed" % (len(locs), n, bad))
sys.exit(1 if bad or len(locs) < 18 else 0)
PY
sh "$HERE/test/pointer/run.sh" || RC=1
exit $RC
