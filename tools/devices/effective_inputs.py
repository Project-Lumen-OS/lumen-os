#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""effective_inputs.py: write every build input a device resolves to, as plain files, so a refactor of device data
(family files, recipe templates, shared tables) is proven by diffing the output of two trees.

  effective_inputs.py --root TREE --out DIR [CODENAME ...]     (default: every devices/<c> with a device.conf)
  effective_inputs.py --diff DIR_A DIR_B                       exit 0 when both hold the same files, byte for byte

Per device it writes, only through the tree's own entry points (so an old and a new tree are read the same way):
  registry/<c>.json            the registry entry (tools/devices/devices.py load_all), keys sorted
  build/<c>.conf.json          the build descriptor as tools/build/recipe.py resolves it: sorted key=value pairs, and
                               for every key with KEY.COND variants the order those variants are tried in (Descriptor.pick)
  build/<c>.plan.<v>.<args>    tools/build/build.py plan for every samples= version and every variant of variants=
                               (BUILD_ID fixed, so the bid and every time derived from it are the same on both trees)
  build/<c>.files              tools/build/build.py files (the staged repo paths), one per line
  apps/<c>.<App>.list          tools/devices/assemble_app.py --list of every app the device builds (sha256 + path of
                               each file of the assembled source tree)
  apps/<c>.<App>.apk           the rows of that list apps/build_apk.sh packs (AndroidManifest.xml, src/, res/,
                               assets/, lib/): the exact APK input
Nothing is written outside --out. Mac or Linux, python3 only, no network.
"""
import json
import os
import re
import subprocess
import sys

HELPER = r'''
import json, os, sys
root, c = sys.argv[1], sys.argv[2]
sys.path[:0] = [os.path.join(root, "tools", "devices"), os.path.join(root, "tools", "build")]
import devices, recipe
out = {}
entry = devices.load_all(root).get(c)
out["registry"] = entry
p = os.path.join(root, "devices", c, "build.conf")
if os.path.isfile(p):
    d = recipe.Descriptor(p, c)
    raw = d.raw
    order = {}
    for k in raw:
        base = k.split(".")[0] if not k.startswith("var.") else ".".join(k.split(".")[:2])
        if k != base:
            order.setdefault(base, []).append(k)
    out["conf"] = {"pairs": sorted(raw.items()), "order": {b: ks for b, ks in sorted(order.items()) if len(ks) > 1}}
print(json.dumps(out, sort_keys=True, ensure_ascii=False, indent=1))
'''


def run(cmd, cwd):
    env = dict(os.environ, BUILD_ID="202611210000")
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, env=env)
    text = r.stdout + ("" if r.returncode == 0 else "\n[exit %d] %s" % (r.returncode, r.stderr.strip()))
    # a plan prints the clock time of the run where no bid fixes it (source_date_epoch, mtime): not a device input
    return re.sub(r"\b(source_date_epoch|mtime)=[0-9]+", r"\1=<now>", text.replace(cwd, "<root>"))


def codenames(root):
    d = os.path.join(root, "devices")
    return sorted(c for c in os.listdir(d) if not c.startswith((".", "_")) and os.path.isfile(os.path.join(d, c, "device.conf")))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def device(root, out, c):
    py = sys.executable
    info = json.loads(subprocess.run([py, "-B", "-c", HELPER, root, c], capture_output=True, text=True, check=True).stdout)
    write(os.path.join(out, "registry", c + ".json"), json.dumps(info["registry"], sort_keys=True, ensure_ascii=False, indent=1) + "\n")
    conf = info.get("conf")
    apps = list((info["registry"] or {}).get("apps") or [])
    if conf:
        write(os.path.join(out, "build", c + ".conf.json"), json.dumps(conf, sort_keys=True, ensure_ascii=False, indent=1) + "\n")
        pairs = dict(conf["pairs"])
        build = [py, "-B", os.path.join(root, "tools", "build", "build.py")]
        variants = [""] + [v.split(":", 1)[1] for v in pairs.get("variants", "").split()]
        for v in pairs.get("samples", "").split():
            for args in variants:
                name = "%s.plan.%s.%s" % (c, v, args.replace(" ", "_").strip("-") or "default")
                write(os.path.join(out, "build", name), run(build + ["plan", c, v] + args.split(), root))
        write(os.path.join(out, "build", c + ".files"), run(build + ["files", c], root))
        apps += [a for a in pairs.get("apps", "").split() if a not in apps]
    folder = os.path.join(root, "devices", c, "app")
    for n in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if n.endswith(".layers") and n[:-7] not in apps:
            apps.append(n[:-7])
    asm = [py, "-B", os.path.join(root, "tools", "devices", "assemble_app.py"), "--root", root]
    for a in sorted(set(apps)):
        lst = os.path.join(out, "apps", "%s.%s.list" % (c, a))
        os.makedirs(os.path.dirname(lst), exist_ok=True)
        r = subprocess.run(asm + ["--device", c, "--app", a, "--list", lst, "--quiet"], cwd=root, capture_output=True, text=True)
        if r.returncode != 0:
            write(lst, "[exit %d] %s\n" % (r.returncode, (r.stdout + r.stderr).strip().replace(root, "<root>")))
            continue
        # the part apps/build_apk.sh packs (manifest, src, res, assets, lib): equal = the same APK; test/ and
        # tools/ of an app ride in the tree but never in the APK
        rows = open(lst, encoding="utf-8").read().split("\n")
        write(lst[:-5] + ".apk", "\n".join(r for r in rows if r.split("  ", 1)[-1].startswith(APK_INPUTS)) + "\n")


APK_INPUTS = ("AndroidManifest.xml", "src/", "res/", "assets/", "lib/")


def diff(a, b):
    def files(top):
        return sorted(os.path.relpath(os.path.join(d, f), top) for d, _, fs in os.walk(top) for f in fs)
    fa, fb = files(a), files(b)
    bad = 0
    for p in sorted(set(fa) | set(fb)):
        pa, pb = os.path.join(a, p), os.path.join(b, p)
        if not os.path.isfile(pa) or not os.path.isfile(pb):
            print("only in %s: %s" % (a if os.path.isfile(pa) else b, p))
            bad += 1
        elif open(pa, "rb").read() != open(pb, "rb").read():
            print("differs: %s" % p)
            bad += 1
    print("effective_inputs: %d files, %d differ" % (len(set(fa) | set(fb)), bad))
    return 1 if bad else 0


def main(argv):
    if argv[:1] == ["--diff"] and len(argv) == 3:
        return diff(argv[1], argv[2])
    if len(argv) < 4 or argv[0] != "--root" or argv[2] != "--out":
        print(__doc__, file=sys.stderr)
        return 2
    root, out = os.path.abspath(argv[1]), os.path.abspath(argv[3])
    for c in argv[4:] or codenames(root):
        device(root, out, c)
    print("effective_inputs: %s -> %s" % (root, out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
