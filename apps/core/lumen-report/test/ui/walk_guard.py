# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The fail-closed guard of the report screen key walk (report_walk.py): test reports go only to the fake server of
fake_server.py, never to a real report server. Before every send the walk calls check() and stops on any reason:
  - the address must be https on a local host (the host as the emulator sees it), never a host of ReportUrl.java;
  - the emulator must read the address back from persist.lumen.report.url;
  - want="fake": the fake server itself answers its marker on that port of the host (a stale or foreign server on the
    port does not count); want="none": nothing listens there (the offline case must not reach any server).
Host test: test/ui/guard_test.py."""
import os
import re
import socket
import ssl
import urllib.parse

LOCAL = {"10.0.2.2": "127.0.0.1", "127.0.0.1": "127.0.0.1", "localhost": "127.0.0.1"}
MARK_PATH = "/lumen/v1/fake-report-server"
MARK = b"lumen-report-fake-server"
URL_SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "org", "lumen", "report",
                       "ReportUrl.java")


def real_hosts(src=URL_SRC):
    """Every host of an https address in ReportUrl.java (the real servers)."""
    text = open(src, encoding="utf-8").read()
    return {urllib.parse.urlsplit(u).hostname for u in re.findall(r'"(https://[^"]+)"', text)}


def answers(host, port, timeout=3.0):
    """'fake' when the fake server answers its marker, 'other' when something else listens, 'none' when nothing does."""
    try:
        raw = socket.create_connection((host, port), timeout=timeout)
    except OSError:
        return "none"
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE                    # a local probe only: the marker decides, not the certificate
    try:
        with ctx.wrap_socket(raw) as s:
            s.settimeout(timeout)
            s.sendall(("GET %s HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n" % (MARK_PATH, host)).encode())
            got = b""
            while len(got) < 65536:
                b = s.recv(4096)
                if not b:
                    break
                got += b
    except (OSError, ssl.SSLError):
        return "other"
    head, _, body = got.partition(b"\r\n\r\n")
    return "fake" if head.startswith(b"HTTP/1.1 200") and body.strip() == MARK else "other"


def check(url, got, want, real=None):
    """None when the walk may send to URL, else the reason to stop."""
    u = urllib.parse.urlsplit(url)
    real = real_hosts() if real is None else real
    if u.scheme != "https":
        return "%r is not https" % url
    if u.hostname in real:
        return "%r is a real report server" % url
    if u.hostname not in LOCAL or not u.port:
        return "%r is not the fake server on this host" % url
    if got != url:
        return "persist.lumen.report.url reads %r, not %r (needs a rooted emulator image)" % (got, url)
    seen = answers(LOCAL[u.hostname], u.port)
    if seen != want:
        return "port %d of the host: want %s, found %s" % (u.port, want, seen)
    return None
