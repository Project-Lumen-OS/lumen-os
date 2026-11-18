#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# The one blobs reader of every Lumen OS image (Z9X, Z9X HB, X50 Ultra and Ultra Max, TITAN Noir Pro and Max, RS30
# Ultra Max). Image path /system/etc/z9x/z9x_blobs.sh 0755; z9x_blobs.rc runs it as an exec at post-fs-data, after
# init.rc's part of that trigger (default mount namespace, APEXes active), before the vendor's part (system rc files
# are parsed first) and before 'on boot', where the media services, audioserver and the vendor HALs start.
# A model differs only in data: /system/etc/z9x/blobs.conf (its reader settings, see below; tools/ota/image/blobs.conf
# for the Z9X, devices/<codename>/image/blobs.conf for the others) and the allow-list that file names.
#
# The user's own MediaTek / XGIMI files (codecs, the VNDK libstagefright_foundation the vendor codec2 plugin and audio
# HAL need, the audio policy without XGIMI's enums; on the X50 family also the stock eye-protection drawer) are never
# in the image. An installed unit has them in the logical partition z9x_blobs<slot> (made by the installer from the
# user's own stock or Lumen system), which survives factory resets and partial OTA payloads (they rename the
# untouched z9x_blobs_<source> to z9x_blobs_<target>).
#   0. eye=1 only: the vendor's native-UI fifos /data/vendor/tmp/nuififo and retnuififo are removed if they are there:
#      in this boot no reader exists yet, and libxgimi's Msrv_System_Control::nuiCommand opens nuififo O_WRONLY, which
#      BLOCKS until a reader opens it (the stock nativeui makes both again when it starts). Never opened here.
#   1. read the partition from its dm device (mapped by first-stage init; no lpdump: on the device it is a binder client
#      of lpdumpd, which init cannot start while it waits for this exec),
#   2. untar it into our own tmpfs /mnt/z9x_blobs (/mnt is noexec and a bind keeps the flags of its source mount).
#      Before extracting: no member name absolute or with '..', no link / device / fifo member; after: only
#      directories and regular files of at most maxf bytes,
#   3. accept only files whose sha256 is in the allow-list (or, for a line with alt=<firmware>:<sha256>, that stock
#      firmware's copy, only while that firmware's vendor runs),
#   4. label them (label=nativeui_main_exec when the line says so, else system_lib_file for a .so and system_file
#      otherwise; mode 0755 only with mode=0755 and a label=) and bind-mount each over its 0-byte placeholder in
#      /system, or for a bind= line over that vendor / VNDK APEX file,
#   5. eye=1: the need=eye lines are all checked first and bound only when every one of them is there with its
#      sha256: never one without the other, never base copies mixed with one firmware's alt= copies,
#   6. remount the tmpfs read-only (every bind shares it).
# A trial run (ro.gsid.image_running=1) boots Lumen OS from /data and leaves the stock super as it is, so there is no
# z9x_blobs partition: steps 1-2 take the allow-listed files read-only from the stock slot of this boot instead
# (stock_collect below), into the same folder; steps 3-6 are the same, so every file is checked against the same
# allow-list and bound at the same place, and a file that does not verify stays out. Only when this unit's model
# code (ro.boot.xgimi.modelname) is one of the codes this image is for (ro.lumen.models) and only when no z9x_blobs
# partition is there (a trial on a unit with Lumen OS installed reads the partition).
# Results: sys.z9x.blobs = ok | missing | bad:<why> | partial, sys.z9x.blobs.set = <set id>, sys.z9x.blobs.src =
# partition (written only with src_partition=1) | stock_a | stock_b (a trial run), and with eye=1 sys.lumen.nui =
# ok | missing | bad:<why> (the X50 family's x50_nui.rc starts the drawer only on ok).
# Without the files the system still boots: no vendor sound path, no secure video, and where the vendor codec2 plugin
# needs MediaTek's own libstagefright_foundation (Z9X HB) no hardware video decoders and a frozen first HDMI frame.
# Never writes any partition. Bounded, since init waits for this exec and the vendor PWM watchdog reboots when
# early-boot is late: the partition read, the two listings and the untar have a 3 s timeout each, then sha256 of at
# most the allow-listed files, each at most maxf bytes; a trial run: each stock mount and dmctl call 3 s, lpdump once
# 3 s (only for a stock partition first stage did not map), the unzip of an APEX payload 5 s. Runs once per boot.
#
# blobs.conf (key=value lines and # comments, every key required, any other key or value: sys.z9x.blobs=bad:conf):
#   tag            log tag
#   allow          the allow-list in /system/etc/z9x (identical to the installer's copy)
#   blocks         64 KiB blocks read from z9x_blobs<slot> (the partition size the installer makes)
#   tmpfs          size of the reader's tmpfs /mnt/z9x_blobs
#   maxf           largest file taken, in bytes
#   eye            1: the need=eye group, the native-UI fifos, sys.lumen.nui, once per boot by sys.lumen.nui;
#                  0: no eye group (a need=eye line is refused), once per boot by sys.z9x.blobs=ok
#   src_partition  1: sys.z9x.blobs.src=partition on an installed unit too
#   stock_apex     how the stock slot keeps its APEXes, for a trial run: zip (<name>.apex with apex_payload.img) or
#                  flat (a folder whose apex_manifest.pb names it)
TAG=z9x_blobs
R=${Z9X_ROOT:-}        # empty on the projector; the host tests point it at a temp dir
CONF=$R/system/etc/z9x/blobs.conf
M=$R/mnt/z9x_blobs
NUIDIR=$R/data/vendor/tmp
EYE=0
say() { log -t "$TAG" "$*"; }
nui() {
  [ "$EYE" = 1 ] || return 0
  setprop sys.lumen.nui "$1"; [ "$1" = ok ] || say "eye-protection drawer nativeui not started: $1"
}
fail() { setprop sys.z9x.blobs "$1"; nui "$1"; say "$1${2:+: $2}"; exit 0; }

