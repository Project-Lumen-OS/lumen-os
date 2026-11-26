#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""model_props_test.py: the stock model properties of the images that set them by an init rc (one loader,
tools/devices/model_props.py, the data per model: devices/goat_elegant/image/hb_model_props.*, devices/horse_popular/
image/z9x_model_props.*).

Per image: the committed rc is exactly what its table makes, every prop file keeps only lines the shared model rule
(x50_props.model_rule) keeps and no key the image sets itself, every supported model and firmware of device.conf has
its block keyed on both, and the recipe stages, proves (model_props.py check) and adds the rc. HB: every line of the
rule, the AI scene the app writes is the scene every file names, verify_tree.py knows the file. Z9X: only the lines
with a reader, each named, none of the seven z9x_trapezoid.rc lines twice, and z9x_trapezoid.rc is the stock
'##trapezoid' block. With the stock dumps present (HB_V130 / HB_V148, default ~/devices/g01{30,48}_mt9660/work/fs;
Z9X_V61519 / Z9X_V61558 / Z9X_V61562, default the xgimi-builder dumps under ~/devices/g0082_mt9952) it runs the
build host check. Negative cases: a dropped, an identity, a spaced, an unread and an unknown-model line each fail.
  python3 tools/devices/test/model_props_test.py [GSI]"""
import hashlib, os, re, shutil, subprocess, sys, tempfile

sys.dont_write_bytecode = True
G = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
TOOL = os.path.join(G, "tools", "devices", "model_props.py")
n = bad = 0


def ok(what, cond):
    global n, bad
    n += 1
    if not cond:
        bad += 1
        print("FAIL " + what)


def read(p):
    return open(p, encoding="utf-8").read() if os.path.exists(p) else ""


def gen(d, table):
    return subprocess.run([sys.executable, TOOL, "rc", d, table], capture_output=True, text=True)


def conf(path):
    out = {}
    for l in read(path).split("\n"):
        if l and not l.startswith("#") and "=" in l:
            k, _, v = l.partition("=")
            out[k.strip()] = v.strip()
    return out


sys.path.insert(0, os.path.join(G, "devices", "g0111", "image"))
import x50_props  # noqa: E402  the one model prop rule of every model's file

home = os.path.expanduser("~")
IMAGES = [
    {"dev": "goat_elegant", "x": "hb", "lines": "rule", "own": ["hb_prop.txt", "hb_lumen_prop.txt", "hb_ota_props.txt"],
     "dumps": {"V130": os.environ.get("HB_V130", home + "/devices/g0130_mt9660/work/fs"),
               "V148": os.environ.get("HB_V148", home + "/devices/g0148_mt9660/work/fs")},
     "skip": {("G0148", "v6.16.24")},      # 6.16.24 has no G0148 config (stock loads nothing), the installer refuses it
     "must": ["vendor.mstar.hwc.framebuffer.format=1", "persist.vendor.xgimi.hd_server=2", "ro.xgimi.mem.policy=2G",
              "ro.xgimi.default.aipq=film"]},
    {"dev": "horse_popular", "x": "z9x", "lines": "read", "own": [],
     "own_files": ["overlay/v1/build_prop_v1.txt", "overlay/v1/product_prop_v1.txt", "overlay/v1/z9x_uires/product_prop.txt",
                   "tools/ota/image/ota_props.txt"],
     "dumps": {"Z19": os.environ.get("Z9X_V61519", home + "/devices/g0082_mt9952/official/v61519/fs"),
               "Z58": os.environ.get("Z9X_V61558", home + "/devices/g0082_mt9952/official/v61558/fs"),
               "Z62": os.environ.get("Z9X_V61562", home + "/devices/g0082_mt9952/v61562/fs")},
     "skip": set(),
     "must": ["ro.xgimi.product.name=Product_dapeng", "vendor.mstar.hwc.framebuffer.format=1"],
     "never": ["ro.xgimi.mem.policy", "persist.vendor.xgimi.hd_server"]},
]
first_start = {x50_props.keyof(l) for l in read(os.path.join(G, "devices", "_image", "first_start_prop.txt")).split("\n")} - {None}
rows_of = {}

for im in IMAGES:
    D = os.path.join(G, "devices", im["dev"])
    IMG = os.path.join(D, "image")
    x = im["x"]
    TSV, RC = x + "_model_props.tsv", x + "_model_props.rc"
    # ---- the committed rc is the generator's output
    r = gen(IMG, TSV)
    ok("%s rc: the generator runs (%s)" % (x, r.stderr.strip()), r.returncode == 0)
    rc = read(os.path.join(IMG, RC))
    ok("%s rc: %s is exactly what the table makes" % (x, RC), r.stdout == rc and rc)
    # ---- the table and the prop files
    rows = [l.split("\t") for l in read(os.path.join(IMG, TSV)).split("\n") if l and not l.startswith("#")]
    rows_of[x] = rows
    ok("%s table: rows of six fields, lines %s" % (x, im["lines"]),
       len(rows) >= 3 and all(len(w) == 6 and w[5] == im["lines"] for w in rows))
    own = set(first_start)
    for f in im["own"]:
        own |= {x50_props.keyof(l) for l in read(os.path.join(IMG, f)).split("\n")} - {None}
    for f in im.get("own_files", []):
        own |= {x50_props.keyof(l) for l in read(os.path.join(G, f)).split("\n")} - {None}
    files = sorted({w[2] for w in rows})
    im["files"] = files
    for f in files:
        text = read(os.path.join(IMG, f)).split("\n")
        lines = [l for l in text if l and not l.startswith("#")]
        ok(f + ": present", bool(lines))
        ok(f + ": every line kept by the shared model rule", all(x50_props.model_rule(l) for l in lines))
        keys = [x50_props.keyof(l) for l in lines]
        ok(f + ": no identity key", not set(keys) & x50_props.IDENTITY)
        ok(f + ": no key the image sets itself: %s" % sorted(set(keys) & own), not set(keys) & own)
        ok(f + ": every key once", len(keys) == len(set(keys)))
        for m in im["must"]:
            ok(f + ": the stock " + m, m in lines)
        for k in im.get("never", []):
            ok(f + ": no " + k, k not in keys)
        if im["lines"] == "read":
            for i, l in enumerate(text):
                if l and not l.startswith("#"):
                    j = i - 1
                    while j >= 0 and text[j].startswith("#") and not text[j].startswith("# read by "):
                        j -= 1
                    ok(f + ": " + l + " names its reader", j >= 0 and text[j].startswith("# read by "))
        for l in lines:
            ok(f + ": " + x50_props.keyof(l) + " is set in every block that names the file",
               all(("    setprop %s %s" % tuple(l.split("=", 1))) in rc.split("on early-init")[i + 1]
                   for i, w in enumerate(rows) if w[2] == f))
    # ---- every supported model and firmware has its block, keyed on both
    dc = conf(os.path.join(D, "device.conf"))
    pairs = {(w[0], w[1]) for w in rows}
    for m in dc.get("models", "").split():
        for fw in dc.get("vendor_firmware", "").split():
            if (m, fw) not in im["skip"]:
                ok("%s table: %s on %s" % (x, m, fw), (m, fw) in pairs)
    heads = re.findall(r"^on (.*)$", rc, re.M)
    ok(x + " rc: one block per row", len(heads) == len(rows))
    for w in rows:
        ok("%s rc: %s %s keyed on the model and the firmware at early-init" % (x, w[0], w[1]),
           "early-init && property:ro.boot.xgimi.modelname=%s && property:ro.vendor.build.version.incremental=%s" % (w[0], w[1]) in heads)
    ok(x + " rc: init builtins only", all(l.startswith(("#", "on ", "    setprop ")) or not l for l in rc.split("\n")))
    # ---- the recipe stages and proves it
    steps = read(os.path.join(IMG, "steps.tsv"))
    bc = conf(os.path.join(D, "build.conf"))
    staged = (bc.get("overlays", "") + " " + bc.get("tools", "")).split()
    for f in files + [RC, TSV, "model_props.py", "x50_props.py"]:
        ok("%s build.conf stages %s" % (x, f), any(os.path.basename(s) == f for s in staged))
        ok("%s steps.tsv needs %s" % (x, f), re.search(r"^\S+\tneed\t.*\b" + re.escape(f) + r"\b", steps, re.M) is not None)
    ok(x + " build.conf: the shared loader, no copy of it", "tools/devices/model_props.py" in staged
       and not any(s.endswith(("hb_model_props.py", "z9x_model_props.py")) for s in staged))
    dump_args = "\t".join("%s={%s}" % (k, k) for k in im["dumps"])
    check_line = "\trun\tmodel_props.py\tcheck\t{in}\t%s\t%s\n" % (TSV, dump_args)
    ok(x + " steps.tsv: the build host proves the files against the dumps", check_line in steps)
    for k in im["dumps"]:
        ok("%s build.conf: the dump %s" % (x, k), ("var.%s=" % k) in read(os.path.join(D, "build.conf")))
    im["check_line"] = check_line

# ---- HB: installed by compose, verify_tree knows the file, the AI scene the app writes is the scene the files name
steps = read(os.path.join(G, "devices/goat_elegant/image/steps.tsv"))
ok("hb steps.tsv: the rc is installed", "lumen\tinstall\t0644\thb_model_props.rc\t{sys}/etc/init/hb_model_props.rc\n" in steps)
ok("hb steps.tsv: proven before installed", steps.find("model_props.py\tcheck") < steps.find("install\t0644\thb_model_props.rc"))
ok("hb verify_tree.py: the rc is an expected file", '"system/etc/init/hb_model_props.rc": os.path.join(IN, "hb_model_props.rc"),'
   in read(os.path.join(G, "devices/goat_elegant/image/verify_tree.py")))
hp = read(os.path.join(G, "apps", "HbProjector", "src", "org", "lumen", "projector", "panel", "HbPictureRows.java"))
ok("hb app: the AI mode hands its write to the shared AI picture quality", "org.lumen.projector.AiScenePolicy.afterOwnWrite(g, m.wire);" in hp)
drv = read(os.path.join(G, "apps", "core", "lumen-aipq", "src", "org", "lumen", "aipq", "AiPqDriver.java"))
ok("lumen-aipq: the AI default scene is ro.xgimi.default.aipq of the model file", 'PROP_RO = "ro.xgimi.default.aipq"' in drv)

# ---- Z9X: added to the tar after the check and read back; the trapezoid lines stay once, in z9x_trapezoid.rc
steps = read(os.path.join(G, "devices/horse_popular/image/steps.tsv"))
add = "*\ttar_add\tsystem/etc/init/z9x_model_props.rc\tz9x_model_props.rc\t644\n"
ok("z9x steps.tsv: the rc is added", add in steps)
ok("z9x steps.tsv: proven before added", 0 <= steps.find("model_props.py\tcheck") < steps.find(add))
ok("z9x steps.tsv: read back from the patched tar", "*\ttar_same\t{tar}\tsystem/etc/init/z9x_model_props.rc\tz9x_model_props.rc\n" in steps)
trap = [l.split()[1:] for l in read(os.path.join(G, "overlay/v1/z9x_trapezoid.rc")).split("\n") if l.strip().startswith("setprop ")]
ok("z9x_trapezoid.rc: the seven trapezoid lines", len(trap) == 7)
zlines = [l for f in IMAGES[1]["files"] for l in read(os.path.join(G, "devices/horse_popular/image", f)).split("\n")
          if l and not l.startswith("#")]
ok("z9x: no trapezoid line set twice", not {k for k, _ in trap} & {x50_props.keyof(l) for l in zlines})
ok("z9x: the AI scene stays the app's (no ro.xgimi.default.aipq, its stock reader XRMService is not in the image)",
   "ro.xgimi.default.aipq" not in {x50_props.keyof(l) for l in zlines})


# ---- negative cases: the generator refuses a broken table or file
def neg(im, name, edit):
    t = tempfile.mkdtemp(prefix="mp.")
    try:
        IMG = os.path.join(G, "devices", im["dev"], "image")
        for f in im["files"] + [im["x"] + "_model_props.tsv"]:
            shutil.copy2(os.path.join(IMG, f), t)
        edit(t)
        r = gen(t, im["x"] + "_model_props.tsv")
        ok("%s negative: %s" % (im["x"], name), r.returncode != 0)
    finally:
        shutil.rmtree(t)


def app(t, f, line):
    open(os.path.join(t, f), "a").write(line + "\n")


for im in IMAGES:
    f0, tsv, rows = im["files"][0], im["x"] + "_model_props.tsv", rows_of[im["x"]]
    reader = "# read by a test\n" if im["lines"] == "read" else ""
    neg(im, "an identity key", lambda t: app(t, f0, reader + "ro.product.name=Product_x"))
    neg(im, "a persist.sys key", lambda t: app(t, f0, reader + "persist.sys.theme.mode=false"))
    neg(im, "a value with a space", lambda t: app(t, f0, reader + "a.b=c d"))
    neg(im, "a value with a comment sign", lambda t: app(t, f0, reader + "a.b=#c"))
    neg(im, "a row twice", lambda t: app(t, tsv, "\t".join(rows[0])))
    neg(im, "a bad dump name", lambda t: app(t, tsv, "\t".join(rows[0][:1] + ["v9.9.9"] + rows[0][2:4] + ["v999"] + rows[0][5:])))
    neg(im, "an unknown lines mode", lambda t: app(t, tsv, "\t".join(rows[0][:1] + ["v9.9.9"] + rows[0][2:5] + ["all"])))
    neg(im, "five fields", lambda t: app(t, tsv, "\t".join(rows[0][:1] + ["v9.9.9"] + rows[0][2:5])))
    neg(im, "an empty prop file", lambda t: open(os.path.join(t, f0), "w").write("# nothing\n"))
neg(IMAGES[1], "a line without its reader", lambda t: app(t, IMAGES[1]["files"][0], "\nvendor.xgimi.softap.sta.coexist=true"))
neg(IMAGES[1], "a line under another comment", lambda t: app(t, IMAGES[1]["files"][0], "\n# a note\nvendor.xgimi.softap.sta.coexist=true"))
neg(IMAGES[1], "a reader comment without a reader", lambda t: app(t, IMAGES[1]["files"][0], "\n# read by \nvendor.xgimi.softap.sta.coexist=true"))

# ---- the check on a made-up stock file: the pin, a rule file is every line, a read file a choice in the stock order
def synth(name, stock, table_lines, prop, rc_from_table=True, extra=None):
    t = tempfile.mkdtemp(prefix="mpc.")
    try:
        d = os.path.join(t, "in"); os.makedirs(d)
        sf = os.path.join(t, "dump", "vendor_a", "etc", "xgimimodelname", "G0001", "build.prop")
        os.makedirs(os.path.dirname(sf))
        open(sf, "w").write(stock)
        sha = hashlib.sha256(stock.encode()).hexdigest()
        open(os.path.join(d, "t_model_props.tsv"), "w").write(
            "".join("G0001\tv1.0.%d\tt_model_prop_a.txt\t%s\tD1\t%s\n" % (i, sha if p else "0" * 64, m) for i, (m, p) in enumerate(table_lines)))
        open(os.path.join(d, "t_model_prop_a.txt"), "w").write(prop)
        shutil.copy2(os.path.join(G, "devices", "g0111", "image", "x50_props.py"), d)
        r = gen(d, "t_model_props.tsv")
        open(os.path.join(d, "t_model_props.rc"), "w").write(r.stdout if rc_from_table else r.stdout + "# edited\n")
        if extra:
            extra(d)
        return subprocess.run([sys.executable, TOOL, "check", d, "t_model_props.tsv", "D1=" + os.path.join(t, "dump")],
                              capture_output=True, text=True).returncode
    finally:
        shutil.rmtree(t)


STOCK = "ro.product.name=x\n#c\na.b=1\nc.d=2\npersist.sys.e=3\ne.f=4\n"
ok("check: rule = every rule line", synth("rule", STOCK, [("rule", True)], "a.b=1\nc.d=2\ne.f=4\n") == 0)
ok("check: rule with a line left out", synth("rule", STOCK, [("rule", True)], "a.b=1\ne.f=4\n") != 0)
ok("check: read = a choice in order", synth("read", STOCK, [("read", True)], "# read by x\na.b=1\n# read by y\ne.f=4\n") == 0)
ok("check: read in another order", synth("read", STOCK, [("read", True)], "# read by y\ne.f=4\n# read by x\na.b=1\n") != 0)
ok("check: read of a value stock does not have", synth("read", STOCK, [("read", True)], "# read by x\na.b=2\n") != 0)
ok("check: read of a key stock does not have", synth("read", STOCK, [("read", True)], "# read by x\nz.z=1\n") != 0)
ok("check: a stock file that is not the pinned one", synth("pin", STOCK, [("read", False)], "# read by x\na.b=1\n") != 0)
ok("check: an rc edited by hand", synth("rc", STOCK, [("read", True)], "# read by x\na.b=1\n", rc_from_table=False) != 0)
def redump(d):
    p = os.path.join(d, "t_model_props.tsv")
    s = open(p).read()
    open(p, "w").write(s.replace("\tD1\t", "\tD2\t"))


ok("check: a dump not given", synth("dump", STOCK, [("read", True)], "# read by x\na.b=1\n", extra=redump) != 0)

# ---- with the stock dumps: the build host check, and the Z9X trapezoid block against every pinned stock file
for im in IMAGES:
    stock = {k: v for k, v in im["dumps"].items()
             if any(os.path.isdir(os.path.join(v, p, "etc", "xgimimodelname")) for p in ("vendor", "vendor_a"))}
    if len(stock) == len(im["dumps"]):
        r = subprocess.run([sys.executable, TOOL, "check", os.path.join(G, "devices", im["dev"], "image"),
                            im["x"] + "_model_props.tsv"] + ["%s=%s" % kv for kv in stock.items()], capture_output=True, text=True)
        ok("%s dumps: model_props.py check (%s)" % (im["x"], (r.stdout + r.stderr).strip()), r.returncode == 0)
        if im["x"] == "z9x":
            for w in rows_of["z9x"]:
                p = next(os.path.join(stock[w[4]], v, "etc", "xgimimodelname", w[0], "build.prop") for v in ("vendor", "vendor_a")
                         if os.path.isfile(os.path.join(stock[w[4]], v, "etc", "xgimimodelname", w[0], "build.prop")))
                raw = open(p, "rb").read()
                ok("z9x dumps: %s %s pinned" % (w[0], w[1]), hashlib.sha256(raw).hexdigest() == w[3])
                lines = [l.strip() for l in raw.decode("utf-8", "replace").split("\n")]
                block = lines[lines.index("##trapezoid") + 1:]
                block = [l for l in block[:block.index("")] if l and not l.startswith("#")] if "" in block else []
                ok("z9x dumps: z9x_trapezoid.rc = the '##trapezoid' block of %s %s" % (w[0], w[1]),
                   [l.split("=", 1) for l in block] == trap)
    else:
        print("note: no %s stock dumps here, the build host check runs in the recipe" % im["x"])

if bad:
    print("model_props_test: %d of %d FAILED" % (bad, n)); sys.exit(1)
print("model_props_test: %d checks passed" % n)
