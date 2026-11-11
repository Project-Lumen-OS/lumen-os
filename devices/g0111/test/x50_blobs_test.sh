#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Host tests of devices/g0111/image/x50_blobs.sh (the X50 image's blobs reader: the user's own codec / audio files
# and the stock eye-protection drawer nativeui from z9x_blobs<slot> at post-fs-data), of x50_nui.rc and
# x50_blobs.rc, and of the format of x50_blobs_allow.txt. POSIX sh, on the Mac, no projector:
#   sh devices/g0111/test/x50_blobs_test.sh          every case under sh, dash and ksh (the ones installed)
#   sh devices/g0111/test/x50_blobs_test.sh eye_     only the cases whose name starts with this
# Every case gets a fake root (Z9X_ROOT): system/etc/z9x/x50_blobs_allow.txt is a TEST allow-list with the real
# one's structure and made-up files (x50_fixture.py; no proprietary file is ever used), 0-byte placeholders under
# system/, the bind= targets under vendor/ and apex/, the partition as a plain 16 MiB file
# dev/block/mapper/z9x_blobs_b, and data/vendor/tmp with the native-UI fifos of an earlier boot. The stubs of
# tools/ota/test/stubs (unchanged) stand in for getprop, setprop, log, timeout, chcon and chown; mount and umount
# are this test's own (they record and drop binds; a bind over $Z9X_T/mount_fail_dst fails). Every case fails on
# any output of the script and on a run longer than 20 s (an open of a fifo without a peer would block).
# Exit status 0 only when every case passes. Never touches anything outside its temp dir.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
IMG=$G/devices/g0111/image
STUBS=$G/tools/ota/test/stubs
INST=$G/installer
ONLY=${1-}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/x50_blobs_test.XXXXXX") || exit 1
trap 'rm -rf "$WORK"' EXIT
BIN=$WORK/bin
mkdir -p "$BIN" "$WORK/files"
for f in getprop setprop log timeout chcon chown; do cp "$STUBS/$f" "$BIN/$f" && chmod 755 "$BIN/$f"; done
cat > "$BIN/mount" <<'EOF'
#!/bin/sh
# test stub: 'mount --bind SRC DST' records "<sha256 of SRC> DST" in $Z9X_T/binds (DST must exist; fails when DST
# is the path in $Z9X_T/mount_fail_dst); 'mount -t TYPE -o OPTS SRC DIR' (the DSU path's stock partitions and the
# APEX payload, which are tars here) refuses OPTS without ro, else extracts SRC into DIR and records
# "TYPE OPTS SRC DIR" in $Z9X_T/fsmounts (and fsmounts.log); every call goes to $Z9X_T/mount.log
echo "mount $*" >> "$Z9X_T/mount.log"
if [ "$1" = -t ] && [ "$3" = -o ]; then
  case ",$4," in *,ro,*) ;; *) echo "mount: $6: not read-only" >&2; exit 1 ;; esac
  [ -f "$5" ] && [ -d "$6" ] || exit 1
  [ -e "$Z9X_T/fail_$2" ] && exit 1
  tar -xf "$5" -C "$6" 2>/dev/null || exit 1
  echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts"; echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts.log"
  exit 0
fi
[ "$1" = --bind ] || exit 0
[ -e "$2" ] && [ -e "$3" ] || { echo "mount: $3: No such file or directory" >&2; exit 1; }
[ "$(cat "$Z9X_T/mount_fail_dst" 2>/dev/null)" = "$3" ] && exit 1
printf '%s %s\n' "$(sha256sum "$2" | cut -d' ' -f1)" "$3" >> "$Z9X_T/binds"
EOF
cat > "$BIN/umount" <<'EOF'
#!/bin/sh
# test stub: a stock mount of $Z9X_T/fsmounts is emptied and dropped; else drops the bind over $1 from
# $Z9X_T/binds (else 'not mounted')
echo "umount $*" >> "$Z9X_T/umount.log"
if grep -q " $1\$" "$Z9X_T/fsmounts" 2>/dev/null; then
  rm -rf "$1" && mkdir "$1"
  grep -v " $1\$" "$Z9X_T/fsmounts" > "$Z9X_T/fsmounts.tmp"; mv "$Z9X_T/fsmounts.tmp" "$Z9X_T/fsmounts"; exit 0
