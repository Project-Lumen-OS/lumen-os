#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""stock_facts_gmpf.py [TREE]: every gmpf (X50 family) model's data against the stock it follows. A model's facts are the research/stock_v*.tsv
files of its folder and of its family folders (tools/devices/layered.py: a firmware line's file holds the firmware facts
of one official build, the model's file its settings tree, feature.xml and hardware rows); a model with no such file is
not checked here. Each rule ties one data file of the model (effective menu profile, signal panel, wall colour, casting,
remotes, HAL hash rule, phone remote, CEC table, descriptors, restore file) to the stock fact it follows, so a row the
stock does not have, or a stock row that went missing, turns it red. One rule set for every model, no model named.
Columns of a facts file: kind (fact, tree, feature, hw), key, value, evidence. Tree keys: iris, memc, color_temp,
picture_modes (the menu's options in the stock order), absent (value: the menu row whose stock node the model's tree
lacks) and extra (value: a stock node or feature Lumen does not offer yet, with its reason). Mutants: stock_facts_gmpf_test.py. Usage: stock_facts_gmpf.py [TREE] (default: this repository).
"""
import glob
import json
import os
import re
import sys

G = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import layered  # noqa: E402
import menu_layers  # noqa: E402

bad = []


def ok(what, cond):
    print(("ok   " if cond else "FAIL ") + what)
    if not cond:
        bad.append(what)


def read(p):
    with open(os.path.join(G, p), encoding="utf-8") as f:
        return f.read()


def rows(p):
    return [l.split("\t") for l in read(p).splitlines() if l.strip() and not l.startswith("#")]


def kv(p):
    return dict(l.split("=", 1) for l in read(p).splitlines() if "=" in l and not l.startswith("#")) if p else {}


def java_consts(text):
    return dict(re.findall(r'static final String (\w+) = "([^"]*)";', text))


HASH_JAVA = read("apps/core/lumen-hal/src/org/lumen/hal/GmpfHash.java")
MODEL_JAVA = read("apps/core/lumen-hal/src/org/lumen/hal/GmpfModel.java")
FIRMWARE_JAVA = read("apps/core/lumen-hal/src/org/lumen/hal/GmpfFirmware.java")
CONSTS = java_consts(HASH_JAVA)
RULES = {n: [CONSTS.get(a.strip().replace("GmpfHash.", ""), a.strip()) for a in args.split(",")]
         for n, args in re.findall(r"static final HashRule (\w+) = new HashRule\(([^)]*)\)", HASH_JAVA)}
REMOTES = [r for r in rows("devices/_remotes/remotes.tsv") if len(r) > 9]
VCONTROL = {r[0]: r for r in rows("devices/_control/vcontrol.tsv")}
CEC = [l.split() for l in read("devices/_image/cec_stock.tsv").splitlines() if l.startswith("dump ")]
checked = 0
for dc in sorted(glob.glob(os.path.join(G, "devices", "*", "device.conf"))):
    c = os.path.basename(os.path.dirname(dc))
    dirs = layered.dirs(dc)
    files = sorted(f for d in dirs for f in glob.glob(os.path.join(d, "research", "stock_v*.tsv")))
    if not files:
        continue
    checked += 1
    dev = layered.effective(dc)
    bconf = layered.effective(os.path.join(G, "devices", c, "build.conf"))
    model = dev["models"].split()[0]
    fw = {}          # version -> facts of that firmware (a line file, or a model file with its own firmware)
    tree, feature, hw, mfact = {}, {}, {}, {}
    absent, extra = [], []
    for f in files:
        own = os.path.dirname(os.path.dirname(f)) == os.path.join(G, "devices", c)
        rs = rows(os.path.relpath(f, G))
        facts = {r[1]: r[2] for r in rs if r[0] == "fact"}
        if "fingerprint" in facts:
            fw[facts["fingerprint"].split("/")[4].split(":")[0]] = facts
        if not own:
            continue
        mfact.update(facts)
        for r in rs:
            if r[0] == "tree" and r[1] == "absent":
                absent.append(r[2])
            elif r[0] == "tree" and r[1] == "extra":
                extra.append(r[2])
            elif r[0] in ("tree", "feature", "hw"):
                {"tree": tree, "feature": feature, "hw": hw}[r[0]][r[1]] = r[2:]
    pre = "%s (%s): " % (c, model)
    menu = menu_layers.effective(os.path.join(G, "devices", c, "menu.json"))
    feats = menu.get("features", {})
    opts = lambda k: (feats.get(k) or {}).get("options")
    on = lambda k: feature.get(k, ["-"])[0] in ("true", "1")
    shown = lambda r: r in feats            # a row is on iff the effective profile has it (off removes it, sections only order)
    # firmware: every stock version of the registry has its facts, its hash is a GmpfHash value the model's rule takes
    ok(pre + "vendor_firmware %s = the firmware facts %s" % (dev["vendor_firmware"], sorted(fw)),
       sorted(dev["vendor_firmware"].split()) == sorted(fw))
    m = re.search(r'\w+\("%s", "%s", GmpfHash\.(\w+), "([^"]*)"\)' % (c, model), MODEL_JAVA)
    ok(pre + "a GmpfModel row", m is not None)
    rule = RULES.get(m.group(1), []) if m else []
    for ver, ff in sorted(fw.items()):
        h = ff.get("gmpf_hash", "")
        got = rule[rule.index(ver) + 1] if ver in rule[1::2] else (rule[0] if rule else None)
        ok(pre + "%s gmpf hash %s is the GmpfHash value its rule %s gives on that firmware" % (ver, h[:8], m.group(1) if m else "-"),
           h in CONSTS.values() and got == h)
        ok(pre + "%s boot, vendor and vbmeta read from the official build" % ver,
           all(len(ff.get(k, "")) == 64 for k in ("boot_sha256", "vendor_sha256", "vbmeta_sha256")))
        cec = [r for r in CEC if r[1] == "%s-%s" % (model, ver) or (c in r[2].split(",") and r[1].endswith("-" + ver))]
        ok(pre + "%s CEC defaults read from this framework-res" % ver,
           any(r[3] == ff.get("framework_res_sha256") and c in r[2].split(",") for r in cec))
        if ver == dev["vendor_firmware"].split()[0]:
            vn, vc = (ff.get("vcontrol") or mfact.get("vcontrol", "- -")).split()[:2]
            ok(pre + "stock phone remote %s %s of the recommended firmware" % (vn, vc), model in VCONTROL and VCONTROL[model][1] == vc and VCONTROL[model][2] == vn)
        # PFM 107: the shift page flag on the old tables, lens memory and isKeystoneAdjusted missing there
        tbl = [r for r in (dev.get("hal_codes", "").split() + dev.get("hal_codes_also", "").split()) if ver.replace(".", "") in r]
        if tbl:
            t = read(tbl[0])
            old107 = re.search(r"^ProjectorFocusManager\t107\tgetUitiltShiftSwithStatus", t, re.M) is not None
            ok(pre + "%s PFM 107 %s: GmpfFirmware SHIFT_PAGE_107 %s" % (ver, "getUitiltShiftSwithStatus" if old107 else "other", "names it" if old107 else "does not"),
               (re.search(r"SHIFT_PAGE_107\(([^)]*)\)", FIRMWARE_JAVA).group(1).find(
                   next((k for k, v in CONSTS.items() if v == h), "?")) >= 0) == old107)
    src = feature.get("support_human_detect_source_type", ["-"])[0]
    ok(pre + "detection inside the vendor iff support_human_detect_source_type 4 (no detector service), none at 0",
       m is not None and (src in ("4", "0")) == (m.group(2) == ""))
    for t in mfact.get("vendor_remotes", next((ff.get("vendor_remotes") for ff in fw.values() if ff.get("vendor_remotes")), "")).split():
        ok(pre + "remote %s of vendor/etc/remote.txt lists %s" % (t, model), any(r[3] == t and model in r[9].split(",") for r in REMOTES))
    # the settings tree
    for key, feat in (("iris", "picture.iris"), ("memc", "picture.memc"), ("color_temp", "picture.color_temp"), ("picture_modes", "picture.mode")):
        if key in tree:
            want = tree[key][1].split()
            # the Dolby Vision pair stands in the list only while Dolby Vision plays (its own stock nodes, the rule below)
            ok(pre + "menu %s = stock %s %s" % (feat, tree[key][0], want), [t for t in (opts(feat) or []) if t not in ("dv_bright", "dv_dark")] == want)
    for r in absent:
        ok(pre + "row %s off: its stock node is not in the tree" % r, not shown(r))
    # feature.xml
    if feature:
        rc = feature.get("real_cinema_mode", ["-"])[0]
        ok(pre + "motion compensation original frame iff real_cinema_mode", ("original" in (opts("picture.memc") or [])) == (rc not in ("-", "false", "0")))
        bb = feature.get("support_brightness_boost", ["-"])[0]
        ok(pre + "no Boost row (support_brightness_boost %s), a stock Boost Lumen lacks is a tree extra row brightness_boost with its reason" % bb,
           "boost" not in (opts("picture.mode") or []) and not shown("boost")
           and (bb in ("0", "-") or "brightness_boost" in extra))
        ok(pre + "HDMI mode Compatible and Enhanced (edid_2_version_val 1)",
           feature.get("edid_2_version_val", ["-"])[0] != "1" or opts("hdmi.edid") == ["hdmi_1_4", "hdmi_2_0"])
        ok(pre + "eye protection one switch (human detect type 1), no row without a detection source (0)",
           "eye" not in feats if src == "0" else feature.get("support_human_detect_type", ["-"])[0] != "1" or opts("eye") == ["off", "on"])
        ok(pre + "senior colour row iff support_elder_color", ("eye.elder_color" in feats) == on("support_elder_color"))
        ok(pre + "Dolby Vision bright / dark in the picture mode list and the screen page iff support_dolby_vision",
           all(t in (opts("picture.mode") or []) for t in ("dv_bright", "dv_dark")) == on("support_dolby_vision")
           and ("picture.dv_screen.size" in feats) == on("support_dolby_vision"))
        ok(pre + "DTS Virtual:X iff support_dts_vx_effect", ("dts_vx" in (opts("sound.enhancement") or [])) == on("support_dts_vx_effect"))
        ok(pre + "no optical output unless support_spdif", on("support_spdif") or "spdif" not in (opts("sound.output") or []))
        ok(pre + "zoom row iff support_optical_zoom", shown("zoom") == on("support_optical_zoom"))
        ok(pre + "screen displacement iff support_pantilt_type", shown("zoom.move") == on("support_pantilt_type"))
        if "supported_rainbow_reduction" in feature:
            ok(pre + "rainbow reduction row iff supported_rainbow_reduction", shown("picture.anti_rbe") == on("supported_rainbow_reduction"))
        gm = menu.get("game_menu", {})
        ok(pre + "VRR only at top speed iff support_vrr_only_at_top_speed", gm.get("vrr_top_speed") is on("support_vrr_only_at_top_speed"))
        ok(pre + "game menu high frame rate iff support_hfr", ("hfr" in gm.get("items", [])) == on("support_hfr"))
        sigp = layered.find(dc, "signal.tsv")
        sig = {r[0]: r for r in rows(os.path.relpath(sigp, G)) if r[0] != "item"} if sigp else {}
        for item, key in (("filmmaker", "support_filmmaker"), ("imax", "support_imax"), ("freesync", "support_amd_free_sync_premium"),
                          ("hgig", "hgig_mode"), ("dv_mode", "support_dolby_vision")):
            ok(pre + "signal row %s iff %s" % (item, key), (item in sig) == on(key))
        if "hdmi_map" in hw:
            mm = re.search(r"ports=([0-9:,]+)", sig.get("hdmi_format", ["", ""])[1])
            ok(pre + "signal HDMI ports = stock tbl_Hdmi_Hardware_Mapping %s" % hw["hdmi_map"][0], bool(mm) and mm.group(1) == hw["hdmi_map"][0])
            ok(pre + "HDMI inputs = the edid14 files", hw["hdmi_ports"][0] == str(len(hw["hdmi_map"][0].split(","))))
        wc = kv(os.path.relpath(layered.find(dc, "wallcolor.conf"), G)) if layered.find(dc, "wallcolor.conf") else {}
        ok(pre + "wall colour conf of this model", wc.get("model") == model)
        ok(pre + "wall colour type = support_wall_color_adjust_type", wc.get("type") == feature.get("support_wall_color_adjust_type", ["-"])[0])
        ok(pre + "wall colour light sensor text = support_ambient_light", wc.get("ambient_light") == feature.get("support_ambient_light", ["false"])[0])
        if "camera_type" in hw:
            ok(pre + "wall colour camera = camera.ini CameraType", wc.get("camera", "").startswith("CameraType %s," % hw["camera_type"][0]))
    cast = kv(os.path.relpath(layered.find(dc, "cast.conf"), G)) if layered.find(dc, "cast.conf") else {}
    ok(pre + "AirPlay and Miracast (Wi-Fi Direct in the stock vendor)", cast.get("airplay") == "1" and cast.get("miracast") == "1")
    # descriptors: kernel of the stock build, model props of the stock file, the restore file of its own stock
    kb = bconf.get("var.KBOOT", "")
    ok(pre + "recipe kernel from the stock build (a dump's boot_b or the official boot.img)", kb in ("{DUMP}/boot_b.img.gz", "{DUMP}/img/boot.img"))
    if "model_prop_sha256" in mfact:
        mp = read("devices/%s/image/%s" % (c, bconf.get("var.MPROPF", "")))
        ok(pre + "model props from the stock file %s" % mfact["model_prop_sha256"][:8], mfact["model_prop_sha256"] in mp)
    if "product" in mfact:
        ok(pre + "var.PRODUCT = system.ini ProductName", bconf.get("var.PRODUCT") == mfact["product"])
    cat = os.path.join(G, "devices", c, "catalog.conf")
    if "restore_bin" in mfact and os.path.isfile(cat):
        cc = kv(os.path.relpath(cat, G))
        ok(pre + "restore file = system.ini UpdateBinName", cc.get("restore_file") == mfact["restore_bin"])
        ok(pre + "restore rows for every stock version", sorted(q.split(">")[0] for p in cc.get("restore_stock", "").split() for q in p.split(":", 1)[1].split(",")) == sorted(fw))
print("stock facts: %d models, %d failed" % (checked, len(bad)))
sys.exit(1 if bad or not checked else 0)
