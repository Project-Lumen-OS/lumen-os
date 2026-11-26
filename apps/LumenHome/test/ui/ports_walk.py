#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Home's Inputs chips follow the one HDMI port state live (emulator only, never a projector): the fake port app of
apps/core/lumen-hdmiports/test/ui/build_fake.sh answers as org.lumen.tvinput's hdmi_state provider and logs every
read. While Home is in front, a port plugged, unplugged or renamed by CEC (the HDMI app's change announcement) makes
Home read the ports again within seconds; while another app is in front Home does not watch; back on Home it reads
again. The emulator has no TV inputs, so the chips themselves are not drawn: the walk proves the reload path, the chip
state rule is PortChipTest.

    python3 ports_walk.py SERIAL   (adb from $ADB; Home and the fake port app installed)"""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
SERIAL = sys.argv[1]
FAKE = "org.lumen.hdmiports.fake"
FAILS = []


def sh(cmd):
    return subprocess.run([ADB, "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=40).stdout.replace("\r", "")


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or not detail else "  (" + str(detail)[:300] + ")"))
    if not ok:
        FAILS.append(name)
    return ok


UID = re.search(r"uid:(\d+)", sh("pm list packages -U org.lumen.home") or "")
UID = UID.group(1) if UID else "?"


def reads():
    log = sh("logcat -d -s FakePorts:I")
    return len(re.findall(r"call hdmi_state null uid=%s\b" % UID, log))


def wait_read(before, secs=6.0):
    end = time.time() + secs
    while time.time() < end:
        n = reads()
        if n > before:
            return n
        time.sleep(0.5)
    return reads()


def fake(port, state=None, title=None):
    a = "am broadcast -n %s/.FakePortsReceiver --ei port %d" % (FAKE, port)
    if state:
        a += " --es state " + state
    if title is not None:
        a += " --es title '%s'" % title
    sh(a)


def main():
    sh("am force-stop " + FAKE)
    sh("am force-stop org.lumen.home")
    sh("logcat -c")
    sh("am start -n org.lumen.home/.HomeActivity")
    time.sleep(6)
    n = reads()
    check("Home reads the one port state when it starts (uid %s)" % UID, n > 0, n)
    for what, args in (("plug HDMI 3", (3, "on")), ("unplug HDMI 1", (1, "disconnected")),
                       ("CEC name on HDMI 2", (2, None, "Apple TV")), ("CEC device asleep", (2, "asleep"))):
        time.sleep(2.5)
        b = reads()
        fake(*args)
        a = wait_read(b)
        check(what + ": Home reads the ports again while in front", a > b, "%d -> %d" % (b, a))
    sh("am start -n %s/.FakePortsFront" % FAKE)
    time.sleep(4)
    check("another app is in front", FAKE + "/" in sh("dumpsys window | grep mCurrentFocus"), sh("dumpsys window | grep mCurrentFocus"))
    b = reads()
    fake(3, "disconnected")
    time.sleep(4)
    a = reads()
    check("another app in front: Home does not watch the ports", a == b, "%d -> %d" % (b, a))
    sh("am start -n org.lumen.home/.HomeActivity")
    a2 = wait_read(a, 8)
    check("back on Home: it reads the ports again", a2 > a, "%d -> %d" % (a, a2))
    print("ports_walk: %s" % ("PASS" if not FAILS else "FAIL %d" % len(FAILS)))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
