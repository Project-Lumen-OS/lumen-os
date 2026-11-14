# SPDX-License-Identifier: Apache-2.0
# blobs_stock_dsu.sh: the blob files of a DSU trial, taken read-only from the stock slot. Shared by the blobs
# readers of the device images: sourced (POSIX sh), never run on its own. Enabled per model (DSU_STOCK_MODELS
# below) and only in an image whose reader sources it: today the X50 Ultra and the X50 Ultra Max (devices/g0111/image/x50_blobs.sh,
# image path /system/etc/z9x/blobs_stock_dsu.sh). The Z9X and Z9X HB readers do not source it.
#
# A DSU trial (ro.gsid.image_running=1, the stock gsid) boots Lumen OS from /data and leaves the stock super as it
# is: there is no z9x_blobs partition, but the stock system, system_ext and product of the booted slot are still
# there (first-stage init maps every logical partition of the slot) and /vendor is the stock vendor of that slot.
# stock_blobs_collect copies the allow-listed files from there into the reader's folder under the names the blobs
# tar uses, plus a MANIFEST; the reader then checks every file against the same sha256 allow-list and binds it
# exactly as one from the partition. Nothing read here is trusted: a file the stock slot lacks, or has with another
# sha256, is refused by the reader like a bad partition member.
#   allow-list source (3rd field)   read from
#     /system/...                   stock system<slot> (system-as-root: <root>/system/...)
#     /system_ext/..., /product/... stock system_ext<slot> / product<slot>, else that folder of stock system
#     /vendor/...                   /vendor (the stock vendor of this slot, mounted by first-stage init)
#     /apex/<name>/...              the stock APEX <name>.apex in /system/apex or /system_ext/apex of the stock slot
#                                   (com.android.vndk.current.apex for a VNDK APEX): its apex_payload.img
#     from=<sha256>:<sed script>    the source must have that sha256; the file is the source with those lines
#                                   deleted (N,Md commands only), as the installer derives it
# Read-only: every stock device gets blockdev --setro before its mount, every mount is ro, nodev, nosuid, noexec
# (erofs, or ext4 with noload: no journal replay); a partition first stage did not map is mapped with dmctl
# create -ro from lpdump's extents of the slot and deleted again. Regular files only, no symlink anywhere in the
# path (the resolved path must stay inside the stock mount), at most MAXF bytes. Every stock mount, loop device and
# dm device made here is gone when stock_blobs_collect returns: the copies live in the reader's own tmpfs. Bounded,
# since init waits for the reader: each mount, lpdump and dmctl call has a 3 s timeout, the unzip of an APEX
# payload 5 s.
#
#   stock_blobs_collect MODEL ALLOW OUT SCRATCH MAXF
#     MODEL    the model the calling image is for: must be in DSU_STOCK_MODELS and equal this unit's
#              ro.boot.xgimi.modelname
#     ALLOW    the image's allow-list; OUT the reader's folder (OUT/<path>, OUT/MANIFEST); SCRATCH an empty folder
#              for the stock mounts (a 64 MiB tmpfs on the device); MAXF the largest file taken
#   Return 0 when a stock system of the slot was mounted and read (the files it lacks are the reader's
#   missing:<path>), 1 otherwise. STOCK_WHY says why not (notdsu, model, slot, tmpfs, nostock), STOCK_N how many
#   files were copied. $R is the test root (empty on the device), log goes to the caller's TAG.
DSU_STOCK_MODELS="G0111 G0100 G0155"

stock_say() { log -t "${TAG:-blobs_stock}" "$*"; }

