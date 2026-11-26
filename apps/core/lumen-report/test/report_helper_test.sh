#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host test of the problem report log helper (image/lumen_report.sh, image/lumen_report.rc) on a fake root: stub
# getprop / setprop / logcat / dmesg / dumpsys / chown / sleep in PATH, the real sed, tail, head, chmod and mv.
# bash apps/core/lumen-report/test/report_helper_test.sh   (LUMEN_SH=mksh: the device shell; default sh)
# Proves: a valid token gets every file (owner group = the report app's uid from packages.list, folder 0750, files
# 0640, the token file), the answer ok, then the files go after the app's ack or after the wait; bad tokens get no
# answer and run nothing; busy inside the floor; refused when reports are off; none without a report app; the size
# caps keep the newest lines; the newest request is served before the script exits; the scrub removes e-mail and MAC
# addresses, Wi-Fi names, passwords, keys, tokens, authorization headers, secret URL parameters and account names and
# keeps HIDL versions and ordinary lines.
set -u
HERE=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT=${LUMEN_REPORT_SCRIPT:-$HERE/image/lumen_report.sh}
RC=$HERE/image/lumen_report.rc
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_report_helper.XXXXXX") && [ -d "$W" ] || { echo "FAIL cannot create a temp dir under ${TMPDIR:-/tmp}" >&2; exit 2; }
trap 'rm -rf "$W"' EXIT
fails=0 oks=0
check() { if eval "$2"; then oks=$((oks + 1)); else fails=$((fails + 1)); echo "FAIL $1"; fi; }
mode() { stat -c %a "$1" 2>/dev/null || stat -f %Lp "$1"; }

# personal values are built at run time: the publish check takes any literal address in the tree for personal data
AT=@
MAIL="anna.k${AT}example.org"
MAC6="1c:2b:3a:4d:5e"; MAC6="$MAC6:6f"
SSID="<name> Home 5G"

mkdir -p "$W/bin"
cat > "$W/bin/getprop" <<'EOF'
#!/bin/sh
cat "$LUMEN_REPORT_ROOT/props/$1" 2>/dev/null
EOF
# setprop: the answer is where the app would read the files: on "<t> ok" the stub copies the folder (what the app
# reads) and then acts as told by the file on_ok: ack (the app acknowledges), new:<token> (a newer request arrives
# 20 s later) or nothing (no ack)
cat > "$W/bin/setprop" <<'EOF'
#!/bin/sh
R=$LUMEN_REPORT_ROOT
printf '%s' "$2" > "$R/props/$1"
[ "$1" = sys.lumen.report.done ] || exit 0
echo "$2" >> "$R/answers"
case "$2" in
  *" ok")
    n=$(wc -l < "$R/answers" | tr -d ' ')
    rm -rf "$R/snap$n"; cp -Rp "$R/dev/lumen_report" "$R/snap$n"
    act=$(cat "$R/on_ok" 2>/dev/null)
    case "$act" in
      ack) printf '%s' "${2% ok}" > "$R/props/sys.lumen.report.ack" ;;
      new:*) printf '%s' "${act#new:}" > "$R/props/sys.lumen.report.req"; echo none > "$R/on_ok"
             printf '9050.00 100.00\n' > "$R/proc/uptime" ;;
    esac ;;
esac
EOF
cat > "$W/bin/logcat" <<'EOF'
#!/bin/sh
echo "logcat $*" >> "$LUMEN_REPORT_ROOT/ran"
case "$*" in
  *"-b crash"*) cat "$LUMEN_REPORT_ROOT/crash" ;;
  *) cat "$LUMEN_REPORT_ROOT/log" ;;
