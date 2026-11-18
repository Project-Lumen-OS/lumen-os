#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# The *-cow rule of installer/lumen-install.sh, executed (not grepped) on every case of cow_cases.tsv, for both slots:
#   - cow_odd / cow_leftover / fb_allowed with the rule data of the real lib/cow_rules.conf;
#   - the Z9X block of cmd_flash (the real lines, from its cow_load to its delete loop) with a fake fastboot;
#   - the FLOW=ab-fastbootd side: the STOP of abf_fbd_check (cow_odd) and abf_cow_clear through fb_allowed;
#   - fb_allowed refuses the delete of every *-cow that is no leftover, in both flows, and of every *-cow while an
#     update is staged or merging; a missing or incomplete lib/cow_rules.conf stops (cow_load), and the rule without
#     its data deletes nothing (cow_odd names the file).
# installer_cow_rules.ps1 runs the same cases on lumen-install.ps1. The java=yes rows run on the installer that runs
# on the projector too, where it is in the tree.
#   bash tools/devices/test/installer_cow_rules.sh [path/to/lumen-install.sh]
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
GSI=$(cd "$HERE/../../.." && pwd)
SRC=${1:-$GSI/installer/lumen-install.sh}
INST=$(cd "$(dirname "$SRC")" && pwd)
CASES=$HERE/cow_cases.tsv
PASS=0 FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; }

fn() {  # the body of shell function $1 of SRC
  awk -v n="$1" 'index($0, n "() {") == 1 { f = 1 } f { print } f && /^}/ { exit }' "$SRC"
}
FUNCS=""
for f in gv_val gv_size cow_load cow_parts cow_leftover cow_odd fb_allowed abf_cow_clear cow_dm_busy cow_ota_busy cow_adb_seen; do
  b=$(fn "$f")
  [ -n "$b" ] || { echo "FAIL $f() not found in $SRC"; exit 1; }
  FUNCS="$FUNCS
$b"
done
# the Z9X block of cmd_flash: its cow_load (the first one of the file) to the end of its delete loop
Z9X=$(awk '/^  cow_load$/ && !f { f = 1 } f { print } f && /^  done$/ { exit }' "$SRC")
case "$Z9X" in *cow_partition_not_slot*removing_update_leftover*) ;; *) echo "FAIL the Z9X *-cow block of cmd_flash not found"; exit 1 ;; esac
# the STOP of abf_fbd_check when adb showed an update in use (COW_ADB) and the table has a *-cow
ABFADB=$(awk '/^  if \[ -n "\$COW_ADB" \] && \[ -n "\$\(cow_parts\)" \]; then$/ { f = 1 } f { print } f && /^  fi$/ { exit }' "$SRC")
case "$ABFADB" in *abf_fb_stop*) ;; *) echo "FAIL the COW_ADB STOP of abf_fbd_check not found"; exit 1 ;; esac

gv_of() {  # status table slot: a fastbootd 'getvar all' (lines end in CR, as on a Windows-made pipe they may)
  local st=$1 sl=$2 o p sz
  [ "$sl" = _a ] && o=_b || o=_a
  printf '(bootloader) snapshot-update-status:%s\r\n' "$st"
  printf '(bootloader) current-slot:%s\r\n' "${sl#_}"
  for p in $3; do
    p=$(printf '%s' "$p" | sed "s/{s}/$sl/g; s/{o}/$o/g")
    case "$p" in *=0) p=${p%=0}; sz=0x0 ;; *) sz=0x100000 ;; esac
    printf '(bootloader) is-logical:%s:yes\r\n(bootloader) partition-size:%s:%s\r\n' "$p" "$p" "$sz"
  done
}
sub() { printf '%s' "$1" | sed "s/{s}/$2/g; s/{o}/$3/g"; }
sorted() { tr ' ' '\n' | grep -v '^$' | LC_ALL=C sort | tr '\n' ' '; }

