#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""check_images.py: every Lumen image carries what the shared remote pairing (apps/core/lumen-remote) needs, read from
the image recipes of the tree (no image, no device). Per model (MODELS below, data only):
  - the projector app's recipe names the module lumen-remote and its manifest requests the Bluetooth permissions the
    module uses (SCAN and CONNECT runtime, PRIVILEGED for createBond / setPairingConfirmation / HID host policy,
    BLUETOOTH for InputDevice.getBluetoothAddress, GRANT_RUNTIME_PERMISSIONS for the self-grant)
  - SCAN and CONNECT are granted at the first boot of a user (a DSU trial starts with empty data, so this is the
    trial case): a default-permissions file for the app's package that the recipe installs
  - BLUETOOTH_PRIVILEGED: platform signature (the app is signed with the image's platform key) or, for a priv-app on
    an image that enforces privapp permissions, the allowlist file the recipe installs
  - a Lumen key layout for every remote of devices/_remotes/remotes.tsv with lumen_kl=1 is installed by the recipe
    (any XGIMI remote works on any projector: its keys are mapped even if the stock model never shipped it)
usage: check_images.py [MODEL...]      MODEL = a key of MODELS or its codename (g0155 = rs30); exit 0 green, 1 red
"""
import os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "devices"))
import patch_file  # noqa: E402
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PERMS = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT",
         "android.permission.BLUETOOTH_PRIVILEGED", "android.permission.BLUETOOTH",
         "android.permission.GRANT_RUNTIME_PERMISSIONS"]

# model: (layers recipe, manifest, recipe files that install the key layouts, default-permissions (recipe, target,
# source xml), privileged route ("signature" or the privapp xml the recipe installs))
MODELS = {
    "z9x": ("devices/horse_popular/app/Z9xProjector.layers", "apps/LumenProjector/core/AndroidManifest.xml",
            ["tools/lumen_v1.sh"],
            ("tools/lumen_v1.sh", "system/product/etc/default-permissions/z9x-projector.xml",
             "overlay/v1/z9x-projector-default-permissions.xml"),
            "signature"),
    "hb": ("devices/goat_elegant/app/HbProjector.layers", "devices/goat_elegant/app/HbProjector.manifest.diff",
           ["devices/goat_elegant/image/steps.tsv"],
           ("devices/goat_elegant/image/steps.tsv", "{sys}/etc/default-permissions/z9x-projector-default-permissions.xml",
            "overlay/v1/z9x-projector-default-permissions.xml"),
           "devices/goat_elegant/image/privapp-permissions-hbprojector.xml"),
    "play6": ("devices/dragon_healthy/app/Play6Projector.layers", "devices/dragon_healthy/app/Play6Projector.manifest.xml",
              ["devices/dragon_healthy/image/steps.tsv"],
              ("devices/dragon_healthy/image/steps.tsv", "{sys}/product/etc/default-permissions/z9x-projector.xml",
               "overlay/v1/z9x-projector-default-permissions.xml"),
              "signature"),
    # the X50 and the Ultra Max start from the published Z9X 1.0.9 image (devices/g0111/image/base.sha256), whose key
    # layouts tools/lumen_v1.sh installed
    "x50": ("devices/_family/mt9681/app/X50Projector.layers", "apps/LumenProjector/host/activity/AndroidManifest.xml",
            ["tools/lumen_v1.sh"],
            ("devices/g0111/image/steps.tsv", "{sys}/product/etc/default-permissions/x50-projector.xml",
             "apps/LumenProjector/host/activity/image/x50-projector.xml"),
            "signature"),
    "x50max": ("devices/_family/mt9681/app/X50Projector.layers", "apps/LumenProjector/host/activity/AndroidManifest.xml",
               ["tools/lumen_v1.sh"],
               ("devices/g0111/image/steps.tsv", "{sys}/product/etc/default-permissions/x50-projector.xml",
                "apps/LumenProjector/host/activity/image/x50-projector.xml"),
               "signature"),
    "rs30": ("devices/_family/mt9681/app/X50Projector.layers", "apps/LumenProjector/host/activity/AndroidManifest.xml",
             ["devices/g0155/image/steps.tsv"],
             ("devices/g0155/image/steps.tsv", "{sys}/product/etc/default-permissions/x50-projector.xml",
              "apps/LumenProjector/host/activity/image/x50-projector.xml"),
             "signature"),
}


def read(rel):
    return open(os.path.join(ROOT, rel), encoding="utf-8").read()


def table_layouts():
    """Vendor_<vid>_Product_<pid> of every remotes.tsv row with lumen_kl=1."""
    rows = [l.split("\t") for l in read("devices/_remotes/remotes.tsv").splitlines() if l and not l.startswith("#")]
    head = rows[0]
    vp, kl = head.index("vid_pid"), head.index("lumen_kl")
    out = []
    for r in rows[1:]:
        if r[kl] == "1" and r[vp] != "-":
            v, p = r[vp].split(":")
            out.append("Vendor_%s_Product_%s" % (v, p))
    return out


def check(model):
    layers, manifest, kl_src, grant, priv = MODELS[model]
    bad = []
    if not re.search(r"(?m)^module\s+lumen-remote\s*$", read(layers)):
        bad.append("%s does not name module lumen-remote" % layers)
    man = read(manifest)
    if manifest.endswith(".diff"):     # the HB: the core manifest plus its patch
        man = patch_file.apply(read("apps/LumenProjector/core/AndroidManifest.xml"), man)
    m = re.search(r'package="([^"]+)"', man)
    pkg = m.group(1) if m else "?"
    for p in PERMS:
        if 'android:name="%s"' % p not in man:
            bad.append("%s does not request %s" % (manifest, p))
    text = "".join(read(f) for f in kl_src)
    for k in table_layouts():
        if k not in text or not os.path.isfile(os.path.join(ROOT, "overlay/v1/keylayout/%s.kl" % k)):
            bad.append("key layout %s.kl is not installed by %s" % (k, " ".join(kl_src)))
    recipe, target, src = grant
    if target not in read(recipe):
        bad.append("%s does not install %s" % (recipe, target))
    xml = read(src)
    ex = re.search(r'<exception\s+package="%s"\s*>(.*?)</exception>' % re.escape(pkg), xml, re.S)
    granted = set(re.findall(r'<permission\s+name="([^"]+)"', ex.group(1))) if ex else set()
    for p in PERMS[:2]:
        if p not in granted:
            bad.append("%s does not grant %s to %s" % (src, p, pkg))
    if priv != "signature":
        px = read(priv)
        blk = re.search(r'<privapp-permissions\s+package="%s"\s*>(.*?)</privapp-permissions>' % re.escape(pkg), px, re.S)
        if not blk or "android.permission.BLUETOOTH_PRIVILEGED" not in blk.group(1):
            bad.append("%s does not allow BLUETOOTH_PRIVILEGED for %s" % (priv, pkg))
        if os.path.basename(priv) not in read(recipe):
            bad.append("%s does not install %s" % (recipe, os.path.basename(priv)))
    print("check_images: %-7s %-24s %s" % (model, pkg, "OK" if not bad else "RED"))
    for b in bad:
        print("check_images:   " + b)
    return not bad


def codename(model):
    """The device folder of the model's projector app recipe (devices/<codename>/app/...)."""
    return MODELS[model][0].split("/")[1]


def main(names):
    alias = {codename(m): m for m in MODELS}
    todo = [alias.get(n, n) for n in names] or list(MODELS)
    for n in todo:
        if n not in MODELS:
            print("check_images: unknown model %s (%s, or a codename: %s)" % (n, " ".join(MODELS), " ".join(alias)))
            return 2
    ok = [check(n) for n in todo]
    print("check_images: %d model(s), %d layouts per image, %s" % (len(todo), len(table_layouts()),
                                                                   "OK" if all(ok) else "RED"))
    return 0 if all(ok) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
