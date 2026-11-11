#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""image_diff.py: compare two Play 6 system images entry by entry, metadata first (the proof that a new build host
composes like the old one: every path both images share keeps its type, mode, owner, SELinux label, file
capabilities and link target), then content.

  sudo python3 -B tools/play6/image_diff.py OLD_ROOT NEW_ROOT [--expect FILE]

OLD_ROOT, NEW_ROOT: the two images mounted read-only (mount -o ro,loop,noload IMG DIR), as root so every file and
xattr is readable. Prints, per class: META (a shared path whose type, mode, uid, gid, security.selinux,
security.capability or link target differ), CONTENT (same metadata, other bytes), ADDED, REMOVED, and the labels
of the added files. --expect FILE: lines 'CONTENT|ADDED|REMOVED<TAB>regex'; any change no line explains is listed
as UNEXPECTED. Exit 1 on any META row or UNEXPECTED change, else 0.
"""
import hashlib
import os
import re
import stat
import sys


def xattr(p, name):
    try:
        return os.getxattr(p, name, follow_symlinks=False)
    except OSError:
        return b""


def walk(root):
    out = {}
    for d, dirs, files in os.walk(root):
        for n in dirs + files:
            p = os.path.join(d, n)
            rel = os.path.relpath(p, root)
            st = os.lstat(p)
            kind = "l" if stat.S_ISLNK(st.st_mode) else "d" if stat.S_ISDIR(st.st_mode) else "f"
            meta = (kind, oct(st.st_mode & 0o7777), st.st_uid, st.st_gid,
                    xattr(p, "security.selinux").rstrip(b"\0").decode("utf-8", "replace"),
                    xattr(p, "security.capability").hex(),
                    os.readlink(p) if kind == "l" else "")
            body = ""
            if kind == "f":
                h = hashlib.sha256()
                with open(p, "rb") as f:
                    for b in iter(lambda: f.read(1 << 20), b""):
                        h.update(b)
                body = h.hexdigest()
            out[rel] = (meta, body)
    return out


def main():
    a = sys.argv[1:]
    expect = []
    if "--expect" in a:
        i = a.index("--expect")
        for line in open(a[i + 1]):
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                cls, rx = line.split("\t", 1)
                expect.append((cls, re.compile(rx)))
        del a[i:i + 2]
    if len(a) != 2:
        sys.exit(__doc__.split("\n\n")[1])
    old, new = walk(a[0]), walk(a[1])
    rows = []
    for k in sorted(set(old) | set(new)):
        if k not in new:
            rows.append(("REMOVED", k, ""))
        elif k not in old:
            m = new[k][0]
            rows.append(("ADDED", k, "%s %s %d:%d %s" % (m[0], m[1], m[2], m[3], m[4])))
        elif old[k][0] != new[k][0]:
            rows.append(("META", k, "%s -> %s" % (old[k][0], new[k][0])))
        elif old[k][1] != new[k][1]:
            rows.append(("CONTENT", k, ""))
    bad = 0
    for cls, k, info in rows:
        why = next((rx.pattern for c, rx in expect if c == cls and rx.search(k)), None) if expect else "-"
        flag = ""
        if cls == "META" or why is None:
            flag = "  UNEXPECTED" if cls != "META" else "  RED"
            bad += 1
        print("%-8s %s %s%s" % (cls, k, info, flag))
    counts = {}
    for cls, _, _ in rows:
        counts[cls] = counts.get(cls, 0) + 1
    print("\nshared paths: %d, %s" % (len(set(old) & set(new)), ", ".join("%s %d" % kv for kv in sorted(counts.items()))))
    print("image_diff: %s" % ("OK" if not bad else "%d row(s) need a look (META or UNEXPECTED)" % bad))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
