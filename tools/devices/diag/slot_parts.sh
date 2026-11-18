#!/bin/sh
# slot_parts.sh: read-only snapshot of a projector's static per-slot partitions, for support. For every partition of
# the XGIMI sets of apps/core/lumen-fwcopy (boot, dtbo, mboot, tvconfig, tvservice, bootdata, optee, owl, satf, armfw,
# xgimiconfig, vbmeta_system) and vbmeta of both slots it writes size, sha256 and the first 4 KiB in hex (PART lines in
# the format of the installer's report; a partition the model has not: missing), misc the same way (the boot record:
# compare two snapshots of one boot to prove a dry run wrote nothing), plus slot and version
# props, the mounts and the kernel banner of boot_a and boot_b (BANNER lines). With --raw it also copies boot,
# tvconfig, tvservice and bootdata of both slots to the computer and checks their sha256. Nothing on the projector
# is written. Reading the partitions needs root: adbd is switched to
# root when the system allows it (Lumen OS, the trial run) and switched back at the end.
#
#   sh tools/devices/diag/slot_parts.sh [-s SERIAL] [--raw] [OUTDIR]
#
# OUTDIR (default ./slot_parts_<date>) gets slot_parts.txt and, with --raw, <part>_<slot>.img.
set -u
SERIAL=${ANDROID_SERIAL:-}
RAW=0
OUT=
while [ $# -gt 0 ]; do
  case $1 in
    -s) SERIAL=$2; shift 2 ;;
    --raw) RAW=1; shift ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) OUT=$1; shift ;;
  esac
done
[ -n "$OUT" ] || OUT=./slot_parts_$(date +%Y%m%d_%H%M%S)
mkdir -p "$OUT" || exit 1
A() { if [ -n "$SERIAL" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }

reconnect() {   # adbd restarts after root / unroot; over Wi-Fi the client has to connect again
  sleep 6
  case $SERIAL in *:*) adb disconnect "$SERIAL" >/dev/null 2>&1; adb connect "$SERIAL" >/dev/null 2>&1 ;; esac
  A wait-for-device
}

A get-state >/dev/null 2>&1 || { echo "no device (adb ${SERIAL:+-s $SERIAL })"; exit 1; }
ROOTED=0
if [ "$(A shell id -u 2>/dev/null | tr -d '\r')" != "0" ]; then
  if A root 2>&1 | grep -q "restarting adbd as root"; then ROOTED=1; reconnect; fi
fi
restore() { if [ "$ROOTED" = 1 ]; then A unroot >/dev/null 2>&1; reconnect; echo "adbd is back to normal"; fi; }
trap restore EXIT
trap 'exit 1' HUP INT TERM
if [ "$(A shell id -u 2>/dev/null | tr -d '\r')" != "0" ]; then
  echo "no root on this system: the partitions cannot be read here. Run it in the trial run or on Lumen OS."
  exit 1
fi

A shell 'for k in ro.boot.xgimi.modelname ro.xgimi.modelname ro.boot.slot_suffix ro.vendor.build.version.incremental \
    ro.vendor.build.fingerprint ro.build.fingerprint ro.z9x.build_id ro.lumen.version ro.gsid.image_running \
    ro.boot.verifiedbootstate ro.product.ab_ota_partitions; do echo "PROP	$k	$(getprop $k)"; done
  grep -E "tvconfig|tvservice|bootdata|xgimiconfig| /vendor | /mnt/vendor" /proc/mounts | sed "s/^/MOUNT	/"
  for f in /dev/block/by-name/*; do echo "BYNAME	${f##*/}	$(readlink $f)"; done
  for p in boot dtbo mboot tvconfig tvservice bootdata optee owl satf armfw xgimiconfig vbmeta_system vbmeta; do
    for s in _a _b; do
      f=/dev/block/by-name/$p$s
      [ -e $f ] || { echo "PART	$p$s	missing"; continue; }
      z=$(blockdev --getsize64 $f)
      h=$(sha256sum $f | cut -d" " -f1)
      x=$(od -An -v -tx1 -N 4096 $f | tr -d " \n")
      echo "PART	$p$s	bytes=$z sha256=$h	head=$x"
    done
  done
  f=/dev/block/by-name/misc
  [ -e $f ] && echo "PART	misc	bytes=$(blockdev --getsize64 $f) sha256=$(sha256sum $f | cut -d" " -f1)	head=$(od -An -v -tx1 -N 4096 $f | tr -d " \n")"
  for s in _a _b; do
    b=$(dd if=/dev/block/by-name/boot$s bs=1048576 count=40 2>/dev/null | strings -n 20 | grep -m1 "^Linux version [0-9]")
    echo "BANNER	boot$s	$b"
  done' | tr -d '\r' > "$OUT/slot_parts.txt"
grep -c '^PART' "$OUT/slot_parts.txt" | sed 's/^/PART lines: /'
grep -E '^PROP	ro\.(boot\.xgimi\.modelname|boot\.slot_suffix|vendor\.build\.version\.incremental)|^BANNER' "$OUT/slot_parts.txt"

if [ "$RAW" = 1 ]; then
  if command -v sha256sum >/dev/null 2>&1; then SHA="sha256sum"; else SHA="shasum -a 256"; fi
  for p in boot tvconfig tvservice bootdata; do
    for s in _a _b; do
      want=$(awk -F'\t' -v n="$p$s" '$1=="PART" && $2==n {print $3}' "$OUT/slot_parts.txt" | sed 's/.*sha256=//')
      [ -n "$want" ] || continue
      A exec-out "dd if=/dev/block/by-name/$p$s bs=1048576 2>/dev/null" > "$OUT/$p$s.img"
      got=$($SHA "$OUT/$p$s.img" | cut -d' ' -f1)
      if [ "$got" = "$want" ]; then echo "raw $p$s ok"; else echo "raw $p$s: sha256 $got, the projector said $want"; fi
    done
  done
fi
echo "written: $OUT"