# ---------------------------------------------------------------- the model's reader settings
[ -f "$CONF" ] || fail bad:conf "no $CONF"
tag=; allown=; BLOCKS=; TMPFS=; MAXF=; eye=; SRCP=; APEXK=; cbad=
while IFS='=' read -r k v; do
  case "$k" in
    tag) tag=$v ;; allow) allown=$v ;; blocks) BLOCKS=$v ;; tmpfs) TMPFS=$v ;; maxf) MAXF=$v ;; eye) eye=$v ;;
    src_partition) SRCP=$v ;; stock_apex) APEXK=$v ;; ''|\#*) ;; *) cbad=${cbad:-key:$k} ;;
  esac
done < "$CONF"
case "$tag" in [a-z]*[a-z0-9]) case "$tag" in *[!a-z0-9_]*) cbad=${cbad:-tag} ;; *) TAG=$tag ;; esac ;; *) cbad=${cbad:-tag} ;; esac
case "$eye" in 0|1) EYE=$eye ;; *) cbad=${cbad:-eye} ;; esac
case "$allown" in *[!a-z0-9_.]*|.*|'') cbad=${cbad:-allow} ;; *_allow.txt) ;; *) cbad=${cbad:-allow} ;; esac
case "$BLOCKS" in [1-9]|[1-9][0-9]|[1-9][0-9][0-9]) ;; *) cbad=${cbad:-blocks} ;; esac
case "$TMPFS" in [1-9]m|[1-9][0-9]m) ;; *) cbad=${cbad:-tmpfs} ;; esac
case "$MAXF" in [1-9][0-9][0-9][0-9][0-9][0-9][0-9]|[1-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;; *) cbad=${cbad:-maxf} ;; esac
case "$SRCP" in 0|1) ;; *) cbad=${cbad:-src_partition} ;; esac
case "$APEXK" in zip|flat) ;; *) cbad=${cbad:-stock_apex} ;; esac
[ -z "$cbad" ] || fail bad:conf "$CONF: $cbad"
ALLOW=$R/system/etc/z9x/$allown

# ---------------------------------------------------------------- once per boot
if [ "$EYE" = 1 ]; then
  [ -n "$(getprop sys.lumen.nui)" ] && exit 0   # a second run must not touch a running drawer's fifos
  for f in nuififo retnuififo; do
    if [ -p "$NUIDIR/$f" ]; then rm -f "$NUIDIR/$f" && say "removed the native-UI fifo $f left from an earlier boot"; fi
  done
