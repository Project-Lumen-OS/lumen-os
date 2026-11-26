#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Host test of the fail-closed guard of the report screen key walk (walk_guard.py, report_walk.py, build.sh): the walk
sends only to a running fake_server.py on this host and stops on anything else. A real fake server with a throwaway
certificate, a foreign TLS server and a closed port are started on 127.0.0.1; no emulator, no network.
    python3 apps/core/lumen-report/test/ui/guard_test.py"""
import http.server
import os
import re
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walk_guard  # noqa: E402

bad = 0


def ok(cond, what):
    global bad
    print(("ok   " if cond else "FAIL ") + what, flush=True)
    bad += 0 if cond else 1


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_port(port):
    for _ in range(100):
        try:
            socket.create_connection(("127.0.0.1", port), timeout=0.2).close()
            return
        except OSError:
            time.sleep(0.1)
    raise SystemExit("guard_test: nothing listens on %d" % port)


tmp = tempfile.mkdtemp(prefix="lumen_walk_guard.")
cert, key, mode = (os.path.join(tmp, n) for n in ("ca.pem", "key.pem", "mode"))
subprocess.run(["openssl", "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes",
                "-days", "1", "-subj", "/CN=10.0.2.2", "-keyout", key, "-out", cert], check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
open(mode, "w").write("ok")
fake, other, closed = free_port(), free_port(), free_port()
srv = subprocess.Popen([sys.executable, "-B", os.path.join(HERE, "fake_server.py"), str(fake), cert, key, mode, "0"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


class Foreign(http.server.BaseHTTPRequestHandler):
    def do_GET(self):                                   # any other https server, e.g. a stale one on the port
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"{}")

    def log_message(self, format, *args):
        pass


ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(cert, key)
fs = http.server.ThreadingHTTPServer(("127.0.0.1", other), Foreign)
fs.socket = ctx.wrap_socket(fs.socket, server_side=True)
threading.Thread(target=fs.serve_forever, daemon=True).start()
try:
    wait_port(fake)
    real = walk_guard.real_hosts()
    ok({"stats.lumen-os.net", "<domain>"} <= real, "the real report hosts are read from ReportUrl.java: %s"
       % " ".join(sorted(real)))
    good = "https://10.0.2.2:%d/lumen/v1/report" % fake
    down = "https://10.0.2.2:%d/lumen/v1/report" % closed
    ok(walk_guard.check(good, good, "fake") is None, "the running fake server passes")
    ok(walk_guard.check(down, down, "none") is None, "a closed port passes for the offline case")
    for u in sorted("https://%s/lumen/v1/report" % h for h in real):
        ok(walk_guard.check(u, u, "fake") is not None, "a real server stops the walk: " + u)
        ok(walk_guard.check(u, u, "none") is not None, "a real server stops the offline case: " + u)
    refuse = [
        ("http://10.0.2.2:%d/lumen/v1/report" % fake, "fake", "plain http"),
        ("https://203.0.113.7:%d/lumen/v1/report" % fake, "fake", "a host that is not this one"),
        ("https://10.0.2.2/lumen/v1/report", "fake", "no port"),
        ("https://10.0.2.2:%d/lumen/v1/report" % other, "fake", "another server answers on the port"),
        ("https://10.0.2.2:%d/lumen/v1/report" % closed, "fake", "nothing listens where the fake must"),
        (good, "none", "the offline address answers"),
    ]
    for u, want, what in refuse:
        ok(walk_guard.check(u, u, want) is not None, "stops: " + what)
    ok(walk_guard.check(good, "", "fake") is not None, "stops: the property did not take (empty read back)")
    ok(walk_guard.check(good, "https://stats.lumen-os.net/lumen/v1/report", "fake") is not None,
       "stops: the property reads the real server")
    srv.terminate()
    srv.wait()
    ok(walk_guard.check(good, good, "fake") is not None, "stops: the fake server is gone")
    walk = open(os.path.join(HERE, "report_walk.py"), encoding="utf-8").read()
    body = walk[walk.index("def url(u):"):walk.index("\ndef ", walk.index("def url(u):") + 1)]
    ok("walk_guard.check(" in body and "sys.exit(2)" in body, "report_walk url() stops on the guard's reason")
    sends = [m.start() for m in re.finditer(r'key\("KEYCODE_DPAD_CENTER"\)', walk)]
    first_url = walk.find("    url(GOOD)", walk.index("def walk("))
    ok(0 < first_url < min(sends), "the walk sets and checks the address before its first OK")
    ok(re.search(r"url\(GOOD\)[^\n]*\n    open\(MODE, \"w\"\)\.write\(\"reject\"\)", walk) is not None and
       re.search(r"url\(DOWN\)\n    key\(\"KEYCODE_DPAD_CENTER\"\)", walk) is not None,
       "the refused and the offline sends are checked again right before their OK")
    nsc = open(os.path.join(HERE, "build.sh"), encoding="utf-8").read()
    ok(re.search(r"<base-config[^>]*>\s*<trust-anchors\s*/>\s*</base-config>", nsc) is not None,
       "the test APK trusts no certificate outside 10.0.2.2 (a fallback to a real server cannot finish TLS)")
finally:
    if srv.poll() is None:
        srv.terminate()
    fs.shutdown()
    subprocess.run(["rm", "-rf", tmp])
print("walk guard: %d failed" % bad)
sys.exit(1 if bad else 0)
