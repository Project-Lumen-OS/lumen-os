#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Home and Google Play without a Google account (PlayGate): Play's TV build crashes then on a projector that is
# not Play Protect certified ("GMS compliance not supported on this form factor"), so Home opens the Google sign-in
# first. sh test/play/run.sh (plain JDK, no R.java)
# PlayGateTest: the rule. The python part pins the wiring: the gate is the first statement of Launch.start (every
# launch of Home goes through it), the sign-in intent (TvSettings' add account screen for com.google, no picker), the
# account read, the permission in the manifest and in both privapp lists of the tree. Mutants: test/play/mutate.py.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=${HEADERS_JAR:-$ROOT/apps/sdk/framework-full-headers.jar}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -cp "$HJ:$AJ" -d "$OUT" \
  "$HERE/src/org/lumen/home/PlayGate.java" "$HERE/test/play/PlayGateTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT:$HJ:$AJ" org.lumen.home.PlayGateTest
python3 - "$HERE" "$ROOT" <<'EOF'
import os, sys
import xml.etree.ElementTree as ET
app, root = sys.argv[1], sys.argv[2]
bad = 0
n = 0

def ok(cond, what):
    global bad, n
    n += 1
    if not cond:
        bad += 1
        print("FAIL home play wiring: " + what)

def body(text, head):
    i = text.find(head)
    if i < 0:
        return []
    j = text.index("{", i)
    depth, k = 0, j
    while True:
        c = text[k]
        depth += c == "{"
        depth -= c == "}"
        if depth == 0:
            break
        k += 1
    return [l.strip() for l in text[j + 1:k].split("\n") if l.strip() and not l.strip().startswith("//")]

H = os.path.join(app, "src/org/lumen/home")
la = open(os.path.join(H, "Launch.java"), encoding="utf-8").read()
st = body(la, "public static void start(Context c, Intent i, String what)")
ok(st[:3] == ["Intent signIn = PlayGate.signInInstead(c, i);", "if (signIn != null) {", "i = signIn;"],
   "Launch.start: the gate first, its intent replaces the launch: %s" % st[:3])
ok("c.startActivity(i, ActivityOptions.makeBasic().toBundle());" in st, "Launch.start starts the (replaced) intent")
# every launch of Play in Home goes through Launch.start: no direct startActivity next to Play's package
past = []
for dp, _, fs in os.walk(H):
    for f in fs:
        if f.endswith(".java"):
            for ln, l in enumerate(open(os.path.join(dp, f), encoding="utf-8").read().split("\n"), 1):
                if "startActivity(" in l and ("com.android.vending" in l or "PKG_PLAY" in l):
                    past.append("%s:%d" % (f, ln))
ok(not past, "Play started past Launch.start: %s" % past)
pg = open(os.path.join(H, "PlayGate.java"), encoding="utf-8").read()
ok(body(pg, "static boolean signInFirst(") == ["return PLAY.equals(pkg) && canRead && accounts == 0 && signInThere;"],
   "signInFirst: the one rule")
ok(body(pg, "static Intent signInIntent()") == ["return new Intent(Settings.ACTION_ADD_ACCOUNT)",
                                               ".putExtra(EXTRA_TYPE, SIGN_IN_TYPE)",
                                               ".putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] {SIGN_IN_TYPE})",
                                               ".addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);"],
   "the sign-in: the system add account screen, one Google type, a new task")
si = body(pg, "public static Intent signInInstead(Context c, Intent launch)")
need = ["if (!PLAY.equals(pkg)) return null;",
        "boolean canRead = c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;",
        "for (String t : PLAY_ACCOUNT_TYPES) n += am.getAccountsByType(t).length;",
        "boolean there = c.getPackageManager().resolveActivity(s, PackageManager.MATCH_DEFAULT_ONLY) != null;",
        "return signInFirst(pkg, canRead, n, there) ? s : null;",
        "} catch (Throwable t) {", "return null;"]
for x in need:
    ok(x in si, "signInInstead: %s" % x)
ok("if (canRead) {" in si and need[2] in si and si.index("if (canRead) {") < si.index(need[2]),
   "accounts read only with the permission")

A = "{http://schemas.android.com/apk/res/android}"
man = ET.parse(os.path.join(app, "AndroidManifest.xml")).getroot()
perm = "android.permission.GET_ACCOUNTS_PRIVILEGED"
ok(perm in [u.get(A + "name") for u in man.findall("uses-permission")], "the manifest asks for " + perm)
lists = [os.path.join(app, "image/privapp-permissions-z9xhome.xml"), os.path.join(root, "overlay/v1/privapp-permissions-z9xhome.xml")]
for f in lists:
    if not os.path.isfile(f):
        print("home play wiring: no %s in this tree, skipped" % os.path.relpath(f, root))
        continue
    pp = [e for e in ET.parse(f).getroot().findall("privapp-permissions") if e.get("package") == "org.lumen.home"]
    ok(pp and perm in [p.get("name") for p in pp[0].findall("permission")], "%s lists %s" % (os.path.relpath(f, root), perm))
if all(os.path.isfile(f) for f in lists):
    ok(open(lists[0], "rb").read() == open(lists[1], "rb").read(), "the image copy in overlay/v1 equals apps/LumenHome/image")
print("home play wiring: %d checks, %d failed" % (n, bad))
sys.exit(1 if bad else 0)
EOF
echo "home play gate: ok"
