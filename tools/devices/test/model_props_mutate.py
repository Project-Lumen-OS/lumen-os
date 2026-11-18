#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""model_props_mutate.py: mutation check of the stock model props (tools/devices/model_props.py, the HB and Z9X data,
the recipe lines, the HB app's AI scene). Each mutant breaks one rule in a scratch copy of the files the test reads;
model_props_test.py must turn red, and the unmutated copy must pass. The stock dumps of the environment are used when
present (HB_V130 / HB_V148, Z9X_V61519 / Z9X_V61558 / Z9X_V61562), so the check-only mutants need them.
  python3 tools/devices/test/model_props_mutate.py   (MUT_ONLY="rc_ z_" to filter)"""
import os, shutil, subprocess, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
I = "devices/goat_elegant/image/"
Z = "devices/horse_popular/image/"
PY = "tools/devices/model_props.py"
RC, TSV, SE, FHM = I + "hb_model_props.rc", I + "hb_model_props.tsv", I + "hb_model_prop_dapengse.txt", I + "hb_model_prop_dapengfhm.txt"
ST, VT, BC = I + "steps.tsv", I + "verify_tree.py", "devices/goat_elegant/build.conf"
ZRC, ZTSV, ZP, ZST, ZBC = Z + "z9x_model_props.rc", Z + "z9x_model_props.tsv", Z + "z9x_model_prop_dapeng.txt", Z + "steps.tsv", \
    "devices/horse_popular/build.conf"
TRAP = "overlay/v1/z9x_trapezoid.rc"
HP = "apps/HbProjector/src/org/lumen/projector/panel/HbPictureRows.java"
DRV = "apps/core/lumen-aipq/src/org/lumen/aipq/AiPqDriver.java"
X50 = "devices/g0111/image/x50_props.py"
MUT = [
    # ---- the one loader
    ("rc_model_only", PY, "on early-init && property:ro.boot.xgimi.modelname=%s && property:ro.vendor.build.version.incremental=%s\"\n                      % (model, fw))",
     "on early-init && property:ro.boot.xgimi.modelname=%s\"\n                      % (model,))"),
    ("rc_boot_trigger", PY, "\"on early-init && property:ro.boot.xgimi.modelname", "\"on boot && property:ro.boot.xgimi.modelname"),
    ("rule_identity_ok", PY, "        if not x.model_rule(line):\n            die(", "        if False:\n            die("),
    ("rule_spaces_ok", PY, 'VALUE = re.compile(r"^[A-Za-z0-9_.:/+,@=-]+$")', 'VALUE = re.compile(r"^.+$")'),
    ("tbl_twice_ok", PY, "        if (model, fw) in seen:\n            die(", "        if False:\n            die("),
    ("tbl_any_lines", PY, "or lines not in LINES \\\n", "\\\n"),
    ("read_unchecked", PY, '        if lines == "read" and not reader:', '        if False:'),
    ("read_any_comment", PY, "reader = reader or (line.startswith(READ_BY) and len(line) > len(READ_BY))", "reader = True"),
    ("chk_pin_skipped", PY, "        if hashlib.sha256(raw).hexdigest() != sha:", "        if False:"),
    ("chk_rule_subset", PY, '        if lines == "rule" and have != want:', '        if lines == "rule" and not in_order(have, want):'),
    ("chk_read_any_order", PY, '        if lines == "read" and not in_order(have, want):', '        if lines == "read" and not set(have) <= set(want):'),
    ("chk_rc_unchecked", PY, "    if open(os.path.join(d, rc), encoding=\"utf-8\").read() != make_rc(d, name):", "    if False:"),
    ("chk_dump_any", PY, "        if dump not in dumps:\n            print(\"model_props: ERROR %s %s: dump %s not given\" % (model, fw, dump)); bad = 1; continue\n"
     "        stock = stock_file(dumps[dump], model)\n", "        stock = stock_file(dumps.get(dump, list(dumps.values())[0]), model)\n"),
    ("rule_shared_widened", X50, "return k is not None and k not in IDENTITY and not k.startswith((\"persist.sys.\",) + FIRST_START)",
     "return k is not None"),
    # ---- HB data and recipe
    ("hb_rc_hand_edit", RC, "    setprop tv.series.model Product_dapengfhm\n", "    setprop tv.series.model Product_dapengse\n"),
    ("hb_tbl_g0148_missing", TSV, "G0148\tv6.17.12\t", "G0148\tv6.16.24\t"),
    ("hb_tbl_g0130_old_missing", TSV, "G0130\tv6.16.24\thb_model_prop_dapengse.txt\t931b67bba055b3be438c0da71706c5625891e01bc7d2c4199f0c5c3b403f2cb4\tV130\trule\n", ""),
    ("hb_tbl_read", TSV, "\tV148\trule\nG0148", "\tV148\tread\nG0148"),
    ("hb_prop_no_hwc", FHM, "vendor.mstar.hwc.framebuffer.format=1\n", ""),
    ("hb_prop_no_hd_server", SE, "persist.vendor.xgimi.hd_server=2\n", ""),
    ("hb_prop_no_mem_policy", SE, "ro.xgimi.mem.policy=2G\n", ""),
    ("hb_prop_tv_scene", FHM, "ro.xgimi.default.aipq=film\n", "ro.xgimi.default.aipq=tv\n"),
    ("hb_prop_own_key", SE, "ro.xgimi.mem.policy=2G\n", "ro.xgimi.mem.policy=2G\nro.lumen.hb.clickcurve=1\n"),
    ("hb_prop_twice", SE, "ro.xgimi.mem.policy=2G\n", "ro.xgimi.mem.policy=2G\nro.xgimi.mem.policy=2G\n"),
    ("hb_app_no_ai_scene", HP, "org.lumen.projector.AiScenePolicy.afterOwnWrite(g, m.wire);", ""),
    ("ai_driver_other_prop", DRV, 'PROP_RO = "ro.xgimi.default.aipq"', 'PROP_RO = "ro.xgimi.aipq"'),
    ("hb_steps_no_check", ST, "lumen\trun\tmodel_props.py\tcheck\t{in}\thb_model_props.tsv\tV130={V130}\tV148={V148}\n", ""),
    ("hb_steps_no_install", ST, "lumen\tinstall\t0644\thb_model_props.rc\t{sys}/etc/init/hb_model_props.rc\n", ""),
    ("hb_steps_not_needed", ST, "\thb_model_prop_dapenghm.txt\thb_model_prop_dapengfhm.txt", "\thb_model_prop_dapengfhm.txt"),
    ("hb_conf_not_staged", BC, " devices/goat_elegant/image/hb_model_prop_dapengfhm.txt ", " "),
    ("hb_conf_rule_not_staged", BC, " devices/g0111/image/x50_props.py ", " "),
    ("hb_conf_loader_copy", BC, " tools/devices/model_props.py ", " devices/goat_elegant/image/hb_model_props.py tools/devices/model_props.py "),
    ("hb_verify_unknown_rc", VT, '        "system/etc/init/hb_model_props.rc": os.path.join(IN, "hb_model_props.rc"),\n', ""),
    # ---- Z9X data and recipe
    ("z_rc_hand_edit", ZRC, "incremental=v6.15.62\n    setprop ro.xgimi.product.name Product_dapeng\n",
     "incremental=v6.15.62\n    setprop ro.xgimi.product.name Product_dapengse\n"),
    ("z_tbl_6158_missing", ZTSV, "G0082\tv6.15.58\t", "G0082\tv6.15.59\t"),
    ("z_tbl_rule", ZTSV, "\tZ62\tread\n", "\tZ62\trule\n"),
    ("z_tbl_pin_swapped", ZTSV, "\t3ac178ea87d0d9e6167c5a148f090f1a3e2002a1fb7489e9185e888b959cf45c\tZ19\t",
     "\td858043dbfad702c834c04f58902a45972c4d808b76224ad45c00383393c7ab1\tZ19\t"),
    ("z_prop_no_product", ZP, "ro.xgimi.product.name=Product_dapeng\n", ""),
    ("z_prop_no_hwc", ZP, "vendor.mstar.hwc.framebuffer.format=1\n", ""),
    ("z_prop_mem_policy", ZP, "vendor.mstar.hwc.framebuffer.format=1\n", "vendor.mstar.hwc.framebuffer.format=1\n# read by x\nro.xgimi.mem.policy=2G\n"),
    ("z_prop_hd_server", ZP, "ro.xgimi.product.name=Product_dapeng\n", "ro.xgimi.product.name=Product_dapeng\n# read by x\npersist.vendor.xgimi.hd_server=2\n"),
    ("z_prop_trapezoid_twice", ZP, "vendor.mstar.hwc.framebuffer.format=1\n", "vendor.mstar.hwc.framebuffer.format=1\n# read by x\nvendor.xgimi.hwc.aa=true\n"),
    ("z_prop_aipq", ZP, "vendor.mstar.hwc.framebuffer.format=1\n", "vendor.mstar.hwc.framebuffer.format=1\n# read by x\nro.xgimi.default.aipq=film\n"),
    ("z_prop_no_reader", ZP, "# read by the vendor mtkhwcomposer", "# the vendor mtkhwcomposer"),
    ("z_prop_reorder", ZP, '# read by lumen-control in Z9xProjector: productName of the XGIMI phone remote reply (Replies, AndroidFacts); stock\n# answers Product_dapeng, Lumen answered nothing\nro.xgimi.product.name=Product_dapeng\n# read by the vendor mtkhwcomposer and audio.primary.mt5877 (VendorProperties mstar_hwc_framebuffer_format); the\n# Z9X /vendor/build.prop sets the same 1 before init, so this keeps the stock order of the file and changes nothing\nvendor.mstar.hwc.framebuffer.format=1\n', '# read by the vendor mtkhwcomposer and audio.primary.mt5877 (VendorProperties mstar_hwc_framebuffer_format); the\n# Z9X /vendor/build.prop sets the same 1 before init, so this keeps the stock order of the file and changes nothing\nvendor.mstar.hwc.framebuffer.format=1\n# read by lumen-control in Z9xProjector: productName of the XGIMI phone remote reply (Replies, AndroidFacts); stock\n# answers Product_dapeng, Lumen answered nothing\nro.xgimi.product.name=Product_dapeng\n'),
    ("z_trap_value", TRAP, "    setprop vendor.mstar.trapezoid.video.gop 0\n", "    setprop vendor.mstar.trapezoid.video.gop 1\n"),
    ("z_trap_line_lost", TRAP, "    setprop vendor.mstar.hwc.tcvideo.format 1\n", ""),
    ("z_steps_no_check", ZST, "*\trun\tmodel_props.py\tcheck\t{in}\tz9x_model_props.tsv\tZ19={Z19}\tZ58={Z58}\tZ62={Z62}\n", ""),
    ("z_steps_no_add", ZST, "*\ttar_add\tsystem/etc/init/z9x_model_props.rc\tz9x_model_props.rc\t644\n", ""),
    ("z_steps_no_readback", ZST, "*\ttar_same\t{tar}\tsystem/etc/init/z9x_model_props.rc\tz9x_model_props.rc\n", ""),
    ("z_steps_not_needed", ZST, "\tz9x_model_prop_dapeng.txt\tmodel_props.py", "\tmodel_props.py"),
    ("z_conf_not_staged", ZBC, " devices/horse_popular/image/z9x_model_prop_dapeng.txt", ""),
    ("z_conf_loader_not_staged", ZBC, " tools/devices/model_props.py", ""),
    ("z_conf_no_dump", ZBC, "var.Z62=", "var.Z63="),
]
FILES = sorted({m[1] for m in MUT} | {I + "hb_model_prop_dapenghm.txt", I + "hb_prop.txt", I + "hb_lumen_prop.txt",
                                     I + "hb_ota_props.txt", "devices/_image/first_start_prop.txt",
                                     "devices/goat_elegant/device.conf", "devices/horse_popular/device.conf",
                                     "overlay/v1/build_prop_v1.txt", "overlay/v1/product_prop_v1.txt",
                                     "overlay/v1/z9x_uires/product_prop.txt", "tools/ota/image/ota_props.txt",
                                     "tools/devices/test/model_props_test.py"})


def tree(dst):
    for f in FILES:
        os.makedirs(os.path.dirname(os.path.join(dst, f)), exist_ok=True)
        shutil.copy2(os.path.join(G, f), os.path.join(dst, f))


def run(root):
    r = subprocess.run([sys.executable, os.path.join(root, "tools/devices/test/model_props_test.py"), root],
                       capture_output=True, text=True)
    return r.returncode == 0, r.stdout


only = os.environ.get("MUT_ONLY", "").split()
w = tempfile.mkdtemp(prefix="mpmut.")
try:
    base = os.path.join(w, "base"); tree(base)
    good, out = run(base)
    if not good:
        print(out[-2000:]); print("BROKEN: the unmutated copy fails"); sys.exit(2)
    print("dumps: " + ("used" if "note: no" not in out else out.strip().split("\n")[0]))
    killed = survived = 0
    for name, f, old, new in MUT:
        if only and not any(name.startswith(o) for o in only):
            continue
        d = os.path.join(w, name); tree(d)
        p = os.path.join(d, f); s = open(p, encoding="utf-8").read()
        if s.count(old) != 1:
            print("ANCHOR %s: %d matches in %s" % (name, s.count(old), f)); survived += 1; continue
        open(p, "w", encoding="utf-8").write(s.replace(old, new))
        if run(d)[0]:
            print("SURVIVED " + name); survived += 1
        else:
            killed += 1
        shutil.rmtree(d)
    print("model_props_mutate: %d killed, %d survived" % (killed, survived))
    sys.exit(1 if survived else 0)
finally:
    shutil.rmtree(w)
