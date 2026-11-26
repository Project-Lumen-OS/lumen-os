#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""gmpf_firmware_calls.py [REPO]: no X50 family firmware ever gets a call its own HAL table names differently.

The X50 family shares one menu app (X50Projector) over many vendor firmwares, and their gmpf tables differ: the RS30
Ultra v1.2.5 table ends ProjectorFocusManager at 107, which is getUitiltShiftSwithStatus there and the lens memory
writer executeNimbleOpticalPos everywhere else. A call is sent only on the interface hash the transport accepted
(org.lumen.hal.GmpfHash, each model's HashRule in GmpfModel), and Gmpf.java holds back the calls of only some
firmwares behind GmpfFirmware (firmwareGuard, uniformityGuard, or an `if (GmpfFirmware.X.on(guardedHash(..)))` branch
that ends in `return;`). This reads every method of Gmpf.java, finds the firmware guard in force at each
`GmpfCall.X` it sends, and checks against the HAL table of every hash that guard lets through (every hash any model
accepts when there is none) that the code is a row with the same method. Each hash's table is proven by the interface
hash written in its header, and each model's HashRule accepts exactly the hashes of its device.conf HAL tables. Exit 1 on any call that would reach a firmware where its code means something else or
nothing.
"""
import glob
import os
import re
import sys

G = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", ".."))
HAL = os.path.join(G, "apps/core/lumen-hal/src/org/lumen/hal")
X50 = os.path.join(G, "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector")
# the HAL table of each interface hash constant (the header line names the hash, checked below)
TABLES = {
    "X50": "devices/g0111/research/aidl_codes.tsv",
    "X50_V1254": "devices/g0111/research/aidl_codes_v1254.tsv",
    "X50_V1338": "devices/g0111/research/aidl_codes_v1338.tsv",
    "TITAN_V1299": "devices/h0062/research/aidl_codes_v1299.tsv",
    "RS30": "devices/g0155/research/aidl_codes.tsv",
    "RS30_ULTRA": "devices/g0170/research/aidl_codes_v125.tsv",
    "RS30_OLD": "devices/_family/rs30old/research/aidl_codes_v1227.tsv",
    "RS30_OLD_V1147": "devices/_family/rs30old/research/aidl_codes_v1147.tsv",
    "MIRA": "devices/_family/mira/research/aidl_codes_v131.tsv",
}
bad = []


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


hashes = dict(re.findall(r'public static final String (\w+) = "([0-9a-f]{40})";', read(os.path.join(HAL, "GmpfHash.java"))))
rules = {}
for name, args in re.findall(r"public static final HashRule (\w+) = new HashRule\(([^)]*)\);", read(os.path.join(HAL, "GmpfHash.java"))):
    rules[name] = [a.strip() for a in args.split(",") if a.strip() in hashes]
models = re.findall(r'\n    (\w+)\("([gh]\d{4})", "([GH]\d{4})", GmpfHash\.(\w+)', read(os.path.join(HAL, "GmpfModel.java")))
accepted = sorted({h for _, _, _, r in models for h in rules.get(r, [])})
firmwares = {}
for name, args in re.findall(r"\n    (\w+)\(((?:GmpfHash\.\w+(?:, )?)+)\)[,;]", read(os.path.join(HAL, "GmpfFirmware.java"))):
    firmwares[name] = re.findall(r"GmpfHash\.(\w+)", args)

if not models or not accepted or not firmwares:
    sys.exit("gmpf_firmware_calls: could not read GmpfModel / GmpfHash / GmpfFirmware")
for m in models:
    if m[3] not in rules:
        bad.append("model %s: rule %s is not a HashRule of GmpfHash" % (m[2], m[3]))

tables = {}
for h in accepted + sorted({x for v in firmwares.values() for x in v}):
    if h in tables:
        continue
    rel = TABLES.get(h)
    if rel is None:
        bad.append("hash %s has no HAL table in TABLES (add its aidl_codes file)" % h)
        continue
    text = read(os.path.join(G, rel))
    head = "\n".join(l for l in text.split("\n")[:12] if l.startswith("#"))
    if hashes[h] not in head:
        bad.append("%s: the header does not name the %s hash %s" % (rel, h, hashes[h]))
    rows = {}
    for l in text.split("\n"):
        if l.startswith("#") or not l.strip():
            continue
        c = l.split("\t")
        rows[(c[0], int(c[1]))] = c[2]
    tables[h] = rows

# each model's HashRule accepts exactly the hashes of its own HAL tables (device.conf hal_codes, hal_codes_also)
for _, code, model, rule in models:
    conf = os.path.join(G, "devices", code, "device.conf")
    if not os.path.isfile(conf):
        bad.append("model %s: no devices/%s/device.conf" % (model, code))
        continue
    own = set()
    sys.path.insert(0, os.path.join(G, "tools", "devices"))
    import layered
    eff = layered.effective(conf)                    # the model's own keys and its family's (one table for a line)
    for v in (eff.get("hal_codes", ""), eff.get("hal_codes_also", "")):
        for rel in v.split():
            head = "\n".join(l for l in read(os.path.join(G, rel)).split("\n")[:12] if l.startswith("#"))
            own |= {h for h, val in hashes.items() if val in head}
    if own != set(rules.get(rule, [])):
        bad.append("model %s (%s): rule %s accepts %s, its HAL tables are %s" % (model, code, rule,
                   sorted(rules.get(rule, [])), sorted(own)))

halcodes = {k: int(v) for k, v in re.findall(r"static final int (\w+) = (-?\d+);", read(os.path.join(X50, "HalCodes.java")))}
ifaces = dict(re.findall(r'\n    (\w+)\("xgimi\.hardware\.gmpf\.I(\w+)"\)', read(os.path.join(X50, "GmpfIface.java"))))
calls = {}
for name, iface, code, method in re.findall(r"\n    (\w+)\(GmpfIface\.(\w+),\s*HalCodes\.(\w+),\s*\"(\w+)\"", read(os.path.join(X50, "GmpfCall.java"))):
    calls[name] = (ifaces[iface], halcodes[code], method)

src = read(os.path.join(X50, "Gmpf.java"))
# the methods of Gmpf: from a declaration at class level (4 spaces) to the closing brace at the same indent
heads = list(re.finditer(r"\n    (?:@Override )?(?:public |private |protected |static |final |synchronized )*[\w<>\[\], .]+ (\w+)\([^;{]*\)(?: throws [\w, ]+)? \{\n", src))
EVENT = re.compile(r"firmwareGuard\([^,]+, GmpfFirmware\.(?P<fg>\w+)|(?P<ug>uniformityGuard)\(|"
                   r"if \(GmpfFirmware\.(?P<br>\w+)\.on\(guardedHash\([^)]*\)\)\) \{|(?P<ret>return;)|GmpfCall\.(?P<call>\w+)")
sent = 0
for i, hm in enumerate(heads):
    body_end = src.find("\n    }\n", hm.end() - 1)
    body = src[hm.end():body_end]
    name = hm.group(1)
    fw, branch = None, None
    for e in EVENT.finditer(body):
        if e.group("fg"):
            fw = e.group("fg")
        elif e.group("ug"):
            fw = "COLOR_UNIFORMITY"
        elif e.group("br"):
            branch = e.group("br")
        elif e.group("ret"):
            branch = None
        elif e.group("call"):
            c = e.group("call")
            if c not in calls:
                if c not in ("GET_INTERFACE_VERSION", "GET_INTERFACE_HASH"):
                    bad.append("Gmpf.%s: GmpfCall.%s is not a constant with an interface" % (name, c))
                continue
            guard = branch or fw
            if guard is not None and guard not in firmwares:
                bad.append("Gmpf.%s: guard %s is not a GmpfFirmware" % (name, guard))
                continue
            svc, code, method = calls[c]
            sent += 1
            for h in (firmwares[guard] if guard else accepted):
                if h not in tables:
                    continue
                have = tables[h].get((svc, code))
                if have != method:
                    bad.append("Gmpf.%s sends GmpfCall.%s (%s %d %s) on the %s hash%s, whose table has %s there"
                               % (name, c, svc, code, method, h, "" if guard is None else " (guard %s)" % guard,
                                  repr(have) if have else "no row"))
if sent < 150:
    bad.append("only %d calls of Gmpf.java read: the method reader broke" % sent)
for b in bad:
    print("FAIL gmpf firmware calls: " + b)
if not bad:
    print("ok   gmpf firmware calls: %d sends of Gmpf.java, each only on firmwares whose table has it (%d hashes, %d models)"
          % (sent, len(tables), len(models)))
sys.exit(1 if bad else 0)