else
  [ "$(getprop sys.z9x.blobs)" = ok ] && exit 0
fi

# ---------------------------------------------------------------- a trial run: the stock slot, read only
# Shared by every model: the stock system, system_ext and product of the booted slot are still there (first-stage init
# maps every logical partition of the slot) and /vendor is the stock vendor of that slot. stock_collect copies the
# allow-listed files from there into OUT under the names the blobs tar uses, plus a MANIFEST; the checks below then
# treat them exactly as the partition's. Nothing read here is trusted: a file the stock slot lacks, or has with another
# sha256, is refused like a bad partition member.
#   allow-list source (3rd field)   read from
#     /system/...                   stock system<slot> (system-as-root: <root>/system/...)
#     /system_ext/..., /product/... stock system_ext<slot> / product<slot>, else that folder of stock system
#     /vendor/...                   /vendor
#     /apex/<name>/...              the stock APEX <name> in /system/apex or /system_ext/apex of the stock slot, or
#                                   com.android.vndk.current for a VNDK APEX: stock_apex=zip its <name>.apex, whose
#                                   apex_payload.img is mounted; stock_apex=flat its folder, whose own
#                                   apex_manifest.pb names <name>, read in place
#     from=<sha256>:<sed script>    the source must have one of the line's from= sha256 (one per stock firmware whose
#                                   original differs); the file is the source with that one's lines deleted (N,Md
#                                   commands only), as the installer derives it
# Read-only: every stock device gets blockdev --setro before its mount, every mount is ro, nodev, nosuid, noexec
# (erofs, or ext4 with noload: no journal replay); a partition first stage did not map is mapped with dmctl create -ro
# from lpdump's extents of the slot and deleted again. Regular files only, no symlink anywhere in the path (the
# resolved path must stay inside the stock mount), at most maxf bytes. Every stock mount, loop device and dm device
# made here is gone when stock_collect returns. STOCK_WHY says why nothing was read, STOCK_N how many files were copied.
stock_dev() {  # PARTITION<slot>: STOCK_DEV = its block device, mapped read-only here when first stage did not
  STOCK_DEV=$R/dev/block/mapper/$1
  if [ -b "$STOCK_DEV" ] || { [ -n "$R" ] && [ -f "$STOCK_DEV" ]; }; then return 0; fi
  STOCK_DEV=
  command -v lpdump >/dev/null 2>&1 && command -v dmctl >/dev/null 2>&1 || return 1
  # lpdump at most once per boot (3 s): a binder client of lpdumpd, which init cannot start while it waits for us
  [ -n "$_lpdone" ] || { _lp=$(timeout 3 lpdump --slot="$_sn" 2>/dev/null); _lpdone=1; }
  _t=$(printf '%s\n' "$_lp" | awk -v n="$1" -v sup="$R/dev/block/by-name/super" '
    $1 == "Name:" { cur = $2; next }
    cur == n && $2 == ".." && $4 == "linear" && $5 == "super" && $1 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ && $6 ~ /^[0-9]+$/ {
      printf "linear %s %d %s %s ", $1, $3 - $1 + 1, sup, $6 }')
  [ -n "$_t" ] || return 1
  # shellcheck disable=SC2086  # the targets are words
  timeout 3 dmctl create "lumen_ro_$1" -ro $_t >/dev/null 2>&1 || return 1
  _dms="lumen_ro_$1 $_dms"
  STOCK_DEV=$(timeout 3 dmctl getpath "lumen_ro_$1" 2>/dev/null)
  [ -n "$STOCK_DEV" ]
}

stock_mount() {  # DEV DIR [loop]: read-only, erofs or ext4 (a loop file: ext4 first, the APEX payload format)
  _o=ro,nodev,nosuid,noexec
  if [ "${3-}" = loop ]; then
    _o=ro,loop,nodev,nosuid,noexec
    timeout 3 mount -t ext4 -o "$_o,noload" "$1" "$2" 2>/dev/null || timeout 3 mount -t erofs -o "$_o" "$1" "$2" 2>/dev/null \
      || return 1
  else
    blockdev --setro "$1" 2>/dev/null
    timeout 3 mount -t erofs -o "$_o" "$1" "$2" 2>/dev/null || timeout 3 mount -t ext4 -o "$_o,noload" "$1" "$2" 2>/dev/null \
      || return 1
  fi
  _mnts="$2 $_mnts"
}

stock_regular() {  # FILE TOP: FILE is a regular file, no symlink in its path (the resolved path is inside TOP)
  [ -f "$1" ] && [ ! -L "$1" ] || return 1
  _rp=$(readlink -f "$1" 2>/dev/null) && _rt=$(readlink -f "$2" 2>/dev/null) || return 1
  case "$_rp" in "$_rt"/*) ;; *) return 1 ;; esac
  [ "$_rp" = "$_rt/${1#"$2"/}" ]
}

stock_inside() {  # FILE TOP: stock_regular and at most maxf bytes
  stock_regular "$1" "$2" && [ $(($(wc -c < "$1"))) -le "$MAXF" ]
}

stock_apex() {  # NAME: STOCK_APEX = the folder with that stock APEX's files (once per name)
  case " $_apexok " in *" $1="*) STOCK_APEX=${_apexok#* $1=}; STOCK_APEX=${STOCK_APEX%% *}; return 0 ;; esac
  case " $_apexbad " in *" $1 "*) return 1 ;; esac
  _apexbad="$_apexbad $1"
  _af=
  _cur=; case "$1" in com.android.vndk.v*) _cur=com.android.vndk.current ;; esac
  for _n in "$1" $_cur; do
    for _d in "$_s/system/system/apex" "$_s/system_ext/apex" "$_s/system/system/system_ext/apex"; do
      if [ "$APEXK" = flat ]; then
        # a real folder whose own apex_manifest.pb names this APEX
        if [ -d "$_d/$_n" ] && [ ! -L "$_d/$_n" ] && stock_regular "$_d/$_n/apex_manifest.pb" "$_s" \
            && grep -q -F "$1" "$_d/$_n/apex_manifest.pb" 2>/dev/null; then
          STOCK_APEX=$_d/$_n; _apexok="$_apexok $1=$STOCK_APEX"; return 0
        fi
      elif stock_regular "$_d/$_n.apex" "$_s"; then _af=$_d/$_n.apex; break 2
      fi
    done
  done
  [ -n "$_af" ] || return 1
  STOCK_APEX=$_s/apex/$1
  mkdir -p "$STOCK_APEX" "$_s/zip/$1" || return 1
  timeout 5 unzip -q -o -d "$_s/zip/$1" "$_af" apex_payload.img >/dev/null 2>&1 || return 1
  [ -f "$_s/zip/$1/apex_payload.img" ] || return 1
  stock_mount "$_s/zip/$1/apex_payload.img" "$STOCK_APEX" loop || return 1
  _apexok="$_apexok $1=$STOCK_APEX"
}

stock_cleanup() {  # the stock mounts newest first, the dm devices made here, the scratch tmpfs
  _left=0
  for _d in $_mnts; do umount "$_d" 2>/dev/null || { _left=1; say "$_d stays mounted (umount failed)"; }; done
  _mnts=
  for _d in $_dms; do timeout 3 dmctl delete "$_d" >/dev/null 2>&1 || say "dm device $_d stays (delete failed)"; done
  _dms=
  [ $_left = 1 ] && return 0
  if [ -z "$R" ]; then umount "$_s" 2>/dev/null && rmdir "$_s" 2>/dev/null; else rm -rf "$_s"; fi
  return 0
}

stock_collect() {  # OUT SCRATCH: the allow-listed files of the stock slot $cur into OUT, plus OUT/MANIFEST
  _out=$1; _s=$2
  STOCK_WHY=; STOCK_N=0; _mnts=; _dms=; _apexok=; _apexbad=; _lp=; _lpdone=
  case "$cur" in _a) _sn=0 ;; _b) _sn=1 ;; *) STOCK_WHY=slot; return 1 ;; esac
  mkdir -p "$_s" "$_out" || { STOCK_WHY=mkdir; return 1; }
  if [ -z "$R" ]; then
    mount -t tmpfs -o mode=0700,uid=0,gid=0,size=64m,nosuid,nodev,noexec tmpfs "$_s" 2>/dev/null \
      || { STOCK_WHY=tmpfs; return 1; }
  fi
  _sys=0
  for _p in system system_ext product; do
    mkdir -p "$_s/$_p"
    if stock_dev "$_p$cur" && stock_mount "$STOCK_DEV" "$_s/$_p"; then
      [ $_p = system ] && _sys=1
    else
      [ $_p = product ] || say "stock $_p$cur not mounted"
    fi
  done
  if [ $_sys = 0 ] || [ ! -d "$_s/system/system" ]; then stock_cleanup; STOCK_WHY=nostock; return 1; fi
  while read -r _h _p _src _opt; do
    case "$_h" in ''|\#*|set=*) continue ;; esac
    case "$_p" in /*|..|../*|*/..|*/../*) continue ;; esac
    _top=$_s; _c=
    case "$_src" in
      /system/*) _c=$_s/system$_src ;;
      /system_ext/*|/product/*)
        _q=${_src#/*/}; _pt=${_src#/}; _pt=${_pt%%/*}
        _c=$_s/$_pt/$_q; [ -f "$_c" ] || _c=$_s/system/system/$_pt/$_q ;;
      /vendor/*) _top=$R/vendor; _c=$R$_src ;;
      /apex/*/*)
        _an=${_src#/apex/}; _q=${_an#*/}; _an=${_an%%/*}
        stock_apex "$_an" && _c=$STOCK_APEX/$_q ;;
    esac
    [ -n "$_c" ] && stock_inside "$_c" "$_top" || continue
    _o=$_out/$_p
    mkdir -p "$(dirname "$_o")" || continue
    case " $_opt " in
      *" from="*)
        _ch=$(sha256sum "$_c" | cut -d' ' -f1); _sc=
        for _fr in $_opt; do
          case "$_fr" in from=*) ;; *) continue ;; esac
          _fr=${_fr#from=}
          [ "${_fr%%:*}" = "$_ch" ] && { _sc=${_fr#*:}; break; }
        done
        case "$_sc" in ''|*[!0-9,d\;]*) continue ;; esac
        sed -e "$_sc" "$_c" > "$_o" || { rm -f "$_o"; continue; } ;;
      *) cat "$_c" > "$_o" || { rm -f "$_o"; continue; } ;;
    esac
    STOCK_N=$((STOCK_N + 1))
  done < "$ALLOW"
  echo "set=$(sed -n 's/^set=//p' "$ALLOW")" > "$_out/MANIFEST"
  stock_cleanup
  return 0
}

