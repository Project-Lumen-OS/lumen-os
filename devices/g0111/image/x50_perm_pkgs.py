#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""x50_perm_pkgs.py: every permission file of the X50 image names packages the image has (t11 review P0.1d).

  x50_perm_pkgs.py NEW_ROOT [BASE_ROOT]     NEW_ROOT / BASE_ROOT = trees holding system/ (staged tree or mounted image)

A default-permissions file (<exception package=...>) or a privapp / sysconfig file (<privapp-permissions package=...>)
that names a package missing from the image grants nothing, silently: after X50Remote was merged into X50Projector a
file still naming the old package would have left the remote without Bluetooth permissions at first boot. Rule: every
package named by such a file that is NEW or CHANGED against the base (all files when no base is given) must be the
package of an APK of the image (system, product and system_ext app / priv-app, framework-res = "android"). Files kept
byte-equal from the Z9X base are only counted (the base names Google and phone packages a TV GSI never ships).
verify_tree_x50.py runs problems() on the staged tree and on the image; devices/g0111/test/x50_perm_pkgs_test.sh tests
it on the Mac. apkinfo.py (tools/lumen, read only, staged next to this file) parses the binary manifests.
Exit 0 = OK, 1 = a file names a missing package, 2 = usage.
"""
import hashlib
import os
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
for _d in (HERE, os.path.join(HERE, "..", "..", "..", "tools", "lumen")):
    if os.path.isfile(os.path.join(_d, "apkinfo.py")):
        sys.path.insert(0, os.path.abspath(_d))
        break
import apkinfo  # noqa: E402  (read only)

PARTS = ("system", "system/product", "system/system_ext")
APP_DIRS = ("app", "priv-app", "framework")
PERM_DIRS = ("etc/default-permissions", "etc/permissions", "etc/sysconfig")
TAGS = {"exception": "default-permissions", "privapp-permissions": "privapp", "signature-permissions": "privapp"}


def _sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def packages(root):
    """package name -> relative APK path of every APK in the app / priv-app / framework folders of the tree"""
    out = {}
    for part in PARTS:
        for sub in APP_DIRS:
            top = os.path.join(root, part, sub)
            if not os.path.isdir(top):
                continue
            for d, _dirs, files in os.walk(top):
                for n in files:
                    if not n.endswith(".apk"):
                        continue
                    p = os.path.join(d, n)
                    if os.path.islink(p):
                        continue
                    try:
                        with zipfile.ZipFile(p) as z:
                            pkg = apkinfo.manifest_facts(z.read("AndroidManifest.xml")).get("package")
                    except Exception:  # noqa: BLE001  (an unreadable APK names nothing; verify_tree checks APKs)
                        continue
                    if pkg:
                        out.setdefault(pkg, os.path.relpath(p, root))
    return out


def perm_files(root):
    """relative path -> absolute path of every .xml in the permission folders of the tree"""
    out = {}
    for part in PARTS:
        for sub in PERM_DIRS:
            top = os.path.join(root, part, sub)
            if not os.path.isdir(top):
                continue
            for n in sorted(os.listdir(top)):
                p = os.path.join(top, n)
                if n.endswith(".xml") and os.path.isfile(p) and not os.path.islink(p):
                    out[os.path.relpath(p, root)] = p
    return out


def named(path):
    """[(kind, package)] named by one permission file; raises ValueError when it is not XML"""
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        raise ValueError(str(e))
    out = []
    for el in tree.iter():
        if el.tag in TAGS and el.get("package"):
            out.append((TAGS[el.tag], el.get("package")))
    return out


def problems(new_root, base_root=None):
    """(bad, summary): bad lists every NEW / CHANGED permission file naming a package the tree lacks"""
    bad = []
    pk = packages(new_root)
    if not pk:
        bad.append("no APK found under %s/system (not a system tree): nothing could be checked" % new_root)
    pk.setdefault("android", "system/framework/framework-res.apk")
    base = perm_files(base_root) if base_root else {}
    if base_root and not base:
        bad.append("no permission file found under %s/system (not the base tree)" % base_root)
    checked = kept = kept_missing = 0
    for rel, p in sorted(perm_files(new_root).items()):
        b = base.get(rel)
        same = b is not None and _sha(b) == _sha(p)
        try:
            names = named(p)
        except ValueError as e:
            bad.append("%s: not readable XML (%s)" % (rel, e))
            continue
        if same:
            kept += 1
            kept_missing += sum(1 for _k, n in names if n not in pk)
            continue
        checked += 1
        for kind, n in names:
            if n not in pk:
                bad.append("%s (%s) names %s, which is not in the image" % (rel, kind, n))
    summary = ("permission files: %d new or changed checked, %d kept from the base (%d names there not in the image, "
               "base as is), %d packages in the image" % (checked, kept, kept_missing, len(pk)))
    return bad, summary


def main(a):
    if not a or len(a) > 2 or a[0].startswith("-"):
        print((__doc__ or "").split("\n\n")[1])
        return 2
    bad, summary = problems(a[0], a[1] if len(a) > 1 else None)
    print("x50_perm_pkgs: " + summary)
    for b in bad:
        print("x50_perm_pkgs: ERROR " + b)
    print("x50_perm_pkgs: " + ("OK" if not bad else "FAILED (%d)" % len(bad)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
