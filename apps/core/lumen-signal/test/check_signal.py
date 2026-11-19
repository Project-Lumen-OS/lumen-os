#!/usr/bin/env python3
"""lumen-signal checks that need the files, not the JVM (test/run.sh runs it):
1. texts: res/values and res/values-ru hold exactly the SignalText keys, with the same %N$s arguments; plain words
   (no semicolon, no long dash, never DSU, vbmeta or slot);
2. data: every devices/*/signal.tsv names only known HAL items, once each, never the banned vendor calls
   (IGmpf 194 isPlayingHDRVideo, IGmpf 275 light sensor, IGmpf2 252 laser current); every app recipe with module
   lumen-signal ships its model's signal.tsv as assets/lumen_signal/items.tsv; every profile with signal.panel has
   a signal.tsv;
3. readers: the projector apps' SignalReader adapters never name a banned call.
"""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
G = os.path.abspath(os.path.join(MOD, "..", "..", ".."))
bad = []


def ok(what, cond):
    if cond:
        print("ok   " + what)
    else:
        print("FAIL " + what)
        bad.append(what)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


src = read(os.path.join(MOD, "src/org/lumen/signal/SignalText.java"))
all_block = src[src.index("ALL = {"):src.index("};", src.index("ALL = {"))]
names = re.findall(r"\b([A-Z][A-Z0-9_]+)\b", all_block)
consts = dict(re.findall(r"\b([A-Z][A-Z0-9_]+) = \"([a-z0-9_]+)\"", src))
keys = {consts[n] for n in names if n in consts}
ok("SignalText.ALL names every key constant (%d)" % len(consts), keys == set(consts.values()))

texts = {}
for loc in ("values", "values-ru"):
    x = read(os.path.join(MOD, "res", loc, "lumen_signal_strings.xml"))
    texts[loc] = dict(re.findall(r'<string name="lumen_signal_([a-z0-9_]+)">([^<]*)</string>', x))
    ok("%s: exactly the SignalText keys" % loc, set(texts[loc]) == keys)
    for k, v in texts[loc].items():
        if ";" in v or "\u2014" in v or "\u2013" in v or re.search(r"\b(dsu|vbmeta|slot)\b", v, re.I):
            bad.append("%s %s: not plain: %r" % (loc, k, v))
for k in keys & set(texts["values"]) & set(texts["values-ru"]):
    a = sorted(re.findall(r"%\d\$s", texts["values"][k]))
    b = sorted(re.findall(r"%\d\$s", texts["values-ru"][k]))
    if a != b:
        bad.append("arguments differ for %s: %s / %s" % (k, a, b))
ok("texts: plain words, same arguments in English and Russian", not [b for b in bad if "not plain" in b or "arguments" in b])

items_src = read(os.path.join(MOD, "src/org/lumen/signal/SignalItem.java"))
hal = {t for t, h, b in re.findall(r'\("([a-z_]+)", (true|false), ReadSpeed\.[A-Z_]+, (?:true|false)(?:, (true|false))?\)',
                                   items_src) if h == "true" or b == "true"}
ok("SignalItem: HAL items found (%d)" % len(hal), len(hal) >= 9)
BANNED = re.compile(r"IGmpf 194|IGmpf 275|IGmpf2 252|isPlayingHDRVideo|getCurHwCurrent|LightSensor", re.I)
devs = sorted(glob.glob(os.path.join(G, "devices/*/signal.tsv")))
ok("signal.tsv of 8 models (%d)" % len(devs), len(devs) >= 8)
for p in devs:
    c = os.path.basename(os.path.dirname(p))
    rows = [l.split("\t") for l in read(p).splitlines() if l and not l.startswith("#") and not l.startswith("item\t")]
    toks = [r[0] for r in rows]
    ok("%s: known HAL items, once each" % c, all(t in hal for t in toks) and len(toks) == len(set(toks)) and toks)
    ok("%s: no banned vendor call" % c, not BANNED.search(read(p)))
for j in glob.glob(os.path.join(G, "devices/*/menu.json")):
    c = os.path.basename(os.path.dirname(j))
    if '"signal.panel"' in read(j):
        ok("%s: profile has signal.panel and a signal.tsv" % c, os.path.exists(os.path.join(G, "devices", c, "signal.tsv")))
for rec in glob.glob(os.path.join(G, "devices/*/app/*.layers")):
    r = read(rec)
    if not re.search(r"^module\s+lumen-signal\s*$", r, re.M):
        continue
    c = rec.split(os.sep)[-3]
    want = r"^file\s+devices/%s/signal\.tsv\s+assets/lumen_signal/items\.tsv\s*$" % c
    ok("%s: ships devices/%s/signal.tsv" % (os.path.relpath(rec, G), c), re.search(want, r, re.M) is not None)
    ok("%s: names lumen-icons (dependency)" % os.path.relpath(rec, G), re.search(r"^module\s+lumen-icons", r, re.M) is not None)
    ok("%s: names lumen-ui (dependency)" % os.path.relpath(rec, G), re.search(r"^module\s+lumen-ui\s*$", r, re.M) is not None)

readers = [p for p in glob.glob(os.path.join(G, "apps/*/src/**/*SignalReader.java"), recursive=True)]
ok("three readers (Z9X and HB, X50 family, Play 6): %d" % len(readers), len(readers) == 3)
for p in readers:
    s = read(p)
    calls = re.sub(r"/\*.*?\*/|//[^\n]*", "", s, flags=re.S)
    ok("%s: no banned call" % os.path.relpath(p, G),
       not BANNED.search(calls) and not re.search(r"\b(194|275|252)\b", calls))

for b in bad:
    if "not plain" in b or "arguments" in b:
        print("FAIL " + b)
# the hold hint on a card opened by the remote key the user gave it (OkHold.hint byKey)
ka = read(os.path.join(G, "apps/core/lumen-remote/src/org/lumen/remote/keys/KeyActions.java"))
sr = read(os.path.join(MOD, "src/org/lumen/signal/SignalReceiver.java"))
ok("KeyActions marks the intent of a key action", ".putExtra(EXTRA_BY_KEY, true)" in ka)
ok("SignalReceiver passes the key mark to the panel",
   "getBooleanExtra(KeyActions.EXTRA_BY_KEY, false)" in sr and "SignalPanel.press(app, byKey)" in sr)
ok("SignalPanel asks the hint with the key mark",
   "CardHold.hint(sApp, sByKey)" in read(os.path.join(MOD, "src/org/lumen/signal/SignalPanel.java")))
print(("ok   " if not bad else "FAIL ") + "check_signal: %d problem(s)" % len(bad))
sys.exit(1 if bad else 0)
