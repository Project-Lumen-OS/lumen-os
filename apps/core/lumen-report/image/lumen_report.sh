#!/system/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen problem report logs (/system/etc/lumen/lumen_report.sh 0755, started by lumen_report.rc as a root oneshot).
# The report app (apps/core/lumen-report RootLogs, inside the projector app) is not the system uid on every model, and
# Android 13+ hands such an app only its own log lines unless the user answers a dialog. Problem reports must always
# carry the system log without asking (2026-11-26), so the app sets sys.lumen.report.req to a fresh token and this
# script collects the logs as root, answers in sys.lumen.report.done = "<token> ok|busy|none|refused", waits until the
# app acknowledged the read (sys.lumen.report.ack = <token>, at most ACK_STEPS) and removes the files again.
#
# Output /dev/lumen_report (tmpfs, gone at reboot), folder 0750 and files 0640, owner root, group = the uid of the
# report app (HOSTS, read from packages.list), so no other app can read them:
#   token              the request token (the app reads nothing whose token differs)
#   logcat.txt         logcat -d -D of main, system and events (-v threadtime -v uid), the last CAP_LOG bytes
#   crash.txt          the crash buffer, the last CAP_CRASH bytes
#   kernel.txt         dmesg, the last CAP_KERNEL bytes
#   hdmi_control.txt   dumpsys hdmi_control (CEC), audio.txt dumpsys audio, power.txt dumpsys power: the first
#                      CAP_DUMP bytes each, every dumpsys bounded to DUMP_TIMEOUT seconds
# Every line passes scrub() before it is written: e-mail addresses, MAC addresses (BSSID), Wi-Fi names (ssid keys,
# quoted names after SSID, WifiConfiguration keys, wpa_supplicant id_str), passwords, keys, tokens, cookies,
# authorization headers, secret URL parameters and account names. The app's Scrubber runs over everything again (it
# also knows the saved Wi-Fi names, paired device names and serials literally) and its LogcatFilter keeps only lines of
# the system and the image's own apps.
# A request closer than MIN_GAP_CS after the last collection answers busy; a token that is not 1-24 of [0-9a-z-] gets
# no answer and nothing runs; an image whose reports are off (ro.lumen.report.url=off) answers refused.
# A request that comes while this runs is not started twice by init: the script serves the newest token before it exits.
# Persistent log (lumen_report.sh keep, service lumen_logkeep of lumen_report.rc): see the keep section below. A
# report request also hands the app the kept files of the last starts (persist.txt, export folder below).
# mksh + toybox + awk only. The scrub is awk, never sed or grep: toybox 0.8.9 sed and grep crash (SIGSEGV, now and
# then) on a bracket expression over a line with non-ASCII text (an emoji, e-acute, Chinese), which cut the log at the
# first such line (Android 14 emulator, 2026-11-26); /system/bin/awk (one-true-awk) reads the same lines.

# R is empty on the device (init gives services a clean environment); the host test sets it and the step count.
R=${LUMEN_REPORT_ROOT:-}
OUT=$R/dev/lumen_report
PKGS=$R/data/system/packages.list
HOSTS="org.lumen.projector org.lumen.x50.projector"
MIN_GAP_CS=1000
# 0.25 s steps: the app reads the files within a few seconds, 60 s at most
ACK_STEPS=${LUMEN_REPORT_ACK_STEPS:-240}
CAP_LOG=6291456
# what logcat may hand the scrub at most: the cap plus room for the markers
RAW_LOG=7340032
CAP_CRASH=524288
CAP_KERNEL=1048576
CAP_DUMP=262144
DUMP_TIMEOUT=5
FILES="token logcat.txt crash.txt kernel.txt hdmi_control.txt audio.txt power.txt persist.txt"
# the app reads the kept starts for up to some minutes (filter and scrub of up to a few hundred MB): the wait for its
# ack when it got them, 0.25 s steps
ACK_STEPS_KEPT=${LUMEN_REPORT_ACK_STEPS_KEPT:-2400}

