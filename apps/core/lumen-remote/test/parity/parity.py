#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Compares the shared engine's trace with the goldens of the old implementations (test/parity/README in run.sh):
- z9xold (Z9X, HB, Play 6 RemoteAutoPair): every decision line must be the same, except the hid-allow lines (the
  shared engine also asks the HID host to connect, from the X50 code; Z9X_HID: where that ends connected), and the
  scenarios in Z9X_WIDER, where the old code refused a remote of the table that the shared engine must now pair
  (any XGIMI remote on any projector);
- x50old (X50, RS30 RemotePair): every remote the old code bonded or connected is bonded or connected by the shared
  engine too, and a scenario that ended connected ends connected (nothing accepted before is refused now), except the
  scenarios in X50_CHANGED where the shared engine follows the Z9X reference on purpose;
- NEW: scenarios that only the shared engine runs (cross-model and generic remotes) with their expected outcome.
Exit 1 on any difference. usage: parity.py GOLDEN_Z9X GOLDEN_X50 TRACE_SHARED
"""
import re
import sys

DECISION = re.compile(r"^\s*[0-9.]+ (createBond|removeBond|confirm|aborted|system-pairing-dialog|prompt|self-grant|check|CRASH)")
Z9X_WIDER = {
    "S03": "Xgimi-RC-S3R (000d:3843) is a table remote: the Z9X 1.0 prefix rule missed it",
    "S04": "BLuetooth 4.0 RC is a stock remote name of every model: the Z9X 1.0 rule needed XGIMI",
    "S08": "General Discoverable is pairing mode on stock and the X50 rule, Z9X 1.0 wanted Limited only",
    "S09": "no flags AD + the HID service while no remote is bonded (X50 rule) is a remote in pairing mode",
}
Z9X_HID = {
    "S26": "the shared engine asks the HID host to connect a bonded remote the stack left down (X50 RemotePair rule)",
}
X50_CHANGED = {
    "S10": "the user-started window bonds a broader set (Z9X reference: an XGIMI name with REMOTE)",
    "S12": "a reset remote is re-paired after the 3 s check (Z9X reference), the X50 code only retried connect",
    "S14": "the automatic scan shares the radio with Bluetooth audio at LOW_POWER (Z9X reference)",
    "S16": "the remote-lost prompt (Z9X reference) now shows on the X50 family too",
    "S17": "missing runtime permissions are granted by the app itself where it may (Z9X reference)",
}
NEW = {
    "S21": ("bond", "<mac>"),
    "S22": ("bond", "<mac>"),
    "S23": ("nobond", "<mac>"),
    "S24": ("nobond", "<mac>"),
    "S25": ("bond", "<mac>"),
    "S27": ("bond", "<mac>"),
    "S28": ("bond", "<mac>"),
    "S29": ("nobond", "<mac>"),
    "S30": ("bond", "<mac>"),
    "S31": ("nobond", "<mac>"),
    "S32": ("nobond", "<mac>"),
    "S33": ("bond", "<mac>"),
}


def load(path):
    out, cur = {}, None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("== "):
            cur = line.split()[1]
            out[cur] = []
        elif cur:
            out[cur].append(line)
    return out


def decisions(lines, drop_hid=True):
    return [l for l in lines if DECISION.match(l) and not (drop_hid and " hid-allow " in l)]


def addrs(lines, what):
    return {l.split()[2] for l in lines if re.match(r"^\s*[0-9.]+ %s " % what, l)}


def main(gz, gx, ts):
    z, x, s = load(gz), load(gx), load(ts)
    bad = []
    for sid, lines in s.items():
        if any(" CRASH " in l for l in lines):
            bad.append("%s: crash %s" % (sid, [l for l in lines if "CRASH" in l]))
    for sid, old in z.items():
        if sid in NEW:
            continue
        new = s.get(sid)
        if new is None:
            bad.append("%s: missing in the shared trace" % sid)
            continue
        if sid in Z9X_WIDER:
            want = addrs(x.get(sid, []), "createBond") or {l.split()[2] for l in old if " createBond " in l}
            got = addrs(new, "createBond")
            if not got:
                bad.append("%s (z9x wider: %s): the shared engine did not bond" % (sid, Z9X_WIDER[sid]))
            continue
        if sid in Z9X_HID:
            nocheck = lambda ls: [l for l in decisions(ls) if " check " not in l]
            if nocheck(old) != nocheck(new) or not any(" check " in l and "connected=true" in l for l in new):
                bad.append("%s (z9x + HID connect: %s): decisions differ or not connected\n  old %s\n  new %s"
                           % (sid, Z9X_HID[sid], decisions(old), decisions(new)))
            continue
        if decisions(old) != decisions(new):
            bad.append("%s: z9x decisions differ\n  old %s\n  new %s" % (sid, decisions(old), decisions(new)))
    for sid, old in x.items():
        new = s.get(sid, [])
        if sid in X50_CHANGED or sid in NEW:
            continue
        for a in addrs(old, "createBond"):
            if a not in addrs(new, "createBond"):
                bad.append("%s: x50 bonded %s, the shared engine does not" % (sid, a))
        for a in addrs(old, "hid-allow"):
            n_old = sum(1 for l in old if re.match(r"^\s*[0-9.]+ hid-allow %s$" % re.escape(a), l))
            n_new = sum(1 for l in new if re.match(r"^\s*[0-9.]+ hid-allow %s$" % re.escape(a), l))
            if n_new < n_old:
                bad.append("%s: x50 asked the HID host to connect %s %d times, the shared engine %d" % (sid, a, n_old, n_new))
        oc = [l for l in old if " check " in l and "connected=true" in l]
        nc = [l for l in new if " check " in l and "connected=true" in l]
        if len(oc) > len(nc):
            bad.append("%s: x50 ended connected, the shared engine does not" % sid)
    for sid, (kind, addr) in NEW.items():
        new = s.get(sid)
        if new is None:
            bad.append("%s: missing in the shared trace" % sid)
            continue
        bonded = addr in addrs(new, "createBond")
        if (kind == "bond") != bonded:
            bad.append("%s: expected %s of %s, trace %s" % (sid, kind, addr, decisions(new)))
    for b in bad:
        print("PARITY FAIL " + b)
    n = len(set(z) | set(x) | set(NEW))
    print("parity: %d scenarios, %d z9x wider, %d z9x + HID connect, %d x50 changed on purpose, %d failures"
          % (n, len(Z9X_WIDER), len(Z9X_HID), len(X50_CHANGED), len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:4]))
