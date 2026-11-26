#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""installer_catalog_test.py: the catalog of Lumen Installer against the published releases.

    python3 tools/devices/test/installer_catalog_test.py           the recorded snapshots (catalog_fixture*/)
    LI_CATALOG_LIVE=1 python3 tools/devices/test/installer_catalog_test.py   also GitHub now (read only)

Every published stable and beta of every projector appears with the right channel, way (trial / computer), files and
stock versions; a trial starts from the newest beta (a stable trial build only on stock versions no beta takes); every
trial build carries the reader of the projector's own vendor files, else the build stops; a model with no build is
absent; broken releases stop the build; the remote erofs lookups agree with mkfs.erofs images; the strict format and
the signature hold. Exit 0 only if all pass.
"""
import contextlib
import io
import copy
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(os.path.abspath(__file__)).parent
G = HERE.parents[2]
sys.path.insert(0, str(G / "tools" / "devices"))
import installer_catalog as ic  # noqa: E402

# what was published on 2026-11-15 after HB 1.0.10-beta16 (build --record), and on 2026-11-13, when the Z9X and HB
# betas had no reader of the projector's own vendor files yet (readers.json: lookups of those images, 2026-11-15)
FIX = HERE / "catalog_fixture_1115"
OLD = HERE / "catalog_fixture"
FAILED = []
GH = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/"


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILED.append(what)


def build_from(fix):
    return ic.build(serial=1, net=ic.Net(str(fix)))


def dies(fn, text=None):
    """fn stops the build (exit 2), with text in what it says when text is given"""
    err = io.StringIO()
    try:
        with contextlib.redirect_stderr(err):
            fn()
    except SystemExit as e:
        return e.code == 2 and (text is None or text in err.getvalue())
    return False


def builds(c, code):
    for e in c["models"]:
        if e["model"] == code:
            return {b["channel"]: b for b in e["builds"]}, e
    return None, None


# what was published on 2026-11-15 (FIX): model -> channel -> (version, tag, image, way, install, stock). The Z9X and
# the HB G0130 also have a published stable release (LEFT_OUT), which a trial no longer starts from.
Z9X_STOCK = ["v6.15.58", "v6.15.62", "v6.15.19"]
WANT = {
    "G0082": {"beta": ("1.0.11-beta9", "z9x-1.0.11-beta9", "lumen-os-1.0.11-beta9-nogms-system.img", "trial", True, Z9X_STOCK)},
    "G0130": {"beta": ("1.0.10-beta16", "z9x-hb-1.0.10-beta16", "lumen-os-hb-1.0.10-beta16-nogms-system.img", "trial", True,
                       ["v6.16.24", "v6.17.12"])},
    "G0148": {"beta": ("1.0.10-beta16", "z9x-hb-1.0.10-beta16", "lumen-os-hb-1.0.10-beta16-nogms-system.img", "trial", True, ["v6.17.12"])},
    "G0111": {"beta": ("1.0.10-beta8", "x50-1.0.10-beta8", "lumen-os-x50-1.0.10-beta8-nogms-system.img", "trial", True, ["v1.2.64", "v1.2.54"])},
    "G0100": {"beta": ("1.0.10-beta8", "x50max-1.0.10-beta8", "lumen-os-x50max-1.0.10-beta8-nogms-system.img", "trial", True,
                       ["v1.2.64", "v1.2.54"])},
    "G0155": {"beta": ("1.0.10-beta6", "rs30-1.0.10-beta6", "system_dsu.img", "trial", False, ["v1.1.42"])},
    "G0058": {"stable": ("1.0.10", "play6-1.0.10", "lumen-os-play6-1.0.10-nogms-system.img", "computer", False, ["v1.7.116", "v1.8.127"])},
}
LEFT_OUT = {"G0082": "z9x-1.0.10", "G0130": "z9x-hb-1.0.9"}


def published_cases(c, rels, exact):
    sizes = {(r["tag_name"], a["name"]): a["size"] for r in rels for a in r["assets"]}
    pre = {r["tag_name"]: r["prerelease"] for r in rels}
    for code, chans in WANT.items():
        got, e = builds(c, code)
        check(got is not None, "%s is in the catalog" % code)
        if got is None:
            continue
        check(set(got) == set(chans), "%s channels %s (want %s)" % (code, sorted(got), sorted(chans)))
        for ch, (v, tag, img, way, inst, stock) in chans.items():
            b = got.get(ch)
            if not b:
                continue
            t = b["image"][len(GH):].split("/")[0]
            if exact:
                check(b["version"] == v and t == tag and b["image"] == GH + tag + "/" + img, "%s %s is %s (%s)" % (code, ch, v, b["image"]))
                check(b["install"] == inst, "%s %s install from the trial %s" % (code, ch, inst))
            check(b["way"] == way and b["edition"] == "nogms", "%s %s way %s" % (code, ch, way))
            check(pre.get(t) == (ch == "beta"), "%s %s: the release %s is a %s" % (code, ch, t, "pre-release" if ch == "beta" else "release"))
            check(b["size"] == sizes.get((t, b["image"].rsplit("/", 1)[1])) and b["sums"] == GH + t + "/SHA256SUMS",
                  "%s %s size and SHA256SUMS of the release" % (code, ch))
            check(b["stock"] == stock, "%s %s stock %s" % (code, ch, b["stock"]))
    for code in ("G0118",):
        check(builds(c, code)[0] is None, "%s (no build published) is not in the catalog" % code)
    if exact:
        for code, tag in LEFT_OUT.items():
            check(pre.get(tag) is False and "stable" not in (builds(c, code)[0] or {}),
                  "%s: the stable release %s is published, a trial starts from the beta" % (code, tag))
    tags = [b["image"][len(GH):].split("/")[0] for e in c["models"] for b in e["builds"]]
    check(not any(x in t for t in tags for x in ("kst", "diag", "play6-beta1")), "test builds (kst, diag) are never builds: %s" % tags)


def files_cases(c, t):
    """catalog.json (Lumen Installer 1.0.3: G codes only) and catalog-v2.json (1.0.4: every capital letter), one build,
    each file checked against its own live serial"""
    ch = c
    v1, v2 = ic.for_file(ch, 1), ic.for_file(ch, 2)
    check([e["model"] for e in v1["models"]] == [e["model"] for e in c["models"] if e["model"][0] == "G"] and v1["serial"] == v2["serial"]
          and [e["model"] for e in v2["models"] if e["model"][0] != "G"] == ["H0062", "H0063"] and v2 == c,
          "files: catalog.json leaves the H0062 and H0063 rows out, catalog-v2.json keeps them, one serial")
    for what, x, v, good in (("H rows in catalog.json", ch, 1, False), ("H rows in catalog-v2.json", ch, 2, True),
                             ("catalog.json without them", v1, 1, True)):
        try:
            ic.strict(x, v)
            check(good, "strict %s: taken" % what)
        except ValueError:
            check(not good, "strict %s: refused" % what)
    for code in ("h0062", "GH062", "H00620", "H062"):
        x = copy.deepcopy(v2)
        x["models"][[e["model"] for e in x["models"]].index("H0062")]["model"] = code
        try:
            ic.strict(x, 2)
            check(False, "strict catalog-v2.json refuses the model code %r" % code)
        except ValueError:
            check(True, "strict catalog-v2.json refuses the model code %r" % code)
    # both files from one build, the published names, the same rows while no other letter is published
    out = t / "out"
    check(ic.main(["x", "build", "--serial", "50", "--from", str(FIX), "--out-dir", str(out)]) == 0
          and json.loads((out / "catalog.json").read_text()) == dict(v1, serial=50)
          and json.loads((out / "catalog-v2.json").read_text()) == dict(c, serial=50),
          "build --out-dir: catalog.json (G rows) and catalog-v2.json (every row), serial 50")
    check(ic.main(["x", "check", str(out / "catalog-v2.json"), "--from", str(FIX)]) == 0
          and ic.main(["x", "check", str(out / "catalog.json"), "--from", str(FIX)]) == 0, "check: each file against a fresh build")
    # the signature and the strict rules of each file: an H row verifies only as catalog-v2.json
    for name in ("catalog.json", "catalog-v2.json"):
        f = t / "sv" / name
        f.parent.mkdir(exist_ok=True)
        f.write_bytes(ic.dump(dict(ch, serial=51)))
        ic.main(["x", "sign", str(f), "--key", str(t / "k.pk8")])
        rc = ic.main(["x", "verify", str(f), str(f) + ".sig", str(t / "k.x509.pem")])
        check(rc == (0 if name == "catalog-v2.json" else 1), "verify %s with an H row: rc %d" % (name, rc))
    g = t / "sv" / "any.json"
    g.write_bytes(ic.dump(dict(ch, serial=51)))
    ic.main(["x", "sign", str(g), "--key", str(t / "k.pk8")])
    check(ic.main(["x", "verify", str(g), str(g) + ".sig", str(t / "k.x509.pem"), "--v2"]) == 0, "verify --v2 under any name")
    # check reads each file with its own rows (a tree whose build has the H0062 row)
    real = ic.build
    ic.build = lambda serial=None, net=None: dict(copy.deepcopy(ch), serial=int(serial))
    try:
        k = t / "chk"
        k.mkdir()
        (k / "catalog-v2.json").write_bytes(ic.dump(dict(ch, serial=70)))
        (k / "catalog.json").write_bytes(ic.dump(dict(v1, serial=70)))
        (k / "h.json").write_bytes(ic.dump(dict(ch, serial=70)))
        rc = [ic.main(["x", "check", str(k / n)] + a) for n, a in (("catalog-v2.json", []), ("catalog.json", []), ("h.json", ["--v2"]),
                                                                    ("h.json", []))]
        check(rc == [0, 0, 0, 1], "check: catalog-v2.json with the H0062 row, catalog.json without it, --v2 by flag: %s" % rc)
    finally:
        ic.build = real

    # serial rules per file, each against its own live copy
    live = t / "live"
    live.mkdir()

    def put(v, serial, edit=None):
        x = dict(copy.deepcopy(ic.for_file(c, v)), serial=serial)
        if edit:
            edit(x)
        (live / ic.FILES[v]).write_bytes(ic.dump(x))

    def run(serial, *outs):
        try:
            return ic.main(["x", "build", "--serial", str(serial), "--from", str(FIX), "--live", str(live)] + list(outs))
        except SystemExit as e:
            return e.code
    o1, o2 = ["--out", str(t / "o1.json")], ["--out-v2", str(t / "o2.json")]
    put(1, 60)
    put(2, 60)
    check(run(60, *o1, *o2) == 0, "live: the same rows at the same serial are taken")
    check(run(59, *o1) == 2 and run(59, *o2) == 2, "live: an older serial stops the build, per file")
    put(2, 60, lambda x: x["models"].pop())
    check(run(60, *o2) == 2 and run(61, *o2) == 0, "live: changed rows need a newer serial")
    put(1, 10)
    put(2, 90)
    check(run(50, *o1) == 0 and run(50, *o2) == 2 and run(95, *o1, *o2) == 0,
          "live: each file against its own serial (catalog.json 10, catalog-v2.json 90)")


# the TITAN betas (published 2026-11-13, in FIX): code, release, image sha256, device folder
TITAN = (("H0062", "XGIMI TITAN Noir Pro", "titanpro-1.0.10-beta1", "6bdf34a6bdf2b0634dbedf4878ad906c752c6622d8aea66f1ae80ab186f900ea", "h0062"),
         ("H0063", "XGIMI TITAN Noir Max", "titanmax-1.0.10-beta1", "6a561173a8c9730a37d08518f3c4c2b2da68a52fc41c3fd67d3063e645dad373", "h0063"))


def titan_cases(c, t):
    """the TITAN Noir Pro and Max (model codes H0062, H0063): rows in catalog-v2.json only, trial only on both stock
    versions of device.conf, each release verified with its own model's certificates from catalog.conf"""
    v1, v2 = ic.for_file(c, 1), ic.for_file(c, 2)
    check([e["model"] for e in v1["models"]] == [e["model"] for e in c["models"] if e["model"][0] == "G"],
          "TITAN: catalog.json is the build without them")
    check(sorted(e["model"] for e in v2["models"] if e["model"][0] != "G") == ["H0062", "H0063"],
          "TITAN: catalog-v2.json = the G rows plus H0062 and H0063")
    for code, name, tag, sha, folder in TITAN:
        got, e = builds(v2, code)
        pems = [(G / "devices" / folder / "certs" / n).read_text() for n in ("ota.x509.pem", "ota_next.x509.pem")]
        stock = [{"version": "v1.2.99", "install": False}, {"version": "v1.2.54", "install": False}, {"version": "v1.3.38", "install": False}]
        check(e is not None and e["name"] == name and e["board"] == "mt9681" and e["platform"] == "mt5877" and e["product"] is None
              and not e["config_check"] and e["stock"] == stock and e["certs"] == pems and "refused" not in e,
              "TITAN %s entry: board, no product check, trial only on v1.2.99, v1.2.54 and v1.3.38, its own certificates" % code)
        b = (got or {}).get("beta")
        check(set(got or {}) == {"beta"} and b["version"] == "1.0.10-beta1" and b["image"] == GH + tag + "/system_dsu.img"
              and b["size"] == 1021317120 and b["sha256"] == sha and b["sums"] == GH + tag + "/SHA256SUMS" and b["way"] == "trial"
              and b["install"] and not b["clears_leftovers"] and b["stock"] == ["v1.2.99", "v1.2.54", "v1.3.38"],
              "TITAN %s beta 1.0.10-beta1 from %s" % (code, tag))
    try:
        ic.strict(v2, 2)
        check(True, "TITAN: catalog-v2.json with both rows passes the strict format")
    except ValueError as x:
        check(False, "TITAN: catalog-v2.json with both rows passes the strict format: %s" % x)
    # each TITAN release is signed with its own model's key: the Max's SHA256SUMS under the Pro's tag stops the build
    d = t / "titan_swap"
    shutil.copytree(FIX, d)
    for n in ("SHA256SUMS", "SHA256SUMS.sig"):
        shutil.copy(d / "files" / TITAN[1][2] / n, d / "files" / TITAN[0][2] / n)
    check(dies(lambda: build_from(d)), "TITAN: the Max's signed SHA256SUMS under the Pro's release stops the build")


