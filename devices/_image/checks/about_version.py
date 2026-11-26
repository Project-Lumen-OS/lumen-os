#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""about_version.py: the Lumen OS version row of Settings > About, one rule for every image.

TvSettings shows ro.lineage.display.version in the row the overlay Z9xLineagePlatformOverlay names Lumen OS version.
Every image shows its own release name there, the value of ro.lumen.version, followed by the maintainer:
"1.0.10-beta13 (kmuradoff)". The Z9X 1.0.9 base that the X50 family images start from carries "1.0.9 (kmuradoff)",
so an image recipe that forgets the row shows the base's version (X50 beta13 did).

  about_version.py check SYSTEM_PROP PRODUCT_PROP    exit 0 when the row equals ro.lumen.version (later file wins)

Recipes import line(version); a recipe never writes its own text for the row. Standard library only.
"""
import re
import sys

KEY = "ro.lineage.display.version"
VERSION_KEY = "ro.lumen.version"


def value(version):
    """the row's text for the release name version"""
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._\-]*", version or ""):
        raise ValueError("not a release name: %r" % (version,))
    return "%s (kmuradoff)" % version


def line(version):
    return "%s=%s" % (KEY, value(version))


def props(*texts):
    """the properties of build.prop texts as init reads them: a later line (and a later file) wins"""
    out = {}
    for t in texts:
        for l in t.splitlines():
            k, eq, v = l.strip().partition("=")
            if eq and not k.startswith("#"):
                out[k] = v
    return out


def problems(p):
    """[] when the row of the properties p equals their release name"""
    v = p.get(VERSION_KEY, "")
    if not v:
        return ["no %s" % VERSION_KEY]
    if p.get(KEY) != value(v):
        return ["About shows %r, the release is %r (want %r)" % (p.get(KEY), v, value(v))]
    return []


def main(a):
    if len(a) >= 2 and a[0] == "check":
        bad = problems(props(*(open(f, encoding="utf-8", errors="replace").read() for f in a[1:])))
        for b in bad:
            print("about_version: ERROR " + b)
        if not bad:
            print("about_version: About shows the release name")
        return 1 if bad else 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
