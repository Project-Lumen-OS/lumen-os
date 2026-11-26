#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host tests of the trial-run path of the one blobs reader tools/ota/image/z9x_blobs.sh with the data of the Z9X HB
# (devices/goat_elegant/image/blobs.conf: the flattened VNDK APEX of Android 13, two from= rules for the audio policy of
# stock 6.16.24 and 6.17.12) and of the Z9X (tools/ota/image/blobs.conf: the com.android.vndk.current.apex file, lib and
# lib64, the codec2 libraries). A trial run on stock has no z9x_blobs partition: without these files the HB's vendor
# codec2 plugin does not load, IInterTunnel is never registered and the HWC freezes the picture on the first HDMI frame
# (tester report 2026-11-14, G0148 6.17.12), and neither model has the vendor sound path. The X50 family's trial path:
# devices/g0111/test/x50_blobs_test.sh (dsu_* cases); every model's installed path: tools/devices/test/blobs_parity.py;
# the real stock dumps: tools/devices/test/blobs_stock_real_test.sh. POSIX sh, no projector:
#   sh tools/devices/test/blobs_trial_test.sh          every case under sh, dash, bash and mksh (the ones installed)
#   sh tools/devices/test/blobs_trial_test.sh hb_      only the cases whose name starts with this
# Every case gets a fake root (Z9X_ROOT): the test allow-lists keep the real ones' structure with made-up files (no
# MediaTek / XGIMI file is ever used); the stock slot is dev/block/mapper/system_<slot> (and system_ext_<slot>), tars the
# mount stub extracts; /vendor holds the stock audio policy of one firmware (the from= source and the bind target).
# Stubs: tools/ota/test/stubs (getprop, setprop, log, timeout, chcon, chown, blockdev) and this test's mount / umount.
# Every case fails on any output of the reader. Exit status 0 only when every case passes. Never touches anything
# outside its temp dir.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
READER=$G/tools/ota/image/z9x_blobs.sh
HBIMG=$G/devices/goat_elegant/image
STUBS=$G/tools/ota/test/stubs
ONLY=${1-}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/blobs_trial_test.XXXXXX") || exit 1
trap 'rm -rf "$WORK"' EXIT
BIN=$WORK/bin
mkdir -p "$BIN"
for f in getprop setprop log timeout chcon chown blockdev; do cp "$STUBS/$f" "$BIN/$f" && chmod 755 "$BIN/$f"; done
cat > "$BIN/mount" <<'EOF'
#!/bin/sh
# test stub: 'mount --bind SRC DST' records "<sha256 of SRC> DST" in $Z9X_T/binds; 'mount -t TYPE -o OPTS SRC DIR'
# (a stock partition or APEX payload, a tar here) refuses OPTS without ro, else extracts SRC into DIR and records it
echo "mount $*" >> "$Z9X_T/mount.log"
if [ "$1" = -t ] && [ "$3" = -o ]; then
  case ",$4," in *,ro,*) ;; *) echo "mount: $6: not read-only" >&2; exit 1 ;; esac
  [ "$2" = erofs ] && [ -e "$Z9X_T/no_erofs" ] && exit 1   # an ext4 stock partition
  [ -f "$5" ] && [ -d "$6" ] || exit 1
  tar -xf "$5" -C "$6" 2>/dev/null || exit 1
  echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts"; echo "$2 $4 $5 $6" >> "$Z9X_T/fsmounts.log"
  exit 0
fi
[ "$1" = --bind ] || exit 0
[ -e "$2" ] && [ -e "$3" ] || { echo "mount: $3: No such file or directory" >&2; exit 1; }
printf '%s %s\n' "$(sha256sum "$2" | cut -d' ' -f1)" "$3" >> "$Z9X_T/binds"
EOF
cat > "$BIN/umount" <<'EOF'
#!/bin/sh
# test stub: a stock mount of $Z9X_T/fsmounts is emptied and dropped
echo "umount $*" >> "$Z9X_T/umount.log"
grep -q " $1\$" "$Z9X_T/fsmounts" 2>/dev/null || { echo "umount: $1: not mounted" >&2; exit 1; }
rm -rf "$1" && mkdir "$1"
grep -v " $1\$" "$Z9X_T/fsmounts" > "$Z9X_T/fsmounts.tmp"; mv "$Z9X_T/fsmounts.tmp" "$Z9X_T/fsmounts"
EOF
chmod 755 "$BIN/mount" "$BIN/umount"