esac
EOF
cat > "$W/bin/dmesg" <<'EOF'
#!/bin/sh
echo "dmesg" >> "$LUMEN_REPORT_ROOT/ran"
cat "$LUMEN_REPORT_ROOT/kmsg"
EOF
cat > "$W/bin/dumpsys" <<'EOF'
#!/bin/sh
echo "dumpsys $*" >> "$LUMEN_REPORT_ROOT/ran"
eval s=\${$#}
cat "$LUMEN_REPORT_ROOT/dump_$s" 2>/dev/null || echo "Can't find service: $s"
EOF
cat > "$W/bin/chown" <<'EOF'
#!/bin/sh
echo "$1 ${2#$LUMEN_REPORT_ROOT}" >> "$LUMEN_REPORT_ROOT/chown"
EOF
printf '#!/bin/sh\n:\n' > "$W/bin/sleep"
chmod +x "$W/bin/"*

setup() {   # setup NAME [PACKAGES_LIST_LINES]
  R="$W/$1"
  mkdir -p "$R/props" "$R/proc" "$R/dev" "$R/data/system"
  printf '%s\n' "${2:-org.lumen.projector 10057 0 /data/user/0/org.lumen.projector platform:privapp:targetSdkVersion=34 3003}" \
    "com.example.player 10101 0 /data/user/0/com.example.player default:targetSdkVersion=33 3003" > "$R/data/system/packages.list"
  printf '9001.50 100.00\n' > "$R/proc/uptime"
  printf 'https://stats.lumen-os.net/lumen/v1/report' > "$R/props/ro.lumen.report.url"
  echo ack > "$R/on_ok"
  : > "$R/answers"
  {
    echo "--------- beginning of main"
    echo "11-26 10:00:00.001  1000  1200  1210 I HdmiControlService: <Report Physical Address> 2.0.0.0"
    echo "11-26 10:00:00.002  1000  1200  1211 I WifiService: connect SSID=\"$SSID\" BSSID=$MAC6 psk=\"hunter2-secret\""
    echo "11-26 10:00:00.003  1010  1300  1301 D wpa_supplicant: wlan0: Trying to associate with SSID '$SSID'"
    echo "11-26 10:00:00.004  1010  1300  1302 D wpa_supplicant: CTRL-EVENT-CONNECTED [id=0 id_str=%7B%22configKey%22%3A%22MyNet]"
    echo "11-26 10:00:00.005  1000  1200  1212 I WifiConfigManager: \"$SSID\"WPA_PSK saved, network key \"$SSID\"wpa2-psk"
    echo "11-26 10:00:00.006 10057  2000  2001 I LumenReport: account Account {name=$MAIL, type=com.google}"
    echo "11-26 10:00:00.007 10101  2100  2101 D OkHttp: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"
    echo "11-26 10:00:00.008 10101  2100  2102 D OkHttp: GET https://api.example.net/v1/feed?user=1&access_token=AbC123xyz&sig=Zx9q77&page=2"
    echo "11-26 10:00:00.009  1041  1400  1401 I audioserver: loaded android.hardware.audio@7.1::IDevicesFactory and vendor.foo@1.0-service.so"
    echo "11-26 10:00:00.010  1000  1200  1213 W PowerManagerService: Password=Qwerty123 api_key: k-777 session_id=s-999 cookie=c-1"
    echo "--------- switch to events"
    echo "11-26 10:00:00.011  1000  1200  1214 I am_proc_start: [0,2000,10057,org.lumen.projector,service,{org.lumen.projector/x}]"
  } > "$R/log"
  echo "11-26 10:00:01.000 10057  2000  2005 E AndroidRuntime: FATAL EXCEPTION: main (mail $MAIL)" > "$R/crash"
  printf '[  12.000000] wlan: connected to ssid=%s bssid %s\n[  13.000000] usb 1-1: new device\n' "$SSID" "$MAC6" > "$R/kmsg"
  echo "HDMI CEC: device TV, logical address 0, osd name \"TV\"" > "$R/dump_hdmi_control"
  echo "Audio: STREAM_MUSIC index 5, bluetooth device $MAC6" > "$R/dump_audio"
  printf 'Wake Locks: size=1\n  PARTIAL_WAKE_LOCK "*sync*/com.google/%s" ACQ=-1s\n' "$MAIL" > "$R/dump_power"
}
SH=${LUMEN_SH:-sh}
run() { LUMEN_REPORT_ROOT="$R" LUMEN_REPORT_ACK_STEPS=5 PATH="$W/bin:$PATH" "$SH" "$SCRIPT"; }
ask() { printf '%s' "$1" > "$R/props/sys.lumen.report.req"; run; }
done_() { cat "$R/props/sys.lumen.report.done" 2>/dev/null; }
O() { echo "$R/dev/lumen_report"; }

# ---------------------------------------------------------------- a valid request
setup ok
ask k3x9-1
S="$R/snap1"
check "answered ok" '[ "$(done_)" = "k3x9-1 ok" ]'
allfiles() { for f in token logcat.txt crash.txt kernel.txt hdmi_control.txt audio.txt power.txt; do [ -f "$1/$f" ] || return 1; done; }
check "every file was there for the app" 'allfiles "$S"'
check "token file = the token" '[ "$(cat "$S/token")" = k3x9-1 ]'
check "files 0640, folder 0750" '[ "$(mode "$S/logcat.txt")" = 640 ] && [ "$(mode "$S/token")" = 640 ] && [ "$(mode "$S/power.txt")" = 640 ] && [ "$(mode "$S")" = 750 ]'
check "group = the report app uid (packages.list), owner root" 'grep -qx "0:10057 /dev/lumen_report" "$R/chown" && grep -qx "0:10057 /dev/lumen_report/logcat.txt.new" "$R/chown" && ! grep -v "^0:10057 " "$R/chown" | grep -q .'
check "logcat: main, system and events with dividers and uid; crash separately; dumpsys bounded" 'grep -qx "logcat -d -D -b main,system,events -v threadtime -v uid" "$R/ran" && grep -qx "logcat -d -b crash -v threadtime -v uid" "$R/ran" && grep -qx "dumpsys -t 5 hdmi_control" "$R/ran" && grep -qx "dumpsys -t 5 audio" "$R/ran" && grep -qx "dumpsys -t 5 power" "$R/ran" && grep -qx dmesg "$R/ran"'
check "the files went after the ack" '[ -z "$(ls "$(O)")" ]'
check "no raw copy while the app reads" '[ ! -e "$S/.raw" ] && [ ! -e "$S/.scrubbed" ]'
ALL=$(cat "$S/logcat.txt" "$S/crash.txt" "$S/kernel.txt" "$S/hdmi_control.txt" "$S/audio.txt" "$S/power.txt")
check "no e-mail address left" '! printf "%s" "$ALL" | grep -q "$MAIL"'
check "no MAC address left" '! printf "%s" "$ALL" | grep -q "$MAC6"'
check "no Wi-Fi name left (keys, quoted, config keys, kernel)" '! printf "%s" "$ALL" | grep -q "<name>"'
check "id_str gone" '! printf "%s" "$ALL" | grep -q "configKey"'
check "no password, psk, api key, session id, cookie" '! printf "%s" "$ALL" | grep -qE "hunter2|Qwerty123|k-777|s-999|c-1"'
check "no bearer token, no URL token" '! printf "%s" "$ALL" | grep -qE "eyJhbGci|AbC123xyz|Zx9q77"'
check "markers in place" 'grep -q "SSID=<ssid>" "$S/logcat.txt" && grep -q "psk=<secret>" "$S/logcat.txt" && grep -q "Authorization: <secret>" "$S/logcat.txt" && grep -q "access_token=<secret>&sig=<secret>&page=2" "$S/logcat.txt" && grep -q "Account {name=<account>" "$S/logcat.txt" && grep -q "\"<ssid>\"WPA_PSK" "$S/logcat.txt"'
check "HIDL versions and ordinary lines kept" 'grep -q "android.hardware.audio@7.1::IDevicesFactory and vendor.foo@1.0-service.so" "$S/logcat.txt" && grep -q "<Report Physical Address> 2.0.0.0" "$S/logcat.txt" && grep -qx -- "--------- switch to events" "$S/logcat.txt" && grep -q "usb 1-1: new device" "$S/kernel.txt" && grep -q "osd name \"TV\"" "$S/hdmi_control.txt"'
check "dumpsys summaries kept" 'grep -q "STREAM_MUSIC" "$S/audio.txt" && grep -q "Wake Locks" "$S/power.txt"'

# ---------------------------------------------------------------- no ack: the files still go
setup noack
echo none > "$R/on_ok"
ask n0ack-1
check "no ack: answered ok, the files went after the wait" '[ "$(done_)" = "n0ack-1 ok" ] && [ -f "$R/snap1/logcat.txt" ] && [ -z "$(ls "$(O)")" ]'

# ---------------------------------------------------------------- bad tokens
setup bad
for t in "" "ABC" "a b" 'a;rm' 'x$(id)' "../x" "abcdefghijklmnopqrstuvwxy" "k_1"; do
  ask "$t"
done
check "bad tokens: no answer" '[ ! -s "$R/answers" ] && [ -z "$(done_)" ]'
check "bad tokens: nothing ran, nothing written" '[ ! -f "$R/ran" ] && [ ! -d "$(O)" ] && [ ! -f "$R/chown" ]'
ask "abcdefghijklmnopqrstuvwx"
check "24 characters are fine" '[ "$(done_)" = "abcdefghijklmnopqrstuvwx ok" ]'

# ---------------------------------------------------------------- the floor between two collections
setup busy
ask b-1
: > "$R/ran"
printf '9010.00 100.00\n' > "$R/proc/uptime"
ask b-2
check "8.5 s later: busy, nothing collected" '[ "$(done_)" = "b-2 busy" ] && [ ! -s "$R/ran" ]'
printf '9011.60 100.00\n' > "$R/proc/uptime"
ask b-3
check "10.1 s later: ok" '[ "$(done_)" = "b-3 ok" ]'

# ---------------------------------------------------------------- refusals
setup off
printf off > "$R/props/ro.lumen.report.url"
ask o-1
check "reports off: refused, nothing collected" '[ "$(done_)" = "o-1 refused" ] && [ ! -f "$R/ran" ]'
setup nohost "com.example.other 10200 0 /data/user/0/com.example.other default 3003"
ask h-1
check "no report app: none, nothing collected" '[ "$(done_)" = "h-1 none" ] && [ ! -f "$R/ran" ]'
setup x50 "org.lumen.x50.projector 1000 0 /data/user/0/org.lumen.x50.projector platform:privapp:targetSdkVersion=34 3003"
ask x-1
check "X50 app (system uid): group 1000" '[ "$(done_)" = "x-1 ok" ] && grep -qx "0:1000 /dev/lumen_report/token.new" "$R/chown"'
setup lookalike "org.lumen.projectorX 10300 0 /data/user/0/x default 3003"
printf 'orgXlumen.projector 10301 0 /data/user/0/y default 3003\n' >> "$R/data/system/packages.list"
ask l-1
check "look-alike package names are not the report app" '[ "$(done_)" = "l-1 none" ]'

# ---------------------------------------------------------------- size caps keep the newest lines
setup cap
awk 'BEGIN { for (i = 0; i < 90000; i++) printf "11-26 10:00:00.%03d  1000  1200  1210 I Filler: line %06d padding padding padding\n", i % 1000, i }' > "$R/log"
echo "11-26 10:59:59.999  1000  1200  1210 I Last: the newest line" >> "$R/log"
awk 'BEGIN { for (i = 0; i < 40000; i++) printf "Wake lock line %06d padding padding padding padding\n", i }' > "$R/dump_audio"
ask c-1
check "logcat capped at 6 MiB, the newest line kept" '[ "$(wc -c < "$R/snap1/logcat.txt")" -le 6291456 ] && tail -n 1 "$R/snap1/logcat.txt" | grep -q "the newest line" && [ "$(wc -c < "$R/log")" -gt 6291456 ]'
check "dumpsys capped at 256 KiB from the top" '[ "$(wc -c < "$R/snap1/audio.txt")" -le 262144 ] && head -n 1 "$R/snap1/audio.txt" | grep -q "line 000000"'

# ---------------------------------------------------------------- a newer request while the files wait
setup newer
echo "new:n-2" > "$R/on_ok"
ask n-1
check "the newer token was served before the script ended" '[ "$(sed -n 1p "$R/answers")" = "n-1 ok" ] && [ "$(sed -n 2p "$R/answers")" = "n-2 ok" ] && [ "$(cat "$R/snap2/token")" = n-2 ]'

# ---------------------------------------------------------------- non-ASCII lines and a failing scrub
# toybox sed and grep crash on such lines on Android 14: the helper scrubs with awk; the test runs the device's awk
# family (one-true-awk on macOS and Android, mawk or gawk on the build hosts)
setup utf8
printf '11-26 10:00:00.020  1000  1200  1215 I Telecom: PhoneAccount registered TSI.rPA(cap)@AAw\360\237\224\222 owner %s\n' "$MAIL" >> "$R/log"
printf '11-26 10:00:00.021  1000  1200  1216 I WifiService: \320\241\320\265\321\202\321\214 SSID="%s" psk=\344\270\255\346\226\207secret\n' "$SSID" >> "$R/log"
echo "11-26 10:59:59.999  1000  1200  1210 I Last: the newest line" >> "$R/log"
ask u-1
check "non-ASCII lines: the log is whole and scrubbed, the text kept" '[ "$(done_)" = "u-1 ok" ] && tail -n 1 "$R/snap1/logcat.txt" | grep -q "the newest line" && grep -qF "TSI.rPA(cap)@AAw$(printf "\360\237\224\222") owner <email>" "$R/snap1/logcat.txt" && grep -qF "$(printf "\320\241\320\265\321\202\321\214") SSID=<ssid> psk=<secret>" "$R/snap1/logcat.txt"'
check "non-ASCII lines: no personal value got through" '! cat "$R/snap1/"*.txt | grep -qE "$MAIL|<name>|hunter2|$MAC6"'
mkdir -p "$W/deadawk"
printf '#!/bin/sh\ncat > /dev/null\nexit 139\n' > "$W/deadawk/awk"
chmod +x "$W/deadawk/awk"
setup dies
printf '%s' d-1 > "$R/props/sys.lumen.report.req"
LUMEN_REPORT_ROOT="$R" LUMEN_REPORT_ACK_STEPS=5 PATH="$W/deadawk:$W/bin:$PATH" "$SH" "$SCRIPT"
check "a failing scrub: every file is the one line saying so, nothing unscrubbed" '[ "$(done_)" = "d-1 ok" ] && [ "$(cat "$R/snap1/logcat.txt")" = "(left out: the text could not be scrubbed)" ] && [ "$(cat "$R/snap1/power.txt")" = "(left out: the text could not be scrubbed)" ] && ! cat "$R/snap1/"*.txt | grep -qE "$MAIL|<name>|hunter2|$MAC6"'
check "no raw copy left behind" '[ ! -e "$R/dev/lumen_report/.raw" ] && [ ! -e "$R/dev/lumen_report/.scrubbed" ] && [ ! -e "$R/snap1/.raw" ]'
check "the floor file is root only" '[ "$(mode "$R/dev/lumen_report/.last")" = 600 ]'
check "the scrub is awk: no sed or grep reads the logs" 'grep -qxF "scrub() { awk \"\$SCRUB_AWK\"; }" "$SCRIPT" && ! grep -qE "sed -E|grep -E" "$SCRIPT"'

# ---------------------------------------------------------------- the init file
check "rc: the framework's CEC lines on before system_server starts (log.tag.HDMI D in early-init, once)" 'awk "/^on /{t=\$0} /^    setprop log.tag.HDMI D\$/{print t}" "$RC" | grep -qx "on early-init" && [ "$(grep -v "^#" "$RC" | grep -c "log.tag.HDMI")" = 1 ]'
check "rc: root oneshot in su, started only after boot by the request token" 'grep -qx "service lumen_report /system/bin/sh /system/etc/lumen/lumen_report.sh" "$RC" && grep -qx "    seclabel u:r:su:s0" "$RC" && grep -qx "    oneshot" "$RC" && grep -qx "    disabled" "$RC" && grep -qx "on property:sys.lumen.report.req=\* && property:sys.boot_completed=1" "$RC" && grep -qx "    mkdir /dev/lumen_report 0750 root system" "$RC"'
check "script parses as $SH" '"$SH" -n "$SCRIPT"'

echo "report helper host test: $oks ok, $fails failed"
[ "$fails" -eq 0 ]
