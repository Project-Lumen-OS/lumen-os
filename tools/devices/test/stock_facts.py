#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""stock_facts.py [TREE]: every model's data against the stock facts it was made from, one test for every model.

A model with stock facts has a stock.tsv in its folder or in a family folder of its chain (devices/README.md): kind,
key, value, evidence, tab separated, '#' comments. The facts are read from the official package of the model's stock
firmware (never typed from memory); the rules below are the same for every model, so a new model is data only.

  fact   code / product / show_name / update_bin / tree / firmware / folder / zip / gmpf_lib64 / hal_table /
         vendor_remotes / scope ...
  node   picture.mode (the stock picture modes of its tree), zoom (the stock zoom page)
  diff   NODE  'stock only' | 'Z9X only'   a node of the model's stock tree against the Z9X tree
  gap    NODE  a stock row the model's app has no code for: not shown, listed so nothing is lost
  feature KEY VALUE (feature.xml)

Rules:
  registry  device.conf models holds the code; its vendor_firmware is the chain's firmware facts, nothing else
  hal       a model with a derived HIDL table (hal/codes.from in its chain): the table equals what tools/hal/derive.py
            derives now (so a hand-edited or stale number is red), every stock table it is derived from is the
            hidl_stock.py table of the firmware fact's library (sha256), and the firmware facts name exactly those tables
  needs     a row of devices/_hal/gmpf-hidl/needs.tsv that sends a method the model's table lacks is not shown
  menu      a row or option the model shows that devices/_menu/stock_nodes.tsv ties to stock nodes has one of them in
            the model's stock tree; every 'stock only' node is shown through stock_nodes.tsv or is a gap
  restore   catalog.conf restore_file is the stock update_bin; every restore_files row is a vendor_firmware version of
            that file in the firmware folder; restore_stock maps each vendor_firmware to itself
  remotes   every vendor remote of the firmware has a devices/_remotes/remotes.tsv row listing the code
  scope     'trial': no updates, no channel, no installer.conf (nothing can install it)
  blocked   a table whose absent call the app names directly (derive.py '# blocked') has no image recipe (build.conf)
