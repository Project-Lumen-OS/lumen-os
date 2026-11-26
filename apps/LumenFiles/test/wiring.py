#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Files wiring (no build): python3 test/wiring.py ROOT. Every check names what it guards; mutate.py turns each red."""
import glob, os, re, sys
import xml.etree.ElementTree as ET

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
APP = os.path.join(ROOT, "apps", "LumenFiles")
HOME = os.path.join(ROOT, "apps", "LumenHome")
A = "{http://schemas.android.com/apk/res/android}"
bad = n = 0


def ok(c, what):
    global bad, n
    n += 1
    if not c:
        bad += 1
        print("FAIL files wiring: " + what)


def rd(*p):
    with open(os.path.join(*p), encoding="utf-8") as f:
        return f.read()


def src(rel):
    return rd(APP, "src/org/lumen/files", rel)


def const(text, name):
    m = re.search(r'static final String[^;]*\b%s = "([^"]*)"' % name, text)
    return m.group(1) if m else None


# 1. manifest: own package, a Home tile, read-only providers, no silent install, http for DLNA
man = ET.parse(os.path.join(APP, "AndroidManifest.xml")).getroot()
perms = [p.get(A + "name") for p in man.iter("uses-permission")]
ok(man.get("package") == "org.lumen.files", "package org.lumen.files")
ok("android.permission.INSTALL_PACKAGES" not in perms, "no INSTALL_PACKAGES: the system installer asks the user")
ok("android.permission.REQUEST_INSTALL_PACKAGES" in perms and "android.permission.MANAGE_EXTERNAL_STORAGE" in perms,
   "may hand APKs to the installer and read every drive")
ok("android.permission.CHANGE_WIFI_MULTICAST_STATE" in perms, "multicast lock for the DLNA search")
appl = man.find("application")
ok(appl.get(A + "usesCleartextTraffic") == "true", "http to DLNA servers allowed")
ok(appl.get(A + "banner") == "@drawable/files_banner", "TV banner for the Home tile")
acts = {a.get(A + "name"): a for a in appl.iter("activity")}
main = acts.get(".ui.FilesActivity")
cats = [c.get(A + "name") for f in (main.iter("intent-filter") if main is not None else []) for c in f.iter("category")]
ok(main is not None and main.get(A + "exported") == "true" and "android.intent.category.LEANBACK_LAUNCHER" in cats,
   "FilesActivity is exported with LEANBACK_LAUNCHER (the Home tile)")
for name in (".open.PlayerActivity", ".open.PhotoActivity"):
    ok(name in acts and acts[name].get(A + "exported") == "false", name + " not exported")
provs = {p.get(A + "name"): p for p in appl.iter("provider")}
for name, auth in ((".open.LocalProvider", "org.lumen.files.local"), (".net.NetFilesProvider", "org.lumen.files.netfiles")):
    p = provs.get(name)
    ok(p is not None and p.get(A + "exported") == "false" and p.get(A + "grantUriPermissions") == "true"
       and p.get(A + "authorities") == auth, name + ": not exported, one-off grants, authority " + auth)
ok(const(src("open/LocalProvider.java"), "AUTHORITY") == "org.lumen.files.local", "LocalProvider.AUTHORITY matches the manifest")
ok(const(src("net/NetFilesProvider.java"), "AUTHORITY") == "org.lumen.files.netfiles", "NetFilesProvider.AUTHORITY matches")
ok('throw new SecurityException("read only")' in src("open/LocalProvider.java"), "LocalProvider serves reads only")

# 2. APK: the system installer with its confirm window, never a session of our own
ai = src("open/ApkInstall.java")
ok("Intent.ACTION_INSTALL_PACKAGE" in ai and ".putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)" in ai,
   "APK goes to the system installer as a trusted source (one confirm window)")
ok(".putExtra(Intent.EXTRA_RETURN_RESULT, true)" in ai and "startActivityForResult" in ai, "installer result comes back")
alls = "".join(rd(p) for p in glob.glob(os.path.join(APP, "src/**/*.java"), recursive=True))
ok("getPackageInstaller()" not in alls and "createSession" not in alls and "INSTALL_PACKAGES\"" not in alls, "Files never installs by itself")