fi
grep -q " $1\$" "$Z9X_T/binds" 2>/dev/null || { echo "umount: $1: not mounted" >&2; exit 1; }
grep -v " $1\$" "$Z9X_T/binds" > "$Z9X_T/binds.tmp"; mv "$Z9X_T/binds.tmp" "$Z9X_T/binds"
EOF
# the DSU path's mapper for a stock partition first stage did not map (only on PATH in the case that needs it)
LPBIN=$WORK/lpbin
mkdir -p "$LPBIN"
cat > "$LPBIN/lpdump" <<'EOF'
#!/bin/sh
# test stub: 'lpdump --slot=N' prints $Z9X_T/lpdump.txt and logs its arguments
echo "lpdump $*" >> "$Z9X_T/dm.log"; cat "$Z9X_T/lpdump.txt"
EOF
cat > "$LPBIN/dmctl" <<'EOF'
#!/bin/sh
# test stub: 'dmctl create NAME -ro TARGETS' (refused without -ro) makes $Z9X_T/dm/NAME a copy of the fake
# partition $Z9X_T/lp/<NAME without lumen_ro_>; getpath prints that path; delete removes it. Logs to dm.log
echo "dmctl $*" >> "$Z9X_T/dm.log"
case "$1" in
  create) [ "$3" = -ro ] || exit 1; mkdir -p "$Z9X_T/dm"; cp "$Z9X_T/lp/${2#lumen_ro_}" "$Z9X_T/dm/$2" ;;
  getpath) [ -f "$Z9X_T/dm/$2" ] && echo "$Z9X_T/dm/$2" ;;
  delete) rm -f "$Z9X_T/dm/$2" ;;
esac
EOF
chmod 755 "$LPBIN/lpdump" "$LPBIN/dmctl"
cp "$STUBS/blockdev" "$BIN/blockdev" && chmod 755 "$BIN/blockdev"
chmod 755 "$BIN/mount" "$BIN/umount"
PASS=0; FAIL=0; CASE=; CFAIL=0; SHN=-
ALLOW=$WORK/allow.txt
python3 "$HERE/x50_fixture.py" allow "$IMG/x50_blobs_allow.txt" "$ALLOW" "$WORK/files" || exit 1
SET=$(sed -n 's/^set=//p' "$ALLOW")
NUI=bin/nativeui
LIB=lib64/xgimi.hardware.gmpf-V1-ndk.so

