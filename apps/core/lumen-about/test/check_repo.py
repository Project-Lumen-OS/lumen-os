#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Repository checks of the XGIMI side in Settings > About (run by test/run.sh):
  1. strings: every res/values*/lumen_strings_about.xml has the keys of the English base, the same placeholders and
     plain text (no semicolon, no em or en dash), 18 files; the Russian file is a translation, not the English text;
  2. every projector app recipe (devices/*/app/*Projector.layers) names lumen-qr and lumen-about, lumen-qr first, and
     lumen-about depends on lumen-qr;
  3. every projector app manifest those recipes build declares the provider org.lumen.about and the screen, both
     exported, and the internet permission the catalog needs;
  4. the contract with the TvSettings fix (tools/base_patch/tvsettings_about): authority, method, the Bundle keys,
     row keys that start with lumen_, the screen named by its class;
  5. the list certificate is Lumen Installer's own, the catalog address is the one Lumen Installer reads;
  6. read only and data only: no command, no property or partition write, no reboot or recovery call, files written
     only into the app's own folder (the kept catalog), no model code, firmware version or official address written
     into the code (they come from the projector and the signed catalog);
  7. the TvSettings fix is in the signers of every image (sign_device for HB, RS30, Titan, the X50 base signer, the
     Play 6 signer, the Z9X recipe).
    python3 apps/core/lumen-about/test/check_repo.py GSI_ROOT"""
import glob
import os
import re
import sys

G = sys.argv[1]
sys.path.insert(0, os.path.join(G, "tools", "devices"))
from assemble_app import app_dir  # noqa: E402  apps/<app>, or its renamed folder (apps/app_dirs.tsv)
M = os.path.join(G, "apps/core/lumen-about")
S = os.path.join(M, "src/org/lumen/about")
bad = []


def fail(msg):
    bad.append(msg)
    print("FAIL " + msg)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


# 1. strings
STR = re.compile(r'<string name="([a-z0-9_]+)">(.*?)</string>', re.S)
PH = re.compile(r"%\d\$[sd]")
base = dict(STR.findall(read(os.path.join(M, "res/values/lumen_strings_about.xml"))))
files = sorted(glob.glob(os.path.join(M, "res/values*/lumen_strings_about.xml")))
if len(files) != 18:
    fail("strings: %d locale files, want 18" % len(files))
for f in files:
    loc = os.path.basename(os.path.dirname(f))
    s = dict(STR.findall(read(f)))
    if set(s) != set(base):
        fail("strings %s: keys differ: %s" % (loc, sorted(set(s) ^ set(base))))
    for k, v in s.items():
        if not k.startswith("lumen_about_"):
            fail("strings %s: key %s outside lumen_about_" % (loc, k))
        if k in base and sorted(PH.findall(v)) != sorted(PH.findall(base[k])):
            fail("strings %s: %s placeholders %s, base %s" % (loc, k, PH.findall(v), PH.findall(base[k])))
        if re.search("[;\u2014\u2013]", v):
            fail("strings %s: %s is not plain text: %r" % (loc, k, v))
ru = dict(STR.findall(read(os.path.join(M, "res/values-ru/lumen_strings_about.xml"))))
# names that stay the same in Russian: a format of one placeholder, the brands Android and Wi-Fi
KEEP = ("lumen_about_back_summary_file", "lumen_about_android", "lumen_about_wifi")
same = [k for k in base if k not in KEEP and ru.get(k) == base[k]]
if same:
    fail("strings ru: still English: %s" % same)
for k in ("lumen_about_back_summary_file",):
    if base[k].count("%1$s") != 1:
        fail("strings: %s needs exactly one %%1$s (AboutRows formats it)" % k)

# 2. recipes
conf = read(os.path.join(M, "module.conf"))
if not re.search(r"^depends=.*\blumen-qr\b", conf, re.M):
    fail("module.conf: lumen-about must depend on lumen-qr")
layers = sorted(glob.glob(os.path.join(G, "devices/*/app/*Projector.layers")))
if len(layers) < 8:
    fail("recipes: %d projector recipes, want every model's (8)" % len(layers))
apps = set()
for p in layers:
    text = read(p)
    mods = re.findall(r"^module\s+(\S+)\s*$", text, re.M)
    app = re.search(r"^app\s+(\S+)\s*$", text, re.M)
    rel = os.path.relpath(p, G)
    if "lumen-about" not in mods or "lumen-qr" not in mods:
        fail("%s: lumen-qr and lumen-about missing" % rel)
    elif mods.index("lumen-qr") > mods.index("lumen-about"):
        fail("%s: lumen-qr must come before lumen-about" % rel)
    if app:
        apps.add(app.group(1))

# 3. manifests
for a in sorted(apps):
    p = os.path.join(G, app_dir(G, a), "AndroidManifest.xml")
    if not os.path.isfile(p):
        fail("%s: no manifest" % a)
        continue
    m = read(p)
    prov = re.search(r'<provider\s[^>]*android:name="org\.lumen\.about\.AboutProvider"[^>]*>', m, re.S)
    if not prov or 'android:authorities="org.lumen.about"' not in prov.group(0) or \
            'android:exported="true"' not in prov.group(0):
        fail("%s: the exported provider org.lumen.about (AboutProvider) is missing" % a)
    act = re.search(r'<activity\s[^>]*android:name="org\.lumen\.about\.XgimiSystemActivity"[^>]*>', m, re.S)
    if not act or 'android:exported="true"' not in act.group(0):
        fail("%s: the exported screen XgimiSystemActivity is missing" % a)
    if 'android:name="android.permission.INTERNET"' not in m:
        fail("%s: no internet permission (the catalog)" % a)
    if m.count('android:authorities="org.lumen.about"') != 1:
        fail("%s: org.lumen.about declared %d times" % (a, m.count('android:authorities="org.lumen.about"')))

# 4. contract with the TvSettings fix
fix = read(os.path.join(G, "tools/base_patch/tvsettings_about/AboutFragment.java"))
prov = read(os.path.join(S, "AboutProvider.java"))
want = {"AUTHORITY": "org.lumen.about", "METHOD_ROWS": "rows", "ROWS": "rows", "KEY": "key", "TITLE": "title",
        "SUMMARY": "summary", "SCREEN": "screen"}
for k, v in want.items():
    if not re.search(r'\b%s = "%s"' % (k, re.escape(v)), prov):
        fail("AboutProvider: %s must be \"%s\" (the TvSettings fix reads it)" % (k, v))
if 'LUMEN_ABOUT = "org.lumen.about"' not in fix or 'LUMEN_ROWS = "rows"' not in fix:
    fail("tvsettings_about: authority or method differs from AboutProvider")
for k in ("key", "title", "summary", "screen"):
    if 'r.getString("%s")' % k not in fix:
        fail("tvsettings_about: does not read the row's %s" % k)
cases = re.findall(r"case ([A-Z_]+): return R\.string\.([a-z_]+);", prov)
enum = re.findall(r"^\s*([A-Z_]+),?\s*$", re.sub(r"/\*.*?\*/|//[^\n]*", "", read(os.path.join(S, "RowText.java")),
                                                  flags=re.S), re.M)
if sorted(c for c, _ in cases) != sorted(set(enum)) or any(r != "lumen_about_" + c.lower() for c, r in cases):
    fail("AboutProvider.text: each RowText X must give R.string.lumen_about_x: %s" % cases)
if "XgimiSystemActivity.class.getName()" not in prov:
    fail("AboutProvider: the screen must be named by its class")
rows = read(os.path.join(S, "AboutRows.java"))
keys = re.findall(r'KEY_[A-Z_]+ = "([a-z_]+)"', rows)
if len(keys) != 12 or len(set(keys)) != 12 or not all(k.startswith("lumen_") for k in keys):
    fail("AboutRows: twelve row keys that start with lumen_ (the TvSettings fix drops others): %s" % keys)

# 4b. Lumen Installer shows the same rows: it takes the plain classes and the text files of this module in exactly the
# languages the app itself is in (one language per screen: a row text in a language the app lacks would mix), no copy
inst_layers = read(os.path.join(G, "apps/LumenInstaller/app.layers"))
lst = "apps/LumenInstaller/lumen-about.files"
if not re.search(r"^base\s+apps/core/lumen-about\s+%s\s*$" % re.escape(lst), inst_layers, re.M):
    fail("apps/LumenInstaller/app.layers: no 'base apps/core/lumen-about %s' line" % lst)
else:
    listed = set(l.strip() for l in read(os.path.join(G, lst)).splitlines() if l.strip() and not l.startswith("#"))
    inst_res = os.path.join(G, "apps/LumenInstaller/res")
    want_files = set("res/%s/lumen_strings_about.xml" % d for d in os.listdir(inst_res)
                     if d.startswith("values") and os.path.isfile(os.path.join(inst_res, d, "strings.xml")))
    for f in sorted(want_files - set(os.path.relpath(f, M) for f in files)):
        fail("Lumen Installer is in a language this module has no texts for: %s" % f)
    want_files |= set("src/org/lumen/about/%s.java" % c for c in
                      ("XgimiFacts", "LightEngine", "FirmwareParts", "AboutRow", "AboutRows", "RowText"))
    if listed != want_files:
        fail("%s: lists %s, want %s" % (lst, sorted(listed ^ want_files), "the plain row classes and the text files of the app's languages"))
for c in glob.glob(os.path.join(G, "apps/LumenInstaller/src/org/lumen/about/*.java")):
    fail("Lumen Installer keeps its own copy of %s" % os.path.basename(c))

# 5. certificate and address
cert = os.path.join(M, "assets/lumen_about/index.x509.pem")
inst = os.path.join(G, "apps/LumenInstaller/assets/index/index.x509.pem")
if not os.path.isfile(cert) or read(cert) != read(inst):
    fail("assets/lumen_about/index.x509.pem must be Lumen Installer's list certificate")
if 'CERT_ASSET = "lumen_about/index.x509.pem"' not in read(os.path.join(S, "CatalogSource.java")):
    fail("CatalogSource: the certificate asset name changed")
engine = read(os.path.join(G, "apps/LumenInstaller/src/org/lumen/installer/core/Engine.java"))
addr = re.search(r'ADDRESS =\s*"([^"]+)"', read(os.path.join(S, "RestoreCatalog.java")))
if not addr or "/releases/download/index/catalog-v2.json" not in addr.group(1) or \
        "Project-Lumen-OS/lumen-installer" not in addr.group(1) or "catalog-v2.json" not in engine:
    fail("RestoreCatalog.ADDRESS must be Lumen Installer's catalog-v2.json on the release index")

# 6. read only, data only
FORBID = (
    (r"Runtime\.getRuntime\(\)|ProcessBuilder", "runs a command"),
    (r"SystemProperties\.set", "writes a property"),
    (r"/dev/block|/dev/|by-name", "touches a partition"),
    (r"RecoverySystem|PowerManager|\breboot\b|ACTION_REBOOT|installPackage", "reboots or installs"),
    (r"\bG0\d{3}\b|\bH0\d{3}\b", "names a model code in the code"),
    (r"\b[vV]?\d+\.\d+\.\d+\b", "names a firmware version in the code"),
    (r"XGIMI_[A-Z0-9][A-Z0-9_]*\.bin", "names a firmware file in the code"),
    (r"product-library\.static\.xgimi\.com/firmware/[A-Za-z]", "names an official address in the code"),
)
for p in sorted(glob.glob(os.path.join(S, "*.java"))):
    code = read(p)
    body = re.sub(r"/\*.*?\*/|//[^\n]*", "", code, flags=re.S)  # comments may name examples
    for rx, what in FORBID:
        if re.search(rx, body):
            fail("%s %s" % (os.path.basename(p), what))
    if os.path.basename(p) != "XgimiFacts.java" and re.search(r'"/[A-Za-z]', body):
        fail("%s names a path of the system (only XgimiFacts reads the stock config)" % os.path.basename(p))
    if re.search(r"FileOutputStream|Files\.write|\.delete\(\)|renameTo", body):
        if os.path.basename(p) != "CatalogSource.java" or "getFilesDir()" not in body:
            fail("%s writes a file outside the app's own folder" % os.path.basename(p))
prov_body = re.sub(r"/\*.*?\*/|//[^\n]*", "", prov, flags=re.S)
for meth, ret in (("insert", "null"), ("delete", "0"), ("update", "0"), ("query", "null")):
    m = re.search(r"public \w+ %s\([^)]*\) \{\s*return (\w+);\s*\}" % meth, prov_body)
    if not m or m.group(1) != ret:
        fail("AboutProvider.%s must do nothing (return %s)" % (meth, ret))

# 7. the TvSettings fix reaches every image
pt = read(os.path.join(G, "tools/base_patch/patches.tsv"))
if "\ttvsettings_about\tLcom/android/tv/settings/about/AboutFragment;\t" not in pt:
    fail("patches.tsv: no tvsettings_about row")
for rel, needle in (("tools/devices/sign_device.py", "base_patch.patched_apk("),
                    ("devices/g0111/image/sign_x50_base.py", "resign_input"),
                    ("tools/play6/sign_play6.py", "base_patch.patched_apk("),
                    ("devices/horse_popular/image/steps.tsv",
                     "system/system_ext/priv-app/TvSettingsTwoPanel/TvSettingsTwoPanel.apk\tstage/apk/TvSettingsTwoPanel.apk")):
    p = os.path.join(G, rel)
    if not os.path.isfile(p) or needle not in read(p):
        fail("%s: the TvSettings fixes are not signed in (%s)" % (rel, needle))

print("check_repo: %d layers, %d apps, %d string files, %d failed" % (len(layers), len(apps), len(files), len(bad)))
sys.exit(1 if bad else 0)
