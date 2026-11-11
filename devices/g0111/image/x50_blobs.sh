#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Lumen OS X50 Ultra (g0111) images (image path /system/etc/z9x/x50_blobs.sh 0755; x50_blobs.rc runs it as an
# exec at post-fs-data, after init.rc's part of that trigger, i.e. in the default mount namespace with the APEXes
# active, before the vendor's part of the trigger (system rc files are parsed first) and before 'on boot').
# Fork of tools/ota/image/z9x_blobs.sh (devices/g0111/forks.tsv): the same steps for the X50's 16 MiB z9x_blobs
# partition, with the eye-protection group of x50_blobs_allow.txt.
#
# The user's own MediaTek / XGIMI files live in the logical partition z9x_blobs<slot> in super (made by the
# installer from the user's own stock or Lumen system: x50_blobs_allow.txt). They survive factory resets.
#   0. the vendor's native-UI fifos /data/vendor/tmp/nuififo and retnuififo are removed if they are there: in
#      this boot no reader exists yet, and libxgimi's Msrv_System_Control::nuiCommand opens nuififo O_WRONLY,
#      which BLOCKS until a reader opens it, under the NUI mutex (only a missing file is retried, 40 x 100 ms,
#      and given up). The stock nativeui makes both again with mkfifo when it starts. They are never opened
#      here: an open and close would let the vendor's write end in SIGPIPE.
#   1. read the partition from its dm device (mapped by first-stage init; no lpdump, see z9x_blobs.sh),
#   2. untar it into our own tmpfs /mnt/z9x_blobs (32 MiB: the 16 MiB copy + the files; /mnt is noexec and a
#      bind keeps the flags of its source mount). Before extracting: no member name absolute or with '..', no
#      link / device / fifo member; after: only directories and regular files of at most 8 MiB,
#   3. accept only files whose sha256 is in /system/etc/z9x/x50_blobs_allow.txt,
#   4. label them (label=nativeui_main_exec when the line says so, else system_lib_file for a .so and
#      system_file otherwise; mode 0755 only with mode=0755 and a label=) and bind-mount each over its 0-byte
#      placeholder in /system, or for a bind= line over that vendor / VNDK APEX file,
#   5. the need=eye lines (stock nativeui and its gmpf ndk library) are all checked first and bound only when
#      every one of them is there with its sha256: never one without the other,
#   6. remount the tmpfs read-only (every bind shares it).
# Results: sys.z9x.blobs = ok | missing | bad:<why> | partial (the whole set, as z9x_blobs.sh), sys.z9x.blobs.set,
# and sys.lumen.nui = ok | missing | bad:<why> (the eye-protection drawer). x50_nui.rc starts the service
# nativeui only on sys.lumen.nui=ok. Without it the system still boots with no drawer and no fifo: the vendor's
# eye-follow mask is not drawn, nothing on the system answers for it, and the vendor itself does not fall back
#: the Lumen projector app reads sys.lumen.nui for that.
# Never writes any partition. Bounded, since init waits for this exec and the vendor PWM watchdog reboots when
# early-boot is late: the 16 MiB read, the two listings and the untar each have a 3 s timeout (each takes well
# under a second on 16 MiB), then sha256 of at most the allow-listed files, each at most 8 MiB. Runs once per boot.
TAG=x50_blobs
R=${Z9X_ROOT:-}        # empty on the projector; devices/g0111/test/x50_blobs_test.sh points it at a temp dir
ALLOW=$R/system/etc/z9x/x50_blobs_allow.txt
M=$R/mnt/z9x_blobs
NUIDIR=$R/data/vendor/tmp
BLOCKS=256             # x 64 KiB = 16 MiB, the partition the installer makes (lib/devices.conf x50.BLOBS_SIZE)
MAXF=8388608           # largest member (stock nativeui is 6021480 bytes)
say() { log -t "$TAG" "$*"; }
nui() { setprop sys.lumen.nui "$1"; [ "$1" = ok ] || say "eye-protection drawer nativeui not started: $1"; }
fail() { setprop sys.z9x.blobs "$1"; nui "$1"; say "$1${2:+: $2}"; exit 0; }

[ -n "$(getprop sys.lumen.nui)" ] && exit 0   # once per boot (a second run must not touch a running drawer's fifos)
for f in nuififo retnuififo; do
  if [ -p "$NUIDIR/$f" ]; then rm -f "$NUIDIR/$f" && say "removed the native-UI fifo $f left from an earlier boot"; fi
done

cur=$(getprop ro.boot.slot_suffix)
name=z9x_blobs$cur
dev=$R/dev/block/mapper/$name
case "$cur" in _a|_b) ;; *) fail missing "no slot suffix" ;; esac
# (the host test's partition is a plain file)
[ -b "$dev" ] || { [ -n "$R" ] && [ -f "$dev" ]; } || fail missing "no $name"

