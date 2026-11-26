#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""channel_guard.py: a channel manifest is never replaced by one with a lower version_code.

The updater offers the highest verified version_code of the channels it reads and keeps a newer offer when a
channel answers with an older manifest (apps/core/lumen-updater Newest). A channel file that goes back to a lower
version_code would leave every unit on its old offer and is no way to withdraw a build: a withdrawn build is
replaced by a newer one (docs: never re-publish a withdrawn build under the same version). So a publish that would
lower a channel stops here, before any upload.

  channel_guard.py NEW.json [NEW.json ...] --live-dir DIR    the live copies saved in DIR under the same file names
  channel_guard.py NEW.json [NEW.json ...] --live-base URL   the live copies at URL/<file name> (fetched fresh)

A file that is not live yet (missing in DIR, 404 at URL) is a first publish. Equal version_code passes (the same
build signed again). Fails (exit 1) on a lower version_code, a new manifest without a version_code and a live copy
that exists but cannot be read (fail closed). Files not ending in .json (.sig) are skipped. Stdlib only, so a
release kit outside the tree can run it as is.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request


def version_code(raw, what):
    try:
        vc = json.loads(raw.decode("utf-8"))["version_code"]
    except (ValueError, KeyError, TypeError, UnicodeDecodeError) as e:
        raise SystemExit("channel_guard: %s: no readable version_code (%s)" % (what, e))
    if not isinstance(vc, int) or isinstance(vc, bool):
        raise SystemExit("channel_guard: %s: version_code is not a number: %r" % (what, vc))
    return vc


def live_dir(d, name):
    p = os.path.join(d, name)
    if not os.path.isfile(p):
        return None
    with open(p, "rb") as f:
        return f.read()


def live_url(base, name, tries=4):
    url = "%s/%s?guard=%d" % (base.rstrip("/"), name, time.time_ns())
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"Cache-Control": "no-cache", "Pragma": "no-cache"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last = e
        except OSError as e:
            last = e
        time.sleep(3 * (i + 1))
    raise SystemExit("channel_guard: live %s unreadable (%s): stopped, nothing may be published blind" % (url, last))


def check(new_files, live):
    """live(name) -> bytes or None. Returns the report lines; raises SystemExit on a lower version_code."""
    out, bad = [], []
    for path in new_files:
        name = os.path.basename(path)
        if not name.endswith(".json"):
            continue
        with open(path, "rb") as f:
            new = version_code(f.read(), path)
        raw = live(name)
        if raw is None:
            out.append("ok %s: first publish, version_code %d" % (name, new))
            continue
        old = version_code(raw, "live " + name)
        if new < old:
            bad.append("%s: version_code %d is below the live %d" % (name, new, old))
        else:
            out.append("ok %s: version_code %d >= live %d" % (name, new, old))
    if bad:
        raise SystemExit("channel_guard: REFUSED, a channel would go back to an older build (publish a newer "
                         "version instead):\n  " + "\n  ".join(bad))
    return out


def main(argv):
    files, d, base = [], None, None
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--live-dir" and i + 1 < len(argv):
            d = argv[i + 1]
            i += 2
        elif a == "--live-base" and i + 1 < len(argv):
            base = argv[i + 1]
            i += 2
        elif a.startswith("-"):
            raise SystemExit(__doc__)
        else:
            files.append(a)
            i += 1
    if not files or (d is None) == (base is None):
        raise SystemExit(__doc__)
    live = (lambda n: live_dir(d, n)) if d is not None else (lambda n: live_url(base, n))
    for line in check(files, live):
        print(line)
    print("CHANNEL_GUARD_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