# 3. open: Lumen Play first, else the built-in player
op = src("open/Opener.java")
ok(const(op, "LUMEN_PLAY") == "com.xgimiru.player", "Lumen Play's package")
play = op[op.index("public static boolean play("):]
ok(play.index(".setPackage(LUMEN_PLAY)") < play.index("PlayerActivity.class"), "videos and music: Lumen Play before the built-in player")
ok("Opener.readable(data)" in src("open/PlayerActivity.java") and "LocalFiles.inPlaces(this, f)" in src("open/PlayerActivity.java"),
   "the built-in player reads only Files' own addresses and drives")

# 4. the screen: long OK, panels without the ring, the strip and the bar
fa = src("ui/FilesActivity.java")
ok("LONG_OK_MS" in fa and "actions(tag);" in fa and "KeyEvent.KEYCODE_MENU" in fa, "long OK and MENU open the actions")
ok("b.ring = false;" in src("ui/Sheet.java"), "the actions panel keeps filled rows without the ring")
ok("mPrompt.busy()) mPrompt.cancel();" in fa, "BACK during a job is Cancel")
ok("return trap(e, mPrompt) || super.dispatchKeyEvent(e);" in fa and "return trap(e, mSheet) || super.dispatchKeyEvent(e);" in fa,
   "arrows stay inside a window and the actions panel")
pr = src("ui/Prompt.java")
ok("(safe ? n : y).requestFocus();" in pr and "mPrompt.confirmLoss(q, getString(R.string.files_cannot_undo)" in fa,
   "a delete confirm starts on Cancel")
ok("give(mBack, lost);" in src("ui/Sheet.java") and "Sheet.give(mBack, lost);" in pr, "closing a panel gives the focus back to its row")
ok("registerReceiver(mMedia, Volumes.changes()" in fa, "drives coming and going are followed live")
ok("ServersMove.once(app)" in fa, "saved servers move from Home once")
ok("text = Math.max(text, s.getPaint().measureText(sub.toString()));" in fa
   and "int width = Fit.width(Ui.px(w), text, b.getPaddingLeft() + b.getPaddingRight() + Ui.px(2), Ui.px(CARD_MAX));" in fa
   and "new LinearLayout.LayoutParams(width, -1);" in fa,
   "a place card grows to fit its free space line (no cut 'of 56...')")
ok("        fitStripHeight();\n    }" in fa and "h = Math.max(h, c.getMeasuredHeight());" in fa
   and "int want = Math.max(Ui.px(STRIP_H), h + mStripScroll.getPaddingTop() + mStripScroll.getPaddingBottom());" in fa,
   "the place strip is as tall as its tallest card (the free space line is never clipped at the bottom)")
for cat in ("VIDEO", "MUSIC", "PHOTO", "APK", "ARCHIVE"):
    ok(re.search(r"\b%s\(Kind\.%s\)" % (cat, cat), src("model/Category.java")), "kind view " + cat)

# 5. Home: the panel moved (not copied), the USB card and the keyboard hint open Files
ok(not os.path.isdir(os.path.join(HOME, "src/org/lumen/home/usb")) and not os.path.isdir(os.path.join(HOME, "src/org/lumen/home/net")),
   "Home keeps no file or SMB code")
smb = [p for p in glob.glob(os.path.join(ROOT, "apps", "**", "Smb2.java"), recursive=True)]
ok(len(smb) == 1 and "LumenFiles" in smb[0], "one SMB client in the tree: %s" % smb)
hm = rd(HOME, "AndroidManifest.xml")
ok("UsbFilesActivity" not in hm and "FilesLauncher" not in hm and "usbfiles" not in hm and "netfiles" not in hm, "Home's manifest has no Files panel")
fl = rd(HOME, "src/org/lumen/home/FilesLink.java")
ok(const(fl, "PKG") == man.get("package") and const(fl, "ACTIVITY") == "org.lumen.files.ui.FilesActivity", "FilesLink names Files' activity")
ok(const(fl, "EXTRA_PATH") == const(fa, "EXTRA_PATH") and const(fl, "EXTRA_CATEGORY") == const(fa, "EXTRA_CATEGORY"),
   "FilesLink extras equal FilesActivity's")
