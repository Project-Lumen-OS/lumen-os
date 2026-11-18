#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""mutate.py: targeted mutants of the HDMI-CEC page (lumen-cec), its Z9X / HB driver and the X50 family driver.
Each mutant changes one line of the code or data the tests pin; the named suites must then fail (the mutant is
killed). The tree is restored after every mutant, also on error.
  python3 apps/core/lumen-cec/test/parity/mutate.py [NAME_SUBSTRING...]
Suites: parity = test/parity/run.sh (Z9X, HB, X50, RS30 goldens), x50 = apps/X50Projector/test/run.sh, defaults =
test/defaults/run.sh (the stock CEC defaults table and the one-time step), tool = tools/devices/test/cec_defaults_test.sh
(the image tool, the RROs and the stock evidence)."""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../../.."))
PAGE = "apps/core/lumen-cec/src/org/lumen/projector/cec/CecPage.java"
POLICY = "apps/ProjectorBase/src/org/lumen/projector/cec/CecPolicyDriver.java"
X = "apps/X50Projector/src/org/lumen/x50/projector/"
SUITES = {
    "parity": ["sh", "apps/core/lumen-cec/test/parity/run.sh"],
    "x50": ["sh", "apps/X50Projector/test/run.sh"],
    "defaults": ["sh", "apps/core/lumen-cec/test/defaults/run.sh"],
    "tool": ["bash", "tools/devices/test/cec_defaults_test.sh"],
}
DEF = "apps/core/lumen-cec/src/org/lumen/cec/CecDefaults.java"
SET = "apps/core/lumen-cec/src/org/lumen/cec/CecSetting.java"
GLUE = "apps/core/lumen-cec/src/org/lumen/projector/cec/CecStockDefaults.java"
Z9X_POLICY = "apps/ProjectorBase/src/org/lumen/projector/cec/CecPolicy.java"
TOOL = "tools/devices/cec_defaults.py"
TABLE = "devices/_image/cec_defaults.tsv"

# (name, file, old, new, suites that must fail)
MUTANTS = [
    ("page: no note rows", PAGE,
     "        if (shown.contains(r)) page.add(new TextRow(app, s(text)));",
     "        if (false) page.add(new TextRow(app, s(text)));", ["parity"]),
    ("page: rows always need HDMI-CEC", PAGE,
     "boolean usable = on || !driver.rowsNeedMaster();", "boolean usable = on;", ["parity"]),
    ("page: rows never need HDMI-CEC", PAGE,
     "boolean usable = on || !driver.rowsNeedMaster();", "boolean usable = true;", ["parity"]),
    ("page: wake note after the switch row", PAGE,
     "        note(CecRow.WAKE_NOTE, R.string.lumen_cec_wake_note);\n",
     "", ["parity"]),
    ("page: no read-back after a change", PAGE,
     "            readAndApply();\n        });", "        });", ["parity"]),
    ("page: driver's after-read hook skipped", PAGE,
     "        driver.afterRead(app);\n", "", ["parity"]),
    ("Z9X driver: shows the X50 notes", POLICY,
     "EnumSet.of(CecRow.MASTER, CecRow.WAKE,", "EnumSet.of(CecRow.MASTER, CecRow.MASTER_NOTE, CecRow.WAKE,", ["parity"]),
    ("Z9X driver: rows usable with HDMI-CEC off", POLICY,
     "public boolean rowsNeedMaster() { return true; }", "public boolean rowsNeedMaster() { return false; }", ["parity"]),
    ("Z9X driver: no PM51 wake prop sync", POLICY,
     "public void afterRead(Context c) { CecSettings.syncWakeProp(c); }", "public void afterRead(Context c) { }", ["parity"]),
    ("Z9X driver: no storm pause", POLICY,
     "public boolean wakePaused() { return CecPolicy.stormPaused(); }", "public boolean wakePaused() { return false; }",
     ["parity"]),
    ("X50 sequence: CEC off keeps the vendor wake", X + "CecSequences.java",
     "            ok &= wakeUpSet(h, false);\n", "", ["parity", "x50"]),
    ("X50 sequence: CEC off keeps send standby", X + "CecSequences.java",
     "            ok &= f.sendStandbySet(false);\n", "", ["parity", "x50"]),
    ("X50 sequence: wake on does not turn HDMI-CEC on", X + "CecSequences.java",
     "        if (on) ok &= f.cecSet(true);\n", "", ["parity", "x50"]),
    ("X50 sequence: wake sets the framework before the vendor", X + "CecSequences.java",
     "        boolean ok = wakeUpSet(h, on);\n        ok &= f.sendStandbySet(on);",
     "        boolean ok = f.sendStandbySet(on);\n        ok &= wakeUpSet(h, on);", ["parity", "x50"]),
    ("X50 sequence: wake without wake-on-play", X + "CecSequences.java",
     "        ok &= f.wakeOnPlaySet(on);\n        if (on)", "        if (on)", ["parity", "x50"]),
    ("X50 sequence: 52's boolean taken as the result", X + "CecSequences.java",
     "            h.cecWakeUpSet(on);\n            return true;", "            return h.cecWakeUpSet(on);", ["x50"]),
    ("X50 sequence: a failed 53 reads off", X + "CecSequences.java",
     "        } catch (Exception e) {\n            return null;", "        } catch (Exception e) {\n            return false;",
     ["parity", "x50"]),
    ("X50 sequence: a failing step stops the rest", X + "CecSequences.java",
     "        ok &= f.sendStandbySet(on);\n        ok &= f.wakeOnPlaySet(on);",
     "        ok = ok && f.sendStandbySet(on);\n        ok = ok && f.wakeOnPlaySet(on);", ["parity", "x50"]),
    ("X50 driver: no stock tip under the wake row", X + "X50Cec.java",
     "CecRow.WAKE, CecRow.WAKE_NOTE,", "CecRow.WAKE,", ["parity", "x50"]),
    ("X50 driver: rows grey with HDMI-CEC off", X + "X50Cec.java",
     "public boolean rowsNeedMaster() { return false; }", "public boolean rowsNeedMaster() { return true; }",
     ["parity", "x50"]),
    ("X50 driver: wake read from the framework, not TV 53", X + "X50Cec.java",
     "return CecSequences.wakeRead(hal);", "return CecSettings.wakeOn(c);", ["parity", "x50"]),
    ("X50 driver: send standby and wake-on-play swapped", X + "X50Cec.java",
     "public boolean sendStandbySet(boolean on) { return CecSettings.setSendStandby(c, on); }",
     "public boolean sendStandbySet(boolean on) { return CecSettings.setWake(c, on); }", ["parity", "x50"]),
    ("X50 pages: no HDMI-CEC page", X + "X50MenuPages.java",
     "            case SETTINGS_CEC: return cec(menu);\n", "", ["x50"]),
    ("X50 features: settings.cec not opened by the app", X + "X50Features.java",
     "            FeatureId.SETTINGS_HDMI, FeatureId.SETTINGS_CEC, FeatureId.KEYSTONE_ROTATION, FeatureId.ZOOM_MEMORY};",
     "            FeatureId.SETTINGS_HDMI, FeatureId.KEYSTONE_ROTATION, FeatureId.ZOOM_MEMORY};", ["x50"]),
    ("X50 features: no settings.cec", X + "X50Features.java",
     "FeatureId.SETTINGS_TEMPERATURE, FeatureId.SETTINGS_CEC, FeatureId.KEYSTONE_ROTATION,\n            FeatureId.FOCUS_GUIDE",
     "FeatureId.SETTINGS_TEMPERATURE, FeatureId.KEYSTONE_ROTATION,\n            FeatureId.FOCUS_GUIDE", ["x50"]),
    ("Gmpf: 52 allowed with any value", X + "GmpfCall.java",
     '"setCecWakeUp", "0..1"),', '"setCecWakeUp", "-"),', ["x50"]),
    ("allow list: no 53 row", "devices/g0111/research/allowed_x50projector.tsv",
     "GmTvManager\t53\tgetCecWakeUpState\tboolean()\targs:-\t", "#GmTvManager\t53\tgetCecWakeUpState\tboolean()\targs:-\t",
     ["x50"]),
    ("RS30 profile: no settings.cec", "devices/g0155/menu.json",
     '    "settings.cec": {},\n', "", ["x50"]),
    ("X50 recipe: no lumen-cec", "devices/g0100/app/X50Projector.layers",
     "module    lumen-cec\n", "", ["x50"]),
    ("ru: wake note missing", "apps/core/lumen-cec/res/values-ru/lumen_strings_cec.xml",
     '    <string name="lumen_cec_wake_note">', '    <string name="lumen_cec_wake_note_x">', ["parity"]),
    ("ru: master note in English", "apps/core/lumen-cec/res/values-ru/lumen_strings_cec.xml",
     "Когда HDMI-CEC выключен, звук через HDMI ARC недоступен.",
     "With HDMI-CEC off, sound over HDMI ARC is not available.", ["parity"]),
    # ---- the stock CEC defaults (cec-standby-stock)
    ("page: a change is not marked as the user's", PAGE,
     "            if (ok) CecSettings.setUserChose(app, r);\n            else ", "            if (!ok) ", ["parity", "defaults"]),
    ("page: the master switch marks the wake row", PAGE,
     "change(CecRow.MASTER, () -> driver.setMaster(app, w))", "change(CecRow.WAKE, () -> driver.setMaster(app, w))",
     ["parity", "defaults"]),
    ("page: a failed change marked as the user's", PAGE,
     "            if (ok) CecSettings.setUserChose(app, r);", "            CecSettings.setUserChose(app, r);", ["parity"]),
    ("defaults: the user's choice ignored", DEF,
     "            if (s.row != null && u.userChose(s.row)) {", "            if (false) {", ["defaults"]),
    ("defaults: a held switch-off not counted as on", DEF,
     "            if ((now || held) == stock) {", "            if (now == stock) {", ["defaults"]),
    ("defaults: the pending restore kept", DEF,
     "            if (held) u.dropHold(s);\n", "", ["defaults"]),
    ("defaults: unknown counts as done", DEF,
     'again at the next start");\n                done = false;\n                continue;\n            }\n            boolean held',
     'again at the next start");\n                continue;\n            }\n            boolean held', ["defaults"]),
    ("defaults: a refused write counts as done", DEF,
     'log.add(s.key + ": refused, again at the next start");\n                done = false;',
     'log.add(s.key + ": refused, again at the next start");', ["defaults"]),
    ("defaults: the default wins over the device line", DEF,
     "        return v != null ? v : defaults.get(s);", "        return defaults.get(s);", ["defaults"]),
    ("defaults: a reset of any setting accepted", DEF,
     '                        if (!s.writable()) throw new IllegalArgumentException(s.key + " is no switch of the app");\n',
     "", ["defaults"]),
    ("defaults: a missing default accepted", DEF,
     '            if (!defaults.containsKey(s)) throw new IllegalArgumentException("no default for " + s.key);',
     "            if (false) throw new IllegalArgumentException();", ["defaults"]),
    ("setting: the wake switch on the send standby row", SET,
     'TV_WAKE_ON_ONE_TOUCH_PLAY("tv_wake_on_one_touch_play", CecRow.WAKE),',
     'TV_WAKE_ON_ONE_TOUCH_PLAY("tv_wake_on_one_touch_play", CecRow.SEND_STANDBY),', ["defaults"]),
    ("glue: done marked before the step completes", GLUE,
     "            if (done) p.edit().putInt(K_DONE, CecDefaults.VERSION).commit();",
     "            p.edit().putInt(K_DONE, CecDefaults.VERSION).commit();", ["defaults"]),
    ("glue: the held wake not dropped", GLUE,
     "            if (s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY) CecSettings.setRestoreOtpPending(c, false);",
     "            if (s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY) CecSettings.restoreOtpPending(c);", ["defaults"]),
    ("Z9X policy: no one-time step at the start", Z9X_POLICY,
     "            CecStockDefaults.applyOnce(sApp);\n", "", ["defaults"]),
    ("X50 app: no one-time step", X + "App.java",
     'new Thread(() -> org.lumen.projector.cec.CecStockDefaults.applyOnce(this), "x50-cec-defaults").start();',
     "", ["defaults"]),
    ("table: send standby on again", TABLE,
     "default  tv_send_standby_on_sleep                  0", "default  tv_send_standby_on_sleep                  1",
     ["defaults", "tool"]),
    ("table: no HB reset", TABLE, "reset    goat_elegant    tv_wake_on_one_touch_play\n", "", ["defaults"]),
    ("table: Play 6 wake as the others", TABLE, "device   dragon_healthy  tv_wake_on_one_touch_play  1\n", "",
     ["defaults", "tool"]),
    ("tool: a stock column never compared", TOOL,
     "                elif got != want:", "                elif False:", ["tool"]),
    ("tool: the RRO states only the default value", TOOL,
     "            out.append('    <bool name=\"%s\">%s</bool>' % (res, \"true\" if v == vals[s] else \"false\"))",
     "            if v == vals[s]: out.append('    <bool name=\"%s\">true</bool>' % res)", ["tool"]),
    ("tool: own copies outside the RRO file allowed", TOOL,
     "            if hit:\n                errs.append", "            if False:\n                errs.append", ["tool"]),
    ("tool: a recipe without the asset passes", TOOL,
     '        if not re.search(r"^file\\s+%s\\s+%s\\s*$" % (re.escape(TABLE), re.escape(ASSET)), t, re.M):',
     "        if False:", ["tool"]),
    ("tool: a device without a dump passes", TOOL,
     '            errs.append("%s: no stock dump of %s" % (STOCK, c))', "            pass", ["tool"]),
    ("tool: an image without its RRO passes", TOOL,
     "        if len(fw) != 1:", "        if len(fw) > 1:", ["tool"]),
    ("tool: Android 11 set lines never compared", TOOL,
     "                if vals[s] != v:", "                if False:", ["tool"]),
]


def main():
    want = sys.argv[1:]
    killed, survived = 0, []
    for name, path, old, new, suites in MUTANTS:
        if want and not any(w in name for w in want):
            continue
        f = os.path.join(ROOT, path)
        with open(f, encoding="utf-8") as fh:
            orig = fh.read()
        if orig.count(old) != 1:
            print("BROKEN %s: the line is not in %s once" % (name, path))
            survived.append(name)
            continue
        try:
            with open(f, "w", encoding="utf-8") as fh:
                fh.write(orig.replace(old, new))
            failed = []
            for s in suites:
                r = subprocess.run(SUITES[s], cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if r.returncode != 0:
                    failed.append(s)
        finally:
            with open(f, "w", encoding="utf-8") as fh:
                fh.write(orig)
        if failed == suites:
            killed += 1
            print("killed   %s (%s)" % (name, ", ".join(failed)))
        else:
            survived.append(name)
            print("SURVIVED %s (failed: %s of %s)" % (name, ", ".join(failed) or "none", ", ".join(suites)))
    print("cec mutants: %d killed, %d survived" % (killed, len(survived)))
    sys.exit(1 if survived else 0)


if __name__ == "__main__":
    main()