fail() { echo "  FAIL [$CASE/$SHN] $*"; CFAIL=1; }
# the lines of an allow-list: "<sha256> <path> <target> <eye 0|1>"
lines() { awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { t = "/system/" $2; e = 0
  for (i = 4; i <= NF; i++) { if ($i ~ /^bind=/) t = substr($i, 6); if ($i == "need=eye") e = 1 }
  print $1, $2, t, e }' "${1:-$ALLOW}"; }

begin() {  # case name [fixture options]: slot B, every placeholder and target in place, a good partition, stale fifos
  CASE=$1; CFAIL=0; shift
  T=$WORK/$SHN/$CASE; R=$T/root
  mkdir -p "$R/system/etc/z9x" "$R/dev/block/mapper" "$R/mnt" "$R/data/vendor/tmp"
  cp "$ALLOW" "$R/system/etc/z9x/x50_blobs_allow.txt"
  lines | while read -r h p t e; do
    mkdir -p "$(dirname "$R$t")"
    case "$t" in /system/*) : > "$R$t" ;; *) echo "the system's own $t" > "$R$t" ;; esac
  done
  mkfifo "$R/data/vendor/tmp/nuififo" "$R/data/vendor/tmp/retnuififo"
  echo "vendor's own" > "$R/data/vendor/tmp/bootcomplete"
  printf '%s\n' ro.boot.slot_suffix=_b > "$T/props"
  : > "$T/setprop.log"; : > "$T/logcat"; : > "$T/out"; : > "$T/binds"; : > "$T/mount.log"; : > "$T/chcon.log"
  python3 "$HERE/x50_fixture.py" tar "$WORK/files" "$ALLOW" "$R/dev/block/mapper/z9x_blobs_b" "$@"
}
mk() { python3 "$HERE/x50_fixture.py" tar "$WORK/files" "$ALLOW" "$R/dev/block/mapper/z9x_blobs_b" "$@"; }
run() {  # the reader, with a 20 s watchdog
  env Z9X_ROOT="$R" Z9X_T="$T" PATH="${XBIN:+$XBIN:}$BIN:$PATH" $SH "$IMG/x50_blobs.sh" >> "$T/out" 2>&1 &
  pid=$!; i=0
  while kill -0 $pid 2>/dev/null; do
    i=$((i + 1)); [ $i -le 200 ] || { kill $pid 2>/dev/null; fail "x50_blobs.sh still runs after 20 s"; break; }
    sleep 0.1
  done
  wait $pid 2>/dev/null
}
prop() { Z9X_T=$T "$BIN/getprop" "$1"; }
expect_prop() { [ "$(prop "$1")" = "$2" ] || fail "$1='$(prop "$1")', want '$2'"; }
bound() { grep -c . "$T/binds" | tr -d ' '; }
expect_bound() { [ "$(bound)" = "$1" ] || fail "$(bound) binds, want $1: $(cat "$T/binds")"; }
is_bound() { grep -qx "$1 $R$2" "$T/binds"; }
expect_all_bound_but() {  # every line bound over its target with its allow-listed file, except the paths given
  lines | while read -r h p t e; do
    case " $* " in *" $p "*) is_bound "$h" "$t" && echo "$p is bound"; continue ;; esac
    is_bound "$h" "$t" || echo "$p not bound over $t"
  done > "$T/miss"
  [ ! -s "$T/miss" ] || fail "$(cat "$T/miss")"
}
expect_eye_unbound() {  # neither eye-protection file bound now, and (unless told otherwise) never bound in the run
  expect_all_bound_but "$NUI" "$LIB"
  [ "${1-}" = was_bound ] && return
  grep -E -- "--bind .* $R/system/($NUI|$LIB)\$" "$T/mount.log" && fail "an eye-protection file was bound and taken off again"
}
expect_log() { grep -q "$1" "$T/logcat" || fail "logcat lacks '$1': $(cat "$T/logcat")"; }
expect_fifos_gone() {
  for f in nuififo retnuififo; do [ ! -e "$R/data/vendor/tmp/$f" ] || fail "the stale fifo $f is still there"; done
  [ -f "$R/data/vendor/tmp/bootcomplete" ] || fail "a vendor file in /data/vendor/tmp was removed"
}
end() {
  [ ! -s "$T/out" ] || fail "script output: $(cat "$T/out")"
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $SHN $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $SHN $CASE"; sed 's/^/     | /' "$T/logcat" | tail -6; fi
}
# a DSU trial on stock: no z9x_blobs partition, ro.gsid.image_running=1, this model, the fake stock slot B
# (dev/block/mapper/system_b and system_ext_b, tars here; the vendor's own files under vendor/) built with these
# x50_fixture.py stock options, and the shared reader in place as in the image
dsu_begin() {  # case name [stock fixture options]
  begin "$1"; shift; XBIN=
  rm -f "$R/dev/block/mapper/z9x_blobs_b"
  cp "$G/tools/devices/image/blobs_stock_dsu.sh" "$R/system/etc/z9x/blobs_stock_dsu.sh"
  printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0111 ro.lumen.model=G0111 > "$T/props"
  python3 "$HERE/x50_fixture.py" stock "$WORK/files" "$ALLOW" "$T/lp" "$R" "$@" || fail "stock fixture"
  mv "$T/lp/system.tar" "$T/lp/system_b"; mv "$T/lp/system_ext.tar" "$T/lp/system_ext_b"
  cp "$T/lp/system_b" "$T/lp/system_ext_b" "$R/dev/block/mapper/"
  : > "$T/fsmounts"; : > "$T/fsmounts.log"; : > "$T/blockdev.log"
  (cd "$T/lp" && sha256sum system_b system_ext_b) > "$T/stock.sha"
}
expect_stock_untouched() {  # nothing left mounted, no scratch left, the stock partitions byte-identical, all ro
  [ ! -s "$T/fsmounts" ] || fail "still mounted: $(cat "$T/fsmounts")"
  [ ! -e "$R/mnt/z9x_stock" ] || fail "the stock scratch folder is left"
  for p in system_b system_ext_b; do
    [ ! -f "$R/dev/block/mapper/$p" ] || cmp -s "$R/dev/block/mapper/$p" "$T/lp/$p" || fail "stock $p changed"
  done
  (cd "$T/lp" && sha256sum system_b system_ext_b) | cmp -s - "$T/stock.sha" || fail "a stock partition changed"
  awk '$2 !~ /(^|,)ro(,|$)/' "$T/fsmounts.log" | grep . && fail "a stock mount was not read-only"
  grep -E -- '-o (rw|remount)|remount' "$T/mount.log" | grep -v z9x_blobs && fail "a remount: $(cat "$T/mount.log")"
  return 0
}
expect_no_stock_mount() { [ ! -s "$T/fsmounts.log" ] || fail "stock mounted: $(cat "$T/fsmounts.log")"; }

want() { [ -z "$ONLY" ] || case "$1" in "$ONLY"*) return 0 ;; *) return 1 ;; esac; }

cases() {
  if want ok; then
    begin ok; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.set "$SET"; expect_prop sys.lumen.nui ok
    expect_bound 11; expect_all_bound_but
    expect_log "ok: 11 files of set $SET bound"
    grep -q "^u:object_r:nativeui_main_exec:s0 .*/x/$NUI$" "$T/chcon.log" || fail "nativeui is not nativeui_main_exec"
    grep -q "^u:object_r:system_lib_file:s0 .*/x/$LIB$" "$T/chcon.log" || fail "the gmpf ndk library is not system_lib_file"
    grep -q "^u:object_r:system_file:s0 .*audio_policy_configuration.xml$" "$T/chcon.log" || fail "the xml is not system_file"
    [ "$(ls -l "$R/mnt/z9x_blobs/x/$NUI" | cut -c1-10)" = -rwxr-xr-x ] || fail "nativeui is not 0755: $(ls -l "$R/mnt/z9x_blobs/x/$NUI")"
    [ "$(ls -l "$R/mnt/z9x_blobs/x/$LIB" | cut -c1-10)" = -rw-r--r-- ] || fail "the library is not 0644"
    [ ! -e "$R/mnt/z9x_blobs/.img" ] && [ ! -e "$R/mnt/z9x_blobs/eye.q" ] || fail "the partition copy or the queue was left"
    expect_fifos_gone; expect_log "removed the native-UI fifo nuififo left from an earlier boot"
    end
  fi
  if want once_per_boot; then
    # a second run in the same boot changes nothing: not even the fifos a running drawer made
    begin once_per_boot; run; : > "$T/binds"; mkfifo "$R/data/vendor/tmp/nuififo"; run
    expect_prop sys.lumen.nui ok; expect_bound 0
    [ -p "$R/data/vendor/tmp/nuififo" ] || fail "the second run removed the drawer's fifo"
    end
  fi
  if want missing_partition; then
    begin missing_partition; rm -f "$R/dev/block/mapper/z9x_blobs_b"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    grep -q -- --bind "$T/mount.log" && fail "mount --bind called: $(cat "$T/mount.log")"
    expect_log "missing: no z9x_blobs_b"; expect_log "eye-protection drawer nativeui not started: missing"
    expect_fifos_gone
    end
  fi
  if want missing_slot; then
    begin missing_slot; printf '%s\n' ro.boot.slot_suffix= > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0; expect_fifos_gone
    end
  fi
  if want slot_a; then
    begin slot_a; printf '%s\n' ro.boot.slot_suffix=_a > "$T/props"
    mv "$R/dev/block/mapper/z9x_blobs_b" "$R/dev/block/mapper/z9x_blobs_a"; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_bound 11
    end
  fi
  if want codec_bad_hash; then
    begin codec_bad_hash --corrupt lib64/libcodec2_soft_common.so; run
    expect_prop sys.z9x.blobs partial; expect_prop sys.lumen.nui ok; expect_bound 10
    expect_all_bound_but lib64/libcodec2_soft_common.so
    expect_log "first problem bad:lib64/libcodec2_soft_common.so"
    end
  fi
  if want eye_drawer_bad_hash; then
    begin eye_drawer_bad_hash --corrupt "$NUI"; run
    expect_prop sys.z9x.blobs partial; expect_prop sys.lumen.nui "bad:$NUI"; expect_bound 9; expect_eye_unbound
    [ ! -e "$R/mnt/z9x_blobs/x/$NUI" ] && [ ! -e "$R/mnt/z9x_blobs/x/$LIB" ] || fail "an eye-protection file was kept"
    expect_log "eye-protection drawer nativeui not started: bad:$NUI"; expect_fifos_gone
    end
  fi
  if want eye_library_bad_hash; then
    begin eye_library_bad_hash --corrupt "$LIB"; run
    expect_prop sys.lumen.nui "bad:$LIB"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want eye_library_missing; then
    begin eye_library_missing --drop "$LIB"; run
    expect_prop sys.z9x.blobs partial; expect_prop sys.lumen.nui "missing:$LIB"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want eye_drawer_missing; then
    begin eye_drawer_missing --drop "$NUI"; run
    expect_prop sys.lumen.nui "missing:$NUI"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want eye_placeholder_not_empty; then
    begin eye_placeholder_not_empty; echo "not empty" > "$R/system/$NUI"; run
    expect_prop sys.lumen.nui "noplaceholder:$NUI"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want eye_placeholder_missing; then
    begin eye_placeholder_missing; rm -f "$R/system/$LIB"; run
    expect_prop sys.lumen.nui "noplaceholder:$LIB"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want eye_second_bind_fails; then
    # the drawer is bound, its library's bind fails: the drawer comes off again
    begin eye_second_bind_fails; echo "$R/system/$LIB" > "$T/mount_fail_dst"; run
    expect_prop sys.lumen.nui "mount:$LIB"; expect_prop sys.z9x.blobs partial; expect_bound 9; expect_eye_unbound was_bound
    grep -qx "umount $R/system/$NUI" "$T/umount.log" || fail "the drawer was not unbound: $(cat "$T/umount.log" 2>/dev/null)"
    end
  fi
  if want eye_label_refused; then
    begin eye_label_refused; sed -i.bak 's/label=nativeui_main_exec/label=su_exec/' "$R/system/etc/z9x/x50_blobs_allow.txt"; run
    expect_prop sys.lumen.nui "option:$NUI"; expect_bound 9; expect_eye_unbound
    grep -q su_exec "$T/chcon.log" && fail "labelled su_exec"
    end
  fi
  if want eye_mode_refused; then
    begin eye_mode_refused; sed -i.bak 's/mode=0755/mode=4755/' "$R/system/etc/z9x/x50_blobs_allow.txt"; run
    expect_prop sys.lumen.nui "option:$NUI"; expect_bound 9; expect_eye_unbound
    end
  fi
  if want mode_without_label_refused; then
    begin mode_without_label_refused; sed -i.bak 's/ label=nativeui_main_exec//' "$R/system/etc/z9x/x50_blobs_allow.txt"; run
    expect_prop sys.lumen.nui "option:$NUI"; expect_bound 9
    end
  fi
  if want eye_bind_refused; then
    # an eye-protection line may not name a bind= target (only the 0-byte placeholder in /system)
    begin eye_bind_refused
    sed -i.bak 's#lib64/xgimi.hardware.gmpf-V1-ndk.so need=eye#& bind=/vendor/etc/audio_policy_configuration.xml#' \
      "$R/system/etc/z9x/x50_blobs_allow.txt"; run
    expect_prop sys.lumen.nui "target:$LIB"; expect_bound 9
    end
  fi
  if want eye_lines_absent; then
    # an allow-list without the need=eye lines (a broken image): the codecs are bound, no drawer, never ok
    begin eye_lines_absent; grep -v need=eye "$ALLOW" > "$R/system/etc/z9x/x50_blobs_allow.txt"; run
    expect_prop sys.lumen.nui bad:allow; expect_prop sys.z9x.blobs partial; expect_bound 9
    end
  fi
  if want big_member; then
    begin big_member --big "$NUI"; run
    expect_prop sys.z9x.blobs "bad:entry"; expect_prop sys.lumen.nui "bad:entry"; expect_bound 0
    expect_log "bad:entry: $NUI"; expect_fifos_gone
    end
  fi
  if want bad_tar; then
    begin bad_tar; head -c 16777216 /dev/urandom > "$R/dev/block/mapper/z9x_blobs_b"; run
    case "$(prop sys.lumen.nui)" in bad:tar|bad:manifest) ;; *) fail "sys.lumen.nui='$(prop sys.lumen.nui)'" ;; esac
    expect_bound 0
    end
  fi
  if want bad_symlink; then
    begin bad_symlink --symlink "$NUI"; run
    expect_prop sys.lumen.nui bad:entry; expect_bound 0
    [ ! -e "$R/mnt/z9x_blobs/x" ] || fail "extracted although a member is a symlink"
    end
  fi
  if want bad_sparse; then
    begin bad_sparse --sparse "$NUI"; run
    expect_prop sys.lumen.nui bad:entry; expect_bound 0
    end
  fi
  if want bad_dotdot; then
    begin bad_dotdot --dotdot x50_escape; run
    expect_prop sys.lumen.nui bad:path; expect_bound 0
    [ ! -e "$R/mnt/x50_escape" ] || fail "extracted although a name has '..'"
    end
  fi
  if want set_mismatch; then
    begin set_mismatch --set x50-other-a; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs.set x50-other-a; expect_bound 11
    expect_log "blob set 'x50-other-a' is not this image's '$SET'"
    end
  fi
  if want z9x_partition; then
    # a Z9X-style 4 MiB partition: what it holds is checked the same way; the eye-protection files are not there
    begin z9x_partition --drop "$NUI"; head -c 4194304 "$R/dev/block/mapper/z9x_blobs_b" > "$T/p"
    mv "$T/p" "$R/dev/block/mapper/z9x_blobs_b"; run
    expect_prop sys.lumen.nui "missing:$NUI"; expect_bound 9
    end
  fi
  if want dsu_ok; then
    # a DSU trial on stock: every file from the stock slot, checked and bound as from the partition
    dsu_begin dsu_ok; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs.src stock_b
    expect_prop sys.z9x.blobs.set "$SET"; expect_bound 11; expect_all_bound_but
    expect_log "DSU trial: no z9x_blobs_b, 11 files read-only from the stock slot _b"
    expect_log "ok: 11 files of set $SET bound"
    grep -q "^u:object_r:nativeui_main_exec:s0 .*/x/$NUI$" "$T/chcon.log" || fail "nativeui is not nativeui_main_exec"
    [ "$(ls -l "$R/mnt/z9x_blobs/x/$NUI" | cut -c1-10)" = -rwxr-xr-x ] || fail "nativeui is not 0755"
    for p in system_b system_ext_b; do grep -q -- "--setro $R/dev/block/mapper/$p\$" "$T/blockdev.log" || fail "no setro on $p"; done
    grep -q '^ext4 ro,loop,nodev,nosuid,noexec,noload .*apex_payload.img ' "$T/fsmounts.log" || fail "the APEX payload: $(cat "$T/fsmounts.log")"
    [ "$(grep -c . "$T/fsmounts.log")" = 3 ] || fail "want 3 stock mounts: $(cat "$T/fsmounts.log")"
    expect_stock_untouched; expect_fifos_gone
    end
  fi
  if want dsu_partition_wins; then
    # a DSU trial that has a z9x_blobs partition after all: the partition, the stock slot is not even mounted
    dsu_begin dsu_partition_wins; mk; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs.src partition; expect_bound 11
    expect_no_stock_mount
    end
  fi
  if want dsu_not_dsu; then
    # no DSU trial: no partition is missing as before, the stock slot is never read
    dsu_begin dsu_not_dsu; printf '%s\n' ro.boot.slot_suffix=_b ro.boot.xgimi.modelname=G0111 > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "missing: no z9x_blobs_b"; expect_no_stock_mount
    end
  fi
  if want dsu_other_model; then
    # the shared reader is on for G0111 only: another model's DSU trial reads nothing
    dsu_begin dsu_other_model; printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0082 ro.lumen.model=G0111 > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "the stock slot _b of this DSU trial was not read (model)"; expect_no_stock_mount
    end
  fi
  if want dsu_ultra_max; then
    # the X50 Ultra Max image (g0100, ro.lumen.model G0100) in a trial on a G0100: the same stock files, read the same way
    dsu_begin dsu_ultra_max; printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0100 ro.lumen.model=G0100 > "$T/props"; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs.src stock_b; expect_bound 11
    expect_log "DSU trial: no z9x_blobs_b, 11 files read-only from the stock slot _b"; expect_stock_untouched
    end
  fi
  if want dsu_ultra_max_image_on_ultra; then
    # the Ultra Max image on an X50 Ultra: not its model, nothing is read
    dsu_begin dsu_ultra_max_image_on_ultra; printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0111 ro.lumen.model=G0100 > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "the stock slot _b of this DSU trial was not read (model)"; expect_no_stock_mount
    end
  fi
  if want dsu_ultra_image_on_ultra_max; then
    # the Ultra image on an X50 Ultra Max: not its model, nothing is read
    dsu_begin dsu_ultra_image_on_ultra_max; printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0100 ro.lumen.model=G0111 > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "the stock slot _b of this DSU trial was not read (model)"; expect_no_stock_mount
    end
  fi
  if want dsu_no_image_model; then
    # an image that names no X50 model: nothing is read (fail closed)
    dsu_begin dsu_no_image_model; printf '%s\n' ro.boot.slot_suffix=_b ro.gsid.image_running=1 ro.boot.xgimi.modelname=G0111 > "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "this image names no X50 model (ro.lumen.model unset)"; expect_no_stock_mount
    end
  fi
  if want dsu_no_stock_reader; then
    # an image without the shared reader: missing, as t5
    dsu_begin dsu_no_stock_reader; rm -f "$R/system/etc/z9x/blobs_stock_dsu.sh"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0; expect_no_stock_mount
    end
  fi
  if want dsu_no_stock; then
    # the stock partitions are not there (and no lpdump / dmctl): fail closed
    dsu_begin dsu_no_stock; rm -f "$R/dev/block/mapper/system_b" "$R/dev/block/mapper/system_ext_b"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.lumen.nui missing; expect_bound 0
    expect_log "was not read (nostock)"; expect_stock_untouched
    end
  fi
  if want dsu_nativeui_missing; then
    dsu_begin dsu_nativeui_missing --drop "$NUI"; run
    expect_prop sys.lumen.nui "missing:$NUI"; expect_prop sys.z9x.blobs partial; expect_bound 9; expect_eye_unbound
    expect_stock_untouched
    end
  fi
  if want dsu_nativeui_wrong_hash; then
    dsu_begin dsu_nativeui_wrong_hash --corrupt "$NUI"; run
    expect_prop sys.lumen.nui "bad:$NUI"; expect_prop sys.z9x.blobs partial; expect_bound 9; expect_eye_unbound
    [ ! -e "$R/mnt/z9x_blobs/x/$NUI" ] && [ ! -e "$R/mnt/z9x_blobs/x/$LIB" ] || fail "an eye-protection file was kept"
    expect_log "eye-protection drawer nativeui not started: bad:$NUI"; expect_stock_untouched
    end
  fi
  if want dsu_library_wrong_hash; then
    dsu_begin dsu_library_wrong_hash --corrupt "$LIB"; run
    expect_prop sys.lumen.nui "bad:$LIB"; expect_bound 9; expect_eye_unbound; expect_stock_untouched
    end
  fi
  if want dsu_codec_wrong_hash; then
    dsu_begin dsu_codec_wrong_hash --corrupt system_ext/lib64/libc2plugin_store.so; run
    expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs partial; expect_bound 10
    expect_all_bound_but system_ext/lib64/libc2plugin_store.so; expect_stock_untouched
    end
  fi
  if want dsu_nativeui_symlink; then
    # a symlink on the stock side is never followed, even to a good copy
    dsu_begin dsu_nativeui_symlink --symlink "$NUI"; run
    expect_prop sys.lumen.nui "missing:$NUI"; expect_bound 9; expect_eye_unbound; expect_stock_untouched
    end
  fi
  if want dsu_audio_source_changed; then
    # the from= source (the stock vendor's file) is not the allow-listed one: not derived, not bound
    dsu_begin dsu_audio_source_changed; echo changed >> "$R/vendor/etc/audio_policy_configuration.xml"; run
    expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs partial; expect_bound 10
    expect_all_bound_but etc/xgimi/audio_policy_configuration.xml
    end
  fi
  if want dsu_no_apex; then
    dsu_begin dsu_no_apex --no-apex; run
    expect_prop sys.lumen.nui ok; expect_prop sys.z9x.blobs partial; expect_bound 9
    expect_all_bound_but etc/xgimi/libstagefright_foundation.so etc/xgimi/libstagefright_foundation64.so
    expect_stock_untouched
    end
  fi
  if want dsu_dmctl_map; then
    # first stage did not map the stock partitions: mapped read-only from lpdump's extents and deleted again
    dsu_begin dsu_dmctl_map; rm -f "$R/dev/block/mapper/system_b" "$R/dev/block/mapper/system_ext_b"; XBIN=$LPBIN
    printf '%s\n' 'Partition table:' '------------------------' '  Name: system_b' '  Group: main_b' '  Extents:' \
      '    0 .. 2047 linear super 2048' '    2048 .. 4095 linear super 10240' '------------------------' \
      '  Name: system_ext_b' '  Extents:' '    0 .. 1023 linear super 8192' '------------------------' > "$T/lpdump.txt"
    run; XBIN=
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_bound 11
    grep -qx "dmctl create lumen_ro_system_b -ro linear 0 2048 $R/dev/block/by-name/super 2048 linear 2048 2048 $R/dev/block/by-name/super 10240" "$T/dm.log" \
      || fail "dmctl create: $(cat "$T/dm.log")"
    grep -qx "lpdump --slot=1" "$T/dm.log" || fail "lpdump not of slot 1"
    grep -qx "dmctl delete lumen_ro_system_b" "$T/dm.log" && grep -qx "dmctl delete lumen_ro_system_ext_b" "$T/dm.log" \
      || fail "the dm devices were not deleted: $(cat "$T/dm.log")"
    [ -z "$(ls "$T/dm" 2>/dev/null)" ] || fail "dm devices left: $(ls "$T/dm")"
    expect_stock_untouched
    end
  fi
  if want only_fifos_removed; then
    # a regular file or a directory with a fifo's name is not ours to remove
    begin only_fifos_removed; rm -f "$R/data/vendor/tmp/nuififo"; echo x > "$R/data/vendor/tmp/nuififo"; run
    [ -f "$R/data/vendor/tmp/nuififo" ] || fail "a regular file named nuififo was removed"
    [ ! -e "$R/data/vendor/tmp/retnuififo" ] || fail "the fifo retnuififo is still there"
    end
  fi
}

