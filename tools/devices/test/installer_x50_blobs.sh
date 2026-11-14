#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Mock test of the installer's blobs step (installer/lumen-install.sh abf_blobs, FLOW=ab-fastbootd) for a profile
# whose allow-list has need=eye lines and whose z9x_blobs partition is not 4 MiB: the X50 Ultra one
# (installer/lib/x50/blobs_allow.txt, devices/g0111). On the Mac or Linux, no projector, no network:
#   bash tools/devices/test/installer_x50_blobs.sh
# installer/lib/devices.conf has the X50 profile 'x50' (its identity is devices/g0111/device.conf's: model, hardware,
# the fastbootd product of its family, firmware; BLOBS_SIZE 16 MiB); each case runs a copy of the installer with the
# real lib/devices.conf, and the BLOBS_SIZE cases change only that one line of the copy. Its allow-list is
# a TEST one made by devices/g0111/test/x50_fixture.py from the real one (its structure, made-up files; the fake
# drawer is 5 MiB, so a 4 MiB partition is too small; no proprietary file is ever used). The projector is the
# fake network adb of tools/devices/test/installer_hb.sh, reduced to what 'blobs' asks ('adb pull' reads $DEV).
# Cases: the 16 MiB tar from a stock projector and its round trip through the real devices/g0111/image/x50_blobs.sh
# (sys.z9x.blobs=ok, sys.lumen.nui=ok, every file bound, the drawer nativeui_main_exec); the drawer or its library
# wrong or missing, a codec file wrong: STOP with the eye-protection text, no tar, never the --no-blobs advice;
# --no-blobs and --blobs=embedded refused before adb is used; BLOBS_SIZE not whole MiB, too small, too large;
# the tar too big for a 4 MiB BLOBS_SIZE; the HB profile (no need=eye line) keeps --no-blobs and its 4 MiB.
# Every case: no fastboot command at all and adb never writes. Never touches anything outside its temp dir.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
INST=$G/installer
OTEST=$G/tools/ota/test
READER=$G/devices/g0111/image/x50_blobs.sh
DCONF=$G/devices/g0111/device.conf
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_x50b.XXXXXX") && W=$(cd "$W" && pwd) || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0; CFAIL=0; CASE=
SER=X50TESTSER01
ADDR=192.0.2.61:5555
dc() { sed -n "s/^$1=//p" "$DCONF" | head -n 1; }
ID=$(dc installer_id); NAME=$(dc name); MODEL=$(dc models | awk '{print $1}'); BOARD=$(dc hardware)
PRODUCT=$(dc fastboot_product); FW=$(dc vendor_firmware | awk '{print $1}'); SL=a
ALLOW=lib/$ID/blobs_allow.txt
[ -f "$INST/$ALLOW" ] || { echo "no $INST/$ALLOW"; exit 1; }
cmp -s "$INST/$ALLOW" "$G/devices/g0111/image/x50_blobs_allow.txt" || { echo "$ALLOW differs from the image's allow-list"; exit 1; }
pc() { awk -v k="$ID.$1=" 'index($0, k) == 1 { print substr($0, length(k) + 1) }' "$INST/lib/devices.conf"; }
[ "$(pc MODEL) $(pc BOARD) $(pc BLOBS_ALLOW) $(pc BLOBS_SIZE)" = "$MODEL $BOARD $ALLOW 16777216" ] \
  || { echo "lib/devices.conf: the $ID profile is not the X50's ($(pc MODEL) $(pc BOARD) $(pc BLOBS_ALLOW) $(pc BLOBS_SIZE))"; exit 1; }
NUI=bin/nativeui
LIB=lib64/xgimi.hardware.gmpf-V1-ndk.so

