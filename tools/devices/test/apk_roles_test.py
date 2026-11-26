#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""apk_roles_test.py: the image builder's HOME rule (tools/build/tree.py apk-roles, live_home_priorities, home_clashes).

Lumen Setup (HOME 10) runs the first start, Lumen Home (HOME 3) is the one launcher after it. Any other priv-app whose
enabled HOME filter is at Lumen Home's priority or above makes Android show its chooser on the first start (the RS30
beta2 trial: TvProvision and Lumen Home both at 3) or open that app. A filter of an activity the manifest disables
(ManagedProvisioning's PostEncryptionActivity at 10) is not resolved and does not count.

Pure cases on the two functions, then real APKs: tiny manifests linked with aapt2 into a fake system tree, checked by
tree.py apk-roles as the builder runs it. --mutate runs the same cases on broken copies of tree.py (each must go red).
  python3 tools/devices/test/apk_roles_test.py [--mutate]
Needs aapt2 and android.jar: ANDROID_SDK (default ~/lumen-tools/android-sdk, else ~/Library/Android/sdk).
"""
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
TREE = os.environ.get("APK_ROLES_TREE", os.path.join(G, "tools", "build", "tree.py"))
HOME = "android.intent.category.HOME"


def load_tree():
    spec = importlib.util.spec_from_file_location("tree_under_test", TREE)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def sdk():
    for d in (os.environ.get("ANDROID_SDK", ""), os.path.expanduser("~/lumen-tools/android-sdk"),
              os.path.expanduser("~/Library/Android/sdk")):
        a, j = os.path.join(d, "build-tools", "36.0.0", "aapt2"), os.path.join(d, "platforms", "android-34", "android.jar")
        if d and os.path.isfile(a) and os.path.isfile(j):
            return a, j
    sys.exit("apk_roles_test: no aapt2 / android.jar (set ANDROID_SDK)")


passed = failed = 0


def check(name, cond):
    global passed, failed
    if cond:
        passed += 1
        print("ok   " + name)
    else:
        failed += 1
        print("FAIL " + name)


def pure(t):
    el = lambda d, tag, **a: (d, tag, a)
    home = [el(0, "manifest"), el(1, "application"),
            el(2, "activity", name=".A"), el(3, "intent-filter", priority=3), el(4, "action", name="MAIN"),
            el(4, "category", name=HOME),
            el(2, "activity", name=".Off", enabled=False), el(3, "intent-filter", priority=10), el(4, "category", name=HOME),
            el(2, "activity-alias", name=".Alias"), el(3, "intent-filter", priority=5), el(4, "category", name=HOME),
            el(2, "activity", name=".NoHome"), el(3, "intent-filter", priority=7), el(4, "category", name="LAUNCHER")]
    check("live HOME: enabled activity and alias counted, a disabled one and a non-HOME filter not",
          t.live_home_priorities(home) == [3, 5])
    check("live HOME: an activity after a disabled one is enabled again",
          t.live_home_priorities([el(0, "manifest"), el(1, "application"), el(2, "activity", enabled=False),
                                  el(3, "intent-filter"), el(4, "category", name=HOME), el(2, "activity"),
                                  el(3, "intent-filter", priority=2), el(4, "category", name=HOME)]) == [2])
    check("live HOME: a disabled application counts nothing",
          t.live_home_priorities([el(0, "manifest"), el(1, "application", enabled=False), el(2, "activity"),
                                  el(3, "intent-filter", priority=3), el(4, "category", name=HOME)]) == [])
    check("live HOME: a resource-valued enabled counts (unknown at build time)",
          t.live_home_priorities([el(0, "manifest"), el(1, "application"), el(2, "activity", enabled="@0x7f050000"),
                                  el(3, "intent-filter", priority=3), el(4, "category", name=HOME)]) == [3])
    check("live HOME: a disabled activity-alias counts nothing",
          t.live_home_priorities([el(0, "manifest"), el(1, "application"), el(2, "activity-alias", enabled=False),
                                  el(3, "intent-filter", priority=4), el(4, "category", name=HOME)]) == [])
    check("live HOME: a receiver's filter is no HOME candidate, also right after an activity's filter",
          t.live_home_priorities([el(0, "manifest"), el(1, "application"), el(2, "activity"),
                                  el(3, "intent-filter", priority=4), el(4, "category", name="LAUNCHER"),
                                  el(2, "receiver"), el(3, "intent-filter", priority=6),
                                  el(4, "category", name=HOME)]) == [])
    tv = ("product/priv-app/TvProvision/TvProvision.apk", [3])
    check("clash: the beta2 tie (TvProvision 3 = Lumen Home 3, no setup)", len(t.home_clashes(None, 3, [tv])) == 1)
    check("clash: the tie also next to Lumen Setup", len(t.home_clashes(10, 3, [tv])) == 1)
    check("no clash: below Lumen Home (the X50's Google TV launcher at 2)",
          t.home_clashes(10, 3, [("product/priv-app/TVLauncher/TVLauncher.apk", [2])]) == [])
    check("no clash: the settings fallback at -1000 and an app without HOME",
          t.home_clashes(10, 3, [("x/TvSettings.apk", [-1000]), ("x/M.apk", [])]) == [])
    check("clash: 10 next to Lumen Setup", len(t.home_clashes(10, 3, [("x/W.apk", [10])])) == 1)
    check("clash: between Lumen Home and Setup", len(t.home_clashes(10, 3, [("x/L.apk", [1, 5])])) == 1)
    check("clash: 10 next to Lumen Setup alone", len(t.home_clashes(10, None, [("x/W.apk", [10])])) == 1)
    check("no clash: below 10 next to Lumen Setup alone", t.home_clashes(10, None, [("x/L.apk", [5])]) == [])
    check("no rule without Setup and Home", t.home_clashes(None, None, [tv]) == [])


MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="%s">
  <application android:hasCode="false">%s</application>
</manifest>
"""


def activity(name, prio, enabled=True, extra=""):
    return ('<activity android:name="%s" android:exported="true"%s><intent-filter android:priority="%d">'
            '<action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.HOME" />'
            '<category android:name="android.intent.category.DEFAULT" />%s</intent-filter></activity>'
            % (name, "" if enabled else ' android:enabled="false"', prio, extra))


APKS = {
    "priv-app/Z9xSetup/Z9xSetup.apk": ("org.lumen.setup", activity(".SetupActivity", 10)),
    "priv-app/Z9xHome/Z9xHome.apk": ("org.lumen.home", activity(".HomeActivity", 3)),
    "product/priv-app/TvProvision/TvProvision.apk": ("com.android.tv.provision", activity(
        ".DefaultActivity", 3, extra='<category android:name="android.intent.category.SETUP_WIZARD" />')),
    "priv-app/ManagedProvisioning/ManagedProvisioning.apk": ("com.android.managedprovisioning", activity(
        ".preprovisioning.PostEncryptionActivity", 10, enabled=False)),
    "system_ext/priv-app/TvSettingsTwoPanel/TvSettingsTwoPanel.apk": ("com.android.tv.settings", activity(
        ".system.FallbackHome", -1000)),
    "priv-app/X50Projector/X50Projector.apk": ("org.lumen.x50.projector", ""),
}
SETUP = "priv-app/Z9xSetup/Z9xSetup.apk:org.lumen.setup:setup"
LHOME = "priv-app/Z9xHome/Z9xHome.apk:org.lumen.home:home"
PROJ = "priv-app/X50Projector/X50Projector.apk:org.lumen.x50.projector:nohome"


def real(tmp):
    aapt2, jar = sdk()
    sysd = os.path.join(tmp, "system")
    for rel, (pkg, body) in APKS.items():
        out = os.path.join(sysd, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        m = os.path.join(tmp, "m.xml")
        open(m, "w", encoding="utf-8").write(MANIFEST % (pkg, body))
        r = subprocess.run([aapt2, "link", "--manifest", m, "-I", jar, "-o", out, "--min-sdk-version", "34",
                            "--target-sdk-version", "34"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if r.returncode:
            sys.exit("apk_roles_test: aapt2 link %s: %s" % (rel, r.stdout.decode()))
    indir = os.path.join(G, "tools", "lumen")

    def roles(*rows):
        r = subprocess.run([sys.executable, "-I", TREE, "apk-roles", sysd, indir] + list(rows),
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return r.returncode, r.stdout.decode()
    rc, out = roles(LHOME, PROJ)
    check("real: Lumen Home and TvProvision both at 3 (the beta2 recipe) refused", rc != 0 and "TvProvision" in out)
    rc, out = roles(SETUP, LHOME, PROJ)
    check("real: the tie is refused next to Lumen Setup too", rc != 0 and "TvProvision" in out)
    tv = os.path.join(sysd, "product/priv-app/TvProvision")
    shutil.move(tv, os.path.join(tmp, "TvProvision"))
    rc, out = roles(SETUP, LHOME, PROJ)
    check("real: Setup 10 + Home 3, TvProvision gone, ManagedProvisioning's disabled 10 ignored: passes",
          rc == 0 and "other priv-apps below HOME 3" in out)
    rc, out = roles(LHOME, PROJ)
    check("real: Home row alone while Lumen Setup sits in the tree unlisted is refused (10 outranks 3)",
          rc != 0 and "Z9xSetup" in out)
    st = os.path.join(sysd, "priv-app/Z9xSetup")
    shutil.move(st, os.path.join(tmp, "Z9xSetup"))
    rc, out = roles(LHOME, PROJ)
    check("real: Home alone, no Setup and no TvProvision, passes", rc == 0 and "below HOME 3" in out)
    shutil.move(os.path.join(tmp, "Z9xSetup"), st)
    shutil.move(os.path.join(tmp, "TvProvision"), tv)
    rc, out = roles(PROJ)
    check("real: no setup or home row, no HOME rule", rc == 0)


def run_all():
    t = load_tree()
    pure(t)
    tmp = tempfile.mkdtemp(prefix="apkroles.")
    try:
        real(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print("apk_roles_test: %d passed, %d failed" % (passed, failed))
    return failed == 0


MUTANTS = [
    ("tie_allowed", "elif home is not None and top >= home:", "elif home is not None and top > home:"),
    ("disabled_counted", 'comp_depth, comp_on = depth, a.get("enabled", True) is not False', "comp_depth, comp_on = depth, True"),
    ("only_with_setup", 'for part in ("", "product", "system_ext") if setup or home_rel else ():',
     'for part in ("", "product", "system_ext") if setup else ():'),
    ("home_rule_gone", "        elif home is not None and top >= home:\n", "        elif False:\n"),
    ("setup_rule_gone", "if setup is not None and top >= SETUP_HOME:", "if False:"),
    ("app_disable_ignored", 'app_on = a.get("enabled", True) is not False', "app_on = True"),
    ("alias_ignored", 'elif tag in ("activity", "activity-alias"):', 'elif tag in ("activity",):'),
    ("receiver_filter_counted", "[a.get(\"priority\", 0), app_on and comp_on, False] if comp_depth is not None else None",
     "[a.get(\"priority\", 0), app_on and comp_on, False]"),
    ("clash_not_fatal", "    if bad:\n        fail(", "    if False:\n        fail("),
]


def mutate():
    src = open(TREE, encoding="utf-8").read()
    killed = 0
    for name, old, new in MUTANTS:
        if src.count(old) != 1:
            print("BROKEN %s (anchor %d hits)" % (name, src.count(old)))
            continue
        d = tempfile.mkdtemp(prefix="apkroles-mut.")
        p = os.path.join(d, "tree.py")
        open(p, "w", encoding="utf-8").write(src.replace(old, new))
        env = dict(os.environ, APK_ROLES_TREE=p)
        r = subprocess.run([sys.executable, os.path.abspath(__file__)], env=env, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT)
        shutil.rmtree(d, ignore_errors=True)
        ok = r.returncode != 0
        killed += ok
        print("%s %s" % ("killed" if ok else "ALIVE ", name))
    print("apk_roles mutants: %d/%d killed" % (killed, len(MUTANTS)))
    return killed == len(MUTANTS)


if __name__ == "__main__":
    if sys.argv[1:] == ["--mutate"]:
        sys.exit(0 if run_all() and mutate() else 1)
    sys.exit(0 if run_all() else 1)
