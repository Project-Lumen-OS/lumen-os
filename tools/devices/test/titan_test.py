#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""titan_test.py: the data of the XGIMI TITAN Noir Pro and Max (devices/h0062, devices/h0063,
the design notes).

Per model: the registry entry, its own identity apart from the X50 Ultra / Ultra Max, the other TITAN and the Z9X, the
in-trial installer's rows (check and dry run only, never LIVE), the X50 family table and its hash rule, the menu
profile (the stock dianahypro / dianahymax tree: the X50 Ultra's rows on the Pro, the Ultra Max's on the Max, the
TITAN picture modes on both), the v1.2.99 code table, the model properties, the build descriptor of the X50 family
recipe, and the installer list and catalog: neither code is listed while installer_index.OTHER_LETTER_CODES is off
(Lumen Installer 1.0.3 refuses a whole catalog with a code of another letter), and once on each catalog entry is trial
only on every stock version, install closed. Read only, no network.

    python3 tools/devices/test/titan_test.py
"""
import json
import os
import re
import sys
from pathlib import Path

HERE = Path(os.path.abspath(__file__)).parent
G = HERE.parents[2]
sys.path.insert(0, str(G / "tools" / "devices"))
sys.path.insert(0, str(G / "devices" / "g0111" / "image"))
sys.path.insert(0, str(HERE))
import devices as registry  # noqa: E402
import dsu_revisions  # noqa: E402
import installer_catalog as ic  # noqa: E402
import installer_index as ix  # noqa: E402
import x50_family  # noqa: E402

# codename, model, installer id, stock product, X50 counterpart (its HumanDetect.ini and laser tables), GmpfModel row,
# update file of xgimiconfig/<model>/system.ini, sha256 of the stock vendor/etc/xgimimodelname/<model>/build.prop
TITANS = (("h0061", "H0061", "titannoir", "Product_dianahy", "g0111", "TITAN_NOIR", "XGIMI_HORSEPOPULAR_XR06EOS.bin",
           "59f03b0e0958df6c4b5ef3a172489d61ad4289ab0c062fafe364f545df8795b3"),
          ("h0062", "H0062", "titanpro", "Product_dianahypro", "g0111", "TITAN_NOIR_PRO", "XGIMI_HORSEPOPULAR_XR04EOS.bin",
           "a75145a19fb35f7133ecbbbc66946b973577c95ca2a896d39a778c84344f9daf"),
          ("h0063", "H0063", "titanmax", "Product_dianahymax", "g0100", "TITAN_NOIR_MAX", "XGIMI_HORSEPOPULAR_XR03EOS.bin",
           "e6ba90c3f999ad4aaf209f85076517de549cbc9402e0253e0372c9feb5f5f64b"))
TITAN_MODES = ["standard", "movie", "sport", "filmmaker", "isf_day", "isf_night", "performance"]
FAILED = []
HAL = G / "apps" / "core" / "lumen-hal" / "src" / "org" / "lumen" / "hal"


def check(ok, what):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        FAILED.append(what)


def rows_of(p):
    out = {}
    for line in p.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        f = line.split("\t")
        if f[0] == "service":
            continue
        out[(f[0], f[1])] = f[2:]
    return out


def registry_cases(c, mc, iid, prod, twin, row, upd, msha):
    root = str(G)
    errs, _ = registry.validate(root)
    check(not [e for e in errs if c in e], "the registry validates devices/%s: %s" % (c, [e for e in errs if c in e]))
    d = registry.get(root, c)
    check(d["family"] == "horse_popular" and d["models"] == [mc] and d["hardware"] == "mt9681" and d["platform"] == "mt5877",
          "%s: family horse_popular, model %s, mt9681 / mt5877 (the X50 platform)" % (c, mc))
    check(registry.identify(root, "horse_popular", mc)["codename"] == c, "(horse_popular, %s) is %s" % (mc, c))
    for m, cc in (("G0111", "g0111"), ("G0100", "g0100"), ("G0082", "horse_popular")):
        e = registry.identify(root, "horse_popular", m)
        check(e is not None and e["codename"] == cc, "(horse_popular, %s) is still %s" % (m, cc))
    for m in ("H0060", "H0064", mc.lower()):
        check(registry.identify(root, "horse_popular", m) is None, "%s names no entry" % m)
    others = [x for cc, x in registry.load(root).items() if cc != c]
    check(all(mc not in x["models"] for x in others), "no other entry holds " + mc)
    check(all(x[k] != d[k] for x in others for k in ("installer_id", "app_prefix", "keys_dir")), "own installer id, app prefix, keys")
    check(d["installer_id"] == iid and d["channel"] == [iid] and d["ota_wire_id"] == "none"
          and d["ota_engine"] == "none" and d["keys_dir"] == "~/.lumen-keys/" + c,
          "%s id and channel, no OTA, its own keys" % iid)
    check(d["vendor_firmware"] == ["v1.2.99", "v1.2.54"], "stock v1.2.99 (newest overseas) and v1.2.54")
    check("devices/h0062/research/aidl_codes_v1299.tsv" in d["hal_codes_also"], "the v1.2.99 code table is checked too")


def revisions_cases(c, mc, iid, prod, *_):
    rows = dsu_revisions.rows(iid)
    check(rows == [("dry", mc, "v1.2.99"), ("dry", mc, "v1.2.54"), ("refuse", mc, None)],
          "%s in-trial rows: DRY on v1.2.99 and v1.2.54, every other version refused: %s" % (mc, rows))
    t = ix.table(iid)
    check([r[3] for r in t] == [prod, prod, None], "the stock product of the rows that run: " + prod)
    check(all(r[0] != "live" for r in t), "no LIVE row: the install stays closed")
    fw = registry.get(str(G), c)["vendor_firmware"]
    check(all(r[2] in fw for r in rows if r[2]), "every row version is a registry stock version")
    for p in ("x50", "x50max", "z9x", "hb", "titanpro", "titanmax"):
        if p != iid:
            check(all(m != mc for _, m, _ in dsu_revisions.rows(p)), "%s is in no %s row" % (mc, p))


def hal_cases(c, mc, iid, prod, twin, row, *_):
    model = (HAL / "GmpfModel.java").read_text(encoding="utf-8")
    hashes = (HAL / "GmpfHash.java").read_text(encoding="utf-8")
    check('%s("%s", "%s", GmpfHash.TITAN_RULE, "hd_server2")' % (row, c, mc) in model,
          "the X50 family table names %s / %s with the TITAN rule and hd_server2 (HumanDetect.ini of %s)" % (c, mc, twin))
    check('TITAN_V1299 = "ad74d059b11cf5c5816cff11e285f8ec3bdc9270"' in hashes and 'TITAN_V1299_FIRMWARE = "v1.2.99"' in hashes,
          "the v1.2.99 hash, only on v1.2.99")
    t = G / "devices" / "h0062" / "research" / "aidl_codes_v1299.tsv"
    check("Interface hash ad74d059b11cf5c5816cff11e285f8ec3bdc9270" in t.read_text(encoding="utf-8"), "the table names its hash")
    a, b = rows_of(G / "devices" / "g0111" / "research" / "aidl_codes.tsv"), rows_of(t)
    check(all(b.get(k) == v for k, v in a.items()), "every X50 v1.2.64 row is the same row on v1.2.99")
    new = sorted(set(b) - set(a))
    check(len(a) == 1199 and len(b) == 1219 and len(new) == 20, "v1.2.99 = the 1199 rows plus 20: %d %d %d" % (len(a), len(b), len(new)))
    allowed = set()
    for f in ("allowed_x50eyeguard.tsv", "allowed_x50projector.tsv"):
        for line in (G / "devices" / "g0111" / "research" / f).read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#") and len(line.split("\t")) > 2:
                allowed.add(line.split("\t")[2])
    check(len(allowed) > 50 and not [k for k in new if b[k][0] in allowed], "no call of ours is one of the 20 new methods")


def menu_cases(c, mc, iid, prod, twin, *_):
    D = G / "devices" / c
    m = json.loads((D / "menu.json").read_text(encoding="utf-8"))
    mx = json.loads((G / "devices" / twin / "menu.json").read_text(encoding="utf-8"))
    check(m["device"] == c and m["model"] == mc and m["adapter"] == mx["adapter"], "menu profile of %s / %s, X50Features" % (c, mc))
    check(m["features"]["picture.mode"]["options"] == TITAN_MODES, "%s: picture modes of the stock TITAN tree" % c)
    a, b = dict(m["features"]), dict(mx["features"])
    a.pop("picture.mode")
    b.pop("picture.mode")
    # the original frame of motion compensation only where feature real_cinema_mode is 1 (the X50 Ultra and Ultra Max):
    # the TITAN v1.2.54 feature.xml lacks the key, so stock sends setOutputTiming(6) first, which Lumen does not port
    want = dict(b.pop("picture.memc", {}))
    want["options"] = [o for o in want.get("options", []) if o != "original"]
    check(a.pop("picture.memc", None) == want, "%s: motion compensation as on %s without the original frame" % (c, twin))
    # the stock TITAN trees (dianahypro, dianahymax) have no screensaver rows and no parental page, the X50 ones do
    # (stock-gaps-2, rs232-parental, X50FeaturesTest holds the same rule)
    for k in ("settings.screensaver_now", "settings.screensaver_delay", "settings.parental"):
        check(k in b and k not in a, "%s: no %s, as the stock TITAN tree (%s has it)" % (c, k, twin))
        b.pop(k, None)
    # Based on of a user picture mode lists the model's own stock modes (user-picture-modes, image_mode_custom_base_*)
    base = a.pop("picture.custom_base", {}).get("options", [])
    check(b.pop("picture.custom_base", None) is not None and base and set(base) <= set(TITAN_MODES),
          "%s: Based on lists TITAN picture modes only %s" % (c, base))
    # the hotspot name is each model's own (hotspot, 5b81d69f), everything else of the row as on the X50
    ha, hb = dict(a.pop("network.hotspot", {})), dict(b.pop("network.hotspot", {}))
    check(ha.pop("name", "") in ("Lumen TITAN Noir", "Lumen TITAN Noir Pro", "Lumen TITAN Noir Max") and hb.pop("name", None) and ha == hb,
          "%s: the hotspot row as on %s, with its own TITAN name" % (c, twin))
    check(a == b and m.get("hints") == mx.get("hints") and m.get("keys") == mx.get("keys"),
          "%s: every other row, hint and key as on its X50 counterpart %s" % (c, twin))
    check(all(k in a for k in ("lamp", "picture.iris", "picture.dynamic_black")),
          "%s: the light level, the iris and the dynamic black, as the stock tree has them (StockLight)" % c)
    check(a.get("picture.iris", {}).get("options") == ["dynamic", "f2", "f3", "f4", "f5_5", "f7"],
          "%s: the iris in the order of the stock node picture_iris_ring_adjustment_diana" % c)
    lay = (D / "app" / "X50Projector.layers").read_text(encoding="utf-8")
    check("devices/%s/menu.json assets/lumen_menu/menu.json" % c in lay and not re.search(r"devices/(g0100|g0111|h006[0-9])/menu", lay.replace("devices/%s/" % c, "")),
          "the menu app carries the %s profile" % c)


def image_cases(c, mc, iid, prod, twin, row, upd, msha):
    D = G / "devices" / c
    p = (D / "image" / ("%s_model_prop.txt" % c)).read_text(encoding="utf-8").splitlines()
    kv = [x for x in p if x and not x.startswith("#")]
    keys = [x.split("=", 1)[0] for x in kv]
    ident = {"ro.product.device", "ro.product.model", "ro.product.brand", "ro.product.manufacturer", "ro.product.board",
             "ro.product.name", "ro.build.product"}
    check(not (set(keys) & ident) and not [k for k in keys if k.startswith("persist.sys.")], "model props keep no identity key")
    check("ro.product.locale" not in keys, "%s: no language of its own (the shared first-start default)" % c)
    other = "dianahymax" if prod.endswith("pro") else "dianahypro"
    check("ro.xgimi.product.name=" + prod in kv and "tv.series.model=" + prod in kv
          and not [x for x in kv if re.search(r"diana(pro|max)\b", x) or other in x],
          "%s: its own product, never the X50 or the other TITAN one" % c)
    check(not [x for x in kv if x.startswith(("ro.z9x.", "ro.lumen."))], "no Z9X or Lumen identity in the stock model props")
    check(len(keys) == len(set(keys)) == 25, "25 lines, each key once: %d" % len(keys))
    pub = [x for x in (D / "image" / ("%s_prop_pub.txt" % c)).read_text(encoding="utf-8").splitlines() if x and not x.startswith("#")]
    for need in ("+ro.lumen.device=" + c, "+ro.lumen.model=" + mc, "ro.z9x.device=x50", "ro.lumen.version=%s-@V@" % iid,
                 "ro.z9x.build_id=lumen-%s-@V@-@BID@" % iid):
        check(need in pub, "%s prop file: %s" % (c, need))
    check(not [x for x in pub if re.search(r"adb\.tcp|adb\.secure=0|G0100|G0111|x50max|dianamax|dianapro", x)], "%s prop file: public, no X50 name" % c)
    fam = x50_family.members(str(G)).get(c)
    check(fam is not None and fam["model"] == mc and fam["prefix"] == iid and fam["keys"] == c and fam["remote_dir"] == iid + "-out",
          "%s takes the X50 family recipe with its own identity: %s" % (c, fam))
    conf = x50_family.read_conf(str(D / "build.conf"))
    check(conf.get("var.STOCK_MODEL_SHA") == msha and conf.get("var.MPROPF") == "%s_model_prop.txt" % c, "%s: the stock %s file (sha256)" % (c, mc))
    pem = D / "certs" / "platform.x509.pem"
    import base64
    import hashlib
    der = base64.b64decode("".join(x for x in pem.read_text().splitlines() if x and not x.startswith("-----")))
    check(conf.get("var.PCERT") == hashlib.sha256(der).hexdigest(), "%s: var.PCERT is its own platform certificate" % c)
    check(not list(D.rglob("*.pk8")) and not list(D.rglob("*.pem.key")), "%s: no private key in the tree" % c)
    others = [x50_family.read_conf(str(G / "devices" / o / "build.conf")).get("var.PCERT") for o in x50_family.members(str(G)) if o != c]
    check(conf.get("var.PCERT") not in others, "%s: no other X50 family model shares its platform certificate" % c)


def catalog_cases(c, mc, iid, prod, *_):
    D = G / "devices" / c
    conf = ic.read_conf(D / "catalog.conf")
    check(conf.get("tag") == iid + "-{version}" and conf.get("image") == "system_dsu.img"
          and conf.get("certs") == "devices/%s/certs/ota.x509.pem devices/%s/certs/ota_next.x509.pem" % (c, c),
          "%s catalog: its own tag, the trial copy, its own OTA certificates" % c)
    check(not (D / "installer.conf").exists(), "no installer.conf (trial only)")
    idx = ix.build(serial=1)
    check(all(e["model"] != mc for e in idx["models"]), "the old installer list (devices.json) has no " + mc)
    cat = ic.build(serial=1, net=ic.Net(str(HERE / "catalog_fixture")), trial_rules=False)
    v1, v2 = ic.for_file(cat, 1), ic.for_file(cat, 2)
    check(all(e["model"] != mc for e in v2["models"]), "nothing published in the fixture: catalog-v2.json has no " + mc)
    bad = json.loads(ic.dump(v1))
    bad["models"].append(dict(bad["models"][0], model=mc))
    check(raises(lambda: ic.strict(bad, 1)), "catalog.json (read by Lumen Installer 1.0.3) refuses a row " + mc)
    check(not raises(lambda: ic.strict(bad, 2)), "catalog-v2.json (Lumen Installer 1.0.4 and later) reads a row " + mc)
    for code in (mc.lower(), "HX0" + mc[3:], mc + "1", mc[1:]):
        b2 = json.loads(ic.dump(v1))
        b2["models"].append(dict(b2["models"][0], model=code))
        check(raises(lambda: ic.strict(b2, 2)), "catalog-v2.json still refuses %r" % code)
    d = registry.get(str(G), c)
    e = ic.entries_of(d, conf, ic.model_rows([d]), ix.installer_conf())
    check(len(e) == 1 and e[0]["model"] == mc and e[0]["board"] == "mt9681" and e[0]["platform"] == "mt5877",
          "one %s entry, board mt9681" % mc)
    check(e and e[0]["stock"] == [{"version": "v1.2.99", "install": False}, {"version": "v1.2.54", "install": False}],
          "trial only on v1.2.99 and v1.2.54, install closed: %s" % (e[0]["stock"] if e else None))
    check(e and len(e[0]["certs"]) == 2, "its two OTA certificates")


def raises(fn):
    try:
        fn()
    except (ValueError, AssertionError, SystemExit):
        return True
    return False


def main():
    for t in TITANS:
        for case in (registry_cases, revisions_cases, hal_cases, menu_cases, image_cases, catalog_cases):
            case(*t)
    print("titan_test: %s" % ("all passed" if not FAILED else "%d FAILED" % len(FAILED)))
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