# ---------------------------------------------------------------- HB data
# the test allow-list and the fake files: the real list's two lines with fake hashes, the audio policy target T
# (8 lines), the 6.16.24 stock = T plus lines 2-3, the 6.17.12 stock = that plus line 6 (as the real from= rules)
F=$WORK/hb
python3 - "$HBIMG/blobs_allow.txt" "$WORK/hb_allow.txt" "$F" <<'EOF' || exit 1
import hashlib, os, sys
real, out, files = sys.argv[1:4]
os.makedirs(files)
h = lambda b: hashlib.sha256(b).hexdigest()
rows = [l.split() for l in open(real).read().splitlines() if l and not l.startswith("#") and not l.startswith("set=")]
lib, pol = rows
assert lib[1] == "lib/hb_vndk33/libstagefright_foundation.so" and lib[3] == "bind=" + lib[2] and len(lib) == 4, lib
froms = [o for o in pol[3:] if o.startswith("from=")]
assert pol[1] == "etc/xgimi/hb_audio_policy_configuration.xml" and pol[3] == "bind=" + pol[2] and len(froms) == 2, pol
T = b"".join(b"<policy line %d/>\n" % i for i in range(1, 9))
s616 = T.split(b"\n")[0] + b"\n<xgimi sub 1/>\n<xgimi sub 2/>\n" + b"\n".join(T.split(b"\n")[1:])
l = s616.split(b"\n"); s617 = b"\n".join(l[:5] + [b"<dusun include/>"] + l[5:])
lib_b = b"fake MediaTek VNDK libstagefright_foundation for the host test\n"
files_ = {"lib": lib_b, "lib.gsi": b"the GSI's own libstagefright_foundation\n", "pol": T, "pol.616": s616,
          "pol.617": s617, "pol.unknown": s617 + b"<another firmware/>\n", "lib.big": lib_b + b"\0" * (4 << 20)}
for k, v in files_.items():
    open(os.path.join(files, k), "wb").write(v)
with open(out, "w") as o:
    o.write("set=hb-test-a\n")
    o.write(" ".join([h(lib_b)] + lib[1:]) + "\n")
    o.write(" ".join([h(T)] + pol[1:3] + [pol[3], "from=%s:2,3d" % h(s616), "from=%s:2,3d;6d" % h(s617)]) + "\n")
EOF
HBALLOW=$WORK/hb_allow.txt
LIBP=lib/hb_vndk33/libstagefright_foundation.so
LIBT=/apex/com.android.vndk.v33/lib/libstagefright_foundation.so
POLP=etc/xgimi/hb_audio_policy_configuration.xml
POLT=/vendor/etc/audio_policy_configuration.xml
LIBH=$(sha256sum "$F/lib" | cut -d' ' -f1)
POLH=$(sha256sum "$F/pol" | cut -d' ' -f1)
MODELS=$(sed -n 's/^ro\.lumen\.models=//p' "$HBIMG/hb_prop.txt")

# ---------------------------------------------------------------- Z9X data (the shared fixtures)
ZF=$WORK/z9x
mkdir -p "$ZF"
python3 "$G/tools/ota/test/blobs_fixture.py" allow "$G/tools/ota/image/blobs_allow.txt" "$WORK/z9x_allow.txt" "$ZF" || exit 1
ZALLOW=$WORK/z9x_allow.txt
ZSET=$(sed -n 's/^set=//p' "$ZALLOW")
zlines() { awk '$1 !~ /^#/ && $1 !~ /^set=/ && NF >= 3 { t = "/system/" $2; for (i = 4; i <= NF; i++) if ($i ~ /^bind=/) t = substr($i, 6); print $1, $2, t }' "$ZALLOW"; }

PASS=0; FAIL=0; CASE=; CFAIL=0; SHN=-
fail() { echo "  FAIL [$CASE/$SHN] $*"; CFAIL=1; }

