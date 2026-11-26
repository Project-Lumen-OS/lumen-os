#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""family_test.py: the family files of the device descriptors (tools/devices/layered.py, devices/README.md).

  python3 tools/devices/test/family_test.py [GSI]

1. the format on scratch files: include chains, {codename}, a model value replacing the family's in place, the errors
   (a key twice in one file, a model line repeating its family value, a missing include, a cycle, include= not first,
   a family file with codename=), a flat folder of staged inputs;
2. the tree: every build.conf and device.conf of devices/ resolves; a key every model of a family file gives itself
   with the same value (after {codename}) is an error: it belongs in that family file (no copies); the same for data
   files and app recipes: a model's copy of its family's file, or one file byte-equal in all models of a family;
3. the builder stages each descriptor's family files (tools/build/build.py files) and the staged copy resolves.
Exit 0 green, 1 red.
"""
import os
import shutil
import subprocess
import sys
import tempfile

G = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import layered  # noqa: E402

bad = []


def ok(name, cond):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        bad.append(name)


def raises(name, fn, want):
    try:
        fn()
    except layered.LayerError as e:
        ok(name, want in str(e))
        return
    ok(name + " (no error)", False)


def put(root, rel, text):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)
    return p


# ---- 1. the format
T = tempfile.mkdtemp(prefix="family_test.")
try:
    put(T, "devices/_family/f/top.build.conf", "# top\nfs=erofs\nstage_dir=build/{codename}/stage\nhost=a\n")
    put(T, "devices/_family/f/mid.build.conf", "include=devices/_family/f/top.build.conf\nsteps=s.tsv\nhost=b\n")
    m = put(T, "devices/m1/build.conf", "# model\ninclude=devices/_family/f/mid.build.conf\ncodename=m1\nhost=c\nkeys=m1\n")
    e = layered.effective(m)
    ok("include chain: widest family first, model keys last", list(e) == ["fs", "stage_dir", "host", "steps", "codename", "keys"])
    ok("{codename} is the including model's", e["stage_dir"] == "build/m1/stage")
    ok("a later file replaces a value in its place", e["host"] == "c")
    ok("files(): the model and its family files", [os.path.relpath(p, T) for p in layered.files(m)]
       == ["devices/m1/build.conf", "devices/_family/f/mid.build.conf", "devices/_family/f/top.build.conf"])
    ok("text(): key=value lines", layered.text(m).startswith("fs=erofs\nstage_dir=build/m1/stage\n"))
    r = put(T, "devices/m2/build.conf", "include=devices/_family/f/mid.build.conf\ncodename=m2\nsteps=s.tsv\n")
    raises("a model line repeating its family value is an error", lambda: layered.effective(r), "repeats the value")
    r = put(T, "devices/m3/build.conf", "include=devices/_family/f/mid.build.conf\ncodename=m3\nkeys=a\nkeys=b\n")
    raises("a key twice in one file is an error", lambda: layered.effective(r), "given twice")
    r = put(T, "devices/m4/build.conf", "codename=m4\ninclude=devices/_family/f/mid.build.conf\n")
    raises("include= after another key is an error", lambda: layered.effective(r), "first key line")
    r = put(T, "devices/m5/build.conf", "include=devices/_family/f/none.build.conf\ncodename=m5\n")
    raises("a missing family file is an error", lambda: layered.effective(r), "not found")
    put(T, "devices/_family/f/c1.conf", "include=devices/_family/f/c2.conf\nfs=x\n")
    put(T, "devices/_family/f/c2.conf", "include=devices/_family/f/c1.conf\nhost=y\n")
    r = put(T, "devices/m6/build.conf", "include=devices/_family/f/c1.conf\ncodename=m6\n")
    raises("an include cycle is an error", lambda: layered.effective(r), "cycle")
    put(T, "devices/_family/f/bad.conf", "codename=x\n")
    r = put(T, "devices/m7/build.conf", "include=devices/_family/f/bad.conf\ncodename=m7\n")
    raises("a family file with codename= is an error", lambda: layered.effective(r), "no codename")
    flat = os.path.join(T, "in")
    os.makedirs(flat)
    for p in layered.files(m):
        shutil.copy(p, os.path.join(flat, os.path.basename(p) if p != m else "build.conf"))
    ok("a flat folder of staged inputs resolves to the same keys", layered.effective(os.path.join(flat, "build.conf")) == e)
finally:
    shutil.rmtree(T)

# ---- 2. the tree
D = os.path.join(G, "devices")
models = sorted(c for c in os.listdir(D) if not c.startswith((".", "_")) and os.path.isfile(os.path.join(D, c, "device.conf")))
members = {}
for c in models:
    for name in ("build.conf", "device.conf"):
        p = os.path.join(D, c, name)
        if not os.path.isfile(p):
            continue
        try:
            layered.effective(p)
        except layered.LayerError as e:
            ok("%s/%s resolves (%s)" % (c, name, e), False)
            continue
        for fam in layered.files(p)[1:]:
            members.setdefault(os.path.relpath(fam, G), []).append((c, p))
ok("every descriptor resolves", not [b for b in bad if "resolves" in b])
for fam, ms in sorted(members.items()):
    if len(ms) < 2:
        ok("%s serves one model only (%s): fold it into the model file" % (fam, ms[0][0]), False)
        continue
    own = []
    for c, p in ms:
        keys = {}
        for src, n, line in layered.lines(p):
            if src == p and "=" in line and not line.lstrip().startswith("#") and not line.startswith(layered.INCLUDE + "="):
                k, v = line.split("=", 1)
                keys[k.strip()] = v.strip().replace(c, layered.TEMPLATE)
        own.append(keys)
    common = set(own[0]).intersection(*own[1:]) - {"codename"}
    copies = sorted(k for k in common if len({o[k] for o in own}) == 1)
    ok("%s: no key that all its %d models give the same (%s)" % (fam, len(ms), " ".join(copies) or "none"), not copies)

# ---- 2b. data files and app recipes: a model file byte-equal to the one its family folder gives is a copy, and a
# file byte-equal in every model of a family folder belongs in that folder
import hashlib  # noqa: E402
SHARED = ("signal.tsv", "sensors.tsv", "source.tsv", "audio/chip_volume.tsv", "wallcolor.conf", "cast.conf", "menu.json")


def digest(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest() if os.path.isfile(p) else None


fam_models = {}
for c in models:
    conf = os.path.join(D, c, "device.conf")
    chain = layered.dirs(conf)[1:]
    for d in chain:
        fam_models.setdefault(d, []).append(c)
    names = list(SHARED) + ["app/" + n for n in (os.listdir(os.path.join(D, c, "app")) if os.path.isdir(os.path.join(D, c, "app")) else [])]
    for n in names:
        own = digest(os.path.join(D, c, n))
        fam = next((d for d in chain if os.path.isfile(os.path.join(d, n))), None)
        if own and fam:
            ok("%s/%s differs from its family's %s (else drop the copy)" % (c, n, os.path.relpath(fam, G)), own != digest(os.path.join(fam, n)))
for d, ms in sorted(fam_models.items()):
    if len(ms) < 2:
        continue
    for n in SHARED + tuple("app/" + x for x in sorted({f for c in ms for f in (os.listdir(os.path.join(D, c, "app")) if os.path.isdir(os.path.join(D, c, "app")) else [])})):
        ds = {digest(os.path.join(D, c, n)) for c in ms}
        ok("%s: %s is not the same file in all its %d models (it would belong in the family folder)" % (os.path.relpath(d, G), n, len(ms)),
           len(ds) != 1 or None in ds)

# ---- 3. staging
for c in ("g0111", "h0063", "g0155", "g0170"):
    if not os.path.isfile(os.path.join(D, c, "build.conf")):
        continue
    out = subprocess.run([sys.executable, "-B", os.path.join(G, "tools/build/build.py"), "files", c], cwd=G,
                         capture_output=True, text=True).stdout.split()
    fams = [os.path.relpath(p, G) for p in layered.files(os.path.join(D, c, "build.conf"))]
    ok("%s: build.py files stages the descriptor and its family files %s" % (c, " ".join(fams[1:])),
       all(f in out for f in fams) and "tools/devices/layered.py" in out)
    names = [os.path.basename(f) for f in out]
    ok("%s: staged names are unique" % c, len(names) == len(set(names)))

print("family_test: %s" % ("RED %d" % len(bad) if bad else "GREEN"))
sys.exit(1 if bad else 0)
