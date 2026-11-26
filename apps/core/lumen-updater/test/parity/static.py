#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""static.py TREE: the static face of one assembled updater, one line per fact, for test/parity/run.sh.

  manifest  the AndroidManifest.xml without comments and with whitespace collapsed (package, uid, permissions,
            the SYSTEM_UPDATE_SETTINGS entry of TvSettings, components)
  res       every file of res/ that is not a values file: path + sha256 (icons, drawables)
  value     every entry of res/values*/*.xml: folder, tag, name, text (strings in every language, colours, themes)

The parity rule (run.sh): every manifest and res line of the recorded app must be there unchanged; value lines may
only be added (a shared string set may carry texts another model uses), never changed or dropped.
"""
import hashlib
import os
import re
import sys
import xml.etree.ElementTree as ET


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def main(tree):
    out = []
    man = open(os.path.join(tree, "AndroidManifest.xml"), encoding="utf-8").read()
    man = re.sub(r"<!--.*?-->", "", man, flags=re.S)
    out.append("manifest " + norm(man))
    res = os.path.join(tree, "res")
    for root, dirs, files in os.walk(res):
        dirs.sort()
        for f in sorted(files):
            p = os.path.join(root, f)
            rel = os.path.relpath(p, res)
            folder = rel.split(os.sep)[0]
            if folder.startswith("values") and f.endswith(".xml"):
                for el in ET.parse(p).getroot():
                    if not isinstance(el.tag, str):
                        continue
                    text = ET.tostring(el, encoding="unicode")
                    text = re.sub(r"^<[^>]*>|</[^>]*>\s*$", "", text.strip())
                    out.append("value %s %s %s = %s" % (folder, el.tag, el.get("name"), norm(text)))
            else:
                h = hashlib.sha256(open(p, "rb").read()).hexdigest()
                out.append("res %s %s" % (rel, h))
    print("\n".join(sorted(out)))


if __name__ == "__main__":
    main(sys.argv[1])