ok('putExtra(EXTRA_CATEGORY, "APK")' in fl, "Install from USB opens the Apps view")
ok("u.intent = FilesLink.FILES + v.path;" in rd(HOME, "src/org/lumen/home/data/HomeLoader.java"), "the USB card carries its drive")
ok("start(c, FilesLink.open(FilesLink.path(k.intent)), \"files\");" in rd(HOME, "src/org/lumen/home/Launch.java"), "the USB card opens Files there")
ho = rd(HOME, "src/org/lumen/home/data/ServersHandOff.java")
sm = src("data/ServersMove.java")
ok('android:authorities="%s"' % const(sm, "AUTHORITY") in hm and const(ho, "METHOD") == const(sm, "METHOD"), "hand-off provider and method match")
ok("checkSignatures(c.getPackageName(), FILES) != PackageManager.SIGNATURE_MATCH" in ho and "!FILES.equals(getCallingPackage())" in ho,
   "only Files signed with Home's key gets the saved servers")
ok(const(ho, "FILES") == man.get("package") and const(ho, "KEY_ALIAS") == "lumen_home_net_pw", "hand-off reads Home's old key")
keys = re.search(r"KEYS = \{([^}]*)\}", sm).group(1).replace('"', "").replace(" ", "").split(",")
ok(all('b.putStringArray("%s"' % k in ho for k in keys) and 'b.putIntArray("port"' in ho, "hand-off bundle keys match")
ok(const(src("net/NetServers.java"), "KEY_ALIAS") == "lumen_files_net_pw", "Files seals passwords under its own keystore key")

# 6. strings: one language per app: English, Russian complete, no partial folder
def keys_of(p):
    return set(re.findall(r'<string name="([a-z0-9_]+)"', rd(p)))
en = keys_of(os.path.join(APP, "res/values/strings.xml"))
dirs = sorted(os.path.basename(d) for d in glob.glob(os.path.join(APP, "res/values-*")) if os.path.exists(os.path.join(d, "strings.xml")))
for d in dirs:
    ok(keys_of(os.path.join(APP, "res", d, "strings.xml")) == en, d + " has every key and no other")
used = set(re.findall(r"R\.string\.([a-z0-9_]+)", alls))
ok(used <= en, "every used string exists: missing %s" % sorted(used - en))
ok(en - used <= {"files_title"}, "no unused string: %s" % sorted(en - used - {"files_title"}))

# 7. icons: every lumen_ic_ Files draws is in the shared set
icons = set(re.findall(r"R\.drawable\.(lumen_ic_[a-z0-9_]+)", alls))
have = {os.path.basename(p)[:-4] for p in glob.glob(os.path.join(ROOT, "apps/core/lumen-icons/res/drawable/lumen_ic_*.xml"))}
ok(icons <= have, "icons from apps/core/lumen-icons: missing %s" % sorted(icons - have))

# 8. every image of every model carries Files at /system/priv-app/LumenFiles
sys.path.insert(0, os.path.join(ROOT, "tools", "devices"))
import layered  # noqa: E402
for bc in sorted(glob.glob(os.path.join(ROOT, "devices/*/build.conf"))):
    t = layered.text(bc)
    m = re.search(r"^apps=(.*)$", t, re.M)
    ok(m and "LumenFiles" in m.group(1).split(), "%s apps= has LumenFiles" % os.path.relpath(bc, ROOT))
for dc in sorted(glob.glob(os.path.join(ROOT, "devices/*/device.conf"))):
    m = re.search(r"^apps=(.*)$", layered.text(dc), re.M)
    if m:
        ok("LumenFiles" in m.group(1).split(), "%s apps= builds LumenFiles" % os.path.relpath(dc, ROOT))
for st in sorted(glob.glob(os.path.join(ROOT, "devices/*/image/steps.tsv"))):
    ok("priv-app/LumenFiles/LumenFiles.apk" in rd(st), "%s places LumenFiles.apk in priv-app" % os.path.relpath(st, ROOT))
ok("--app LumenFiles" in rd(ROOT, "tools/play6/build_play6.sh"), "the Play 6 build assembles Files")
print("files wiring: %d checks, %d failed" % (n, bad))
sys.exit(1 if bad else 0)
