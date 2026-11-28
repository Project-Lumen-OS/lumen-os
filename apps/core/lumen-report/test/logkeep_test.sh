#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host test of the persistent log of the last starts (image/lumen_report.sh keep, service lumen_logkeep) on a fake
# root: stub getprop / setprop / logcat / dmesg / dumpsys / df / sync / nice / chown / sleep in PATH, the real awk,
# gzip, stat, du, sort and coreutils. bash apps/core/lumen-report/test/logkeep_test.sh   (LUMEN_SH=mksh: device shell)
# Proves: one folder per start with its info (boot id), logcat and kernel chunks rotated and gzipped once finished
# (the open one stays plain), every line scrubbed, the vendor flood capped per tag after the first minutes (crash
# buffer and system tags never), the important lines in imp, dumpsys snapshots, the sync of the open chunks; a
# restart in the same start goes on there (logcat -T the last line); the newest 6 starts kept, pstore saved into the
# start it belongs to, the next boot reason in the previous start's info; the budget (10 % of free, 5 GiB, Lumen OS
# Live 5 % and 1 GiB, half on low RAM, the /data floor); eviction order (older starts' log middles first, this
# start's head and tail never); a kill -9 keeps every line written before it; a report request exports the kept
# files (hard links, 0750 folder, persist.txt) and removes them after the ack.
set -u
HERE=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT=${LUMEN_REPORT_SCRIPT:-$HERE/image/lumen_report.sh}
SH=${LUMEN_SH:-sh}
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_logkeep.XXXXXX") && [ -d "$W" ] || { echo "FAIL cannot create a temp dir" >&2; exit 2; }
: "${W:?}"
trap 'rm -rf "$W"' EXIT
fails=0 oks=0
check() { if eval "$2"; then oks=$((oks + 1)); else fails=$((fails + 1)); echo "FAIL $1"; fi; }
AT=@
MAIL="anna.k${AT}example.org"

mkdir -p "$W/bin"
cat > "$W/bin/getprop" <<'EOF'
#!/bin/sh
cat "$LUMEN_REPORT_ROOT/props/$1" 2>/dev/null
EOF
cat > "$W/bin/setprop" <<'EOF'
#!/bin/sh
R=$LUMEN_REPORT_ROOT
printf '%s' "$2" > "$R/props/$1"
case "$1 $2" in
  "sys.lumen.report.done "*" ok")
    rm -rf "$R/snapdev"; cp -Rp "$R/dev/lumen_report" "$R/snapdev"
    ls -la "$R/data/misc/lumen_logs/export" > "$R/snapex" 2>&1
    stat -c '%a' "$R/data/misc/lumen_logs/export" > "$R/snapmode" 2>&1
    printf '%s' "${2% ok}" > "$R/props/sys.lumen.report.ack" ;;
esac
EOF
cat > "$W/bin/logcat" <<'EOF'
#!/bin/sh
R=$LUMEN_REPORT_ROOT
echo "logcat $*" >> "$R/ran"
case "$*" in *" -d"*|"-d"*) cat "$R/logsrc"; exit 0 ;; esac
cat "$R/logsrc"
[ -f "$R/hold" ] && { echo $$ > "$R/logcat.pid"; exec /bin/sleep 30; }
exit 0
EOF
cat > "$W/bin/dmesg" <<'EOF'
#!/bin/sh
cat "$LUMEN_REPORT_ROOT/kmsg"
EOF
cat > "$W/bin/dumpsys" <<'EOF'
#!/bin/sh
echo "dumpsys $* ok"
echo "  mPsk password=abcd1234secret for $3"
EOF
cat > "$W/bin/df" <<'EOF'
#!/bin/sh
echo "Filesystem 1K-blocks Used Available Use% Mounted on"
echo "/dev/block/dm-9 999999999 0 $(cat "$LUMEN_REPORT_ROOT/free_k") 0% /data"
EOF
cat > "$W/bin/sync" <<'EOF'
#!/bin/sh
echo "sync $*" >> "$LUMEN_REPORT_ROOT/syncs"
EOF
cat > "$W/bin/nice" <<'EOF'
#!/bin/sh
shift 2
exec "$@"
EOF
printf '#!/bin/sh\nexit 0\n' > "$W/bin/chown"
printf '#!/bin/sh\nexec /bin/sleep 0.05\n' > "$W/bin/sleep"
chmod +x "$W/bin/"*
export PATH="$W/bin:$PATH"