Exit 0 green, 1 red (one FAIL line per finding).
"""
import hashlib
import os
import re
import sys

G = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
sys.path.insert(0, os.path.join(G, "tools", "hal"))
sys.path.insert(0, os.path.join(G, "tools", "ota"))
import devices  # noqa: E402
import layered  # noqa: E402
import menu_layers  # noqa: E402
import codes_table  # noqa: E402
import derive  # noqa: E402
import licences  # noqa: E402

bad = []


def ok(what, cond):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        bad.append(what)


def rows(path):
    with open(path, encoding="utf-8") as f:
        return [l.rstrip("\n").split("\t") for l in f if l.strip() and not l.startswith("#")]


def chain_dirs(c):
    """The model folder, then its family folders nearest first."""
    files = layered.files(os.path.join(G, "devices", c, "device.conf"))
    return [os.path.dirname(os.path.join(G, f) if not os.path.isabs(f) else f) for f in files]


def facts_of(c):
    out = {"fact": {}, "node": {}, "diff": {}, "gap": {}, "feature": {}, "files": []}
    for d in chain_dirs(c):
        p = os.path.join(d, "stock.tsv")
        if not os.path.isfile(p):
            continue
        out["files"].append(os.path.relpath(p, G))
        for r in rows(p):
            if r[0] not in out or len(r) < 3:
                ok("%s: line %r is kind key value evidence" % (os.path.relpath(p, G), "\t".join(r)), False)
                continue
            if r[0] == "fact" and r[1] == "firmware":
                out["fact"].setdefault("firmware", []).append(r[2])
            else:
                out[r[0]].setdefault(r[1], r[2])
    return out


def conf(path):
    if not os.path.isfile(path):
        return None
    return dict(l.split("=", 1) for l in open(path, encoding="utf-8").read().splitlines() if "=" in l and not l.startswith("#"))


def sha_header(rel):
    m = re.search(r"sha256 ([0-9a-f]{64})", open(os.path.join(G, rel), encoding="utf-8").read(2000))
    return m and m.group(1)


node_map = [(r[0], r[1].split()) for r in rows(os.path.join(G, "devices/_menu/stock_nodes.tsv"))]
needs = derive.needs(G)
remotes = [r for r in rows(os.path.join(G, "devices/_remotes/remotes.tsv")) if len(r) > 9]
models = 0
for c, d in sorted(devices.load_all(G).items()):
    F = facts_of(c)
    if not F["files"]:
        continue
    models += 1
    fact = F["fact"]
    code = fact.get("code")
    dev = layered.effective(os.path.join(G, "devices", c, "device.conf"))
    # registry
    ok("%s: device.conf models %s holds the stock code %s" % (c, dev.get("models"), code), code in dev.get("models", "").split())
    ok("%s: vendor_firmware %s = the firmware facts %s" % (c, dev.get("vendor_firmware"), fact.get("firmware")),
       sorted(dev.get("vendor_firmware", "").split()) == sorted(fact.get("firmware", [])))
    # hal
    spec = next((os.path.join(dd, "hal", "codes.from") for dd in chain_dirs(c) if os.path.isfile(os.path.join(dd, "hal", "codes.from"))), None)
    absent = set()
    if spec:
        table = os.path.join(os.path.dirname(spec), "codes.tsv")
        try:
            text, maps = derive.derive(G, c, spec)
            ok("%s: %s is what derive.py derives by method name now" % (c, os.path.relpath(table, G)),
               open(table, encoding="utf-8").read() == text)
            for p, body in maps.items():
                ok("%s: %s is generated (derive.py)" % (c, os.path.relpath(p, G)),
                   licences.current(os.path.relpath(p, G), open(p, encoding="utf-8").read() if os.path.isfile(p)
                                    else None, body))
        except Exception as e:  # noqa: BLE001  any refusal of the derivation is a red line of this model
            ok("%s: derive %s: %s" % (c, os.path.relpath(spec, G), e), False)
        stocks = sorted({r[2] for r in rows(spec) if r[0] == "stock"})
        ok("%s: the stock tables %s are the firmware facts' hal_table %s" % (c, stocks, fact.get("hal_table")),
           stocks == sorted(fact.get("hal_table", "").split()))
        for t in stocks:
            ok("%s: %s was read from the firmware's library (sha256 %s)" % (c, t, (fact.get("gmpf_lib64") or "?")[:12]),
               sha_header(t) == fact.get("gmpf_lib64"))
        blocked = []
        for line in open(table, encoding="utf-8"):
            if line.startswith("# absent\t"):
                f = line.rstrip("\n").split("\t")
                absent.add((f[1], f[3]))
            elif line.startswith("# blocked\t"):
                blocked.append(line.split("\t")[1])
        ok("%s: no image recipe while its HAL table is blocked (%s)" % (c, " ".join(blocked) or "not blocked"),
           not blocked or not os.path.isfile(os.path.join(G, "devices", c, "build.conf")))
    try:
        menu = menu_layers.effective(os.path.join(G, "devices", c, "menu.json"))
    except (menu_layers.LayerError, layered.LayerError, ValueError) as e:
        ok("%s: the menu profile merges (%s)" % (c, e), False)
        menu = {}
    feats = menu.get("features", {})
    for row, iface, methods, _ in needs:
        miss = [m for m in methods if (iface, m) in absent]
        if miss:
            ok("%s: %s is not shown (its HAL lacks %s)" % (c, row, " ".join(miss)), row not in feats)
    # menu against the stock tree
    stock_only = {k for k, v in F["diff"].items() if v == "stock only"}
    z9x_only = {k for k, v in F["diff"].items() if v == "Z9X only"}
    has = lambda n: n in stock_only or n not in z9x_only
    used = set()
    for key, ns in node_map:
        row, _, opt = key.partition(":")
        shown = row in feats and (not opt or opt in (feats[row].get("options") or []))
        used.update(ns)
        if shown:
            ok("%s: %s shown, the stock tree has %s" % (c, key, " or ".join(ns)), any(has(n) for n in ns))
    for n in sorted(stock_only - used):
        ok("%s: stock node %s is a gap row or a shown row" % (c, n), n in F["gap"])
    # restore rows
    cat = conf(os.path.join(G, "devices", c, "catalog.conf")) or {}
    ok("%s: restore_file = the stock update_bin %s" % (c, fact.get("update_bin")), cat.get("restore_file") == fact.get("update_bin"))
    for item in cat.get("restore_files", "").split():
        v, _, url = item.split(":", 2)[0], None, item.split(":", 2)[2]
        ok("%s: restore file %s is a vendor_firmware version" % (c, v), v in dev.get("vendor_firmware", "").split())
        ok("%s: restore url of %s is the update_bin in the firmware folder" % (c, v),
           url.endswith("/" + fact.get("update_bin", "?")) and fact.get("folder", "?") in url)
    want = " ".join("%s:%s>%s" % (code, v, v) for v in dev.get("vendor_firmware", "").split())
    ok("%s: restore_stock %s maps each stock to itself" % (c, cat.get("restore_stock")), cat.get("restore_stock") == want)
    # remotes
    for vid in fact.get("vendor_remotes", "").split():
        ok("%s: vendor remote %s has a remotes.tsv row listing %s" % (c, vid, code),
           any(r[3] == vid and code in r[9].split(",") for r in remotes))
    # scope
    if fact.get("scope") == "trial":
        ok("%s: trial only: no updates, no channel, no installer.conf" % c,
           dev.get("ota_engine") == "none" and dev.get("channel") == "none"
           and not os.path.isfile(os.path.join(G, "devices", c, "installer.conf")))
ok("stock facts read for %d models" % models, models > 0)
print("stock_facts: %d failed" % len(bad))
sys.exit(1 if bad else 0)
