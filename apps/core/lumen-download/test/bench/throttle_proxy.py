#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""A local HTTPS proxy that limits every connection on its own, like the per-connection shaping of GitHub by
providers in Russia: CONNECT tunnels only, the download direction of each tunnel at most RATE bytes/s, the upload
direction free. Prints a line per tunnel with the open and the peak count.

  python3 throttle_proxy.py PORT RATE
"""
import asyncio
import sys
import time

PORT, RATE = int(sys.argv[1]), int(sys.argv[2])
live = peak = 0


async def pipe(r, w, rate):
    t0, sent = time.monotonic(), 0
    try:
        while True:
            b = await r.read(16384)
            if not b:
                break
            w.write(b)
            await w.drain()
            if rate:
                sent += len(b)
                d = t0 + sent / rate - time.monotonic()
                if d > 0:
                    await asyncio.sleep(d)
    except (ConnectionError, OSError):
        pass
    finally:
        try:
            w.close()
        except OSError:
            pass


async def handle(cr, cw):
    global live, peak
    try:
        host, port = (await cr.readline()).split()[1].decode().rsplit(":", 1)
        while (await cr.readline()) not in (b"\r\n", b"\n", b""):
            pass
        sr, sw = await asyncio.open_connection(host, int(port))
    except (OSError, ValueError, IndexError):
        cw.close()
        return
    cw.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
    await cw.drain()
    live += 1
    peak = max(peak, live)
    print("%s tunnel %s open=%d peak=%d" % (time.strftime("%H:%M:%S"), host, live, peak), flush=True)
    await asyncio.gather(pipe(cr, sw, 0), pipe(sr, cw, RATE))
    live -= 1


async def main():
    srv = await asyncio.start_server(handle, "127.0.0.1", PORT)
    async with srv:
        await srv.serve_forever()


asyncio.run(main())
