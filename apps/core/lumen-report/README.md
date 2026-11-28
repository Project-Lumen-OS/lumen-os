# lumen-report

Problem reports of every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra, X50 Ultra Max, RS30 Ultra Max), built
once: reports on every model, sent automatically to our own server, with an off switch for the user.

## What the user sees

- **General > Send a problem report**, the shared menu row (lumen-menu `FeatureId.SETTINGS_REPORT`, a module screen:
  the menu opens it itself, no device adapter is involved; every `devices/<c>/menu.json` names `settings.report`).
- The screen (`ReportActivity`, owner pick A 2026-11-26) is the shared
  two-pane page of lumen-ui (`org.lumen.ui.page.TwoPane`): on the left where the user is, what a report does and the
  code of the last report (manual or automatic, the newer one), a privacy line at the bottom; on the right the actions
  **Send report** (the focus starts here), **Send automatically** (switch, on by default), **What is in a report**
  (a page: what a report holds and what is never sent, Back) and **Close**. Sending shows a moving bar with Cancel,
  then the code `LR-XXXXXX` in large even digits to read out; a refused report says why with **Try again**; without
  a connection the report waits in the queue (the offline screen) and its code shows as the last report once it went
  out. BACK goes back to the start from every screen (a running report is stopped), and leaves from the start.
  Which screen, which actions and the first focus are `ReportView` (host tested); the page follows the light / dark
  theme live (lumen-ui ThemeFade). Icons: Lucide line icons of the mock, `tools/report_icons.py` (ISC).
- Texts: `res/values*/lumen_strings_report.xml`, 17 languages (+ pt-BR), written per language.

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
whole buffer (main, system, events) of the system and image apps (`LogcatFilter`), the crash buffer, the kernel log of
this start (dmesg), the previous start (pstore, z9x_diag), update logs, DropBox records of 7 days, exit reasons,
hdmi.txt (`HdmiDump`: dumpsys hdmi_control with the last 250 CEC messages, the power status, System Audio and ARC;
the CEC journal of the system log (`CecJournal`): every CEC message the framework sent or received since the system log
buffer begins, a soundbar's power status questions every 2 s counted as one line per unchanged question and answer
between two other messages; then the HDMI / ARC / System Audio lines of dumpsys audio) and power.txt (`PowerDump`: dumpsys power without the
wake lock and uid lists, which name apps).
boots/ (`BootLogs`, the persistent log of the last starts, below): this start and the one before with their boot
and their last part (log 8 + 40 MiB and 8 + 24 MiB of text), the important lines, kernel log and dumpsys snapshots
of every kept start, pstore of a hard stop, info.txt per start and budget.txt; gzipped entries, about 15 MB at most.
Automatic (at most 1 MB): summary.txt with the incidents, props.txt, the newest logcat lines that fit and the crash
buffer of the system and image apps only, the DropBox records of those incidents of the last day.
Every line passes `Scrubber`: e-mail, IP and MAC addresses, Wi-Fi names (also the saved ones and URL-encoded),
paired Bluetooth names, the projector's own name, account and user names, phone numbers, serials and device ids
(android_id, gsf_id, gaia_id), tokens, passwords, cookies, mDNS host names. The install id of reports is random
(not the serial, not the statistics id).

## The log helper (always the full system log, never a dialog)

