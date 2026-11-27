#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The Android power menu is one definition for every model.

Android's own power menu (the held KEYCODE_POWER where a family maps it to the menu) takes its entries from
config_globalActionsList and its power off / restart screen from layout/shutdown_dialog. Both live once in
apps/common/overlay (values/lumen_power_menu.xml, layout/shutdown_dialog.xml) and reach every model through the one
framework RRO its image ships (sync.sh item `power`). Red when:
  - the shared list is not exactly power, restart (decision 2026-10-04: Power off and Restart; everything else
    is in Lumen's own power menu),
  - a framework RRO a model ships lacks a copy or holds a changed one,
  - any other resource file defines config_globalActionsList (a family's own list again),
  - a model ships no framework RRO or more than one.

  python3 apps/common/test/power_menu_test.py [--self-test]
"""
import os, re, shutil, sys, tempfile

G = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
SHARED = {"res/values/lumen_power_menu.xml": "apps/common/overlay/values/lumen_power_menu.xml",
          "res/layout/shutdown_dialog.xml": "apps/common/overlay/layout/shutdown_dialog.xml"}
ENTRIES = ["power", "restart"]
ARRAY = re.compile(r'<string-array\s+name="config_globalActionsList"[^>]*>(.*?)</string-array>', re.S)


def conf(root, path, seen=()):
    """key -> value of a build.conf with its include chain (the including file wins)."""
    if path in seen:
        raise ValueError("include loop at " + path)
    own, inc = {}, []
    for l in open(os.path.join(root, path), encoding="utf-8"):
        l = l.strip()
        if not l or l.startswith("#") or "=" not in l:
            continue
        k, v = l.split("=", 1)
        if k == "include":
            inc.append(v)
        else:
            own[k] = v
    out = {}
    for i in inc:
        out.update(conf(root, i, seen + (path,)))
    out.update(own)
    return out


def models(root):
    """{codename: [apps]} of every model folder with a build.conf."""
    d = os.path.join(root, "devices")
    return {m: conf(root, "devices/%s/build.conf" % m).get("apps", "").split()
            for m in sorted(os.listdir(d))
            if not m.startswith("_") and os.path.isfile(os.path.join(d, m, "build.conf"))}


def framework_rro(root, app):
    m = os.path.join(root, "apps", app, "AndroidManifest.xml")
    if not os.path.isfile(m):
        return False
    s = open(m, encoding="utf-8").read()
    return re.search(r'targetPackage="android"', s) is not None and "<overlay" in s


def check(root):
    errs = []
    src = os.path.join(root, SHARED["res/values/lumen_power_menu.xml"])
    m = ARRAY.search(open(src, encoding="utf-8").read()) if os.path.isfile(src) else None
    got = re.findall(r"<item>\s*([^<\s]+)\s*</item>", m.group(1)) if m else None
    if got != ENTRIES:
        errs.append("%s: config_globalActionsList is %s, not %s" % (SHARED["res/values/lumen_power_menu.xml"], got, ENTRIES))
    rros = set()
    ms = models(root)
    for name, apps in ms.items():
        mine = [a for a in apps if framework_rro(root, a)]
        if len(mine) != 1:
            errs.append("%s ships %d framework RROs (%s), not one" % (name, len(mine), " ".join(mine) or "none"))
        rros.update(mine)
    for a in sorted(rros):
        for dst, s in SHARED.items():
            p = os.path.join(root, "apps", a, dst)
            if not os.path.isfile(p):
                errs.append("apps/%s/%s missing (sync.sh apps/%s power)" % (a, dst, a))
            elif open(p, "rb").read() != open(os.path.join(root, s), "rb").read():
                errs.append("apps/%s/%s differs from %s" % (a, dst, s))
    own = set("apps/%s/%s" % (a, "res/values/lumen_power_menu.xml") for a in rros)
    own.add(SHARED["res/values/lumen_power_menu.xml"])
    for top, _, files in os.walk(os.path.join(root, "apps")):
        for f in files:
            if not f.endswith(".xml") or "/res/values" not in top + "/" and "/overlay/values" not in top:
                continue
            rel = os.path.relpath(os.path.join(top, f), root)
            if rel in own:
                continue
            if "config_globalActionsList" in open(os.path.join(top, f), encoding="utf-8", errors="replace").read() \
                    and ARRAY.search(open(os.path.join(top, f), encoding="utf-8", errors="replace").read()):
                errs.append("%s defines config_globalActionsList: the list lives only in %s"
                            % (rel, SHARED["res/values/lumen_power_menu.xml"]))
    return errs, len(ms), sorted(rros)


def self_test():
    """Each mutant of a scratch copy (apps/common, the framework RROs, devices) must turn the check red."""
    base = tempfile.mkdtemp(prefix="pmt_")
    try:
        def fresh():
            t = os.path.join(base, "t")
            shutil.rmtree(t, ignore_errors=True)
            shutil.copytree(os.path.join(G, "devices"), os.path.join(t, "devices"),
                            ignore=shutil.ignore_patterns("_baseline", "research", "*.png", "*.zip", "*.img"))
            for a in ["common"] + sorted({x for v in models(G).values() for x in v if framework_rro(G, x)}):
                shutil.copytree(os.path.join(G, "apps", a), os.path.join(t, "apps", a))
            return t

        def edit(t, rel, old, new):
            p = os.path.join(t, rel)
            s = open(p, encoding="utf-8").read()
            assert old in s, (rel, old)
            open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))

        mutants = [
            ("a third entry in the shared list", lambda t: edit(t, SHARED["res/values/lumen_power_menu.xml"],
                                                                 "<item>restart</item>", "<item>restart</item><item>screenshot</item>"),
             "not ['power', 'restart']"),
            ("restart dropped from the shared list", lambda t: edit(t, SHARED["res/values/lumen_power_menu.xml"],
                                                                     "<item>restart</item>", ""), "not ['power', 'restart']"),
            ("the X50 copy gone", lambda t: os.remove(os.path.join(t, "apps/X50PowerOverlay/res/values/lumen_power_menu.xml")),
             "apps/X50PowerOverlay/res/values/lumen_power_menu.xml missing"),
            ("the X50 shutdown screen gone", lambda t: os.remove(os.path.join(t, "apps/X50PowerOverlay/res/layout/shutdown_dialog.xml")),
             "apps/X50PowerOverlay/res/layout/shutdown_dialog.xml missing"),
            ("the Play 6 copy edited by hand", lambda t: edit(t, "apps/Play6FrameworkOverlay/res/values/lumen_power_menu.xml",
                                                               "<item>power</item>", "<item>power</item><item>logout</item>"),
             "apps/Play6FrameworkOverlay/res/values/lumen_power_menu.xml differs"),
            ("the HB shutdown screen edited", lambda t: edit(t, "apps/HbFrameworkOverlay/res/layout/shutdown_dialog.xml",
                                                              "#FF000000", "#FF000001"), "apps/HbFrameworkOverlay/res/layout/shutdown_dialog.xml differs"),
            ("a family list back in the Z9X config.xml", lambda t: edit(t, "apps/Z9xFrameworkKeysOverlay/res/values/config.xml",
                                                                         "<resources>", '<resources><string-array name="config_globalActionsList"><item>power</item></string-array>'),
             "apps/Z9xFrameworkKeysOverlay/res/values/config.xml defines config_globalActionsList"),
            ("a model without its framework RRO", lambda t: edit(t, "devices/_family/rs30/rs30.build.conf", "X50PowerOverlay ", ""),
             "ships 0 framework RROs"),
            ("a model with two framework RROs", lambda t: edit(t, "devices/goat_elegant/build.conf", "HbFrameworkOverlay ",
                                                                "HbFrameworkOverlay Z9xFrameworkKeysOverlay "), "ships 2 framework RROs"),
        ]
        bad = 0
        for name, fn, want in mutants:
            t = fresh()
            fn(t)
            errs, _, _ = check(t)
            hit = any(want in e for e in errs)
            print("  %s: %s" % ("killed" if hit else "SURVIVED", name))
            bad += not hit
        t = fresh()
        errs, n, _ = check(t)
        if errs:
            print("  the unchanged scratch copy is red: %s" % errs[0])
            bad += 1
        print("self-test: %d of %d mutants killed" % (len(mutants) - bad, len(mutants)))
        return bad
    finally:
        shutil.rmtree(base, ignore_errors=True)


def main(argv):
    errs, n, rros = check(G)
    for e in errs:
        print("power_menu: RED " + e)
    print("power_menu: %d models, framework RROs %s, one list (%s) and one power off / restart screen: %s"
          % (n, " ".join(rros), ", ".join(ENTRIES), "RED" if errs else "OK"))
    rc = 1 if errs else 0
    if "--self-test" in argv:
        rc |= 1 if self_test() else 0
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