# ---------------------------------------------------------------- where the files come from
cur=$(getprop ro.boot.slot_suffix)
name=z9x_blobs$cur
dev=$R/dev/block/mapper/$name
case "$cur" in _a|_b) ;; *) fail missing "no slot suffix" ;; esac
# (the host test's partition is a plain file)
src=partition
if ! { [ -b "$dev" ] || { [ -n "$R" ] && [ -f "$dev" ]; }; }; then
  [ "$(getprop ro.gsid.image_running)" = 1 ] || fail missing "no $name"
  # a trial run on stock: only on a model this image is for (ro.lumen.models, from its build descriptor), an XGIMI
  # model code (a capital letter and four digits) the unit itself reports
  mdl=$(getprop ro.boot.xgimi.modelname)
  case $mdl in [A-Z][0-9][0-9][0-9][0-9]) ;; *) fail missing "no $name, and this unit reports no model code (${mdl:-unset})" ;; esac
  case " $(getprop ro.lumen.models) " in *" $mdl "*) ;; *) fail missing "no $name, and this image is not for $mdl" ;; esac
  src=stock$cur
fi

mkdir -p $M || fail bad:mkdir
[ -n "$R" ] || mount -t tmpfs -o mode=0750,uid=0,gid=0,size=$TMPFS,nosuid,nodev tmpfs $M 2>/dev/null || fail bad:tmpfs
[ "$src" = partition ] && [ "$SRCP" = 0 ] || setprop sys.z9x.blobs.src "$src"
if [ "$src" = partition ]; then
  img=$M/.img
  timeout 3 dd if="$dev" of=$img bs=65536 count=$BLOCKS 2>/dev/null || fail bad:read "$dev"
  timeout 3 tar -tf $img 2>/dev/null | grep -qE '^/|^\.\.(/|$)|/\.\.(/|$)' && fail bad:path
  odd=$(timeout 3 tar -tvf $img 2>/dev/null | grep '^[lhcbps]' | head -n 1)
  [ -z "$odd" ] || fail bad:entry "$odd"
  mkdir -p $M/x
  ( cd $M/x && timeout 3 tar -xf $img ) 2>/dev/null || fail bad:tar
  rm -f $img