# The scrub: awk functions (SCRUB_LIB: scrub_init() once, then scrub(line); no single quote inside: \047) shared by
# the report collection (SCRUB_AWK) and the persistent log (keep). Keys match case-insensitively on tolower(line),
# which keeps every byte offset; a rule runs only when its cheap index() test finds its key on the line.
SCRUB_LIB='
function whole(s, re, mark,   o, lc) {
  o = ""
  while (match(tolower(s), re)) { o = o substr(s, 1, RSTART - 1) mark; s = substr(s, RSTART + RLENGTH) }
  return o s
}
function keyed(s, pre, val, mark,   o, st, ln, m) {
  o = ""
  while (match(tolower(s), pre val)) {
    st = RSTART; ln = RLENGTH; m = substr(tolower(s), st, ln)
    match(m, "^" pre)
    o = o substr(s, 1, st - 1) substr(s, st, RLENGTH) mark; s = substr(s, st + ln)
  }
  return o s
}
function email(s,   o, m, at) {
  o = ""
  while (match(s, /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z][A-Za-z]+/)) {
    m = substr(s, RSTART, RLENGTH); at = index(m, "@")
    if (substr(m, at + 1) ~ /^[0-9]+\.[0-9]/) { o = o substr(s, 1, RSTART + at - 1); s = substr(s, RSTART + at) }
    else { o = o substr(s, 1, RSTART - 1) "<email>"; s = substr(s, RSTART + RLENGTH) }
  }
  return o s
}
function wifikey(s,   o, m, q) {
  o = ""
  while (match(tolower(s), WIFI)) {
    m = substr(s, RSTART, RLENGTH); q = index(substr(m, 2), "\"") + 1
    o = o substr(s, 1, RSTART - 1) "\"<ssid>\"" substr(m, q + 1); s = substr(s, RSTART + RLENGTH)
  }
  return o s
}
function scrub_init() {
  SEP = "[\"\047]?[ \t]*[:=][ \t]*"
  VAL = "(\"[^\"]*\"|\047[^\047]*\047|[^ \t,;&}\"\047)]+)"
  H = "[0-9a-f][0-9a-f]"
  MAC = H ":" H ":" H ":" H ":" H ":" H
  KEYS = "(password|passwd|passphrase|psk|pre_?shared_?key|wep_?key[0-9]?|auth_?token|access_?token|refresh_?token|id_?token|api_?key|client_?secret|secret|session_?id|cookie|authorization)"
  QKEYS = "(token|access_token|id_token|auth|key|api_key|apikey|sig|signature|password|pass|pwd|session|sid|secret|code|ticket)"
  WIFI = "\"[^\"]+\"-?(wpa|wep|sae|owe|wapi|ieee8021x|none|open|suite_b|dpp|osen|passpoint)"
}
function scrub(s,   lc) {
  lc = tolower(s)
  if (index(s, "@")) s = email(s)
  if (index(s, ":")) s = whole(s, MAC, "<mac>")
  if (index(lc, "ssid")) {
    s = keyed(s, "ssid" SEP, VAL, "<ssid>")
    s = keyed(s, "ssid[ \t]+", "(\"[^\"]*\"|\047[^\047]*\047)", "\047<ssid>\047")
  }
  if (index(s, "\"")) s = wifikey(s)
  if (index(lc, "id_str=")) s = keyed(s, "id_str=", "[^ \t]+", "<ssid>")
  if (lc ~ /pass|psk|key|token|secret|session|cookie|auth/) {
    s = keyed(s, KEYS SEP, "((bearer|basic|digest|token)[ \t]+)?" VAL, "<secret>")
    if (index(s, "=")) s = keyed(s, "[?&;]" QKEYS "=", "[^&# \t\"\047<>]+", "<secret>")
  }
  if (index(lc, "account")) s = keyed(s, "account[ \t]*[{][ \t]*name=", "[^,}]*", "<account>")
  return s
}'
SCRUB_AWK=$SCRUB_LIB'
BEGIN { scrub_init() }
{ print scrub($0) }'
scrub() { awk "$SCRUB_AWK"; }

# scrubbed IN OUT: IN scrubbed into OUT, or one line saying it could not be: never an unscrubbed line
scrubbed() {
  scrub < "$1" > "$2" && return 0
  echo "(left out: the text could not be scrubbed)" > "$2"
}

# collect NAME CAP COMMAND...: the scrubbed output of COMMAND, its newest CAP bytes
collect() {
  name=$1 cap=$2
  shift 2
  "$@" 2>&1 | tail -c "$RAW_LOG" > "$OUT/.raw"
  scrubbed "$OUT/.raw" "$OUT/.scrubbed"
  tail -c "$cap" "$OUT/.scrubbed" > "$OUT/$name.new"
  rm -f "$OUT/.raw" "$OUT/.scrubbed"
  put "$name"
}

valid() {
  case "$1" in ''|*[!0-9a-z-]*) return 1 ;; esac
  [ "${#1}" -le 24 ]
}

clear_out() {
  for f in $FILES; do rm -f "$OUT/$f" "$OUT/$f.new"; done
  rm -f "$OUT/.raw" "$OUT/.scrubbed"
  rm -rf "$LK/export"
}

