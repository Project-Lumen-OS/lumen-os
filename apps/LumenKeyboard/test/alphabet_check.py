#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Every letter of every keyboard language can be typed, lower and upper case (test/run.sh).

Reads the shipped pages (res/xml), not the generator's table. A letter is typed by a key of its language's page, or
by a long press the README names ("Russian, Belarusian: е gives ё") on a key that shows it small in its corner
(android:popupCharacters). Upper case is what the view makes of a label with shift on (toUpperCase), so every
letter must map to its own capital.
"""
import os
import re
import sys

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
XML = os.path.join(APP, "res", "xml")

# tag: (lower case, upper case). The apostrophe is part of the Ukrainian and Belarusian spelling (п'ять, з'ява).
ALPHABETS = {
    "en": ("abcdefghijklmnopqrstuvwxyz", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    "ru": ("абвгдеёжзийклмнопрстуфхцчшщъыьэюя", "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"),
    "uk": ("абвгґдеєжзиіїйклмнопрстуфхцчшщьюя'", "АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ'"),
    "be": ("абвгдеёжзійклмнопрстуўфхцчшыьэюя'", "АБВГДЕЁЖЗІЙКЛМНОПРСТУЎФХЦЧШЫЬЭЮЯ'"),
    "kk": ("аәбвгғдеёжзийкқлмнңоөпрстуұүфхһцчшщъыіьэюя", "АӘБВГҒДЕЁЖЗИЙКҚЛМНҢОӨПРСТУҰҮФХҺЦЧШЩЪЫІЬЭЮЯ"),
    "ar": ("ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي", "ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي"),
}
# letters per alphabet: the 28 Arabic letters plus hamza, its seats, taa marbuta and alef maqsura
SIZES = {"en": 26, "ru": 33, "uk": 34, "be": 33, "kk": 42, "ar": 36}
problems = []


def bad(msg):
    problems.append(msg)


def read(*p):
    with open(os.path.join(APP, *p), encoding="utf-8") as f:
        return f.read()


def unescape(s):
    s = s.replace("&quot;", '"').replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    return re.sub(r"\\(.)", r"\1", s)


def keys(name):
    """[(row, code, label, popup file or None, hint)] of a page or popup in res/xml."""
    out = []
    for r, row in enumerate(re.findall(r"<Row[^>]*>(.*?)</Row>", read("res", "xml", name), re.S)):
        for k in re.findall(r"<Key\b(.*?)/>", row, re.S):
            a = dict(re.findall(r'android:(\w+)="([^"]*)"', k))
            pop = a.get("popupKeyboard", "")
            out.append((r, int(a.get("codes", "0").split(",")[0]), unescape(a.get("keyLabel", "")),
                        pop[len("@xml/"):] + ".xml" if pop.startswith("@xml/") else None,
                        unescape(a.get("popupCharacters", ""))))
    return out


def documented():
    """{(language name, base key): letters} of the README's long press list."""
    out = {}
    for names, base, chars in re.findall(r"^- (.+?): (\S) gives (.+)$", read("README.md"), re.M):
        for n in names.split(", "):
            out[(n.strip(), base)] = set(chars.split())
    return out


def languages():
    """[(tag, English name, page files)] in KeyboardLanguage order."""
    enum = read("src", "org", "lumen", "keyboard", "KeyboardLanguage.java")
    rows = re.findall(r'^\s+([A-Z]+)\("([a-z]+)", "[A-Za-z_]+", 0x[0-9a-f]+, (?:true|false)\)[,;]', enum, re.M)
    cnt = read("src", "com", "android", "inputmethod", "leanback", "LeanbackKeyboardContainer.java")
    m = re.search(r"LANGUAGE_PAGES = \{\s*([^}]*)\}", cnt)
    pages = [p.strip() for p in m.group(1).split(",") if p.strip()] if m else []
    if len(pages) != len(rows):
        bad("LANGUAGE_PAGES has %d pages, KeyboardLanguage %d rows" % (len(pages), len(rows)))
    latin = sorted(f for f in os.listdir(XML) if re.match(r"(qwerty|qwertz|azerty)[a-z_]*\.xml$", f))
    out = []
    for (name, tag), page in zip(rows, pages):
        files = latin if page == "0" else [page[len("R.xml."):] + ".xml"]
        out.append(("en" if page == "0" else tag, name.capitalize(), files))
    return out


def check_page(tag, name, page, docs):
    lower, upper = ALPHABETS[tag]
    direct, by_long = set(), {}

    def code_ok(where, code, label):
        # the view types the label, the code tells the service it is a letter (auto space after a period)
        if label.lower() in lower and code not in (ord(label), ord(label.lower())):
            bad("%s: key %r has code %d, not its letter" % (where, label, code))

    for _, code, label, pop, hint in keys(page):
        if len(label) != 1:
            continue
        code_ok(page, code, label)
        direct.add(label.lower())
        if pop:
            for _, pcode, plabel, _, _ in keys(pop)[1:]:
                if len(plabel) == 1:
                    code_ok(pop, pcode, plabel)
                    by_long.setdefault(plabel.lower(), []).append((label, hint))
    missing, hidden = [], []
    for ch, cap in zip(lower, upper):
        if ch.upper() != cap or cap.lower() != ch:
            bad("%s (%s): %r does not type the capital %r with shift" % (name, page, ch, cap))
        if ch in direct:
            continue
        ways = by_long.get(ch, [])
        if not ways:
            missing.append(ch)
        elif not any(ch in docs.get((name, base), ()) and ch in hint for base, hint in ways):
            hidden.append("%s on %s" % (ch, "/".join(b for b, _ in ways)))
    if missing:
        bad("%s (%s): missing %s" % (name, page, " ".join(missing)))
    if hidden:
        bad("%s (%s): only on a long press the README does not name or the key does not show: %s"
            % (name, page, ", ".join(hidden)))
    return len(lower) - len(missing) - len(hidden)


def main():
    for tag, (lower, upper) in ALPHABETS.items():
        if len(lower) != SIZES[tag] or len(upper) != SIZES[tag] or len(set(lower)) != SIZES[tag]:
            bad("test table: %s has %d letters, want %d" % (tag, len(set(lower)), SIZES[tag]))
    docs = documented()
    seen = set()
    for tag, name, files in languages():
        seen.add(tag)
        if tag not in ALPHABETS:
            bad("%s: no alphabet in this test" % name)
            continue
        for page in files:
            n = check_page(tag, name, page, docs)
            print("alphabet_check: %s %s %d/%d" % (name, page, n, SIZES[tag]))
    for tag in ALPHABETS:
        if tag not in seen:
            bad("alphabet %s has no keyboard language" % tag)
    for p in problems:
        print("FAIL", p)
    print("alphabet_check: %d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
