#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Tests of tools/devices/cast_models.py (screen casting on every model) and tools/devices/airplay_source.py (the
GPL source zip). python3 tools/devices/test/cast_models_test.py ; exit 0 = all passed."""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import airplay_source  # noqa: E402
import cast_models  # noqa: E402

fails = runs = 0


def ok(what, cond):
    global fails, runs
    runs += 1
    if not cond:
        fails += 1
        print("FAIL " + what)


def copy_tree():
    """The parts of the tree cast_models reads: devices/*/{build,device,cast}.conf + menu.json and the table."""
    t = tempfile.mkdtemp()
    for c in os.listdir(os.path.join(G, "devices")):
        src = os.path.join(G, "devices", c)
        if not os.path.isfile(os.path.join(src, "build.conf")):
            continue
        os.makedirs(os.path.join(t, "devices", c))
        for f in ("build.conf", "device.conf", "cast.conf", "menu.json"):
            if os.path.isfile(os.path.join(src, f)):
                shutil.copy(os.path.join(src, f), os.path.join(t, "devices", c, f))
    # the family files the descriptors include (devices/_family, tools/devices/layered.py)
    shutil.copytree(os.path.join(G, "devices", "_family"), os.path.join(t, "devices", "_family"))
    shutil.copytree(os.path.join(G, "devices", "_vendor"), os.path.join(t, "devices", "_vendor"))   # the vendor layer
    shutil.copytree(os.path.join(G, "devices", "_menu"), os.path.join(t, "devices", "_menu"))   # the menu base layer
    os.makedirs(os.path.join(t, os.path.dirname(cast_models.TABLE)))
    shutil.copy(os.path.join(G, cast_models.TABLE), os.path.join(t, cast_models.TABLE))
    return t


def edit(root, rel, old, new):
    p = os.path.join(root, rel)
    s = open(p, encoding="utf-8").read()
    assert old in s, (rel, old)
    open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))


def red(root):
    return bool(cast_models.problems(root)) or open(os.path.join(root, cast_models.TABLE)).read() != cast_models.table(root)


def main():
    ok("the tree is green", not red(G))
    devs = cast_models.devices(G)
    # every registry model with a build recipe, by the registry's own reader (no hand count: G0170 made it ten)
    import devices as registry
    ok("every shipping device", devs == sorted(c for c in registry.load(G)
                                               if os.path.isfile(os.path.join(G, "devices", c, "build.conf"))) and len(devs) >= 10)
    ok("Play 6 offers no 4K", "G0058\tdragon_healthy\tarm32\t0" in cast_models.table(G))
    cases = [
        ("a model without Miracast", "devices/g0111/cast.conf", "miracast=1", "miracast=0"),
        ("a model without AirPlay", "devices/h0063/cast.conf", "airplay=1", "airplay=0"),
        ("an unknown cast key", "devices/goat_elegant/cast.conf", "mirror_4k=1", "mirror_4k=1\nhdcp=1"),
        ("a bad 4K flag", "devices/horse_popular/cast.conf", "mirror_4k=1", "mirror_4k=yes"),
        ("an HB recipe without the sink", "devices/goat_elegant/build.conf", " LumenMiracast ", " "),
        ("a TITAN recipe without AirPlay", "devices/_family/titan/titan.build.conf", " Z9xAirPlay ", " "),
        ("an X50 recipe without the P2P overlay", "devices/_family/x50ultra/x50ultra.build.conf", " LumenWifiP2pOverlay ", " "),
        ("a recipe without the sink permissions", "devices/_family/rs30/rs30.build.conf",
         " overlay/v1/privapp-permissions-lumen-miracast.xml", ""),
        ("a model without Lumen Play (DLNA)", "devices/g0100/build.conf", "include=devices/_family/x50ultra/x50ultra.build.conf\n", "include=devices/_family/x50ultra/x50ultra.build.conf\nprebuilt=\n"),
        ("a profile without the casting row", "devices/dragon_healthy/menu.json", '  "off": {\n', '  "off": {\n    "settings.casting": "mutant",\n'),
        ("a profile without the mirroring tile", "devices/h0062/menu.json", '"include": "devices/_family/titan/titan.menu.json",\n',
         '"include": "devices/_family/titan/titan.menu.json",\n  "off": {"mirroring": "mutant"},\n'),
        ("a changed 4K flag with an old table", "devices/_family/rs30/cast.conf", "mirror_4k=1", "mirror_4k=0"),
    ]
    for what, rel, old, new in cases:
        t = copy_tree()
        try:
            edit(t, rel, old, new)
            ok("red: " + what, red(t))
        finally:
            shutil.rmtree(t)
    t = copy_tree()
    try:
        os.remove(os.path.join(t, "devices", "g0111", "cast.conf"))
        ok("red: a device without cast.conf", red(t))
    finally:
        shutil.rmtree(t)
    # the command line: check is green on the tree
    r = subprocess.run([sys.executable, "-B", os.path.join(G, "tools", "devices", "cast_models.py"), "--root", G, "check"],
                       capture_output=True, text=True)
    ok("check exit 0 on the tree: " + r.stdout[-200:], r.returncode == 0)
    # the source zip: the app, its native part, the modules, no binaries, same bytes twice
    d = tempfile.mkdtemp()
    try:
        a, b = os.path.join(d, "a.zip"), os.path.join(d, "b.zip")
        airplay_source.build(G, a)
        airplay_source.build(G, b)
        ok("source zip is reproducible", open(a, "rb").read() == open(b, "rb").read())
        names = zipfile.ZipFile(a).namelist()
        for need in ("apps/LumenAirPlay/src/org/lumen/airplay/CastActivity.java", "apps/LumenAirPlay/LICENSE",
                     "apps/LumenAirPlay/jni/build_native.sh", "apps/LumenAirPlay/jni/BUILDINFO.txt",
                     "apps/LumenAirPlay/app.layers", "apps/core/lumen-ui/src/org/lumen/ui/Palette.java",
                     "apps/core/lumen-icons/res/drawable/lumen_ic_tiles.xml", "apps/build_apk.sh", "README-SOURCE.txt",
                     "apps/LumenAirPlay/test/run.sh"):
            ok("source zip has " + need, need in names)
        ok("source zip has the patches", any(n.startswith("apps/LumenAirPlay/jni/patches/uxplay/") for n in names))
        ok("source zip has no binary", not any(n.endswith((".so", ".apk", ".ttf")) or "/lib/" in n for n in names))
    finally:
        shutil.rmtree(d)
    print("%s cast_models_test: %d/%d" % ("OK" if fails == 0 else "RED", runs - fails, runs))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