# the kept starts for the app: LK/export (0750, group = the report app) holds a hard link of every finished
# (compressed) chunk and a copy of every open one, named <start>.<file>; persist.txt lists "<name> <bytes>" (pstore
# files as <start>.pstore.<file>) plus budget.txt. The chunks were scrubbed when they were written (keep).
export_kept() {
  X=$LK/export
  rm -rf "$X"
  ls -d "$LK"/b[0-9]* > /dev/null 2>&1 || return 1
  mkdir -p "$X" && chown "0:$gid" "$X" && chmod 0750 "$X" || return 1
  : > "$OUT/persist.txt.new"
  for b in "$LK"/b[0-9]*; do
    [ -d "$b" ] || continue
    bn=${b##*/}
    for f in "$b"/* "$b"/pstore/*; do
      [ -f "$f" ] || continue
      case "$f" in "$b"/pstore/*) name=$bn.pstore.${f##*/} ;; *) name=$bn.${f##*/} ;; esac
      case "$f" in
        *.gz) ln "$f" "$X/$name" 2> /dev/null || cp "$f" "$X/$name" ;;
        *) cp "$f" "$X/$name" ;;                     # an open chunk: the writer goes on adding to it
      esac
      chown "0:$gid" "$X/$name" && chmod 0640 "$X/$name" || continue
      echo "$name $(stat -c %s "$X/$name")" >> "$OUT/persist.txt.new"
    done
  done
  [ -f "$LK/budget.txt" ] && cp "$LK/budget.txt" "$X/budget.txt" && chown "0:$gid" "$X/budget.txt" && chmod 0640 "$X/budget.txt" && echo "budget.txt $(stat -c %s "$X/budget.txt")" >> "$OUT/persist.txt.new"
  put persist.txt
}

# the uid of the report app: its group may read the output
host_gid() {
  for p in $HOSTS; do
    re=$(printf '%s' "$p" | sed 's/\./\\./g')
    g=$(sed -n "s/^$re \([0-9][0-9]*\) .*/\1/p" "$PKGS" 2>/dev/null | head -n 1)
    [ -n "$g" ] && { echo "$g"; return 0; }
  done
  return 1
}

# put NAME: OUT/NAME.new (written by the caller) gets its owner and mode, then replaces NAME
put() {
  chown "0:$gid" "$OUT/$1.new" && chmod 0640 "$OUT/$1.new" && mv -f "$OUT/$1.new" "$OUT/$1"
}

serve() {
  clear_out
  [ "$(getprop ro.lumen.report.url)" = off ] && { setprop sys.lumen.report.done "$req refused"; return; }
  gid=$(host_gid) || { setprop sys.lumen.report.done "$req none"; return; }
  mkdir -p "$OUT" && chown "0:$gid" "$OUT" && chmod 0750 "$OUT" || { setprop sys.lumen.report.done "$req none"; return; }

  # the floor between two collections, in 1/100 s of uptime
  now=$(sed -n 's/^\([0-9]*\)\.\([0-9][0-9]\).*/\1\2/p' "$R/proc/uptime" 2>/dev/null)
  case "$now" in ''|*[!0-9]*) setprop sys.lumen.report.done "$req none"; return ;; esac
  last=$(cat "$OUT/.last" 2>/dev/null)
  case "$last" in ''|*[!0-9]*) last=0 ;; esac
  gap=$((now - last))
  if [ "$last" -ne 0 ] && [ "$gap" -ge 0 ] && [ "$gap" -lt "$MIN_GAP_CS" ]; then
    setprop sys.lumen.report.done "$req busy"
    return
  fi
  echo "$now" > "$OUT/.last"
  chmod 0600 "$OUT/.last"
  umask 077

  collect logcat.txt "$CAP_LOG" logcat -d -D -b main,system,events -v threadtime -v uid
  collect crash.txt "$CAP_CRASH" logcat -d -b crash -v threadtime -v uid
  collect kernel.txt "$CAP_KERNEL" dmesg
  for s in hdmi_control audio power; do
    dumpsys -t "$DUMP_TIMEOUT" "$s" 2>&1 | head -c "$CAP_DUMP" > "$OUT/.raw"
    scrubbed "$OUT/.raw" "$OUT/.scrubbed"
    head -c "$CAP_DUMP" "$OUT/.scrubbed" > "$OUT/$s.txt.new"
    rm -f "$OUT/.raw" "$OUT/.scrubbed"
    put "$s.txt"
  done
  steps=$ACK_STEPS
  export_kept && steps=$ACK_STEPS_KEPT
  printf '%s\n' "$req" > "$OUT/token.new"
  if ! put token; then
    clear_out
    setprop sys.lumen.report.done "$req none"
    return
  fi
  setprop sys.lumen.report.done "$req ok"

  # the app reads the files, then acknowledges; they never stay longer than ACK_STEPS (ACK_STEPS_KEPT with kept starts)
  n=0
  while [ "$n" -lt "$steps" ]; do
    [ "$(getprop sys.lumen.report.ack)" = "$req" ] && break
    [ "$(getprop sys.lumen.report.req)" = "$req" ] || break
    sleep 0.25
    n=$((n + 1))
  done
  clear_out
}

