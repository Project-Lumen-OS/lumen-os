#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# The trial-run path of the one blobs reader tools/ota/image/z9x_blobs.sh on the REAL stock dumps of every model, with
# each model's real settings and allow-list: Z9X G0082 6.15.19 / 6.15.58 / 6.15.62, Z9X HB G0130 6.16.24 and G0148
# 6.17.12, X50 Ultra G0111 1.2.64, RS30 Ultra Max G0155 1.1.42, TITAN Noir Pro H0062 1.2.99 and 1.2.54. Runs where
# the dumps are (xgimi-builder ~/devices); nothing of a dump is ever copied out of that host or into the tree:
#   sh tools/devices/test/blobs_stock_real_test.sh [DUMPS_DIR]      (default ~/devices; no dumps: skip, exit 0)
# Every case gets a fake root (Z9X_ROOT) as a trial run on stock slot A sees it: dev/block/mapper/<partition>_a is a
# pointer to the dump's partition folder, which this test's mount stub links read-only into the reader's mount point
# (cp -al: the dump's own files, layout, symlinks and APEX files; a loop mount of an APEX payload is debugfs rdump);
# /vendor/etc has a copy of the dump's audio policy; every allow-list target is a 0-byte placeholder or the GSI's own
# stand-in. Each case: sys.z9x.blobs=ok, every allow-listed file bound with its allow-listed (or that firmware's alt=)
# sha256, sys.lumen.nui=ok where the model has the eye-protection pair, no stock mount left, no reader output, and no
# file of the dump modified (find -newer). Plus refusals on real data: the Z9X image on an HB dump (not its model), the
# HB settings on a Z9X dump (no flat VNDK APEX there: the VNDK lib stays out, fail closed).
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
D=${1:-$HOME/devices}
READER=$G/tools/ota/image/z9x_blobs.sh
[ -d "$D/g0082_mt9952" ] || { echo "blobs_stock_real_test: skip (no stock dumps in $D)"; exit 0; }
PATH=$PATH:/usr/sbin:/sbin
command -v debugfs >/dev/null 2>&1 && command -v unzip >/dev/null 2>&1 || { echo "need debugfs and unzip"; exit 1; }
mkdir -p "$HOME/scratch" && W=$(mktemp -d "$HOME/scratch/blobs_real.XXXXXX") || exit 1   # next to the dumps: cp -al
trap 'rm -rf "$W"' EXIT
BIN=$W/bin; mkdir -p "$BIN"
for f in getprop setprop log timeout chcon chown blockdev; do cp "$G/tools/ota/test/stubs/$f" "$BIN/$f" && chmod 755 "$BIN/$f"; done
cat > "$BIN/mount" <<'EOF'
#!/bin/sh
# test stub. 'mount --bind SRC DST': records "<sha256 of SRC> DST". 'mount -t TYPE -o OPTS SRC DIR' (read-only
# only): SRC a pointer file "dump <folder>": that folder hard-linked into DIR; with loop, SRC an APEX payload (ext4):
# its lib and lib64 dumped into DIR with debugfs
echo "mount $*" >> "$Z9X_T/mount.log"
if [ "$1" = -t ] && [ "$3" = -o ]; then
  case ",$4," in *,ro,*) ;; *) echo "mount: $6: not read-only" >&2; exit 1 ;; esac
  [ -f "$5" ] && [ -d "$6" ] || exit 1
  case ",$4," in
    *,loop,*) [ "$2" = ext4 ] || exit 1
      for l in lib lib64 apex_manifest.pb; do debugfs -R "rdump /$l $6" "$5" >/dev/null 2>&1; done
      [ -e "$6/lib" ] || exit 1 ;;
    *) read -r k src < "$5" && [ "$k" = dump ] && [ -d "$src" ] || exit 1
      cp -al "$src/." "$6/" || exit 1 ;;
  esac
  echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts"; echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts.log"
  exit 0