# a fresh shell with the installer's functions and fakes: m prints its id and values, die and fb are recorded
stubs='m() { echo "$*"; }; say() { :; }; die() { echo "DIE $*"; exit 3; }
fb() { echo "FB $*" >&2; [ "$1 $2" = "getvar all" ] && printf "%s\n" "$GV"; return 0; }
fb_do() { fb_allowed "$1" "$2" || { echo "NOTALLOWED $1 $2"; return 1; }; echo "DO $1 $2"; }
abf_fb_stop() { echo "STOP $*"; exit 4; }
state_get() { [ "$1" = slot ] && echo "$S"; }
abf_gv() { printf "%s\n" "$GV" | sed -n "s/^(bootloader) $1: *//p" | head -n 1 | tr -d " \r"; }'

while IFS='	' read -r name st table want java; do
  case "$name" in ''|'#'*) continue ;; esac
  for S in _a _b; do
    [ "$S" = _a ] && O=_b || O=_a
    GV=$(gv_of "$st" "$S" "$table")
    w=$(sub "$want" "$S" "$O")
    # 1. the decision: cow_odd, else the *-cow list
    got=$(HERE=$INST S=$S GV=$GV bash -c "$stubs
$FUNCS
cow_load
c=\$(cow_odd \"\$S\")
if [ -n \"\$c\" ]; then echo \"stop:\$c\"; else l=\$(cow_parts | tr '\n' ' '); [ -n \"\$l\" ] && echo \"clear:\$l\" || echo none; fi" 2>/dev/null)
    case "$w" in clear:*) wn="clear:$(printf '%s' "${w#clear:}" | sorted)"; gn="clear:$(printf '%s' "${got#clear:}" | sorted)" ;; *) wn=$w; gn=$got ;; esac
    [ "$gn" = "$wn" ] && ok "$name$S: decision $w" || bad "$name$S: decision: got [$got] want [$w]"
    # 2. the Z9X block of cmd_flash (P_FLOW is no FLOW: the Z9X branch of fb_allowed)
    case "$w" in
      none) zw="" ;;
      clear:*) zw=$(for p in ${w#clear:}; do echo "DO delete $p"; done | LC_ALL=C sort | tr '\n' '|') ;;
      stop:*) zw="DIE cow_partition_not_slot ${w#stop:} ${S#_}|" ;;
    esac
    got=$(HERE=$INST S=$S GV=$GV P_FLOW="" bash -c "$stubs
$FUNCS
$Z9X" 2>/dev/null | grep -E '^(DO|DIE|NOTALLOWED)' | tr '\n' '|')
    [ "$got" = "$zw" ] && ok "$name$S: Z9X block" || bad "$name$S: Z9X block: got [$got] want [$zw]"
    # 3. FLOW=ab-fastbootd: abf_fbd_check's STOP, else abf_cow_clear (each delete through fb_allowed)
    case "$w" in
      none) aw="" ;;
      clear:*) aw=$(for p in ${w#clear:}; do echo "DO delete $p"; done | LC_ALL=C sort | tr '\n' '|') ;;
      stop:*) aw="STOP ${w#stop:}|" ;;
    esac
    got=$(HERE=$INST S=$S GV=$GV P_FLOW=ab-fastbootd P_SLOTS="a b" ABF_S=$S bash -c "$stubs
$FUNCS
cow_load
c=\$(cow_odd \"\$S\")
[ -z \"\$c\" ] || abf_fb_stop \"\$c\"
abf_cow_clear" 2>/dev/null | grep -E '^(DO|STOP|NOTALLOWED)' | tr '\n' '|')
    [ "$got" = "$aw" ] && ok "$name$S: ab-fastbootd" || bad "$name$S: ab-fastbootd: got [$got] want [$aw]"
    # 4. fb_allowed, one partition at a time: the *-cow that is no leftover refused in both flows, every one while an
    # update is staged or merging, a leftover let through (the STOP of a case comes from cow_odd before any delete)
    for p in $(printf '%s\n' "$GV" | tr -d '\r' | sed -n 's/^(bootloader) partition-size:\([^:]*-cow\):.*/\1/p'); do
      case "$w" in
        clear:*) case " ${w#clear:} " in *" $p "*) exp=0 ;; *) exp=1 ;; esac ;;
        *) exp=0; { [ "$p" = "${w#stop:}" ] || [ "$st" != none ]; } && exp=1 ;;
      esac
      for flow in "" ab-fastbootd; do
        HERE=$INST S=$S GV=$GV P_FLOW=$flow P_SLOTS="a b" ABF_S=$S bash -c "$stubs
