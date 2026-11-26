#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Setup with a mouse or a touch screen, and its two "other" buttons (every model).
#   sh test/pointer/run.sh          (python3 only)
# Setup is made of standard clickable views (Pill, Row, cards with click listeners), so a tap is a click: nothing in
# the app may take touches away (no touch overrides or listeners, no view that wants a first tap for the focus).
# The remote step's "Use another remote" and the Wi-Fi step's "Other options" open their Android page through the
# shared chain of apps/core/lumen-system (SystemPages): the RS30 t7 sent android.settings.BLUETOOTH_SETTINGS, which no
# app of a Lumen image handles (TvSettings has no such filter), so the click did nothing. Every recipe that builds
# Lumen Setup ships that module. Mutants: test/pointer/mutate.py.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
ROOT=${LUMEN_ROOT:-$(cd "$HERE/../.." && pwd)}
python3 - "$HERE" "$ROOT" <<'EOF'
import os, re, sys
app, root = sys.argv[1], sys.argv[2]
bad = n = 0

def ok(cond, what):
    global bad, n
    n += 1
    if not cond:
        bad += 1
        print("FAIL setup pointer: " + what)

def body(text, head):
    i = text.find(head)
    if i < 0:
        return []
    j = text.index("{", i)
    depth, k = 0, j
    while True:
        depth += text[k] == "{"
        depth -= text[k] == "}"
        if depth == 0:
            break
        k += 1
    return [l.strip() for l in text[j + 1:k].split("\n") if l.strip() and not l.strip().startswith("//")]

src = os.path.join(app, "src/org/lumen/setup")
files = {}
for d, _, fs in os.walk(src):
    for f in fs:
        if f.endswith(".java"):
            files[os.path.relpath(os.path.join(d, f), src)] = open(os.path.join(d, f), encoding="utf-8").read()
alltext = "\n".join(files.values())

# touches reach the standard click path
for pat, what in [(r"\bdispatchTouchEvent\s*\(", "a dispatchTouchEvent override"),
                  (r"\bonInterceptTouchEvent\s*\(", "an onInterceptTouchEvent override"),
                  (r"\bonTouchEvent\s*\(", "an onTouchEvent override"),
                  (r"setOnTouchListener\s*\(", "a touch listener"),
                  (r"setFocusableInTouchMode\s*\(\s*true", "a view that takes the first tap for the focus")]:
    hits = [f for f, t in files.items() if re.search(pat, t)]
    ok(not hits, "%s in %s" % (what, hits))
pill, row = files["ui/Pill.java"], files["ui/Row.java"]
ok("setClickable(true);" in body(pill, "public Pill(Context c, CharSequence text, boolean primary)"), "Pill is clickable")
ok("setFocusableInTouchMode(false);" in body(pill, "public Pill(Context c, CharSequence text, boolean primary)"),
   "Pill clicks at the first tap (not focusable in touch mode)")
ok("setClickable(true);" in body(row, "public Row(Context c, CharSequence titleText)"), "Row is clickable")
for cls in ("ui/Pill.java", "ui/Row.java"):
    pc = body(files[cls], "public boolean performClick()")
    ok(pc and pc[-1] == "return super.performClick();", "%s.performClick ends in the view's click (listener)" % cls)

# the remote step's and the Wi-Fi step's Android pages
rs = files["steps/RemoteStep.java"]
m = re.search(r"Pill other = new Pill\(ctx\(\), s\(R\.string\.remote_other\), false\)(?:\.icon\(Icon\.BLUETOOTH\))?;(.*?)mOther = other;", rs, re.S)
ok(m is not None and "other.setOnClickListener(x -> {" in m.group(1)
   and "if (!SystemPages.open(host, SettingsPage.BLUETOOTH)) L.w(" in m.group(1),
   "Use another remote: SystemPages.open(host, SettingsPage.BLUETOOTH)")
ok(re.search(r"(?m)^\s*btns\.addView\(other, ", rs) is not None, "Use another remote is on screen")
ns = files["steps/NetworkStep.java"]
ok(re.search(r"if \(a\.sec == WifiUtil\.SEC_OTHER\) \{\s*openWifiSettings\(\);", ns) is not None,
   "a network of another kind (enterprise) opens the Android Wi-Fi page through openWifiSettings")
ow = body(ns, "private void openWifiSettings()")
ok(ow and ow[0].startswith("if (SystemPages.open(host, SettingsPage.NETWORK))"), "Other options: SystemPages NETWORK: %s" % ow)
for raw in ("ACTION_BLUETOOTH_SETTINGS", "android.settings.BLUETOOTH_SETTINGS", "ACTION_WIFI_SETTINGS"):
    ok(raw not in alltext, "no own %s in Setup (the shared chain only)" % raw)

# the shared chain: Remotes & Accessories of TvSettings first (its slice), then the plain intents, then Settings
si = open(os.path.join(root, "apps/core/lumen-system/src/org/lumen/system/SystemIntent.java"), encoding="utf-8").read()
bt = re.search(r"case BLUETOOTH:\s*return Arrays\.asList\((.*?)\);", si, re.S)
ok(bt is not None and re.sub(r"\s+", " ", bt.group(1)) ==
   "new SystemIntent(SLICE_SETTINGS, TV_SETTINGS, SLICE_URI, ACCESSORIES), of(BLUETOOTH), of(ROOT)",
   "BLUETOOTH chain: the accessories slice, BLUETOOTH_SETTINGS, Settings")
ok('ACCESSORIES = "content://com.android.tv.settings.accessories.sliceprovider/general";' in si
   and 'TV_SETTINGS = "com.android.tv.settings";' in si and 'SLICE_SETTINGS = "android.settings.SLICE_SETTINGS"' in si,
   "the accessories slice of TvSettings")
nw = re.search(r"case NETWORK:\s*return Arrays\.asList\((.*?)\);", si, re.S)
ok(nw is not None and re.sub(r"\s+", " ", nw.group(1)) == "of(WIFI), of(ROOT)", "NETWORK chain: Wi-Fi, Settings")
sp = open(os.path.join(root, "apps/core/lumen-system/src/org/lumen/system/SystemPages.java"), encoding="utf-8").read()
ok("if (i.resolveActivity(ctx.getPackageManager()) == null) continue;" in sp, "SystemPages skips a way no app handles")
man = open(os.path.join(app, "AndroidManifest.xml"), encoding="utf-8").read()
ok("android.permission.QUERY_ALL_PACKAGES" in man, "Setup sees TvSettings for resolveActivity (QUERY_ALL_PACKAGES)")

# every recipe of Lumen Setup ships the module
recipes = []
for c in sorted(os.listdir(os.path.join(root, "devices"))):
    d = os.path.join(root, "devices", c, "app")
    if not os.path.isdir(d):
        continue
    for f in sorted(os.listdir(d)):
        t = open(os.path.join(d, f), encoding="utf-8").read() if f.endswith(".layers") else ""
        if re.search(r"(?m)^app\s+(Z9xSetup|HbSetup)\s*$", t):
            recipes.append(c)
            ok(re.search(r"(?m)^module\s+lumen-system\s*$", t) is not None, "devices/%s/app/%s ships lumen-system" % (c, f))
ok("g0155" in recipes and "horse_popular" in recipes and "goat_elegant" in recipes, "Setup recipes of the Z9X, the RS30 and the HB: %s" % recipes)
print("setup pointer: %d checks, %d failed" % (n, bad))
sys.exit(1 if bad else 0)
EOF
