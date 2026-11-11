#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""x50_fixture.py: fake X50 z9x_blobs files for the host tests (devices/g0111/test/x50_blobs_test.sh,
tools/devices/test/installer_x50_blobs.sh). A thin wrapper of tools/ota/test/blobs_fixture.py (unchanged, a Z9X
gate input) with the X50 partition size, 16 MiB. Never reads or writes a real MediaTek / XGIMI file: every file is
made-up text, and the test allow-list keeps the real one's structure (paths, sources, bind= / from= / label= /
mode= / need= options) with the fake files' hashes.

    x50_fixture.py allow REAL_ALLOW OUT_ALLOW FILES_DIR
        as blobs_fixture.py allow (FILES_DIR/<path> = the fake file of every line, <path>.stock for from=)
    x50_fixture.py tar FILES_DIR ALLOW OUT_IMG [--set ID] [--corrupt PATH] [--drop PATH] [--symlink PATH]
                   [--sparse PATH] [--dotdot NAME] [--big PATH]
        the z9x_blobs partition image the installer makes for the X50 (ustar, MANIFEST + files, padded to 16 MiB);
        the crafted ones of blobs_fixture.py, and --big PATH: that member is 9 MiB (more than x50_blobs.sh takes)
"""
import os
import shutil
import sys
import tempfile

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.dont_write_bytecode = True   # no __pycache__ in tools/ota/test
sys.path.insert(0, os.path.join(G, "tools", "ota", "test"))
import blobs_fixture as bf  # noqa: E402

bf.SIZE = 16 << 20


def main(a):
    if a[:1] == ["allow"] and len(a) == 4:
        bf.cmd_allow(a[1], a[2], a[3], False)
    elif a[:1] == ["tar"] and len(a) >= 4:
        opt = dict(zip(a[4::2], a[5::2]))
        files = a[1]
        tmp = None
        if "--big" in opt:
            tmp = tempfile.mkdtemp()
            shutil.copytree(a[1], os.path.join(tmp, "f"))
            files = os.path.join(tmp, "f")
            with open(os.path.join(files, opt["--big"]), "wb") as f:
                f.write(b"b" * (9 << 20))
        try:
            bf.cmd_tar(files, a[2], a[3], opt.get("--set"), opt.get("--corrupt"), opt.get("--drop"), opt.get("--symlink"),
                       opt.get("--sparse"), opt.get("--dotdot"))
        finally:
            if tmp:
                shutil.rmtree(tmp)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