fi
[ "$1" = --bind ] || exit 0
[ -e "$2" ] && [ -e "$3" ] || { echo "mount: $3: No such file or directory" >&2; exit 1; }
printf '%s %s\n' "$(sha256sum "$2" | cut -d' ' -f1)" "$3" >> "$Z9X_T/binds"
EOF
cat > "$BIN/umount" <<'EOF'
#!/bin/sh
echo "umount $*" >> "$Z9X_T/umount.log"
grep -q " $1\$" "$Z9X_T/fsmounts" 2>/dev/null || { echo "umount: $1: not mounted" >&2; exit 1; }
rm -rf "$1" && mkdir "$1"
grep -v " $1\$" "$Z9X_T/fsmounts" > "$Z9X_T/fsmounts.tmp"; mv "$Z9X_T/fsmounts.tmp" "$Z9X_T/fsmounts"
EOF
chmod 755 "$BIN/mount" "$BIN/umount"

PASS=0; FAIL=0; CFAIL=0; CASE=
fail() { echo "  FAIL [$CASE] $*"; CFAIL=1; }
prop() { Z9X_T=$T "$BIN/getprop" "$1"; }
expect_prop() { [ "$(prop "$1")" = "$2" ] || fail "$1='$(prop "$1")', want '$2'"; }

