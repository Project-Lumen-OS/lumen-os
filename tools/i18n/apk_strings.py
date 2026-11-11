#!/usr/bin/env python3
"""apk_strings.py: the resolved string table of an APK, per locale (aapt2 dump resources), and a diff of two APKs.

  apk_strings.py dump APK            "name<TAB>locale<TAB>text" for every string, references (aliases) resolved
  apk_strings.py diff OLD.apk NEW.apk  the (name, locale) pairs whose resolved text differs or exists on one side only

The gate of the lumen-common-strings step: moving texts into the shared vocabulary
must not change what an app shows, so the resolved tables of the APK before and after are compared; a locale that the
app did not have before and has now (a ru-only app gaining 16 locales) is listed as "new", a changed text as
"changed". Exit 0 when the diff is empty (dump: always 0), 1 otherwise, 2 usage.
"""
import os
import re
import subprocess
import sys

AAPT2 = os.path.join(os.environ.get("BUILD_TOOLS", os.path.expanduser("~/Library/Android/sdk/build-tools/36.0.0")), "aapt2")
RES = re.compile(r"^\s+resource (0x[0-9a-f]+) string/(\S+)")
VAL = re.compile(r'^\s+\(([^)]*)\) (?:"(.*)"|@(?:string/)?([0-9A-Za-z_]+))\s*$')


def table(apk):
    out = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True, check=True).stdout
    by_id, cur = {}, None
    for line in out.split("\n"):
        m = RES.match(line)
        if m:
            cur = (m.group(1), m.group(2))
            by_id[cur[0]] = (cur[1], {})
            continue
        if line.strip().startswith("resource "):
            cur = None
            continue
        if cur:
            v = VAL.match(line)
            if v:
                loc = v.group(1) or "default"
                by_id[cur[0]][1][loc] = ("ref", v.group(3)) if v.group(3) else ("text", v.group(2))
    by_name = {name: (name, vals) for name, vals in by_id.values()}
    by_id.update(by_name)                                  # aapt2 prints a reference by name (@string/x) or by id
    resolved = {}
    for rid, (name, vals) in list(by_id.items()):
        if rid in by_name:
            continue
        for loc in vals:
            kind, x = vals[loc]
            seen = 0
            while kind == "ref" and seen < 8:
                tgt = by_id.get(x)
                if not tgt:
                    break
                v = tgt[1].get(loc) or tgt[1].get("default")
                if not v:
                    break
                kind, x = v
                seen += 1
            resolved[(name, loc)] = x if kind == "text" else "@" + x
    # locales the alias target adds: resolve the default alias for every locale of its target
    for rid, (name, vals) in list(by_id.items()):
        d = vals.get("default")
        if d and d[0] == "ref" and d[1] in by_id:
            for loc, v in by_id[d[1]][1].items():
                if (name, loc) not in resolved and v[0] == "text":
                    resolved[(name, loc)] = v[1]
    return resolved


def main(argv):
    if len(argv) == 2 and argv[0] == "dump":
        for (n, loc), t in sorted(table(argv[1]).items()):
            print("%s\t%s\t%s" % (n, loc, t))
        return 0
    if len(argv) == 3 and argv[0] == "diff":
        a, b = table(argv[1]), table(argv[2])
        bad = 0
        for k in sorted(set(a) | set(b)):
            if k[0].startswith("lumen_"):
                continue                                   # the vocabulary's own entries: compared through the aliases
            if a.get(k) != b.get(k):
                what = "new" if k not in a else ("gone" if k not in b else "changed")
                print("%s %s [%s]: %r -> %r" % (what, k[0], k[1], a.get(k), b.get(k)))
                bad = 1
        print("apk_strings: %s" % ("differ" if bad else "same resolved strings"))
        return bad
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