# ============================================================================================================ keep
# The persistent log of this start and the 5 before it: lumen_report.sh keep, service lumen_logkeep of
# lumen_report.rc, started at post-fs-data and restarted by init whenever it ends. The logcat ring of these images
# (256 KiB compressed, Android 14 logd ignores its size props) holds about a minute next to the vendor's flood
# (Motion_det_ctl, FaceCutout, GM_DISP) and the previous start's log is not readable, so a report lost the first
# minutes of a start and the moments before a freeze or a power cut. Here, under LK (0711; every start's folder 0700):
#   bNNNNNN/info.txt          start number, kernel boot id, wall time, build; later the boot reason of the next start
#   bNNNNNN/log.NNNNNN.txt    logcat main, system, events, crash, radio (-D -v threadtime -v uid, the report's format)
#   bNNNNNN/imp.NNNNNN.txt    the important lines of the same stream (crash buffer, E/F, system tags IMP): small, so
#                             a report can take them for every start
#   bNNNNNN/kernel.NNNNNN.txt dmesg -w (the kernel ring from the start of this boot on)
#   bNNNNNN/snap.NNNNNN.txt   dumpsys SNAP_SERVICES at SNAP_FIRST_S after boot completed, then every SNAP_S
#   bNNNNNN/pstore/           pstore / last_kmsg read at the NEXT start (the kernel's own record of a hard stop)
# Every line is scrubbed when written (SCRUB_LIB). After the first OPEN_MIN minutes of a start, a tag that is not
# important keeps at most TAG_CAP lines a minute (the rest is one counted line), so the vendor flood can neither fill the budget nor push out our lines. A chunk ends
# at CHUNK_LOG / CHUNK_SMALL bytes and is gzipped once finished (written whole, synced, then renamed); the open chunk
# is synced every SYNC_S seconds, so a power cut loses at most those last seconds.
# Budget (budget.txt): CAP_MAX_K at most, PCT % of what /data has free plus what these logs hold (PCT_LIVE and at most
# CAP_LIVE_K on Lumen OS Live, whose data is small), half of that on a low-RAM unit, and never so much that /data keeps
# less than FLOOR_K free. Over it, chunks go in this order: the middle of an older start's log, the middle of this
# start's log, the middle of an older start's other streams, whole older starts (oldest first), the middle of this
# start's other streams; the first HEAD_CHUNKS and last TAIL_CHUNKS chunks of this start always stay.
# persist.lumen.logkeep=off: nothing is kept.
LK=$R/data/misc/lumen_logs
KEEP_BOOTS=6
CHUNK_LOG=${LUMEN_LOGKEEP_CHUNK:-4194304}
CHUNK_SMALL=${LUMEN_LOGKEEP_CHUNK_SMALL:-1048576}
HEAD_CHUNKS=2
TAIL_CHUNKS=2
TAG_CAP=${LUMEN_LOGKEEP_TAG_CAP:-600}
# the first minutes of a start are kept whole (the boot itself is a burst of every tag)
OPEN_MIN=3
CAP_MAX_K=5242880
PCT=10
PCT_LIVE=5
CAP_LIVE_K=1048576
LOWRAM_K=2621440
FLOOR_K=1048576
MIN_K=16384
SYNC_S=${LUMEN_LOGKEEP_SYNC_S:-2}
HOUSE_S=${LUMEN_LOGKEEP_HOUSE_S:-60}
SNAP_FIRST_S=${LUMEN_LOGKEEP_SNAP_FIRST_S:-60}
SNAP_S=900
SNAP_S_LOWRAM=1800
SNAP_CAP=262144
SNAP_SERVICES="hdmi_control audio power input meminfo"
PSTORE_CAP=2097152
LOGCAT_ARGS="-b main,system,events,crash,radio -D -v threadtime -v uid"
# never capped, and copied to imp: crashes, the system's life cycle, power, HDMI-CEC, sound, input, updates, ours
IMP='^(AndroidRuntime|DEBUG|libc|crash_dump(32|64)?|tombstoned|Watchdog|SystemServer|Zygote|zygote(64)?|ActivityManager|ActivityTaskManager|PowerManagerService|ShutdownThread|BootReceiver|bootstat|lowmemorykiller|lmkd|init|vold|HDMI|Hdmi[A-Za-z]*|CEC[A-Za-z]*|AudioService|InputDispatcher|update_engine|UpdateEngine|gsid|apexd|[Ll]umen.*|Z9x.*|Hb[A-Z].*|X50.*|am_[a-z_]+|boot_progress_[a-z_]+|power_[a-z_]+|screen_toggled|sysui_[a-z_]+|wm_on_[a-z_]+|input_focus|[Tt]hermal.*)$'