def index_cases(t):
    """installer_index and dsu_revisions read model codes of any capital letter"""
    import installer_index as ii
    import dsu_revisions as dr
    old = {m["model"]: m for m in ii.build(serial=1)["models"]}
    check(old.get("G0118", {}).get("status") == "not_yet",
          "devices.json (Lumen Installer 1.0 to 1.0.2 read it): G0118 listed as not yet: %s" % old.get("G0118"))
    conf = t / "installer.conf"
    conf.write_text("status=supported\nrefused_H0062_v1.2.64_en=The X50 Ultra firmware\nrefused_H0062_v1.2.64_ru=Прошивка X50 Ultra\n")
    check(not dies(lambda: ii.read_conf(conf)) and ii.read_conf(conf).get("refused_H0062_v1.2.64_en") == "The X50 Ultra firmware",
          "installer.conf: refused_ keys of an H model code")
    conf.write_text("refused_h0062_v1.2.64_en=x\n")
    check(dies(lambda: ii.read_conf(conf)), "installer.conf: a lower-case model code is an unknown key")
    rev = t / "rev" / "apps" / "LumenDsuInstall" / "src" / "org" / "lumen" / "dsuinstall" / "Revisions.java"
    rev.parent.mkdir(parents=True)
    rev.write_text("\n".join((
        'row("titanpro", "H0062", TITAN, "1.2.99", RevisionVerdict.DRY, "Product_dianahypro", X50_BLOBS_SET,',
        'row("titanpro", "H0062", TITAN, ANY, RevisionVerdict.REFUSE, null, null,',
        'row("x50", "G0111", X50, "1.2.64", RevisionVerdict.LIVE, "Product_dianapro", X50_BLOBS_SET,')) + "\n")
    g, r = ii.G, dr.REV
    ii.G, dr.REV = t / "rev", rev
    try:
        got = ii.table("titanpro")
    except SystemExit as x:
        got = "exit %s" % x.code
    finally:
        ii.G, dr.REV = g, r
    check(got == [("dry", "H0062", "v1.2.99", "Product_dianahypro"), ("refuse", "H0062", None, None)],
          "in-trial table rows of an H model code with their products: %s" % (got,))


