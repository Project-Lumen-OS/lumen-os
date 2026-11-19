#!/usr/bin/env python3
"""lumen-sensors data and wiring checks: every devices/*/sensors.tsv names only SensorItem tokens and never a forbidden
vendor call (IGmpf 194, IGmpf 275, IGmpf2 252), every recipe with the signal panel also has the sensor card and its
table, every reader stays off the forbidden getters, English and Russian texts have the same keys, the old LumenSensors
app is gone."""
import glob, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
M = os.path.dirname(HERE)
G = os.path.normpath(os.path.join(M, "..", "..", ".."))
bad = []
items = re.findall(r'^\s+([A-Z_]+)\("([a-z_]+)"', open(os.path.join(M, "src/org/lumen/sensors/SensorItem.java")).read(), re.M)
tokens = {t for _, t in items}
FORBIDDEN = [r"\bIGmpf 194\b", r"\bIGmpf 275\b", r"\bIGmpf2 252\b"]
for tsv in sorted(glob.glob(os.path.join(G, "devices/*/sensors.tsv"))):
    for n, line in enumerate(open(tsv, encoding="utf-8"), 1):
        if line.startswith("#") or line.startswith("item\t") or not line.strip():
            continue
        tok = line.split("\t")[0]
        if tok not in tokens:
            bad.append(f"{tsv}:{n}: unknown item {tok}")
        for f in FORBIDDEN:
            if re.search(f, line.split("#")[0]):
                bad.append(f"{tsv}:{n}: forbidden call {line.strip()}")
for lay in sorted(glob.glob(os.path.join(G, "devices/*/app/*.layers"))):
    s = open(lay).read()
    if "lumen-signal" in s:
        c = lay.split(os.sep)[-3]
        if "module    lumen-sensors" not in s or f"devices/{c}/sensors.tsv assets/lumen_sensors/items.tsv" not in s:
            bad.append(f"{lay}: the signal panel without the sensor card or its table")
# forbidden getters: the stock names of the three codes (codes tables), never called by a reader
names = set()
for codes in glob.glob(os.path.join(G, "devices/*/hal/codes.tsv")):
    for line in open(codes, encoding="utf-8"):
        c = line.split("\t")
        if len(c) > 3 and ((c[0] == "IGmpf" and c[2] in ("194", "275")) or (c[0] == "IGmpf2" and c[2] == "252")):
            names.add(c[3])
readers = [os.path.join(G, p) for p in ("apps/ProjectorBase/src/org/lumen/projector/panel/GmpfSensorReader.java",
           "apps/Play6Projector/src/org/lumen/projector/panel/Play6SensorReader.java",
           "apps/X50Projector/src/org/lumen/x50/projector/X50SensorReader.java")]
for r in readers + glob.glob(os.path.join(M, "src/org/lumen/sensors/*.java")):
    if not os.path.exists(r):
        bad.append(f"missing {r}")
        continue
    s = open(r).read()
    for nm in names:
        if re.search(r"\b" + re.escape(nm) + r"\b", s):
            bad.append(f"{r}: calls the forbidden {nm}")
def keys(p):
    return set(re.findall(r'name="(lumen_sensors_[a-z_]+)"', open(p, encoding="utf-8").read()))
en, ru = keys(os.path.join(M, "res/values/lumen_sensors_strings.xml")), keys(os.path.join(M, "res/values-ru/lumen_sensors_strings.xml"))
if en != ru:
    bad.append(f"texts differ: {sorted(en ^ ru)}")
if os.path.exists(os.path.join(G, "apps/LumenSensors")):
    bad.append("apps/LumenSensors still there (the card replaces it)")
for b in bad:
    print("FAIL", b)
print(("ok   " if not bad else "FAIL ") + f"check_sensors: {len(glob.glob(os.path.join(G, 'devices/*/sensors.tsv')))} tables, {len(names)} forbidden getters named")
sys.exit(1 if bad else 0)