# the writer: stdin (logcat or dmesg) into dir/<stream>.NNNNNN.txt (+ dir/imp.NNNNNN.txt), scrubbed, capped per tag,
# every line flushed; -v dir stream chunk ichunk n0 i0 cap open (minutes without the cap: the boot) after (kernel:
# drop lines up to this time) imp (regex)
WRITER_AWK='
function next_chunk() {
  if (cur != "") close(cur)
  n++; cur = sprintf("%s/%s.%06d.txt", dir, stream, n); bytes = 0
}
function next_imp() {
  if (icur != "") close(icur)
  ni++; icur = sprintf("%s/imp.%06d.txt", dir, ni); ibytes = 0
}
function put(line) {
  if (cur == "" || bytes >= chunk) next_chunk()
  print line > cur; fflush(cur); bytes += length(line) + 1
}
function put_imp(line) {
  if (icur == "" || ibytes >= ichunk) next_imp()
  print line > icur; fflush(icur); ibytes += length(line) + 1
}
function capped(   t, m) {
  for (t in drop) {
    if (stream == "log") m = (last != "" ? last : "01-01 00:00:00.000") "  root     0     0 W lumen-logkeep: " t ": " drop[t] " more lines in that minute left out (over " cap ")"
    else m = "[lumen-logkeep] " t ": " drop[t] " more lines in that minute left out (over " cap ")"
    put(m)
    if (stream == "log") put_imp(m)
    delete drop[t]
  }
  for (t in cnt) delete cnt[t]
}
BEGIN { scrub_init(); n = n0 + 0; ni = i0 + 0; cur = ""; icur = ""; win = -1; buf = "main"; after = after + 0; wins = 0 }
{
  line = $0; tag = ""; lvl = ""; w = win
  if (stream == "log") {
    if (substr(line, 1, 10) == "--------- ") {
      buf = line; sub(/^--------- (beginning of|switch to) /, "", buf)
      put(line); next
    }
    if (match(line, /^[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9]+ +[^ ]+ +[0-9]+ +[0-9]+ [VDIWEFSA] /)) {
      lvl = substr(line, RLENGTH - 1, 1); rest = substr(line, RLENGTH + 1)
      c = index(rest, ": "); tag = c ? substr(rest, 1, c - 1) : rest; sub(/ +$/, "", tag)
      w = substr(line, 7, 2) * 60 + substr(line, 10, 2); stamp = substr(line, 1, 18)
    }
  } else if (match(line, /^\[ *[0-9]+\.[0-9]+\]/)) {
    ts = substr(line, 2, RLENGTH - 2) + 0
    if (ts <= after) next
    w = int(ts / 60); tag = substr(line, RLENGTH + 2); sub(/[ :\[(].*$/, "", tag); tag = substr(tag, 1, 32)
  }
  if (w != win) { if (win >= 0) capped(); win = w; wins++ }
  if (stamp != "") last = stamp
  keep = buf == "crash" || (tag != "" && tag ~ imp)
  if (tag != "" && !keep && wins > open && ++cnt[tag] > cap) { drop[tag]++; next }
  line = scrub(line)
  put(line)
  if (stream == "log" && (keep || lvl == "E" || lvl == "F")) put_imp(line)
}
END { if (win >= 0) capped() }'

# eviction order: "<bytes> <path>" of every kept file in, "<class> <start> <index> <bytes> <path>" out (class 1 goes
# first); -v cur (this start's folder) head tail. The head and tail chunks and info.txt of this start never appear.
EVICT_AWK='
{
  p = $2; k = split(p, a, "/")
  if (a[k] == "pstore" || p ~ /\/export\//) next
  if (a[k - 1] == "pstore") { b = a[k - 2]; st = "pstore"; ix = "000000" }
  else { b = a[k - 1]; st = a[k]; sub(/\..*$/, "", st); ix = a[k]; sub(/^[^.]*\./, "", ix); sub(/\..*$/, "", ix) }
  if (st == "info" || st == "budget") ix = "999999"
  N++; S[N] = $1; P[N] = p; B[N] = b; T[N] = st; I[N] = ix
  if (st == "log" || st == "imp" || st == "kernel" || st == "snap") pos[N] = ++cnt[b SUBSEP st]
}
END {
  cb = cur; sub(/^.*\//, "", cb)
  for (i = 1; i <= N; i++) {
    mid = (i in pos) && pos[i] > head && pos[i] <= cnt[B[i] SUBSEP T[i]] - tail
    old = B[i] != cb
    if (T[i] == "log" && mid) c = old ? 1 : 2
    else if (old && mid) c = 3
    else if (old) c = 4
    else if (mid) c = 5
    else continue
    printf "%d %s %s %d %s\n", c, B[i], I[i], S[i], P[i]
  }
}'

lk_num() { awk -v s="$1" 'BEGIN { sub(/^.*\//, "", s); sub(/^[a-z]*\.?/, "", s); sub(/\..*$/, "", s); print s + 0 }'; }
lk_starts() { ls -d "$LK"/b[0-9][0-9][0-9][0-9][0-9][0-9] 2> /dev/null; }
# the newest chunk of a stream in a start's folder (finished or open), empty when none
lk_last() { ls "$1/$2".[0-9]*.txt* 2> /dev/null | tail -n 1; }
lk_last_line() {
  case "$1" in '') ;; *.gz) gzip -dc "$1" 2> /dev/null | tail -n 1 ;; *) tail -n 1 "$1" ;; esac
}

lk_start() {
  mkdir -p "$LK" && chown 0:0 "$LK" && chmod 0711 "$LK" || return 1
  rm -rf "$LK/export"
  bid=$(cat "$R/proc/sys/kernel/random/boot_id" 2> /dev/null)
  D=$(lk_starts | tail -n 1)
  RESUME=0
  if [ -n "$D" ] && [ -n "$bid" ] && [ "$(sed -n 's/^boot_id=//p' "$D/info.txt" 2> /dev/null)" = "$bid" ]; then
    RESUME=1
    return 0
  fi
  prev=$D
  seq=$(cat "$LK/seq" 2> /dev/null)
  case "$seq" in ''|*[!0-9]*) seq=0 ;; esac
  [ -n "$prev" ] && [ "$(lk_num "$prev")" -gt "$seq" ] && seq=$(lk_num "$prev")
  seq=$((seq + 1))
  D=$LK/b$(printf '%06d' "$seq")
  mkdir -p "$D" && chmod 0700 "$D" || return 1
  echo "$seq" > "$LK/seq"
  {
    echo "start=$seq"
    echo "boot_id=$bid"
    echo "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "uptime_at_start=$(cut -d' ' -f1 "$R/proc/uptime" 2> /dev/null)"
    for p in ro.lumen.version ro.lumen.build_id ro.boot.xgimi.modelname ro.product.device ro.build.fingerprint \
        ro.gsid.image_running ro.boot.slot_suffix; do
      echo "$p=$(getprop "$p")"
    done
  } > "$D/info.txt"
  [ -n "$prev" ] && lk_pstore "$prev"
  # this start and the KEEP_BOOTS - 1 before it
  lk_starts | sort -r | awk -v k="$KEEP_BOOTS" 'NR > k' | while read -r old; do rm -rf "$old"; done
  return 0
}

