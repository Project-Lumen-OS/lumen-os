# lumen-report

Problem reports of every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra, X50 Ultra Max, RS30 Ultra Max), built
once: reports on every model, sent automatically to our own server, with an off switch for the user.

## What the user sees

- **General > Send a problem report**, the shared menu row (lumen-menu `FeatureId.SETTINGS_REPORT`, a module screen:
  the menu opens it itself, no device adapter is involved; every `devices/<c>/menu.json` names `settings.report`).
- The screen (`ReportActivity`): what goes in and what is removed, **Send report** / **Cancel** (focus on Cancel),
  then the code `LR-XXXXXX` the user tells us. Without a connection the report waits in the queue and the code shows
  on the screen once it went out. The same screen has the switch **Send problem reports automatically** (on by
  default) and the codes of the last manual and automatic reports.
- Texts: `res/values*/lumen_strings_report.xml`, 17 languages (+ pt-BR). The texts of the old Z9X screen are kept
  word for word, the new ones (automatic reports, queued report) are written per language.

## Automatic reports

`ReportBoot` (BOOT_COMPLETED; DROPBOX_ENTRY_ADDED of a watched tag through a receiver registered in the running
process, `ReportBoot.listen`, because Android skips manifest receivers for that broadcast: "Background execution not
allowed") and a 6 hour job start `ReportService` SCAN: `AutoReports` reads the DropBox entries since the last look and
the boot reason of this start (`IncidentScan`):

| kind | what |
|---|---|
| crash | system_server_crash / native_crash, system_app_crash / native_crash of an image app (org.lumen.*, com.android.* without Play Store and Chrome, LineageOS) |
| anr | system_server_anr, system_app_anr of an image app |
| boot | system_server_watchdog, SYSTEM_RESTART (framework restart), SYSTEM_LAST_KMSG with a kernel panic, sys.boot.reason panic / watchdog |

Never: data_app_* (apps the user installed), tombstones of other apps, history from before the first look (the first
scan after an install only remembers the time). `AutoPolicy`: only while the image has a server, the switch is on
and the first-run setup ended (the statistics rule); one report per incident (kind + process) a day, at most 4 a
day, at most 5 incidents in one report. Turning the switch off forgets the log and the report install id, and
queued automatic reports are dropped unsent.

## Unexpected stops (a freeze, then the plug)

A projector that froze and was unplugged leaves no crash record, and on X50 every start reads a bare `reboot` (the
pstore is lost with the power). So the projector app keeps a sign of life of each start (`AliveWatch`, file
`files/report_alive`, one line, written atomically with fsync):

- **UP** with a fresh time while the picture is on: at the process start, screen on, the end of a standby, the
  screensaver starting or ending, and a beat every 10 minutes while the screen is on. It names the activity in
  front, the input (HDMI when `org.lumen.tvinput/.PassthroughActivity` is in front, else an app) and the screensaver.
- **CLEAN** on every normal way out, written before the broadcast returns: `ACTION_SHUTDOWN` and `ACTION_REBOOT`
  (every Lumen restart, power off and update restart goes through PowerManager; nothing is written after them),
  `SCREEN_OFF` (sleep, STR, the deadline of a standby), the lamp-only standby of Z9X, HB and Play 6
  (`org.lumen.projector.action.STANDBY_CHANGED` true), and the standby screen of Z9X and HB in front (an update
  restart shows it from the boot on, before this process listens).

At the first look of a start (`Alive.bootCheck`, at the process start: the provider `AliveStart` runs before
anything else in the process, also when it came back in the same start) an UP record of another start (kernel boot
id) and no orderly reason in `sys.boot.reason.last` (else `sys.boot.reason`: `reboot,<why>`, `shutdown,<why>`,
shutdown, bootloader, recovery, fastboot; not the hardware reasons longkey, undervoltage, powerloss, thermal, and
not `reboot,<x>` that bootstat made of an unknown bootloader reason x) leave `files/report_stop`. The record is
rewritten for this start right away, so there is at most one per start. The next scan takes it (also while the
switch is off, then it is dropped) and adds the incident `boot unexpected_stop` with the time of the last sign of
life, also when the clock went back after the power cut. The report (kind `boot`) holds `unexpected_stop.txt` in
plain English (last sign of life, time after that start, app in front, input, screensaver, boot reasons),
`previous_boot/` (logcat -L filtered the automatic way, pstore console, /proc/last_kmsg, z9x_diag) and the
SYSTEM_BOOT and SYSTEM_LAST_KMSG records of this start even without a panic. The usual limits hold (one a day,
consent).

Never reported: standby of any kind then the plug, an update restart, the user's restart or power off, an init
reboot (`adb reboot`, the boot gates), a damaged or missing record. Known gaps: a freeze while already in standby
is not reported (by design), and "picture off" (the lamp off with the screen on, `LampOff`) then the plug is
reported as a stop.

## What a report holds, what is removed

Manual (`ReportCollector`, at most 5 MB): summary.txt (kind, model code, versions, build, slot, trial or installed,
uptime, boot reason, Lumen apps, memory, storage, displays, thermal state, network TYPE), props.txt (`PropFilter`:
build, boot, display, Lumen and model keys, never serial, MAC, Wi-Fi, account, address or secret keys), logcat of the
last 30 minutes of the system and image apps (`LogcatFilter`), the crash buffer, the kernel log, the previous start
(pstore, z9x_diag), update logs, DropBox records of 7 days, exit reasons.
Automatic (at most 1 MB): summary.txt with the incidents, props.txt, 10 minutes of logcat and the crash buffer of the
system and image apps only, the DropBox records of those incidents of the last day.
Every line passes `Scrubber`: e-mail, IP and MAC addresses, Wi-Fi names (also the saved ones and URL-encoded),
paired Bluetooth names, the projector's own name, account and user names, phone numbers, serials and device ids
(android_id, gsf_id, gaia_id), tokens, passwords, cookies, mDNS host names. The install id of reports is random
(not the serial, not the statistics id).

## Where it goes

`ReportUrl`: our statistics server (`tools/stats/server/lumen_reports.py`, served by `lumen_stats.py`), by default
`https://prompt.az/lumen/v1/report` through Cloudflare. `ro.lumen.report.url` may list several https addresses
(spaces or commas between them, `off` switches reports off); `ReportUploader.uploadAny` tries them in that order;
`persist.lumen.report.url` overrides the list on one unit for tests.

Networks in Russia freeze every connection to a Cloudflare address after about 20 KB (measured 2026-11-14 on two
Russian networks and from a projector: 20 KB goes through, 32 KB and more never, plain http too; the server's own
address took 1 MB in half a second). A report is bigger, so from Russia it needs an address that reaches the server
without Cloudflare, listed first in `ro.lumen.report.url` with the Cloudflare one after it (server side:
`tools/stats/server/deploy/nginx-report-direct.conf`). One upload is cut after 30 seconds plus 16 KB a second
(`ReportUploader.deadlineMs`; the sockets are closed hard, a polite close waits behind the stuck writer): a frozen
connection fails fast and the next address gets its turn. The next address is also tried after a server failure or
an answer that is not our server's (3xx, 404, 405); 400, 411, 413, 415 and 429 end it (they judge the report or the
sender, the same everywhere). Live check from a host: `sh apps/core/lumen-report/test/live_upload.sh KB URL [URL...]`.
Large zips (Lumen Installer's stock system info, about 100 MB) go with `PartUpload`: four parts on their way at once,
each on its own connection, 512 KiB each until one gets no answer, then 12 KiB for good (`choked()`, kept by the caller
with the upload id), every part sent again up to six times. The server keeps the byte ranges it has in any order and
answers a state query (`X-Lumen-Query: 1`), so an upload that stopped goes on where it stopped, also after a restart.
The queue (`ReportQueue`, files/report_queue) keeps at
most 6 reports and 8 MB (automatic ones leave first), retries after 1, 5, 15 minutes, 1, 3, 6, 12 hours, drops a
report after 7 days or 12 tries; a refusal of the server (400, 413, 415) drops it at once.

## Host app wiring (every projector app)

Recipe: `module lumen-report` (before `module lumen-menu`, which depends on it). Manifest (see any projector app):
permissions INTERNET, READ_LOGS, DUMP, PACKAGE_USAGE_STATS, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE,
RECEIVE_BOOT_COMPLETED; the activity `org.lumen.report.ReportActivity`, the service `org.lumen.report.ReportService`
(BIND_JOB_SERVICE), the receiver `org.lumen.report.ReportBoot` (BOOT_COMPLETED; its DROPBOX_ENTRY_ADDED filter is
never served by Android, the run-time receiver of `ReportBoot.listen` does that work, the host app is persistent) and
the provider `org.lumen.report.AliveStart` (authority `<package>.alive`, not exported, not direct boot aware:
it starts the unexpected-stop watch at every process start). REAL_GET_TASKS names the activity in front. READ_LOGS and
DUMP are signature|privileged: platform-signed apps get them, a priv-app on an enforcing image also needs them in its
privapp-permissions file (HB: devices/goat_elegant/image/privapp-permissions-hbprojector.xml). Android 13+ asks the
user before an app that is not the system uid reads all logs: without that answer logcat.txt holds only this app's
lines (summary.txt says "LIMITED log access"), DropBox records are not affected.

## Tests

- `sh apps/core/lumen-report/test/run.sh`: ReportTest (redaction, incidents, consent and limits, queue, address, zip,
  answers, the unexpected stop: a freeze against every normal way out over several simulated starts), every module
  source compiled against android-34, `check_repo.py` (texts in every language, recipes, manifests, menu row, consent
  lines, the standby contract of the apps that send STANDBY_CHANGED). Run it on builder-llm
  (`ANDROID_SDK=~/lumen-tools/android-sdk`).
- `python3 apps/core/lumen-report/test/mutate.py [NAME_PART...]`: every mutant must turn run.sh red (in place: a
  frozen clone only); name parts pick a subset (`stop orderly standby` for the unexpected stop).
- Server: `python3 tools/stats/server/test_lumen_reports.py`, `python3 tools/stats/server/mutate_lumen_reports.py`.

## Our server or Cloudflare

`cloud/report-worker` is a ready Cloudflare Worker + R2 version of the same upload (never deployed). We send to our
own server instead: it needs no new account or key, keeps the reports in the same place as the statistics, has no
public read path at all (reports are read over ssh only), and its limits
(5 MB per report, 2 GB store, 30 days) fit the 16 GB free disk of the VPS. Cloudflare would add global reach without
the VPS in the path and R2's free 10 GB, but needs an account, a wrangler token and the R2 payment setup, and puts
user logs on a third party. The client keeps both open: `ro.lumen.report.url` points an image at the Worker.
