#!/usr/bin/env python3
"""check_hal.py: the HAL caller check of a device, chosen by hal_check in devices/<codename>/device.conf
.

  check_hal.py [--root DIR] (--device CODENAME [--app APP] [--dex DEX] | --all)

  z9x-callers   apps/check_hal_callers.py <hal_app>/src          (Z9X gate input, unchanged)
  play6-hidl    tools/play6/check_play6_hal.py <hal_app>          (unchanged)
  aidl-table    tools/devices/check_aidl_hal.py --app <hal_app> --codes <hal_codes> [--codes <each of hal_codes_also>]
                --banned <hal_banned> --allowed <hal_allowed> [--dex DEX]   (no hal_allowed: the checker stops, the
                device is red; hal_codes_also: the tables of the other supported stock firmware, every call the same
                row in each)
  none          no app of ours in the image calls the projector HAL (hal_app none): nothing to check, exit 0
--dex (aidl-table only) also checks the compiled app, classes.dex or its APK, right after a build.
One front door for every HAL app of every device: hal_app and, for aidl-table, each APP:ALLOWED of hal_more (the X50
has two HAL apps, the eye guard and the menu, each with its own allow list; 2026-11-10). hal_unbanned (aidl-table,
t20): APP:UNBANNED entries, the maintainer's per-app exceptions to the banned table; the list is passed (--unbanned) to the
run of that one app only, never to another app of the device or to another device. --app APP picks one of them
(needed with --dex when the device has more than one). An app whose transport is the shared module apps/core/lumen-hal
is checked on its assembled tree, where check_aidl_hal.py finds the module layout by itself.
A hal_app built from a recipe (devices/<codename>/app/*.layers whose app is the hal_app's name, docs/architecture/
the design notes) is checked on its assembled tree (tools/devices/assemble_app.py into a scratch dir; a recipe still in compare
mode, 'identical allow', leaves the full app folder in place and is not used), which is exactly
what is compiled: nothing can reach the APK through a base layer unchecked.
The shipped checkers stay the source of truth: their output and exit status are passed through as they are,
nothing is re-implemented here. Exit 0 = every device green, 1 = red, 2 = usage.
"""
import os
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import devices  # noqa: E402


def entries(d):
    """[(app, allowed)] of every HAL app of the device: hal_app with hal_allowed, then each APP:ALLOWED of hal_more."""
    out = [(d["hal_app"], d.get("hal_allowed", "none"))]
    for ent in d.get("hal_more", "").split():
        ap, _, al = ent.partition(":")
        out.append((ap, al))
    return out


def assembled(root, d, scratch, app_rel=None):
    """The folder to check for a HAL app: its assembled tree when a recipe builds it, else the app folder."""
    import assemble_app  # noqa: E402
    app_rel = app_rel or d["hal_app"]
    name = os.path.basename(app_rel.rstrip("/"))
    recipe = assemble_app.find_recipe(root, d["codename"], name)
    if not recipe or assemble_app.parse(root, recipe)["identical"] == "allow":
        # no recipe, or one still in compare mode: the app folder is the full tree
        return os.path.join(root, app_rel), None
    out = os.path.join(scratch, d["codename"], name)
    r = subprocess.call([sys.executable, "-B", os.path.join(HERE, "assemble_app.py"), "--root", root, recipe,
                         "--out", out, "--quiet"])
    return out, (None if r == 0 else "check_hal: %s: the recipe %s does not assemble" % (d["codename"], recipe))


def unbanned_for(d, app_rel):
    """The hal_unbanned list of app_rel on this device (relative path), or None: never another app's list."""
    for ent in d.get("hal_unbanned", "").split():
        ap, _, ub = ent.partition(":")
        if ap.rstrip("/") == app_rel.rstrip("/"):
            return ub
    return None


def command(root, d, dex=None, app=None, allowed=None, app_rel=None):
    app_rel = app_rel or d["hal_app"]
    app = app or os.path.join(root, d["hal_app"])
    allowed = allowed or d.get("hal_allowed", "none")
    k = d["hal_check"]
    if k == "z9x-callers":
        return [os.path.join(root, "apps/check_hal_callers.py"), os.path.join(app, "src")]
    if k == "play6-hidl":
        return [os.path.join(root, "tools/play6/check_play6_hal.py"), app]
    cmd = [os.path.join(root, "tools/devices/check_aidl_hal.py"), "--app", app,
           "--codes", os.path.join(root, d["hal_codes"]), "--banned", os.path.join(root, d["hal_banned"])]
    for c in d.get("hal_codes_also", []):   # the other supported stock firmware: the same rows in every table
        cmd += ["--codes", os.path.join(root, c)]
    if allowed != "none":
        cmd += ["--allowed", os.path.join(root, allowed)]
    ub = unbanned_for(d, app_rel)
    if ub:
        cmd += ["--unbanned", os.path.join(root, ub)]
    return cmd + (["--dex", dex] if dex else [])


def main(argv):
    root = devices.ROOT
    if argv[:1] == ["--root"] and len(argv) > 1:
        root, argv = os.path.abspath(argv[1]), argv[2:]
    try:
        reg = devices.load(root)
    except devices.RegistryError as e:
        print("check_hal: ERROR %s" % e)
        return 1
    dex = only = None
    if len(argv) >= 4 and argv[0] == "--device" and argv[2] == "--app":
        only, argv = argv[3].rstrip("/"), argv[:2] + argv[4:]
    if len(argv) == 4 and argv[0] == "--device" and argv[2] == "--dex":
        dex, argv = os.path.abspath(argv[3]), argv[:2]
    if argv == ["--all"]:
        targets = sorted(reg)
    elif len(argv) == 2 and argv[0] == "--device":
        if argv[1] not in reg:
            print("check_hal: unknown device %r (devices.py list)" % argv[1])
            return 1
        targets = [argv[1]]
        if dex and reg[argv[1]]["hal_check"] != "aidl-table":
            print("check_hal: --dex is for hal_check aidl-table, %s has %s" % (argv[1], reg[argv[1]]["hal_check"]))
            return 2
    else:
        print(__doc__)
        return 2
    status = 0
    scratch = tempfile.mkdtemp(prefix="check_hal.")
    try:
        for c in targets:
            if len(targets) > 1:
                print("check_hal: %s (%s)" % (c, reg[c]["hal_check"]))
                sys.stdout.flush()
            if reg[c]["hal_check"] == "none":
                print("check_hal: %s: no HAL caller app in its image (hal_check none)" % c)
                continue
            ents = entries(reg[c])
            if only is not None:
                ents = [e for e in ents if e[0].rstrip("/") == only]
                if not ents:
                    print("check_hal: %s is no HAL app of %s (hal_app, hal_more)" % (only, c))
                    status = 1
                    continue
            if dex and len(ents) != 1:
                print("check_hal: --dex needs --app: %s has %d HAL apps" % (c, len(ents)))
                return 2
            for app_rel, allowed in ents:
                if len(ents) > 1:
                    print("check_hal: %s: %s" % (c, app_rel))
                    sys.stdout.flush()
                app, err = assembled(root, reg[c], scratch, app_rel)
                if err:
                    print(err)
                    status = 1
                    continue
                r = subprocess.call([sys.executable, "-B"] + command(root, reg[c], dex, app, allowed, app_rel))
                status = status or (1 if r else 0)
    finally:
        shutil.rmtree(scratch, ignore_errors=True)
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
