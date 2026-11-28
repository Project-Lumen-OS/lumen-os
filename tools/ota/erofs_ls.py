#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""erofs_ls.py: the regular files of an erofs image by dump.erofs --ls (read only), for check_release_assets.py and
delta_proof.py."""
import re
import subprocess


def erofs_files(img, dump):
    """Yield (path, nid) of every regular file of an erofs image (dump.erofs --ls, read only; works on a
    case-insensitive Mac file system, unlike fsck.erofs --extract)."""
    todo = [("", None)]                                # root: --path=/
    while todo:
        path, nid = todo.pop()
        args = [dump, "--ls"] + (["--nid=%d" % nid] if nid is not None else ["--path=/"]) + [img]
        r = subprocess.run(args, capture_output=True, text=True, errors="surrogateescape")
        if r.returncode:
            raise RuntimeError(f"dump.erofs --ls {path or '/'}: {r.stderr[-200:]}")
        rows = r.stdout.split("NID TYPE  FILENAME", 1)
        if len(rows) != 2:
            raise RuntimeError(f"dump.erofs --ls {path or '/'}: unexpected output")
        for line in rows[1].splitlines():
            m = re.match(r"^\s*(\d+)\s+(\d+)\s+(.*)$", line)
            if not m or m.group(3) in (".", ".."):
                continue
            n, t, name = int(m.group(1)), int(m.group(2)), m.group(3)
            child = path + "/" + name
            if t == 2:
                todo.append((child, n))
            elif t == 1:
                yield child.lstrip("/"), n
