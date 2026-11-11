#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# installer_x50_image.sh: the installer (installer/lumen-install.sh, this tree, a copy) against a REAL signed X50
# test image: the X50 profile accepts it, the Z9X way refuses it. Fake projectors only (adb / fastboot stubs as
# tools/devices/test/installer_x50.sh), nothing is written anywhere but a temp dir. On the Mac or Linux:
#   bash tools/devices/test/installer_x50_image.sh DIR
# DIR holds lumen-os-x50-<tN>-test-system.img (the composer's system_x50-tN-net.img under the name the signed list
# gives it), SHA256SUMS naming it and SHA256SUMS.sig made with the g0111 ota key (devices/g0111/image/
# sign_x50_test.sh). The real certificates stay in the copy (certs/ota.x509.pem = the Z9X release key,
# certs/x50/ota.x509.pem = the X50 one) and the image's content is read with the installer's own dump.erofs
# (installer/tools/erofs-utils, checked against lib/erofs_utils.txt). Cases:
#   x50_test_build_accepted    --unsigned-image (your own test build) on a stock G0111 over the network:
#                              the signature of SHA256SUMS is checked with the X50 key and passes, the sha256
#                              matches, the image says ro.lumen.device=g0111, check passes, nothing written
#   x50_repo_installer_device_opt  the same with the installer folder of this tree (release.conf of the Z9X) and
#                              --device x50: a warning that an own image is used, then the same checks pass
#   x50_release_way_refuses    the same file without --unsigned-image: not a release file (no -nogms-)
#   x50_sums_tampered          SHA256SUMS changed by one byte: the X50 signature check stops
#   x50_on_a_z9x               the X50 download on a G0082: not an X50
#   z9x_by_name                the Z9X way (a G0082 on the cable) with this file: the image is for the X50
#   z9x_renamed_signature      the same bytes under the Z9X release name next to the X50-signed SHA256SUMS:
#                              not signed by the Z9X key
#   z9x_renamed_unsigned       renamed, --unsigned-image, no SHA256SUMS, dump.erofs on PATH: its build.prop names
#                              the X50 (LUMEN_PROP of the x50 profile): stop
# Exit 0 only when every case passes.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
INST=$G/installer
CONF=$INST/lib/devices.conf
D=$(cd "${1:?usage: installer_x50_image.sh DIR}" && pwd) || exit 1
IMGN=$(cd "$D" && ls lumen-os-x50-*-test-system.img 2>/dev/null | head -n 1)
[ -n "$IMGN" ] && [ -f "$D/SHA256SUMS" ] && [ -f "$D/SHA256SUMS.sig" ] \
  || { echo "$D: needs lumen-os-x50-<tN>-test-system.img, SHA256SUMS and SHA256SUMS.sig"; exit 1; }
[ -d "$INST/tools/erofs-utils" ] || { echo "no installer/tools/erofs-utils in this tree (copy it from the main checkout)"; exit 1; }
W=$(mktemp -d "${TMPDIR:-/tmp}/lumen_x50img.XXXXXX") && W=$(cd "$W" && pwd) || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0; FAIL=0; CFAIL=0; CASE=
SER=CL45TEST0001
ADDR=192.0.2.62:5555
conf() { awk -v k="$1.$2=" 'index($0, k) == 1 { print substr($0, length(k) + 1) }' "$CONF"; }
X=x50
XNAME=$(conf $X NAME); XMODEL=$(conf $X MODEL); XBOARD=$(conf $X BOARD); XFW=$(conf $X VENDOR_OK | awk '{print $1}')
ZNAME=$(conf z9x NAME); ZMODEL=$(conf z9x MODEL); ZBOARD=$(conf z9x BOARD); ZFW=$(conf z9x VENDOR_OK | awk '{print $1}')
FBP=$(conf z9x FB_PRODUCT); XSL=a
case $(uname -s) in Darwin) PLAT=darwin ;; *) case $(uname -m) in aarch64|arm64) PLAT=linux-aarch64 ;; *) PLAT=linux-x86_64 ;; esac ;; esac
SUM=$(awk -v n="$IMGN" '$2 == n { print $1 }' "$D/SHA256SUMS")
[ -n "$SUM" ] || { echo "SHA256SUMS does not name $IMGN"; exit 1; }

