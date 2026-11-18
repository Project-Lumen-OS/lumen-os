#!/usr/bin/env python3
"""check_glyphs.py: no text glyph stands in for an icon in a Lumen app (the icons are apps/core/lumen-icons).

  check_glyphs.py [--root GSI] [--quiet]      exit 0 green, 1 red (one line per finding: file:line, glyph, rule)

What is read: every app and module under apps/ (not apps/sdk, apps/third_party, test/ folders, the icon module itself):
  strings  res/values*/*.xml <string>, <item> texts and apps/i18n/langs/*.txt values (the sources of the generated
           translations)
  layouts  res/layout*/*.xml android:text, android:hint, android:contentDescription
  code     the string and char literals of *.java and *.kt (comments are not read); a line that logs (Log.x()) or
           holds the marker comment "lumen-icons: not an icon (why)" is skipped, in a strings file the line that ends
           the string
Rules:
  icon     a mark, a status, a media or a device glyph (ICON) anywhere in a text: draw lumen_ic_* instead
  key      a remote key arrow (KEYS) in code: IconText.LEFT/RIGHT/UP/DOWN with IconText.keys, or IconText.key; in a
           string only when tools/icons/key_hints.tsv names the string and the file that draws it through IconText
           (that file must hold the given anchor text)
  decor    a bullet or a chevron (DECOR, in strings also a lone x sign or middle dot) that is the whole text or
           starts or ends it: a list bullet is IconText.bullet, a chevron is Icons.chevron
A spoken key name (SPOKEN: the AOSP keyboard's keyboardview_keycode_* strings, read aloud by the screen reader and
never drawn) may name its key with the arrow its translation uses.
Dimensions (3840 x 2160), minus signs, quotes, ellipses and separators inside a sentence are typography, not icons.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
GSI = os.path.dirname(os.path.dirname(HERE))

KEYS = "\u25C0\u25B6\u25B2\u25BC\u2190\u2192\u2191\u2193"
ICON = ("\u2713\u2714\u2717\u2718\u2715\u2716\u2610\u2611\u2612\u25CB\u25CF\u25C9\u25EF\u25CC\u2605\u2606\u2699"
        "\u23FB\u23FC\u23FD\u2B58\u23F8\u23EF\u23F9\u23FA\u23CF\u275A\u258C\u266A\u266B\u24D8\u2139\u26A0\u21BB"
        "\u21BA\u27F3\u27F2\u232B\u21E7\u2328\u270E\u270F\u25BA\u25C4\u25B8\u25C2\u25B4\u25BE\u25B3\u25BD\u21E3"
        "\u21E1\u21E6\u21E8\u21E9\u2B05\u27A1\u2B06\u2B07")
DECOR_CODE = "\u2022\u203A\u2039"
DECOR_STRING = DECOR_CODE + "\u00D7\u00B7"
MARKER = "lumen-icons: not an icon"
SPOKEN = re.compile(r"^keyboardview_keycode_")
SKIP_DIRS = {".git", "build", "out", "test", "tests", "sdk", "third_party", "pending", "lumen-icons", "node_modules"}
TEXT_ATTRS = re.compile(r'android:(?:text|hint|contentDescription)="([^"]*)"')
STRING = re.compile(r'<(string|item)\b([^>]*)>(.*?)</\1>', re.S)
NAME = re.compile(r'\bname="([^"]+)"')
ESC = re.compile(r'\\u([0-9a-fA-F]{4})')


def emoji(ch):
    cp = ord(ch)
    return 0x1F300 <= cp <= 0x1FAFF or 0x2600 <= cp <= 0x27BF


def unescape(s):
    return ESC.sub(lambda m: chr(int(m.group(1), 16)), s)


def decor(text, glyphs):
    t = text.strip()
    if not t:
        return None
    if all(c in glyphs or c.isspace() for c in t):
        return t[0]
    if t[0] in glyphs and len(t) > 1 and t[1].isspace():
        return t[0]
    if t[-1] in glyphs and len(t) > 1 and t[-2].isspace():
        return t[-1]
    return None


def text_findings(text, where, keys_ok, decor_set):
    out = []
    for ch in sorted(set(text)):
        if ch in ICON or emoji(ch):
            out.append((where, ch, "icon"))
        elif ch in KEYS and not keys_ok:
            out.append((where, ch, "key"))
    d = decor(text, decor_set)
    if d:
        out.append((where, d, "decor"))
    return out


def literals(src):
    """(line, text) of every string and char literal of Java or Kotlin source, comments left out."""
    out, i, n, line = [], 0, len(src), 1
    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            i += 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            line += src.count("\n", i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append((line, src[i + 3:j - 3]))
            line += src.count("\n", i, j)
            i = j
        elif c in "\"'":
            j, buf = i + 1, []
            while j < n and src[j] != c and src[j] != "\n":
                if src[j] == "\\" and j + 1 < n:
                    buf.append(src[j:j + 2])
                    j += 2
                    continue
                buf.append(src[j])
                j += 1
            out.append((line, unescape("".join(buf))))
            i = j + 1
        else:
            i += 1
    return out


def key_hints(root):
    """{string name: (file, anchor)} of tools/icons/key_hints.tsv."""
    out = {}
    p = os.path.join(root, "tools", "icons", "key_hints.tsv")
    if os.path.isfile(p):
        for line in open(p, encoding="utf-8"):
            if line.startswith("#") or not line.strip():
                continue
            f = line.rstrip("\n").split("\t")
            out[f[0]] = (f[1], f[2])
    return out


def scan(root):
    hints = key_hints(root)
    found, seen_hints = [], set()
    apps = os.path.join(root, "apps")
    for d, dirs, names in os.walk(apps):
        dirs[:] = sorted(x for x in dirs if x not in SKIP_DIRS)
        rel_dir = os.path.relpath(d, root).replace(os.sep, "/")
        for fn in sorted(names):
            p = os.path.join(d, fn)
            rel = rel_dir + "/" + fn
            if fn.endswith((".java", ".kt")):
                src = open(p, encoding="utf-8", errors="replace").read()
                lines = src.split("\n")
                for ln, text in literals(src):
                    code = lines[ln - 1] if ln - 1 < len(lines) else ""
                    if MARKER in code or re.search(r"\bLog\.[vdiwe]\(", code):
                        continue
                    found += text_findings(text, "%s:%d" % (rel, ln), False, DECOR_CODE)
            elif fn.endswith(".xml") and "/res/values" in rel:
                src = open(p, encoding="utf-8", errors="replace").read()
                lines = src.split("\n")
                for m in STRING.finditer(src):
                    nm = NAME.search(m.group(2))
                    name = nm.group(1) if nm else "?"
                    ln = src.count("\n", 0, m.start()) + 1
                    if MARKER in lines[src.count("\n", 0, m.end())]:
                        continue
                    text = unescape(m.group(3))
                    keyed = name in hints and any(k in text for k in KEYS)
                    if keyed:
                        seen_hints.add(name)
                    keyed = keyed or bool(SPOKEN.match(name))
                    found += text_findings(text, "%s:%d (%s)" % (rel, ln, name), keyed, DECOR_STRING)
            elif fn.endswith(".xml") and "/res/layout" in rel:
                src = open(p, encoding="utf-8", errors="replace").read()
                for m in TEXT_ATTRS.finditer(src):
                    if m.group(1).startswith("@"):
                        continue
                    ln = src.count("\n", 0, m.start()) + 1
                    found += text_findings(unescape(m.group(1)), "%s:%d" % (rel, ln), False, DECOR_STRING)
            elif fn.endswith(".txt") and rel.startswith("apps/i18n/langs/"):
                for ln, line in enumerate(open(p, encoding="utf-8"), 1):
                    if "=" not in line or line.startswith(("#", "@")):
                        continue
                    name, text = line.rstrip("\n").split("=", 1)
                    name = name.split("#", 1)[0]
                    keyed = name in hints and any(k in text for k in KEYS)
                    found += text_findings(text, "%s:%d (%s)" % (rel, ln, name), keyed, DECOR_STRING)
    for name, (f, anchor) in sorted(hints.items()):
        fp = os.path.join(root, f)
        if not os.path.isfile(fp) or anchor not in open(fp, encoding="utf-8").read():
            found.append(("tools/icons/key_hints.tsv (%s)" % name, "", "key hint not drawn: %s has no %r" % (f, anchor)))
        elif name not in seen_hints:
            found.append(("tools/icons/key_hints.tsv (%s)" % name, "", "key hint names no string with a key arrow"))
    return found


def main(argv):
    root = GSI
    if "--root" in argv:
        root = os.path.abspath(argv[argv.index("--root") + 1])
    found = scan(root)
    for where, ch, rule in found:
        print("check_glyphs: %s  %s  %s" % (where, ch and "U+%04X %s" % (ord(ch), ch), rule))
    if "--quiet" not in argv or found:
        print("check_glyphs: %s, %d finding(s)" % ("RED" if found else "OK", len(found)))
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
