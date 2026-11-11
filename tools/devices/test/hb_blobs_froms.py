#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""hb_blobs_froms.py REAL_ALLOW TEST_ALLOW FILES_DIR: several from= sources on one line of a test allow-list.

tools/ota/test/blobs_fixture.py (a Z9X gate input, unchanged) keeps one from= per line. A real line with several from=
(one stock original per XGIMI firmware, e.g. the HB audio policy of V6.16.24 and of V6.17.12) gets them all here, with
made-up files: FILES_DIR/<path> the target, FILES_DIR/<path>.stock the original of the first from=, .stock2, .stock3
... the originals of the next ones, each turned into the same target by its own 'N,Md' deletes (the real line ranges).
TEST_ALLOW is rewritten in place (the line's sha256 and every from= get the fake files' hashes). Never reads or writes
a real MediaTek / XGIMI file.
"""
import hashlib
import re
import sys


def sha(b):
    return hashlib.sha256(b).hexdigest()


def gone(script):
    g = set()
    for c in script.split(";"):
        m = re.fullmatch(r"(\d+)(?:,(\d+))?d", c)
        assert m, c
        g.update(range(int(m.group(1)), int(m.group(2) or m.group(1)) + 1))
    return g


def main(real, allow, files):
    multi = {}
    for line in open(real):
        w = line.split()
        if len(w) < 3 or w[0].startswith("#") or w[0].startswith("set="):
            continue
        fr = [o[5:].split(":", 1)[1] for o in w[3:] if o.startswith("from=")]
        if len(fr) >= 2:
            multi[w[1]] = fr
    out = []
    for line in open(allow):
        w = line.split()
        if len(w) < 3 or w[0].startswith("#") or w[0].startswith("set=") or w[1] not in multi:
            out.append(line.rstrip("\n"))
            continue
        rel, scripts = w[1], multi[w[1]]
        gs = [gone(s) for s in scripts]
        top = max(max(g) for g in gs) + 20 + max(len(g) for g in gs)
        first = [b"<line %d of the fake stock %s/>\n" % (i, rel.encode()) for i in range(1, top + 1)]
        target = [l for i, l in enumerate(first, 1) if i not in gs[0]]
        stocks = [b"".join(first)]
        for k, g in enumerate(gs[1:], 2):
            n, it, st = len(target) + len(g), iter(target), []
            for i in range(1, n + 1):
                st.append(b"<line %d only in the fake stock %d of %s/>\n" % (i, k, rel.encode()) if i in g else next(it))
            stocks.append(b"".join(st))
        data = b"".join(target)
        for k, st in enumerate(stocks, 1):
            assert b"".join(l for i, l in enumerate(st.splitlines(True), 1) if i not in gs[k - 1]) == data
            open("%s/%s.stock%s" % (files, rel, "" if k == 1 else k), "wb").write(st)
        open("%s/%s" % (files, rel), "wb").write(data)
        opts = [o for o in w[3:] if not o.startswith("from=")]
        opts += ["from=%s:%s" % (sha(st), s) for st, s in zip(stocks, scripts)]
        out.append(" ".join([sha(data), rel, w[2]] + opts))
    open(allow, "w").write("\n".join(out) + "\n")
    print("hb_blobs_froms: %d line(s) with several from= sources" % len(multi))


if __name__ == "__main__":
    main(*sys.argv[1:4])
