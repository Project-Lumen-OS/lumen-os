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
    x50_fixture.py stock FILES_DIR ALLOW OUT_DIR ROOT [--drop PATH] [--corrupt PATH] [--symlink PATH] [--no-apex]
        a fake stock slot for the DSU-trial path (tools/devices/image/blobs_stock_dsu.sh): OUT_DIR/system.tar and
        OUT_DIR/system_ext.tar stand for the stock partitions (the test's mount stub extracts them), each file at
        its stock source path (system-as-root: system/...), the stock VNDK APEX system/apex/
        com.android.vndk.current.apex as a zip whose stored apex_payload.img is a tar of the /apex/... files, and
        ROOT/vendor/... the stock vendor files (the from= source). PATH is a line's path inside the blobs tar:
        --drop leaves it out, --corrupt changes one byte of it, --symlink makes it a symlink to a good copy
"""
import io
import os
import shutil
import sys
import tarfile
import tempfile
import zipfile

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.dont_write_bytecode = True   # no __pycache__ in tools/ota/test
sys.path.insert(0, os.path.join(G, "tools", "ota", "test"))
import blobs_fixture as bf  # noqa: E402

bf.SIZE = 16 << 20


def _add(tf, name, data=None, link=None):
    ti = tarfile.TarInfo(name)
    ti.mtime, ti.uname, ti.gname = 0, "root", "root"
    if link is not None:
        ti.type, ti.linkname, ti.mode = tarfile.SYMTYPE, link, 0o777
        tf.addfile(ti)
    else:
        ti.size, ti.mode = len(data), 0o644
        tf.addfile(ti, io.BytesIO(data))


def cmd_stock(files, allow, out, root, opt):
    os.makedirs(out, exist_ok=True)
    parts = {"system": [], "system_ext": [], "payload": []}   # (member, data, symlink?)
    for _, f in bf.parse(allow):
        if not f:
            continue
        h, rel, src, opts = f
        if opt.get("--drop") == rel:
            continue
        data = open(os.path.join(files, rel + (".stock" if "from" in opts else "")), "rb").read()
        if opt.get("--corrupt") == rel:
            data = bytes([data[0] ^ 1]) + data[1:]
        if src.startswith("/system_ext/"):
            where, member = "system_ext", src[len("/system_ext/"):]
        elif src.startswith("/system/"):
            where, member = "system", src[1:]
        elif src.startswith("/apex/"):
            where, member = "payload", src.split("/", 3)[3]
        elif src.startswith("/vendor/"):
            p = os.path.join(root, src[1:])
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(data)
            continue
        else:
            sys.exit("no stock place for " + src)
        parts[where].append((member, data, opt.get("--symlink") == rel))
    payload = io.BytesIO()
    with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as tf:
        for m, d, _ in parts["payload"]:
            _add(tf, m, d)
    if parts["payload"] and "--no-apex" not in opt:
        z = io.BytesIO()
        with zipfile.ZipFile(z, "w", zipfile.ZIP_STORED) as zf:
            zf.writestr("apex_payload.img", payload.getvalue())
            zf.writestr("apex_manifest.pb", b"com.android.vndk.v34")
        parts["system"].append(("system/apex/com.android.vndk.current.apex", z.getvalue(), False))
    for where in ("system", "system_ext"):
        with tarfile.open(os.path.join(out, where + ".tar"), "w", format=tarfile.USTAR_FORMAT) as tf:
            for m, d, sym in parts[where]:
                if sym:
                    _add(tf, m + ".real", d)
                    _add(tf, m, link=os.path.basename(m) + ".real")
                else:
                    _add(tf, m, d)


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
    elif a[:1] == ["stock"] and len(a) >= 5:
        opt = {}
        rest = a[5:]
        while rest:
            if rest[0] == "--no-apex":
                opt[rest.pop(0)] = True
            else:
                opt[rest[0]] = rest[1]
                rest = rest[2:]
        cmd_stock(a[1], a[2], a[3], a[4], opt)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