# the way back (catalog.conf restore_*): model -> stock -> (official version, size), the guides and release notes
WAY_BACK = {
    "G0082": {"v6.15.19": ("v6.15.19", 2589266736), "v6.15.58": ("v6.15.62", 2656047920),
              "v6.15.62": ("v6.15.62", 2656047920)},
    "G0130": {"v6.16.24": ("v6.16.24", 2160464496), "v6.17.12": ("v6.17.12", 2178814576)},
    "G0148": {"v6.17.12": ("v6.17.12", 2178814576)},
    "G0058": {"v1.7.116": ("v1.7.116", 2051543712), "v1.8.127": ("v1.8.127", 2115834528)},
    "G0111": {"v1.2.64": ("v1.2.64", 2693993312), "v1.2.54": ("v1.2.54", 2693731168), "v1.3.38": ("v1.3.38", 2867991104)},
    "G0100": {"v1.2.54": ("v1.2.54", 2693731168), "v1.2.64": ("v1.2.64", 2693993312), "v1.3.38": ("v1.3.38", 2867991104)},
    "H0062": {"v1.2.99": ("v1.2.99", 2519405360), "v1.3.38": ("v1.3.38", 2734101008)},
    "H0063": {"v1.2.99": ("v1.2.99", 2519405360), "v1.3.38": ("v1.3.38", 2734101008)},
}
STICKS = {"G0082": "XGIMI_HORSEPOPULAR_XR03D.bin", "G0130": "XGIMI_GOATELEGANT_XR03D.bin",
          "G0148": "XGIMI_GOATELEGANT_XR03D.bin", "G0058": "XGIMI_DRAGONHEALTHY_XN03C.bin",
          "G0111": "XGIMI_HORSEPOPULAR_XR04E.bin", "G0100": "XGIMI_HORSEPOPULAR_XR03E.bin",
          "H0062": "XGIMI_HORSEPOPULAR_XR04EOS.bin", "H0063": "XGIMI_HORSEPOPULAR_XR03EOS.bin"}


