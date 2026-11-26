#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""patch_file.py BASE DIFF: BASE with the unified diff DIFF applied exactly (no fuzz, no offset), on stdout; exit 1 when
a hunk does not apply. The one reader of a recipe's patch line (tools/devices/assemble_app.py rule 10), also for tests
that read a patched file, e.g. the HB manifest: the core manifest plus devices/goat_elegant/app/HbProjector.manifest.diff.
"""
import re
import sys

HUNK = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")


class PatchError(Exception):
    pass


def target(diff_text):
    """The path the diff names (its '+++ b/PATH' line), None without one."""
    return next((x[6:].split("\t")[0] for x in diff_text.split("\n") if x.startswith("+++ b/")), None)


def apply(base_text, diff_text, name="the base"):
    lines, old = diff_text.split("\n"), base_text.split("\n")
    out, at, i = [], 0, 0
    while i < len(lines):
        m = HUNK.match(lines[i])
        i += 1
        if not m:
            continue
        start = int(m.group(1)) - (0 if m.group(2) == "0" else 1)
        if start < at:
            raise PatchError("hunks out of order at line %d" % (start + 1))
        out.extend(old[at:start])
        at = start
        while i < len(lines) and lines[i][:1] in (" ", "-", "+", "\\"):
            x = lines[i]
            i += 1
            if x[:1] == "+":
                out.append(x[1:])
            elif x[:1] in (" ", "-"):
                if at >= len(old) or old[at] != x[1:]:
                    raise PatchError("does not apply to %s at line %d (the base changed: review it, then make the "
                                     "diff again)" % (name, at + 1))
                if x[:1] == " ":
                    out.append(old[at])
                at += 1
    out.extend(old[at:])
    return "\n".join(out)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    try:
        sys.stdout.write(apply(open(argv[0], encoding="utf-8").read(), open(argv[1], encoding="utf-8").read(), argv[0]))
    except (PatchError, OSError) as e:
        print("patch_file: %s: %s" % (argv[1], e), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
