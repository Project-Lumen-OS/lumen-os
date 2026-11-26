#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""A fake report server for the emulator key walk (test/ui/report_walk.py), never a real one: https on PORT with a
throwaway certificate for 10.0.2.2 (the host as the emulator sees it). Every POST is read to the end and answered
after DELAY seconds by the mode in MODEFILE: "ok" -> 200 {"code":"LR-4AD5M6"}, "reject" -> 400 (the report is
refused: the screen shows why and Try again).
    python3 fake_server.py PORT CERT KEY MODEFILE [DELAY]"""
import http.server
import json
import ssl
import sys
import time

PORT, CERT, KEY, MODE = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
DELAY = float(sys.argv[5]) if len(sys.argv) > 5 else 3.0


class H(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0)
        left = n
        while left > 0:
            b = self.rfile.read(min(65536, left))
            if not b:
                break
            left -= len(b)
        time.sleep(DELAY)
        mode = open(MODE).read().strip()
        print("POST %s %d bytes kind=%s mode=%s" % (self.path, n, self.headers.get("X-Lumen-Kind"), mode), flush=True)
        if mode == "reject":
            body, st = b'{"error":"refused"}', 400
        else:
            body, st = json.dumps({"code": "LR-4AD5M6", "bytes": n}).encode(), 200
        self.send_response(st)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        """The marker walk_guard.py probes before every send: only this server answers it."""
        ok = self.path == "/lumen/v1/fake-report-server"
        body = b"lumen-report-fake-server" if ok else b"{}"
        self.send_response(200 if ok else 404)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(CERT, KEY)
srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), H)
srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
print("fake report server on %d" % PORT, flush=True)
srv.serve_forever()
