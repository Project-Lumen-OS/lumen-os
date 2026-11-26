# lumen-download: the one download of large files

`org.lumen.download` fetches one file over several HTTP range connections at once. Plain Java, no Android and no app
code, so every app that downloads a big file from GitHub releases uses this one copy:

| app | what it downloads | its wrapper |
|---|---|---|
| the updater (apps/core/lumen-updater) | the OTA zip of a manifest | `PackageDownload`: waits for the network, retries, sha256 of the manifest |
| Lumen Installer (apps/LumenInstaller) | the trial image, an app update of the signed list | `core/Engine.fetch`: retries, then its own sha256 step against SHA256SUMS and the list |

Each app names the module in its recipe (`module lumen-download`) and passes its own `ParallelDownload.Opener`
(its HTTPS-only `Http` with its User-Agent). Checking the finished file stays with the app.

## How it works

Providers in Russia shape GitHub per connection (a Z9X on 5 GHz Wi-Fi, 2026-11-19: the one-connection download ran at
1.1 MB/s while a second curl beside it got 2.5 MB/s). `ParallelDownload` starts with 4 connections and adds one per
4 s window while the total speed grows by 10 %, up to 8 (`ConnectionScaler`). A window right after a change, a window
in which a connection failed and a window without data decide nothing. A connection that runs out of work takes the
back half of the largest range left (`Segments`), so all of them stay busy to the last byte.

The last ranges are too small to halve (under 4 MB). A connection without work used to end there, so one slow
connection kept the end of the file alone: an HB beta23 -> beta24 OTA stood at "518 MB of 520 MB, 22 kB/s" for 2-3
minutes. Bench on builder-llm (real GitHub, beta24 HB OTA, 1.1 MB/s per connection, the third CDN tunnel at 22 KB/s):
the old code had 3.8 MB left on that one connection after 79 s and needed 246 s in all, now the whole file takes 77 s,
same sha256. A connection without work now stays and takes over the whole unread rest of the range that would end
last, once that range's own connection has had it for 2 s, is less than half as fast and would need more than 2 s
more. The slow one ends at its last written byte, so nothing is fetched twice.

The data goes to `<file>.part`, the missing ranges to `<file>.part.state` (the caller's key, the sha256 of the file it
expects, the size, the ETag, one `pos end` line per range), written after an fsync of the data every 2 s and at every
stop, so a network loss, a pause or a restart continues each range where it stopped. A state file of another key or
size is ignored. The file appears under its own name only when every byte is in. A file that an older one-connection
download left under the final name continues as one missing range. `ParallelDownload.have` counts the bytes on disk,
`discard` removes the file with its partial download.

A server that ignores Range gets one plain connection from the first byte (no resume). A cut connection tries again
after a growing pause while the others go on; a round fails only when no data came for 60 s, or on 404 / 410
(`HttpError`, the file is gone). The speed the screens show comes from the last 10 s (`Rate`).

## GitHub releases

- The stable URL `github.com/<owner>/<repo>/releases/download/<tag>/<name>` redirects to a signed CDN link
  (release-assets.githubusercontent.com, Fastly in front) whose `jwt` lives about 1 h. The link is resolved at the start
  of every round and again by one connection for all when the CDN refuses it (any 4xx on a range): a long download
  outlives its first link.
- The CDN serves a cached file whatever `sig`, `jwt` or `se` say (spoiled values still answer 206 with `x-cache: HIT`),
  only another asset path gets 403. Test the renew path against real GitHub by spoiling the path
  (`test/bench/Bench.java`, mode `parallel+403`).
- The probe asks `Range: bytes=0-0`. A client that drops Range on the redirect gets 200: the probe then asks the CDN
  link itself before it gives up on ranges.
- Every range request carries `If-Range` with the ETag when the ETag is strong (a weak `W/` validator never matches,
  RFC 9110), so a file that changed on the server answers 200 and the download starts over instead of mixing two files.

## Tests

- `test/run.sh`: the module compiles with the JDK alone, then `DownloadTest` against a local GitHub-like server
  (redirect to a link that expires, Range with ETag and If-Range, a throttle per connection, one crawling connection
  at the end, cut connections, a server without Range, a file that changes on the server, a legacy prefix, 404). Its `Server` and helpers are public
  for the apps' own download tests (the updater's `PackageDownloadTest`).
- `test/mutate.py`: mutants of the ranges, the takeover of a slow end, the renew, If-Range, resume, the fallback and
  the scaler.
- `test/bench/run.sh URL SIZE SHA256` (on a builder, never on the Mac): one connection, then the parallel download of a
  real release asset through `throttle_proxy.py` (RATE bytes/s per connection, default 1.1 MB/s). On builder-llm at
  1.1 MB/s per connection: one connection 1.15 MB/s, the parallel download 8.1 MB/s on average over a whole beta OTA,
  sha256 match. Not measured on a projector yet.