# the files themselves: allow-list format and copies, the two rc files
static() {
  CASE=$1; SHN=-; CFAIL=0
  case "$1" in
    allowlist)
      cmp -s "$IMG/x50_blobs_allow.txt" "$INST/lib/x50/blobs_allow.txt" \
        || fail "installer/lib/x50/blobs_allow.txt differs from devices/g0111/image/x50_blobs_allow.txt"
      # the nine codec / audio lines are the Z9X set's, byte for byte (the X50 stock copies have the same sha256)
      grep -E '^[0-9a-f]{64} ' "$G/tools/ota/image/blobs_allow.txt" > "$WORK/z9x.lines"
      grep -E '^[0-9a-f]{64} ' "$IMG/x50_blobs_allow.txt" | grep -v ' need=eye' > "$WORK/x50.lines"
      cmp -s "$WORK/z9x.lines" "$WORK/x50.lines" || fail "the codec / audio lines differ from tools/ota/image/blobs_allow.txt"
      python3 - "$IMG/x50_blobs_allow.txt" <<'PY' || fail "format"
import re, sys
n = {"place": 0, "bind": 0, "eye": 0}; sets = []
for line in open(sys.argv[1]):
    w = line.split()
    if not w or w[0].startswith("#"):
        continue
    if w[0].startswith("set="):
        sets.append(w[0]); continue
    assert re.fullmatch(r"[0-9a-f]{64}", w[0]) and len(w) >= 3, line
    o = dict(x.split("=", 1) for x in w[3:])
    assert set(o) <= {"bind", "from", "label", "mode", "need"}, line
    if "bind" in o:
        assert re.fullmatch(r"/(vendor/etc|apex/com\.android\.vndk\.v\d+/lib(64)?)/[^/]+", o["bind"]), line
        assert "need" not in o and "label" not in o and "mode" not in o, line
        n["bind"] += 1
    else:
        n["place"] += 1
    if "from" in o:
        assert re.fullmatch(r"[0-9a-f]{64}:\d+(,\d+)?d(;\d+(,\d+)?d)*", o["from"]), line
    if "need" in o:
        assert o["need"] == "eye" and w[2] == "/system/" + w[1], line
        n["eye"] += 1
    if "label" in o:
        assert o["label"] == "nativeui_main_exec" and o.get("mode") == "0755" and w[1] == "bin/nativeui", line