def restore_cases(c):
    got = {e["model"]: {f["stock"]: (f["version"], f["size"]) for f in e["restore"]["files"]}
           for e in c["models"] if "restore" in e}
    check(got == WAY_BACK, "way back: every model's stock versions with the official file each goes back with: %s" % got)
    check({e["model"]: e["restore"]["file"] for e in c["models"] if "restore" in e} == STICKS,
          "way back: the stick file name of each model")
    check(all(f["url"].startswith(ic.XGIMI_FIRMWARE + "release/") and f["url"].endswith("/" + e["restore"]["file"])
              for e in c["models"] if "restore" in e for f in e["restore"]["files"]),
          "way back: every file on XGIMI's release server under the stick name")
    check(all("restore" not in e for e in c["models"] if e["model"] in ("G0155",)),
          "way back: no official file for the models without a known one (the screen shows the guide)")
    # the same files as the guides and the computer installer
    docs = {m: (G / "docs/install" / g / "en.md").read_text(encoding="utf-8")
            for m, g in (("G0082", "z9x"), ("G0130", "hb"), ("G0148", "hb"), ("G0058", "play6"))}
    check(all(f["url"] in docs[e["model"]] for e in c["models"] if e["model"] in docs for f in e["restore"]["files"]),
          "way back: every official address of the Z9X, HB and Play 6 is the one their guide links")
    conf = (G / "installer/lib/devices.conf").read_text(encoding="utf-8")
    play6 = next(e for e in c["models"] if e["model"] == "G0058")["restore"]
    check("play6.RESTORE_BIN=" + play6["file"] in conf and all(
        "%s:%s" % (f["version"].upper(), f["url"]) in conf for f in play6["files"]),
          "way back: the Play 6 files are the computer installer's RESTORE_BIN and RESTORE_URL")
    check("x50.RESTORE_BIN=" + STICKS["G0111"] in conf, "way back: the X50 Ultra stick name is the computer installer's")
    check(all(ic.stock_key(f["version"]) >= ic.stock_key(f["stock"])
              for e in c["models"] if "restore" in e for f in e["restore"]["files"]),
          "way back: no stock goes back with an older file (the stick may refuse it)")

    d = {"codename": "x", "models": ["G0082"], "vendor_firmware": ["v6.15.58", "v6.15.62", "v6.15.19", "v6.15.9"]}
    stick = "XGIMI_HORSEPOPULAR_XR03D.bin"

    def at(v, built, name=stick):
        return "%srelease/A/B/X_%s_%s/%s" % (ic.XGIMI_FIRMWARE, v, built, name)
    url, url19 = at("6.15.62", "20260325-154151"), at("6.15.19", "20251014-012201")
    ok = {"restore_file": stick, "restore_files": "v6.15.62:5:" + url, "restore_stock": "G0082:v6.15.58>v6.15.62"}
    check(ic.restore_of(d, ok)["G0082"] == {"file": stick, "files": [
        {"stock": "v6.15.58", "version": "v6.15.62", "size": 5, "url": url}]}, "restore_of: one model, one row")
    check(ic.restore_of(d, {}) == {}, "restore_of: none without the keys")
    by_number = {"restore_files": "v6.15.10:5:" + at("6.15.10", "20260101-000000"), "restore_stock": "G0082:v6.15.9>v6.15.10"}
    check(not dies(lambda: ic.restore_of(d, dict(ok, **by_number))),
          "restore_of: versions compare by number (V6.15.10 is newer than V6.15.9)")
    both = "v6.15.19:5:%s v6.15.62:6:%s" % (url19, url)
    check([r["version"] for r in ic.restore_of(d, dict(ok, restore_files=both, restore_stock="G0082:v6.15.19>v6.15.19,"
                                                                                          "v6.15.62>v6.15.62"))["G0082"]["files"]]
          == ["v6.15.19", "v6.15.62"], "restore_of: each stock with its own file")
    for what, edit in (
            ("only some of the keys", {"restore_stock": ""}),
            ("an odd stick name", {"restore_file": "../XGIMI_X.bin"}),
            ("a stick name of another kind", {"restore_file": "update.bin",
                                              "restore_files": "v6.15.62:5:" + at("6.15.62", "20260325-154151", "update.bin")}),
            ("another file name in the address", {"restore_files": "v6.15.62:5:" + url.replace("XR03D", "XR04E")}),
            ("an address off XGIMI's server", {"restore_files": "v6.15.62:5:https://example.com/X_6.15.62_20260325-154151/" + stick}),
            ("an address with ..", {"restore_files": "v6.15.62:5:" + url.replace("/A/", "/../")}),
            ("size 0", {"restore_files": "v6.15.62:0:" + url}),
            ("no size", {"restore_files": "v6.15.62:" + url}),
            ("a file twice", {"restore_files": "v6.15.62:5:%s v6.15.62:6:%s" % (url, url)}),
            ("a model of another projector", {"restore_stock": "G0130:v6.15.58>v6.15.62"}),
            ("a stock the projector never had", {"restore_stock": "G0082:v6.15.50>v6.15.62"}),
            ("a version without a file", {"restore_stock": "G0082:v6.15.58>v6.15.19"}),
            ("a stock twice", {"restore_stock": "G0082:v6.15.58>v6.15.62,v6.15.58>v6.15.62"}),
            ("a file older than the stock", {"restore_files": "v6.15.19:5:" + url19, "restore_stock": "G0082:v6.15.58>v6.15.19"}),
            ("a newer file while the stock has its own", {"restore_files": both, "restore_stock": "G0082:v6.15.19>v6.15.62"}),
            ("an address without the version", {"restore_files": "v6.15.62:5:" + url19}),
            ("an address without a build time", {"restore_files": "v6.15.62:5:" + url.replace("_20260325-154151", "")}),
            ("a higher version built earlier", {"restore_files": "v6.15.19:5:%s v6.15.62:6:%s"
                                                                  % (at("6.15.19", "20260401-000000"), url),
                                                "restore_stock": "G0082:v6.15.19>v6.15.19"})):
        check(dies(lambda: ic.restore_of(d, dict(ok, **edit))), "restore_of refuses " + what)
    z = copy.deepcopy(next(e for e in c["models"] if e["model"] == "G0082"))
    for what, edit in (("a file off XGIMI's server", lambda r: r["files"][0].update(url="https://example.com/x/" + r["file"])),
                       ("a string size", lambda r: r["files"][0].update(size="5")),
                       ("a stock twice", lambda r: r["files"].append(dict(r["files"][0]))),
                       ("an odd stick name", lambda r: r.update(file="x.bin")),
                       ("a file older than its stock", lambda r: r["files"][1].update(version="v6.15.19"))):
        x = copy.deepcopy(c)
        e = next(e for e in x["models"] if e["model"] == "G0082")
        edit(e["restore"])
        try:
            ic.strict(x, 2)
            ok_strict = False
        except ValueError:
            ok_strict = True
        check(ok_strict, "strict refuses a way back with " + what)
    check(z["restore"]["files"][1]["stock"] == "v6.15.58", "strict: the real way back passes")


