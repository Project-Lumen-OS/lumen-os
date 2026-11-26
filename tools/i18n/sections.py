#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""sections.py: the section names of the translation sources apps/i18n/langs and apps/i18n/pending/*.

A strings file listed in apps/common/projector/FILES (shared by the projector recipes) is a @common section, never
@projector; every @common section names a listed file. The Play 6 texts are core feature files
(apps/LumenProjector/core.features texts-play6): @projector/strings_p6.xml, no @play6 section any more.
apps/i18n/gen.py --check-only checks the texts themselves. Exit 0 = green, 1 = red.
"""
import glob
import os
import re
import sys

sys.dont_write_bytecode = True
G = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
I18N = os.path.join(G, "apps", "i18n")
sys.path.insert(0, I18N)
import gen  # noqa: E402  (read-only: its list of the shared files)

SECTION = re.compile(r"^@([^/\s]+)/(\S+)\s*$")


def main():
    errs = []
    shared = {p[len("res/values/"):] for p in gen.shared_files() if re.match(r"^res/values/strings[^/]*\.xml$", p)}
    srcs = sorted(glob.glob(os.path.join(I18N, "langs", "*.txt"))) + \
        sorted(glob.glob(os.path.join(I18N, "pending", "*", "*.txt")))
    n = 0
    for path in srcs:
        rel = os.path.relpath(path, G)
        with open(path, encoding="utf-8") as fh:
            for ln, line in enumerate(fh, 1):
                m = SECTION.match(line.rstrip("\n"))
                if not m:
                    continue
                n += 1
                app, f = m.groups()
                if app == "projector" and f in shared:
                    errs.append("%s:%d: @projector/%s is a shared file (apps/common/projector/FILES): use @common/%s"
                                % (rel, ln, f, f))
                elif app == "common" and f not in shared:
                    errs.append("%s:%d: @common/%s is not listed in apps/common/projector/FILES" % (rel, ln, f))
                elif app == "play6":
                    errs.append("%s:%d: the Play 6 texts are the section @projector/strings_p6.xml" % (rel, ln))
    for e in errs:
        print("sections: ERROR " + e)
    print("sections: %d sections checked, %d shared strings files (target common): %s"
          % (n, len(shared), "FAILED" if errs else "OK"))
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
