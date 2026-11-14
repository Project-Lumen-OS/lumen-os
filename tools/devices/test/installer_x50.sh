#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Mock test: the XGIMI Z9X (G0082) and the XGIMI X50 Ultra (G0111) never mix in the installer
# (installer/lumen-install.sh). Both run XGIMI's horse_popular product image: ro.product.device, the board platform
# and the fastbootd 'product' are the same, so only the model (ro.boot.xgimi.modelname), the board
# (ro.boot.hardware), each one's own signing key and what the image says about itself tell them apart. On the Mac
# or Linux, no projector, no network:
#   bash tools/devices/test/installer_x50.sh
# The X50 profile is the one of installer/lib/devices.conf whose id is devices/g0111/device.conf's installer_id;
# the Z9X one is the z9x lines (the Z9X constants of the script). Every value comes from there, none is written
# here. Each case runs a copy of the installer: certs/ota.x509.pem (the Z9X) and the X50 profile's CERT are two
# throwaway test certificates, SHA256SUMS of a fake 1 MiB erofs image is signed with one of the two keys, a fake
# dump.erofs serves the image's build.prop, the fake projector answers over the USB cable (the Z9X way) or over
# the network (the X50 way, as tools/devices/test/installer_hb.sh), with getprop from $FB/props.
# Cases, direction 1 (a Z9X image never reaches a G0111):
#   the Z9X download on a G0111 on the cable: STOP, it is not a Z9X, the way of the X50 named, nothing else read
#   the X50 download with a Z9X image: a Z9X file name; X50-named but signed with the Z9X key; signed right but
#     its build.prop is the Z9X's (no ro.lumen.device of the X50): STOP each, nothing written
#   a G0111 that runs Lumen OS for the Z9X (ro.z9x.version, no ro.lumen.device): STOP, not taken for stock
# direction 2 (an X50 image never reaches a G0082):
#   the X50 download on a G0082 over the network: STOP, it is not an X50, the way of the Z9X named
#   the Z9X download with an X50 image: its file name; the X50 download given --device z9x; renamed but signed with
#     the X50 key; renamed, signed with the Z9X key, its build.prop the X50's (ro.lumen.device of the X50, or
#     ro.z9x.device not z9x): STOP each; a Z9X build.prop (ro.z9x.device=z9x) still passes
# the X50 way itself: check on stock passes with its own key and build.prop; the full flash visit writes exactly
#   z9x_blobs (BLOBS_SIZE) of the running slot, system, the format of WIPE_PARTS, never a delete (product and
#   system_ext of the stock stay: KEEP) and never another partition; fastbootd must answer the family's product;
#   a KEEP partition missing in fastbootd: STOP; 'stick' is not there for it yet.
# the data format (WIPE_FS): before any case, the profile's WIPE_FS must be the file system the stock X50 fstab
#   mounts on every WIPE_PARTS partition (devices/g0111/research/data_fs.tsv: f2fs), never ext4; the fake fastbootd
#   records any 'format:ext4' as unexpected, so every flash case fails if the X50 is ever formatted ext4; the wipe is
#   exactly '--fs-options=<the stock options> format:f2fs userdata' and 'format:f2fs metadata'; without make_f2fs
#   next to fastboot: STOP before anything; a WIPE_FS that is neither ext4 nor f2fs: internal error.
# Exit status 0 only when every case passes. Never touches anything outside its temp dir.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
INST=$G/installer
CONF=$INST/lib/devices.conf
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_x50.XXXXXX") && W=$(cd "$W" && pwd) || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0; CFAIL=0; CASE=
SER=X50SEPTEST01
ADDR=192.0.2.62:5555
conf() { awk -v k="$1.$2=" 'index($0, k) == 1 { print substr($0, length(k) + 1) }' "$CONF"; }
X=$(sed -n 's/^installer_id=//p' "$G/devices/g0111/device.conf")
[ -n "$X" ] && [ -n "$(conf "$X" NAME)" ] || { echo "lib/devices.conf has no profile for devices/g0111 (installer_id '$X')"; exit 1; }
XNAME=$(conf "$X" NAME); XMODEL=$(conf "$X" MODEL); XBOARD=$(conf "$X" BOARD); XFW=$(conf "$X" VENDOR_OK | awk '{print $1}')
XTOK=$(conf "$X" IMAGE_TOKEN); XCERT=$(conf "$X" CERT); XALLOW=$(conf "$X" BLOBS_ALLOW); XBS=$(conf "$X" BLOBS_SIZE)
XLP=$(conf "$X" LUMEN_PROP | awk '{print $1}'); XLV=$(conf "$X" LUMEN_PROP | awk '{print $2}'); XVP=$(conf "$X" VERSION_PROP)
XFAM=$(conf "$X" FAMILY); XKEEP=$(conf "$X" KEEP); XWIPE=$(conf "$X" WIPE_PARTS); XSL=$(conf "$X" SLOTS | awk '{print $1}')
XWFS=$(conf "$X" WIPE_FS)
ZNAME=$(conf z9x NAME); ZMODEL=$(conf z9x MODEL); ZBOARD=$(conf z9x BOARD); ZFW=$(conf z9x VENDOR_OK | awk '{print $1}')
ZVP=$(conf z9x VERSION_PROP)
# the RS30 Ultra Max (installer_id of the devices/ entry whose models name it): a profile that is not finished (TODO)
RREG=""
for f in "$G"/devices/*/device.conf; do grep -qx 'abi=arm32' "$f" && grep -qx 'family=horse_popular' "$f" && { RREG=$f; break; }; done
RID=$(sed -n 's/^installer_id=//p' "$RREG"); RNAME=$(conf "$RID" NAME); RMODEL=$(conf "$RID" MODEL)
RBOARD=$(conf "$RID" BOARD); RFW=$(conf "$RID" VENDOR_OK | awk '{print $1}'); RTOK=$(conf "$RID" IMAGE_TOKEN)
RCERT=$(conf "$RID" CERT); RLP=$(conf "$RID" LUMEN_PROP)
[ -n "$RID" ] && [ -n "$RNAME" ] && [ "$(conf "$RID" ABI)" = arm32 ] && conf "$RID" SYSTEM_MAX | grep -q '^TODO' \
  || { echo "lib/devices.conf has no unfinished arm32 profile of the horse_popular family"; exit 1; }
# the X50 Ultra Max (devices/g0100): the X50 Ultra's firmware and board, no profile of its own (trial only)
MREG=$G/devices/g0100/device.conf
MID=$(sed -n 's/^installer_id=//p' "$MREG"); MMODEL=$(sed -n 's/^models=//p' "$MREG" | awk '{print $1}')
MBOARD=$(sed -n 's/^hardware=//p' "$MREG")
[ -n "$MID" ] && [ -n "$MMODEL" ] && [ "$MBOARD" = "$XBOARD" ] || { echo "devices/g0100 is not an X50 Ultra Max entry"; exit 1; }
[ "$XFAM" = z9x ] || { echo "the $X profile is not on the Z9X's product image (FAMILY '$XFAM')"; exit 1; }
FBP=$(conf "$XFAM" FB_PRODUCT)
[ -z "$(conf "$X" FB_PRODUCT)" ] || { echo "the $X profile names an FB_PRODUCT of its own"; exit 1; }
[ "$XMODEL" != "$ZMODEL" ] && [ "$XBOARD" != "$ZBOARD" ] || { echo "the $X and z9x profiles share a model or a board"; exit 1; }
VER=9.9.9-test
XIMG=lumen-os-$XTOK-$VER-nogms-system.img
ZIMG=lumen-os-1.0.2-nogms-system.img
case "$XSL" in a) XOSL=b ;; *) XOSL=a ;; esac
# the data format: what the stock X50 fstab mounts on each WIPE_PARTS partition (data_fs.tsv) is what the profile
# formats, and that is never ext4 (the design notes: an ext4 /data under an f2fs fstab never mounts)
DFS=$G/devices/g0111/research/data_fs.tsv
[ -f "$DFS" ] || { echo "no $DFS"; exit 1; }
XWANT_WIPE=""   # the wipe commands the stock fstab asks for, in WIPE_PARTS order
for p in $XWIPE; do
  fs=$(awk -F'\t' -v p="$p" '$1 == p { print $3 }' "$DFS"); fo=$(awk -F'\t' -v p="$p" '$1 == p { print $4 }' "$DFS")
  [ -n "$fs" ] || { echo "data_fs.tsv has no row for $p (WIPE_PARTS of the $X profile)"; exit 1; }
  [ "$fs" = "$XWFS" ] || { echo "the $X profile formats $p as '$XWFS' (WIPE_FS), the stock X50 fstab mounts it as $fs"; exit 1; }
  [ "$XWFS" != ext4 ] || { echo "the $X profile formats $p as ext4"; exit 1; }
  if [ -n "$fo" ] && [ "$fo" != - ]; then XWANT_WIPE="$XWANT_WIPE-s $SER --fs-options=$fo format:$fs $p;"
  else XWANT_WIPE="$XWANT_WIPE-s $SER format:$fs $p;"; fi
done

mkdir -p "$W/bin" "$W/keys" "$W/files"
for k in z x; do   # z: the Z9X release key of this test, x: the X50's
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$W/keys/$k.key" -out "$W/keys/$k.pem" -subj "/CN=lumen-test-$k" -days 2 >/dev/null 2>&1 \
    || { echo "openssl cannot make a test key"; exit 1; }
done
python3 "$G/devices/g0111/test/x50_fixture.py" allow "$INST/$XALLOW" "$W/allow.txt" "$W/files" || exit 1

cat > "$W/bin/adb" <<'EOF'
#!/bin/sh
# fake adb of one projector: on the USB cable ($FB/usb: 'adb devices' lists $FB_SERIAL, the Z9X way) or over the
# network ($FB/addr: adb connect, the X50 way); its system runs while $FB/mode is 'android'
case "$1" in start-server|kill-server) exit 0 ;; esac
echo "$*" >> "$FB/adb.log"
mode=$(cat "$FB/mode")
case "$1" in
  version) echo "Android Debug Bridge version 1.0.41"; echo "Version 35.0.2-12147458"; exit 0 ;;
  connect) if [ "$mode" = android ] && [ "$2" = "$(cat "$FB/addr" 2>/dev/null)" ]; then
             grep -qxF "$2" "$FB/connected" && { echo "already connected to $2"; exit 0; }
             echo "$2" >> "$FB/connected"; echo "connected to $2"; exit 0
           fi
           echo "failed to connect to '$2': Connection refused"; exit 1 ;;
  disconnect) grep -vxF "$2" "$FB/connected" > "$FB/c.tmp"; mv "$FB/c.tmp" "$FB/connected"; echo "disconnected $2"; exit 0 ;;
  devices) echo "List of devices attached"
           if [ "$mode" = android ]; then
             [ -e "$FB/usb" ] && printf '%s\tdevice\n' "$FB_SERIAL"
             while read -r t; do [ -n "$t" ] && printf '%s\tdevice\n' "$t"; done < "$FB/connected"
           fi
           echo; exit 0 ;;
  mdns) echo "List of discovered mdns services"; exit 0 ;;
  -s) T=$2; shift 2 ;;
  *) echo "UNEXPECTED $*" >> "$FB/unexpected"; exit 1 ;;
esac
[ "$mode" = android ] || { echo "error: device '$T' not found" >&2; exit 1; }
if [ "$T" = "$FB_SERIAL" ]; then [ -e "$FB/usb" ] || { echo "error: device '$T' not found" >&2; exit 1; }
else grep -qxF "$T" "$FB/connected" || { echo "error: device '$T' not found" >&2; exit 1; }; fi
[ "$1" = get-state ] && { echo device; exit 0; }
gp() { awk -v k="$1" 'index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' "$FB/props"; }
case "$1" in
  reboot) [ "${2-}" = fastboot ] || { echo "UNEXPECTED reboot $*" >> "$FB/unexpected"; exit 1; }
          echo fastboot > "$FB/mode" ;;
  pull) [ -f "$DEV$2" ] || { echo "adb: error: failed to stat remote object '$2': No such file or directory" >&2; exit 1; }
        cp "$DEV$2" "$3" ;;
  shell) shift
    case "$*" in
      "getprop "*) gp "$2" ;;
      "ls /dev/block/mapper/") cat "$FB/mapper" ;;
      "pm list packages -3") : ;;
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
# fake fastboot of the projector's fastbootd (see tools/devices/test/installer_hb.sh); 'getvar' answers on stderr
echo "$*" >> "$FB/fb.log"
[ "$1" = --version ] && { echo "fastboot version 35.0.2-12147458"; exit 0; }
if [ "$1" = devices ]; then [ "$(cat "$FB/mode")" = fastboot ] && printf '%s\tfastboot\n' "$FB_SERIAL"; exit 0; fi
[ "$1" = -s ] && { [ "$2" = "$FB_SERIAL" ] || { echo "< waiting for $2 >" >&2; exit 1; }; shift 2; }
[ "$(cat "$FB/mode")" = fastboot ] || { echo "< waiting for any device >" >&2; exit 1; }
size() { awk -v p="$1" '$1 == p { print $2 }' "$FB/parts"; }
setsize() { awk -v p="$1" '$1 != p' "$FB/parts" > "$FB/p.tmp"; echo "$1 $2" >> "$FB/p.tmp"; mv "$FB/p.tmp" "$FB/parts"; }
case "$1" in
  getvar)
    # what fastbootd reports: $FB/vars, after the system write $FB/vars_after when a case gives one; a single
    # 'getvar NAME' answers from $FB/single when a case gives one (a fastbootd that leaves a value out of 'all')
    V=$FB/vars; [ -e "$FB/flashed" ] && [ -f "$FB/vars_after" ] && V=$FB/vars_after
    if [ "$2" = all ]; then
      cat "$V" >&2
      while read -r p s; do printf '(bootloader) is-logical:%s:yes\n(bootloader) partition-size:%s:0x%X\n' "$p" "$p" "$s" >&2; done < "$FB/parts"
      echo "all: Done" >&2
    else
      case "$2" in
        partition-size:*) s=$(size "${2#partition-size:}"); [ -n "$s" ] && printf '%s: 0x%X\n' "$2" "$s" >&2 ;;
        *) [ -f "$FB/single" ] && V=$FB/single
           v=$(awk -v k="(bootloader) $2:" 'index($0, k) == 1 { print substr($0, length(k) + 1); exit }' "$V")
           [ -n "$v" ] && printf '%s: %s\n' "$2" "$v" >&2 ;;
      esac
      echo "Finished. Total time: 0.001s" >&2
    fi ;;
  create-logical-partition) [ -z "$(size "$2")" ] || { echo "FAILED (remote: 'Partition already exists')" >&2; exit 1; }; setsize "$2" "$3" ;;
  resize-logical-partition) [ -n "$(size "$2")" ] || { echo "FAILED (remote: 'Could not find partition')" >&2; exit 1; }; setsize "$2" "$3" ;;
  flash) case "$2" in system_*) setsize "$2" "$(wc -c < "$3" | tr -d ' ')"; touch "$FB/flashed"; [ ! -f "$FB/hook" ] || sh "$FB/hook" ;; z9x_blobs_*) cp "$3" "$FB/$2.img" ;; esac
         echo "Writing '$2' OKAY" >&2 ;;
  format:ext4) echo "UNEXPECTED fastboot $* (the X50 mounts userdata and metadata as f2fs)" >> "$FB/unexpected"; exit 1 ;;
  format:f2fs) [ "$2" = metadata ] || { echo "UNEXPECTED fastboot $*" >> "$FB/unexpected"; exit 1; }; touch "$FB/formatted_$2" ;;
  --fs-options=*) [ "$2 $3" = "format:f2fs userdata" ] || { echo "UNEXPECTED fastboot $*" >> "$FB/unexpected"; exit 1; }
                  touch "$FB/formatted_$3" ;;
  reboot) if [ -e "$FB/flashed" ]; then echo lumen > "$FB/mode"; else echo android > "$FB/mode"; fi; : > "$FB/connected" ;;
  *) echo "UNEXPECTED fastboot $*" >> "$FB/unexpected"; exit 1 ;;
esac
exit 0
EOF
cat > "$W/bin/dump.erofs" <<'EOF'
#!/bin/sh
# fake dump.erofs: the image's files are $FB/erofs/<path> (no $FB/erofs: the image cannot be read)
[ -d "$FB/erofs" ] || exit 1
case "$1" in
  --cat) p=${2#--path=}; [ -f "$FB/erofs$p" ] && cat "$FB/erofs$p" ;;
  --path=*) p=${1#--path=}; [ -f "$FB/erofs$p" ] || exit 1; echo "Path : $p"; echo "Size: $(wc -c < "$FB/erofs$p" | tr -d ' ')  On-disk size: 0" ;;
  *) exit 1 ;;
esac
EOF
cat > "$W/bin/date" <<'EOF'
#!/bin/sh
case "${1-}" in +%H:%M:%S) echo 12:00:00 ;; +%Y%m%d-%H%M) echo 20261107-1200 ;; +%Y%m%d-%H%M%S) echo 20261107-120000 ;; *) exec /bin/date "$@" ;; esac
EOF
cat > "$W/bin/df" <<'EOF'
#!/bin/sh
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n/dev/disk3s5 970000000 400000000 500000000 45%% /\n'
EOF
printf '#!/bin/sh\nexit 0\n' > "$W/bin/sleep"
printf '#!/bin/sh\nexit 0\n' > "$W/bin/mke2fs"; cp "$W/bin/mke2fs" "$W/bin/make_f2fs"
chmod 755 "$W/bin/"*
# the same Platform-Tools without make_f2fs (the x50_no_make_f2fs case)
mkdir -p "$W/pt_nof2fs"; for f in adb fastboot mke2fs; do cp "$W/bin/$f" "$W/pt_nof2fs/$f"; done

mkimg() {  # file: a 1 MiB image with the erofs magic at byte 1024
  dd if=/dev/zero of="$1" bs=4096 count=256 2>/dev/null
  printf '\342\341\365\340' | dd of="$1" bs=1 seek=1024 conv=notrunc 2>/dev/null
}
sign() {  # dir key: SHA256SUMS of the images in dir, signed with the key (z or x)
  (cd "$1" && for f in lumen-os-*-system.img; do { sha256sum "$f" 2>/dev/null || shasum -a 256 "$f"; }; done > SHA256SUMS
   openssl dgst -sha256 -sign "$W/keys/$2.key" -out SHA256SUMS.sig SHA256SUMS)
}
content() {  # props-lines...: the fake image's product build.prop
  mkdir -p "$FB/erofs/system/product/etc"; printf '%s\n' "$@" > "$FB/erofs/system/product/etc/build.prop"
}
# begin case device way: a fresh copy of the installer and a projector (G0082 or G0111), on the cable or the network
begin() {
  CASE=$1; CFAIL=0
  C=$W/$1; rm -rf "$C"; mkdir -p "$C/inst" "$C/fb" "$C/state" "$C/backup" "$C/dev"
  FB=$C/fb; DEV=$C/dev
  cp "$INST/lumen-install.sh" "$INST/release.conf" "$C/inst/"; cp -R "$INST/lib" "$INST/certs" "$C/inst/"
  cp "$W/allow.txt" "$C/inst/$XALLOW"
  cp "$W/keys/z.pem" "$C/inst/certs/ota.x509.pem"; cp "$W/keys/x.pem" "$C/inst/$XCERT"
  case "$2" in
    z9x) printf '%s\n' "ro.boot.xgimi.modelname=$ZMODEL" "ro.boot.hardware=$ZBOARD" "ro.vendor.build.version.incremental=$ZFW" ;;
    x50) printf '%s\n' "ro.boot.xgimi.modelname=$XMODEL" "ro.boot.hardware=$XBOARD" "ro.vendor.build.version.incremental=$XFW" ;;
    x50max) printf '%s\n' "ro.boot.xgimi.modelname=$MMODEL" "ro.boot.hardware=$MBOARD" "ro.vendor.build.version.incremental=$XFW" ;;
    rs30) printf '%s\n' "ro.boot.xgimi.modelname=$RMODEL" "ro.boot.hardware=$RBOARD" "ro.vendor.build.version.incremental=$RFW" ;;
  esac > "$FB/props"
  printf '%s\n' "ro.serialno=$SER" "ro.boot.slot_suffix=_$XSL" ro.virtual_ab.enabled=true ro.build.type=user ro.debuggable=0 \
    sys.boot_completed=1 >> "$FB/props"
  case "$3" in usb) touch "$FB/usb" ;; net) echo "$ADDR" > "$FB/addr" ;; esac
  printf '%s\n' control "system_$XSL" "vendor_$XSL" "product_$XSL" "system_ext_$XSL" > "$FB/mapper"
  echo android > "$FB/mode"; : > "$FB/connected"; : > "$FB/adb.log"; : > "$FB/fb.log"
  { echo "(bootloader) is-userspace:yes"; echo "(bootloader) product:$FBP"; echo "(bootloader) serialno:$SER"
    echo "(bootloader) unlocked:yes"; echo "(bootloader) current-slot:$XSL"; echo "(bootloader) slot-count:2"
    echo "(bootloader) slot-successful:$XSL:yes"; echo "(bootloader) slot-unbootable:$XOSL:no"
    echo "(bootloader) slot-unbootable:$XSL:no"; echo "(bootloader) slot-retry-count:$XSL:2"
    echo "(bootloader) slot-retry-count:$XOSL:3"; echo "(bootloader) slot-successful:$XOSL:no"
    echo "(bootloader) snapshot-update-status:none"; echo "(bootloader) super-partition-name:super"
    echo "(bootloader) max-download-size:0x10000000"; echo "(bootloader) partition-size:super:0x113400000"
    echo "(bootloader) partition-type:userdata:raw"; echo "(bootloader) partition-type:metadata:raw"
  } > "$FB/vars"
  # super as in the X50 stock dump (sizes of lpdump), the other slot empty
  printf '%s\n' "system_$XSL 942546944" "vendor_$XSL 1543102464" "product_$XSL 131313664" "system_ext_$XSL 33144832" \
    "system_$XOSL 0" "vendor_$XOSL 0" "product_$XOSL 0" "system_ext_$XOSL 0" > "$FB/parts"
  # the projector's own files of the X50 allow-list (a from= line: only its stock original)
  awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { print $2, $3, ($0 ~ / from=/) }' "$W/allow.txt" | while read -r rel src fr; do
    mkdir -p "$(dirname "$DEV$src")"
    if [ "$fr" = 1 ]; then cp "$W/files/$rel.stock" "$DEV$src"; else cp "$W/files/$rel" "$DEV$src"; fi
  done
}
x50_download() {  # the X50 release download: release.conf of its id and version, its image signed with key $1
  printf 'DEVICE=%s\nVERSION=%s\n' "$X" "$VER" > "$C/inst/release.conf"
  mkimg "$C/inst/${2:-$XIMG}"; sign "$C/inst" "$1"
}
z9x_download() {  # the Z9X release download: the image ${2:-the Z9X name} signed with key $1
  mkimg "$C/inst/${2:-$ZIMG}"; sign "$C/inst" "$1"
}
inst() {  # answers args... -> the installer copy; exit status in RC, output in $C/out
  local ans=$1; shift
  printf '%b' "$ans" | env LUMEN_PLATFORM_TOOLS="${PT_BIN:-$W/bin}" PATH="$W/bin:$PATH" FB="$FB" DEV="$DEV" FB_SERIAL=$SER \
    LUMEN_STATE_DIR="$C/state" LUMEN_BACKUP_DIR="$C/backup" LUMEN_EROFS_UTILS= LANG=en_US.UTF-8 \
    bash "$C/inst/lumen-install.sh" "$@" > "$C/out" 2>&1
  RC=$?
}
fail() { echo "  FAIL [$CASE] $*"; CFAIL=1; }
expect_rc() { [ "$RC" = "$1" ] || fail "exit $RC, want $1"; }
expect_out() { grep -qF -- "$1" "$C/out" || fail "output lacks '$1'"; }
expect_no_out() { if grep -qF -- "$1" "$C/out"; then fail "output has '$1'"; fi; }
cmds() { grep -v -e '^--version$' -e '^devices$' "$FB/fb.log" | sed "s|$C/||g" | tr '\n' ';'; }
no_fastboot() { [ -z "$(cmds)" ] || fail "fastboot used: $(cmds)"; }
reads_only() {  # adb only read the projector (no reboot, push, shell write)
  if grep -E ' (reboot|root|push|install|uninstall|am)( |$)| shell (echo|rm|setprop|svc|dd) ' "$FB/adb.log" | grep -q .; then
    fail "adb wrote: $(tr '\n' ';' < "$FB/adb.log")"
  fi
}
end() {
  [ ! -s "$FB/unexpected" ] || fail "unexpected: $(cat "$FB/unexpected")"
  # never a delete, an erase, -w, vbmeta, a slot switch, or a partition the X50 keeps (KEEP) or of the other slot
  if grep -qE '(^| )(-w|erase|delete-logical-partition|vbmeta|--slot|--slot=.*|set_active|snapshot-update|flashall|update|wipe-super|oem)( |:|$)' "$FB/fb.log"; then
    fail "forbidden fastboot command: $(tr '\n' ';' < "$FB/fb.log")"
  fi
  for k in $XKEEP; do grep -qE " ${k}_[ab]( |$)" "$FB/fb.log" && fail "a KEEP partition was touched: $(tr '\n' ';' < "$FB/fb.log")"; done
  grep -qE "_$XOSL( |$)" "$FB/fb.log" && fail "a partition of the other slot: $(tr '\n' ';' < "$FB/fb.log")"
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $CASE"; sed 's/^/     | /' "$C/out" | tail -8; fi
}
# what the X50 image says about itself (devices/g0111/image/x50_prop.txt): the profile's LUMEN_PROP, its own device id
XPT=$G/devices/g0111/image/x50_prop.txt
grep -qx "+$XLP=$XLV" "$XPT" || { echo "x50_prop.txt does not set $XLP=$XLV (the profile's LUMEN_PROP)"; exit 1; }
XZD=$(sed -n 's/^ro\.z9x\.device=//p' "$XPT")
[ -n "$XZD" ] && [ "$XZD" != z9x ] || { echo "x50_prop.txt: ro.z9x.device '$XZD'"; exit 1; }
ZPROPS_IMG="ro.z9x.device=z9x ro.z9x.gms=0 $ZVP=1.0.2 ro.adb.secure=1"
XPROPS_IMG="$XLP=$XLV ro.z9x.device=$XZD ro.z9x.gms=0 $XVP=$VER ro.adb.secure=1"

# ---------------------------------------------------------------- 1. a Z9X image never reaches a G0111
begin z9x_download_on_x50 x50 usb
z9x_download z
inst '' check
expect_rc 1; expect_out "this is not an $ZNAME (model '$XMODEL', board '$XBOARD'). Nothing was done."
expect_out "It is an $XNAME: it installs with the installer of its own release (--device $X, see its guide)."
expect_no_out "check passed"; no_fastboot; reads_only
end

begin x50_download_z9x_file_name x50 net
x50_download x "$ZIMG"
inst '' --ip "$ADDR" --image "$C/inst/$ZIMG" check
expect_rc 1; expect_out "$ZIMG is not an image for the $XNAME (its name must contain '$XTOK'). Nothing was written."
no_fastboot; reads_only
end

begin x50_download_signed_with_z9x_key x50 net
x50_download z
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "SHA256SUMS is not signed with the Lumen OS key for the $XNAME: do not install this image"
no_fastboot; reads_only
end

begin x50_download_z9x_content x50 net
x50_download x
# shellcheck disable=SC2086
content $ZPROPS_IMG
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "$XIMG is not Lumen OS for the $XNAME ($XLP=''). Nothing was written."
no_fastboot; reads_only
end

begin x50_running_z9x_lumen x50 net
x50_download x
printf '%s\n' "$ZVP=1.0.9" ro.z9x.gms=0 >> "$FB/props"
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "this projector runs Lumen OS for another device ('$XFAM'). Nothing was done."
expect_no_out "XGIMI stock"; no_fastboot; reads_only
end

# ---------------------------------------------------------------- 2. an X50 image never reaches a G0082
begin x50_download_on_z9x z9x net
x50_download x
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "this is not an $XNAME (model '$ZMODEL', board '$ZBOARD'). Nothing was done."
expect_out "It is an $ZNAME: it installs over the USB cable with the installer of the $ZNAME release (see its guide)."
no_fastboot; reads_only
end

begin z9x_download_x50_file_name z9x usb
z9x_download z "$XIMG"
inst '' check
expect_rc 1; expect_out "this image is for the $XNAME, not for the $ZNAME. Nothing was written."
no_fastboot; reads_only
end

begin x50_download_given_device_z9x z9x usb
x50_download x
inst '' --device z9x check
expect_rc 1; expect_out "this download of the installer is for the $XNAME, not for the $ZNAME"
grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
no_fastboot
end

begin z9x_download_signed_with_x50_key z9x usb
z9x_download x
inst '' check
expect_rc 1; expect_out "SHA256SUMS is not signed by Lumen OS: do not install this image"
no_fastboot; reads_only
end

begin z9x_download_x50_content z9x usb
z9x_download z
# shellcheck disable=SC2086
content $XPROPS_IMG
inst '' check
expect_rc 1; expect_out "this image is for the $XNAME, not for the $ZNAME. Nothing was written."
expect_no_out "check passed"; no_fastboot; reads_only
end

begin z9x_download_other_device_id z9x usb
z9x_download z
content ro.z9x.device=other ro.z9x.gms=0 "$ZVP=1.0.2"
inst '' check
expect_rc 1; expect_out "$ZIMG is not Lumen OS for the $ZNAME (ro.z9x.device='other'). Nothing was written."
no_fastboot; reads_only
end

begin z9x_download_z9x_content_ok z9x usb
z9x_download z
# shellcheck disable=SC2086
content $ZPROPS_IMG
inst '' check
expect_rc 0; expect_out "check passed. Nothing was written"; expect_out "$ZNAME $SER"
no_fastboot; reads_only
end

# ---------------------------------------------------------------- 3. the X50 way itself
begin x50_check_stock x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
inst '' --ip "$ADDR" check
expect_rc 0; expect_out "$XNAME $SER ($ADDR)"; expect_out "SHA256SUMS signature: Lumen OS key for the $XNAME"
expect_out "image: Lumen OS $VER for the $XNAME"; expect_out "check passed. Nothing was written"
no_fastboot; reads_only
end

begin x50_flash_full x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 0; expect_out "fastboot mode: $XNAME $SER, slot $XSL"
F="-s $SER"
want="$F getvar all;$F create-logical-partition z9x_blobs_$XSL $XBS;$F flash z9x_blobs_$XSL backup/$X-blobs-$SER.img;"
want="$want$F flash system_$XSL inst/$XIMG;$F getvar partition-size:system_$XSL;$F getvar all;"
want="$want$XWANT_WIPE$F reboot;"
[ "$(cmds)" = "$want" ] || fail "fastboot commands: got '$(cmds)', want '$want'"
[ "$(wc -c < "$FB/z9x_blobs_$XSL.img" | tr -d ' ')" = "$XBS" ] || fail "z9x_blobs_$XSL is not $XBS bytes"
for k in $XKEEP; do grep -q "^${k}_$XSL [1-9]" "$FB/parts" || fail "${k}_$XSL is gone"; done
# SLOT_CHECK: the empty other slot told and asked before the first write, the slots read again after system, the
# empty other slot told once more at the end; the other slot stays empty
expect_out "Slot $XOSL of the $XNAME holds no system (system_$XOSL is empty, as XGIMI ships it)."
expect_out "(slot-unbootable:$XOSL 'no', slot-retry-count:$XOSL '3')"
expect_out "Write slot $XSL anyway?"
expect_out "slot $XSL stays the one that starts (current-slot $XSL, not unbootable). Slot $XOSL was not touched"
expect_out "Slot $XSL starts Lumen OS. Slot $XOSL still holds no system, and the installer did not switch slots."
expect_out "wiping data (format:$XWFS $XWIPE)"
for p in $XWIPE; do [ -e "$FB/formatted_$p" ] || fail "$p was not formatted"; done
expect_out "$(conf "$X" RESTORE_BIN)"
expect_no_out "no longer marked successful"
for k in system vendor product system_ext; do grep -q "^${k}_$XOSL 0$" "$FB/parts" || fail "${k}_$XOSL is not empty any more"; done
end

# ---------------------------------------------------------------- 4. the slots (SLOT_CHECK of the X50 profile)
[ "$(conf "$X" SLOT_CHECK)" = yes ] || { echo "the $X profile has no SLOT_CHECK=yes"; exit 1; }
begin x50_slot_other_empty_no x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
inst 'ERASE\n\nno\n' --ip "$ADDR" flash
expect_rc 1; expect_out "Write slot $XSL anyway?"
expect_out "cancelled. Nothing was written. The projector starts its old system."
[ "$(cmds)" = "-s $SER getvar all;-s $SER reboot;" ] || fail "fastboot commands: $(cmds)"
end

begin x50_slot_other_holds_system x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
sed -i.bak "s/^system_$XOSL .*/system_$XOSL 942546944/" "$FB/parts"
inst 'ERASE\n\n' --ip "$ADDR" flash
expect_rc 0; expect_out "slot $XOSL: system_$XOSL holds a system (898 MB). It stays as it is"
expect_no_out "anyway?"; expect_no_out "still holds no system"
expect_out "slot $XSL stays the one that starts"
grep -q "^system_$XOSL 942546944$" "$FB/parts" || fail "system_$XOSL changed"
grep -q "format:$XWFS userdata" "$FB/fb.log" || fail "no wipe"
end

