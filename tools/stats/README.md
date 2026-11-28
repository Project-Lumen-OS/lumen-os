# Usage statistics

How many projectors run which Lumen OS version. Two sources:

1. **GitHub download counts** (no device involved): `python3 tools/stats/github_downloads.py` (per asset),
   `--summary` (per release: installs, updates, checks), `--json` (rows for the dashboard). Reads the public
   releases through `gh api`; the token stays in gh.
2. **Usage ping** from the updater (`apps/core/lumen-stats`, shared by every model):
   at most once a day, at the normal update check and only after the first-run setup, one HTTPS POST to each of
   `Ping.URLS` in turn until one answers (a fallback server goes after the first): install id (random UUID),
   serial (ro.serialno, else Build.getSerial), codename, XGIMI model, version, version code, build id, channel,
   and on the first send after a version change the previous version and the method (ota_full, ota_delta, usb,
   installer; "first" for a new install id). One switch, owned by the updater (Store.statsOn, default
   `Stats.DEFAULT_ON`), shown in the updater, the setup wizard's Done step and the projector settings
   (Diagnostics); the other two use the updater's StateProvider calls "stats" / "stats_set" (signature permission
   org.lumen.updater.permission.OTA_STATE). Privacy text: README "Privacy" (`docs/repo/ROOT_README.md`, `README.ru.md`).

## Server (`server/`)

`lumen_stats.py`: Python standard library + python3-maxminddb, SQLite, on 127.0.0.1:8095 behind nginx. Today it
runs on the old host behind Cloudflare: `https://prompt.az/lumen/v1/`. Its own name `https://stats.lumen-os.net/lumen/v1/`
(our own server, TLS there, no Cloudflare: Russian networks freeze Cloudflare connections after about 20 KB) is the
first address of the clients and gets its DNS record when the server moves to a host that Russian home lines reach in
full. After the move the old host forwards every request of the old address there unchanged, with the sender's address
in a header the new server believes only from that host, so installs that know only the old address keep counting. Stores per install and day: HMAC of the install id and of
the serial (secret salt; `SERIAL_RAW = True` would keep the serial itself), model, XGIMI model, version, version
code, build, channel, country; and update events (from, to, method). The country comes from the client
address through DB-IP Lite Country, no city (CC BY 4.0, attribution "IP Geolocation by DB-IP", https://db-ip.com, refreshed
monthly by the lumen-stats-geoip timer); the address is never stored, the rate limit (20 per hour per address)
lives in memory, nginx keeps no access log for `/lumen/v1/`. `GET /lumen/v1/stats`: installs and projectors for 7
and 30 days by version, model and country, per day for 90 days, update events per day, from-to pairs, reset
projectors. Test: `python3 tools/stats/server/test_lumen_stats.py`. Deploy or update: the private deploy kit of the
server ships a pushed commit of this folder (not in the public repository).

## Problem reports (`server/lumen_reports.py`)

The upload end of the projectors' problem reports (`apps/core/lumen-report`), served by `lumen_stats.py` on the same
port: `POST /lumen/v1/report` with the report zip (at most 5 MiB; a report with the kept log of the last starts goes
in parts to `/lumen/v1/report/part`, at most 64 MiB) answers `201 {"code":"LR-XXXXXX"}`. No HTTP read
path: reports are read over ssh (`lumen_reports.py list | show CODE | cat CODE | rm CODE | prune`, or from the Mac the
private wrapper in `server/deploy/`). Stored in the state directory for 30 days, at most 8 GiB, with the HMAC of the
report install id, kind, model, version, size and sha256, never the address. Limits in memory: 6 an hour and 20 a
day per address, 10 a day per projector, 300 a day in all. Kind `test` is checked and not stored (the deploy health
check). Tests: `python3 tools/stats/server/test_lumen_reports.py`, mutants `mutate_lumen_reports.py`.

Write only from the internet: nginx names every route (ping POST, stats GET, report POST, report/part POST) and
answers 404 for anything else under `/lumen/`. The limits count the peer's own address; on the old address only a
Cloudflare edge gets in, and the forwarded sender address counts there. Dumps in parts: an upload is the pair (install id, upload id), both random
secrets of the sender, so nobody can add to or ask about another sender's upload; at most 3 open uploads per sender
(an IPv6 sender by its /64), 12 parts handled at once, idle uploads make room under pressure, the open uploads'
missing bytes count against the 3 GiB disk floor. The store is the service's state directory (0700, files 0600,
outside any web root); zips are listed, never unpacked on the server.

## Dashboard

"Lumen OS installs" (claude.ai artifact, private to the owner). Refresh:

```
python3 tools/stats/refresh.py
```

writes `build/stats/dash/{github,totals,active,models,countries,daily,updates,pairs}.json` and appends the GitHub snapshot to
`tools/stats/data/github_history.json` (GitHub keeps no history; one snapshot per refresh gives the curve).
Then upload the files to the dashboard (ask Claude: "refresh the Lumen OS installs dashboard").

## Private page on the server

`server/web/index.html` (Russian, no external resources): totals, projectors for 7 / 30 days by version, model,
country, per day, updates per day, from-to pairs, GitHub downloads per release. nginx serves it on
**127.0.0.1:8097 only**, basic auth (user and password in `XGIMI PLAY 6/vps/lumen-stats-web.txt` on the Mac, only
the apr1 hash on the server), no access log; open it through an ssh tunnel (`ssh -L 8097:127.0.0.1:8097 ...`, then
http://127.0.0.1:8097/). GitHub counts: `server/github_fetch.py`, hourly timer `lumen-stats-github` (one
unauthenticated API call), history in `/opt/lumen-stats/web/github.json`.
