#!/usr/bin/env python3
"""menu_ids.py FEATURE_ID_JAVA MENU_JSON...: every feature and hint a lumen-menu profile names is an id of the
module's FeatureId enum. apps/build_apk.sh runs it on every app tree with a menu profile, so
a typo in devices/<codename>/menu.json fails the build instead of showing no menu on the device. Exit 1 on any
unknown id."""
import json
import re
import sys

ENTRY = re.compile(r'^\s+[A-Z][A-Z0-9_]*\("([a-z0-9_.]+)", FeatureKind\.[A-Z]+\)[,;]', re.M)


def ids(java):
    with open(java, encoding="utf-8") as f:
        out = set(ENTRY.findall(f.read()))
    if not out:
        sys.exit("menu_ids: no FeatureId entries in %s" % java)
    return out


def unknown(known, path):
    with open(path, encoding="utf-8") as f:
        profile = json.load(f)
    named = list(profile.get("features") or {}) + list(profile.get("hints") or {})
    return [i for i in named if i not in known]


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    known, bad = ids(argv[1]), 0
    for path in argv[2:]:
        for i in unknown(known, path):
            print("menu_ids: %s: unknown feature \"%s\" (not in FeatureId)" % (path, i), file=sys.stderr)
            bad = 1
    return bad


if __name__ == "__main__":
    sys.exit(main(sys.argv))
