#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Host test of tools/release/channel_guard.py: a channel never goes back to a lower version_code.
python3 tools/release/test/channel_guard_test.py (loopback only, a temp dir)."""
import http.server
import json
import os
import subprocess
import sys
import tempfile
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(HERE, "..", "channel_guard.py")
passed = failed = 0


def eq(what, want, got):
    global passed, failed
    if want == got:
        passed += 1
    else:
        failed += 1
        print("FAIL %s: want %r, got %r" % (what, want, got))


def manifest(d, name, vc):
    p = os.path.join(d, name)
    with open(p, "w") as f:
        json.dump({"schema": 1, "version": "x", "version_code": vc}, f)
    return p


def guard(*args):
    r = subprocess.run([sys.executable, "-B", GUARD] + list(args), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    t = tempfile.mkdtemp(prefix="channel_guard.")
    new, live = os.path.join(t, "new"), os.path.join(t, "live")
    os.makedirs(new)
    os.makedirs(live)
    b = manifest(new, "update-public-beta.json", 1010003)
    s = manifest(new, "update-public-beta.json.sig", 0)
    manifest(live, "update-public-beta.json", 1010002)
    rc, out = guard(b, s, "--live-dir", live)
    eq("newer passes", 0, rc)
    eq("newer says ok", True, "CHANNEL_GUARD_OK" in out and "1010003 >= live 1010002" in out)
    manifest(live, "update-public-beta.json", 1010003)
    eq("equal passes (signed again)", 0, guard(b, "--live-dir", live)[0])
    manifest(live, "update-public-beta.json", 1010004)
    rc, out = guard(b, "--live-dir", live)
    eq("lower refused", 1, rc)
    eq("lower names both codes", True, "REFUSED" in out and "1010003 is below the live 1010004" in out)
    t2 = manifest(new, "update-x50-test.json", 1001013)
    rc, out = guard(t2, b, "--live-dir", live)
    eq("one lower file of two refuses the publish", 1, rc)
    eq("first publish passes", 0, guard(t2, "--live-dir", os.path.join(t, "none"))[0])
    with open(os.path.join(live, "update-x50-test.json"), "w") as f:
        f.write("<html>rate limited</html>")
    rc, out = guard(t2, "--live-dir", live)
    eq("unreadable live copy fails closed", 1, rc)
    bad = os.path.join(new, "update-bad.json")
    with open(bad, "w") as f:
        json.dump({"version_code": "1010003"}, f)
    eq("new version_code not a number", 1, guard(bad, "--live-dir", live)[0])
    eq("no source given", 1, guard(b)[0])
    eq("two sources given", 1, guard(b, "--live-dir", live, "--live-base", "http://127.0.0.1:9")[0])

    # --live-base: the live copies over HTTP, fetched with a fresh query, 404 = first publish
    seen = []

    class H(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            seen.append((self.path, self.headers.get("Cache-Control")))
            name = self.path.split("?")[0].lstrip("/")
            p = os.path.join(live, name)
            if not os.path.isfile(p):
                self.send_response(404)
                self.end_headers()
                return
            data = open(p, "rb").read()
            self.send_response(200)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, format, *args):
            pass

    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = "http://127.0.0.1:%d" % srv.server_address[1]
    manifest(live, "update-public-beta.json", 1010004)
    eq("live-base lower refused", 1, guard(b, "--live-base", base)[0])
    manifest(live, "update-public-beta.json", 1010002)
    eq("live-base newer passes", 0, guard(b, "--live-base", base)[0])
    eq("live-base 404 is a first publish", 0, guard(manifest(new, "update-new-beta.json", 5), "--live-base", base)[0])
    eq("live-base asks fresh", True, all("?guard=" in p and c == "no-cache" for p, c in seen))
    srv.shutdown()
    print("channel_guard passed=%d failed=%d" % (passed, failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