stock_dev() {  # PARTITION<slot>: STOCK_DEV = its block device, mapped read-only here when first stage did not
  STOCK_DEV=$R/dev/block/mapper/$1
  if [ -b "$STOCK_DEV" ] || { [ -n "$R" ] && [ -f "$STOCK_DEV" ]; }; then return 0; fi
  STOCK_DEV=
  command -v lpdump >/dev/null 2>&1 && command -v dmctl >/dev/null 2>&1 || return 1
  _t=$(timeout 3 lpdump --slot="$_sn" 2>/dev/null | awk -v n="$1" -v sup="$R/dev/block/by-name/super" '
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

stock_inside() {  # FILE TOP: stock_regular and at most _max bytes
  stock_regular "$1" "$2" && [ $(($(wc -c < "$1"))) -le "$_max" ]
}

stock_apex() {  # NAME: STOCK_APEX = the folder with that stock APEX's payload mounted (once per name)
  STOCK_APEX=$_s/apex/$1
  case " $_apexok " in *" $1 "*) return 0 ;; esac
  case " $_apexbad " in *" $1 "*) return 1 ;; esac
  _apexbad="$_apexbad $1"
  _af=
  _cur=; case "$1" in com.android.vndk.v*) _cur=com.android.vndk.current ;; esac
  for _n in "$1" $_cur; do
    for _c in "$_s/system/system/apex/$_n.apex" "$_s/system_ext/apex/$_n.apex" "$_s/system/system/system_ext/apex/$_n.apex"; do
      if stock_regular "$_c" "$_s"; then _af=$_c; break 2; fi
    done
  done
  [ -n "$_af" ] || return 1
  mkdir -p "$STOCK_APEX" "$_s/zip/$1" || return 1
  timeout 5 unzip -q -o -d "$_s/zip/$1" "$_af" apex_payload.img >/dev/null 2>&1 || return 1
  [ -f "$_s/zip/$1/apex_payload.img" ] || return 1
  stock_mount "$_s/zip/$1/apex_payload.img" "$STOCK_APEX" loop || return 1
  _apexok="$_apexok $1"
}

stock_cleanup() {  # every mount (newest first), the dm devices made here, the scratch tmpfs
  _left=0
  for _d in $_mnts; do umount "$_d" 2>/dev/null || { _left=1; stock_say "$_d stays mounted (umount failed)"; }; done
  _mnts=
  for _d in $_dms; do timeout 3 dmctl delete "$_d" >/dev/null 2>&1 || stock_say "dm device $_d stays (delete failed)"; done
  _dms=
  [ $_left = 1 ] && return 0
  if [ -z "$R" ]; then umount "$_s" 2>/dev/null && rmdir "$_s" 2>/dev/null; else rm -rf "$_s"; fi
  return 0
}

stock_blobs_collect() {
  _model=$1; _allow=$2; _out=$3; _s=$4; _max=$5
  STOCK_WHY=; STOCK_N=0; _mnts=; _dms=; _apexok=; _apexbad=
  [ "$(getprop ro.gsid.image_running)" = 1 ] || { STOCK_WHY=notdsu; return 1; }
  case " $DSU_STOCK_MODELS " in *" $_model "*) ;; *) STOCK_WHY=model; return 1 ;; esac
  [ "$(getprop ro.boot.xgimi.modelname)" = "$_model" ] || { STOCK_WHY=model; return 1; }
  _slot=$(getprop ro.boot.slot_suffix)
  case "$_slot" in _a) _sn=0 ;; _b) _sn=1 ;; *) STOCK_WHY=slot; return 1 ;; esac
  mkdir -p "$_s" "$_out" || { STOCK_WHY=mkdir; return 1; }
  if [ -z "$R" ]; then
    mount -t tmpfs -o mode=0700,uid=0,gid=0,size=64m,nosuid,nodev,noexec tmpfs "$_s" 2>/dev/null \
      || { STOCK_WHY=tmpfs; return 1; }
  fi
  _sys=0
  for _p in system system_ext product; do
    mkdir -p "$_s/$_p"
    if stock_dev "$_p$_slot" && stock_mount "$STOCK_DEV" "$_s/$_p"; then
      [ $_p = system ] && _sys=1
    else
      [ $_p = product ] || stock_say "stock $_p$_slot not mounted"
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
        _fr=${_opt#*from=}; _fr=${_fr%% *}; _fh=${_fr%%:*}; _sc=${_fr#*:}
        case "$_sc" in ''|*[!0-9,d\;]*) continue ;; esac
        [ "$(sha256sum "$_c" | cut -d' ' -f1)" = "$_fh" ] || continue
        sed -e "$_sc" "$_c" > "$_o" || { rm -f "$_o"; continue; } ;;
      *) cat "$_c" > "$_o" || { rm -f "$_o"; continue; } ;;
    esac
    STOCK_N=$((STOCK_N + 1))
  done < "$_allow"
  echo "set=$(sed -n 's/^set=//p' "$_allow")" > "$_out/MANIFEST"
  stock_cleanup
  return 0
}