def trial_cases(c, t):
    """a trial starts from the newest beta, and only from a build whose image carries the reader of the projector's
    own vendor files (the shared one or the X50 family's older one), else the build stops"""
    def b(ch, way, *stock):
        return {"channel": ch, "way": way, "version": ch, "stock": list(stock)}
    for what, got, want in (
            ("stable and beta on the same stock versions: the beta only",
             [b("stable", "trial", "v1", "v2"), b("beta", "trial", "v1", "v2")], [b("beta", "trial", "v1", "v2")]),
            ("a stable on stock versions no beta takes: the stable for those only",
             [b("stable", "trial", "v1", "v2", "v3"), b("beta", "trial", "v2")], [b("stable", "trial", "v1", "v3"), b("beta", "trial", "v2")]),
            ("a stable only", [b("stable", "trial", "v1")], [b("stable", "trial", "v1")]),
            ("a beta only", [b("beta", "trial", "v1")], [b("beta", "trial", "v1")]),
            ("computer builds keep both", [b("stable", "computer", "v1"), b("beta", "computer", "v1")],
             [b("stable", "computer", "v1"), b("beta", "computer", "v1")]),
            ("a computer beta leaves a trial stable", [b("stable", "trial", "v1"), b("beta", "computer", "v1")],
             [b("stable", "trial", "v1"), b("beta", "computer", "v1")])):
        before = copy.deepcopy(got)
        out = ic.trial_from_beta(got)
        check(out == want and got == before, "trial from the beta: %s: %s" % (what, out))

    class Net:
        def __init__(self, ans):
            self.ans, self.asked = ans, []

        def reader(self, tag, name, sha):
            self.asked.append(tag)
            return self.ans.get(tag, "")

    def entry(*bs):
        return {"model": "G0000", "builds": [{"channel": ch, "way": way, "version": "1.0." + tag[-1], "sha256": "0" * 64,
                                              "image": GH + tag + "/x.img"} for ch, way, tag in bs]}
    n = Net({"t-b": "shared reader"})
    check(not dies(lambda: ic.check_readers(entry(("stable", "computer", "t-c"), ("beta", "trial", "t-b")), n)) and n.asked == ["t-b"],
          "reader guard: a trial build with the shared reader passes, a computer build is not looked up: %s" % n.asked)
    check(not dies(lambda: ic.check_readers(entry(("beta", "trial", "t-x")), Net({"t-x": "x50 reader"}))),
          "reader guard: the X50 family's older reader passes")
    check(dies(lambda: ic.check_readers(entry(("stable", "trial", "t-s"), ("beta", "trial", "t-b")), Net({"t-b": "shared reader"})),
               "G0000: stable 1.0.s (t-s) has no reader"), "reader guard: a stable trial build without the reader stops the build")
    check(dies(lambda: ic.check_readers(entry(("beta", "trial", "t-n")), Net({})), "G0000: beta 1.0.n (t-n) has no reader"),
          "reader guard: a beta trial build without the reader stops the build")

    # the snapshot: every trial build was looked up and has a reader, computer builds and left-out stables never are
    rd = json.loads((FIX / ic.READERS).read_text())
    trial = {x["sha256"] for e in c["models"] for x in e["builds"] if x["way"] == "trial"}
    other = {x["sha256"] for e in c["models"] for x in e["builds"] if x["way"] != "trial"}
    check(trial and set(rd) == trial and all(rd[h] for h in trial) and not other & set(rd),
          "snapshot: readers.json holds the trial builds only, each with a reader: %s" % sorted(set(rd.values())))
    check(sorted(rd[x["sha256"]] for e in c["models"] if e["model"][0] == "H" for x in e["builds"]) == ["x50 reader", "x50 reader"],
          "snapshot: the TITAN betas carry the X50 family's older reader")

    def with_readers(src, name, edit):
        d = t / name
        shutil.copytree(src, d)
        r = json.loads((d / ic.READERS).read_text())
        edit(r)
        (d / ic.READERS).write_text(json.dumps(r))
        return d
    sha = {e["model"]: x["sha256"] for e in c["models"] for x in e["builds"]}
    d = with_readers(FIX, "rd_none", lambda r: r.update({sha["G0082"]: ""}))
    check(dies(lambda: build_from(d), "G0082: beta 1.0.11-beta9 (z9x-1.0.11-beta9) has no reader"),
          "snapshot: the Z9X beta without the reader stops the build")
    d = with_readers(FIX, "rd_gone", lambda r: r.pop(sha["G0130"]))
    check(dies(lambda: build_from(d), "no recorded vendor file reader lookup"), "snapshot: a trial build never looked up stops the build")
    left = {"c236c1fb14877eb1c61fd414393f326c87aadcaade770244ed340033a982b0b9": "",
            "67fc9430c38c205ff7fa88bca118f09d2b09907f3979e2457d830aedba03ba90": ""}
    d = with_readers(FIX, "rd_left", lambda r: r.update(left))
    try:
        same = build_from(d) == c
    except SystemExit:
        same = False
    check(same, "snapshot: the left-out stables Z9X 1.0.10 and HB 1.0.9 (no reader, looked up 2026-11-15) are no trial builds")
    # 2026-11-13: the betas a trial then started from had no reader
    check(dies(lambda: build_from(OLD), "G0130: beta 1.0.10-beta12 (z9x-hb-1.0.10-beta12) has no reader"),
          "2026-11-13 snapshot: the HB 1.0.10-beta12 trial without the reader stops the build")
    hb12 = "19e934c08dbad1cbbe8fe21cbda4b61ab54570efccb4a7afe20e754a667d783f"
    d = with_readers(OLD, "old_hb", lambda r: r.update({hb12: "shared reader"}))
    check(dies(lambda: build_from(d), "G0082: beta 1.0.11-beta3 (z9x-1.0.11-beta3) has no reader"),
          "2026-11-13 snapshot: the Z9X 1.0.11-beta3 trial without the reader stops the build")
    d = with_readers(OLD, "old_ok", lambda r: r.update({k: "shared reader" for k, v in r.items() if not v}))
    try:
        o = build_from(d)
        got = {(e["model"], x["channel"]): x["version"] for e in o["models"] for x in e["builds"]}
    except SystemExit as x:
        got = "exit %s" % x.code
    check(got == {("G0058", "stable"): "1.0.7", ("G0058", "beta"): "1.0.10-beta4", ("G0082", "beta"): "1.0.11-beta3",
                  ("G0100", "beta"): "1.0.10-beta3", ("G0111", "beta"): "1.0.10-beta3", ("G0130", "beta"): "1.0.10-beta12",
                  ("G0148", "beta"): "1.0.10-beta12", ("G0155", "beta"): "1.0.10-beta1"},
          "2026-11-13 snapshot with readers: Z9X and HB from the beta, Play 6 (computer) keeps both: %s" % (got,))
    # the app's host tests of the Version choice (apps/LumenInstaller/test/run.sh): every newest build, no lookup
    try:
        a = ic.build(serial=1, net=ic.Net(str(with_readers(OLD, "old_none", lambda r: r.clear()))), trial_rules=False)
        got = {(e["model"], x["channel"]): x["version"] for e in a["models"] for x in e["builds"] if e["model"] in ("G0082", "G0130")}
    except SystemExit as x:
        got = "exit %s" % x.code
    check(got == {("G0082", "stable"): "1.0.10", ("G0082", "beta"): "1.0.11-beta3", ("G0130", "stable"): "1.0.9",
                  ("G0130", "beta"): "1.0.10-beta12"},
          "without the trial rules (the app's Version choice tests): stable and beta, no reader looked up: %s" % (got,))