mkdir -p "$W/bin" "$W/files" "$W/dbin"
python3 "$G/devices/g0111/test/x50_fixture.py" allow "$INST/$ALLOW" "$W/allow.txt" "$W/files" || exit 1
# the fake drawer is 5 MiB (the real one is 5.7 MiB): the tar no longer fits into 4 MiB
head -c 5242880 /dev/zero | tr '\0' 'n' > "$W/files/$NUI"
h=$(shasum -a 256 "$W/files/$NUI" | cut -d' ' -f1)
awk -v p="$NUI" -v h="$h" '$2 == p { $1 = h } { print }' "$W/allow.txt" > "$W/a.tmp" && mv "$W/a.tmp" "$W/allow.txt"
grep -cE '^[0-9a-f]{64} .* need=eye( |$)' "$W/allow.txt" | grep -qx 2 || { echo "the test allow-list lost its need=eye lines"; exit 1; }
SET=$(sed -n 's/^set=//p' "$W/allow.txt")
NB=$(awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3' "$W/allow.txt" | wc -l | tr -d ' ')

cat > "$W/bin/adb" <<'EOF'
#!/bin/sh
# fake network adb of a projector (see tools/devices/test/installer_hb.sh): connect, devices, get-state, getprop,
# pull from $DEV; anything else is recorded as unexpected
case "$1" in start-server|kill-server) exit 0 ;; esac
echo "$*" >> "$FB/adb.log"
case "$1" in
  version) echo "Android Debug Bridge version 1.0.41"; echo "Version 35.0.2-12147458"; exit 0 ;;
  connect) [ "$2" = "$(cat "$FB/addr")" ] || { echo "failed to connect to '$2': Connection refused"; exit 1; }
           grep -qxF "$2" "$FB/connected" && { echo "already connected to $2"; exit 0; }
           echo "$2" >> "$FB/connected"; echo "connected to $2"; exit 0 ;;
  disconnect) grep -vxF "$2" "$FB/connected" > "$FB/c.tmp"; mv "$FB/c.tmp" "$FB/connected"; echo "disconnected $2"; exit 0 ;;
  devices) echo "List of devices attached"; while read -r t; do [ -n "$t" ] && printf '%s\tdevice\n' "$t"; done < "$FB/connected"; echo; exit 0 ;;
  mdns) echo "List of discovered mdns services"; exit 0 ;;
  -s) T=$2; shift 2 ;;
  *) echo "UNEXPECTED $*" >> "$FB/unexpected"; exit 1 ;;
esac
grep -qxF "$T" "$FB/connected" || { echo "error: device '$T' not found" >&2; exit 1; }
[ "$1" = get-state ] && { echo device; exit 0; }
gp() { awk -v k="$1" 'index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' "$FB/props"; }
case "$1" in
  pull) [ -f "$DEV$2" ] || { echo "adb: error: failed to stat remote object '$2': No such file or directory" >&2; exit 1; }
        cp "$DEV$2" "$3" ;;
  shell) shift
    case "$*" in
      "getprop "*) gp "$2" ;;
      "ls /dev/block/mapper/") printf '%s\n' control system_a vendor_a product_a system_ext_a ;;
      "pm list packages -3") : ;;
      "cat /system/etc/z9x/"*) cat "$DEV$2" 2>/dev/null ;;
      "id -u") echo 2000 ;;
      "["*) exit 1 ;;
      *) echo "UNEXPECTED shell $*" >> "$FB/unexpected"; exit 1 ;;
    esac ;;
  *) echo "UNEXPECTED -s $T $*" >> "$FB/unexpected"; exit 1 ;;
esac
exit 0
EOF
cat > "$W/bin/fastboot" <<'EOF'
#!/bin/sh
# 'blobs' never uses fastboot beyond the version check of the Platform-Tools
echo "$*" >> "$FB/fb.log"
[ "$1" = --version ] && { echo "fastboot version 35.0.2-12147458"; exit 0; }
echo "UNEXPECTED fastboot $*" >> "$FB/unexpected"; exit 1
EOF
cat > "$W/bin/date" <<'EOF'
#!/bin/sh
case "${1-}" in +%H:%M:%S) echo 12:00:00 ;; +%Y%m%d-%H%M) echo 20261107-1200 ;; +%Y%m%d-%H%M%S) echo 20261107-120000 ;; *) exec /bin/date "$@" ;; esac
EOF
printf '#!/bin/sh\nexit 0\n' > "$W/bin/sleep"
chmod 755 "$W/bin/"*
for f in getprop setprop log timeout chcon chown; do cp "$OTEST/stubs/$f" "$W/dbin/$f" && chmod 755 "$W/dbin/$f"; done
cat > "$W/dbin/mount" <<'EOF'
#!/bin/sh
[ "$1" = --bind ] || exit 0
[ -e "$2" ] && [ -e "$3" ] || exit 1
printf '%s %s\n' "$(shasum -a 256 "$2" | cut -d' ' -f1)" "$3" >> "$Z9X_T/binds"
EOF
printf '#!/bin/sh\nexit 0\n' > "$W/dbin/umount"
chmod 755 "$W/dbin/mount" "$W/dbin/umount"