The report app is not the system uid on every model (Z9X: an app uid), and Android 13+ gives such an app only its own
log lines unless the user answers a log access dialog. Reports never ask (owner 2026-11-26): every image carries the
root oneshot `image/lumen_report.{rc,sh}` (`/system/etc/init`, `/system/etc/lumen`, staged for every device by
`tools/build/build.py` COMMON_FILES; `build.py check` refuses a device whose recipe does not place it). `RootLogs`
sets `sys.lumen.report.req` to a fresh token ([0-9a-z-], at most 24), the helper writes `/dev/lumen_report`
(tmpfs; folder 0750, files 0640, owner root, group = the uid of the report app from packages.list, `HOSTS`):
`token`, `logcat.txt` (main, system, events, the last 6 MiB), `crash.txt`, `kernel.txt` (dmesg, 1 MiB),
`hdmi_control.txt`, `audio.txt`, `power.txt` (dumpsys, 5 s and 256 KiB each), every line scrubbed in the helper
(e-mail and MAC addresses, Wi-Fi names, passwords, keys, tokens, authorization headers, secret URL parameters, account
names), answers `sys.lumen.report.done` = `<token> ok|busy|none|refused` (busy: less than 10 s after the last one;
refused: `ro.lumen.report.url=off`; none: no report app), and removes the files when the app set
`sys.lumen.report.ack` to the token, at the latest after 60 s. The app takes the files only when `token` matches,
passes the logs through `LogcatFilter` and `Scrubber` as before and the dumps through `HdmiDump` and `PowerDump` (the
whole audio dump lists the apps that played sound and stays out). Without an answer in 30 s (90 s for a manual report: a
busy unit can take longer, evleo790 LR-9MEQZP had no system log, kernel log or dump after 30 s) the app runs logcat itself
(often its own lines only; summary.txt says which source it was; a run the time limit stops keeps what it read) and
hdmi.txt names both parts not readable. When the helper answers while the app read its own log, its files replace the
app's ("answered late" in summary.txt).