def reader_cases(t, mk):
    """the reader lookup over HTTP ranges against mkfs.erofs images, its readers.json cache and record"""
    imgs = {}
    for name, files in (("shared", ("system/etc/z9x/z9x_blobs.sh", "system/etc/z9x/blobs.conf")),
                        ("x50", ("system/etc/z9x/x50_blobs.sh", "system/etc/z9x/blobs_stock_dsu.sh")),
                        ("both", ("system/etc/z9x/blobs_stock_dsu.sh", "system/etc/z9x/blobs.conf")),
                        ("none", ("system/etc/z9x/z9x_blobs.sh", "system/etc/init/z9x_blobs.rc")),
                        ("noprop", ("system/etc/z9x/blobs.conf",))):
        src = t / ("rd_" + name)
        for f in files + (() if name == "noprop" else ("system/build.prop",)):
            (src / f).parent.mkdir(parents=True, exist_ok=True)
            (src / f).write_text(f + "\n")
        subprocess.run([mk, "-zlz4hc", "-T", "1230768000", str(t / (name + ".img")), str(src)], check=True, capture_output=True)
        imgs[name] = (t / (name + ".img")).read_bytes()
    imgs["junk"] = bytes(70000)
    reads = []

    def rng(url, off, n):
        reads.append(url)
        return imgs[url.rsplit("/", 1)[1]][off:off + n]
    net = ic.Net(cache=str(t / "rc" / "handover.json"), record=str(t / "rrec"))
    net._range = rng
    names = ("shared", "x50", "both", "none")
    got = {k: net.reader("tag-" + k, k, "%064x" % i) for i, k in enumerate(names)}
    check(got == {"shared": "shared reader", "x50": "x50 reader", "both": "shared reader", "none": ""}
          and all(u.startswith(GH + "tag-") for u in reads), "reader over ranges: shared, X50 family, both, none: %s" % got)
    for k in ("junk", "noprop"):
        check(dies(lambda: net.reader("tag-" + k, k, "f" * 64), "cannot be read"), "reader over ranges: %s image stops the build" % k)
    want = {"%064x" % i: got[k] for i, k in enumerate(names)}
    check(json.loads((t / "rc" / ic.READERS).read_text()) == want and json.loads((t / "rrec" / ic.READERS).read_text()) == want,
          "readers.json next to the cache and in the record: the four answers, nothing for the unreadable images")
    again = ic.Net(cache=str(t / "rc" / "handover.json"))
    again._range = lambda url, off, n: (_ for _ in ()).throw(OSError("read again"))
    check(again.reader("tag-x50", "x50", "%064x" % 1) == "x50 reader", "readers.json answers the next run without reading the image")


def with_app(t, name, assets):
    """the snapshot plus app files on X50 releases: {(tag, file name): (bytes, size the release lists)}"""
    d = t / name
    shutil.copytree(FIX, d)
    rels = json.loads((d / "releases.json").read_text())
    for (tag, n), (data, size) in assets.items():
        r = [x for x in rels if x["tag_name"] == tag]
        if not r:
            r = [{"tag_name": tag, "prerelease": True, "draft": False, "assets": []}]
            rels.append(r[0])
        r[0]["assets"].append({"name": n, "size": size})
        (d / "files" / tag).mkdir(parents=True, exist_ok=True)
        (d / "files" / tag / n).write_bytes(data)
    (d / "releases.json").write_text(json.dumps(sorted(rels, key=lambda x: x["tag_name"])))
    return d