# root R with boot id $1, free KiB $2
mkroot() {
  R=$W/r; mkdir -p "$R/props" "$R/proc/sys/kernel/random" "$R/data/misc" "$R/dev" "$R/sys/fs/pstore" "$R/data/system"
  echo "$1" > "$R/proc/sys/kernel/random/boot_id"
  echo "$2" > "$R/free_k"
  printf '120.50 100.00\n' > "$R/proc/uptime"
  printf 'MemTotal:        3906000 kB\n' > "$R/proc/meminfo"
  printf 1 > "$R/props/sys.boot_completed"
  printf 'reboot,test' > "$R/props/sys.boot.reason"
  printf 'org.lumen.projector 10070 0 /data/user/0/org.lumen.projector default:privapp none\n' > "$R/data/system/packages.list"
}
keep() {  # keep [ROUNDS]: one run of the keeper
  LUMEN_REPORT_ROOT=$R LUMEN_LOGKEEP_ROUNDS=${1:-0} LUMEN_LOGKEEP_SYNC_S=1 LUMEN_LOGKEEP_HOUSE_S=1 \
    LUMEN_LOGKEEP_SNAP_FIRST_S=1 LUMEN_LOGKEEP_CHUNK=65536 LUMEN_LOGKEEP_CHUNK_SMALL=16384 \
    timeout 60 "$SH" "$SCRIPT" keep > "$W/keep.out" 2>&1
  [ -f "$R/logcat.pid" ] && { kill "$(cat "$R/logcat.pid")" 2>/dev/null; rm -f "$R/logcat.pid"; }
  return 0
}
LK() { echo "$R/data/misc/lumen_logs"; }
# all lines of a stream of one start, chunks in order
stream() { for f in "$(LK)/$1/$2".*; do case "$f" in *.gz) gzip -dc "$f" ;; *.txt) cat "$f" ;; esac; done; }

# ---- a source log: minute 10:00 a flood within the open minutes, 10:04 a flood after them, a crash block, secrets
gen() {
  awk -v m="$MAIL" 'BEGIN {
    print "--------- beginning of main"
    for (i = 0; i < 700; i++) printf "11-28 10:00:%02d.%03d  1000  500  500 I Motion_det_ctl: frame %d\n", i % 60, i % 1000, i
    for (i = 0; i < 60; i++) printf "11-28 10:01:%02d.000  1000  600  600 I SomeTag: line %d\n", i, i
    printf "11-28 10:02:00.000  1010  700  700 I wpa_supplicant: password=hunter2 mail %s\n", m
    for (i = 0; i < 1000; i++) printf "11-28 10:04:%02d.%03d  1000  500  500 I Motion_det_ctl: frame %d\n", i % 60, i % 1000, i
    for (i = 0; i < 700; i++) printf "11-28 10:04:%02d.500  1000  800  800 I ActivityManager: am line %d\n", i % 60, i
    for (i = 0; i < 5; i++) printf "11-28 10:04:30.000  1000  900  900 E VendorThing: error %d\n", i
    print "--------- switch to crash"
    for (i = 0; i < 700; i++) printf "11-28 10:04:40.000 u0_a77 1200 1200 E SomeCrash: frame %d\n", i
    print "--------- switch to main"
    printf "11-28 10:05:00.000  1000  600  600 I SomeTag: the last line\n"
  }' > "$R/logsrc"
  { for i in 1 2 3; do echo "[    $i.000000] init: kernel line $i"; done; echo "[    5.000000] wlan: psk=secretpsk1"; } > "$R/kmsg"
}