# HB stock slot tar: system/apex/<APEXDIR>/{apex_manifest.pb,lib/...}; options: lib=FILE manifest=NAME apexdir=NAME symlink zip
hb_stock() {
  _lib=$F/lib; _man=com.android.vndk.v33; _dir=com.android.vndk.current; _sym=0
  for a in "$@"; do case $a in lib=*) _lib=$F/${a#lib=} ;; manifest=*) _man=${a#manifest=} ;; apexdir=*) _dir=${a#apexdir=} ;; symlink) _sym=1 ;; esac; done
  s=$T/stock; rm -rf "$s"; mkdir -p "$s/system/apex/$_dir/lib"
  printf '\n\024%s' "$_man" > "$s/system/apex/$_dir/apex_manifest.pb"
  if [ $_sym = 1 ]; then cp "$_lib" "$s/system/apex/$_dir/lib/real.so"; ln -s real.so "$s/system/apex/$_dir/lib/libstagefright_foundation.so"
  else cp "$_lib" "$s/system/apex/$_dir/lib/libstagefright_foundation.so"; fi
  (cd "$s" && tar -cf "$R/dev/block/mapper/system_a" system)
  (cd "$T" && sha256sum "$R/dev/block/mapper/system_a" "$R$POLT" > "$T/stock.sha")
}
logs() {
  : > "$T/setprop.log"; : > "$T/logcat"; : > "$T/out"; : > "$T/binds"; : > "$T/mount.log"; : > "$T/fsmounts"
  : > "$T/fsmounts.log"; : > "$T/chcon.log"; : > "$T/blockdev.log"
}
hb_begin() {  # case name: a trial run on stock slot A of a G0148 with stock 6.17.12, no z9x_blobs partition
  CASE=$1; CFAIL=0; STOCKP=system_a
  T=$WORK/$SHN/$CASE; R=$T/root
  mkdir -p "$R/system/etc/z9x" "$R/dev/block/mapper" "$R/mnt" "$R/system/lib/hb_vndk33" "$R/apex/com.android.vndk.v33/lib" "$R/vendor/etc"
  cp "$HBALLOW" "$R/system/etc/z9x/blobs_allow.txt"; cp "$HBIMG/blobs.conf" "$R/system/etc/z9x/blobs.conf"
  cp "$F/lib.gsi" "$R$LIBT"; cp "$F/pol.617" "$R$POLT"
  printf '%s\n' ro.gsid.image_running=1 ro.boot.slot_suffix=_a ro.boot.xgimi.modelname=G0148 \
    ro.vendor.build.version.incremental=v6.17.12 "ro.lumen.models=$MODELS" > "$T/props"
  logs; hb_stock
}
hb_partition() {  # the z9x_blobs_a the installer writes: ustar, MANIFEST + both files, 4 MiB
  python3 - "$R/dev/block/mapper/z9x_blobs_a" "$F" <<'EOF'
import io, sys, tarfile
out, f = sys.argv[1:3]
b = io.BytesIO()
with tarfile.open(fileobj=b, mode="w", format=tarfile.USTAR_FORMAT) as t:
    for name, data in (("MANIFEST", b"set=hb-test-a\n"), ("lib/hb_vndk33/libstagefright_foundation.so", open(f + "/lib", "rb").read()),
                       ("etc/xgimi/hb_audio_policy_configuration.xml", open(f + "/pol", "rb").read())):
        i = tarfile.TarInfo(name); i.size = len(data); i.mode = 0o644; t.addfile(i, io.BytesIO(data))
d = b.getvalue(); open(out, "wb").write(d + b"\0" * ((4 << 20) - len(d)))
EOF
}
z9x_begin() {  # case name [x50_fixture.py stock options]: a trial run on stock slot B of a Z9X (G0082), no partition
  CASE=$1; CFAIL=0; shift; STOCKP=system_b
  T=$WORK/$SHN/$CASE; R=$T/root
  mkdir -p "$R/system/etc/z9x" "$R/dev/block/mapper" "$R/mnt"
  cp "$ZALLOW" "$R/system/etc/z9x/blobs_allow.txt"; cp "$G/tools/ota/image/blobs.conf" "$R/system/etc/z9x/blobs.conf"
  zlines | while read -r h p t; do
    mkdir -p "$(dirname "$R$t")"
    case "$t" in /system/*) : > "$R$t" ;; *) echo "the system's own $t" > "$R$t" ;; esac
  done
  printf '%s\n' ro.gsid.image_running=1 ro.boot.slot_suffix=_b ro.boot.xgimi.modelname=G0082 \
    ro.vendor.build.version.incremental=v6.15.62 ro.lumen.models=G0082 > "$T/props"
  logs
  python3 "$G/devices/g0111/test/x50_fixture.py" stock "$ZF" "$ZALLOW" "$T/lp" "$R" "$@" || fail "stock fixture"
  mv "$T/lp/system.tar" "$R/dev/block/mapper/system_b"; mv "$T/lp/system_ext.tar" "$R/dev/block/mapper/system_ext_b"
  (cd "$T" && sha256sum "$R/dev/block/mapper/system_b" "$R/dev/block/mapper/system_ext_b" "$R$POLT" > "$T/stock.sha")
}
run() { env Z9X_ROOT="$R" Z9X_T="$T" PATH="$BIN:$PATH" $SH "$READER" >> "$T/out" 2>&1; }
prop() { Z9X_T=$T "$BIN/getprop" "$1"; }
expect_prop() { [ "$(prop "$1")" = "$2" ] || fail "$1='$(prop "$1")', want '$2'"; }
is_bound() { grep -qx "$1 $R$2" "$T/binds"; }
expect_bound() { [ "$(grep -c . "$T/binds" | tr -d ' ')" = "$1" ] || fail "binds: $(cat "$T/binds"), want $1"; }
expect_both() { is_bound "$LIBH" "$LIBT" || fail "the VNDK lib not bound: $(cat "$T/binds")"
                is_bound "$POLH" "$POLT" || fail "the audio policy not bound: $(cat "$T/binds")"; }
expect_z9x_bound_but() {  # every Z9X line bound over its target with its allow-listed file, except the paths given
  zlines | while read -r h p t; do
    case " $* " in *" $p "*) is_bound "$h" "$t" && echo "$p is bound"; continue ;; esac
    is_bound "$h" "$t" || echo "$p not bound over $t"
  done > "$T/miss"
  [ ! -s "$T/miss" ] || fail "$(cat "$T/miss")"
}
expect_log() { grep -q "$1" "$T/logcat" || fail "logcat lacks '$1': $(cat "$T/logcat")"; }
expect_stock_untouched() {
  [ ! -s "$T/fsmounts" ] || fail "still mounted: $(cat "$T/fsmounts")"
  [ ! -e "$R/mnt/z9x_stock" ] || fail "the stock scratch folder is left"
  (cd "$T" && sha256sum -c --quiet "$T/stock.sha" >/dev/null 2>&1) || fail "a stock file changed"
  awk '$2 !~ /(^|,)ro(,|$)/' "$T/fsmounts.log" | grep . && fail "a stock mount was not read-only"
  grep -q -- "--setro $R/dev/block/mapper/$STOCKP\$" "$T/blockdev.log" 2>/dev/null || fail "no setro on $STOCKP"
}
expect_no_stock_mount() { [ ! -s "$T/fsmounts.log" ] || fail "stock mounted: $(cat "$T/fsmounts.log")"; }
end() {
  [ ! -s "$T/out" ] || fail "reader output: $(cat "$T/out")"
  if [ "$CFAIL" = 0 ]; then PASS=$((PASS + 1)); echo "ok   $SHN $CASE"
  else FAIL=$((FAIL + 1)); echo "FAIL $SHN $CASE"; sed 's/^/     | /' "$T/logcat" | tail -6; fi
}
want() { case "$1" in "$ONLY"*) return 0 ;; esac; return 1; }

cases() {
  # ------------------------------------------------------------ HB
  if want hb_617_ok; then
    # the tester's unit: G0148, stock 6.17.12 (the second from= rule), flattened VNDK APEX in stock system_a
    hb_begin hb_617_ok; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.set hb-test-a; expect_prop sys.z9x.blobs.src stock_a
    expect_prop sys.lumen.nui ""; expect_bound 2; expect_both
    expect_log "z9x_blobs: trial run: no z9x_blobs_a, 2 files read-only from the stock slot _a"
    expect_log "z9x_blobs: ok: 2 files of set hb-test-a bound"
    grep -q 'u:object_r:system_lib_file:s0 .*libstagefright_foundation.so$' "$T/chcon.log" || fail "lib label: $(cat "$T/chcon.log")"
    expect_stock_untouched; end
  fi
  if want hb_616_ok; then
    # a G0130 on stock 6.16.24: the first from= rule
    hb_begin hb_616_ok; cp "$F/pol.616" "$R$POLT"; sed -i.b 's/=G0148$/=G0130/; s/v6.17.12/v6.16.24/' "$T/props"; hb_stock; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.src stock_a; expect_bound 2; expect_both
    expect_stock_untouched; end
  fi
  if want hb_installed_partition; then
    # an installed unit (no trial): the partition's copies, no stock mount, no source prop (as before the move)
    hb_begin hb_installed_partition; hb_partition; sed -i.b 's/^ro.gsid.image_running=1$/ro.gsid.image_running=0/' "$T/props"; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.src ""; expect_bound 2; expect_both; expect_no_stock_mount; end
  fi
  if want hb_trial_partition_there; then
    # a trial whose slot has a z9x_blobs partition after all (Lumen OS installed): the partition, no stock mount
    hb_begin hb_trial_partition_there; hb_partition; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.src ""; expect_bound 2; expect_no_stock_mount; end
  fi
  if want hb_installed_no_partition; then
    # an installed unit without the partition: missing as before, the stock slot is never read
    hb_begin hb_installed_no_partition; sed -i.b 's/^ro.gsid.image_running=1$/ro.gsid.image_running=0/' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "missing: no z9x_blobs_a"; expect_no_stock_mount; end
  fi
  if want hb_model_g0118; then
    # a model code this image is not for: nothing read, the trial stays without the files
    hb_begin hb_model_g0118; sed -i.b 's/=G0148$/=G0118/' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "this image is not for G0118"; expect_no_stock_mount
    [ ! -e "$R/mnt/z9x_blobs" ] || fail "the reader's folder was made for another model"; end
  fi
  if want hb_no_model; then
    hb_begin hb_no_model; sed -i.b '/^ro.boot.xgimi.modelname=/d' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "reports no model code (unset)"; expect_no_stock_mount; end
  fi
  if want hb_no_model_list; then
    # an image that names no model codes: fail closed
    hb_begin hb_no_model_list; sed -i.b '/^ro.lumen.models=/d' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "this image is not for G0148"; expect_no_stock_mount; end
  fi
  if want hb_no_vndk_target; then
    # the image lacks the VNDK file the lib would cover: not bound (notarget), the audio policy still is
    hb_begin hb_no_vndk_target; rm -f "$R$LIBT"; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem notarget:$LIBP"
    is_bound "$POLH" "$POLT" || fail "the audio policy not bound"; expect_stock_untouched; end
  fi
  if want hb_lib_corrupt; then
    # the stock lib is not the allow-listed one: not bound; the audio policy still is
    hb_begin hb_lib_corrupt; hb_stock lib=lib.gsi; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem bad:$LIBP"
    is_bound "$POLH" "$POLT" || fail "the audio policy not bound"; expect_stock_untouched; end
  fi
  if want hb_policy_unknown_firmware; then
    # an audio policy no from= rule knows (an unknown stock version): not taken; the lib still bound (HDMI, codecs)
    hb_begin hb_policy_unknown_firmware; cp "$F/pol.unknown" "$R$POLT"; hb_stock; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$POLP"
    is_bound "$LIBH" "$LIBT" || fail "the lib not bound"; expect_stock_untouched; end
  fi
  if want hb_ext4_stock; then
    # an ext4 stock system (erofs refused): mounted ext4, read-only, without journal replay
    hb_begin hb_ext4_stock; : > "$T/no_erofs"; run
    expect_prop sys.z9x.blobs ok; expect_bound 2; expect_both
    grep -q "^ext4 ro,nodev,nosuid,noexec,noload $R/dev/block/mapper/system_a " "$T/fsmounts.log" || fail "ext4: $(cat "$T/fsmounts.log")"
    expect_stock_untouched; end
  fi
  if want hb_lib_too_big; then
    # a stock file larger than maxf is not copied at all (the files the reader takes stay bounded)
    hb_begin hb_lib_too_big; hb_stock lib=lib.big; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$LIBP"; expect_stock_untouched; end
  fi
  if want hb_from_not_delete; then
    # a from= script that is not line deletions only is never run: the audio policy stays out
    hb_begin hb_from_not_delete; sed -i.b 's/:2,3d;6d$/:s,policy,evil,/' "$R/system/etc/z9x/blobs_allow.txt"; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$POLP"; expect_stock_untouched; end
  fi
  if want hb_apex_other_name; then
    # a flattened folder whose apex_manifest.pb names another APEX is not the VNDK one
    hb_begin hb_apex_other_name; hb_stock manifest=com.android.vndk.v34; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$LIBP"; expect_stock_untouched; end
  fi
  if want hb_apex_named_folder; then
    # the folder named after the APEX itself (com.android.vndk.v33) is taken the same way
    hb_begin hb_apex_named_folder; hb_stock apexdir=com.android.vndk.v33; run
    expect_prop sys.z9x.blobs ok; expect_bound 2; expect_both; expect_stock_untouched; end
  fi
  if want hb_lib_symlink; then
    hb_begin hb_lib_symlink; hb_stock symlink; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$LIBP"; expect_stock_untouched; end
  fi
  if want hb_stock_apex_zip; then
    # settings that say the stock APEX is a .apex file (the Z9X layout) on the HB's flattened stock: no VNDK lib
    hb_begin hb_stock_apex_zip; sed -i.b 's/^stock_apex=flat$/stock_apex=zip/' "$R/system/etc/z9x/blobs.conf"; run
    expect_prop sys.z9x.blobs partial; expect_bound 1; expect_log "first problem missing:$LIBP"; expect_stock_untouched; end
  fi
  if want hb_no_stock; then
    # stock system_a cannot be mounted: nothing bound, the reason in the log
    hb_begin hb_no_stock; rm -f "$R/dev/block/mapper/system_a"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0
    expect_log "the stock slot _a of this trial run was not read (nostock)"; end
  fi
  if want hb_bad_target; then
    # an allow-list line that would bind over anything but /vendor/etc or a VNDK lib is never bound
    hb_begin hb_bad_target; mkdir -p "$R/system/bin"; echo "the system's own sh" > "$R/system/bin/sh"
    echo "$LIBH lib/evil.so $LIBT bind=/system/bin/sh" >> "$R/system/etc/z9x/blobs_allow.txt"; run
    expect_prop sys.z9x.blobs partial; expect_bound 2; expect_both; expect_log "first problem target:lib/evil.so"
    grep -q " $R/system/bin/sh\$" "$T/binds" && fail "bound over /system/bin/sh"; expect_stock_untouched; end
  fi
  # ------------------------------------------------------------ Z9X
  if want z9x_ok; then
    # a trial run on a stock Z9X: the nine files from stock system, system_ext, the VNDK APEX file (lib and lib64) and
    # the vendor's audio policy (its from= rule)
    z9x_begin z9x_ok; run
    expect_prop sys.z9x.blobs ok; expect_prop sys.z9x.blobs.set "$ZSET"; expect_prop sys.z9x.blobs.src stock_b
    expect_bound 9; expect_z9x_bound_but
    expect_log "z9x_blobs: trial run: no z9x_blobs_b, 9 files read-only from the stock slot _b"
    grep -q '^ext4 ro,loop,nodev,nosuid,noexec,noload .*apex_payload.img ' "$T/fsmounts.log" || fail "the APEX payload: $(cat "$T/fsmounts.log")"
    is_bound "$(zlines | awk '$3 ~ /lib64\/libstagefright_foundation.so$/ {print $1}')" /apex/com.android.vndk.v34/lib64/libstagefright_foundation.so \
      || fail "the lib64 VNDK file is not bound"
    expect_stock_untouched; end
  fi
  if want z9x_policy_unknown_firmware; then
    # a stock audio policy no from= rule knows: the policy stays out, the other eight files are bound (fail closed per file)
    z9x_begin z9x_policy_unknown_firmware; echo "<another firmware/>" >> "$R$POLT"
    (cd "$T" && sha256sum "$R/dev/block/mapper/system_b" "$R/dev/block/mapper/system_ext_b" "$R$POLT" > "$T/stock.sha"); run
    expect_prop sys.z9x.blobs partial; expect_bound 8; expect_z9x_bound_but etc/xgimi/audio_policy_configuration.xml
    expect_log "first problem missing:etc/xgimi/audio_policy_configuration.xml"; expect_stock_untouched; end
  fi
  if want z9x_codec_corrupt; then
    z9x_begin z9x_codec_corrupt --corrupt system_ext/lib64/libc2plugin_store.so; run
    expect_prop sys.z9x.blobs partial; expect_bound 8; expect_z9x_bound_but system_ext/lib64/libc2plugin_store.so
    expect_stock_untouched; end
  fi
  if want z9x_no_apex; then
    z9x_begin z9x_no_apex --no-apex; run
    expect_prop sys.z9x.blobs partial; expect_bound 7
    expect_z9x_bound_but etc/xgimi/libstagefright_foundation.so etc/xgimi/libstagefright_foundation64.so; expect_stock_untouched; end
  fi
  if want z9x_stock_apex_flat; then
    # settings that say flat on the Z9X's .apex file: no VNDK lib (the layout is per-model data)
    z9x_begin z9x_stock_apex_flat; sed -i.b 's/^stock_apex=zip$/stock_apex=flat/' "$R/system/etc/z9x/blobs.conf"; run
    expect_prop sys.z9x.blobs partial; expect_bound 7
    expect_z9x_bound_but etc/xgimi/libstagefright_foundation.so etc/xgimi/libstagefright_foundation64.so; expect_stock_untouched; end
  fi
  if want z9x_other_model; then
    # the Z9X image on an X50 Ultra, the HB image's codes on a Z9X: not this image's model, nothing is read
    z9x_begin z9x_other_model; sed -i.b 's/^ro.boot.xgimi.modelname=G0082$/ro.boot.xgimi.modelname=G0111/' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "this image is not for G0111"; expect_no_stock_mount; end
    z9x_begin z9x_hb_codes; sed -i.b "s/^ro.lumen.models=.*/ro.lumen.models=$MODELS/" "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_log "this image is not for G0082"; expect_no_stock_mount; end
  fi
  if want z9x_no_models; then
    z9x_begin z9x_no_models; sed -i.b '/^ro.lumen.models=/d' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_bound 0; expect_no_stock_mount; end
  fi
  if want z9x_installed; then
    # an installed Z9X without the partition: missing as before, never the stock slot
    z9x_begin z9x_installed; sed -i.b '/^ro.gsid.image_running=/d' "$T/props"; run
    expect_prop sys.z9x.blobs missing; expect_prop sys.z9x.blobs.src ""; expect_bound 0; expect_log "missing: no z9x_blobs_b"
    expect_no_stock_mount; end
  fi
}