def app_update_cases(c, t):
    """app_update of catalog.conf (Lumen Installer 1.0.6): the X50 row comes from the newest x50 release that carries
    the file, package and version code read from the APK, which must be signed with the X50 platform certificate"""
    plat = ic.apkinfo.der_sha256_of_pem(str(G / "devices" / "g0111" / "certs" / "platform.x509.pem"))
    fake = {"facts": {"package": "org.lumen.updater", "versionCode": 2}, "signer": plat}
    real = (ic.apkinfo.manifest_bytes, ic.apkinfo.signer_sha256)
    ic.apkinfo.manifest_bytes = lambda b: dict(fake["facts"])
    ic.apkinfo.signer_sha256 = lambda b: fake["signer"]
    apk = b"PK fake updater 2"
    name3 = "lumen-updater-x50-1.0.10-beta3.apk"
    try:
        x = build_from(with_app(t, "app_none", {("x50-1.0.10-beta3", "other.apk"): (apk, len(apk))}))
        check(x == c, "app update: no release carries the file, no row, the catalog as before")
        x = build_from(with_app(t, "app_b3", {("x50-1.0.10-beta3", name3): (apk, len(apk))}))
        rows = {e["model"]: e.get("app_updates") for e in x["models"]}
        want = [{"package": "org.lumen.updater", "version_code": 2, "apk": GH + "x50-1.0.10-beta3/" + name3, "size": len(apk),
                 "sha256": ic.hashlib.sha256(apk).hexdigest(), "signer": plat, "lumen": ["1.0.10-beta5", "1.0.10-beta6"]}]
        check(rows.get("G0111") == want and all(v is None for k, v in rows.items() if k != "G0111"),
              "app update: the G0111 row from x50-1.0.10-beta3, no other model gets one: %s" % rows.get("G0111"))
        for v in (1, 2):
            try:
                ic.strict(ic.for_file(x, v), v)
                check(True, "app update: the row passes the strict format of file %d" % v)
            except ValueError as e:
                check(False, "app update: the row passes the strict format of file %d: %s" % (v, e))
        newer = b"PK fake updater 3"
        fake["facts"]["versionCode"] = 3
        x = build_from(with_app(t, "app_b9", {("x50-1.0.10-beta3", name3): (apk, len(apk)),
                                               ("x50-1.0.10-beta9", "lumen-updater-x50-1.0.10-beta9.apk"): (newer, len(newer))}))
        a = [e for e in x["models"] if e["model"] == "G0111"][0]["app_updates"][0]
        check(a["apk"].endswith("/x50-1.0.10-beta9/lumen-updater-x50-1.0.10-beta9.apk") and a["version_code"] == 3
              and a["sha256"] == ic.hashlib.sha256(newer).hexdigest(), "app update: the newest release that carries the file: %s" % a["apk"])
        fake["facts"]["versionCode"] = 2
        for what, edit in (("another signer", lambda: fake.update(signer="0" * 64)),
                           ("no package", lambda: fake["facts"].update(package=None)),
                           ("an odd package", lambda: fake["facts"].update(package="Org.Lumen")),
                           ("version code 0", lambda: fake["facts"].update(versionCode=0))):
            keep = (dict(fake["facts"]), fake["signer"])
            edit()
            check(dies(lambda: build_from(with_app(t, "app_bad_" + what.replace(" ", "_"), {("x50-1.0.10-beta3", name3): (apk, len(apk))}))),
                  "app update: %s stops the build" % what)
            fake["facts"], fake["signer"] = keep
        check(dies(lambda: build_from(with_app(t, "app_size", {("x50-1.0.10-beta3", name3): (apk, len(apk) + 1)}))),
              "app update: a file of another size than the release lists stops the build")
        ic.apkinfo.signer_sha256 = real[1]
        check(dies(lambda: build_from(with_app(t, "app_unsigned", {("x50-1.0.10-beta3", name3): (apk, len(apk))}))),
              "app update: a file that is no signed APK stops the build")
    finally:
        ic.apkinfo.manifest_bytes, ic.apkinfo.signer_sha256 = real
    conf = t / "catalog.conf"
    for line, good in (("app_update=u-{version}.apk 1.0.10-beta5", True), ("app_update=u.apk 1.0.10-beta5", False),
                       ("app_update=u-{version}.zip 1.0.10-beta5", False), ("app_update=u-{version}.apk", False),
                       ("app_update=u-{version}.apk beta5", False), ("app_update=u-{version}.apk 1.0.10-beta5 1.0.10-beta5", False),
                       ("app_update=u-{version}-{version}.apk 1.0.10-beta5", False)):
        conf.write_text("tag=x50-{version}\nimage=i-{version}.img\n" + line + "\n")
        check(dies(lambda: ic.read_conf(conf)) != good, "catalog.conf %s: %s" % (line, "taken" if good else "refused"))
    row = {"package": "org.lumen.updater", "version_code": 2, "apk": GH + "x50-1.0.10-beta7/u.apk", "size": 10, "sha256": "a" * 64,
           "signer": "b" * 64, "lumen": ["1.0.10-beta5"]}
    for k, v in (("apk", "http://github.com/u.apk"), ("apk", GH + "x/u.zip"), ("size", 0), ("size", (64 << 20) + 1), ("sha256", "xyz"),
                 ("signer", "B" * 64), ("package", "updater"), ("version_code", 0), ("version_code", "2"), ("lumen", []),
                 ("lumen", ["beta5"]), ("lumen", ["1.0.10-beta5", "1.0.10-beta5"])):
        y = copy.deepcopy(c)
        [e for e in y["models"] if e["model"] == "G0111"][0]["app_updates"] = [dict(row, **{k: v})]
        try:
            ic.strict(y, 2)
            check(False, "strict refuses an app row with %s=%r" % (k, v))
        except ValueError:
            check(True, "strict refuses an app row with %s=%r" % (k, v))
    real_apk = os.environ.get("LI_REAL_UPDATER_APK")
    if real_apk:
        b = Path(real_apk).read_bytes()
        f = ic.apkinfo.manifest_bytes(b)
        check(f["package"] == "org.lumen.updater" and isinstance(f["versionCode"], int) and ic.apkinfo.signer_sha256(b) == plat,
              "a real X50 LumenUpdater: package, version code %r, the X50 platform certificate" % f["versionCode"])