$FUNCS
cow_load
fb_allowed delete '$p'" >/dev/null 2>&1
        r=$?
        [ "$r" = "$exp" ] && ok "$name$S: fb_allowed ${flow:-z9x} delete $p -> $r" || bad "$name$S: fb_allowed ${flow:-z9x} delete $p: rc $r, want $exp"
      done
    done
  done
done < "$CASES"

# the rule data: a missing file or key stops, and without it the rule deletes nothing
E=$(mktemp -d)
mkdir -p "$E/lib"
for conf in "" "suffix=-cow
status=none" "suffix=-cow
base=
status=none"; do
  [ -z "$conf" ] && rm -f "$E/lib/cow_rules.conf" || printf '%s\n' "$conf" > "$E/lib/cow_rules.conf"
  got=$(HERE=$E S=_b GV="" bash -c "$stubs
$FUNCS
cow_load
echo loaded" 2>/dev/null)
  case "$got" in "DIE lib/cow_rules.conf has no "*) ok "cow_load stops on [$(printf '%s' "$conf" | tr '\n' ' ')]" ;; *) bad "cow_load with [$conf]: $got" ;; esac
done
rm -rf "$E"
GV=$(gv_of none _b "system{s} system{s}-cow")
got=$(S=_b GV=$GV P_FLOW="" bash -c "$stubs
$FUNCS
echo \"odd=\$(cow_odd _b)\"
fb_allowed delete system_b-cow && echo allowed" 2>/dev/null)
[ "$got" = "odd=lib/cow_rules.conf" ] && ok "without cow_load: cow_odd names the file, fb_allowed refuses" || bad "without cow_load: $got"

# what adb showed before fastbootd (cow_adb_seen): a snapshot device in /dev/block/mapper, or with a root shell the
# boot indicator or a state other than none / merge-completed in /metadata/ota
fake_adb='adbs() { case "$*" in
  "shell ls /dev/block/mapper/") printf "%s\n" "$MAPPER" ;;
  "shell id -u") echo "$UID_" ;;
  "shell ls /metadata/ota/ 2>/dev/null") printf "%s\n" "$OTA_LS" ;;
  "shell od -An -tx1 /metadata/ota/state 2>/dev/null") printf "%s\n" "$OTA_ST" ;;
  *) echo "ADB $*" >&2 ;;
