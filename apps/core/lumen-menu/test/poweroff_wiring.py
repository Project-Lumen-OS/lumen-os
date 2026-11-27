#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""One power off for every family: the power menu's Power off of each family reaches the shared Halt.powerOff (Android's
shutdown, as Settings > Power off), never the system power key (the X50 family's sleep, report LR-YVHNX4), and the Sleep
items stay sleeps. Source wiring only (the call itself: HaltTest; on an emulator: ui/power_walk.py POWER_OFF=1).
  python3 poweroff_wiring.py GSI_ROOT"""
import os
import sys

G = sys.argv[1]
FAILS = []


def read(p):
    with open(os.path.join(G, p), encoding="utf-8") as f:
        return f.read()


def check(what, ok):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILS.append(what)


V = "apps/LumenProjector/vendor/xgimi/"
P = "apps/LumenProjector/core/src/org/lumen/projector/power/"
X = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
common = read("apps/common/projector/src/org/lumen/projector/power/PowerMenu.java")
check("Z9X HB RS20 Play 6 power menu: the Power off tile is Family.powerOff",
      "guarded(() -> Family.powerOff(ctx)));" in common)
check("Z9X HB RS20 Play 6 power menu: the Sleep tile stays a sleep",
      "guarded(() -> Family.powerSleepWithFade(ctx, \"power menu sleep\")));" in common)
for fam, key in (("hidl-gmpf", "PowerKey"), ("aidl-gmpf", "PowerKey"), ("play6-hidl", "Play6PowerKey")):
    check(fam + ": Family.powerOff is " + key + ".PowerActions.powerOff",
          "public static void powerOff(Context c) { org.lumen.projector.power." + key + ".PowerActions.powerOff(c); }"
          in read(V + fam + "/src/org/lumen/projector/Family.java"))
for key in ("PowerKey", "Play6PowerKey"):
    s = read(P + key + ".java")
    body = s[s.index("private static void halt("):]
    body = body[:body.index("\n    }\n")]
    check(key + ": halt() powers off through Halt.powerOff and restarts through Halt.restart",
          "org.lumen.projector.panel.Halt.powerOff(app, \"power menu\");" in body
          and "org.lumen.projector.panel.Halt.restart(app, \"power menu\");" in body)
    check(key + ": halt() has no own PowerManager call left", ".shutdown(" not in body and ".reboot(" not in body)
menu = read("apps/core/lumen-menu/src/org/lumen/projector/panel/PowerMenu.java")
check("shared round power menu: POWER_OFF is the device's Power.powerOff", "case POWER_OFF:\n                closeThen(power::powerOff);" in menu)
x = read(X + "X50Power.java")
check("X50 family: Power.powerOff is halt(false) = Halt.powerOff",
      "    @Override public void powerOff() {\n        halt(false);" in x and "else Halt.powerOff(app, \"power menu\");" in x)
check("X50 family: only the power menu's Sleep sends the power key (Standby), Power off and Restart never",
      x.count("Standby.press(\"power menu") == 1 and "    @Override public void sleep() {\n        Standby.press(\"power menu sleep\", null);" in x
      and "KEYCODE_POWER" not in x)
check("X50 family: the remote power key's Sleep stays the power key sleep",
      "            case SLEEP:\n                Standby.press(\"remote power key\", null);" in x)
st = read(X + "Standby.java")
check("X50 Standby: the stock power off of a sleep (SleepRule) is Halt.powerOff, the sleep the power key",
      "else Halt.powerOff(sApp, why + \", \" + off);" in st and "sendKeyDownUpSync(KeyEvent.KEYCODE_POWER)" in st)
# the stock menu (power-menu-1127): the items of PowerItem, the 15 s power off in the shared round panel, the power key
# again powers off on every family
rt = read("apps/common/projector/src/org/lumen/projector/ui/RoundTilePanel.java")
check("round panel: no key stops the 15 s power off (stock: UP, DOWN and the keys that keep the focus keep it)",
      "stopCountdown(KeyEvent" not in rt and rt.count("stopCountdown(") == 3)
check("round panel: the focus leaving Power off stops the count (stock onFocusChange)",
      'power.setOnFocusChangeListener((v, has) -> {\n                if (countdown.focusChanged(has)) stopCountdown("the focus left Power off");' in rt)
check("round panel: the count starts once settled, on the focused countdown tile, without a timer",
      "if (power != null && countdown.start(first == power, timerRunning())) {" in rt)
check("round panel: the end powers off only with Power off focused and nothing blocking",
      'String why = !t.hasFocus() ? "the focus left Power off" : blockedReason();' in rt and "        onCountdownEnd();" in rt)
check("round panel: closing the menu stops the count", '        stopCountdown("menu closed");' in rt)
check("round panel: the stock 20 s auto hide", "AUTO_HIDE_MS = 20_000;" in rt and "autoHideMs() { return AUTO_HIDE_MS; }" in rt)
check("Z9X HB RS20 Play 6 menu: the shared item list, Power off the countdown tile",
      "for (PowerItem item : PowerItem.offered(" in common and "@Override protected RoundTile countdownTile() { return powerOffTile; }" in common)
check("Z9X HB RS20 Play 6 menu: the count end and the power key again power off (Family.powerOff)",
      "        dismiss();\n        Family.powerOff(c);" in common and "        m.dismiss();\n        Family.powerOff(m.shownIn);" in common)
check("Z9X HB RS20 Play 6 menu: Turn off screen is the lamp.off row's LampOffHal",
      "LampOffHal.get(ctx).run(r -> { })" in common)
kp = read(P + "PowerKeyPress.java")
check("gmpf and Play 6 power key: a short press with the menu open powers off",
      "PowerKeyPrefs.choice(ctx).shortPress(PowerMenu.isOpen())" in kp
      and 'a == PowerAction.POWER_OFF && !PowerMenu.powerOffNow("power key " + press + " again")' in kp)
check("shared round power menu: the count end and the power key again power off",
      "@Override protected void onCountdownEnd() { closeThen(power::powerOff); }" in menu
      and "        closeThen(power::powerOff);\n        return true;" in menu)
check("X50 family: the press rule knows the open menu, its POWER_OFF is the menu's power off",
      "() -> sMenu != null && sMenu.isShowing());" in x and 'sMenu.powerOffNow("remote power key again")' in x)
h = read("apps/core/lumen-menu/src/org/lumen/projector/panel/Halt.java")
check("Halt: shutdown(false, SHUTDOWN_USER_REQUESTED, false) and reboot(SHUTDOWN_USER_REQUESTED)",
      ".shutdown(false, PowerManager.SHUTDOWN_USER_REQUESTED, false);" in h and ".reboot(PowerManager.SHUTDOWN_USER_REQUESTED);" in h)
print("poweroff_wiring: " + ("all green" if not FAILS else "%d FAIL" % len(FAILS)))
sys.exit(1 if FAILS else 0)
