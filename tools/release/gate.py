#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""gate.py: the one release gate of every model ("One gate"). The same rule for
each model of the registry: what its image is built from is recorded, and any change of it is named per model.

  gate.py [--root GSI] check  [--model C ...] [--apk]   compare with the records, exit 1 on any change or no record
  gate.py [--root GSI] record [--model C ...] [--apk]   write the records (devices/_baseline/gate/<codename>.tsv)
  gate.py [--root GSI] show   --model C                 the record lines the tree gives now
  gate.py [--root GSI] pins   [--model C ...]           run the pin rebuild check of each model in pins.tsv

What a record holds (one file per model, the same lines for every model):
  tools   sha256 of the shared build inputs (apps/build_apk.sh, apps/sdk, tools/devices, tools/build)
  image   sha256 of the image inputs: every file the image descriptor names (devices/<c>/build.conf effective, through
          tools/devices/layered.py: props, overlays, tools, steps, base pin) and what tools/build/build.py files stages
  app     APP, the sha256 of its assembled tree (tools/devices/assemble_app.py --list: every source, resource and data
          file of the APK) and, with --apk, the sha256 of the APK apps/build_apk.sh builds from it (on the build host)
  input   PATH, the sha256 of an image input the tree does not track (boot animation, stock analysis tables): a checkout
          with another copy is named by path
  pin     APP, the sha256 of a prebuilt APK the image ships (devices/_baseline/gate/pins.tsv names the model's pin
          folder and the check that rebuilds each pin from its commit)
  patch   FIX, RULE, RESULT: a fix of tools/base_patch the image carries (patch_lines): each patches.tsv row (every
          image signs its base APKs through it), the services.jar fix of the model's base (build.conf framework=) and
          'tool', the code that builds and swaps them. A check names the fix whose rule or pinned result moved.
