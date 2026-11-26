#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""res_strings.py: a strings file of an app or a core module as the APK resolves it (tests read texts through it).

A text that two places show is defined once in apps/core/lumen-common-strings and the other places keep their key as
an alias in res/values only (tools/i18n/share.py). A test that reads a strings file to check its texts must see what
the app shows: text(PATH) is the file with every alias of its res/values twin resolved, in the file's own locale, to
the shared text (res/values: the English replaces the alias; res/values-<locale>: the shared translation is added).
A locale file that only had aliases is gone from the tree; text() still returns its resolved texts.

  python3 tools/i18n/res_strings.py FILE    print the resolved file (shell tests)
Python: sys.path.insert(0, "<gsi>/tools/i18n"); import res_strings; res_strings.text(path), res_strings.strings(path)
"""
import glob
import os
import re
import sys

STRING_RE = re.compile(r'(?ms)<string\s+name="([^"]+)"([^>]*)>(.*?)</string>')
MODULE = "apps/core/lumen-common-strings"
_cache = {}


def root_of(path):
    d = os.path.dirname(os.path.abspath(path))
    while d != os.path.dirname(d):
        if os.path.isdir(os.path.join(d, MODULE)):
            return d
        d = os.path.dirname(d)
    return None


def _defs(root, folder):
    """{key: raw} of the shared module and every core module in res/<folder> (the alias targets)."""
    k = (root, folder)
    if k not in _cache:
        out = {}
        for f in sorted(glob.glob(os.path.join(root, "apps/core/*/res", folder, "*.xml"))):
            for name, _, raw in STRING_RE.findall(open(f, encoding="utf-8").read()):
                out.setdefault(name, raw)
        _cache[k] = out
    return _cache[k]


def _local(res, folder):
    """{key: raw} of every strings file in RES/<folder> (an assembled app tree holds the module's texts there)."""
    k = ("local", res, folder)
    if k not in _cache:
        out = {}
        for f in sorted(glob.glob(os.path.join(res, folder, "*.xml"))):
            for name, _, raw in STRING_RE.findall(open(f, encoding="utf-8").read()):
                out.setdefault(name, raw)
        _cache[k] = out
    return _cache[k]


def resolve(root, folder, key, depth=0, res=None):
    """The text of KEY in res/<folder>: the same res folder first (an assembled tree), else the module that defines
    it, aliases followed; None when it has none."""
    raw = _local(res, folder).get(key) if res else None
    if raw is not None and not raw.strip().startswith("@string/"):
        return raw
    if root is None:
        return None
    raw = _defs(root, folder).get(key)
    if raw is not None and raw.strip().startswith("@string/") and depth < 8:
        return resolve(root, folder, raw.strip()[len("@string/"):], depth + 1)
    if raw is None and folder != "values" and _defs(root, "values").get(key, "").strip().startswith("@string/"):
        return resolve(root, folder, _defs(root, "values")[key].strip()[len("@string/"):], depth + 1)
    return raw


def text(path):
    folder, name = os.path.basename(os.path.dirname(path)), os.path.basename(path)
    own = open(path, encoding="utf-8").read() if os.path.isfile(path) else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'
    twin = os.path.join(os.path.dirname(os.path.dirname(path)), "values", name)
    root = root_of(path)
    res = os.path.dirname(os.path.dirname(path))
    if not os.path.isfile(twin):
        return own
    aliases = [(k, raw.strip()[len("@string/"):]) for k, _, raw in STRING_RE.findall(open(twin, encoding="utf-8").read())
               if raw.strip().startswith("@string/")]
    if folder == "values":
        for k, target in aliases:
            t = resolve(root, folder, target, res=res) or ""
            if t:
                own = re.sub(r'(<string\s+name="%s"[^>]*>)@string/%s(</string>)' % (re.escape(k), re.escape(target)),
                             lambda m: m.group(1) + t + m.group(2), own)
        return own
    have = {k for k, _, _ in STRING_RE.findall(own)}
    add = "".join('    <string name="%s">%s</string>\n' % (k, resolve(root, folder, target, res=res)) for k, target in aliases
                  if k not in have and resolve(root, folder, target, res=res) is not None)
    i = own.rfind("</resources>")
    return own[:i] + add + own[i:] if i >= 0 else own + add


def strings(path):
    """{key: raw text} of the resolved file."""
    return {k: raw for k, _, raw in STRING_RE.findall(text(path))}


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.stdout.write(text(sys.argv[1]))
