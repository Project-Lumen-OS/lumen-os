#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""dexclass_diff.py BASE.jar PATCHED.jar OUTDIR: every class of two service-wifi.jar dex files compared one by one
(lineage/patches/wifi/0001 must change only the P2P HAL: docs/casting/WIFI_P2P_HAL.md "Proofs").

dexdump -d prints each class with its fields, methods and every instruction with names resolved. The pool indices
(method@, field@, string@, type@, proto@, call_site@, *_idx), instruction addresses, raw code units and the class
number move whenever a class is added anywhere in the dex, so they are dropped; everything else (names, flags,
signatures, registers, every instruction with its operands, branch targets relative to the method, try/catch, line
and local tables) must match. Writes OUTDIR/classes.tsv (class, state, sha256 of the normalised dump of each side)
and OUTDIR/diff/<class>.diff for every class that differs, prints the summary.
"""
import hashlib
import os
import re
import subprocess
import sys
import zipfile
import difflib

DEXDUMP = os.environ.get("DEXDUMP", os.path.expanduser("~/Library/Android/sdk/build-tools/36.1.0/dexdump"))
# an instruction line: absolute address, raw code units (they hold the pool indices), then |relative address and the
# instruction; the payloads (switch tables, array data) keep their raw units since they hold no index
INSN = re.compile(r"^[0-9a-f]{6}: ((?:[0-9a-f]{4} )*(?:\.\.\. )?)\s*\|([0-9a-f]{4}: .*)$")
PAYLOAD = re.compile(r"[0-9a-f]{4}: (packed-switch-data|sparse-switch-data|array-data) ")
STRIP = [
    (re.compile(r"\s*//\s*(method|field|string|type|proto|call_site|method_handle)@[0-9a-f]+"), ""),
    (re.compile(r"\b(\w+_idx|\w+_off)\s*:\s*\d+(\s*\(0x[0-9a-f]+\))?"), r"\1 : N"),
    (INSN, lambda m: (m.group(1).strip() + " " if PAYLOAD.match(m.group(2)) else "") + "|" + m.group(2)),
    (re.compile(r"^Class #\d+"), "Class #N"),
]
CODE_ADDR = re.compile(r"^[0-9a-f]{6}:\s+\|\[[0-9a-f]{6}\]")  # a method's code header: its absolute address


def dex_bytes(jar):
    with zipfile.ZipFile(jar) as z:
        names = sorted(n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        return {n: z.read(n) for n in names}


def dump(dex, tmp):
    p = os.path.join(tmp, "x.dex")
    open(p, "wb").write(dex)
    out = subprocess.run([DEXDUMP, "-d", p], capture_output=True, check=True).stdout.decode("utf-8", "surrogateescape")
    classes, cur, name = {}, [], None
    for line in out.splitlines():
        if line.startswith("Class #"):
            if name:
                classes[name] = cur
            cur, name = [], None
        m = re.match(r"\s*Class descriptor\s*:\s*'(.+)'", line)
        if m and name is None:
            name = m.group(1)
        if CODE_ADDR.match(line):
            line = "|[ADDR]" + line.split("]", 1)[1]
        for rx, rep in STRIP:
            line = rx.sub(rep, line)
        cur.append(line)
    if name:
        classes[name] = cur
    return classes


def main():
    base, patched, outdir = sys.argv[1:4]
    os.makedirs(os.path.join(outdir, "diff"), exist_ok=True)
    a_dex, b_dex = dex_bytes(base), dex_bytes(patched)
    assert list(a_dex) == list(b_dex) == ["classes.dex"], (list(a_dex), list(b_dex))
    a, b = dump(a_dex["classes.dex"], outdir), dump(b_dex["classes.dex"], outdir)
    os.remove(os.path.join(outdir, "x.dex"))
    h = lambda lines: hashlib.sha256("\n".join(lines).encode("utf-8", "surrogateescape")).hexdigest() if lines is not None else "-"
    rows, changed = [], []
    for c in sorted(set(a) | set(b)):
        sa, sb = h(a.get(c)), h(b.get(c))
        state = "same" if sa == sb else ("added" if c not in a else "removed" if c not in b else "changed")
        rows.append("%s\t%s\t%s\t%s" % (c, state, sa, sb))
        if state != "same":
            changed.append((c, state))
            d = difflib.unified_diff(a.get(c, []), b.get(c, []), "base/" + c, "patched/" + c, lineterm="", n=2)
            fn = re.sub(r"[^A-Za-z0-9_.$-]", "_", c.strip("L;")) + ".diff"
            open(os.path.join(outdir, "diff", fn), "w", errors="surrogateescape").write("\n".join(d) + "\n")
    open(os.path.join(outdir, "classes.tsv"), "w").write("class\tstate\tbase\tpatched\n" + "\n".join(rows) + "\n")
    print("classes: base %d, patched %d, same %d, differing %d" % (len(a), len(b), len(rows) - len(changed), len(changed)))
    for c, s in changed:
        print("  %s %s" % (s, c))


if __name__ == "__main__":
    main()