assert sets == ["set=x50-v1264-a"] and n == {"place": 8, "bind": 3, "eye": 2}, (sets, n)
PY
      ;;
    rc)
      # stock nativeui.rc: service nativeui /system/bin/nativeui, class main, oneshot (root, label by the file);
      # here the same plus 'disabled', started with class main only on sys.lumen.nui=ok
      awk '/^service nativeui /,/^$/' "$IMG/x50_nui.rc" > "$WORK/svc"
      printf '%s\n' "service nativeui /system/bin/nativeui" "    class main" "    oneshot" "    disabled" "" > "$WORK/svc.want"
      cmp -s "$WORK/svc" "$WORK/svc.want" || fail "x50_nui.rc service: $(cat "$WORK/svc")"
      [ "$(grep -c '^ *start nativeui$' "$IMG/x50_nui.rc")" = 1 ] || fail "nativeui is started more than once"
      grep -B1 '^ *start nativeui$' "$IMG/x50_nui.rc" | head -n 1 | grep -qx 'on nonencrypted && property:sys.lumen.nui=ok' \
        || fail "nativeui is not started by 'on nonencrypted && property:sys.lumen.nui=ok'"
      awk '/^on property:init.svc.nativeui=stopped$/,/^$/' "$IMG/x50_nui.rc" > "$WORK/stop"
      for l in "setprop sys.lumen.nui down" "rm /data/vendor/tmp/nuififo" "rm /data/vendor/tmp/retnuififo"; do
        grep -qx "    $l" "$WORK/stop" || fail "the stopped trigger lacks '$l'"
      done
      grep -E '^ *(user|group|seclabel|capabilities|exec|setenv) ' "$IMG/x50_nui.rc" && fail "x50_nui.rc changes the stock user, label or environment"
      [ "$(grep -v '^#' "$IMG/x50_blobs.rc" | grep .)" = "$(printf '%s\n' 'on post-fs-data' '    exec u:r:su:s0 root root -- /system/bin/sh /system/etc/z9x/x50_blobs.sh')" ] \
        || fail "x50_blobs.rc is not only the post-fs-data exec of x50_blobs.sh"
      # nothing but rm and the reader names the fifo; no file of ours opens it
      grep -nE 'nuififo' "$IMG/x50_blobs.sh" | grep -vE 'rm -f|for f in nuififo|^[0-9]+:#|say "removed' && fail "x50_blobs.sh uses the fifo other than to remove it"
      grep -nE '(<|>)[^&]*nuififo|exec [0-9]' "$IMG/x50_blobs.sh" "$IMG/x50_nui.rc" && fail "a redirection to the fifo"
      # verify_tree_x50.py lets only these two /system/etc files name the fifo
      grep -l nuififo "$IMG/x50_blobs_allow.txt" "$IMG/x50_blobs.rc" "$IMG/x50_netadb.rc" "$G/tools/devices/image/blobs_stock_dsu.sh" \
        && fail "another staged file names the fifo"
      # the shared DSU reader is on for the X50 only, and only the X50 reader sources it (the Z9X / HB readers do not)
      [ "$(sed -n 's/^DSU_STOCK_MODELS=//p' "$G/tools/devices/image/blobs_stock_dsu.sh")" = '"G0111 G0100"' ] \
        || fail "DSU_STOCK_MODELS is not exactly the X50 Ultra and Ultra Max (G0111 G0100)"
      grep -l blobs_stock_dsu "$G/tools/ota/image/z9x_blobs.sh" "$G"/devices/goat_elegant/image/* 2>/dev/null && fail "a Z9X / HB reader sources the DSU reader"
      ;;
  esac
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   - $1"; else FAIL=$((FAIL + 1)); echo "FAIL - $1"; fi
}
for s in allowlist rc; do { [ -z "$ONLY" ] || [ "$ONLY" = "$s" ]; } && static "$s"; done

for SH in sh dash ksh; do
  command -v $SH >/dev/null 2>&1 || continue
  SHN=$SH
  cases
done
echo "x50_blobs: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