A shared change shows as a changed app line in every model it reaches, never as one model's special case: the
re-record after a proven refactor is one 'gate.py record' for every model, its diff the list of what changed where.
Exit 0 green, 1 red, 2 usage.
"""
import argparse
import datetime
import glob
import hashlib
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REC = "devices/_baseline/gate"
TOOLS = ("apps/build_apk.sh", "apps/sdk", "tools/devices", "tools/build", "apps/check_hal_callers.py")
SKIP_DIRS = {"__pycache__", "test", ".DS_Store"}
BP = "tools/base_patch"   # fixes in the code of base APKs and of services.jar (patch_lines)
BP_TABLES = ("patches.tsv", "framework.tsv")


def sha_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def files_under(root, rel):
    p = os.path.join(root, rel)
    if os.path.isfile(p):
        return [rel]
    out = []
    for d, ds, fs in os.walk(p):
        ds[:] = sorted(x for x in ds if x not in SKIP_DIRS)
        out += [os.path.relpath(os.path.join(d, f), root) for f in fs if not f.endswith(".pyc") and f != ".DS_Store"]
    return sorted(out)


def digest(root, paths):
    h = hashlib.sha256()
    for r in sorted(set(paths)):
        p = os.path.join(root, r)
        h.update(("%s  %s\n" % (sha_file(p) if os.path.isfile(p) else "missing", r)).encode())
    return h.hexdigest()


def devices(root):
    sys.path.insert(0, os.path.join(root, "tools", "devices"))
    import devices as d  # noqa: E402
    import layered  # noqa: E402
    import assemble_app  # noqa: E402
    return d, layered, assemble_app


def models(root):
    out = subprocess.run([sys.executable, "-B", os.path.join(root, "tools/devices/devices.py"), "--root", root, "list",
                          "--images"], capture_output=True, text=True).stdout
    return [ln.split()[0] for ln in out.splitlines() if ln.strip()]


def image_inputs(root, c):
    _, layered, _ = devices(root)
    conf = os.path.join(root, "devices", c, "build.conf")
    paths = set(os.path.relpath(p, root) for p in [conf] + [os.path.join(root, f) for f in
                                                           layered_files(root, conf)])
    for k, v in layered.effective(conf).items():
        for w in v.replace(",", " ").split():
            w = w.split("=", 1)[-1]
            if "/" in w and not w.startswith(("/", "~", "{")) and os.path.isfile(os.path.join(root, w)):
                paths.add(w)
    # --no-built: the built= inputs are build outputs with their own sha pin in the descriptor (the Wi-Fi capex)
    out = subprocess.run([sys.executable, "-B", os.path.join(root, "tools/build/build.py"), "files", "--no-built", c],
                         cwd=root, capture_output=True, text=True).stdout
    paths |= {ln.strip() for ln in out.splitlines() if ln.strip() and os.path.isfile(os.path.join(root, ln.strip()))}
    return sorted(paths)


def layered_files(root, conf):
    out = subprocess.run([sys.executable, "-B", os.path.join(root, "tools/devices/layered.py"), "--files", conf],
                         cwd=root, capture_output=True, text=True).stdout
    return [ln.strip() for ln in out.splitlines() if ln.strip()]


def apps_of(root, c):
    _, layered, asm = devices(root)
    conf = os.path.join(root, "devices", c, "build.conf")
    names = layered.effective(conf).get("apps", "").split()
    return [(n, asm.find_recipe(root, c, n)) for n in names]


def tree(root, c, app, recipe, work):
    os.makedirs(work, exist_ok=True)
    out = os.path.join(work, c + "." + app)
    lst = out + ".list"
    r = subprocess.run([sys.executable, "-B", os.path.join(root, "tools/devices/assemble_app.py"), "--root", root,
                        "--device", c, "--app", app, "--out", out, "--list", lst, "--quiet"], cwd=root,
                       capture_output=True, text=True)
    if r.returncode != 0:
        return None, out, (r.stdout + r.stderr).strip().splitlines()[-1:]
    return hashlib.sha256(open(lst, "rb").read()).hexdigest(), out, None


def apk(root, tree_dir, work, name):
    out = os.path.join(work, name + ".apk")
    env = dict(os.environ, BUILD_DIR=os.path.join(work, "b_" + name))
    r = subprocess.run(["sh", os.path.join(root, "apps/build_apk.sh"), tree_dir, out], cwd=root, env=env,
                       capture_output=True, text=True)
    return sha_file(out) if r.returncode == 0 and os.path.isfile(out) else "BUILD_FAIL"


def read_pins(root, c):
    p = os.path.join(root, REC, "pins.tsv")
    return [w for w in (ln.rstrip("\n").split("\t") for ln in open(p, encoding="utf-8") if not ln.startswith("#"))
            if len(w) >= 3 and w[0] == c] if os.path.isfile(p) else []


def pins(root, c):
    """[("pin", APP, sha256)] from the PINS.sha256 of the model's pin folder (pins.tsv), and problems: a pinned APK
    present in the checkout whose bytes differ from its row (the pins are ignored files, a checkout may lack them)."""
    rows, out, errs = [], {}, []
    p = os.path.join(root, REC, "pins.tsv")
    if os.path.isfile(p):
        for ln in open(p, encoding="utf-8"):
            w = ln.rstrip("\n").split("\t")
            if len(w) >= 3 and not ln.startswith("#") and w[0] == c:
                rows.append(w)
    for _, folder, _check in rows:
        lst = os.path.join(root, folder, "PINS.sha256")
        for ln in (open(lst, encoding="utf-8") if os.path.isfile(lst) else []):
            w = ln.split()
            if len(w) == 2 and not ln.startswith("#") and w[1].endswith(".apk"):
                out[w[1][:-4]] = w[0]
        for name, want in out.items():
            a = os.path.join(root, folder, name + ".apk")
            if os.path.isfile(a) and sha_file(a) != want:
                errs.append("%s pin %s: %s is %s, PINS.sha256 says %s" % (c, name, os.path.relpath(a, root),
                                                                          sha_file(a)[:12], want[:12]))
    return [("pin", n, out[n]) for n in sorted(out)], errs


def base_patch(root):
    """tools/base_patch of root: base_patch.py (the fixes of base APKs, patches.tsv) and services.py (the services.jar
    fix of each Lumen base, framework.tsv)"""
    import importlib.util
    d = os.path.join(root, BP)
    if d not in sys.path:
        sys.path.insert(0, d)
    import base_patch as bp  # noqa: E402
    spec = importlib.util.spec_from_file_location("base_patch_services", os.path.join(d, "services.py"))
    sv = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sv)
    return bp, sv


def text_sha(rows, files_digest):
    return hashlib.sha256(("".join("\t".join(r) + "\n" for r in rows) + files_digest).encode()).hexdigest()


def patch_lines(root, c):
    """[("patch", FIX, RULE, RESULT)] of what the model's image takes from tools/base_patch, and errors.
    A base APK fix (patches.tsv row; every image signs its base APKs through base_patch.patched_apk): RULE = its row and
    its source folder <id>/, RESULT = its pinned classes.dex. The services.jar fix of the model's base (build.conf
    framework=): RULE = the base's framework.tsv rows, services_hdmi/, the services* tools and the LineageOS patch it is
    written from, RESULT = the patched pins of its files. 'tool': the other files that build and swap every fix.
    Errors: a built result in this checkout (build/base_patch/...) whose bytes differ from its pin."""
    bp, sv = base_patch(root)
    _, layered, _ = devices(root)
    lineage = os.path.relpath(sv.PATCH, bp.G)
    tracked = set(subprocess.run(["git", "ls-files", "--", BP, lineage], cwd=root, capture_output=True,
                                 text=True).stdout.split("\n"))

    def src(*rels):   # tracked sources only (an untracked checkout: every file), no notes
        return [f for r in rels for f in files_under(root, r) if (f in tracked or not tracked - {""}) and
                not f.endswith(".md")]
    top = [f for f in src(BP) if os.path.dirname(f) == BP]
    svc = [f for f in top if os.path.basename(f).startswith("services")]
    tables = [os.path.join(BP, t) for t in BP_TABLES]
    out = [("patch", "tool", digest(root, [f for f in top if f not in svc + tables]), "-")]
    errs = []
    for r in bp.rows():
        row = [r[k] for k in ("path", "id", "cls", "base", "out")]
        out.append(("patch", r["id"], text_sha([row], digest(root, src(os.path.join(BP, r["id"])))), r["out"]))
        p = os.path.join(bp.DEX_DIR, r["id"], bp.DEX)
        if os.path.isfile(p) and sha_file(p) != r["out"]:
            errs.append("%s base_patch %s: %s is %s, patches.tsv pins %s" % (c, r["id"], os.path.relpath(p, root),
                                                                           sha_file(p)[:12], r["out"][:12]))
    base = layered.effective(os.path.join(root, "devices", c, "build.conf")).get("framework")
    if base:
        rows = [r for r in bp.table(BP_TABLES[1]) if r[1:2] == [base]]
        files = [r for r in rows if r[0] == "file"]
        rule = text_sha(rows, digest(root, svc + src(os.path.join(BP, sv.FIX), lineage)))
        out.append(("patch", "%s:%s" % (sv.FIX, base), rule, text_sha([(r[2], r[4]) for r in files], "")))
        for r in files:
            p = os.path.join(sv.OUT_DIR, base, r[2])
            if os.path.isfile(p) and sha_file(p) != r[4]:
                errs.append("%s base_patch %s:%s: %s is %s, framework.tsv pins %s" % (
                    c, sv.FIX, base, os.path.relpath(p, root), sha_file(p)[:12], r[4][:12]))
    return out, errs


def lines_now(root, c, with_apk, work):
    _, layered, _ = devices(root)
    tracked = set(subprocess.run(["git", "ls-files", "--"] + list(TOOLS), cwd=root, capture_output=True,
                                 text=True).stdout.split("\n"))
    # tracked files only: a build host generates files there (apps/sdk system modules of its JDK)
    tools = [f for t in TOOLS for f in files_under(root, t) if f in tracked or not tracked - {""}]
    # tools/base_patch (framework.tsv) is in the patch lines, per fix and per base, not in the image line of every model
    img = [f for f in image_inputs(root, c) if not f.startswith(BP + "/")]
    tracked_img = set(subprocess.run(["git", "ls-files", "--"] + img, cwd=root, capture_output=True,
                                     text=True).stdout.split("\n"))
    out = [("tools", digest(root, tools)), ("image", digest(root, [f for f in img if f in tracked_img]))]
    # inputs the tree does not track (the boot animation, pins, stock analysis tables): one line each, so a check names
    # the file a checkout has differently
    out += [("input", f, sha_file(os.path.join(root, f))) for f in img if f not in tracked_img]
    errs = []
    for app, recipe in apps_of(root, c):
        if not recipe:
            d = os.path.join(root, devices(root)[2].app_dir(root, app))
            src = [f for f in files_under(root, os.path.relpath(d, root))] if os.path.isdir(d) else []
            out.append(("app", app, digest(root, src) if src else "none", "-"))
            continue
        t, tdir, err = tree(root, c, app, recipe, work)
        if err:
            errs.append("%s %s: %s" % (c, app, err[0] if err else "assembly failed"))
            out.append(("app", app, "ASM_FAIL", "-"))
            continue
        out.append(("app", app, t, apk(root, tdir, work, c + "." + app) if with_apk else "-"))
    pl, perr = pins(root, c)
    bl, berr = patch_lines(root, c)
    return out + pl + bl, errs + perr + berr


def read_record(root, c):
    p = os.path.join(root, REC, c + ".tsv")
    if not os.path.isfile(p):
        return None
    return [tuple(ln.rstrip("\n").split("\t")) for ln in open(p, encoding="utf-8") if ln.strip() and
            not ln.startswith("#")]


def key(line):
    return line[:2] if line[0] in ("app", "pin", "input", "patch") else line[:1]


def compare(rec, now, with_apk):
    r = {key(x): x for x in rec}
    n = {key(x): x for x in now}
    diffs = []
    for k in sorted(set(r) | set(n)):
        a, b = r.get(k), n.get(k)
        if a is None or b is None:
            diffs.append("%s %s" % ("new" if a is None else "gone", "base_patch " + k[1] if k[0] == "patch" else
                                    " ".join(k)))
            continue
        if k[0] == "patch":
            if a[2:] != b[2:]:
                diffs.append(patch_diff(k[1], a, b))
            continue
        if k[0] == "app":
            if a[2] != b[2]:
                diffs.append("app %s: tree %s -> %s" % (k[1], a[2][:12], b[2][:12]))
            elif with_apk and a[3] != "-" and a[3] != b[3]:
                diffs.append("app %s: same tree, APK %s -> %s (a build input outside the tree)" % (k[1], a[3][:12],
                                                                                                b[3][:12]))
        elif a[-1] != b[-1]:
            diffs.append("%s: %s -> %s" % (" ".join(k), a[-1][:12], b[-1][:12]))
    return diffs


def patch_diff(fix, a, b):
    """what moved in a base_patch fix: its pinned result, or its rule with the same result"""
    prove = "(prove it: tools/base_patch/test/run.sh, mutate.py, then gate.py record)"
    if fix == "tool":
        return "the base_patch tool (base_patch.py, DexSwap, JarLink, tools.tsv): %s -> %s, every fix it builds %s" % (
            a[2][:12], b[2][:12], prove)
    if a[3] != b[3]:
        return "base_patch %s: result %s -> %s, rule %s -> %s %s" % (fix, a[3][:12], b[3][:12], a[2][:12], b[2][:12],
                                                                    prove)
    return "base_patch %s: rule %s -> %s, same result pin %s" % (fix, a[2][:12], b[2][:12], prove)


def head(root):
    return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=root, capture_output=True,
                          text=True).stdout.strip()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(HERE)))
    ap.add_argument("cmd", choices=("check", "record", "show", "pins"))
    ap.add_argument("--model", action="append")
    ap.add_argument("--apk", action="store_true")
    ap.add_argument("--full", action="store_true", help="pins: rebuild every pin, not only those whose inputs changed")
    a = ap.parse_args(argv)
    root = os.path.abspath(a.root)
    ms = a.model or models(root)
    red = 0
    if a.cmd == "pins":   # the pin rebuild check of every model whose image ships prebuilt APKs (pins.tsv)
        for c in ms:
            for row in read_pins(root, c):
                cmd = row[2] + (" --full" if a.full else "")
                print("gate: %s pins: %s" % (c, cmd))
                red |= 1 if subprocess.call(cmd, shell=True, cwd=root) else 0
        print("gate: pins %s" % ("RED" if red else "GREEN"))
        return red
    with tempfile.TemporaryDirectory(prefix="gate_") as work:
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=int(os.environ.get("GATE_JOBS", "8"))) as ex:
            done = dict(zip(ms, ex.map(lambda c: lines_now(root, c, a.apk, os.path.join(work, c)), ms)))
        for c in ms:
            now, errs = done[c]
            for e in errs:
                print("gate: RED %s" % e)
                red = 1
            if a.cmd == "show":
                print("\n".join("\t".join(x) for x in now))
                continue
            if a.cmd == "record":
                if errs:
                    print("gate: %s not recorded (an app does not assemble)" % c)
                    continue
                os.makedirs(os.path.join(root, REC), exist_ok=True)
                old = read_record(root, c) or []
                if not a.apk:   # keep the APK column of apps whose tree did not change
                    o = {key(x): x for x in old}
                    now = [x[:3] + (o[key(x)][3],) if x[0] == "app" and key(x) in o and o[key(x)][2] == x[2]
                           else x for x in now]
                with open(os.path.join(root, REC, c + ".tsv"), "w") as f:
                    f.write("# tools/release/gate.py record of %s at %s, %s (docs/architecture/ONE_PROJECTOR.md)\n"
                            % (c, head(root), datetime.date.today().isoformat()))
                    f.write("".join("\t".join(x) + "\n" for x in now))
                print("gate: %s recorded (%d lines)" % (c, len(now)))
                continue
            rec = read_record(root, c)
            if rec is None:
                print("gate: RED %s: no record (%s/%s.tsv): gate.py record --model %s" % (c, REC, c, c))
                red = 1
                continue
            d = compare(rec, now, a.apk)
            for x in d:
                print("gate: RED %s: %s" % (c, x))
            red |= 1 if d else 0
            print("gate: %s %s" % (c, "changed (%d)" % len(d) if d else "GREEN"))
    print("gate: %s (%d models)" % ("RED" if red else "GREEN", len(ms)))
    return red


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
