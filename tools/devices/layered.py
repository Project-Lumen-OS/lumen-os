#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""layered.py: the family files of the device descriptors (devices/README.md, "Family files").

A model's devices/<codename>/build.conf or device.conf may name, on its first key line, the family file it builds on:

    include=devices/_family/x50/x50.build.conf

A family file has the same key=value format and may itself include a wider family file. The model's descriptor is
then: every key of the family files (the widest first, in their file order), then the model's own keys. A model key
that the family gives too replaces the family value in its place; the same value again is an error (a pointless copy:
drop the line). In a family file "{codename}" stands for the codename of the model that includes it (the codename=
key of the model file), so a path such as devices/{codename}/certs/platform.x509.pem is one family line.

The parsers of both formats (tools/build/recipe.py parse_conf, tools/devices/devices.py parse) read a descriptor only
through lines() and put(), and every other reader uses them or this command line:

    python3 layered.py FILE            the effective key=value lines of FILE, family keys resolved (exit 1 on an error)
    python3 layered.py --files FILE    FILE and every family file it includes, one path per line (what a build stages)
    python3 layered.py --find FILE NAME   the data file NAME of FILE's model: its own, else its nearest family's
    python3 layered.py --members ROOT FAMILY   the models of devices/_family/FAMILY in the tree ROOT (test model lists)

An include is a path relative to the tree (gsi/); in a flat folder of staged inputs (<remote_dir>/in on the build host)
it is the file of the same name next to FILE.
"""
import os
import sys

INCLUDE = "include"
TEMPLATE = "{codename}"


class LayerError(Exception):
    pass


def _root_of(path):
    """The tree of a descriptor under <tree>/devices/, else None (a flat folder of staged inputs)."""
    d = os.path.dirname(os.path.abspath(path))
    while d != os.path.dirname(d):
        if os.path.basename(d) == "devices":
            return os.path.dirname(d)
        d = os.path.dirname(d)
    return None


def locate(path, inc):
    """The file an include= line of PATH names: <tree>/<inc>, else <dir of PATH>/<basename of inc> (staged inputs)."""
    root = _root_of(path)
    if root and os.path.isfile(os.path.join(root, inc)):
        return os.path.join(root, inc)
    flat = os.path.join(os.path.dirname(os.path.abspath(path)), os.path.basename(inc))
    if os.path.isfile(flat):
        return flat
    raise LayerError("%s: include %s not found" % (path, inc))


def _raw(path):
    with open(path, encoding="utf-8") as f:
        return [(path, n, line.rstrip("\n")) for n, line in enumerate(f, 1)]


def _codename(rows):
    for _, _, line in rows:
        if line.startswith("codename="):
            return line.split("=", 1)[1].strip()
    return None


def lines(path, _seen=None, _codename_of=None):
    """-> [(file, line number, text)] of PATH with its include= line replaced by the included file's lines (its own
    includes first), "{codename}" in family lines replaced by the codename of the model file. LayerError on a missing
    include, a cycle, an include that is not the first key line or a family file that gives codename=."""
    seen = list(_seen or [])
    if os.path.abspath(path) in seen:
        raise LayerError("include cycle: %s" % " -> ".join(seen + [os.path.abspath(path)]))
    seen.append(os.path.abspath(path))
    rows = _raw(path)
    code = _codename_of or _codename(rows)
    out, keys = [], 0
    for src, n, line in rows:
        s = line.strip()
        if not s or line.lstrip().startswith("#"):
            out.append((src, n, line))
            continue
        keys += 1
        if line.startswith(INCLUDE + "="):
            if keys != 1:
                raise LayerError("%s:%d: include= must be the first key line" % (src, n))
            for inc in line.split("=", 1)[1].split():
                fam = lines(locate(path, inc), seen, code)
                if any(t.startswith("codename=") for _, _, t in fam):
                    raise LayerError("%s: a family file gives no codename=" % inc)
                out += fam
            continue
        if _seen is not None:
            if TEMPLATE in line and not code:
                raise LayerError("%s:%d: %s outside a model's include" % (src, n, TEMPLATE))
            line = line.replace(TEMPLATE, code or TEMPLATE)
        out.append((src, n, line))
    return out


def files(path, _seen=None):
    """PATH and every file it includes (the widest family last)."""
    out = [path]
    for src, n, line in _raw(path):
        if line.startswith(INCLUDE + "="):
            for inc in line.split("=", 1)[1].split():
                out += files(locate(path, inc))
    return out


def dirs(path):
    """The folder of a descriptor and of each family file it includes, nearest first: where a model's data and app
    recipes are looked up (its own file wins, then its family's)."""
    out = []
    for p in files(path):
        d = os.path.dirname(os.path.abspath(p))
        if d not in out:
            out.append(d)
    return out


def find(path, name):
    """NAME (a relative path) in the folder of the descriptor PATH, else in the nearest family folder that has it:
    a model's data file (signal.tsv, audio/chip_volume.tsv ...) that its family shares. None when no folder has it."""
    for d in dirs(path):
        if os.path.isfile(os.path.join(d, name)):
            return os.path.join(d, name)
    return None


def put(out, origin, key, value, src, n, errs):
    """Store KEY=VALUE read at SRC:N into OUT ({key: value}, file order kept); ORIGIN maps each key to the file that
    gave it. A key twice in one file is an error, a later file replaces an earlier file's value in place, unless the
    value is the same (an error: the line repeats its family file)."""
    if key in out:
        if origin[key] == src:
            errs.append("%s:%d: %s given twice" % (src, n, key))
            return
        if out[key] == value:
            errs.append("%s:%d: %s repeats the value of %s (drop the line)" % (src, n, key, origin[key]))
            return
    out[key] = value
    origin[key] = src


def effective(path):
    """{key: value} of a descriptor with its family files (no key checks: the format's parser does those)."""
    out, origin, errs = {}, {}, []
    for src, n, line in lines(path):
        if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        put(out, origin, k.strip(), v.strip(), src, n, errs)
    if errs:
        raise LayerError("; ".join(errs))
    return out


def text(path):
    """The effective key=value lines of a descriptor as one text (for readers that search the file's text)."""
    return "".join("%s=%s\n" % kv for kv in effective(path).items())


def members(root, family):
    """The codenames whose device.conf includes devices/_family/FAMILY (directly or through a narrower family), sorted:
    the model lists of tests come from here, not from a hand list."""
    out = []
    want = os.path.join("devices", "_family", family) + os.sep
    for c in sorted(os.listdir(os.path.join(root, "devices"))):
        conf = os.path.join(root, "devices", c, "device.conf")
        if os.path.isfile(conf) and any(os.path.relpath(f, root).startswith(want) for f in files(conf)[1:]):
            out.append(c)
    return out


def main(argv):
    try:
        if len(argv) == 3 and argv[0] == "--members":
            print("\n".join(members(argv[1], argv[2])))
            return 0
        if len(argv) == 2 and argv[0] == "--files":
            print("\n".join(files(argv[1])))
            return 0
        if len(argv) == 3 and argv[0] == "--find":
            p = find(argv[1], argv[2])
            if p:
                print(p)
            return 0 if p else 1
        if len(argv) == 1:
            sys.stdout.write(text(argv[0]))
            return 0
    except (LayerError, OSError) as e:
        print("layered: ERROR %s" % e, file=sys.stderr)
        return 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