# the kernel's own record of the previous start (pstore console and dmesg-ramoops, last_kmsg on older kernels; the
# binary pmsg-ramoops as text through logcat -L)
lk_pstore() {
  mkdir -p "$1/pstore" && chmod 0700 "$1/pstore" || return 0
  for f in "$R"/sys/fs/pstore/* "$R/proc/last_kmsg"; do
    [ -f "$f" ] && [ -s "$f" ] || continue
    case "${f##*/}" in pmsg-*) continue ;; esac
    head -c "$PSTORE_CAP" "$f" | awk "$SCRUB_AWK" > "$1/pstore/${f##*/}.txt"
  done
  if ls "$R"/sys/fs/pstore/pmsg-* > /dev/null 2>&1; then
    logcat -L -d -b all -D -v threadtime -v uid 2> /dev/null | tail -c "$PSTORE_CAP" | awk "$SCRUB_AWK" > "$1/pstore/logcat_pmsg.txt"
    [ -s "$1/pstore/logcat_pmsg.txt" ] || rm -f "$1/pstore/logcat_pmsg.txt"
  fi
  rmdir "$1/pstore" 2> /dev/null
  return 0
}

lk_budget() {
  free=$(df -k "$R/data" 2> /dev/null | awk 'NR == 2 { print $4 + 0 }')
  used=$(du -sk "$LK" 2> /dev/null | awk '{ print $1 + 0 }')
  mem=$(awk '/^MemTotal:/ { print $2 + 0 }' "$R/proc/meminfo" 2> /dev/null)
  LIVE=0
  [ "$(getprop ro.gsid.image_running)" = 1 ] && LIVE=1
  LOW=0
  [ "$(getprop ro.config.low_ram)" = true ] && LOW=1
  [ -n "$mem" ] && [ "$mem" -gt 0 ] && [ "$mem" -lt "$LOWRAM_K" ] && LOW=1
  CAP_K=$(awk -v free="${free:-0}" -v used="${used:-0}" -v live="$LIVE" -v low="$LOW" -v pct="$PCT" -v pctl="$PCT_LIVE" \
      -v max="$CAP_MAX_K" -v maxl="$CAP_LIVE_K" -v floor="$FLOOR_K" -v min="$MIN_K" 'BEGIN {
    pool = free + used; cap = pool * (live ? pctl : pct) / 100; m = live ? maxl : max
    if (low) m = m / 2
    if (cap > m) cap = m
    if (cap > pool - floor) cap = pool - floor
    if (cap < min) cap = min
    printf "%d\n", cap
  }')
  printf 'cap_kib=%s\nused_kib=%s\nfree_kib=%s\nmem_kib=%s\nlive=%s\nlow_ram=%s\nstarts=%s\n' "$CAP_K" "$used" \
    "$free" "$mem" "$LIVE" "$LOW" "$(lk_starts | wc -l | tr -d ' ')" > "$LK/budget.txt"
}

