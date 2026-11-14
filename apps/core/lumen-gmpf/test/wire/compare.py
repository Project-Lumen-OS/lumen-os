#!/usr/bin/env python3
"""compare.py OLD NEW: two wire traces of run.sh, call by call.

An "Unrepresentable" result of the new trace (a call shape its typed API cannot express) matches an old exception of a
call that sent nothing in both traces, and is counted apart. Red (exit 1) when any call differs on the wire (TX, GETSERVICE / CHECKSERVICE / LINK, REPLY, EVENT, GATE lines), in
its result (= line) or in the class of its exception. A changed exception message alone is listed, not red: messages
are log text, the bytes and results are the contract. Calls are paired by position and title (the sweep is sorted).
"""
import sys


def blocks(path):
    out, cur = [], None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("C "):
            cur = [line[2:], [], None]
            out.append(cur)
        elif line.startswith("  ") and cur is not None:
            body = line[2:]
            if body.startswith(("= ", "! ")):
                cur[2] = body
            else:
                cur[1].append(body)
    return out


def main(old, new):
    a, b = blocks(old), blocks(new)
    titles_a = [x[0] for x in a]
    titles_b = [x[0] for x in b]
    if titles_a != titles_b:
        only_a = sorted(set(titles_a) - set(titles_b))
        only_b = sorted(set(titles_b) - set(titles_a))
        print("compare: the call lists differ (%d / %d calls)" % (len(a), len(b)))
        for t in only_a[:20]:
            print("  only old: " + t)
        for t in only_b[:20]:
            print("  only new: " + t)
        return 1
    wire = result = cls = msg = unrep = 0
    for (t, wa, ra), (_, wb, rb) in zip(a, b):
        if rb.startswith("! Unrepresentable:") and ra.startswith("! ") and not any(l.startswith("TX ") for l in wa + wb):
            unrep += 1
            continue
        if wa != wb:
            wire += 1
            if wire <= 10:
                print("WIRE %s\n  old %s\n  new %s" % (t, wa, wb))
        if ra == rb:
            continue
        if ra.startswith("= ") or rb.startswith("= "):
            result += 1
            if result <= 10:
                print("RESULT %s\n  old %s\n  new %s" % (t, ra, rb))
        elif ra.split(":", 1)[0] != rb.split(":", 1)[0]:
            cls += 1
            if cls <= 10:
                print("EXCEPTION %s\n  old %s\n  new %s" % (t, ra, rb))
        else:
            msg += 1
    tx = sum(1 for x in a for l in x[1] if l.startswith("TX "))
    print("compare: %d calls, %d transactions: %d wire, %d result, %d exception class differences; %d message changes%s"
          % (len(a), tx, wire, result, cls, msg, "; %d unrepresentable" % unrep if unrep else ""))
    return 1 if wire or result or cls else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
