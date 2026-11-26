#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The signers sign a base APK with its base_patch fix: HB, RS30 and Titan (tools/devices/sign_device.py), the X50
family (devices/g0111/image/sign_x50_base.py) and the Play 6 (tools/play6/sign_play6.py)."""
import hashlib
import inspect
import os
import sys
import tempfile
import zipfile

T = os.path.dirname(os.path.abspath(__file__))
G = os.path.dirname(os.path.dirname(os.path.dirname(T)))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(G, "tools", "devices"))
sys.path.insert(0, os.path.join(G, "devices", "g0111", "image"))
sys.path.insert(0, os.path.join(G, "tools", "play6"))
sys.path.insert(0, T)
import sign_device as sd  # noqa: E402
import sign_play6  # noqa: E402
import sign_x50_base  # noqa: E402
from base_patch_test import make_apk  # noqa: E402

bp = sd.base_patch
FAILS = []


def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        FAILS.append(name)


def main():
    w = tempfile.mkdtemp(prefix="base-patch-hook-")
    base_dex, new_dex = b"dex\n035\0" + b"A" * 999, b"dex\n035\0" + b"C" * 999
    apk = os.path.join(w, "Tv.apk")
    make_apk(apk, base_dex)
    path = "system/system_ext/priv-app/TvSettingsTwoPanel/TvSettingsTwoPanel.apk"
    check("stage_base signs what resign_input returns",
          "sign_tar.resign_apk, resign_input(r, work)" in inspect.getsource(sd.stage_base))
    check("the X50 family base signs what resign_input returns",
          'sign_tar.resign_apk, sign_device.resign_input({"path": n, "file": p}, work), dst'
          in inspect.getsource(sign_x50_base.stage_base))
    check("the Play 6 base signs the base_patch copy",
          'sign_tar.resign_apk, base_patch.patched_apk(r["path"], r["file"], work) or r["file"],'
          in inspect.getsource(sign_play6.stage_base))
    check("an APK without a fix is signed as it is",
          sd.resign_input({"path": "system/app/Other/Other.apk", "file": apk}, w) == apk)
    try:
        sd.resign_input({"path": path, "file": apk}, w)
        check("a TvSettings with another dex stops the signer", False)
    except SystemExit:
        check("a TvSettings with another dex stops the signer", True)
    real_rows, real_dir = bp.rows, bp.DEX_DIR
    bp.DEX_DIR = os.path.join(w, "dex")
    os.makedirs(os.path.join(bp.DEX_DIR, "fakefix"))
    open(os.path.join(bp.DEX_DIR, "fakefix", "classes.dex"), "wb").write(new_dex)
    bp.rows = lambda: [{"path": path, "id": "fakefix", "cls": "La/B;", "base": hashlib.sha256(base_dex).hexdigest(),
                        "out": hashlib.sha256(new_dex).hexdigest()}]
    try:
        got = sd.resign_input({"path": path, "file": apk}, w)
        with zipfile.ZipFile(got) as z:
            check("the TvSettings row is signed from the patched copy", got != apk and z.read("classes.dex") == new_dex)
    finally:
        bp.rows, bp.DEX_DIR = real_rows, real_dir
    print("signer_hook_test: %d failed" % len(FAILS))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
