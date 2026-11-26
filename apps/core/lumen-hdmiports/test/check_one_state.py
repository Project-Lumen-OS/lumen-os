#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""lumen-hdmiports guard: one HDMI port state for every place that shows or uses it.

Rules (2026-11-24, X50 Ultra: Home chips said "Not connected" while the HDMI settings page said "connected"):
  1. Lumen Home decides no port state itself: no framework input state or CEC device list for our ports
     (one getInputState, for another TV input's inputs only), and it watches the port states while started.
  2. The Source key (lumen-source) and the menu (lumen-menu) read the port states through HdmiPorts; the only
     framework read left is SourceInputs' path for an older HDMI app without port states.
  3. The HDMI app has no settings screen of its own: SettingsActivity only redirects to the menu's HDMI page, and
     no projector menu opens it.
  4. The menu's HDMI page and Home word the states with the same shared strings.
Run: python3 -B apps/core/lumen-hdmiports/test/check_one_state.py [GSI] [--mutants]
"""
import os
import re
import shutil
import sys
import tempfile

RULES = []


def rule(f):
    RULES.append(f)
    return f


def read(root, rel):
    with open(os.path.join(root, rel), encoding="utf-8") as fh:
        return fh.read()


def code(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def java_files(root, rel):
    out = []
    for d, _, fs in os.walk(os.path.join(root, rel)):
        out += [os.path.relpath(os.path.join(d, f), root) for f in fs if f.endswith(".java")]
    return sorted(out)


@rule
def home_reads_one_state(root):
    bad = []
    n = 0
    for f in java_files(root, "apps/LumenHome/src"):
        c = code(read(root, f))
        k = c.count("getInputState(")
        if k and not f.endswith("data/InputSource.java"):
            bad.append("%s: framework input state outside InputSource" % f)
        n += k
        if "getConnectedDevices(" in c:
            bad.append("%s: its own CEC device list" % f)
        if "hdmistate" in c and "lumen-hdmiports" not in c and "HdmiPorts" not in c:
            bad.append("%s: reads the HDMI app's provider by hand" % f)
    if n > 1:
        bad.append("Home reads the framework input state %d times (one: another TV input's inputs)" % n)
    src = code(read(root, "apps/LumenHome/src/org/lumen/home/data/InputSource.java"))
    if "HdmiPorts.read(" not in src:
        bad.append("InputSource does not take the ports from HdmiPorts")
    act = code(read(root, "apps/LumenHome/src/org/lumen/home/HomeActivity.java"))
    if "HdmiPorts.watch(" not in act:
        bad.append("HomeActivity does not watch the port states (chips stay as they were at the last load)")
    for f in ("apps/LumenHome/src/org/lumen/home/HomeActivity.java", "apps/LumenHome/src/org/lumen/home/data/HomeLoader.java"):
        if "PortChip.off(" not in code(read(root, f)):
            bad.append("%s: its own rule for a disconnected chip" % f)
    return bad


@rule
def menu_and_source_read_one_state(root):
    bad = []
    for base in ("apps/core/lumen-menu/src", "apps/core/lumen-source/src"):
        for f in java_files(root, base):
            k = code(read(root, f)).count("getInputState(")
            allowed = 1 if f.endswith("SourceInputs.java") else 0
            if k > allowed:
                bad.append("%s: framework input state read %d times (allowed %d)" % (f, k, allowed))
    if "HdmiPorts.read(" not in code(read(root, "apps/core/lumen-source/src/org/lumen/source/SourceInputs.java")):
        bad.append("SourceInputs does not take the connected ports from HdmiPorts")
    page = code(read(root, "apps/core/lumen-menu/src/org/lumen/projector/panel/HdmiPage.java"))
    if "HdmiPorts.read(" not in page:
        bad.append("HdmiPage does not read HdmiPorts")
    return bad


@rule
def no_old_hdmi_screen(root):
    bad = []
    for rel in ("apps/X50TvInput/src/org/lumen/tvinput/SettingsActivity.java",
                "apps/X50TvInput/src/org/lumen/tvinput/InputPickerActivity.java",
                "apps/LumenTvInput/src/org/lumen/tvinput/InputPickerActivity.java"):
        if os.path.exists(os.path.join(root, rel)):
            bad.append("%s: an old HDMI screen is back (the menu's HDMI page and the Source card replace it)" % rel)
    s = code(read(root, "apps/LumenTvInput/src/org/lumen/tvinput/SettingsActivity.java"))
    for w in ("setContentView", "CheckBox", "LinearLayout", "TextView"):
        if w in s:
            bad.append("tvinput SettingsActivity builds a screen (%s): it must only redirect to the menu page" % w)
    for f in java_files(root, "apps"):
        if "/test/" in f or f.startswith("apps/LumenTvInput/") or f.startswith("apps/X50TvInput/"):
            continue
        c = code(read(root, f))
        if '"org.lumen.tvinput.SettingsActivity"' in c and "/org/lumen/x50/projector/MenuActivity.java" not in f:
            bad.append("%s: opens the HDMI app's settings address instead of the menu's HDMI page" % f)
    mc = code(read(root, "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"))
    if "FeatureId.SETTINGS_HDMI == f) { showHdmiPage()" not in mc:
        bad.append("MenuController does not open the HDMI page for settings.hdmi")
    return bad


@rule
def one_wording(root):
    bad = []
    page = code(read(root, "apps/core/lumen-menu/src/org/lumen/projector/panel/HdmiPage.java"))
    want = {"HdmiPort.ON": "lumen_connected", "HdmiPort.ASLEEP": "lumen_asleep", "HdmiPort.NO_SIGNAL": "lumen_no_signal"}
    for state, res in want.items():
        if not re.search(r"case %s: return c\.getString\(R\.string\.%s\)" % (re.escape(state), res), page):
            bad.append("HdmiPage words %s other than %s" % (state, res))
    if not re.search(r"default: return c\.getString\(R\.string\.lumen_not_connected\)", page):
        bad.append("HdmiPage: an unknown or empty port is not 'Not connected'")
    home = read(root, "apps/LumenHome/res/values/strings.xml")
    for name, res in (("input_asleep", "lumen_asleep"), ("input_no_signal", "lumen_no_signal"),
                      ("input_not_connected", "lumen_not_connected")):
        if not re.search(r'<string name="%s">@string/%s</string>' % (name, res), home):
            bad.append("Home's %s is not the shared %s" % (name, res))
    return bad


def check(root):
    bad = []
    for r in RULES:
        bad += ["%s: %s" % (r.__name__, b) for b in r(root)]
    return bad


MUTANTS = [
    ("Home reads the framework state again", "apps/LumenHome/src/org/lumen/home/HomeActivity.java",
     "mPorts = null;", "mPorts = null; getSystemService(android.media.tv.TvInputManager.class).getInputState(\"x\");"),
    ("Home stops watching", "apps/LumenHome/src/org/lumen/home/HomeActivity.java",
     "org.lumen.hdmiports.HdmiPorts.watch(this, mMain, () -> loadSoon(false))", "null"),
    ("Home's own CEC list", "apps/LumenHome/src/org/lumen/home/data/InputSource.java",
     "List<HdmiPort> read = HdmiPorts.read(c);",
     "List<HdmiPort> read = HdmiPorts.read(c); c.getSystemService(android.hardware.hdmi.HdmiControlManager.class).getConnectedDevices();"),
    ("loader's own off rule", "apps/LumenHome/src/org/lumen/home/data/HomeLoader.java",
     "org.lumen.home.data.PortChip.off(k.state)", "k.state == 13"),
    ("Source key reads the framework", "apps/core/lumen-source/src/org/lumen/source/SourceInputs.java",
     "if (p.plugged()) s.connected.add(p.port);",
     "if (c.getSystemService(TvInputManager.class).getInputState(p.inputId) == 0) s.connected.add(p.port);"),
    ("menu opens the old screen", "apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xMenuPages.java",
     "if (menu != null) menu.showHdmiPage();",
     "app.startActivity(new Intent().setClassName(\"org.lumen.tvinput\", \"org.lumen.tvinput.SettingsActivity\"));"),
    ("old screen back", "apps/LumenTvInput/src/org/lumen/tvinput/SettingsActivity.java",
     "Safe.run(TAG, \"redirect\", () -> openMenuPage(this));", "setContentView(new android.widget.TextView(this));"),
    ("page words ON as not connected", "apps/core/lumen-menu/src/org/lumen/projector/panel/HdmiPage.java",
     "case HdmiPort.ON: return c.getString(R.string.lumen_connected);",
     "case HdmiPort.ON: return c.getString(R.string.lumen_not_connected);"),
    ("menu row opens nothing", "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java",
     "if (FeatureId.SETTINGS_HDMI == f) { showHdmiPage(); return; }", ""),
]

FILES = sorted({m[1] for m in MUTANTS} | {
    "apps/LumenHome/res/values/strings.xml", "apps/core/lumen-menu/src/org/lumen/projector/panel/HdmiPage.java"})


def mutants(root):
    red = 0
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix="hdmiports_mut.")
        try:
            for d in ("apps",):
                shutil.copytree(os.path.join(root, d), os.path.join(tmp, d),
                                ignore=shutil.ignore_patterns("build", "*.apk", "res", "assets", "image"))
            shutil.copy(os.path.join(root, "apps/LumenHome/res/values/strings.xml"), os.path.join(tmp, "x.xml"))
            os.makedirs(os.path.join(tmp, "apps/LumenHome/res/values"), exist_ok=True)
            shutil.copy(os.path.join(root, "apps/LumenHome/res/values/strings.xml"),
                        os.path.join(tmp, "apps/LumenHome/res/values/strings.xml"))
            p = os.path.join(tmp, rel)
            s = open(p, encoding="utf-8").read()
            if old not in s:
                print("FAIL mutant %s: anchor gone in %s" % (name, rel))
                continue
            open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
            if check(tmp):
                red += 1
                print("ok   mutant red: %s" % name)
            else:
                print("FAIL mutant stays green: %s" % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    return red == len(MUTANTS)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    root = args[0] if args else os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../.."))
    bad = check(root)
    for b in bad:
        print("FAIL " + b)
    ok = not bad
    if ok:
        print("ok   lumen-hdmiports guard: one HDMI port state (%d rules)" % len(RULES))
    if "--mutants" in sys.argv:
        ok = mutants(root) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