PROPS="ro.boot.xgimi.modelname=$MODEL
ro.boot.hardware=$BOARD
ro.vendor.build.version.incremental=$FW
ro.serialno=$SER
ro.boot.slot_suffix=_$SL
ro.virtual_ab.enabled=true
ro.build.type=user
ro.adb.secure=0
sys.boot_completed=1"
profile() {  # BLOBS_SIZE value (empty: none): that one line of the real x50 profile in the copy C/inst
  local f=$C/inst/lib/devices.conf
  grep -v "^$ID\.BLOBS_SIZE=" "$f" > "$f.tmp"
  [ -z "$1" ] || printf '%s\n' "$ID.BLOBS_SIZE=$1" >> "$f.tmp"
  mv "$f.tmp" "$f"
}
begin() {  # case [BLOBS_SIZE (default 16777216; - for none)]
  CASE=$1; CFAIL=0
  C=$W/$1; rm -rf "$C"; mkdir -p "$C/inst" "$C/fb" "$C/state" "$C/backup" "$C/dev"
  FB=$C/fb; DEV=$C/dev
  cp "$INST/lumen-install.sh" "$C/inst/"; cp -R "$INST/lib" "$INST/certs" "$C/inst/"
  cp "$W/allow.txt" "$C/inst/$ALLOW"
  local bs=${2:-16777216}; [ "$bs" = - ] && bs=""
  profile "$bs"
  printf 'DEVICE=%s\nVERSION=9.9.9-test\n' "$ID" > "$C/inst/release.conf"
  printf '%s\n' "$PROPS" > "$FB/props"; echo "$ADDR" > "$FB/addr"
  : > "$FB/connected"; : > "$FB/adb.log"; : > "$FB/fb.log"
  # the projector's files: every allow-list source (a from= line: only its stock original)
  awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { print $2, $3, ($0 ~ / from=/) }' "$W/allow.txt" | while read -r rel src fr; do
    mkdir -p "$(dirname "$DEV$src")"
    if [ "$fr" = 1 ]; then cp "$W/files/$rel.stock" "$DEV$src"; else cp "$W/files/$rel" "$DEV$src"; fi
  done
}
inst() {  # args... -> the installer copy; exit status in RC, output in $C/out
  env LUMEN_PLATFORM_TOOLS="$W/bin" PATH="$W/bin:$PATH" FB="$FB" DEV="$DEV" LUMEN_STATE_DIR="$C/state" \
    LUMEN_BACKUP_DIR="$C/backup" LUMEN_EROFS_UTILS= LANG=en_US.UTF-8 ${ENVX-} \
    bash "$C/inst/lumen-install.sh" "$@" < /dev/null > "$C/out" 2>&1
  RC=$?
}
fail() { echo "  FAIL [$CASE] $*"; CFAIL=1; }
expect_rc() { [ "$RC" = "$1" ] || fail "exit $RC, want $1"; }
expect_out() { grep -qF -- "$1" "$C/out" || fail "output lacks '$1'"; }
expect_no_out() { if grep -qF -- "$1" "$C/out"; then fail "output has '$1'"; fi; }
BLOBF() { echo "$C/backup/$ID-blobs-$SER.img"; }
expect_no_tar() { [ ! -e "$(BLOBF)" ] || fail "a blobs tar was written"; [ "$(sed -n 's/^step_blobs=//p' "$C/state/$ID/state" 2>/dev/null | tail -1)" != done ] || fail "step_blobs=done"; }
end() {
  [ ! -s "$FB/unexpected" ] || fail "unexpected: $(cat "$FB/unexpected")"
  grep -qvx -- --version "$FB/fb.log" && fail "fastboot used: $(tr '\n' ';' < "$FB/fb.log")"
  grep -E ' (reboot|root|push|install|uninstall|am)( |$)| shell (echo|rm|setprop|svc) ' "$FB/adb.log" && fail "adb wrote"
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $CASE"; sed 's/^/     | /' "$C/out" | tail -8; fi
}
roundtrip() {  # the tar the installer wrote, read by the real x50_blobs.sh with the test allow-list
  local R=$C/root T=$C/dt img; img=$(BLOBF)
  mkdir -p "$R/system/etc/z9x" "$R/dev/block/mapper" "$R/mnt" "$R/data/vendor/tmp" "$T"
  cp "$W/allow.txt" "$R/system/etc/z9x/x50_blobs_allow.txt"; cp "$img" "$R/dev/block/mapper/z9x_blobs_$SL"
  awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { t = "/system/" $2; for (i = 4; i <= NF; i++) if ($i ~ /^bind=/) t = substr($i, 6); print $1, t }' \
    "$W/allow.txt" > "$T/want"
  while read -r h t; do mkdir -p "$(dirname "$R$t")"; case "$t" in /system/*) : > "$R$t" ;; *) echo own > "$R$t" ;; esac; done < "$T/want"
  printf '%s\n' "ro.boot.slot_suffix=_$SL" "ro.vendor.build.version.incremental=${RTFW:-$FW}" > "$T/props"; : > "$T/binds"
  env Z9X_ROOT="$R" Z9X_T="$T" PATH="$W/dbin:$PATH" sh "$READER" > "$T/out" 2>&1
  [ ! -s "$T/out" ] || fail "x50_blobs.sh output: $(cat "$T/out")"
  grep -qx 'sys.z9x.blobs=ok' "$T/props" || fail "round trip: $(grep sys.z9x.blobs "$T/props") $(cat "$T/logcat" 2>/dev/null)"
  grep -qx 'sys.lumen.nui=ok' "$T/props" || fail "round trip: $(grep sys.lumen.nui "$T/props")"
  while read -r h t; do
    case " ${RTALT-} " in *" $t "*) h=$(awk -v t="$t" '"/system/" $2 == t { for (i = 4; i <= NF; i++) if ($i ~ /^alt=/) { sub(/^alt=[^:]*:/, "", $i); print $i } }' "$W/allow.txt") ;; esac
    grep -qx "$h $R$t" "$T/binds" || fail "round trip: $t not bound with its file"
  done < "$T/want"
  grep -q "^u:object_r:nativeui_main_exec:s0 .*/$NUI$" "$T/chcon.log" || fail "round trip: the drawer is not nativeui_main_exec"
}
eye_text="cannot draw its eye-protection mask. Nothing was written."
nob_text="draws its eye-protection mask with files from its own system: --no-blobs and --blobs=embedded are not possible for it. Nothing was done."