# ======================================================================================= 1. first start
mkroot aaaa-1111 20971520
gen
touch "$R/hold"
keep 4
D=b000001
check "first start folder and seq" '[ -d "$(LK)/$D" ] && [ "$(cat "$(LK)/seq")" = 1 ]'
check "info has the boot id" 'grep -q "^boot_id=aaaa-1111$" "$(LK)/$D/info.txt"'
check "info has boot completed" 'grep -q "^boot_completed_uptime=120.50$" "$(LK)/$D/info.txt"'
check "LK 711, start 700" '[ "$(stat -c %a "$(LK)")" = 711 ] && [ "$(stat -c %a "$(LK)/$D")" = 700 ]'
nlog=$(ls "$(LK)/$D"/log.* | wc -l)
check "log rotated into several chunks ($nlog)" '[ "$nlog" -ge 3 ]'
check "finished chunks gzipped, the newest open" '[ "$(ls "$(LK)/$D"/log.*.txt | wc -l)" = 1 ] && [ "$(ls "$(LK)/$D"/log.*.txt.gz | wc -l)" = $((nlog - 1)) ] && [ "$(ls "$(LK)/$D"/log.* | tail -n 1 | grep -c "\.txt$")" = 1 ]'
check "no partial gz left" '! ls "$(LK)/$D"/*.part > /dev/null 2>&1'
stream "$D" log > "$W/all.log"
check "flood inside the first minutes kept whole" '[ "$(grep -c "10:00:.* Motion_det_ctl" "$W/all.log")" = 700 ]'
check "flood after them capped at 600" '[ "$(grep -c "10:04:.* I Motion_det_ctl" "$W/all.log")" = 600 ]'
check "the cap is one counted line" 'grep -q "W lumen-logkeep: Motion_det_ctl: 400 more lines in that minute left out (over 600)" "$W/all.log"'
check "system tags never capped" '[ "$(grep -c "ActivityManager: am line" "$W/all.log")" = 700 ]'
check "crash buffer never capped" '[ "$(grep -c "SomeCrash" "$W/all.log")" = 700 ]'
check "buffer dividers kept" 'grep -q "^--------- switch to crash" "$W/all.log"'
check "the last line is there" 'grep -q "the last line" "$W/all.log"'
check "scrubbed: password and e-mail" '! grep -q hunter2 "$W/all.log" && ! grep -qF "$MAIL" "$W/all.log" && grep -q "password=<secret>" "$W/all.log"'
stream "$D" imp > "$W/imp.log"
check "imp: system tags, errors, crash, the cap line" '[ "$(grep -c "am line" "$W/imp.log")" = 700 ] && grep -q "E VendorThing" "$W/imp.log" && [ "$(grep -c SomeCrash "$W/imp.log")" = 700 ] && grep -q "lumen-logkeep: Motion" "$W/imp.log"'
check "imp: no ordinary lines" '! grep -q "I Motion_det_ctl" "$W/imp.log" && ! grep -q "SomeTag" "$W/imp.log"'
stream "$D" kernel > "$W/k.log"
check "kernel log kept and scrubbed" 'grep -q "kernel line 3" "$W/k.log" && ! grep -q secretpsk1 "$W/k.log"'
check "snapshots of every service, scrubbed" '(for s in hdmi_control audio power input meminfo; do grep -q "dumpsys $s =====" "$(LK)/$D"/snap.000001.txt || exit 1; done) && ! grep -q abcd1234secret "$(LK)/$D"/snap.000001.txt'
check "open chunks synced with sync -d" 'grep -q "^sync -d .*log\.0000" "$R/syncs"'
check "budget: 10 % of free" 'c=$(sed -n "s/^cap_kib=//p" "$(LK)/budget.txt"); [ "$c" -ge 2097152 ] && [ "$c" -lt 2105000 ]'
check "logcat of every buffer with dividers" 'grep -q "^logcat -b main,system,events,crash,radio -D -v threadtime -v uid$" "$R/ran"'

# ======================================================================================= 2. restart, same start
rm -f "$R/ran" "$R/hold"
printf '11-28 10:06:00.000  1000  600  600 I SomeTag: after the restart\n' > "$R/logsrc"
: > "$R/kmsg"
keep 3
check "a restart goes on in the same start" '[ "$(ls -d "$(LK)"/b[0-9]* | wc -l)" = 1 ]'
check "logcat from the last kept line" 'grep -q -- "-T 11-28 10:05:00.000$" "$R/ran"'
check "the new line is kept" 'stream "$D" log | grep -q "after the restart"'

# ======================================================================================= 3. six more starts
for b in b2 b3 b4 b5 b6 b7; do
  # the kernel's record of b000002's hard stop, read when b000003 starts
  [ "$b" = b3 ] && echo "[ 9.9] panic: the console of a hard stop, mail $MAIL" > "$R/sys/fs/pstore/console-ramoops-0"
  echo "boot-$b" > "$R/proc/sys/kernel/random/boot_id"
  printf '11-28 11:00:00.000  1000  600  600 I SomeTag: start %s\n' "$b" > "$R/logsrc"
  keep 2
  rm -f "$R/sys/fs/pstore/console-ramoops-0"