begin x50_slot_running_unbootable x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
sed -i.bak "s/^(bootloader) slot-unbootable:$XSL:no/(bootloader) slot-unbootable:$XSL:yes/" "$FB/vars"
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 1; expect_out "the boot loader marks slot $XSL, the one to be written, unbootable (slot-unbootable:$XSL yes)."
expect_out "Nothing was written."
[ "$(cmds)" = "-s $SER getvar all;-s $SER reboot;" ] || fail "fastboot commands: $(cmds)"
end

begin x50_slot_after_current_changed x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
sed "s/^(bootloader) current-slot:.*/(bootloader) current-slot:$XOSL/" "$FB/vars" > "$FB/vars_after"
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 1; expect_out "after the write fastboot mode reports current-slot '$XOSL'"
expect_out "nothing more is written (no wipe, no restart)"
F="-s $SER"
[ "$(cmds)" = "$F getvar all;$F create-logical-partition z9x_blobs_$XSL $XBS;$F flash z9x_blobs_$XSL backup/$X-blobs-$SER.img;$F flash system_$XSL inst/$XIMG;$F getvar partition-size:system_$XSL;$F getvar all;" ] \
  || fail "fastboot commands: $(cmds)"
[ ! -e "$FB/formatted_userdata" ] || fail "the data was wiped"
[ "$(cat "$FB/mode")" = fastboot ] || fail "it left fastboot mode"
# a second run continues in fastboot mode and stops again, never a wipe or a restart
inst 'ERASE\n' --ip "$ADDR" flash
expect_rc 1; expect_out "the projector stays in fastboot mode"
grep -qE "format|reboot" "$FB/fb.log" && fail "a wipe or a restart: $(cmds)"
end

