#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the one power off path outside lumen-menu (the module mutator copies only the module): each mutant is
written into a scratch copy of the files poweroff_wiring.py reads, which must then go red. Exit 1 when one survives.
  python3 apps/core/lumen-menu/test/poweroff_mutate.py"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))
P = "apps/LumenProjector/core/src/org/lumen/projector/power/"
V = "apps/LumenProjector/vendor/xgimi/"
X = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
FILES = [P + "PowerKey.java", P + "Play6PowerKey.java", X + "X50Power.java", X + "Standby.java",
         "apps/common/projector/src/org/lumen/projector/power/PowerMenu.java",
         "apps/core/lumen-menu/src/org/lumen/projector/panel/PowerMenu.java",
         "apps/core/lumen-menu/src/org/lumen/projector/panel/Halt.java"] + \
        [V + f + "/src/org/lumen/projector/Family.java" for f in ("hidl-gmpf", "aidl-gmpf", "play6-hidl")]
HALT = 'org.lumen.projector.panel.Halt.powerOff(app, "power menu");'
MUTANTS = [
    ("Z9X HB: halt powers off with its own PowerManager", P + "PowerKey.java", HALT,
     "app.getSystemService(android.os.PowerManager.class).shutdown(false, null, false);"),
    ("Z9X HB: Power off restarts", P + "PowerKey.java", HALT, 'org.lumen.projector.panel.Halt.restart(app, "power menu");'),
    ("Play 6: halt powers off with its own PowerManager", P + "Play6PowerKey.java", HALT,
     "app.getSystemService(android.os.PowerManager.class).shutdown(false, null, false);"),
    ("Play 6: Power off restarts", P + "Play6PowerKey.java", HALT, 'org.lumen.projector.panel.Halt.restart(app, "power menu");'),
    ("Z9X: Family.powerOff sleeps", V + "hidl-gmpf/src/org/lumen/projector/Family.java",
     "PowerKey.PowerActions.powerOff(c); }", "PowerKey.PowerActions.sleepWithFade(c, \"off\"); }"),
    ("HB: Family.powerOff restarts", V + "aidl-gmpf/src/org/lumen/projector/Family.java",
     "PowerKey.PowerActions.powerOff(c); }", "PowerKey.PowerActions.restart(c); }"),
    ("Play 6: Family.powerOff sleeps", V + "play6-hidl/src/org/lumen/projector/Family.java",
     "Play6PowerKey.PowerActions.powerOff(c); }", "Play6PowerKey.PowerActions.sleepWithFade(c, \"off\"); }"),
    ("Z9X menu: the Power off tile sleeps", "apps/common/projector/src/org/lumen/projector/power/PowerMenu.java",
     "guarded(() -> Family.powerOff(ctx)));", "guarded(() -> Family.powerSleepWithFade(ctx, \"off\")));"),
    ("Z9X menu: the Sleep tile powers off", "apps/common/projector/src/org/lumen/projector/power/PowerMenu.java",
     "guarded(() -> Family.powerSleepWithFade(ctx, \"power menu sleep\")));", "guarded(() -> Family.powerOff(ctx)));"),
    ("round menu: POWER_OFF restarts", "apps/core/lumen-menu/src/org/lumen/projector/panel/PowerMenu.java",
     "case POWER_OFF:\n                closeThen(power::powerOff);", "case POWER_OFF:\n                closeThen(power::restart);"),
    ("X50: Power off is the power key again", X + "X50Power.java", "    @Override public void powerOff() {\n        halt(false);",
     "    @Override public void powerOff() {\n        Standby.press(\"power menu: power off\", null);"),
    ("X50: remote Sleep powers off", X + "X50Power.java", "            case SLEEP:\n                Standby.press(\"remote power key\", null);",
     "            case SLEEP:\n                halt(false);"),
    ("X50 Standby: the SleepRule power off sends the key", X + "Standby.java", "else Halt.powerOff(sApp, why + \", \" + off);",
     "else new Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_POWER);"),
]


def main():
    alive = 0
    for name, f, old, new in MUTANTS:
        t = tempfile.mkdtemp(prefix="poweroff_mut.")
        try:
            for p in FILES:
                os.makedirs(os.path.dirname(os.path.join(t, p)), exist_ok=True)
                shutil.copy(os.path.join(G, p), os.path.join(t, p))
            s = open(os.path.join(t, f), encoding="utf-8").read()
            if s.count(old) != 1:
                print("BROKEN " + name)
                alive += 1
                continue
            open(os.path.join(t, f), "w", encoding="utf-8").write(s.replace(old, new))
            r = subprocess.run([sys.executable, os.path.join(HERE, "poweroff_wiring.py"), t], capture_output=True, text=True)
            print(("killed " if r.returncode else "ALIVE  ") + name)
            alive += r.returncode == 0
        finally:
            shutil.rmtree(t)
    print("poweroff_mutate: %d/%d killed" % (len(MUTANTS) - alive, len(MUTANTS)))
    return 1 if alive else 0


if __name__ == "__main__":
    sys.exit(main())
