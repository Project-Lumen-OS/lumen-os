#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""One language of the installers' message catalog (installer/lib/messages.tsv) out and back in.

    python3 tools/devices/installer_msgs_lang.py export LANG [--all] > LANG.tsv    rows to translate: id, English, LANG text
    python3 tools/devices/installer_msgs_lang.py import LANG LANG.tsv               writes the LANG column of those ids

The file holds one row per id: 'id <TAB> English <TAB> text' (import reads the first and the last column; the
escapes are the catalog's: \\n, \\t, \\\\). export lists the rows whose LANG cell is empty (--all: every row), without
the .win rows that only change option names (import fills those from the translation of their row: the option names,
paths and commands are the same in every language). import refuses a row whose values ({1}, {2}, ...) differ from
the English, and an id the catalog does not have. Several translators can each work on one language file and the
catalog takes them one after another, so the shared rows of the catalog never meet in a merge.
Then: python3 tools/devices/test/installer_msgs.py
"""
import re
import sys
from pathlib import Path

G = Path(__file__).resolve().parents[2]
CAT = G / "installer" / "lib" / "messages.tsv"
OPTS = [("bash lumen-install.sh", "lumen-install.cmd"), ("--platform-tools", "-PlatformTools"),
        ("--erofs-utils", "-ErofsUtils"), ("--blobs=embedded", "-Blobs embedded"),
        ("--blobs=partition", "-Blobs partition"), ("--wipe=factory-reset", "-Wipe factory-reset"),
        ("--unsigned-image", "-UnsignedImage"), ("--gapps-zip", "-GappsZip"), ("--keep-data", "-KeepData"),
        ("--no-blobs", "-NoBlobs"), ("--device", "-Device"), ("--serial", "-Serial"), ("--image", "-Image"),
        ("--remove", "-Remove"), ("--lang", "-Lang"), ("--zip", "-Zip"), ("--ip", "-Ip")]


def windows(t):  # the ps1 wording of a text that differs only by option names
    for a, b in OPTS:
        t = t.replace(a, b)
    return t


def read():
    lines = CAT.read_text(encoding="utf-8").split("\n")
    head = next(i for i, l in enumerate(lines) if l.startswith("id\t"))
    return lines, head, lines[head].split("\t")


def derived(rows, i):  # a .win row that only changes option names: its base row id
    if not i.endswith(".win") or i[:-4] not in rows:
        return None
    return i[:-4] if windows(rows[i[:-4]][1]) == rows[i][1] else None


def main(argv):
    if len(argv) < 3 or argv[1] not in ("export", "import"):
        print(__doc__)
        return 2
    lines, head, cols = read()
    lang = argv[2]
    if lang not in cols[2:]:
        print("no column %s (the catalog has %s)" % (lang, ", ".join(cols[1:])))
        return 2
    c = cols.index(lang)
    rows, at = {}, {}
    for k in range(head + 1, len(lines)):
        if lines[k] and not lines[k].startswith("#"):
            f = lines[k].split("\t")
            rows[f[0]] = f
            at[f[0]] = k
    if argv[1] == "export":
        for i, f in rows.items():
            if (f[c] == "" or "--all" in argv) and not derived(rows, i):
                print("%s\t%s\t%s" % (i, f[1], f[c]))
        return 0
    bad = 0
    got = {}
    for n, l in enumerate(Path(argv[3]).read_text(encoding="utf-8").split("\n"), 1):
        if not l.strip() or l.startswith("#"):
            continue
        f = l.split("\t")
        i, t = f[0], f[-1]
        if i not in rows:
            print("line %d: no id %s in the catalog" % (n, i))
            bad = 1
            continue
        if set(re.findall(r"\{[0-9]+\}", t)) != set(re.findall(r"\{[0-9]+\}", rows[i][1])):
            print("line %d: %s has the values %s, the English %s" % (n, i, sorted(set(re.findall(r"\{[0-9]+\}", t))),
                                                                     sorted(set(re.findall(r"\{[0-9]+\}", rows[i][1])))))
            bad = 1
            continue
        if "\u2014" in t:
            print("line %d: %s: an em dash" % (n, i))
            bad = 1
            continue
        got[i] = t
    if bad:
        return 1
    for i, t in got.items():
        rows[i][c] = t
    for i in rows:   # the .win rows that only change option names follow their row
        b = derived(rows, i)
        if b and rows[b][c]:
            rows[i][c] = windows(rows[b][c])
    for i, f in rows.items():
        lines[at[i]] = "\t".join(f)
    CAT.write_text("\n".join(lines), encoding="utf-8")
    print("%s: %d rows written, %d of %d rows have a %s text" % (lang, len(got), sum(1 for f in rows.values() if f[c]),
                                                                 len(rows), lang))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