begin blobs_stock
inst --ip "$ADDR" blobs
expect_rc 0; expect_out "$NB MediaTek / XGIMI files from your projector, set $SET"
[ "$(wc -c < "$(BLOBF)" | tr -d ' ')" = 16777216 ] || fail "the tar is not 16 MiB"
tar -tf "$(BLOBF)" > "$C/list" 2>/dev/null || fail "not a tar"
grep -qx MANIFEST "$C/list" && grep -qx "$NUI" "$C/list" && grep -qx "$LIB" "$C/list" || fail "tar members: $(tr '\n' ' ' < "$C/list")"
tar -xOf "$(BLOBF)" MANIFEST > "$C/manifest" 2>/dev/null
[ "$(sed -n 1p "$C/manifest")" = "set=$SET" ] || fail "MANIFEST set line"
[ "$(grep -c . "$C/manifest" | tr -d ' ')" = $((NB + 1)) ] || fail "MANIFEST lines: $(cat "$C/manifest")"
grep -qx "$NUI $(awk -v p="$NUI" '$2 == p { print $1 }' "$W/allow.txt") 5242880" "$C/manifest" || fail "MANIFEST lacks the drawer line"
roundtrip
end

# stock v1.2.54: its own eye-protection pair (the alt= copies), taken only there
begin blobs_stock_v1254
sed -i.bak 's/^ro.vendor.build.version.incremental=.*/ro.vendor.build.version.incremental=v1.2.54/' "$FB/props"
cp "$W/files/$NUI.alt" "$DEV/system/$NUI"; cp "$W/files/$LIB.alt" "$DEV/system/$LIB"
inst --ip "$ADDR" blobs
expect_rc 0; expect_out "$NB MediaTek / XGIMI files from your projector, set $SET"
tar -xOf "$(BLOBF)" MANIFEST > "$C/manifest" 2>/dev/null
alt() { awk -v p="$1" '$2 == p { for (i = 4; i <= NF; i++) if ($i ~ /^alt=/) { sub(/^alt=[^:]*:/, "", $i); print $i } }' "$W/allow.txt"; }
grep -q "^$NUI $(alt "$NUI") " "$C/manifest" && grep -q "^$LIB $(alt "$LIB") " "$C/manifest" || fail "MANIFEST: $(cat "$C/manifest")"
RTFW=v1.2.54 RTALT="/system/$NUI /system/$LIB" roundtrip
end
begin eye_alt_on_v1264
cp "$W/files/$NUI.alt" "$DEV/system/$NUI"; cp "$W/files/$LIB.alt" "$DEV/system/$LIB"
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "$NUI from the projector does not match the allow-list"; expect_out "$eye_text"; expect_no_tar
end