begin x50_slot_after_other_changed x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
# the fake changes the other slot's system while system is written (the installer never writes it: the check after
# system must see it)
cat > "$FB/hook" <<EOF
awk '\$1 == "system_$XOSL" { \$2 = 1048576 } { print }' "$FB/parts" > "$FB/p.hook" && mv "$FB/p.hook" "$FB/parts"
EOF
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 1; expect_out "system_$XOSL of 1048576 bytes (before the write: slot $XSL, 0 bytes)"
grep -qE "format|reboot" "$FB/fb.log" && fail "a wipe or a restart: $(cmds)"
end

begin x50_slot_after_successful_lost x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
sed "s/^(bootloader) slot-successful:$XSL:yes/(bootloader) slot-successful:$XSL:no/" "$FB/vars" > "$FB/vars_after"
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 0
expect_out "slot $XSL is no longer marked successful (slot-successful:$XSL 'no'): the boot loader now counts its starts down (slot-retry-count:$XSL '2')"
expect_out "it switches to slot $XOSL, which holds no system: then only the stock USB restore $(conf "$X" RESTORE_BIN) helps"
grep -q "format:$XWFS userdata" "$FB/fb.log" || fail "no wipe"
end

begin x50_slot_after_mark_only_single x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
# a fastbootd that leaves slot-successful out of 'getvar all' after the write: asked for the written slot alone
grep -v "slot-successful:" "$FB/vars" > "$FB/vars_after"
printf '%s\n' "(bootloader) slot-successful:$XSL:yes" > "$FB/single"
inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 0; expect_no_out "no longer marked successful"
grep -qx -- "-s $SER getvar slot-successful:$XSL" "$FB/fb.log" || fail "the written slot's mark was not asked: $(cmds)"
end

