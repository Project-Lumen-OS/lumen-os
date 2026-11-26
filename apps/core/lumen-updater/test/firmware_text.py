#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""firmware_text.py: the firmware refusal of the shared updater in every language it ships (test/run.sh).

  firmware_text.py RES

A newer release that does not list this projector's firmware shows blk_vendor (a short title, no placeholder) and
under it blk_vendor_body: %2$s = the firmware versions the release supports, %1$s = the projector's own firmware
(Preflight.applicable stores them as one argument, own first). Every res/values*/strings.xml has both texts, the
placeholders exactly so, plain words only: no dash, no semicolon, no slot, partition, vbmeta or DSU, and the body
says what to do (it names Lumen OS, the update to wait for). Exit 0 green, 1 red.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

PH = re.compile(r"%(\d+\$)?[sd]")
BANNED = re.compile(r"slot|partition|vbmeta|\bdsu\b", re.I)
PUNCT = re.compile(r"[;\-\u2010-\u2015\u2212]")


def main(res):
    errors, n = [], 0
    for d in sorted(os.listdir(res)):
        p = os.path.join(res, d, "strings.xml")
        if not (d == "values" or d.startswith("values-")) or not os.path.isfile(p):
            continue
        s = {e.get("name"): "".join(e.itertext()) for e in ET.parse(p).getroot().iter("string")}
        title, body = s.get("blk_vendor"), s.get("blk_vendor_body")
        if title is None or body is None:
            errors.append("%s: blk_vendor or blk_vendor_body missing" % d)
            continue
        n += 1
        if PH.findall(title):
            errors.append("%s: blk_vendor has a placeholder (the title names no version)" % d)
        if sorted(PH.findall(body)) != ["1$", "2$"]:
            errors.append("%s: blk_vendor_body placeholders %s, want %%1$s and %%2$s" % (d, PH.findall(body)))
        for k, t in (("blk_vendor", title), ("blk_vendor_body", body)):
            if PUNCT.search(t):
                errors.append("%s: %s has a dash or a semicolon: %s" % (d, k, t))
            if BANNED.search(t):
                errors.append("%s: %s names a technical word: %s" % (d, k, t))
        if "Lumen OS" not in body:
            errors.append("%s: blk_vendor_body does not say which update to wait for (Lumen OS)" % d)
    if n < 18:
        errors.append("only %d languages carry the firmware texts, want the 18 the updater ships" % n)
    for e in errors:
        print("FAIL " + e)
    print("firmware text: %d languages, %d failed" % (n, len(errors)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