for SH in ${BLOBS_TRIAL_SHELLS:-sh dash bash mksh}; do
  command -v $SH >/dev/null 2>&1 || continue
  SHN=$SH; cases
done

# static: the image data the reader relies on
if want static; then
  CASE=static; SHN=-; CFAIL=0; T=$WORK; : > "$T/out"; : > "$T/logcat"
  dm=$(sed -n 's/^models=//p' "$G/devices/goat_elegant/device.conf")
  [ -n "$MODELS" ] && [ "$MODELS" = "$dm" ] || fail "hb_prop.txt ro.lumen.models '$MODELS' != device.conf models '$dm'"
  grep -q '^ro.lumen.models=' "$HBIMG/hb_lumen_prop.txt" && fail "ro.lumen.models belongs to hb_prop.txt (every image), not hb_lumen_prop.txt"
  want="tag=z9x_blobs allow=blobs_allow.txt blocks=64 tmpfs=8m maxf=4194304 eye=0 src_partition=0 stock_apex=flat"
  [ "$(grep -v '^#' "$HBIMG/blobs.conf" | tr '\n' ' ')" = "$want " ] || fail "HB blobs.conf: $(grep -v '^#' "$HBIMG/blobs.conf")"
  for l in 'lumen,pub	install	0644	z9x_blobs.rc	{sys}/etc/init/z9x_blobs.rc' \
           'lumen,pub	install	0755	z9x_blobs.sh	{sys}/etc/z9x/z9x_blobs.sh' \
           'lumen,pub	install	0644	blobs.conf	{sys}/etc/z9x/blobs.conf' \
           'lumen,pub	install	0644	blobs_allow.txt	{sys}/etc/z9x/blobs_allow.txt'; do
    grep -qxF "$l" "$HBIMG/steps.tsv" || fail "HB steps.tsv lacks: $l"
  done
  # one reader for every model: no recipe names another one, every image's ro.lumen.models is its device.conf models
  grep -n -E 'hb_blobs_trial|blobs_stock_dsu|x50_blobs\.(sh|rc)' "$G"/devices/*/build.conf "$G"/devices/*/image/steps.tsv \
    && fail "a second blobs reader in a recipe"
  for c in g0111 g0100 h0061 h0062 h0063 g0155 horse_popular goat_elegant; do
    grep -q 'tools/ota/image/z9x_blobs.sh' "$G/devices/$c/build.conf" || fail "$c build.conf lacks the one reader"
  done
  for pc in g0111/image/x50_prop.txt:g0111 g0111/image/x50_prop_pub.txt:g0111 g0111/image/x50max_prop_pub.txt:g0100 \
            g0155/image/rs30_prop.txt:g0155 h0061/image/h0061_prop_pub.txt:h0061 h0062/image/h0062_prop_pub.txt:h0062 h0063/image/h0063_prop_pub.txt:h0063; do
    dm=$(sed -n 's/^models=//p' "$G/devices/${pc#*:}/device.conf")
    grep -qx "+ro.lumen.models=$dm" "$G/devices/${pc%%:*}" || fail "${pc%%:*}: ro.lumen.models is not '$dm'"
  done
  grep -qxF "*	line	{mn}/system/product/etc/build.prop	ro.lumen.models=$(sed -n 's/^models=//p' "$G/devices/g0155/device.conf")" \
    "$G/devices/g0155/image/steps.tsv" || fail "RS30 steps.tsv: no ro.lumen.models line"
  grep -q "^models=G0082\$" "$G/devices/horse_popular/device.conf" || fail "Z9X device.conf models changed"
  grep -qxF "*	tar_line	{tar}	system/product/etc/build.prop	ro.lumen.models=G0082" "$G/devices/horse_popular/image/steps.tsv" \
    || fail "Z9X steps.tsv: no ro.lumen.models line"
  grep -qF "nro.lumen.models='" "$G/devices/horse_popular/image/z9x_props.py" || fail "z9x_props.py writes no ro.lumen.models"
  for l in '*	tar_add	system/etc/z9x/z9x_blobs.sh	z9x_blobs.sh	755' '*	tar_add	system/etc/z9x/blobs.conf	blobs.conf	644' \
           '*	tar_add	system/etc/init/z9x_blobs.rc	z9x_blobs.rc	644'; do
    grep -qxF "$l" "$G/devices/horse_popular/image/steps.tsv" || fail "Z9X steps.tsv lacks: $l"
  done
  # the HB recipe pins the reader, its rc and the HB settings by sha256: the pins are these files
  for f in "$G/tools/ota/image/z9x_blobs.rc" "$G/tools/ota/image/z9x_blobs.sh" "$HBIMG/blobs.conf"; do
    grep -qxF "pub	sha	$(basename "$f")	$(sha256sum "$f" | cut -d' ' -f1)" "$HBIMG/steps.tsv" \
      || fail "HB steps.tsv: no sha pin of the current $(basename "$f")"
  done
  # every model's reader settings carry exactly the keys the reader takes
  for f in "$G/tools/ota/image/blobs.conf" "$G/devices/g0111/image/blobs.conf" "$HBIMG/blobs.conf"; do
    k=$(grep -v -e '^#' -e '^$' "$f" | cut -d= -f1 | tr '\n' ' ')
    [ "$k" = "tag allow blocks tmpfs maxf eye src_partition stock_apex " ] || fail "$f keys: $k"
  done
  end
fi

echo "blobs_trial_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