begin x50_slot_check_bad_value x50 net
x50_download x
sed -i.bak "s/^$X\.SLOT_CHECK=.*/$X.SLOT_CHECK=no/" "$C/inst/lib/devices.conf"
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "internal: lib/devices.conf: $X.SLOT_CHECK"
no_fastboot
end


begin x50_fastbootd_not_the_family_product x50 net
x50_download x
sed -i.bak "s/^(bootloader) product:.*/(bootloader) product:other5877/" "$FB/vars"
inst 'ERASE\n\n' --ip "$ADDR" flash
expect_rc 1; expect_out "fastboot mode check failed: fastbootd is not the one of the $XNAME (other5877)."
[ "$(cmds)" = "-s $SER getvar all;-s $SER reboot;" ] || fail "fastboot commands: $(cmds)"
end

k1=$(printf '%s\n' $XKEEP | sed -n 2p)
begin "x50_keep_${k1}_missing" x50 net
x50_download x
sed -i.bak "s/^${k1}_$XSL .*/${k1}_$XSL 0/" "$FB/parts"
inst 'ERASE\n\n' --ip "$ADDR" flash
expect_rc 1; expect_out "the partition ${k1}_$XSL is missing"
[ "$(cmds)" = "-s $SER getvar all;-s $SER reboot;" ] || fail "fastboot commands: $(cmds)"
end

