#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""airplay_source.py: the Corresponding Source of the AirPlay receiver (GPL-3.0) as one zip, for EVERY release of
every model whose image ships it (every model since 2026-11-21: screen casting is shared). GPL-3.0 section 6(d):
the source sits next to the image at the same place (the GitHub release), named lumen-os-<version>-airplay-source.zip.

  airplay_source.py [--root DIR] OUT.zip

The zip holds what the APK is built from, as apps/LumenAirPlay/app.layers assembles it: the app (Java, resources,
assets, manifest, NOTICE, LICENSE), its native part (jni/: the glue, the patches, build_native.sh that fetches
UxPlay, libplist, BoringSSL and ALAC at the commits of jni/BUILDINFO.txt, LIBS.sha256), the shared modules the app
compiles in (apps/core/lumen-ui, apps/core/lumen-icons), the recipe and the APK build script (apps/build_apk.sh,
tools/devices/assemble_app.py). No binary (lib/, *.so, *.apk, the font file: its licence is in), fixed timestamps and
sorted entries, so the same tree always gives the same bytes. Exit 0 and the sha256 on stdout.
"""
import argparse
import hashlib
import os
import sys
import zipfile

PARTS = ("apps/LumenAirPlay", "apps/core/lumen-ui", "apps/core/lumen-icons")
FILES = ("apps/build_apk.sh", "tools/devices/assemble_app.py", "apps/app_dirs.tsv")
SKIP_EXT = (".so", ".apk", ".ttf", ".otf", ".pyc", ".DS_Store")
STAMP = (1980, 1, 1, 0, 0, 0)
README = """AirPlay receiver of Lumen OS (org.lumen.airplay): complete source, GPL-3.0 (apps/LumenAirPlay/LICENSE).

Build: python3 tools/devices/assemble_app.py apps/LumenAirPlay/app.layers --out TREE, put the native library into
TREE/lib/<abi>/ (apps/LumenAirPlay/jni/build_native.sh with Android NDK r29 builds it from the upstream sources at
the commits of jni/BUILDINFO.txt), then sh apps/build_apk.sh TREE Z9xAirPlay.apk. The Inter font the build copies in
is from github.com/rsms/inter (SIL Open Font License 1.1, assets/licenses/OFL-1.1-Inter.txt in the app).
"""


def entries(root):
    out = []
    for part in PARTS:
        top = os.path.join(root, part)
        for d, dirs, files in os.walk(top):
            rel_d = os.path.relpath(d, top)
            skip = {"build", "__pycache__"} | ({"lib"} if rel_d == "." else set())
            if part != "apps/LumenAirPlay" and rel_d == ".":
                skip |= {"test", "tools"}      # a module's tests and tools are not compiled into the APK
            dirs[:] = sorted(x for x in dirs if x not in skip)
            for f in sorted(files):
                if f.endswith(SKIP_EXT) or f.startswith("._"):
                    continue
                out.append(os.path.relpath(os.path.join(d, f), root))
    out.extend(FILES)
    return sorted(set(out))


def build(root, dest):
    names = entries(root)
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        info = zipfile.ZipInfo("README-SOURCE.txt", STAMP)
        info.external_attr = 0o644 << 16
        z.writestr(info, README, zipfile.ZIP_DEFLATED)
        for n in names:
            info = zipfile.ZipInfo(n, STAMP)
            mode = 0o755 if n.endswith(".sh") else 0o644
            info.external_attr = mode << 16
            with open(os.path.join(root, n), "rb") as f:
                z.writestr(info, f.read(), zipfile.ZIP_DEFLATED)
    return names


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    ap.add_argument("out")
    a = ap.parse_args(argv)
    names = build(a.root, a.out)
    h = hashlib.sha256(open(a.out, "rb").read()).hexdigest()
    print("airplay_source: %s %d files sha256 %s" % (a.out, len(names), h))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