lk_evict() {
  used=$(du -sk "$LK" 2> /dev/null | awk '{ print $1 + 0 }')
  need=$(awk -v u="${used:-0}" -v c="$CAP_K" 'BEGIN { printf "%d\n", (u - c) * 1024 }')
  [ "$need" -gt 0 ] || return 0
  stat -c '%s %n' "$LK"/b[0-9]*/* "$LK"/b[0-9]*/pstore/* 2> /dev/null \
    | awk -v cur="$D" -v head="$HEAD_CHUNKS" -v tail="$TAIL_CHUNKS" "$EVICT_AWK" \
    | sort -k1,1n -k2,2 -k3,3 \
    | awk -v need="$need" '{ if (got >= need) exit; print $5; got += $4 }' \
    | while read -r f; do rm -f "$f"; done
  for b in $(lk_starts); do [ "$b" = "$D" ] || { rmdir "$b/pstore" 2> /dev/null; rmdir "$b" 2> /dev/null; }; done
  return 0
}

# finished chunks (every one but the newest of each stream of this start) into .gz: written whole and synced before
# the plain file goes, so a power cut leaves one of the two complete
lk_compress() {
  ol=$(lk_last "$D" log) oi=$(lk_last "$D" imp) ok=$(lk_last "$D" kernel) os=$(lk_last "$D" snap)
  set --
  for f in "$LK"/b[0-9]*/*.txt; do
    [ -f "$f" ] || continue
    case "$f" in "$ol"|"$oi"|"$ok"|"$os"|*/info.txt) continue ;; esac
    if nice -n 19 gzip -6 -c "$f" > "$f.gz.part" 2> /dev/null; then set -- "$@" "$f"; else rm -f "$f.gz.part"; fi
  done
  [ $# -gt 0 ] || return 0
  sync
  for f in "$@"; do mv -f "$f.gz.part" "$f.gz" && rm -f "$f"; done
  sync
}

# the open chunks to the disk: sync -d FILE (data only) where toybox has it, else everything
lk_sync() {
  if [ "$SYNCF" = 1 ]; then
    sync -d $(lk_last "$D" log) $(lk_last "$D" imp) $(lk_last "$D" kernel) 2> /dev/null
  else
    sync
  fi
}

lk_snap() {
  f=$(lk_last "$D" snap)
  case "$f" in *.gz) f= ;; esac
  if [ -z "$f" ] || [ "$(stat -c %s "$f" 2> /dev/null || echo 0)" -ge "$CHUNK_SMALL" ]; then
    i=$(lk_num "$(lk_last "$D" snap)")
    f=$D/snap.$(printf '%06d' $((i + 1))).txt
  fi
  for s in $SNAP_SERVICES; do
    {
      echo "===== $(date '+%m-%d %H:%M:%S') uptime $(cut -d' ' -f1 "$R/proc/uptime") dumpsys $s ====="
      nice -n 10 dumpsys -t 10 "$s" 2>&1 | head -c "$SNAP_CAP"
      echo
    } | awk "$SCRUB_AWK" >> "$f"
  done
  sync -d "$f" 2> /dev/null || sync
}