esac; }'
seen() {  # mapper uid ls state -> cow_adb_seen
  MAPPER=$1 UID_=$2 OTA_LS=$3 OTA_ST=$4 bash -c "$stubs
$fake_adb
$FUNCS
cow_adb_seen" 2>/dev/null
}
L0='control
system_a
vendor_a
system_a-cow
system-verity'
for x in -base -src -cow-img -user-cow -user-cow-init; do
  got=$(seen "$L0
vendor_a$x" 2000 "" "")
  [ "$got" = "/dev/block/mapper/vendor_a$x" ] && ok "cow_adb_seen: mapper vendor_a$x" || bad "cow_adb_seen: mapper vendor_a$x: [$got]"
done
got=$(seen "/dev/block/mapper/system_b-base" 2000 "" ""); [ "$got" = /dev/block/mapper/system_b-base ] && ok "cow_adb_seen: a listing with paths" || bad "cow_adb_seen: a listing with paths: [$got]"
#   mapper  uid  ls  state  want
while IFS='|' read -r mp u ls st want; do
  got=$(seen "$(printf '%b' "$mp")" "$u" "$(printf '%b' "$ls")" "$st")
  [ "$got" = "$want" ] && ok "cow_adb_seen: uid $u, ls [$ls], state [$st] -> [$want]" || bad "cow_adb_seen: uid $u, ls [$ls], state [$st]: [$got] want [$want]"
done <<'ROWS'
control\nsystem_a\nsystem_a-cow|2000|snapshot-boot\nstate| 08 03|
control\nsystem_a\nsystem_a-cow|0||
control\nsystem_a\nsystem_a-cow|0|state|
control\nsystem_a\nsystem_a-cow|0|state| 08 00|
control\nsystem_a\nsystem_a-cow|0|state snapshots| 08 05|
control\nsystem_a\nsystem_a-cow|0|snapshot-boot\nstate| 08 05|/metadata/ota/snapshot-boot
control\nsystem_a\nsystem_a-cow|0|snapshot-boot-without-slot-switch\nstate||
control\nsystem_a\nsystem_a-cow|0|state| 08 01|/metadata/ota/state 08 01
control\nsystem_a\nsystem_a-cow|0|state| 08 02|/metadata/ota/state 08 02
control\nsystem_a\nsystem_a-cow|0|state| 08 03|/metadata/ota/state 08 03
control\nsystem_a\nsystem_a-cow|0|state| 08 04|/metadata/ota/state 08 04
control\nsystem_a\nsystem_a-cow|0|state| 08 06|/metadata/ota/state 08 06
control\nsystem_a\nsystem_a-cow|0|state| 75 6e 76|/metadata/ota/state 75 6e 76
ROWS
# with COW_ADB set (after the functions: the extract of gv_size runs on to cow_load and carries COW_ADB=""): every
# *-cow kept by the rule, and the ab-fastbootd check stops before the first write when the
# table has one (stock or Lumen OS words), never without one
GV=$(gv_of none _a "system{s} system{s}-cow")
for lum in 0 1; do
  got=$(HERE=$INST S=_a GV=$GV SEEN=/dev/block/mapper/system_a-base LUM=$lum P_FLOW=ab-fastbootd P_SLOTS="a b" ABF_S=_a bash -c "$stubs
state_get() { [ \"\$1\" = dev_lumen ] && echo \"\$LUM\"; }
$FUNCS
COW_ADB=\$SEEN
cow_load
$ABFADB
echo passed" 2>/dev/null)
  if [ "$lum" = 1 ]; then w="STOP fastboot_mode_check_failed: update_finishing (/dev/block/mapper/system_a-base): start_lumen_os_wait_10."
  else w="STOP fastboot_mode_check_failed: xgimi_update_staged_not_finished (/dev/block/mapper/system_a-base): let_projector_start_turn_automatic."; fi
  [ "$got" = "$w" ] && ok "ab-fastbootd: adb showed an update, a *-cow there (dev_lumen $lum): STOP" || bad "ab-fastbootd: adb showed an update (dev_lumen $lum): [$got] want [$w]"
done
got=$(HERE=$INST S=_a GV="$(gv_of none _a "system{s}")" SEEN=/dev/block/mapper/system_a-base P_FLOW=ab-fastbootd ABF_S=_a bash -c "$stubs
$FUNCS
COW_ADB=\$SEEN
cow_load
$ABFADB
echo passed" 2>/dev/null)
[ "$got" = passed ] && ok "ab-fastbootd: adb showed an update, no *-cow: no STOP" || bad "ab-fastbootd: adb showed an update, no *-cow: [$got]"
for flow in "" ab-fastbootd; do
  got=$(HERE=$INST S=_a GV=$GV SEEN=/metadata/ota/snapshot-boot P_FLOW=$flow P_SLOTS="a b" ABF_S=_a bash -c "$stubs
$FUNCS
COW_ADB=\$SEEN
cow_load
echo \"odd=\$(cow_odd _a)\"
fb_allowed delete system_a-cow && echo allowed" 2>/dev/null)
  [ "$got" = "odd=system_a-cow" ] && ok "COW_ADB set: cow_odd names the *-cow, fb_allowed ${flow:-z9x} refuses its delete" || bad "COW_ADB set (${flow:-z9x}): [$got]"
done

echo "installer cow rules (sh): $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