def main():
    rels = json.loads((FIX / "releases.json").read_text())
    c = build_from(FIX)
    c1 = ic.for_file(c, 1)
    ic.strict(c1)
    ic.strict(c, 2)
    published_cases(c, rels, True)
    _, z = builds(c, "G0082")
    check(z["stock"] == [{"version": "v6.15.58", "install": True}, {"version": "v6.15.62", "install": True},
                         {"version": "v6.15.19", "install": False}] and z["product"] == "Product_dapeng" and not z["config_check"],
          "G0082 stock rows from the in-trial table (6.15.19 trial only), product")
    _, h48 = builds(c, "G0148")
    clears = {(e["model"], b["channel"]): b["clears_leftovers"] for e in c["models"] for b in e["builds"]}
    check(clears == {("G0082", "beta"): True, ("G0130", "beta"): True, ("G0148", "beta"): True, ("G0111", "beta"): False,
                     ("G0100", "beta"): False, ("G0155", "beta"): False, ("G0058", "stable"): False, ("H0062", "beta"): False,
                     ("H0063", "beta"): False},
          "leftovers cleared by the Z9X and HB betas only: %s" % clears)
    check(h48["refused"][0]["version"] == "v6.16.24" and "6.17.12" in h48["refused"][0]["message"]["en"] and h48["config_check"],
          "G0148 on 6.16.24 refused with its text")
    _, rs = builds(c, "G0155")
    check(rs["board"] == "mt9681" and rs["platform"] == "mt5877" and rs["product"] is None and len(rs["certs"]) == 2,
          "G0155 entry: board, platform, its own two certificates")
    check(json.loads(ic.dump(c)) == c and ic.dump(build_from(FIX)) == ic.dump(c), "the build is stable (same snapshot, same bytes)")
    want = ["https://stats.lumen-os.net/lumen/v1/report", "https://prompt.az/lumen/v1/report"]
    check(ic.report_urls() == want and c.get("reports") == want,
          "report addresses: our server straight first, the old Cloudflare address after it: %s" % c.get("reports"))
    restore_cases(c)

    with tempfile.TemporaryDirectory() as t:
        def variant(edit):
            d = Path(t) / ("v%d" % len(os.listdir(t)))
            shutil.copytree(FIX, d)
            r = json.loads((d / "releases.json").read_text())
            edit(d, r)
            (d / "releases.json").write_text(json.dumps(r))
            return d

        def rel(r, tag):
            return [x for x in r if x["tag_name"] == tag][0]

        d = variant(lambda d, r: shutil.copy(d / "files/play6-1.0.10/SHA256SUMS.sig", d / "files/z9x-1.0.11-beta9/SHA256SUMS.sig"))
        check(dies(lambda: build_from(d)), "a release whose SHA256SUMS is signed with another key stops the build")
        d = variant(lambda d, r: (d / "files/rs30-1.0.10-beta6/SHA256SUMS").write_text("0" * 64 + "  system_dsu.img\n"))
        check(dies(lambda: build_from(d)), "SHA256SUMS changed after signing stops the build")
        d = variant(lambda d, r: r.remove(rel(r, "z9x-hb-1.0.10-beta16")))
        check(dies(lambda: build_from(d), "the beta channel manifest names 1.0.10-beta16"),
              "a channel manifest naming another build than the newest release stops the build")

        def no_image(d, r):
            for x in r:
                if x["tag_name"].startswith("rs30-"):
                    x["assets"] = [a for a in x["assets"] if a["name"] != "system_dsu.img"]
        d = variant(no_image)
        cc = build_from(d)
        check(builds(cc, "G0155")[0] is None, "a release without its image is not a build (RS30 then absent)")

        def odd_size(d, r):
            for a in rel(r, "rs30-1.0.10-beta6")["assets"]:
                if a["name"] == "system_dsu.img":
                    a["size"] += 512
        d = variant(odd_size)
        check(dies(lambda: build_from(d)), "an image that is not whole 4 KiB blocks stops the build")

        def draft(d, r):
            for x in r:
                if x["tag_name"].startswith("rs30-"):
                    x["draft"] = True
        d = variant(draft)
        check(builds(build_from(d), "G0155")[0] is None, "a draft release is never a build")

    check([ic.version_key(v) for v in ("1.0.9", "1.0.10-beta1", "1.0.10-beta12", "1.0.10-rc1", "1.0.10", "1.0.11-beta3")]
          == sorted(ic.version_key(v) for v in ("1.0.9", "1.0.10-beta1", "1.0.10-beta12", "1.0.10-rc1", "1.0.10", "1.0.11-beta3")),
          "version order: 1.0.10-beta12 after beta1, rc after beta, final after rc")

    bad = [("unknown way", lambda x: x["models"][0]["builds"][0].update(way="usb")),
           ("plain http image", lambda x: x["models"][0]["builds"][0].update(image="http://example.org/a.img")),
           ("build stock outside the model", lambda x: x["models"][0]["builds"][0]["stock"].append("v9.9.9")),
           ("install on a computer build", lambda x: [b.update(install=True) for e in x["models"] for b in e["builds"] if b["way"] == "computer"]),
           ("model twice", lambda x: x["models"].append(copy.deepcopy(x["models"][0]))),
           ("odd size", lambda x: x["models"][0]["builds"][0].update(size=4097)),
           ("no builds", lambda x: x["models"][0].update(builds=[])),
           ("other kind", lambda x: x.update(kind="lumen-installer-devices")),
           ("clears leftovers without install", lambda x: [b.update(clears_leftovers=True) for e in x["models"] for b in e["builds"] if not b["install"]])]
    for what, edit in bad:
        x = copy.deepcopy(c1)
        edit(x)
        try:
            ic.strict(x)
            check(False, "strict format refuses: " + what)
        except (ValueError, KeyError, TypeError):
            check(True, "strict format refuses: " + what)

    with tempfile.TemporaryDirectory() as t:
        t = Path(t)
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(t / "k.pem")], check=True,
                       capture_output=True)
        subprocess.run(["openssl", "pkcs8", "-topk8", "-inform", "PEM", "-outform", "DER", "-nocrypt", "-in", str(t / "k.pem"),
                        "-out", str(t / "k.pk8")], check=True)
        subprocess.run(["openssl", "req", "-new", "-x509", "-key", str(t / "k.pem"), "-days", "2", "-subj", "/CN=t", "-out",
                        str(t / "k.x509.pem")], check=True, capture_output=True)
        f = t / "catalog.json"
        f.write_bytes(ic.dump(c1))
        ok = ic.main(["x", "sign", str(f), "--key", str(t / "k.pk8")]) == 0
        ok = ok and ic.main(["x", "verify", str(f), str(f) + ".sig", str(t / "k.x509.pem")]) == 0
        f.write_bytes(ic.dump(c1).replace(b'"v1.1.42"', b'"v1.1.43"'))
        check(ok and ic.main(["x", "verify", str(f), str(f) + ".sig", str(t / "k.x509.pem")]) != 0, "sign and verify, a changed catalog fails")
        files_cases(c, t)
        titan_cases(c, t)
        app_update_cases(c, t)
        index_cases(t)
        trial_cases(c, t)

        if shutil.which("mkfs.erofs"):
            reader_cases(t, shutil.which("mkfs.erofs"))
            for name, inst in (("plain", 0), ("handover", 1)):
                src = t / ("src_" + name)
                (src / "system/etc/init").mkdir(parents=True)
                (src / "system/etc/many").mkdir(parents=True)
                (src / "system/build.prop").write_text("ro.lumen.version=1\n")
                for i in range(300):
                    (src / ("system/etc/many/file_with_a_long_name_%03d.txt" % i)).write_text("x%d" % i)
                if inst:
                    (src / "system/priv-app/LumenDsuInstall").mkdir(parents=True)
                    (src / "system/priv-app/LumenDsuInstall/LumenDsuInstall.apk").write_text("PK")
                    (src / "system/etc/init/lumen_dsu_install.rc").write_text("on x\n")
                img = t / (name + ".img")
                subprocess.run(["mkfs.erofs", "-zlz4hc", "-T", "1230768000", str(img), str(src)], check=True, capture_output=True)
                data = img.read_bytes()
                reads = []
                got = ic.handover(lambda off, n: reads.append(n) or data[off:off + n])
                check(got == inst, "erofs over ranges: %s image -> %d (%d reads, %d bytes)" % (name, got, len(reads), sum(reads)))
                e = ic.Erofs(lambda off, n: data[off:off + n])
                check(e.exists("/system/etc/many/file_with_a_long_name_299.txt") and not e.exists("/system/etc/many/file_with_a_long_name_300.txt"),
                      "erofs: a 300-entry directory over several blocks")
            check(ic.handover(lambda off, n: bytes(n)) == -1, "erofs: not an erofs image -> cannot tell")
        else:
            check(False, "mkfs.erofs missing (erofs lookup not tested)")

    if os.environ.get("LI_CATALOG_LIVE") == "1":
        with tempfile.TemporaryDirectory() as t:
            net = ic.Net(record=t, cache=str(Path(t) / "h.json"))
            live = ic.build(serial=1, net=net)
            ic.strict(live, 2)
            published_cases(live, json.loads((Path(t) / "releases.json").read_text()), False)

    print("%d failed" % len(FAILED))
    for f in FAILED:
        print("  - " + f)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