mkdir -p "$W/bin" "$W/bin_dump"
cat > "$W/bin/adb" <<'EOF'
#!/bin/sh
# fake adb of one projector: on the USB cable ($FB/usb) or over the network ($FB/addr); getprop from $FB/props
case "$1" in start-server|kill-server) exit 0 ;; esac
echo "$*" >> "$FB/adb.log"
case "$1" in
  version) echo "Android Debug Bridge version 1.0.41"; echo "Version 35.0.2-12147458"; exit 0 ;;
  connect) if [ "$2" = "$(cat "$FB/addr" 2>/dev/null)" ]; then
             grep -qxF "$2" "$FB/connected" && { echo "already connected to $2"; exit 0; }
             echo "$2" >> "$FB/connected"; echo "connected to $2"; exit 0
           fi
           echo "failed to connect to '$2': Connection refused"; exit 1 ;;
  disconnect) grep -vxF "$2" "$FB/connected" > "$FB/c.tmp"; mv "$FB/c.tmp" "$FB/connected"; exit 0 ;;
  devices) echo "List of devices attached"
           [ -e "$FB/usb" ] && printf '%s\tdevice\n' "$FB_SERIAL"
           while read -r t; do [ -n "$t" ] && printf '%s\tdevice\n' "$t"; done < "$FB/connected"
           echo; exit 0 ;;
  mdns) echo "List of discovered mdns services"; exit 0 ;;
  -s) T=$2; shift 2 ;;
  *) echo "UNEXPECTED $*" >> "$FB/unexpected"; exit 1 ;;
esac
if [ "$T" = "$FB_SERIAL" ]; then [ -e "$FB/usb" ] || { echo "error: device '$T' not found" >&2; exit 1; }
else grep -qxF "$T" "$FB/connected" || { echo "error: device '$T' not found" >&2; exit 1; }; fi
[ "$1" = get-state ] && { echo device; exit 0; }
gp() { awk -v k="$1" 'index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' "$FB/props"; }
case "$1" in
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
# fake fastboot: the projector never is in fastboot mode here (check only); anything but these is a failure
echo "$*" >> "$FB/fb.log"
[ "$1" = --version ] && { echo "fastboot version 35.0.2-12147458"; exit 0; }
[ "$1" = devices ] && exit 0
echo "UNEXPECTED fastboot $*" >> "$FB/unexpected"; exit 1
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
# the installer's own dump.erofs (sha256 of lib/erofs_utils.txt), for the Z9X way that looks for one on PATH
want=$(awk -v p="$PLAT/dump.erofs" '$2 == p { print $1 }' "$INST/lib/erofs_utils.txt")
got=$(shasum -a 256 "$INST/tools/erofs-utils/$PLAT/dump.erofs" 2>/dev/null | cut -d' ' -f1)
[ -n "$want" ] && [ "$want" = "$got" ] || { echo "installer/tools/erofs-utils/$PLAT/dump.erofs is not the one of lib/erofs_utils.txt"; exit 1; }
cp "$INST/tools/erofs-utils/$PLAT/dump.erofs" "$W/bin_dump/dump.erofs"; chmod 755 "$W/bin_dump/dump.erofs"

# begin case device way [rel]: a fresh installer copy (real certificates, own erofs-utils), a fake projector
begin() {
  CASE=$1; CFAIL=0
  C=$W/$1; rm -rf "$C"; mkdir -p "$C/inst" "$C/fb" "$C/state" "$C/backup" "$C/img"
  FB=$C/fb
  cp "$INST/lumen-install.sh" "$C/inst/"; cp -R "$INST/lib" "$INST/certs" "$C/inst/"
  mkdir -p "$C/inst/tools"; cp -R "$INST/tools/erofs-utils" "$C/inst/tools/"
  case "${4:-x50}" in
    x50) printf 'BLOBS=partition\nDEVICE=%s\nVERSION=t0-test\n' "$X" ;;
    z9x) printf 'BLOBS=partition\nVERSION=1.0.9\n' ;;
  esac > "$C/inst/release.conf"
  case "$2" in
    z9x) printf '%s\n' "ro.boot.xgimi.modelname=$ZMODEL" "ro.boot.hardware=$ZBOARD" "ro.vendor.build.version.incremental=$ZFW" ;;
    x50) printf '%s\n' "ro.boot.xgimi.modelname=$XMODEL" "ro.boot.hardware=$XBOARD" "ro.vendor.build.version.incremental=$XFW" ;;
  esac > "$FB/props"
  printf '%s\n' "ro.serialno=$SER" "ro.boot.slot_suffix=_$XSL" ro.virtual_ab.enabled=true ro.build.type=user ro.debuggable=0 \
    sys.boot_completed=1 >> "$FB/props"
  case "$3" in usb) touch "$FB/usb" ;; net) echo "$ADDR" > "$FB/addr" ;; esac
  printf '%s\n' control "system_$XSL" "vendor_$XSL" "product_$XSL" "system_ext_$XSL" > "$FB/mapper"
  : > "$FB/connected"; : > "$FB/adb.log"; : > "$FB/fb.log"
}
link() {  # name [nosums]: the real image (hard link, no copy) in the case's image folder under that name
  ln "$D/$IMGN" "$C/img/$1" 2>/dev/null || ln -s "$D/$IMGN" "$C/img/$1"
  [ "${2:-}" = nosums ] || cp "$D/SHA256SUMS" "$D/SHA256SUMS.sig" "$C/img/"
}
inst() {  # answers path-extra args... -> the installer copy; exit status in RC, output in $C/out
  local ans=$1 px=$2; shift 2
  printf '%b' "$ans" | env LUMEN_PLATFORM_TOOLS="$W/bin" PATH="$W/bin$px:$PATH" FB="$FB" FB_SERIAL=$SER \
    LUMEN_STATE_DIR="$C/state" LUMEN_BACKUP_DIR="$C/backup" LUMEN_EROFS_UTILS= LANG=en_US.UTF-8 \
    bash "$C/inst/lumen-install.sh" "$@" > "$C/out" 2>&1
  RC=$?
}
fail() { echo "  FAIL [$CASE] $*"; CFAIL=1; }
expect_rc() { [ "$RC" = "$1" ] || fail "exit $RC, want $1"; }
expect_out() { grep -qF -- "$1" "$C/out" || fail "output lacks '$1'"; }
expect_no_out() { if grep -qF -- "$1" "$C/out"; then fail "output has '$1'"; fi; }
reads_only() {
  if grep -E ' (reboot|root|push|install|uninstall|am)( |$)| shell (echo|rm|setprop|svc|dd) ' "$FB/adb.log" | grep -q .; then
    fail "adb wrote: $(tr '\n' ';' < "$FB/adb.log")"
  fi
  if grep -v -e '^--version$' -e '^devices$' "$FB/fb.log" | grep -q .; then fail "fastboot used: $(tr '\n' ';' < "$FB/fb.log")"; fi
}
end() {
  [ ! -s "$FB/unexpected" ] || fail "unexpected: $(cat "$FB/unexpected")"
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $CASE"; sed 's/^/     | /' "$C/out" | tail -10; fi
}
VER=$(sed -n 's/^lumen-os-x50-\(.*\)-test-system\.img$/\1/p' <<< "$IMGN")
ZREL=lumen-os-1.0.9-nogms-system.img