mkdir -p $M || fail bad:mkdir
[ -n "$R" ] || mount -t tmpfs -o mode=0750,uid=0,gid=0,size=32m,nosuid,nodev tmpfs $M 2>/dev/null || fail bad:tmpfs
img=$M/.img
timeout 3 dd if="$dev" of=$img bs=65536 count=$BLOCKS 2>/dev/null || fail bad:read "$dev"
timeout 3 tar -tf $img 2>/dev/null | grep -qE '^/|^\.\.(/|$)|/\.\.(/|$)' && fail bad:path
odd=$(timeout 3 tar -tvf $img 2>/dev/null | grep '^[lhcbps]' | head -n 1)
[ -z "$odd" ] || fail bad:entry "$odd"
mkdir -p $M/x
( cd $M/x && timeout 3 tar -xf $img ) 2>/dev/null || fail bad:tar
rm -f $img
odd=$(find $M/x ! -type d \( ! -type f -o -size +${MAXF}c \) 2>/dev/null | head -n 1)
[ -z "$odd" ] || fail bad:entry "${odd#$M/x/}"
[ -f $M/x/MANIFEST ] || fail bad:manifest
set=$(sed -n 's/^set=//p' $M/x/MANIFEST)
want=$(sed -n 's/^set=//p' $ALLOW)
setprop sys.z9x.blobs.set "$set"
[ "$set" = "$want" ] || say "blob set '$set' is not this image's '$want' (files still checked one by one)"

n=0; bad=; eye=0; eyebad=; Q=$M/eye.q
: > $Q
while read -r h p src opt; do
  case "$h" in ''|\#*|set=*) continue ;; esac
  e=0; case " $opt " in *" need=eye "*) e=1; eye=$((eye + 1)) ;; esac
  f=$M/x/$p
  if [ ! -f "$f" ]; then
    bad=${bad:-missing:$p}; [ $e = 0 ] || eyebad=${eyebad:-missing:$p}; continue
  fi
  got=$(sha256sum "$f" | cut -d' ' -f1)
  if [ "$got" != "$h" ]; then
    bad=${bad:-bad:$p}; [ $e = 0 ] || eyebad=${eyebad:-bad:$p}; rm -f "$f"; continue
  fi
  dst=/system/$p
  case " $opt " in
    *" bind="*) dst=${opt#*bind=}; dst=${dst%% *}
      # only the vendor / VNDK APEX files xgimi_compat.rc binds on a private image; never an eye-protection file
      case "$e:$dst" in 0:/vendor/etc/*|0:/apex/com.android.vndk.v*/lib/*|0:/apex/com.android.vndk.v*/lib64/*) ;;
        *) bad=${bad:-target:$p}; [ $e = 0 ] || eyebad=${eyebad:-target:$p}; continue ;; esac
      [ -f "$R$dst" ] || { bad=${bad:-notarget:$p}; continue; } ;;
    *) if ! { [ -f "$R$dst" ] && [ ! -s "$R$dst" ]; }; then
         bad=${bad:-noplaceholder:$p}; [ $e = 0 ] || eyebad=${eyebad:-noplaceholder:$p}; continue
       fi ;;
  esac
  lab=u:object_r:system_file:s0
  case "$p" in *.so) lab=u:object_r:system_lib_file:s0 ;; esac
  mode=0644; ok=1
  case " $opt " in
    *" label="*) l=${opt#*label=}; l=${l%% *}
      case "$l" in nativeui_main_exec) lab=u:object_r:$l:s0 ;; *) ok=0 ;; esac ;;
  esac
  case " $opt " in
    *" mode="*) m=${opt#*mode=}; m=${m%% *}
      case "$m:$opt" in 0755:*label=*) mode=0755 ;; *) ok=0 ;; esac ;;
  esac
  if [ $ok = 0 ]; then bad=${bad:-option:$p}; [ $e = 0 ] || eyebad=${eyebad:-option:$p}; continue; fi
  chmod $mode "$f"; chown root:root "$f"
  chcon "$lab" "$f"
  if [ $e = 1 ]; then echo "$f $R$dst" >> $Q; continue; fi   # bound below, all or none
  mount --bind "$f" "$R$dst" || { bad=${bad:-mount:$p}; continue; }
  n=$((n + 1))
done < $ALLOW

# the eye-protection drawer: every need=eye line verified, or none of them bound
[ $eye -gt 0 ] || eyebad=${eyebad:-bad:allow}
en=0
if [ -z "$eyebad" ]; then
  while read -r f dst; do
    mount --bind "$f" "$dst" || { eyebad=mount:${f#$M/x/}; break; }
    en=$((en + 1))
  done < $Q
fi
if [ -n "$eyebad" ]; then
  bad=${bad:-$eyebad}
  # one bound before a later bind failed comes off again: nativeui never runs without its library
  while read -r f dst; do
    if [ $en -gt 0 ]; then umount "$dst" 2>/dev/null || say "$dst stays bound (umount failed)"; en=$((en - 1)); fi
    rm -f "$f"
  done < $Q
else
  n=$((n + en))
fi
rm -f $Q
chmod 0755 $M
[ -n "$R" ] || mount -o remount,ro $M 2>/dev/null || say "$M stays writable (remount failed)"
if [ -z "$eyebad" ]; then nui ok; else nui "$eyebad"; fi
if [ -z "$bad" ]; then
  setprop sys.z9x.blobs ok
  say "ok: $n files of set $set bound"
elif [ $n -gt 0 ]; then
  setprop sys.z9x.blobs partial
  say "partial: $n files bound, first problem $bad"
else
  setprop sys.z9x.blobs "$bad"; say "$bad"
fi
exit 0