begin x50_no_make_f2fs x50 net
x50_download x
# shellcheck disable=SC2086
content $XPROPS_IMG
PT_BIN=$W/pt_nof2fs inst 'ERASE\n\nyes\n' --ip "$ADDR" flash
expect_rc 1; expect_out "make_f2fs missing next to fastboot ($W/pt_nof2fs): use the official Platform-Tools zip. Nothing was done."
no_fastboot; reads_only
end

begin x50_wipe_fs_bad_value x50 net
x50_download x
sed -i.bak "s/^$X\.WIPE_FS=.*/$X.WIPE_FS=xfs/" "$C/inst/lib/devices.conf"
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "internal: lib/devices.conf: $X.WIPE_FS"
no_fastboot
end

begin x50_stick_not_yet x50 net
inst '' --device "$X" stick "$C"
expect_rc 1; expect_out "'stick' is not available for the $XNAME yet. Nothing was done."
grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
no_fastboot
end

# ---------------------------------------------------------------- the X50 Ultra Max: no installer profile, refused
begin x50max_on_x50_download x50max net
x50_download x
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "'$MMODEL'"
expect_no_out "check passed"; no_fastboot; reads_only
end

begin x50max_on_z9x_download x50max usb
z9x_download z
inst '' check
expect_rc 1; expect_out "'$MMODEL'"
expect_no_out "check passed"; no_fastboot; reads_only
end