settings.txt of a manual report (`SettingsDump`): the rows of Settings.Global, Secure and System whose key names HDMI,
CEC, ARC, eARC, sound or volume, the "CEC settings:" block of the helper's dumpsys hdmi_control (every HdmiCecConfig
value with its default) and the report app's own shared preferences files about CEC, sound, HDMI and power (night
guard, output choice, one-time steps), as key=value lines. The CEC journal leaves out HdmiCecConfig's read lines
("Getting CEC setting value", "Reading ... shared preference", most of LR-9MEQZP's journal) and keeps a written value.

## The persistent log of the last starts

The logcat ring of these images holds about a minute next to the vendor's log flood and the previous start's log is
not readable, so `image/lumen_report.sh keep` (service `lumen_logkeep` of `lumen_report.rc`, from post-fs-data on,
restarted by init) keeps the log of this start and the 5 before it in `/data/misc/lumen_logs/bNNNNNN/`: logcat main,
system, events, crash and radio (`log.N`), its important lines (`imp.N`: crash buffer, errors, system life cycle,
power, HDMI-CEC, sound, input, updates, Lumen tags), `dmesg -w` (`kernel.N`), dumpsys hdmi_control, audio, power,
input and meminfo 1 minute after boot completed and then every 15 minutes (30 on a low-RAM unit) (`snap.N`), pstore
of a hard stop (read at the next start) and info.txt (boot id, build, the next start's boot reason). Every line is
scrubbed when written (the helper's rules); after the first 3 minutes of a start a tag that is not important keeps at
most 600 lines a minute, the rest is one counted line. Chunks of 4 MiB (1 MiB for the other streams) are gzipped once
finished; the open one is synced every 2 s, so a power cut loses about the last 2 seconds (emulator: kill -9 of the
keeper and an immediate reboot without sync lost 1.3 s). Budget (`budget.txt`): 10 % of free /data plus what the logs
hold, at most 5 GiB; Lumen OS Live 5 % and at most 1 GiB; half the ceiling on a low-RAM unit (under 2.5 GiB or
`ro.config.low_ram`); never below 1 GiB free on /data. Over it go first the middle chunks of older starts' logs, then
of this start's log, then the other streams' middles of older starts, whole older starts, the other streams' middles
of this start; the first 2 and the last 2 chunks of this start stay. `persist.lumen.logkeep=off` switches it off.
A report request also gets the kept files: hard links in `/data/misc/lumen_logs/export` (0750, group = the report app)
listed in `persist.txt`; the app (`BootLogs`) picks the chunks per start, filters (`LogcatFilter`) and scrubs
(`Scrubber`) every line again, and the helper waits up to 10 minutes for its ack. A report over 5 MB goes in parts
(`ReportUploader.parts`, `PartUpload`, upload id = the start of the zip's sha256, so a try from the queue resumes it).
Tests: `test/logkeep_test.sh` (host), mutants `keep` and `bootlogs` of `test/mutate.py`.

## Where it goes

`ReportUrl`: our statistics server (`tools/stats/server/lumen_reports.py`, served by `lumen_stats.py`), by default
`https://stats.lumen-os.net/lumen/v1/report` straight to our server, then `https://prompt.az/lumen/v1/report`
through Cloudflare (the old address forwards to the same server; it stays the second try for one release).
`ro.lumen.report.url` may list several https addresses (spaces or commas between them, `off` switches reports off);
`ReportUploader.uploadAny` tries them in that order; `persist.lumen.report.url` overrides the list on one unit for
tests.

Networks in Russia freeze every connection to a Cloudflare address after about 20 KB (measured 2026-11-14 on two
Russian networks and from a projector: 20 KB goes through, 32 KB and more never, plain http too; the server's own
address took 1 MB in half a second). A report is bigger, which is why the default address reaches the server without
Cloudflare. One upload is cut after 30 seconds plus 16 KB a second
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
privapp-permissions file (HB: devices/goat_elegant/image/privapp-permissions-hbprojector.xml). The system log itself
comes from the log helper (above), so no app ever shows Android's log access dialog.

## Tests

- `sh apps/core/lumen-report/test/run.sh`: ReportTest (redaction, incidents, consent and limits, queue, address, zip,
  the log helper's token, answer and file rules (RootLogs), hdmi.txt and power.txt,
  answers, the unexpected stop: a freeze against every normal way out over several simulated starts), every module
  source compiled against android-34, `report_helper_test.sh` (the helper on a fake root: tokens, refusals,
  scrub, caps, modes, cleanup), `check_repo.py` (texts in every language, recipes, manifests, menu row, consent
  lines, the standby contract of the apps that send STANDBY_CHANGED). Run it on builder-llm
  (`ANDROID_SDK=~/lumen-tools/android-sdk`).
- `sh apps/core/lumen-report/test/ui/run_walk.sh SERIAL OUT [DEVICE:APP ...]`: the screen's key walk on a ROOTED
  emulator (persist.lumen.report.url must take, else it stops before any Send: an unrooted image would send the test
  reports to our real server), against `test/ui/fake_server.py` on the host; asserts focus, pages, the switch, the
  code, failed and offline in Russian and English, light and dark, one screenshot per state.
- `python3 apps/core/lumen-report/test/mutate.py [NAME_PART...]`: every mutant must turn run.sh red (in place: a
  frozen clone only); name parts pick a subset (`stop orderly standby` for the unexpected stop).
- Server: `python3 tools/stats/server/test_lumen_reports.py`, `python3 tools/stats/server/mutate_lumen_reports.py`.

## Our server or Cloudflare

`cloud/report-worker` is a ready Cloudflare Worker + R2 version of the same upload (never deployed). We send to our
own server instead: it needs no new account or key, keeps the reports in the same place as the statistics, has no
public read path at all (reports are read over ssh only), and its limits
(5 MB per report in one request, 64 MiB in parts, 8 GiB store, 30 days) fit the 82 GB free disk of the VPS. Cloudflare would add global reach without
the VPS in the path and R2's free 10 GB, but needs an account, a wrangler token and the R2 payment setup, and puts
user logs on a third party. The client keeps both open: `ro.lumen.report.url` points an image at the Worker.

## CEC journal

system_server prints a CEC message (HdmiLogger `[S]:` / `[R]:`, tag HDMI, `<Name> SD:OP:params`, OSD names and vendor
commands hidden by the framework) only when `log.tag.HDMI` allows DEBUG, read once when system_server loads the class.
`image/lumen_report.rc` sets `log.tag.HDMI D` at early-init in every image. The message text is built either way, so
the cost is the log line: one per message, about 60 a minute with a soundbar that asks the power status every 2 s
(evleo790 HB, LR-VRE2Z1: 58 messages a minute in the dump). The 250 entries of dumpsys hdmi_control reach back about
4 minutes there; the system log buffer (256 KiB compressed, Android 14 logd ignores size props) about 36 minutes with
these lines added (40 without them). Host replay of that report: `~/lumen-build-tmp/cec-journal-1126/`.
