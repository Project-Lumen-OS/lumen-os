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
FILES="token logcat.txt crash.txt kernel.txt hdmi_control.txt audio.txt power.txt"

# The scrub, one awk program (no single quote inside: \047). Keys match case-insensitively on tolower(line), which
# keeps every byte offset; a rule runs only when its cheap index() test finds its key on the line.
SCRUB_AWK='
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
BEGIN {
  SEP = "[\"\047]?[ \t]*[:=][ \t]*"
  VAL = "(\"[^\"]*\"|\047[^\047]*\047|[^ \t,;&}\"\047)]+)"
  H = "[0-9a-f][0-9a-f]"
  MAC = H ":" H ":" H ":" H ":" H ":" H
  KEYS = "(password|passwd|passphrase|psk|pre_?shared_?key|wep_?key[0-9]?|auth_?token|access_?token|refresh_?token|id_?token|api_?key|client_?secret|secret|session_?id|cookie|authorization)"
  QKEYS = "(token|access_token|id_token|auth|key|api_key|apikey|sig|signature|password|pass|pwd|session|sid|secret|code|ticket)"
  WIFI = "\"[^\"]+\"-?(wpa|wep|sae|owe|wapi|ieee8021x|none|open|suite_b|dpp|osen|passpoint)"
}
{
  s = $0; lc = tolower(s)
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
  print s
}'
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
  printf '%s\n' "$req" > "$OUT/token.new"
  if ! put token; then
    clear_out
    setprop sys.lumen.report.done "$req none"
    return
  fi
  setprop sys.lumen.report.done "$req ok"

  # the app reads the files, then acknowledges; they never stay longer than ACK_STEPS
  n=0
  while [ "$n" -lt "$ACK_STEPS" ]; do
    [ "$(getprop sys.lumen.report.ack)" = "$req" ] && break
    [ "$(getprop sys.lumen.report.req)" = "$req" ] || break
    sleep 0.25
    n=$((n + 1))
  done
  clear_out
}

req=$(getprop sys.lumen.report.req)
valid "$req" || exit 0
while :; do
  serve
  next=$(getprop sys.lumen.report.req)
  [ "$next" != "$req" ] && valid "$next" || break
  req=$next
done
exit 0