begin eye_drawer_mismatch
echo "another build" > "$DEV/system/$NUI"
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "$NUI from the projector does not match the allow-list"; expect_out "$eye_text"
expect_no_out "Run again with --no-blobs"; expect_no_tar
end

begin eye_library_missing
rm -f "$DEV/system/$LIB"
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "$LIB from the projector does not match the allow-list ()"; expect_out "$eye_text"; expect_no_tar
end

begin codec_mismatch
echo "other codec" > "$DEV/system/lib64/libcodec2_soft_common.so"
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "lib64/libcodec2_soft_common.so from the projector does not match"; expect_out "$eye_text"
expect_no_out "--no-blobs"; expect_no_tar
end

for c in "no_blobs:--no-blobs blobs:" "no_blobs_check:--no-blobs check:" "embedded:blobs:BLOBS=embedded" "embedded_opt:--blobs=embedded blobs:"; do
  n=${c%%:*}; r=${c#*:}; a=${r%%:*}; ENVX=${r#*:}
  begin "refused_$n"
  # shellcheck disable=SC2086
  inst --ip "$ADDR" $a
  expect_rc 1; expect_out "the $NAME $nob_text"
  grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
  expect_no_tar
  end
done
ENVX=

for bs in 16777215 1048576 134217728 16M; do
  begin "blobs_size_$bs" "$bs"
  inst --ip "$ADDR" blobs
  expect_rc 1; expect_out "internal: lib/devices.conf: $ID.BLOBS_SIZE"; expect_no_tar
  end
done

begin blobs_size_4mib 4194304
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "blobs tar larger than 4 MiB"; expect_no_tar
end

begin blobs_size_none -
inst --ip "$ADDR" blobs
expect_rc 1; expect_out "blobs tar larger than 4 MiB"; expect_no_tar
end

# the HB profile has no need=eye line: --no-blobs stays possible there, as before
HB=$(sed -n 's/^\([a-z0-9]*\)\.FLOW=ab-fastbootd$/\1/p' "$INST/lib/devices.conf" | grep -vx "$ID" | head -n 1)
begin hb_no_blobs
inst --device "$HB" --ip "$ADDR" --no-blobs blobs
expect_rc 0; expect_out "--no-blobs: the MediaTek / XGIMI files are skipped"; expect_no_out "eye-protection"
end

echo "installer x50 blobs: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