begin x50max_device_option x50max net
inst '' --device "$MID" check
expect_rc 1; expect_out "--device"
grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
no_fastboot
end

# ---------------------------------------------------------------- the RS30 Ultra Max: a profile that is not finished
rs30_download() {  # the RS30 download: release.conf of its id, its image signed with the test key x as its CERT
  printf 'DEVICE=%s\nVERSION=%s\n' "$RID" "$VER" > "$C/inst/release.conf"
  cp "$W/keys/x.pem" "$C/inst/$RCERT"
  mkimg "$C/inst/lumen-os-$RTOK-$VER-nogms-system.img"; sign "$C/inst" x
}
RPROPS_IMG="$RLP ro.z9x.gms=0 $XVP=$VER ro.adb.secure=1"
RPROPS_IMG=$(printf '%s' "$RPROPS_IMG" | sed 's/ /=/')

begin rs30_flash_refused rs30 net
rs30_download
inst '' --device "$RID" --ip "$ADDR" flash
expect_rc 1; expect_out "Lumen OS for the $RNAME cannot be installed from a computer yet. Nothing was written."
grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
no_fastboot
end

begin rs30_all_refused rs30 net
rs30_download
inst 'y\ny\ny\n' --device "$RID" --ip "$ADDR"
expect_rc 1; expect_out "cannot be installed from a computer yet"
grep -qvx version "$FB/adb.log" && fail "adb was used: $(tr '\n' ';' < "$FB/adb.log")"
no_fastboot
end