# ---------------------------------------------------------------- the X50 way
begin x50_test_build_accepted x50 net
link "$IMGN"
inst '' "" --ip "$ADDR" --unsigned-image --image "$C/img/$IMGN" check
expect_rc 0; expect_out "$XNAME $SER ($ADDR)"; expect_out "SHA256SUMS signature: Lumen OS key for the $XNAME"
expect_out "$IMGN sha256 ${SUM:0:16}"; expect_out "image: Lumen OS x50-$VER for the $XNAME"
expect_out "check passed; nothing was written"; expect_no_out "Type UNSIGNED"
reads_only
end

begin x50_repo_installer_device_opt x50 net z9x
link "$IMGN"
inst '' "" --device "$X" --ip "$ADDR" --unsigned-image --image "$C/img/$IMGN" check
expect_rc 0; expect_out "your own image is used (--unsigned-image)"
expect_out "SHA256SUMS signature: Lumen OS key for the $XNAME"; expect_out "image: Lumen OS x50-$VER for the $XNAME"
expect_out "check passed; nothing was written"
reads_only
end

begin x50_release_way_refuses x50 net
link "$IMGN"
inst '' "" --ip "$ADDR" --image "$C/img/$IMGN" check
expect_rc 1; expect_out "$IMGN is not a release file"; expect_no_out "check passed"
reads_only
end

begin x50_sums_tampered x50 net
link "$IMGN"
python3 -c 'import sys; p = sys.argv[1]; b = bytearray(open(p, "rb").read()); b[0] = ord("0") if b[0] != ord("0") else ord("1"); open(p, "wb").write(bytes(b))' "$C/img/SHA256SUMS"
inst '' "" --ip "$ADDR" --unsigned-image --image "$C/img/$IMGN" check
expect_rc 1; expect_out "SHA256SUMS is not signed with the Lumen OS key for the $XNAME: do not install this image"
expect_no_out "check passed"; reads_only
end

begin x50_on_a_z9x z9x net
link "$IMGN"
inst '' "" --ip "$ADDR" --unsigned-image --image "$C/img/$IMGN" check
expect_rc 1; expect_out "this is not an $XNAME (model '$ZMODEL', board '$ZBOARD'). Nothing was done."
reads_only
end

# ---------------------------------------------------------------- the Z9X way
begin z9x_by_name z9x usb z9x
link "$IMGN"
inst '' "" --image "$C/img/$IMGN" check
expect_rc 1; expect_out "this image is for the $XNAME, not for the $ZNAME. Nothing was written."
expect_no_out "check passed"; reads_only
end

begin z9x_renamed_signature z9x usb z9x
link "$ZREL"
inst '' "" --image "$C/img/$ZREL" check
expect_rc 1; expect_out "SHA256SUMS is not signed by Lumen OS: do not install this image"
expect_no_out "check passed"; reads_only
end

begin z9x_renamed_unsigned z9x usb z9x
link "$ZREL" nosums
inst 'UNSIGNED\n' ":$W/bin_dump" --unsigned-image --image "$C/img/$ZREL" check
expect_rc 1; expect_out "this image is for the $XNAME, not for the $ZNAME. Nothing was written."
expect_no_out "check passed"; reads_only
end

echo "installer_x50_image ($IMGN, sha256 ${SUM:0:16}...): $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