# real ID MODEL FS SYSROOT CONF ALLOW IMGALLOW: one trial run of the reader on that dump
real() {
  CASE=$1; CFAIL=0; mdl=$2; fs=$3; sysroot=$4; conf=$5; allow=$6; inimg=$7
  T=$W/$CASE; R=$T/root
  if [ ! -d "$fs" ]; then echo "skip $CASE (no $fs)"; return 1; fi
  mkdir -p "$R/system/etc/z9x" "$R/dev/block/mapper" "$R/mnt" "$R/vendor/etc"
  cp "$conf" "$R/system/etc/z9x/blobs.conf"; cp "$allow" "$R/system/etc/z9x/$inimg"
  echo "dump $sysroot" > "$R/dev/block/mapper/system_a"
  [ -d "$fs/system_ext" ] && echo "dump $fs/system_ext" > "$R/dev/block/mapper/system_ext_a"
  [ -d "$fs/product" ] && echo "dump $fs/product" > "$R/dev/block/mapper/product_a"
  cp "$fs/vendor/etc/audio_policy_configuration.xml" "$R/vendor/etc/audio_policy_configuration.xml"
  awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { t = "/system/" $2; for (i = 4; i <= NF; i++) if ($i ~ /^bind=/) t = substr($i, 6); print t }' "$allow" |
    while read -r t; do
      mkdir -p "$(dirname "$R$t")"
      case "$t" in /system/*) : > "$R$t" ;; /vendor/*) ;; *) echo "the GSI's own $t" > "$R$t" ;; esac
    done
  fw=$(sed -n 's/^ro.vendor.build.version.incremental=//p' "$fs/vendor/build.prop")
  printf '%s\n' ro.gsid.image_running=1 ro.boot.slot_suffix=_a "ro.boot.xgimi.modelname=$mdl" \
    "ro.vendor.build.version.incremental=$fw" "ro.lumen.models=${MODELS:-$mdl}" > "$T/props"
  : > "$T/binds"; : > "$T/fsmounts"; : > "$T/fsmounts.log"; : > "$T/logcat"; : > "$T/setprop.log"; : > "$T/chcon.log"
  : > "$T/blockdev.log"; : > "$T/mount.log"
  touch "$T/before"; sleep 1
  env Z9X_ROOT="$R" Z9X_T="$T" PATH="$BIN:$PATH" sh "$READER" > "$T/out" 2>&1
  [ ! -s "$T/out" ] || fail "reader output: $(cat "$T/out")"
  [ ! -s "$T/fsmounts" ] || fail "still mounted: $(cat "$T/fsmounts")"
  [ ! -e "$R/mnt/z9x_stock" ] || fail "the stock scratch folder is left"
  awk '$2 !~ /(^|,)ro(,|$)/' "$T/fsmounts.log" | grep . && fail "a stock mount was not read-only"
  ch=$(find "$fs" -newer "$T/before" -print 2>/dev/null | head -n 3)
  [ -z "$ch" ] || fail "dump files modified: $ch"
  return 0
}
# every allow-list line bound over its target with the base sha256 or, where given, that firmware's alt= one
expect_all_bound() {
  awk -v fw="$fw" '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 {
      t = "/system/" $2; h = $1; a = ""
      for (i = 4; i <= NF; i++) { if ($i ~ /^bind=/) t = substr($i, 6); if (index($i, "alt=" fw ":") == 1) a = substr($i, length(fw) + 6) }
      print h, (a == "" ? "-" : a), t }' "$allow" |
    while read -r h a t; do
      grep -qx "$h $R$t" "$T/binds" || { [ "$a" != - ] && grep -qx "$a $R$t" "$T/binds"; } || echo "$t not bound with its file"
    done > "$T/miss"
  [ ! -s "$T/miss" ] || fail "$(cat "$T/miss") (binds: $(sed "s|$R||" "$T/binds" | tr '\n' ' '))"
  want=$(awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3' "$allow" | grep -c .)
  [ "$(grep -c . "$T/binds" | tr -d ' ')" = "$want" ] || fail "$(grep -c . "$T/binds") binds, want $want"
}
end() {
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $CASE ($fw: $(grep -c . "$T/binds" | tr -d ' ') files bound)"
  else FAIL=$((FAIL + 1)); echo "FAIL $CASE"; sed 's/^/     | /' "$T/logcat" | tail -8; fi
}

Z9XC=$G/tools/ota/image/blobs.conf; Z9XA=$G/tools/ota/image/blobs_allow.txt
HBC=$G/devices/goat_elegant/image/blobs.conf; HBA=$G/devices/goat_elegant/image/blobs_allow.txt
X50C=$G/devices/g0111/image/blobs.conf; X50A=$G/devices/g0111/image/x50_blobs_allow.txt
RS30A=$G/devices/g0155/image/rs30_blobs_allow.txt
HBM=$(sed -n 's/^models=//p' "$G/devices/goat_elegant/device.conf")

for v in v61519 v61558 v61562; do
  fs=$D/g0082_mt9952/official/$v/fs
  if real "z9x_$v" G0082 "$fs" "$fs/system" "$Z9XC" "$Z9XA" blobs_allow.txt; then
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.src stock_a; expect_prop sys.lumen.nui ""; expect_all_bound; end
  fi
done
for hb in g0130_mt9660:G0130 g0148_mt9660:G0148; do
  fs=$D/${hb%%:*}/work/fs
  if MODELS=$HBM real "hb_${hb%%:*}" "${hb#*:}" "$fs" "$fs/system" "$HBC" "$HBA" blobs_allow.txt; then
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.src stock_a; expect_all_bound; end
  fi
done
fs=$D/g0111_mt9681/work/fs
if real x50_g0111 G0111 "$fs" "$fs/system_root" "$X50C" "$X50A" x50_blobs_allow.txt; then
  expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_all_bound; end
fi
fs=$D/g0155_mt9681/work/fs
if real rs30_g0155 G0155 "$fs" "$fs/system" "$X50C" "$RS30A" x50_blobs_allow.txt; then
  expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_all_bound; end
fi
for v in v1299 v1254; do
  fs=$D/h0062_mt9681/official/$v/fs
  if real "titan_$v" H0062 "$fs" "$fs/system" "$X50C" "$X50A" x50_blobs_allow.txt; then
    expect_prop sys.z9x.blobs ok; expect_prop sys.lumen.nui ok; expect_all_bound; end
  fi
done
# refusals on real data
fs=$D/g0148_mt9660/work/fs
if MODELS=G0082 real z9x_image_on_hb G0148 "$fs" "$fs/system" "$Z9XC" "$Z9XA" blobs_allow.txt; then
  expect_prop sys.z9x.blobs missing; [ ! -s "$T/binds" ] || fail "bound: $(cat "$T/binds")"
  [ ! -s "$T/fsmounts.log" ] || fail "stock mounted"; end
fi
fs=$D/g0082_mt9952/official/v61562/fs
if MODELS=G0082 real hb_conf_on_z9x G0082 "$fs" "$fs/system" "$HBC" "$HBA" blobs_allow.txt; then
  # the HB's VNDK v33 lib is not on a Z9X and its audio policy rules do not match the Z9X file: nothing bound
  expect_prop sys.z9x.blobs missing:lib/hb_vndk33/libstagefright_foundation.so; [ ! -s "$T/binds" ] || fail "bound: $(cat "$T/binds")"; end
fi
echo "blobs_stock_real_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