done
check "the newest 6 starts kept" '[ "$(ls -d "$(LK)"/b[0-9]* | wc -l)" = 6 ] && [ ! -d "$(LK)/b000001" ] && [ -d "$(LK)/b000007" ] && [ "$(cat "$(LK)/seq")" = 7 ]'
check "pstore saved into the start it belongs to, scrubbed" 'grep -q "panic: the console" "$(LK)/b000002/pstore/console-ramoops-0.txt" && ! grep -qF "$MAIL" "$(LK)/b000002/pstore/console-ramoops-0.txt" && [ ! -d "$(LK)/b000003/pstore" ]'
check "the next boot reason in the previous start" 'grep -q "^next_boot_reason=reboot,test$" "$(LK)/b000006/info.txt" && ! grep -q next_boot_reason "$(LK)/b000007/info.txt"'
check "every start has its own log" 'stream b000005 log | grep -q "start b5"'

# ======================================================================================= 4. budget
budget() {  # free_k live mem_k low_ram_prop -> cap_kib
  echo "$1" > "$R/free_k"; printf '%s' "$2" > "$R/props/ro.gsid.image_running"
  printf 'MemTotal: %s kB\n' "$3" > "$R/proc/meminfo"; printf '%s' "$4" > "$R/props/ro.config.low_ram"
  keep 1
  sed -n 's/^cap_kib=//p' "$(LK)/budget.txt"
}
check "budget: at most 5 GiB" '[ "$(budget 104857600 0 3906000 false)" = 5242880 ]'
check "budget: Lumen OS Live at most 1 GiB" '[ "$(budget 31457280 1 3906000 false)" = 1048576 ]'
c=$(budget 10485760 1 3906000 false)
check "budget: Lumen OS Live 5 % ($c)" '[ "$c" -ge 524288 ] && [ "$c" -lt 530000 ]'
check "budget: low RAM halves the ceiling" '[ "$(budget 104857600 0 1900000 false)" = 2621440 ]'
check "budget: ro.config.low_ram halves it too" '[ "$(budget 104857600 0 3906000 true)" = 2621440 ]'
c=$(budget 1050000 0 3906000 false)
check "budget: /data keeps its floor ($c)" '[ "$c" = 16384 ]'

# ======================================================================================= 5. eviction order
rm -rf "$W/r"
mkroot cur-boot 1000000
L=$(LK); mkdir -p "$L"
for s in 1 2 3; do
  d=$L/b00000$s; mkdir -p "$d"
  printf 'start=%s\nboot_id=%s\n' "$s" "$([ $s = 3 ] && echo cur-boot || echo old-$s)" > "$d/info.txt"
  for i in 01 02 03 04 05 06 07 08 09 10; do head -c 1048576 /dev/urandom > "$d/log.0000$i.txt.gz"; done
  for i in 01 02; do head -c 262144 /dev/urandom > "$d/imp.0000$i.txt.gz"; done
done
echo 3 > "$L/seq"
printf '11-28 12:00:00.000  1000  600  600 I SomeTag: x\n' > "$R/logsrc"; : > "$R/kmsg"
touch "$R/hold"
keep 2
rm -f "$R/hold"
check "budget at the floor: 16 MiB" '[ "$(sed -n "s/^cap_kib=//p" "$L/budget.txt")" = 16384 ]'
check "within the budget after eviction" '[ "$(du -sk "$L" | cut -f1)" -le 16700 ]'
check "older starts lose their log middle first" '[ ! -f "$L/b000001/log.000003.txt.gz" ] && [ ! -f "$L/b000002/log.000008.txt.gz" ] && [ -f "$L/b000001/log.000001.txt.gz" ] && [ -f "$L/b000001/log.000010.txt.gz" ]'
check "then this start's log middle, oldest first" '[ ! -f "$L/b000003/log.000003.txt.gz" ] && [ -f "$L/b000003/log.000008.txt.gz" ]'
check "this start keeps its head and tail" '(for i in 01 02 09 10; do [ -f "$L/b000003/log.0000$i.txt.gz" ] || exit 1; done)'
check "older starts still there (whole starts go later)" '[ -f "$L/b000001/info.txt" ] && [ -f "$L/b000002/imp.000001.txt.gz" ]'