# boot completed: its time in this start's info, the boot reason in the previous start's (how that one ended)
lk_completed() {
  echo "boot_completed_uptime=$(cut -d' ' -f1 "$R/proc/uptime")" >> "$D/info.txt"
  p=$(lk_starts | awk -v d="$D" '$0 == d { print prev; exit } { prev = $0 }')
  [ -n "$p" ] || return 0
  echo "next_boot_reason=$(getprop sys.boot.reason)" >> "$p/info.txt"
  echo "next_boot_reason_last=$(getprop sys.boot.reason.last)" >> "$p/info.txt"
}

lk_writers() {
  nl=$(lk_last "$D" log) nk=$(lk_last "$D" kernel)
  T= A=0 O=$OPEN_MIN
  if [ "$RESUME" = 1 ]; then
    O=0
    T=$(lk_last_line "$nl" | awk '/^[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9]+ / { print $1 " " $2 }')
    A=$(lk_last_line "$nk" | awk 'match($0, /^\[ *[0-9]+\.[0-9]+\]/) { print substr($0, 2, RLENGTH - 2) + 0 }')
  fi
  W=$SCRUB_LIB$WRITER_AWK
  if [ -n "$T" ]; then logcat $LOGCAT_ARGS -T "$T" 2> /dev/null; else logcat $LOGCAT_ARGS 2> /dev/null; fi \
    | awk -v dir="$D" -v stream=log -v chunk="$CHUNK_LOG" -v ichunk="$CHUNK_SMALL" -v n0="$(lk_num "$nl")" \
        -v i0="$(lk_num "$(lk_last "$D" imp)")" -v cap="$TAG_CAP" -v open="$O" -v imp="$IMP" "$W" &
  LW=$!
  dmesg -w 2> /dev/null \
    | awk -v dir="$D" -v stream=kernel -v chunk="$CHUNK_SMALL" -v ichunk="$CHUNK_SMALL" -v n0="$(lk_num "$nk")" \
        -v i0=0 -v cap="$TAG_CAP" -v open="$O" -v after="${A:-0}" -v imp="$IMP" "$W" &
  KW=$!
}

keep() {
  if [ "$(getprop persist.lumen.logkeep)" = off ]; then
    while :; do sleep 86400; done                    # init restarts a service that ends
  fi
  umask 077
  lk_start || exit 1
  SYNCF=0
  sync -d "$D/info.txt" 2> /dev/null && SYNCF=1
  lk_budget
  lk_writers
  t=0 house=$HOUSE_S snap= rounds=${LUMEN_LOGKEEP_ROUNDS:-0} r=0 SP=
  [ "$RESUME" = 1 ] && [ "$(getprop sys.boot_completed)" = 1 ] && snap=$SNAP_FIRST_S
  while :; do
    sleep "$SYNC_S"
    lk_sync
    t=$((t + SYNC_S))
    if [ "$t" -ge "$house" ]; then
      lk_compress
      lk_budget
      lk_evict
      house=$((t + HOUSE_S))
    fi
    if [ -z "$snap" ] && [ "$(getprop sys.boot_completed)" = 1 ]; then
      lk_completed
      snap=$((t + SNAP_FIRST_S))
    fi
    if [ -n "$snap" ] && [ "$t" -ge "$snap" ] && ! { [ -n "$SP" ] && kill -0 "$SP" 2> /dev/null; }; then
      lk_snap &
      SP=$!
      if [ "$LOW" = 1 ]; then snap=$((t + SNAP_S_LOWRAM)); else snap=$((t + SNAP_S)); fi
    fi
    kill -0 "$LW" 2> /dev/null || break             # logcat ended (logd restarted): init starts us again
    r=$((r + 1))
    [ "$rounds" -gt 0 ] && [ "$r" -ge "$rounds" ] && break
  done
  kill "$LW" "$KW" 2> /dev/null
  [ -n "$SP" ] && wait "$SP" 2> /dev/null
  lk_sync
  exit 0
}

[ "${1:-}" = keep ] && keep

req=$(getprop sys.lumen.report.req)
valid "$req" || exit 0
while :; do
  serve
  next=$(getprop sys.lumen.report.req)
  [ "$next" != "$req" ] && valid "$next" || break
  req=$next
done
exit 0
