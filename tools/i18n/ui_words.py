#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""ui_words.py: no app shows people the technical words of the install machinery (maintainer rule, "we are not robots":
DSU, vbmeta and slot become plain words, such as "trial run", "the current system stays as it is", "the projector
must be prepared once").

    python3 tools/i18n/ui_words.py [GSI_DIR]      exit 0 when no user-visible string carries one, 1 otherwise

Scans every <string>, every <plurals> item and every <string-array> item of every res/values*/*.xml under apps/,
overlay/ and devices/ (all texts Android can put on screen, every language, translatable="false" ones too). A text
that is only a reference (@string/...) is skipped: the referenced string is checked where it lives. The words, in the
languages the apps ship: DSU, vbmeta, slot (and its translations: Systemslot, ranura, emplacement, slot,
yuva, khana / fatha, слот, スロット, 슬롯, 槽位, 插槽). Build folders are skipped.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

FORBIDDEN = (
    ("DSU", re.compile(r"\bDSU\b", re.I)),
    ("vbmeta", re.compile(r"vbmeta", re.I)),
    ("slot", re.compile(r"\bslots?\b|systemslot", re.I)),          # en, de, fr, it, pl, pt (Afrikaans 'slot' = lock: no \b)
    ("slot (es)", re.compile(r"\branuras?\b", re.I)),
    ("slot (fr)", re.compile(r"\bemplacements?\b", re.I)),
    ("slot (tr)", re.compile(r"\bsistem yuva", re.I)),
    ("slot (ar)", re.compile(r"خانة النظام|خانة الأخرى|فتحة النظام")),
    ("slot (ru, uk, be, kk)", re.compile(r"слот", re.I)),
    ("slot (ja)", re.compile(r"スロット")),
    ("slot (ko)", re.compile(r"슬롯")),
    ("slot (zh)", re.compile(r"槽位|插槽")),
)
ROOTS = ("apps", "overlay", "devices")
REF = re.compile(r"@(android:)?string/[A-Za-z0-9_.]+")
SKIP = {"build", ".git", "node_modules", "__pycache__"}


def texts(path):
    """(name, text) of every user-visible entry of one values XML file."""
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        yield ("<parse error>", "%s" % e)
        return
    for el in root:
        if el.tag == "string":
            yield el.get("name"), "".join(el.itertext())
        elif el.tag in ("plurals", "string-array"):
            for i, item in enumerate(el.iter("item")):
                yield "%s[%d]" % (el.get("name"), i), "".join(item.itertext())


def files(gsi):
    for top in ROOTS:
        for d, dirs, names in os.walk(os.path.join(gsi, top)):
            dirs[:] = sorted(x for x in dirs if x not in SKIP)
            if not os.path.basename(d).startswith("values") or os.sep + "res" + os.sep not in d + os.sep:
                continue
            for n in sorted(names):
                if n.endswith(".xml"):
                    yield os.path.join(d, n)


def check(gsi):
    """Every hit as 'path: name: word: text'."""
    hits = []
    for p in files(gsi):
        for name, text in texts(p):
            if name == "<parse error>":
                hits.append("%s: not readable XML (%s)" % (os.path.relpath(p, gsi), text))
                continue
            t = text.strip()
            if REF.fullmatch(t):
                continue
            for word, rx in FORBIDDEN:
                if rx.search(t):
                    hits.append("%s: %s: %s: %s" % (os.path.relpath(p, gsi), name, word, t[:120]))
    return hits


def main(argv):
    gsi = argv[1] if len(argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    n = sum(1 for _ in files(gsi))
    hits = check(gsi)
    for h in hits:
        print("RED " + h)
    if n == 0:
        print("ui_words: no values files found under %s (wrong tree?)" % gsi)
        return 1
    print("ui_words: %d values files, %d forbidden word(s) on screen" % (n, len(hits)))
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