else
  stock_collect $M/x $R/mnt/z9x_stock \
    || fail missing "no $name, and the stock slot $cur of this trial run was not read ($STOCK_WHY)"
  say "trial run: no $name, $STOCK_N files read-only from the stock slot $cur (checked below like the partition's)"
fi
odd=$(find $M/x ! -type d \( ! -type f -o -size +${MAXF}c \) 2>/dev/null | head -n 1)
[ -z "$odd" ] || fail bad:entry "${odd#$M/x/}"
[ -f $M/x/MANIFEST ] || fail bad:manifest
set=$(sed -n 's/^set=//p' $M/x/MANIFEST)
want=$(sed -n 's/^set=//p' $ALLOW)
setprop sys.z9x.blobs.set "$set"
[ "$set" = "$want" ] || say "blob set '$set' is not this image's '$want' (files still checked one by one)"

# ---------------------------------------------------------------- check, label, bind
# alt=<firmware>:<sha256>: that stock firmware's own copy of the file, accepted only while that firmware's vendor runs
# (ro.vendor.build.version.incremental: in a trial run and on an installed Lumen OS /vendor is the unit's stock vendor).
# The need=eye files must all be the base copies or all this firmware's.
fw=$(getprop ro.vendor.build.version.incremental)
n=0; bad=; eye=0; eyebad=; eyebase=0; eyealt=0; Q=$M/eye.q
[ "$EYE" = 0 ] || : > $Q
while read -r h p s opt; do
  case "$h" in ''|\#*|set=*) continue ;; esac
  e=0; case " $opt " in *" need=eye "*) e=1; eye=$((eye + 1)) ;; esac
  if [ $e = 1 ] && [ "$EYE" = 0 ]; then bad=${bad:-option:$p}; continue; fi
  f=$M/x/$p
  if [ ! -f "$f" ]; then
    bad=${bad:-missing:$p}; [ $e = 0 ] || eyebad=${eyebad:-missing:$p}; continue
  fi
  got=$(sha256sum "$f" | cut -d' ' -f1)
  if [ "$got" = "$h" ]; then
    [ $e = 0 ] || eyebase=$((eyebase + 1))
  else
    a=0
    for o in $opt; do case "$o" in alt=*) [ -n "$fw" ] && [ "${o#alt=}" = "$fw:$got" ] && a=1 ;; esac; done
    if [ $a = 0 ]; then
      bad=${bad:-bad:$p}; [ $e = 0 ] || eyebad=${eyebad:-bad:$p}; rm -f "$f"; continue
    fi
    [ $e = 0 ] || eyealt=$((eyealt + 1))
  fi
  dst=/system/$p
  case " $opt " in
    *" bind="*) dst=${opt#*bind=}; dst=${dst%% *}
      # only a vendor config or VNDK APEX library; never an eye-protection file
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

if [ "$EYE" = 1 ]; then
  # the eye-protection drawer: every need=eye line verified, or none of them bound; never a base nativeui with this
  # firmware's library or the other way round
  [ $eye -gt 0 ] || eyebad=${eyebad:-bad:allow}
  [ $eyebase = 0 ] || [ $eyealt = 0 ] || eyebad=${eyebad:-bad:mixed}
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
fi
chmod 0755 $M
[ -n "$R" ] || mount -o remount,ro $M 2>/dev/null || say "$M stays writable (remount failed)"
if [ "$EYE" = 1 ]; then
  if [ -z "$eyebad" ]; then nui ok; else nui "$eyebad"; fi
fi
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