# ======================================================================================= 6. kill -9 keeps the lines
rm -rf "$W/r"
mkroot cut-boot 20971520
awk 'BEGIN { for (i = 1; i <= 500; i++) printf "11-28 13:00:00.%03d  1000  600  600 I SomeTag: cut line %d\n", i % 1000, i }' > "$R/logsrc"
: > "$R/kmsg"
touch "$R/hold"
LUMEN_REPORT_ROOT=$R LUMEN_LOGKEEP_SYNC_S=1 setsid "$SH" "$SCRIPT" keep > /dev/null 2>&1 &
P=$!
for i in $(seq 1 60); do grep -q "cut line 500" "$(LK)"/b000001/log.000001.txt 2>/dev/null && break; /bin/sleep 0.1; done
kill -9 -- "-$P" 2>/dev/null; kill -9 "$P" 2>/dev/null
[ -f "$R/logcat.pid" ] && kill "$(cat "$R/logcat.pid")" 2>/dev/null
/bin/sleep 0.2
check "kill -9: every line written before it is in the open chunk" '[ "$(grep -c "cut line" "$(LK)/b000001/log.000001.txt")" = 500 ]'

# ======================================================================================= 7. a report exports them
gzip -c "$(LK)/b000001/log.000001.txt" > "$(LK)/b000001/log.000000.txt.gz"
printf 'tok-1' > "$R/props/sys.lumen.report.req"
printf '11-28 13:00:00.000  1000  600  600 I SomeTag: helper line\n' > "$R/logsrc"
LUMEN_REPORT_ROOT=$R LUMEN_REPORT_ACK_STEPS=4 LUMEN_REPORT_ACK_STEPS_KEPT=4 timeout 60 "$SH" "$SCRIPT" > /dev/null 2>&1
check "the report answered ok" '[ "$(cat "$R/props/sys.lumen.report.done")" = "tok-1 ok" ]'
check "persist.txt lists the kept files with sizes" 'grep -q "^b000001.log.000000.txt.gz [0-9][0-9]*$" "$R/snapdev/persist.txt" && grep -q "^b000001.info.txt " "$R/snapdev/persist.txt" && grep -q "^budget.txt " "$R/snapdev/persist.txt"'
check "export folder 0750" '[ "$(cat "$R/snapmode")" = 750 ]'
check "finished chunks are hard links (no copy)" '[ "$(stat -c %h "$(LK)/b000001/log.000000.txt.gz")" = 1 ] && grep -q "^-rw-r----- *2 .*b000001.log.000000.txt.gz$" "$R/snapex"'
check "the export is gone after the ack" '[ ! -d "$(LK)/export" ] && [ ! -f "$R/dev/lumen_report/persist.txt" ]'
check "the kept files stay" '[ -f "$(LK)/b000001/log.000000.txt.gz" ]'

# ======================================================================================= 8. off switch
rm -rf "$W/r"
mkroot off-boot 20971520
printf off > "$R/props/persist.lumen.logkeep"
LUMEN_REPORT_ROOT=$R timeout 2 "$SH" "$SCRIPT" keep > /dev/null 2>&1
check "persist.lumen.logkeep=off keeps nothing" '[ ! -d "$(LK)" ]'

# the rc wiring
RC=$HERE/image/lumen_report.rc
check "rc: the keeper starts at post-fs-data" 'awk "/^on post-fs-data/ { f = 1; next } /^on / { f = 0 } f && /start lumen_logkeep/ { ok = 1 } END { exit !ok }" "$RC"'
check "rc: the folder 0711 root" 'grep -q "mkdir /data/misc/lumen_logs 0711 root root" "$RC"'
check "rc: a restarted (not oneshot) root service" 'awk "/^service lumen_logkeep / { f = 1; next } /^service / { f = 0 } f" "$RC" > "$W/svc"; grep -q "user root" "$W/svc" && grep -q "seclabel u:r:su:s0" "$W/svc" && ! grep -q oneshot "$W/svc"'

echo "logkeep host tests passed=$oks failed=$fails"
[ "$fails" = 0 ]