begin rs30_check_reads_then_stops rs30 net
rs30_download
# shellcheck disable=SC2086
content $RPROPS_IMG ro.system.product.cpu.abilist=armeabi-v7a,armeabi
inst '' --device "$RID" --ip "$ADDR" check
expect_rc 1; expect_out "$RNAME $SER ($ADDR)"; expect_out "image: Lumen OS $VER for the $RNAME"
expect_out "cannot be installed from a computer yet"
expect_no_out "check passed"; no_fastboot; reads_only
[ ! -s "$C/state/$RID/state" ] || ! grep -q '^step_check=done' "$C/state/$RID/state" || fail "the check was kept as a step done"
end

begin rs30_arm64_image_refused rs30 net
rs30_download
# shellcheck disable=SC2086
content $RPROPS_IMG ro.system.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
inst '' --device "$RID" --ip "$ADDR" check
expect_rc 1; expect_out "The file lumen-os-$RTOK-$VER-nogms-system.img does not fit the $RNAME. The projector would not start with it."
expect_no_out "check passed"; no_fastboot; reads_only
end

begin rs30_unit_given_x50_download rs30 net
x50_download x
inst '' --ip "$ADDR" check
expect_rc 1; expect_out "It is an $RNAME. Lumen OS for it is not ready yet."
expect_no_out "check passed"; no_fastboot; reads_only
end

echo "installer x50 / z9x separation: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
