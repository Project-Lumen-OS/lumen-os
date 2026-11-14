#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Lumen OS installer for the XGIMI Z9X and the XGIMI Play 6 (macOS and Linux, bash 3.2 compatible).
#
#   bash lumen-install.sh [options] [command]
#   bash lumen-install.sh --ip ADDRESS [options] [command]      the XGIMI Play 6 (over Wi-Fi, see below)
#
# Commands, XGIMI Z9X (without one: continue the install from where it stopped):
#   check      read-only: computer, cable, projector, image, signatures. Writes nothing anywhere.
#   backup     copy your user apps (APKs), photos/videos/downloads and setting lists to the computer
#   blobs      public images only: copy the MediaTek video codec and audio files from YOUR projector's
#              own system, check them against blobs_allow.txt, build z9x_blobs.img (flashed by 'flash')
#   vbmeta     explains the one manual step (vbmeta). The installer never runs a vbmeta command.
#   flash      writes Lumen OS: system (+ z9x_blobs), then the full data wipe (format, never erase)
#   verify     after the first boot: checks over USB that everything came up
#   gsf        shows the Google Services Framework ID for google.com/android/uncertified (only when Google
#              services are there: built in, or added with 'gapps')
#   gapps      Lumen OS without Google: add Google services from YOUR OWN download of MindTheGapps (the
#              guide names the one accepted file): gapps --zip FILE. They go into their own partition
#              z9x_gapps_<slot> (kept by updates and factory resets), then the data is wiped like 'flash'
#              does (--keep-data: not). gapps --remove takes them out again (also from fastboot mode, after
#              failed starts), with the same wipe. 'gapps' alone shows their state
#   status     shows the saved progress.  reset   forgets it (does not touch the projector)
#
# Options:
#   --image FILE        system image (default: the one lumen-os-*-system.img next to this script or in ..,
#                       with several there, e.g. with and without Google, --image is required)
#   --serial SERIAL     the projector's adb serial (default: the only connected device)
#   --keep-data         'flash' writes system only, no wipe (repair of an installed Lumen OS)
#   --wipe=format|factory-reset   how the data wipe is done (default format, see the guide)
#   --lang en|ru        language of the messages (default from $LANG)
#   --yes               do not ask before read-only steps (the wipe is always confirmed)
#   --blobs=partition|embedded   override release.conf (published default: partition, codec and audio
#                       files from your own projector). The maintainer's private image carries them: --blobs=embedded
#   --no-blobs          public image without those files (skips 'blobs': NO SOUND from the projector and
#                       no protected video, e.g. Kinopoisk HD, only for a first test). Same as
#                       --blobs=none or the environment BLOBS=none
#   --unsigned-image    your own build without SHA256SUMS + SHA256SUMS.sig: the image is NOT
#                       authenticated. Asks you to type UNSIGNED (never use it for a downloaded image)
#   --zip FILE          'gapps': the MindTheGapps zip you downloaded.  --remove  'gapps': take them out
#   --gapps-zip FILE    the full install (no command) with Google services in one pass and ONE data wipe:
#                       the MindTheGapps zip the guide names for this projector. Not with --keep-data or
#                       --wipe=factory-reset. XGIMI Z9X: the Google services partition is built on this computer
#                       (erofs, tools/erofs-utils) before the restart, then ONE fastbootd visit writes system, the
#                       Google services and wipes the data. No USB debugging or Rooted debugging needed after it.
#                       XGIMI Play 6: Lumen OS starts once on the old data and the Google apps are filled in as root
#   --erofs-utils DIR   use mkfs.erofs, fsck.erofs and dump.erofs (erofs-utils 1.9.4) in DIR instead of the
#                       installer's own tools/erofs-utils: for a computer it ships none for. The image built with
#                       them must still be bit for bit the checked one (lib/gapps_images.txt)
#   --ip ADDR[:PORT]    the XGIMI Play 6's Wi-Fi address (port 5555 if not given): it has no USB debugging
#   --device z9x|play6|ID  which projector (default: the one this download is for, or an unfinished install,
#                       ID: a projector of lib/devices.conf with FLOW=ab-fastbootd, see below)
#   --platform-tools DIR  use the adb and fastboot in DIR (Platform-Tools 35 or newer, with mke2fs and
#                       make_f2fs) instead of downloading them: for a computer without internet access
#
# adb and fastboot: the installer never uses the ones on PATH. On first use it downloads Google's
# Platform-Tools 35.0.2 (zip from dl.google.com, sha256 pinned in the script) into tools/platform-tools next to
# this script and reuses that folder after checking it again (a platform-tools_r35.0.2-<os>.zip already in tools/
# is checked and used without a download). Its adb server runs on its own port (5038), so adb servers of
# other programs cannot get in the way, and only that server is stopped at the end. Every adb and fastboot
# call has a time limit: a call that hangs stops the installer with a message instead of waiting for ever.
#
# XGIMI Z9X, what it writes, ever: system_<slot>, z9x_blobs_<slot> (create/resize/flash), z9x_gapps_<slot>
# (--gapps-zip: create/resize/flash of the erofs image built here, 'gapps': create/resize/format, filled over adb as
# root, delete), and formats userdata, metadata and cache. It may
# delete only *-cow leftovers and XGIMI's unused product_<slot> and system_ext_<slot>. Every other
# partition name is refused by fb_allowed(). It NEVER touches vbmeta,
# boot, vendor, dtbo, mboot, persist, xgimi*, tvconfig, misc, frp or the other slot, and never runs
# 'fastboot -w' or 'fastboot erase' (proven boot hang on this projector).
#
# XGIMI Play 6 (its model code, firmware versions and image name are in lib/devices.conf, guide:
# docs/install/play6/<language>.md). The computer talks to stock Android over Wi-Fi (network
# debugging on). Fastboot mode is reached over a USB A-to-A cable in the projector's one USB port, plugged
# in only for the write. Commands:
#   (none)     check, backup (asked), flash, then the first start. Continues where it stopped
#   check      read-only: computer, Wi-Fi connection, projector, firmware, image, signature, and the pre-checks
#              (Wi-Fi chip, serial, firmware, battery and adapter, what is in the USB port, install.log)
#   backup     as for the Z9X, over Wi-Fi
#   flash      writes system in fastboot mode and, in the same visit, the erase request for XGIMI's recovery
#              (the 2 KiB in misc that Android's factory reset writes there): the recovery erases all data,
#              Wi-Fi too, BEFORE the first start of Lumen OS, which so runs on clean data. If fastboot mode
#              refuses the request or the recovery does not carry it out, the first start runs on the old
#              data and Android's own factory reset follows (as root over adb). --keep-data: no erase, a repair
#   verify     later, after joining Wi-Fi and turning debugging on again: checks the clean install
#   stick DIR  read-only check of a stock-restore USB stick (MBR, one FAT32 partition, the .bin, an empty
#              factory.txt)
#   gapps      add Google services from YOUR OWN download of MindTheGapps for 32-bit Android 14 TV (the
#              guide names the one accepted file): gapps --zip FILE. They go into their own partition
#              lumen_gapps (kept by updates and factory resets), then the data is wiped by Android's factory
#              reset (--keep-data: not). gapps --remove takes them out again, with the same wipe. 'gapps'
#              alone shows their state
#   gsf        shows the Google Services Framework ID for google.com/android/uncertified
#   status, reset (--device play6 reset forgets only the Play 6's progress)
# What it writes on the Play 6: 'system', the 2 KiB erase request at the start of misc (the recovery's
# command block, nothing else in misc). 'gapps' also creates, resizes or deletes the logical partition
# lumen_gapps in super (fb_allowed: nothing else, no format, erase or -w), then formats and fills it on the
# projector (adb root, lib/play6/gapps_fill.sh) and writes /cache/lumen_gapps_off while it does. Every data
# wipe is carried out by XGIMI's recovery, as for Settings > Reset. Never vendor, boot, recovery, MBOOT, env
# (MPOOL), calibration or factory data.
#
# Projectors with FLOW=ab-fastbootd in lib/devices.conf (their model codes, firmware, slots and image name are
# there, guide: docs/install/<GUIDE>/<language>.md). The computer talks to their system over the network (--ip
# ADDRESS. On stock, Developer options > USB debugging opens it). Fastbootd is reached over a USB A-to-A cable in
# the projector's USB port, plugged in when the installer asks. Commands:
#   (none)     check, backup (asked), vbmeta (asked), blobs, flash. Continues where it stopped
#   check      read-only: computer, network connection, projector, firmware, slot, the vbmeta state, image,
#              signature
#   backup     as for the Z9X, over the network
#   vbmeta     shows the one manual step (Google's vbmeta.img, both slots). The installer never runs it
#   blobs      copies the MediaTek / XGIMI files of the profile's allow-list from YOUR projector's own system
#   flash      in one fastbootd visit: z9x_blobs_<slot>, system_<slot>, then 'fastboot format' of userdata
#              and metadata with the file system of the projector's stock fstab (ext4 on the HB, f2fs with
#              casefold and project quota for userdata on the X50: the profile's WIPE_FS). The full data wipe,
#              never erase or -w. No --keep-data
#   verify     after the setup, with debugging on again: checks the install over the network
#   gapps      (a profile with GAPPS_ALLOW) add Google services from YOUR OWN download of MindTheGapps (the guide
#              names the one accepted file) to the installed Lumen OS: gapps --zip FILE. The add-on image is built
#              on this computer (erofs) and, in one fastbootd visit, written into z9x_gapps_<slot>, then
#              userdata and metadata are formatted (the data wipe). gapps --remove deletes it again, with the same
#              wipe. 'gapps' alone shows their state. --gapps-zip FILE with the full install (no command): the
#              same add-on in the install's own fastbootd visit, after system and before the wipe
#   gsf        shows the Google Services Framework ID for google.com/android/uncertified (adb root)
#   status, reset (--device ID reset forgets only that projector's progress)
# What it writes there: system_<slot> and z9x_blobs_<slot> (create, resize, flash) of the running slot (a or b,
# the one fastbootd reports) when the profile's SLOTS has it, z9x_gapps_<slot> of that slot (create, resize,
# flash, delete, only with GAPPS_ALLOW), and the format of userdata and metadata, only while no update is staged or
# merging (snapshot-update-status none).
# Never vbmeta, boot, vendor, the KEEP partitions, the other slot, --slot, set_active, a delete of anything but
# z9x_gapps_<slot>, erase or -w (fb_allowed). stick: not yet.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
PT_VER=35.0.2           # Google's Platform-Tools this installer downloads (sha256 of each zip as published)
PT_SHA_darwin=1820078db90bf21628d257ff052528af1c61bb48f754b3555648f5652fa35d78
PT_SHA_linux=acfdcccb123a8718c46c46c059b2f621140194e5ec1ac9d81715be3d6ab6cd0a
PT_URL=https://dl.google.com/android/repository
ADB_PORT=5038           # the installer's own adb server (adb's default is 5037)
PT_OPT=${LUMEN_PLATFORM_TOOLS:-}   # --platform-tools DIR
PT_DIR=""               # the adb and fastboot in use (set by pt_setup)
PT_SHA=""
PT_SERVER=0
PT_SLEEP=/bin/sleep     # the watchdog's clock: the system's own sleep, never one found on PATH
[ -x "$PT_SLEEP" ] || PT_SLEEP=/usr/bin/sleep
[ -x "$PT_SLEEP" ] || PT_SLEEP=$(command -pv sleep 2>/dev/null || echo sleep)
STATE_DIR=${LUMEN_STATE_DIR:-$HOME/.lumen-installer}
BACKUP_ROOT=${LUMEN_BACKUP_DIR:-$HOME/Lumen-backup}
GUIDE_BASE="https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install"   # one guide per projector and language
GOOGLE_GSI="https://source.android.com/docs/core/tests/vts/gsi#flashing-gsis"
BLOB_SIZE=4194304
SERIAL=""
IMG=""
KEEP_DATA=0
UNSIGNED=0
WIPE=format
YES=0
LANG_UI=en            # the language of the messages (lib/messages.tsv): from $LANG, or --lang
case "${LANG:-}" in
  ru*|RU*) LANG_UI=ru ;;
  zh_TW*|zh_HK*|zh_MO*) LANG_UI=zh-TW ;;
  zh*) LANG_UI=zh-CN ;;
  ar*|be*|de*|es*|fr*|it*|ja*|kk*|ko*|pl*|pt*|tr*|uk*) LANG_UI=${LANG%"${LANG#??}"} ;;
esac
BLOBS_ENV=${BLOBS:-}                                    # an explicit environment value wins over release.conf
DEVICE=""
[ -f "$HERE/release.conf" ] && . "$HERE/release.conf"   # BLOBS=partition|embedded, VERSION=..., DEVICE=z9x|play6
BLOBS=${BLOBS_ENV:-${BLOBS:-partition}}
REL_DEVICE=$DEVICE                                      # the projector this download's image is for (empty: z9x)
FORCE_HASH=0
GAPPS_ZIP=""
GAPPS_REMOVE=0
GAPPS_WITH=""         # --gapps-zip: the full install with Google services in one pass
PROJECTOR=""          # the projector: an id of lib/devices.conf (resolve_device), its FLOW picks the steps
DEVICE_OPT=""
IP=""
IP_GIVEN=0
ADB_T=""              # XGIMI Play 6: adb over Wi-Fi, ADDR:PORT (SERIAL is then the fastboot serial = ro.serialno)
SD=$STATE_DIR         # where the device's progress is kept (the Z9X: $STATE_DIR/state, as always)
IMG_GLOB="lumen-os-*-system.img"
STICK=""
ERASE_NOW=0
WIPED_NOW=0
P6_BCB_OK=0           # XGIMI Play 6: set only while p6_bcb_write writes its checked erase request into misc
P6_PRE_DONE=0         # XGIMI Play 6: the pre-checks of this run are done (p6_prechecks)

# ------------------------------------------------------------------ helpers
MSG_FILE=$HERE/lib/messages.tsv   # every text the user reads, by id, in each language
MSG_LANG=""
msg_load() {  # the texts of $LANG_UI as M_<id> (lib/messages.tsv; English where a text has no translation)
  local id s
  [ -f "$MSG_FILE" ] || { printf '%s\n' "lib/messages.tsv is missing: download the installer again. Nothing was done." >&2; exit 1; }
  while IFS=$'\t' read -r id s; do
    printf -v "M_$id" '%b' "$s"
  done < <(awk -F '\t' -v l="$LANG_UI" '/^#/ { next }
    !h { for (i = 2; i <= NF; i++) if ($i == l) c = i; h = 1; next }
    $1 ~ /^[a-z0-9_]+$/ { print $1 "\t" ((c && $c != "") ? $c : $2) }' "$MSG_FILE")
  MSG_LANG=$LANG_UI
}
m() {  # id [value...]: the text of id in the language of the messages, {1}, {2}, ... filled in with the values
  local n=M_$1 s r="" k
  [ "$MSG_LANG" = "$LANG_UI" ] || msg_load   # --lang given just now (the option loop)
  s=${!n-"[$1]"}
  shift
  while :; do
    case $s in
      *\{[1-9]*) ;;
      *) break ;;
    esac
    k=${s%%\{[1-9]*}; r=$r$k; s=${s#"$k"}
    case $s in
      \{[1-9]\}*) k=${s:1:1}; s=${s:3} ;;
      \{[1-9][0-9]\}*) k=${s:1:2}; s=${s:4} ;;
      *) r=$r{; s=${s:1}; continue ;;
    esac
    r=$r${!k-}
  done
  printf '%s' "$r$s"
}
msg_load
say() { printf '%s %s\n' "$(date +%H:%M:%S)" "$*"; }
ok() { say "  ✓ $*"; }
warn() { say "  ! $*"; }
die() { say "$(m stop): $*"; exit 1; }
sha256() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
# identity of the file on disk (size, mtime, inode): a cached check is only valid for exactly that file
filekey() { local m; m=$(stat -f '%z %m %i' "$1" 2>/dev/null) || m=$(stat -c '%s %Y %i' "$1" 2>/dev/null) || m="?"; printf '%s' "$m" | tr ' ' ':'; }
ask() {  # ask "question" -> 0 if yes
  local a
  printf '%s [%s] ' "$1" "$(m yes_no)"
  read -r a
  case "$a" in y|Y|yes|Yes|YES|д|Д|да|Да|ДА) return 0 ;; '') return 1 ;; esac
  case " $(m answer_yes) " in *" $a "*) return 0 ;; esac   # the words of the language of the messages
  return 1
}
# progress of the projector in use: $SD/state ($SD = $STATE_DIR on the Z9X, $STATE_DIR/play6 on the Play 6)
state_get() { [ -f "$SD/state" ] && sed -n "s/^$1=//p" "$SD/state" | tail -1; }
state_set() {
  mkdir -p "$SD"
  { [ -f "$SD/state" ] && grep -v "^$1=" "$SD/state"; echo "$1=$2"; } > "$SD/state.tmp"
  mv "$SD/state.tmp" "$SD/state"
}
# ------------------------------------------------------------------ adb and fastboot (own copy, time limits)
adb() { pt_call adb "$@"; }
fastboot() { pt_call fastboot "$@"; }
pt_run() {  # seconds program args...: runs it; 124 = it did not finish in time and was stopped
  local secs=$(pt_cap "$1") p w rc fired; shift
  "$@" <&0 9>&- &
  p=$!
  ( st=1; s=""
    trap 'kill $s 2>/dev/null; exit $st' TERM
    "$PT_SLEEP" "$secs" & s=$!; wait $s || exit 1
    st=0; kill -TERM "$p" 2>/dev/null
    "$PT_SLEEP" 3 & s=$!; wait $s; kill -KILL "$p" 2>/dev/null; exit 0 ) </dev/null >/dev/null 2>&1 9>&- &
  w=$!
  pt_reap "$p"; rc=$?
  kill -TERM "$w" 2>/dev/null; pt_reap "$w"; fired=$?
  [ "$fired" = 0 ] && [ "$rc" -ge 128 ] && return 124
  return "$rc"
}
pt_reap() {  # pid: waits for it and returns its exit status. bash 3.2 (macOS /bin/bash) can miss the SIGCHLD of a
  # child that ends just as 'wait' starts and then sleeps until another child ends: in pt_run the watchdog, minutes
  # later (seen: a 7 s flash waited 300 s, an adb shell 1800 s). Short sleeps keep children ending, so a missed
  # signal costs at most a tenth of a second.
  while kill -0 "$1" 2>/dev/null; do "$PT_SLEEP" 0.1; done
  wait "$1" 2>/dev/null
}
pt_cap() { if [ -n "${LUMEN_TIMEOUT_MAX:-}" ] && [ "$1" -gt "$LUMEN_TIMEOUT_MAX" ]; then echo "$LUMEN_TIMEOUT_MAX"; else echo "$1"; fi; }   # tests only
pt_sub() {  # args...: the command word after the options (-s SERIAL, --fs-options=...)
  while [ $# -gt 0 ]; do
    case "$1" in
      -s|-t) if [ $# -ge 2 ]; then shift 2; else shift; fi ;;
      --version) echo version; return ;;
      -*) shift ;;
      *) echo "$1"; return ;;
    esac
  done
}
pt_limit() {  # adb|fastboot args...: seconds the call may take (generous: only a hang reaches it)
  local tool=$1 sub sz; shift
  sub=$(pt_sub "$@")
  case "$tool $sub" in
    *" version"|"adb devices"|"adb start-server"|"adb kill-server"|"adb get-state"|"adb get-serialno"|"adb disconnect"|"adb mdns") echo 20 ;;
    "adb connect") echo 30 ;;
    "adb root"|"adb unroot"|"adb reboot"|"adb reconnect") echo 60 ;;
    "adb wait-for-"*) echo 180 ;;
    "adb pull") echo 7200 ;;
    "adb shell"|"adb push"|"adb install"*) echo 1800 ;;
    "fastboot devices") echo 20 ;;
    "fastboot getvar") echo 60 ;;
    "fastboot reboot"*) echo 180 ;;
    "fastboot flash")   # at least 2 MB/s from the disk to the projector (USB 2.0 does about 30)
      eval "sz=\${$#}"; sz=$(wc -c < "$sz" 2>/dev/null | tr -d ' '); echo $((300 + ${sz:-0} / 2097152)) ;;
    *) echo 600 ;;
  esac
}
pt_call() {  # adb|fastboot args...: our own copy, within the time limit of that call
  local tool=$1 secs rc; shift
  [ -n "$PT_DIR" ] || die "internal: $tool before the Platform-Tools check"
  secs=$(pt_cap "$(pt_limit "$tool" "$@")")
  pt_run "$secs" "$PT_DIR/$tool" "$@"; rc=$?
  [ "$rc" = 124 ] || return "$rc"
  if [ "$tool" = fastboot ] && [ "$(pt_sub "$@")" = flash ]; then   # the caller says what that means for the projector
    say "  ! $(m fastboot_did_not_finish_s "$*" "$secs")" >&9
    return 124
  fi
  say "$(m stop): $(m did_not_answer_within_s "$tool" "$*" "$secs")" >&9   # fd 9: the terminal, also when the caller hides stderr
  kill -TERM $$ 2>/dev/null   # the whole installer stops, also from inside $(...)
  exit 1
}
pt_major() { sed -n -e 's/^Version \([0-9][0-9]*\)\..*/\1/p' -e 's/^fastboot version \([0-9][0-9]*\)\..*/\1/p' | head -n 1; }
pt_files() { echo adb; echo fastboot; echo mke2fs; echo make_f2fs; }   # what the installer runs or fastboot needs
pt_marker_ok() {  # DIR: our checked unpack (zip sha256, and the sha256 of each file it runs)
  local d=$1 f want
  [ -f "$d/.lumen-platform-tools" ] || return 1
  [ "$(sed -n 's/^zip //p' "$d/.lumen-platform-tools")" = "$PT_SHA" ] || return 1
  for f in $(pt_files); do
    want=$(awk -v f="$f" '$2 == f { print $1 }' "$d/.lumen-platform-tools")
    [ -n "$want" ] && [ -f "$d/$f" ] && [ "$(sha256 "$d/$f")" = "$want" ] || return 1
  done
}
pt_fetch() {  # zip name: the pinned zip into $HERE/tools (kept: a later run checks it again instead of downloading)
  local zip=$1 url=$PT_URL/$1 dir=$HERE/tools
  mkdir -p "$dir" 2>/dev/null && [ -w "$dir" ] || die "$(m cannot_write_copy_installer_folder "$dir")"
  if [ -f "$dir/$zip" ] && [ "$(sha256 "$dir/$zip")" = "$PT_SHA" ]; then return 0; fi
  say "$(m downloading_googles_android_sdk "$PT_VER" "$url")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 3 --connect-timeout 20 --speed-limit 1024 --speed-time 60 -o "$dir/$zip.part" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -T 60 -t 3 -O "$dir/$zip.part" "$url"
  else
    false
  fi || die "$(m download_failed_no_internet "$url" "$dir")"
  [ "$(sha256 "$dir/$zip.part")" = "$PT_SHA" ] || { rm -f "${dir:?}/${zip:?}.part"; die "$(m downloaded_not_googles_file_sha256 "$zip")"; }
  mv "$dir/$zip.part" "$dir/$zip"
}
pt_unpack() {  # zip name -> $HERE/tools/platform-tools, with the marker that lets later runs reuse it
  local zip=$HERE/tools/$1 new=$HERE/tools/.platform-tools.new f
  rm -rf "${new:?}"; mkdir -p "$new"
  if command -v unzip >/dev/null 2>&1; then unzip -q "$zip" -d "$new"
  else python3 -I -m zipfile -e "$zip" "$new"; fi \
    || { rm -rf "${new:?}"; die "$(m cannot_unpack_install_unzip "$zip")"; }
  [ -f "$new/platform-tools/adb" ] || { rm -rf "${new:?}"; die "internal: $zip has no platform-tools/adb"; }
  for f in $(pt_files) make_f2fs_casefold; do [ -f "$new/platform-tools/$f" ] && chmod 755 "$new/platform-tools/$f"; done
  { echo "zip $PT_SHA"; for f in $(pt_files); do [ -f "$new/platform-tools/$f" ] && echo "$(sha256 "$new/platform-tools/$f") $f"; done; } \
    > "$new/platform-tools/.lumen-platform-tools"
  rm -rf "${HERE:?}/tools/platform-tools"; mv "$new/platform-tools" "$HERE/tools/platform-tools"; rm -rf "${new:?}"
}
pt_setup() {  # adb and fastboot of our own, checked; our own adb server started (it answers within seconds)
  local os="" zip dir v src
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) case "$(uname -m)" in x86_64|amd64) os=linux ;; esac ;;
  esac
  if [ -n "$PT_OPT" ]; then
    dir=$(cd "$PT_OPT" 2>/dev/null && pwd) && [ -x "$dir/adb" ] && [ -x "$dir/fastboot" ] \
      || die "$(m platform_tools_no_adb_fastboot "$PT_OPT")"
    src=$dir
  else
    [ -n "$os" ] || die "$(m googles_platform_tools_run_macos "$(uname -s)" "$(uname -m)")"
    eval "PT_SHA=\$PT_SHA_$os"; zip=platform-tools_r$PT_VER-$os.zip; dir=$HERE/tools/platform-tools
    if ! pt_marker_ok "$dir"; then
      pt_fetch "$zip"; pt_unpack "$zip"
      pt_marker_ok "$dir" || die "internal: the unpacked Platform-Tools do not check out"
    fi
    src="tools/platform-tools, $(m checked)"
  fi
  PT_DIR=$dir
  exec 9>&2
  export ANDROID_ADB_SERVER_PORT=$ADB_PORT; unset ADB_SERVER_SOCKET
  PT_ADBV=$(pt_run 20 "$dir/adb" version 2>/dev/null </dev/null | tr -d '\r')
  PT_FBV=$(pt_run 20 "$dir/fastboot" --version 2>/dev/null </dev/null | tr -d '\r')
  for v in "$(printf '%s\n' "$PT_ADBV" | pt_major)" "$(printf '%s\n' "$PT_FBV" | pt_major)"; do
    [ -n "$v" ] && [ "$v" -ge 35 ] 2>/dev/null || die "$(m adb_fastboot_too_old_not "$dir")"
  done
  PT_ADBV=$(printf '%s\n' "$PT_ADBV" | head -n 1 | awk '{print $NF}'); PT_FBV=$(printf '%s\n' "$PT_FBV" | head -n 1 | awk '{print $3}')
  trap pt_cleanup EXIT; trap 'exit 1' TERM; trap 'exit 130' INT
  PT_SERVER=1
  pt_run 20 "$dir/adb" start-server >/dev/null 2>&1 </dev/null
  case $? in
    0) ;;
    124) die "$(m adb_did_not_start_within "$ADB_PORT")" ;;
    *) die "$(m adb_did_not_start_port "$ADB_PORT")" ;;
  esac
  ok "Platform-Tools $(printf '%s' "$PT_FBV" | cut -d- -f1) ($src), $(m own_adb_server_port) $ADB_PORT"
}
pt_cleanup() {  # only our own adb server, never another program's
  [ "$PT_SERVER" = 1 ] && pt_run 10 "$PT_DIR/adb" kill-server >/dev/null 2>&1 </dev/null
  PT_SERVER=0
}
adbs() { adb -s "${ADB_T:-$SERIAL}" "$@"; }   # ADB_T: the Play 6 over Wi-Fi; never set on the Z9X
prop() { adbs shell getprop "$1" 2>/dev/null | tr -d '\r'; }
fb() { fastboot -s "$SERIAL" "$@"; }

fb_allowed() {  # action partition -> 0 if the installer may touch it
  # XGIMI Play 6: system, and the Google services add-on partition lumen_gapps ('gapps': create, resize,
  # delete; it is formatted and filled on the projector). Single slot; never format, erase or another flash.
  # misc: only the 2 KiB erase request for XGIMI's recovery (p6_bcb_write checks its bytes before the write).
  if [ "$P_FLOW" = play6-wifi ]; then
    case "$1:$2" in flash:system|create:lumen_gapps|resize:lumen_gapps|delete:lumen_gapps) return 0 ;; esac
    [ "$1:$2" = flash:misc ] && [ "$P6_BCB_OK" = 1 ] && return 0
    return 1
  fi
  # FLOW=ab-fastbootd: system and z9x_blobs of the slot abf_fbd_check found running (ABF_S, one of SLOTS), the
  # Google services add-on z9x_gapps of that slot when the profile has GAPPS_ALLOW (the only delete), and the
  # format of userdata and metadata when WIPE_PARTS names them. No erase, vbmeta, KEEP partition or other slot.
  if [ "$P_FLOW" = ab-fastbootd ]; then
    case " $P_SLOTS " in *" ${ABF_S#_} "*) ;; *) return 1 ;; esac
    case "$1:$2" in
      flash:"system$ABF_S"|create:"z9x_blobs$ABF_S"|resize:"z9x_blobs$ABF_S"|flash:"z9x_blobs$ABF_S") return 0 ;;
      create:"z9x_gapps$ABF_S"|resize:"z9x_gapps$ABF_S"|flash:"z9x_gapps$ABF_S"|delete:"z9x_gapps$ABF_S")
        [ -n "$P_GAPPS_ALLOW" ] && return 0 ;;
      format:userdata|format:metadata)   # never while an update is staged or merging (the last getvar all)
        [ "$(abf_gv snapshot-update-status)" = none ] || return 1
        case " $P_WIPE " in *" $2 "*) return 0 ;; esac ;;
    esac
    return 1
  fi
  local s; s=$(state_get slot)
  case "$1:$2" in
    flash:system|flash:"system$s") return 0 ;;
    flash:"z9x_blobs$s"|create:"z9x_blobs$s"|resize:"z9x_blobs$s") return 0 ;;
    create:"z9x_gapps$s"|resize:"z9x_gapps$s"|format:"z9x_gapps$s"|delete:"z9x_gapps$s"|flash:"z9x_gapps$s") return 0 ;;
    delete:*-cow) return 0 ;;
    delete:"product$s"|delete:"system_ext$s") return 0 ;;
    format:userdata|format:metadata|format:cache) return 0 ;;
  esac
  return 1
}
fb_do() {  # action partition [args...]: every partition write goes through here
  local act=$1 part=$2; shift 2
  fb_allowed "$act" "$part" || die "internal: $act $part is not on the allow-list"
  case "$act" in
    flash) fb flash "$part" "$@" ;;
    create) fb create-logical-partition "$part" "$@" ;;
    resize) fb resize-logical-partition "$part" "$@" ;;
    delete) fb delete-logical-partition "$part" ;;
    format)
      # FLOW=ab-fastbootd: fastbootd says raw, so the type is given: the profile's WIPE_FS, the file system its
      # stock fstab mounts there (ext4 on the HB, f2fs on the X50); f2fs as the Z9X way below
      if [ "$P_FLOW" = ab-fastbootd ] && [ "$P_WIPE_FS" = f2fs ]; then
        if [ "$part" = userdata ]; then fb --fs-options=casefold,projid format:f2fs userdata
        else fb format:f2fs "$part"; fi   # metadata
      elif [ "$P_FLOW" = ab-fastbootd ]; then fb format:ext4 "$part"
      elif [ "$part" = userdata ]; then fb --fs-options=casefold,projid format:f2fs userdata
      elif [ "$part" = metadata ]; then fb format:f2fs metadata
      else fb format:ext4 "$part"; fi ;;  # cache, z9x_gapps_<slot>
  esac
}

pick_device() {
  local list n
  list=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}')
  if [ -n "$SERIAL" ]; then
    printf '%s\n' "$list" | grep -qx "$SERIAL" && return 0
    [ "$SERIAL_FROM_STATE" = 1 ] || die "$(m device_not_connected_over_adb "$SERIAL")"
    SERIAL=""
  fi
  n=$(printf '%s\n' "$list" | grep -c .)
  if [ "$n" != 1 ]; then
    adb devices
    die "$(m exactly_one_projector_must "$n")"
  fi
  SERIAL=$list
}

find_image() {
  [ -n "$IMG" ] && { [ -f "$IMG" ] || die "$(m no_image "$IMG")"; image_owner; return; }
  local found f n=0
  for f in "$HERE"/$IMG_GLOB "$HERE"/../$IMG_GLOB; do
    [ -f "$f" ] || continue
    n=$((n + 1)); found=$f
  done
  [ "$n" -gt 0 ] || die "$(m no_found_use_image_file "$IMG_GLOB")"
  # never pick one of several by name order: the editions (with and without Google) differ
  [ "$n" = 1 ] || die "$(m several_images_found_without)"
  IMG=$found
  image_owner
}

# a Z9X never takes an image named for another projector (lumen-os-play6-... matches lumen-os-*-system.img): the
# Play 6 token anywhere in the name, the token of a FLOW=ab-fastbootd projector after lumen-os-
image_owner() {
  [ "$P_FLOW" = z9x-usb ] || return 0
  local id tok
  for id in $(profile_ids); do
    tok=$(devconf_get "$id" IMAGE_TOKEN)
    [ -n "$tok" ] || continue
    case "$(profile_flow "$id"):$(basename "$IMG")" in
      play6-wifi:*"$tok"*|ab-fastbootd:"lumen-os-$tok-"*) die "$(m image_not_xgimi_z9x_nothing "$(dev_name "$id")")" ;;
    esac
  done
}

# the edition of the image (1.0.1): 0 = Lumen OS without Google (-nogms- in the name, ro.z9x.gms=0), 1 = with
# Google services. With dump.erofs on this computer the image's own product build.prop must agree.
image_edition() {
  local name g p bp d id lp
  name=$(basename "$IMG")
  case "$name" in *-nogms-*) g=0 ;; *) g=1 ;; esac
  if command -v dump.erofs >/dev/null 2>&1; then
    bp=$(dump.erofs --cat --path=/system/product/etc/build.prop "$IMG" 2>/dev/null) || bp=""
    p=$(printf '%s\n' "$bp" | sed -n 's/^ro\.z9x\.gms=//p' | tail -1)
    if [ -n "$bp" ] && { { [ "$g" = 0 ] && [ "$p" != 0 ]; } || { [ "$g" = 1 ] && [ "$p" = 0 ]; }; }; then
      die "$(m file_name_image_disagree_about "$name" "$p")"
    fi
    # whose image it is: Lumen OS that names another projector of lib/devices.conf (its LUMEN_PROP, e.g. the X50
    # Ultra, which runs the same XGIMI product image) or another device id than the Z9X's is never written here
    if [ -n "$bp" ]; then
      while IFS='=' read -r id lp; do
        [ -n "$lp" ] || continue
        id=${id%.LUMEN_PROP}
        [ "$(printf '%s\n' "$bp" | sed -n "s/^${lp%% *}=//p" | tail -1)" != "${lp#* }" ] \
          || die "$(m image_not_xgimi_z9x_nothing "$(dev_name "$id")")"
      done <<EOF
$([ -f "$DEVCONF" ] && awk '{ sub(/\r$/, "") } /^[a-z0-9]+\.LUMEN_PROP=[^ ]+ [^ ]+$/' "$DEVCONF")
EOF
      d=$(printf '%s\n' "$bp" | sed -n 's/^ro\.z9x\.device=//p' | tail -1)
      [ -z "$d" ] || [ "$d" = z9x ] \
        || die "$(m not_lumen_os_xgimi_z9x "$name" "$d")"
    fi
  fi
  state_set image_gms "$g"
  if [ "$g" = 0 ]; then ok "$(m edition_lumen_os_without_google)"
  else ok "$(m edition_google_services)"; fi
}

# keep-data repairs never switch editions: Play and Google services updates in /data would be orphaned and
# Google accounts would survive. dev_gms comes from check_device (0, 1, or stock).
edition_guard() {
  local d i
  d=$(state_get dev_gms); i=$(state_get image_gms)
  case "$d" in
    0|1) [ "$d" = "$i" ] && return 0
         if [ "$d" = 0 ]; then
           die "$(m projector_runs_lumen_os_without)"
         else
           die "$(m projector_runs_lumen_os_google)"
         fi ;;
  esac
  return 0
}

# ------------------------------------------------------------------ check
check_tools() {  # pt_setup ran: adb and fastboot of our own, 35 or newer
  if [ "$P_FLOW" = z9x-usb ] && [ "$KEEP_DATA" = 0 ] && [ "$WIPE" = format ]; then
    [ -x "$PT_DIR/make_f2fs" ] && [ -x "$PT_DIR/mke2fs" ] \
      || die "$(m make_f2fs_mke2fs_missing_next "$PT_DIR")"
  fi
  # FLOW=ab-fastbootd: 'fastboot format:<WIPE_FS>' makes the file system with the tool next to fastboot
  if [ "$P_FLOW" = ab-fastbootd ] && [ "$P_WIPE_FS" = f2fs ]; then
    [ -x "$PT_DIR/make_f2fs" ] \
      || die "$(m make_f2fs_missing_next_fastboot "$PT_DIR")"
  elif [ "$P_FLOW" = ab-fastbootd ]; then
    [ -x "$PT_DIR/mke2fs" ] \
      || die "$(m mke2fs_missing_next_fastboot_use "$PT_DIR")"
  fi
  ok "adb $PT_ADBV, fastboot $PT_FBV"
}

check_image() {
  find_image
  local sums="$(dirname "$IMG")/SHA256SUMS" sig name want got cert="$HERE/$P_CERT" pub a
  name=$(basename "$IMG")
  sig="$sums.sig"
  # image variant vs BLOBS: a -PRIVATE- image carries the codec files ('embedded'); with dump.erofs on
  # this computer the image itself says it (0-byte placeholder = public)
  case "$name" in *PRIVATE*) [ "$BLOBS" = embedded ] || die "$(m private_image_codec_files_inside "$name")" ;; esac
  [ "$BLOBS" != none ] || warn "$(m no_blobs_no_sound_no)"
  if command -v dump.erofs >/dev/null 2>&1; then
    local inf sz
    inf=$(dump.erofs --path=/system/system_ext/lib64/libc2plugin_store.so "$IMG" 2>/dev/null) || inf=""
    sz=$(printf '%s\n' "$inf" | sed -n 's/^Size: \([0-9]*\).*/\1/p' | head -1)
    if [ -n "$sz" ]; then
      if [ "$sz" -gt 0 ] && [ "$BLOBS" != embedded ]; then
        die "$(m image_carries_codec_files_itself)"
      elif [ "$sz" -eq 0 ] && [ "$BLOBS" = embedded ]; then
        die "$(m public_image_codec_placeholder_run)"
      fi
    fi
  fi
  if [ -f "$sums" ] && [ -f "$sig" ] && [ -f "$cert" ] && command -v openssl >/dev/null 2>&1; then
    pub=$(mktemp)
    openssl x509 -in "$cert" -pubkey -noout > "$pub"
    openssl dgst -sha256 -verify "$pub" -signature "$sig" "$sums" >/dev/null 2>&1 \
      || { rm -f "$pub"; die "$(m sha256sums_not_signed_lumen_os)"; }
    rm -f "$pub"
    ok "$(m sha256sums_signature_lumen_os)"
  elif [ "$UNSIGNED" = 1 ]; then
    warn "$(m unsigned_image_image_not)"
    if [ "$(state_get unsigned_ok)" != "$name:$(filekey "$IMG")" ]; then
      printf '%s ' "$(m type_unsigned_install_anyway "$name")"
      read -r a
      [ "$a" = UNSIGNED ] || die "$(m cancelled_nothing_written)"
      state_set unsigned_ok "$name:$(filekey "$IMG")"
    fi
    if [ ! -f "$sums" ]; then   # e.g. the maintainer's build/SHA256SUMS_lumen_v1.txt: the file that lists this image
      local f; sums=""
      for f in "$(dirname "$IMG")"/SHA256SUMS*; do
        case "$f" in *.sig) continue ;; esac
        [ -f "$f" ] && awk -v n="$name" '$2==n || $2=="*"n {f=1} END{exit !f}' "$f" && { sums=$f; break; }
      done
    fi
  else
    [ -f "$sums" ] || die "$(m no_sha256sums_next_image_cannot)"
    [ -f "$sig" ] || die "$(m no_sha256sums_sig_next_image)"
    [ -f "$cert" ] || die "$(m installer_certificate_certs_ota)"
    die "$(m openssl_needed_check_image)"
  fi
  if [ -n "$sums" ] && [ -f "$sums" ]; then
    want=$(awk -v f="$name" '$2==f || $2=="*"f {print $1}' "$sums")
    [ -n "$want" ] || die "$(m not_listed "$name" "$(basename "$sums")")"
    # the cache only spares a second hash in 'check' for exactly the same file (sha + size + mtime +
    # inode); 'flash' always hashes the file it is about to write (FORCE_HASH)
    if [ "$FORCE_HASH" = 1 ] || [ "$(state_get image_ok)" != "$want:$(filekey "$IMG")" ]; then
      say "$(m checking_image_checksum_about)"
      got=$(sha256 "$IMG")
      [ "$got" = "$want" ] || { state_set image_ok none; die "$(m checksum_mismatch_image_damaged)"; }
      state_set image_ok "$want:$(filekey "$IMG")"
    fi
    ok "$name sha256 ${want:0:16}…"
  else
    warn "$(m unsigned_image_without_any)"
  fi
  image_edition
}

check_device() {
  pick_device
  local code board vend slot sys free
  code=$(prop ro.boot.xgimi.modelname); board=$(prop ro.boot.hardware)
  vend=$(prop ro.vendor.build.version.incremental); slot=$(prop ro.boot.slot_suffix)
  sys=$(prop ro.z9x.version)
  [ "$code" = "$P_MODEL" ] && [ "$board" = "$P_BOARD" ] \
    || die "$(m not_xgimi_z9x_model_board "$code" "$board")$(other_hint "$code" "$board")"
  case " $P_VENDOR_OK " in
    *" $vend "*) ;;
    *) die "$(m xgimi_firmware_not_supported_v6 "$vend")" ;;
  esac
  state_set serial "$SERIAL"; state_set slot "$slot"
  if [ -n "$sys" ]; then local g; g=$(prop ro.z9x.gms); state_set dev_gms "${g:-1}"; else state_set dev_gms stock; fi
  ok "XGIMI Z9X $SERIAL, $(m firmware) $vend, $(m slot) ${slot#_}, $(m system): ${sys:+Lumen/Z9X OS }${sys:-$(m xgimi_stock)}"
  free=$(df -k "$HOME" | awk 'NR==2{print $4}')
  [ "${free:-0}" -ge 4194304 ] || warn "$(m less_4_gb_free_computer)"
}

cmd_check() {
  check_tools
  check_device
  check_image
  check_vbmeta_effective "$(prop ro.boot.slot_suffix)"
  ok "$(m check_passed_nothing_written)"
  state_set step_check done
}

# ------------------------------------------------------------------ backup
cmd_backup() {
  local pre=""
  if [ "$P_FLOW" = play6-wifi ]; then play6_adb 60 || die "$(p6_noconn_text)"; pre=$PROJECTOR-
  elif [ "$P_FLOW" = ab-fastbootd ]; then abf_adb 60 || die "$(p6_noconn_text)"; pre=$PROJECTOR-
  else pick_device; fi
  local d="$BACKUP_ROOT/$pre$(date +%Y%m%d-%H%M)" p path n=0
  mkdir -p "$d/apps" "$d/sdcard" "$d/settings"
  say "$(m backup "$d")"
  [ "$P_FLOW" = z9x-usb ] || say "$(m over_wi_fi_takes_about)"
  for p in $(adbs shell pm list packages -3 2>/dev/null | tr -d '\r' | sed 's/^package://'); do
    for path in $(adbs shell pm path "$p" 2>/dev/null | tr -d '\r' | sed 's/^package://'); do
      mkdir -p "$d/apps/$p"
      adbs pull "$path" "$d/apps/$p/" >/dev/null 2>&1 && n=$((n + 1))
    done
  done
  ok "$(m apk_files_apps_app_data "$n")"
  for p in Download Movies Music Pictures DCIM Documents; do
    adbs shell "[ -d /sdcard/$p ] && [ -n \"\$(ls -A /sdcard/$p 2>/dev/null)\" ]" 2>/dev/null \
      && adbs pull "/sdcard/$p" "$d/sdcard/" >/dev/null 2>&1 && ok "/sdcard/$p"
  done
  for p in global secure system; do adbs shell settings list "$p" 2>/dev/null | tr -d '\r' > "$d/settings/$p.txt"; done
  adbs shell pm list packages -3 2>/dev/null | tr -d '\r' > "$d/settings/user_apps.txt"
  state_set backup "$d"
  state_set step_backup done
  ok "$(m backup_done)"
}

# ------------------------------------------------------------------ blobs (public images)
# blobs_allow.txt lines: <sha256> <path in the tar> <stock source path> [bind=<path>] [from=<sha256>:<sed>].
# from=: a file the projector has only as the stock original (XGIMI's audio policy) is derived here by
# deleting lines ('N,Md' only), then checked against <sha256> like every other file.
cmd_blobs() {
  if [ "$BLOBS" = none ]; then
    warn "$(m no_blobs_codec_audio_files)"
    state_set step_blobs skip; return 0
  fi
  if [ "$BLOBS" != partition ]; then
    ok "$(m image_carries_codec_files_itself_2)"
    state_set step_blobs skip; return 0
  fi
  pick_device
  local allow="$HERE/lib/blobs_allow.txt" w="$STATE_DIR/blobs" set h rel src opt o from got n=0 img out
  [ -f "$allow" ] || die "missing $allow"
  rm -rf "$w"; mkdir -p "$w/x"
  set=$(sed -n 's/^set=//p' "$allow")
  printf 'set=%s\n' "$set" > "$w/x/MANIFEST"
  while read -r h rel src opt; do
    case "$h" in ''|\#*|set=*) continue ;; esac
    from=""
    for o in $opt; do case "$o" in from=*) from=${o#from=} ;; esac; done
    case "${from#*:}" in *[!0-9,d\;]*) die "internal: $rel: from= may only delete lines" ;; esac
    mkdir -p "$w/x/$(dirname "$rel")"
    got=""
    for p in "$src" "/system/$rel"; do
      adbs pull "$p" "$w/x/$rel" >/dev/null 2>&1 || continue
      got=$(sha256 "$w/x/$rel")
      [ "$got" = "$h" ] && break
      if [ -n "$from" ] && [ "$got" = "${from%%:*}" ]; then
        sed "${from#*:}" "$w/x/$rel" > "$w/x/$rel.new" && mv "$w/x/$rel.new" "$w/x/$rel"
        got=$(sha256 "$w/x/$rel")
        [ "$got" = "$h" ] && break
      fi
      got="bad"
    done
    [ "$got" = "$h" ] || die "$(m projector_not_match_allow_list "$rel" "$got")"
    printf '%s %s %s\n' "$rel" "$h" "$(wc -c < "$w/x/$rel" | tr -d ' ')" >> "$w/x/MANIFEST"
    n=$((n + 1))
  done < "$allow"
  img="$w/z9x_blobs.img"
  find "$w/x" -exec env TZ=UTC0 touch -t 197001010000 {} +
  if tar --version 2>/dev/null | grep -q bsdtar; then
    (cd "$w/x" && find . -mindepth 1 | sed 's|^\./||' | LC_ALL=C sort | tar --format ustar --uid 0 --gid 0 --uname root --gname root -n -cf "$img" -T -)
  else
    (cd "$w/x" && find . -mindepth 1 | sed 's|^\./||' | LC_ALL=C sort | tar --format=ustar --owner=0 --group=0 --numeric-owner --mtime=@0 --no-recursion -cf "$img" -T -)
  fi
  [ "$(wc -c < "$img")" -le "$BLOB_SIZE" ] || die "blobs tar larger than 4 MiB"
  dd if=/dev/null of="$img" bs=1 seek="$BLOB_SIZE" 2>/dev/null
  [ "$(wc -c < "$img" | tr -d ' ')" = "$BLOB_SIZE" ] || die "cannot pad $img"
  mkdir -p "$BACKUP_ROOT"
  out="$BACKUP_ROOT/blobs-$SERIAL.img"
  cp "$img" "$out"
  state_set blobs_img "$out"
  state_set step_blobs done
  ok "$(m codec_audio_files_projector_set "$n" "$set" "$out")"
}

# ------------------------------------------------------------------ vbmeta (manual)
cmd_vbmeta() {
  local g; g=$(p6_guide)
  echo
  m one_manual_step_vbmeta_yourself
  echo "  Google: $GOOGLE_GSI"
  echo "  $(m guide): $g#vbmeta"
  echo
  if ask "$(m have_done_vbmeta_step_did)"; then
    state_set step_vbmeta done
  else
    die "$(m vbmeta_step_first_run_installer)"
  fi
}

# Read-only proof that the vbmeta step took effect on the running slot: with verification disabled,
# first-stage init sets up no dm-verity, so /dev/block/mapper has no 'system-verity' device. Stock with
# its own vbmeta shows system-verity, product-verity, vendor-verity... A system written over a slot
# that is still verified fails dm-verity in the first seconds of every boot (no USB, lamp never on).
vbmeta_effective() {  # 0 = verification off on the running slot
  local m
  m=$(adbs shell ls /dev/block/mapper/ 2>/dev/null | tr -d '\r')
  [ -n "$m" ] || return 2
  printf '%s\n' "$m" | grep -qx 'system-verity' && return 1
  return 0
}

check_vbmeta_effective() {
  vbmeta_effective; case $? in
    0) ok "$(m vbmeta_verification_off_slot_no "${1#_}")" ;;
    1) state_set step_vbmeta ""
       die "$(m running_slot_still_verifies_system "${1#_}")" ;;
    *) warn "$(m could_not_read_dev_block)" ;;
  esac
}

# ------------------------------------------------------------------ flash
fb_wait() {  # wait up to 90 s for the projector in fastbootd
  local i
  for i in $(seq 1 45); do
    fastboot devices 2>/dev/null | awk '{print $1}' | grep -qx "$SERIAL" && return 0
    sleep 2
  done
  return 1
}
gv_val() { printf '%s\n' "$GV" | awk -F: -v k="(bootloader) $1" '$1==k {sub(/^[^:]*:/,""); gsub(/[ \r]/,""); print; exit}'; }

cmd_flash() {
  check_tools
  check_device
  FORCE_HASH=1 check_image
  [ "$KEEP_DATA" = 0 ] || edition_guard
  [ "$(state_get step_vbmeta)" = done ] || cmd_vbmeta
  check_vbmeta_effective "$(prop ro.boot.slot_suffix)"
  if [ "$BLOBS" = partition ] && [ "$(state_get step_blobs)" != done ]; then cmd_blobs; fi
  [ -z "$GAPPS_WITH" ] || z9x_gapps_prepare   # everything slow before fastbootd (its ~2 minute limit)
  local slot S blobs="" p
  slot=$(state_get slot); S=$slot
  [ "$BLOBS" = partition ] && blobs=$(state_get blobs_img)
  echo
  local a
  if [ "$KEEP_DATA" = 1 ]; then
    m writing_lumen_os_slot_without "${S#_}" "$S"; echo
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
  else
    m writing_lumen_os_slot_erasing "${S#_}" "$S"; echo
    if [ -n "$GAPPS_WITH" ]; then
      m gapps_zip_same_session_also "$GZ_NAME" "$S" "$(( (GI_SIZE + 1048575) / 1048576 ))"; echo
    fi
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
    ERASE_NOW=1
  fi
  # --gapps-zip: the Google services of this write (z9x_gapps_finish completes them); any other write forgets
  # what an earlier one-pass install left saved
  if [ -n "$GAPPS_WITH" ] || [ -n "$(state_get gapps_plan)$(state_get step_gapps_part)$(state_get step_gapps_fill)$(state_get step_gapps_flash)" ]; then
    state_set gapps_plan "$GZ_SHA"; state_set gapps_img "$GI_SHA"
    state_set step_gapps_part ""; state_set step_gapps_fill ""; state_set step_gapps_flash ""
    [ -z "$GAPPS_WITH" ] || state_set step_wipe ""
  fi
  try_root && calib_snapshot before
  say "$(m restarting_fastbootd_about_30_s)"
  adbs reboot fastboot >/dev/null 2>&1 || die "adb reboot fastboot failed"
  fb_wait || die "$(m projector_did_not_appear_fastboot)"
  GV=$(fb getvar all 2>&1)
  [ "$(gv_val is-userspace)" = yes ] || { fb reboot; die "$(m not_fastbootd)"; }
  [ "$(gv_val product)" = "$P_FB_PRODUCT" ] || { fb reboot; die "$(m fastbootd_not_z9x_one) ($(gv_val product))"; }
  [ "$(gv_val unlocked)" = yes ] || { fb reboot; die "$(m bootloader_locked)"; }
  [ "$(gv_val current-slot)" = "${S#_}" ] || { fb reboot; die "slot $(gv_val current-slot) != ${S#_}"; }
  if [ "$(gv_val snapshot-update-status)" != none ]; then
    fb reboot
    die "$(m xgimi_lumen_update_still_being "$(gv_val snapshot-update-status)")"
  fi
  for p in $(printf '%s\n' "$GV" | grep -oE 'partition-size:[a-z0-9_]+-cow' | cut -d: -f2 | sort -u); do
    say "$(m removing_update_leftover) $p"; fb_do delete "$p" || { fb reboot; die "delete $p"; }
  done
  for p in "product$S" "system_ext$S"; do
    if printf '%s\n' "$GV" | grep -q "partition-size:$p:"; then
      say "$(m removing_unused_xgimi_partition) $p"; fb_do delete "$p" || warn "delete $p"
    fi
  done
  if [ -z "$GAPPS_WITH" ] && printf '%s\n' "$GV" | grep -q "partition-size:z9x_gapps$S:"; then
    say "$(m z9x_gapps_google_services_added "$S")"
  fi
  say "$(m writing_system_about_40_s)"
  fb_do flash system "$IMG" || { fb reboot; die "$(m system_not_written_projector_still)"; }
  state_set step_flash_system done
  if [ -n "$blobs" ]; then
    if printf '%s\n' "$GV" | grep -q "partition-size:z9x_blobs$S:"; then fb_do resize "z9x_blobs$S" "$BLOB_SIZE"
    else fb_do create "z9x_blobs$S" "$BLOB_SIZE"; fi || warn "z9x_blobs$S"
    fb_do flash "z9x_blobs$S" "$blobs" && ok "z9x_blobs$S" || warn "$(m codec_audio_files_not_written)"
  fi
  if [ -n "$GAPPS_WITH" ]; then z9x_gapps_write "$S" || state_set gapps_plan ""; fi
  if [ "$KEEP_DATA" = 0 ]; then
    if [ "$WIPE" = format ]; then
      say "$(m wiping_data_format_userdata)"
      fb_do format userdata || { fb reboot; die "$(m wipe_failed_projector_starts_lumen)"; }
      fb_do format metadata || warn "format metadata"
      printf '%s\n' "$GV" | grep -q "partition-type:cache:" && { fb_do format cache || warn "format cache"; }
      state_set step_wipe done
    else
      state_set step_wipe factory-reset
    fi
  fi
  fb reboot >/dev/null 2>&1
  state_set step_flash done
  if [ "$(state_get step_gapps_flash)" = done ]; then
    ok "$(m written_first_start_takes_2)"
    return 0
  fi
  ok "$(m written_first_start_takes_2_2)"
  if [ "$WIPE" = factory-reset ] && [ "$KEEP_DATA" = 0 ]; then
    say "$(m now_projector_settings_device)"
  fi
}

# ------------------------------------------------------------------ calibration fingerprint (read-only, root only)
try_root() {  # 0 if adb shell runs as root (Lumen/Z9X userdebug builds; never on XGIMI stock)
  [ "$(prop ro.debuggable)" = 1 ] || return 1
  [ "$(adbs shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && return 0
  adbs root >/dev/null 2>&1 || return 1
  sleep 3; adbs wait-for-device
  [ "$(adbs shell id -u 2>/dev/null | tr -d '\r')" = 0 ]
}
root_setting_wait() {  # --gapps-zip: Developer options > Rooted debugging is off; 0 = the user turned it on, try again
  # (LineageOS adbd asks its adb_root service first: "ADB Root access is disabled by system setting"; never bypassed)
  echo
  m root_debugging_off_projector; echo
  ask "$(m rooted_debugging_now)"
}
root_setting_stop() {  # the user did not turn it on now: the next run continues at this step
  die "$(m turn_rooted_debugging_settings)"
}
CALIB="persist xgimidatabase xgimisps xgimicri project_id tvcertificate"
calib_snapshot() {  # before|after: sha256 of XGIMI calibration partitions (read-only, needs adb root)
  local p f="$STATE_DIR/calib_$1.txt"
  : > "$f"
  for p in $CALIB; do
    adbs shell "[ -e /dev/block/by-name/$p ] && sha256sum /dev/block/by-name/$p" 2>/dev/null | tr -d '\r' >> "$f"
  done
  [ -s "$f" ] && ok "$(m calibration_fingerprint_saved_read "$1")"
}

# ------------------------------------------------------------------ verify
cmd_verify() {
  local i s
  say "$(m waiting_projector_finish_starting)"
  for i in $(seq 1 150); do
    s=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}' | head -1)
    [ -n "$s" ] && SERIAL=${SERIAL:-$s} && [ "$(prop sys.boot_completed)" = 1 ] && break
    sleep 2
  done
  if [ "$(prop sys.boot_completed)" != 1 ]; then
    warn "$(m no_usb_connection_started_system)"
    return 0
  fi
  local v keys bl gm data
  v=$(prop ro.z9x.version); keys=$(prop ro.z9x.keys); bl=$(prop sys.z9x.blobs); gm=$(prop init.svc.gmpf_main)
  local ng=""; [ "$(prop ro.z9x.gms)" = 0 ] && ng=", $(m without_google)"
  [ -n "$v" ] && ok "Lumen OS $v ($(prop ro.z9x.build_id), $(prop ro.z9x.variant | sed 's/^$/private/')$ng)" || warn "$(m ro_z9x_version_missing)"
  if [ -n "$ng" ] && [ "$(prop sys.z9x.gapps)" = ok ]; then
    ok "$(m google_services_added_gapps)"
  elif [ -n "$ng" ]; then
    local gp
    gp=$(adbs shell pm list packages 2>/dev/null | tr -d '\r' | sed 's/^package://' | grep -E '^(com\.google\.|com\.mtg\.|com\.android\.vending$)' | head -3 | tr '\n' ' ')
    [ -z "$gp" ] && ok "$(m no_google_package)" || warn "$(m google_packages_lumen_os_without): $gp"
    case "$(prop sys.z9x.gapps)" in
      off|bad:*) warn "$(m google_services_add_not_use "$(prop sys.z9x.gapps)")" ;;
    esac
  fi
  [ "$keys" = release ] && ok "$(m release_keys)" || warn "ro.z9x.keys=$keys"
  [ "$gm" = running ] && ok "$(m projector_service_lamp_fans)" || warn "gmpf_main: $gm"
  if [ "$BLOBS" = partition ]; then [ "$bl" = ok ] && ok "$(m codec_audio_files_z9x_blobs) ok" || warn "sys.z9x.blobs=$bl ($(m no_sound_protected_video_until))"; fi
  adbs shell dumpsys media.audio_policy 2>/dev/null | grep -q "Output" && ok "$(m audio_outputs_present)" || warn "$(m no_audio_outputs_listed)"
  data=$(adbs shell 'mount | grep " /data "' 2>/dev/null | tr -d '\r')
  case "$data" in *f2fs*) ok "/data f2fs" ;; *) warn "/data: $data" ;; esac
  if [ -f "$STATE_DIR/calib_before.txt" ] && try_root; then
    calib_snapshot after
    # XGIMI's own services write persist, xgimidatabase, xgimisps, xgimicri and tvcertificate at every start
    # (measured 2026-10-10 on a Z9X: all five hashes change across a plain reboot), so a changed hash proves
    # nothing. The guarantee is fb_allowed(): the installer never writes them. The snapshots stay as a record.
    ok "$(m calibration_partitions_never "$STATE_DIR")"
    adbs unroot >/dev/null 2>&1
  fi
  state_set step_verify done
}

# ------------------------------------------------------------------ gsf
cmd_gsf() {
  pick_device
  [ -n "$(prop ro.z9x.version)" ] || die "$(m projector_not_running_lumen_os)"
  # Lumen OS without Google with the user's add-on ('gapps') has GSF too: the package decides, not ro.z9x.gms
  if [ -z "$(adbs shell pm path com.google.android.gsf 2>/dev/null | tr -d '\r')" ]; then
    ok "$(m lumen_os_has_no_google)"
    [ "$(prop ro.z9x.gms)" = 0 ] && say "$(m add_them_bash_lumen_install)"
    return 0
  fi
  adbs root >/dev/null 2>&1; sleep 3; adbs wait-for-device
  local id
  id=$(adbs shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db \"select value from main where name='android_id';\"" 2>/dev/null | tr -d '\r')
  adbs unroot >/dev/null 2>&1
  [ -n "$id" ] || die "$(m no_gsf_id_yet_connect)"
  echo "GSF ID: $id"
  echo "$(m register_https_www_google_com)"
}

# ------------------------------------------------------------------ waiting for fastbootd
# Lumen OS 1.0.1+ restarts into fastbootd by itself after 3 starts in a row that did not complete. There is
# no adb then ('gapps --remove' waits for it there). XGIMI's fastbootd leaves by itself after about 2 minutes,
# so everything slow happens before the wait.
fbd_find() {  # wait up to $1 s for the projector in fastboot; sets SERIAL (0 = found)
  local i=0 list n
  while [ "$i" -lt "$1" ]; do
    list=$(fastboot devices 2>/dev/null | awk 'NF {print $1}')
    n=$(printf '%s\n' "$list" | grep -c .)
    if [ -n "$SERIAL" ] && printf '%s\n' "$list" | grep -qx "$SERIAL"; then return 0; fi
    if [ "$n" = 1 ] && { [ -z "$SERIAL" ] || [ "$SERIAL_FROM_STATE" = 1 ]; }; then
      [ -n "$SERIAL" ] && warn "$(m using_saved_serial "$list" "$SERIAL")"
      SERIAL=$list
      return 0
    fi
    [ "$n" -gt 1 ] && die "$(m several_devices_fastboot_mode)"
    [ "$i" -gt 0 ] && [ $((i % 120)) = 0 ] && say "$(m still_waiting_fastboot_mode)"
    sleep 2; i=$((i + 2))
  done
  return 1
}

# ------------------------------------------------------------------ gapps (Lumen OS without Google + the user's Google services)
# The user's own download of MindTheGapps (only a zip whose sha256 is in lib/gapps_allow.txt, the same file as
# the image's /system/etc/z9x/gapps_allow.txt) goes into the logical partition z9x_gapps_<slot> (ext4), which
# the image's z9x_gapps.sh checks and overlays at every start. 'gapps --zip FILE':
#   1. checks: the zip (sha256), the projector (Lumen OS without Google, userdebug: adb root), its own
#      allow-list has the zip; lib/gapps_fill.sh, pushed to the projector, checks the rest (unzip, room in
#      /data for the zip). The same zip already on (sys.z9x.gapps=ok): it stops, nothing written (writing it
#      again means one start without the Google apps, and PackageManager then deletes their data);
#   2. fastbootd: room in super (else it stops, nothing written), create or resize z9x_gapps_<slot> to the
#      allow-list's part= size, 'fastboot format:ext4' (fastboot's own mke2fs), restart;
#   3. adb root: the zip is pushed, gapps_fill.sh fills the partition with exactly the allow-listed files
#      (each checked by sha256), writes MANIFEST, unmounts; the image's z9x_gapps.sh 'check' must say ok;
#      the zip is deleted again and a 'turned off after failed starts' marker of z9x_gapps.sh is removed
#      (an empty or half-filled partition has no MANIFEST: the system starts without Google services);
#   4. the data wipe of 'flash' (fastbootd format of userdata, metadata, cache), unless --keep-data or
#      --wipe=factory-reset. Why a wipe: on the old data the Google apps would miss the default permissions
#      and the setup's Google step that only a first start gives them.
# 'gapps --remove': deletes z9x_gapps_<slot> in fastbootd (reached with adb, or waited for there
# when the system no longer starts) and wipes the data the same way (Play and Google services updates in
# /data would stay behind as broken apps). 'gapps': the add-on's state; turns it on again after failed starts.
GAPPS_DIR=/data/local/tmp/lumen-gapps

gapps_line() { awk -v z="zip=$1" '$1 == z' "$GAPPS_ALLOW_F" | head -n 1; }   # zip sha256 -> its zip= line
gapps_field() { printf '%s\n' "$1" | tr ' ' '\n' | sed -n "s/^$2=//p" | head -n 1; }    # zip= line, key -> value
gv_size() { printf '%s\n' "$GV" | sed -n "s/^(bootloader) partition-size:$1: *\(0x[0-9a-fA-F]*\).*/\1/p" | head -n 1; }
super_free() {  # bytes of super that no logical partition uses: each rounded up to 1 MiB, 2 MiB for metadata
  local sup p sz used=0
  sup=$(gv_size super)
  [ -n "$sup" ] || { echo 0; return; }
  for p in $(printf '%s\n' "$GV" | sed -n 's/^(bootloader) is-logical:\([^:]*\):yes.*/\1/p' | sort -u); do
    sz=$(gv_size "$p"); [ -n "$sz" ] || continue
    used=$(( used + (sz + 1048575) / 1048576 * 1048576 ))
  done
  echo $(( sup - used - 2097152 ))
}
fbd_check() {  # [slot]: the fastbootd checks of 'flash' on GV; sets S (the slot fastbootd runs)
  local cs
  GV=$(fb getvar all 2>&1)
  [ "$(gv_val is-userspace)" = yes ] || { fb reboot; die "$(m not_fastbootd)"; }
  [ "$(gv_val product)" = "$P_FB_PRODUCT" ] || { fb reboot; die "$(m fastbootd_not_z9x_one) ($(gv_val product))"; }
  [ "$(gv_val unlocked)" = yes ] || { fb reboot; die "$(m bootloader_locked)"; }
  cs=$(gv_val current-slot)
  case "$cs" in a|b) ;; *) fb reboot; die "current-slot '$cs'" ;; esac
  if [ -n "${1-}" ] && [ "$cs" != "${1#_}" ]; then fb reboot; die "slot $cs != ${1#_}"; fi
  S=_$cs
  if [ "$(gv_val snapshot-update-status)" != none ]; then
    fb reboot
    die "$(m lumen_os_update_still_being "$(gv_val snapshot-update-status)")"
  fi
}
fbd_enter() {  # slot: adb reboot fastboot, wait, check
  say "$(m restarting_fastbootd_about_30_s)"
  adbs reboot fastboot >/dev/null 2>&1 || die "adb reboot fastboot failed"
  fb_wait || die "$(m projector_did_not_appear_fastboot)"
  fbd_check "$1"
}
fb_wipe() {  # the data wipe of 'flash', in fastbootd
  say "$(m wiping_data_format_userdata)"
  fb_do format userdata || { fb reboot; die "$(m wipe_failed_projector_settings)"; }
  fb_do format metadata || warn "format metadata"
  printf '%s\n' "$GV" | grep -q "partition-type:cache:" && { fb_do format cache || warn "format cache"; }
  state_set step_wipe done
}
boot_wait() {  # up to 5 min for the projector back over adb with sys.boot_completed=1
  local i
  for i in $(seq 1 150); do
    if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}' | grep -qx "$SERIAL" \
       && [ "$(prop sys.boot_completed)" = 1 ]; then return 0; fi
    sleep 2
  done
  return 1
}
gapps_sh() { adbs shell sh "$GAPPS_DIR/fill.sh" "$@" 2>&1 | tr -d '\r'; }
gapps_push_fill() {
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  adbs shell mkdir -p "$GAPPS_DIR" >/dev/null 2>&1
  adbs push "$HERE/lib/gapps_fill.sh" "$GAPPS_DIR/fill.sh" >/dev/null 2>&1 || die "adb push lib/gapps_fill.sh"
}

gapps_zipcheck() {  # FILE: the zip on this computer (sha256 in lib/gapps_allow.txt); sets GZ_SHA, GZ_NAME, GZ_PART
  local zl
  say "$(m checking "$(basename "$1")")"
  GZ_SHA=$(sha256 "$1")
  zl=$(gapps_line "$GZ_SHA")
  [ -n "$zl" ] || die "$(m not_mindthegapps_file_installer "$(basename "$1")" "${GZ_SHA:0:16}"). $(m nothing_written)"
  GZ_NAME=$(printf '%s\n' "$zl" | cut -d' ' -f2); GZ_PART=$(gapps_field "$zl" part)
  case "$GZ_PART" in ''|*[!0-9]*) die "internal: gapps_allow.txt part=" ;; esac
  ok "$GZ_NAME, sha256 ${GZ_SHA:0:16}..."
}

gapps_install() {
  local zip=$GAPPS_ZIP zsha zl name part slot p o a free cur have
  [ -f "$zip" ] || die "$(m no_file "$zip")"
  check_tools
  [ -x "$PT_DIR/mke2fs" ] || die "$(m mke2fs_missing_next_fastboot_use_2 "$PT_DIR")"
  gapps_zipcheck "$zip"
  zsha=$GZ_SHA; name=$GZ_NAME; part=$GZ_PART
  check_device
  slot=$(state_get slot); p=z9x_gapps$slot
  case "$(state_get dev_gms)" in
    0) ;;
    1) die "$(m projector_runs_lumen_os_google_2)" ;;
    *) die "$(m projector_not_run_lumen_os)" ;;
  esac
  # the same add-on, on: writing it again would only cost the Google apps their data (one start without them)
  have=$(prop sys.z9x.gapps)
  if [ "$have" = ok ] && [ "$(prop sys.z9x.gapps.zip)" = "$zsha" ]; then
    ok "$(m these_google_services_already "$name")"
    return 0
  fi
  try_root || die "$(m adb_root_not_possible_system)"
  if ! adbs shell cat /system/etc/z9x/gapps_allow.txt 2>/dev/null | tr -d '\r' | grep -q "^zip=$zsha "; then
    die "$(m lumen_os_projector_not_accept)"
  fi
  echo
  if [ "$KEEP_DATA" = 1 ] || [ "$WIPE" = factory-reset ]; then
    m adding_google_services_slot "${slot#_}" "$p" "$((part / 1048576))" "$name"; echo
    if [ "$KEEP_DATA" = 1 ]; then
      say "$(m keep_data_sign_google_later)"
      case "$have" in ''|none) ;; *)
        say "$(m google_services_already_installed)" ;;
      esac
    fi
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
  else
    m adding_google_services_slot_2 "${slot#_}" "$p" "$((part / 1048576))" "$name"; echo
    if [ "$(state_get step_backup)" != done ] && ask "$(m make_backup_apps_files_first)"; then
      cmd_backup
    fi
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
  fi
  # 1. the fill script's own checks on the projector (root, edition, its allow-list, unzip, room in /data
  # for the zip) before any partition is touched
  gapps_push_fill
  o=$(gapps_sh pre "$zsha" "$(wc -c < "$zip" | tr -d ' ')")
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  case "$o" in "OK pre "*) ;; *) die "$(m projector_not_ready): ${o#FAIL }. $(m nothing_written)" ;; esac
  # 2. fastbootd: the partition
  fbd_enter "$slot"
  free=$(super_free); cur=0
  if printf '%s\n' "$GV" | grep -q "partition-size:$p:"; then cur=$(( $(gv_size "$p") )); fi
  if [ $(( free + cur )) -lt "$part" ]; then
    fb reboot
    die "$(m not_enough_free_space_super "$(( (free + cur) / 1048576 ))" "$((part / 1048576))")"
  fi
  say "$(m creating_mb_mb_super_stay "$p" "$((part / 1048576))" "$(( (free + cur - part) / 1048576 ))")"
  if [ "$cur" -gt 0 ]; then fb_do resize "$p" "$part"; else fb_do create "$p" "$part"; fi \
    || { fb reboot; die "$(m could_not_created_nothing_written "$p")"; }
  if ! fb_do format "$p"; then
    fb_do delete "$p" >/dev/null 2>&1
    fb reboot
    die "$(m could_not_formatted_removed_again "$p")"
  fi
  fb reboot >/dev/null 2>&1
  # 3. filled over adb as root, checked by the image's own script
  say "$(m waiting_projector_start_up_5)"
  boot_wait || die "$(m projector_did_not_come_back "$p")"
  try_root || die "adb root"
  gapps_push_fill
  say "$(m copying_projector_about_minute "$name")"
  adbs push "$zip" "$GAPPS_DIR/gapps.zip" >/dev/null 2>&1 \
    || { adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1; die "$(m adb_push_zip_failed_still "$p")"; }
  say "$(m copying_google_apps_checking_each "$p")"
  o=$(gapps_sh fill "$zsha")
  case "$o" in
    "OK fill "*) ;;
    *) adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
       die "$(m google_services_were_not_added): ${o#FAIL }. $(m lumen_os_starts_without_them)" ;;
  esac
  o=$(adbs shell sh /system/etc/z9x/z9x_gapps.sh check 2>&1 | tr -d '\r')
  adbs shell rm -rf "$GAPPS_DIR" /metadata/z9x_gapps >/dev/null 2>&1
  case "$o" in
    "ok: "*) ok "$p: ${o#ok: }" ;;
    *) die "$(m projectors_own_check_failed "$p"): $o. $(m lumen_os_starts_without_google)" ;;
  esac
  state_set gapps "$name"
  # 4. the wipe, so that the first start sets up Google services
  if [ "$KEEP_DATA" = 1 ]; then
    adbs reboot >/dev/null 2>&1
    say "$(m restarting)"
    if boot_wait && [ "$(prop sys.z9x.gapps)" = ok ]; then
      ok "$(m google_services_sign_settings)"
    else
      warn "$(m projector_did_not_report_google "$(prop sys.z9x.gapps)")"
    fi
  elif [ "$WIPE" = factory-reset ]; then
    adbs reboot >/dev/null 2>&1
    state_set step_wipe factory-reset
    say "$(m now_projector_settings_device_2)"
  else
    fbd_enter "$slot"
    fb_wipe
    fb reboot >/dev/null 2>&1
    ok "$(m done_first_start_takes_2)"
  fi
  state_set step_gapps done
}

gapps_remove() {
  local a via removed=0 p
  check_tools
  if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then via=adb; check_device; else via=fastboot; fi
  echo
  if [ "$KEEP_DATA" = 1 ] || [ "$WIPE" = factory-reset ]; then
    m removing_google_services_added; echo
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
  else
    m removing_google_services_added_2; echo
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
  fi
  if [ "$via" = adb ]; then
    fbd_enter "$(state_get slot)"
  else
    say "$(m no_projector_over_adb_waiting)"
    fbd_find 900 || die "$(m projector_did_not_appear_fastboot_2)"
    fbd_check ""
    state_set serial "$SERIAL"; state_set slot "$S"
  fi
  p=z9x_gapps$S
  if printf '%s\n' "$GV" | grep -q "partition-size:$p:"; then
    fb_do delete "$p" || { fb reboot; die "$(m could_not_deleted_nothing_changed "$p")"; }
    removed=1; ok "$(m deleted "$p")"
  else
    ok "$(m slot_has_no_nothing_remove "${S#_}" "$p")"
  fi
  if [ "$removed" = 1 ] && [ "$KEEP_DATA" = 0 ] && [ "$WIPE" = format ]; then fb_wipe; fi
  fb reboot >/dev/null 2>&1
  state_set gapps ""
  if [ "$removed" = 1 ] && [ "$KEEP_DATA" = 0 ] && [ "$WIPE" = factory-reset ]; then
    say "$(m now_projector_settings_device)"
  fi
  ok "$(m projector_starts_lumen_os_without)"
}

gapps_status() {
  local v z a
  pick_device
  [ -n "$(prop ro.z9x.version)" ] || die "$(m projector_not_running_lumen_os)"
  v=$(prop sys.z9x.gapps); z=$(prop sys.z9x.gapps.zip)
  case "$v" in
    ok) ok "$(m google_services_added) ($(gapps_line "$z" | cut -d' ' -f2))" ;;
    skip) ok "$(m lumen_os_has_google_services)" ;;
    none|'') say "$(m no_google_services_added_add)" ;;
    bad:slow) warn "$(m last_start_check_google_services)" ;;
    off)
      warn "$(m google_services_were_turned_off)"
      if ask "$(m turn_them_again_projector_restarts)"; then
        try_root || die "adb root"
        adbs shell rm -rf /metadata/z9x_gapps >/dev/null 2>&1
        adbs reboot >/dev/null 2>&1
        ok "$(m turned_projector_restarts)"
      fi ;;
    *) warn "$(m google_services_added_cannot_used "$v")" ;;
  esac
}

cmd_gapps() {
  [ -z "$GAPPS_ZIP" ] || [ "$GAPPS_REMOVE" = 0 ] || die "gapps: --zip or --remove, not both"
  if [ "$GAPPS_REMOVE" = 1 ]; then gapps_remove
  elif [ -n "$GAPPS_ZIP" ]; then gapps_install
  else gapps_status; fi
}

# ------------------------------------------------------------------ XGIMI Z9X: Lumen OS and Google services in one pass (--gapps-zip FILE)
# The full install with the Google services add-on in ONE fastbootd visit and ONE data wipe (decision
# 2026-10-30): no start on the old data, no USB debugging or Rooted debugging
# after the stock vbmeta step. The partition z9x_gapps_<slot> is not filled on the projector: it is built HERE as
# an erofs image (gi_build) and flashed like z9x_blobs. mkfs.erofs --tar=f takes owner, mode and SELinux label of
# every entry from the tar, and the installer writes that tar itself (gi_tar), so neither root nor e2fsdroid is
# needed. The image's z9x_gapps.sh mounts erofs or ext4 (the 'gapps' fill below) with the same checks.
#   1. before anything is written: the zip (gapps_zipcheck), the image without Google (image_gms=0) and able to
#      use an erofs add-on of this zip (gi_image_ok: dump.erofs reads its z9x_gapps.sh and gapps_allow.txt), then
#      the add-on image built and checked (gi_build; about 10 s, ~1 GB free space for a moment);
#   2. one ERASE question, then one fastbootd session: system (+ z9x_blobs), z9x_gapps_<slot> created or resized
#      to the image (rounded up to 1 MiB) and flashed (z9x_gapps_write), then the wipe of 'flash', reboot. Not
#      enough room in super, or a failed create / resize / flash: the partition is deleted and the install
#      finishes without Google (warned; 'gapps --zip FILE' adds them later). The wipe stays the last step: a
#      failure before it leaves the data as it was.
#   3. the first start is clean and its setup has the Google sign-in step.
# fastbootd that goes away in the middle (its ~2 minute limit, the cable): the projector starts by itself on the
# old data, where USB debugging and this computer's key still are, so the same command simply runs 'flash'
# again (the image built here is reused after a sha256 check).
# State: gapps_plan (the zip's sha256), gapps_img (the add-on image's sha256), step_gapps_flash (done |
# skipped:<why>), step_wipe. An install started by an older installer (Lumen OS on the old data waiting for the
# fill: step_gapps_part=done, no step_wipe) is finished by z9x_gapps_finish: the same flash in fastbootd, then
# the wipe.
GZ_SHA=""; GZ_NAME=""; GZ_PART=""
# the add-on's allow-list (the same file as the image's /system/etc/z9x/gapps_allow.txt; gapps_line and 'gapps' read it
# too) and the checked add-on images: the Z9X ones here; a FLOW profile names its own (GAPPS_ALLOW, GAPPS_IMAGES)
GAPPS_ALLOW_F=$HERE/lib/gapps_allow.txt
GAPPS_IMAGES_F=$HERE/lib/gapps_images.txt
EU_VER=1.9.4               # erofs-utils of tools/erofs-utils (tools/ota/build_erofs_utils.sh in the Lumen OS tree)
EU_OPT=${LUMEN_EROFS_UTILS:-}   # --erofs-utils DIR
EU_DIR=""
GI_IMG=""; GI_SHA=""; GI_SIZE=""; GI_READY=0
GI_LABEL=u:object_r:system_file:s0
GI_MTIME=1230768000        # one fixed time for every inode (compact inodes, the same image everywhere)

eu_plat() {  # this computer's folder in tools/erofs-utils (nothing: none is shipped for it)
  case "$(uname -s)" in
    Darwin) echo darwin ;;
    Linux) case "$(uname -m)" in x86_64|amd64) echo linux-x86_64 ;; aarch64|arm64) echo linux-aarch64 ;; esac ;;
  esac
}
eu_setup() {  # mkfs.erofs, fsck.erofs, dump.erofs: our own (tools/erofs-utils), checked against lib/erofs_utils.txt, run from a
  # copy in the state folder (executable, without a browser's quarantine flag; checked again on every run)
  [ -z "$EU_DIR" ] || return 0
  local plat src sums d p want v
  if [ -n "$EU_OPT" ]; then
    d=$(cd "$EU_OPT" 2>/dev/null && pwd) && [ -x "$d/mkfs.erofs" ] && [ -x "$d/fsck.erofs" ] && [ -x "$d/dump.erofs" ] \
      || die "$(m erofs_utils_no_mkfs_erofs "$EU_OPT")"
    v=$("$d/mkfs.erofs" --version 2>/dev/null | head -n 1 | awk '{ print $NF }')
    [ "$v" = "$EU_VER" ] || die "$(m erofs_utils_erofs_utils_installer "$EU_OPT" "$v" "$EU_VER")"
    EU_DIR=$d; ok "erofs-utils $EU_VER (--erofs-utils $d)"; return 0
  fi
  plat=$(eu_plat); src=$HERE/tools/erofs-utils; sums=$HERE/lib/erofs_utils.txt
  if [ -z "$plat" ] || [ ! -f "$sums" ] || ! grep -q " $plat/mkfs.erofs\$" "$sums" || [ ! -d "$src/$plat" ]; then
    die "$(m installer_has_no_erofs_utils "$(uname -s)" "$(uname -m)")"
  fi
  d=$STATE_DIR/erofs-utils-$EU_VER/$plat
  mkdir -p "$d" || die "mkdir $d"
  for p in mkfs fsck dump; do
    want=$(awk -v f="$plat/$p.erofs" '$2 == f { print $1 }' "$sums")
    [ -n "$want" ] || die "internal: lib/erofs_utils.txt has no $plat/$p.erofs"
    [ -f "$d/$p.erofs" ] && [ "$(sha256 "$d/$p.erofs")" = "$want" ] && continue
    [ -f "$src/$plat/$p.erofs" ] && [ "$(sha256 "$src/$plat/$p.erofs")" = "$want" ] \
      || die "$(m tools_erofs_utils_erofs_missing "$plat" "$p")"
    cat "$src/$plat/$p.erofs" > "$d/$p.erofs.new" && chmod 755 "$d/$p.erofs.new" && mv "$d/$p.erofs.new" "$d/$p.erofs" \
      || die "$(m cannot_copy_erofs "$p" "$d")"
    [ "$(sha256 "$d/$p.erofs")" = "$want" ] || die "internal: $d/$p.erofs"
  done
  v=$("$d/mkfs.erofs" --version 2>/dev/null | head -n 1 | awk '{ print $NF }')
  [ "$v" = "$EU_VER" ] || die "$(m mkfs_erofs_tools_erofs_utils "$plat")"
  EU_DIR=$d
  ok "erofs-utils $EU_VER (tools/erofs-utils, $(m checked))"
}

# the tar for mkfs.erofs --tar=f, written here byte by byte (bash 3.2, no Python): every entry a PAX header with its
# SELinux label (SCHILY.xattr.security.selinux, as tools/patch_tar.py writes it for the system image) and a ustar
# header: owner root (0 / 0), the fixed time, mode 0755 (directories) or 0644 (files)
gi_nul() { [ "$1" -gt 0 ] && head -c "$1" /dev/zero; return 0; }
gi_fld() { printf '%s' "$1"; gi_nul $(( $2 - ${#1} )); }
gi_hdr() {  # name mode size type: one 512-byte ustar header
  local sz mt sum
  [ ${#1} -le 99 ] || die "internal: tar name longer than 99 characters: $1"
  sz=$(printf '%011o' "$3"); mt=$(printf '%011o' "$GI_MTIME")
  # the checksum: every byte of the header, the checksum field counted as 8 spaces (NUL bytes add nothing)
  sum=$(printf '%s' "$1$2${GI_Z7}${GI_Z7}$sz$mt$4ustar00rootroot${GI_Z7}${GI_Z7}" | od -An -v -tu1 | awk '{ for (i = 1; i <= NF; i++) s += $i } END { print s + 256 }')
  gi_fld "$1" 100; gi_fld "$2" 8; gi_fld "$GI_Z7" 8; gi_fld "$GI_Z7" 8; gi_fld "$sz" 12; gi_fld "$mt" 12
  gi_fld "$(printf '%06o' "$sum")" 7; printf ' %s' "$4"; gi_nul 100
  printf 'ustar'; gi_nul 1; printf '00'; gi_fld root 32; gi_fld root 32; gi_fld "$GI_Z7" 8; gi_fld "$GI_Z7" 8; gi_nul 167
}
GI_Z7=0000000
gi_entry() {  # name mode size type: the PAX header with the label, then the entry's own header
  local body="SCHILY.xattr.security.selinux=$GI_LABEL" l n
  # record "<n> <body>NUL<newline>": n counts its own digits
  l=${#body}; n=$((l + 4)); n=$((l + 3 + ${#n})); n=$((l + 3 + ${#n}))
  gi_hdr ././@PaxHeader 0000644 "$n" x
  printf '%s %s' "$n" "$body"; printf '\0\n'; gi_nul $(( 512 - n ))
  gi_hdr "$1" "$2" "$3" "$4"
}
gi_unzip() {  # zip member: one member to stdout
  if command -v unzip >/dev/null 2>&1; then unzip -p "$1" "$2"
  else python3 -I -c 'import shutil, sys, zipfile; shutil.copyfileobj(zipfile.ZipFile(sys.argv[1]).open(sys.argv[2]), sys.stdout.buffer)' "$1" "$2"; fi
}
sha256_in() { if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi; }
gi_lines() { awk -v z="zip=$1" '$1 ~ /^zip=/ { on = ($1 == z); next } on && NF == 3 && $1 !~ /^#/ { print $1, $2, $3 }' "$GAPPS_ALLOW_F"; }
gi_tar() {  # zip out: the add-on's tree as a tar, laid out exactly as lib/gapps_fill.sh fills the ext4 partition
  local zip=$1 out=$2 w lines d h s p mf
  w=$(dirname "$out")
  lines=$(gi_lines "$GZ_SHA")
  [ -n "$lines" ] || die "internal: no files for $GZ_NAME in ${GAPPS_ALLOW_F#"$HERE/"}"
  { echo "# Lumen OS: Google services add-on (lumen-install gapps). z9x_gapps.sh checks it at every start"
    echo "# against /system/etc/z9x/gapps_allow.txt: do not edit."
    gapps_line "$GZ_SHA" | cut -d' ' -f1,2
    echo "$lines"; } > "$w/MANIFEST"
  (
    gi_entry ./ 0000755 0 5   # the image root: without this entry it would be 0777 and unlabelled
    for d in $(printf '%s\n' "$lines" | awk '{ p = $3; while (sub(/\/[^\/]*$/, "", p)) print p }' | LC_ALL=C sort -u); do
      gi_entry "$d/" 0000755 0 5
    done
    printf '%s\n' "$lines" | LC_ALL=C sort -k3,3 | while read -r h s p; do
      case "$p" in ''|/*|*/|*//*|*..*|*[!A-Za-z0-9_@+./-]*) echo "bad path $p" > "$w/bad"; exit 1 ;; esac
      gi_unzip "$zip" "system/$p" > "$w/file" 2>/dev/null || { echo "unzip system/$p" > "$w/bad"; exit 1; }
      [ "$(sha256 "$w/file")" = "$h" ] && [ "$(wc -c < "$w/file" | tr -d ' ')" = "$s" ] || { echo "sha256 of $p" > "$w/bad"; exit 1; }
      gi_entry "$p" 0000644 "$s" 0; cat "$w/file"; gi_nul $(( (512 - s % 512) % 512 ))
    done || exit 1
    mf=$(wc -c < "$w/MANIFEST" | tr -d ' ')
    gi_entry MANIFEST 0000644 "$mf" 0; cat "$w/MANIFEST"; gi_nul $(( (512 - mf % 512) % 512 ))
    gi_nul 1024
  ) > "$out"
}
gi_image_ok() {  # the Lumen OS image to be written can use an erofs add-on of this zip (read with our dump.erofs)
  local s
  s=$("$EU_DIR/dump.erofs" --cat --path=/system/etc/z9x/z9x_gapps.sh "$IMG" 2>/dev/null) || s=""
  printf '%s\n' "$s" | grep -q '^# lumen-gapps-fs:.* erofs' \
    || die "$(m cannot_take_google_services_one "$(basename "$IMG")")"
  s=$("$EU_DIR/dump.erofs" --cat --path=/system/etc/z9x/gapps_allow.txt "$IMG" 2>/dev/null) || s=""
  printf '%s\n' "$s" | grep -q "^zip=$GZ_SHA " \
    || die "$(m not_accept_not_gapps_allow "$(basename "$IMG")" "$GZ_NAME")"
  ok "$(m image_takes_google_services_add)"
}
gi_build() {  # zip: the add-on image in $STATE_DIR/gapps, built and checked; sets GI_IMG, GI_SHA, GI_SIZE
  local zip=$1 ref w img kb lines h s p got imf=${GAPPS_IMAGES_F#"$HERE/"}
  ref=$(awk -v z="$GZ_SHA" -v v="$EU_VER" '$1 == z && $2 == v && $3 == "plain" { print; exit }' "$GAPPS_IMAGES_F" 2>/dev/null)
  [ -n "$ref" ] || die "$(m names_no_checked_add_image "$imf" "$GZ_NAME" "$EU_VER")"
  GI_SHA=$(printf '%s\n' "$ref" | awk '{ print $4 }'); GI_SIZE=$(printf '%s\n' "$ref" | awk '{ print $5 }')
  case "$GI_SIZE" in ''|*[!0-9]*) die "internal: $imf size" ;; esac
  w=$STATE_DIR/gapps; img=$w/z9x_gapps-$(printf '%s' "$GZ_SHA" | cut -c1-16).img
  GI_IMG=$img
  if [ -f "$img" ] && [ "$(wc -c < "$img" | tr -d ' ')" = "$GI_SIZE" ] && [ "$(sha256 "$img")" = "$GI_SHA" ]; then
    ok "$(m google_services_add_image_built) ($(( (GI_SIZE + 1048575) / 1048576 )) $(m mb))"
    return 0
  fi
  rm -rf "${w:?}"; mkdir -p "$w" || die "mkdir $w"
  kb=$(df -k "$w" | awk 'NR == 2 { print $4 }')
  [ "${kb:-0}" -ge $(( GI_SIZE / 1024 * 2 + 65536 )) ] \
    || die "$(m not_enough_free_space_computer "$((GI_SIZE * 2 / 1048576 + 64))" "$w")"
  say "$(m building_google_services_add_image "$GZ_NAME")"
  if ! gi_tar "$zip" "$w/gapps.tar"; then
    die "$(m zip_not_hold_expected_files "$(cat "$w/bad" 2>/dev/null)")"
  fi
  rm -f "$w/file"
  "$EU_DIR/mkfs.erofs" -b4096 -E noinline_data -T"$GI_MTIME" -U 00000000-0000-0000-0000-000000000000 --all-root --workers=1 \
    -Lz9x_gapps --tar=f "$img.part" "$w/gapps.tar" > "$w/mkfs.log" 2>&1 \
    || { rm -f "$w/gapps.tar" "$img.part"; die "$(m mkfs_erofs_failed): $(tail -n 3 "$w/mkfs.log" | tr '\n' ' '). $(m nothing_written)"; }
  rm -f "$w/gapps.tar"
  "$EU_DIR/fsck.erofs" "$img.part" > "$w/fsck.log" 2>&1 || { rm -f "$img.part"; die "fsck.erofs: $(tail -n 3 "$w/fsck.log" | tr '\n' ' '). $(m nothing_written)"; }
  # every file read back out of the image
  lines=$(gi_lines "$GZ_SHA")
  while read -r h s p; do
    got=$("$EU_DIR/dump.erofs" --cat --path="/$p" "$img.part" 2>/dev/null | sha256_in)
    [ "$got" = "$h" ] || { rm -f "$img.part"; die "$(m read_back_add_image_differs "$p")"; }
  done <<EOF
$lines
EOF
  [ "$("$EU_DIR/dump.erofs" --cat --path=/MANIFEST "$img.part" 2>/dev/null | sha256_in)" = "$(sha256 "$w/MANIFEST")" ] \
    || { rm -f "$img.part"; die "$(m manifest_read_back_add_image)"; }
  got=$(sha256 "$img.part")
  if [ "$got" != "$GI_SHA" ] || [ "$(wc -c < "$img.part" | tr -d ' ')" != "$GI_SIZE" ]; then
    rm -f "$img.part"
    die "$(m add_image_built_here_sha256 "$(printf '%s' "$got" | cut -c1-16)" "$(printf '%s' "$GI_SHA" | cut -c1-16)")"
  fi
  mv "$img.part" "$img"
  ok "$(m google_services_add_image): $(printf '%s\n' "$lines" | grep -c .) $(m files_checked), $(( (GI_SIZE + 1048575) / 1048576 )) $(m mb), sha256 $(printf '%s' "$GI_SHA" | cut -c1-16)..."
}

z9x_gapps_plan() {  # --gapps-zip, before anything else: the zip, and that the install saved here can take it
  local plan
  [ -f "$GAPPS_WITH" ] || die "$(m no_file "$GAPPS_WITH")"
  gapps_zipcheck "$GAPPS_WITH"
  plan=$(state_get gapps_plan)
  if [ "$(state_get step_flash)" = done ]; then
    # an older installer's install waiting for its fill (z9x_gapps_finish completes it), or this one-pass install done
    if ! { [ "$(state_get step_gapps_part)" = done ] && [ -z "$(state_get step_wipe)" ]; } \
       && [ "$(state_get step_gapps_flash)" != done ] && [ "$(state_get step_gapps_fill)" != done ]; then
      die "$(m lumen_os_installed_here_without)"
    fi
    [ -z "$plan" ] || [ "$plan" = "$GZ_SHA" ] || die "$(m install_started_another)"
  fi
}
z9x_gapps_prepare() {  # before the ERASE question: erofs-utils, the image takes the add-on, the add-on image built
  [ "$GI_READY" = 0 ] || return 0
  [ -n "$IMG" ] && [ -f "$IMG" ] || find_image
  [ "$(state_get image_gms)" = 0 ] || die "$(m image_has_google_services_built)"
  eu_setup
  gi_image_ok
  gi_build "$GAPPS_WITH"
  GI_READY=1
}
z9x_gapps_write() {  # slot, fastbootd after system (+ z9x_blobs): z9x_gapps_<slot> sized to the image and flashed;
  # 1 = left out (warned; the partition is deleted, so the system starts plainly without Google)
  local p=z9x_gapps$1 need free cur=0 why
  need=$(( (GI_SIZE + 1048575) / 1048576 * 1048576 ))
  why=$(m install_goes_without_google)
  GV=$(fb getvar all 2>&1)   # system and z9x_blobs have their new sizes now
  free=$(super_free)
  if printf '%s\n' "$GV" | grep -q "partition-size:$p:"; then cur=$(( $(gv_size "$p") )); fi
  if [ $(( free + cur )) -lt "$need" ]; then
    [ "$cur" -gt 0 ] && fb_do delete "$p" >/dev/null 2>&1
    warn "$(m not_enough_free_space_super_2 "$(( (free + cur) / 1048576 ))" "$((need / 1048576))" "$why")"
    state_set step_gapps_flash skipped:room; return 1
  fi
  say "$(m writing_google_services_mb_about "$p" "$((need / 1048576))")"
  if [ "$cur" -gt 0 ]; then fb_do resize "$p" "$need"; else fb_do create "$p" "$need"; fi \
    || { fb_do delete "$p" >/dev/null 2>&1; warn "$(m could_not_created "$p" "$why")"; state_set step_gapps_flash skipped:create; return 1; }
  if ! fb_do flash "$p" "$GI_IMG"; then
    # fastbootd gone (its time limit, the cable): nothing is erased, the projector starts on the old data with USB
    # debugging, and the same command writes everything again in one visit
    z9x_in_fastboot || die "$(m fastbootd_ended_while_being "$p")"
    fb_do delete "$p" >/dev/null 2>&1
    warn "$(m could_not_written_removed_again "$p" "$why")"
    state_set step_gapps_flash skipped:flash; return 1
  fi
  state_set step_gapps_flash done; state_set gapps "$GZ_NAME"; state_set gapps_img "$GI_SHA"
  ok "$p: $GZ_NAME"
}
z9x_in_fastboot() { [ -n "$SERIAL" ] && fastboot devices 2>/dev/null | awk '{print $1}' | grep -qx "$SERIAL"; }
z9x_gapps_finish() {  # an install of an older installer that waits on the old data for its fill: the add-on flashed
  # as erofs in fastbootd (with --gapps-zip), then the one data wipe there
  local slot a
  slot=$(state_get slot)
  if [ -z "$GAPPS_WITH" ]; then
    say "$(m install_started_google_services)"
    ask "$(m finish_install_without_google)" \
      || die "$(m run_same_command_again_gapps)"
    state_set gapps_plan ""
  else
    FORCE_HASH=1 check_image
    z9x_gapps_prepare
  fi
  if [ "$ERASE_NOW" != 1 ]; then
    echo
    m lumen_os_written_next_installer; echo
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_erased)"
    ERASE_NOW=1
  fi
  if z9x_in_fastboot; then fbd_check "$slot"; else fbd_enter "$slot"; fi
  if [ -n "$GAPPS_WITH" ]; then z9x_gapps_write "$slot" || state_set gapps_plan ""; fi
  fb_wipe
  fb reboot >/dev/null 2>&1
  state_set step_gapps_part ""; state_set step_gapps_fill ""
  if [ "$(state_get step_gapps_flash)" = done ]; then
    ok "$(m data_wiped_first_start_takes)"
  else
    ok "$(m data_wiped_first_start_takes_2)"
  fi
}

# ------------------------------------------------------------------ projector profiles (lib/devices.conf, lib/flows.conf)
# What a projector IS comes from lib/devices.conf, which commands its FLOW has from lib/flows.conf: data only,
# checked line by line here and never sourced. What the installer DOES (the steps of each flow, and fb_allowed)
# stays in this script, so neither file can widen what is written.
DEVCONF="$HERE/lib/devices.conf"
FLOWCONF="$HERE/lib/flows.conf"
P_NAME=""; P_MODEL=""; P_BOARD=""; P_FB_PRODUCT=""; P_VENDOR_OK=""; P_CERT=""; P_TOKEN=""; P_FS=""; P_VERSION_PROP=""
P_LUMEN_PROP=""; P_LOGICAL=""; P_RESERVE=0; P_GUIDE=""; P_RESTORE_BIN=""; P_RESTORE_MD5=""; P_RESTORE_URL=""
P_FLOW=""; P_SLOTS=""; P_KEEP=""; P_WIPE=""; P_SYSMAX=""; P_BLOBS_ALLOW=""   # FLOW=ab-fastbootd and its keys
P_GAPPS_ALLOW=""; P_GAPPS_IMAGES=""   # FLOW=ab-fastbootd, optional (both or neither): the Google services add-on
P_BLOBS_SIZE=""   # FLOW=ab-fastbootd, optional: its z9x_blobs partition in bytes (default BLOB_SIZE)
P_FAMILY=""       # optional, instead of FB_PRODUCT: the id whose product image (and fastbootd 'product') it shares
P_SLOT_CHECK=""   # FLOW=ab-fastbootd, optional: yes = the other slot told (and asked) before the write, the slots read after
P_WIPE_FS=""      # FLOW=ab-fastbootd, optional: ext4 (default) or f2fs, the file system WIPE_PARTS are formatted with
# FLOW=ab-fastbootd, optional (all of them or none): the model code x stock firmware table of a projector sold under
# several model codes, each model's stock product name, the stock ro.product.device, the root of the per-model stock
# config folders, and the model codes of that projector that are not supported yet
P_MODELS_OK=""; P_PRODUCT_NAMES=""; P_DEVICE=""; P_MODEL_CONFIG=""; P_MODELS_NOT_YET=""
P_MODELS_CHECK_ONLY=""   # with MODELS_OK, optional: models of the table that get 'check' and 'blobs' only, no write
P_ABI=""          # FLOW=ab-fastbootd, optional: arm32 | arm64, the userspace its image must be built for
P_TODO=""         # the keys of the profile whose value is TODO: not finished, nothing is written (abf_todo_stop)
ABF_MODEL=""      # the model code abf_identity found (one of MODELS_OK, else MODEL)
P_KEYS="NAME MODEL BOARD FB_PRODUCT VENDOR_OK CERT IMAGE_TOKEN IMAGE_FS VERSION_PROP LUMEN_PROP LOGICAL SUPER_RESERVE GUIDE RESTORE_BIN RESTORE_MD5 RESTORE_URL FLOW SLOTS KEEP WIPE_PARTS SYSTEM_MAX BLOBS_ALLOW GAPPS_ALLOW GAPPS_IMAGES BLOBS_SIZE FAMILY SLOT_CHECK WIPE_FS MODELS_OK PRODUCT_NAMES DEVICE MODEL_CONFIG MODELS_NOT_YET MODELS_CHECK_ONLY ABI"
TODO_KEYS="VENDOR_OK LOGICAL KEEP SYSTEM_MAX BLOBS_ALLOW BLOBS_SIZE RESTORE_BIN"   # the only keys that may say TODO
devconf_check() {  # stops on a missing file, a broken line, an unknown key or a key given twice
  local bad
  [ -f "$DEVCONF" ] || die "internal: lib/devices.conf is missing: re-download the installer"
  bad=$(awk -v keys=" $P_KEYS " '
    { sub(/\r$/, "") }
    /^#/ || /^$/ { next }
    !/^[a-z0-9]+\.[A-Z][A-Z0-9_]*=[A-Za-z0-9 ._:\/,-]*$/ { print NR; exit }
    { k = substr($0, 1, index($0, "=") - 1); n = k; sub(/^[^.]*\./, "", n)
      if (index(keys, " " n " ") == 0 || (k in seen)) { print NR; exit }
      seen[k] = 1 }' "$DEVCONF")
  [ -z "$bad" ] || die "internal: lib/devices.conf line $bad is not valid (unknown key, a key given twice, or characters that are not allowed): re-download the installer"
}
devconf_get() {  # id KEY -> value (nothing without lib/devices.conf)
  [ -f "$DEVCONF" ] || return 0
  awk -v k="$1.$2=" '{ sub(/\r$/, "") } index($0, k) == 1 { print substr($0, length(k) + 1); exit }' "$DEVCONF"
}
is_todo() { case "$1" in TODO*) return 0 ;; esac; return 1; }   # a profile value that is not known yet (TODO, TODO:<why>)
devconf_todo() {  # id -> the keys of that profile whose value is TODO, space-separated (nothing: finished)
  [ -f "$DEVCONF" ] || return 0
  awk -v id="$1." '{ sub(/\r$/, "") } index($0, id) == 1 { k = substr($0, length(id) + 1); e = index(k, "=")
    if (index(substr(k, e + 1), "TODO") == 1) { printf "%s%s", s, substr(k, 1, e - 1); s = " " } }' "$DEVCONF"
}
dev_name() {  # id -> the name shown to the user
  local n; n=$(devconf_get "$1" NAME)
  printf '%s' "${n:-$1}"
}
profile_ids() {  # every projector of lib/devices.conf, in its order
  [ -f "$DEVCONF" ] || return 0
  awk '{ sub(/\r$/, "") } /^[a-z0-9]+\.NAME=/ { print substr($0, 1, index($0, ".") - 1) }' "$DEVCONF"
}
profile_is() { [ -n "$1" ] && profile_ids | grep -qx -- "$1"; }   # id -> 0 for a projector of lib/devices.conf
profile_flow() { [ -n "$1" ] && devconf_get "$1" FLOW; return 0; }   # id -> its FLOW
flow_id() {  # flow -> the first projector of lib/devices.conf with that FLOW
  [ -f "$DEVCONF" ] || return 0
  awk -v f="$1" '{ sub(/\r$/, "") } /^[a-z0-9]+\.FLOW=/ && substr($0, index($0, "=") + 1) == f { print substr($0, 1, index($0, ".") - 1); exit }' "$DEVCONF"
}
state_dir_of() {  # id -> the folder of its saved progress (lib/flows.conf STATE)
  if [ "$(flow_get "$(profile_flow "$1")" STATE)" = root ]; then printf '%s' "$STATE_DIR"; else printf '%s' "$STATE_DIR/$1"; fi
}
state_file_of() { printf '%s/state' "$(state_dir_of "$1")"; }
not_yet() { die "$(m not_available_yet_nothing_done "$1" "$P_NAME")"; }   # a command or option this projector has not yet
device_opt_text() { printf '%s' "--device $(profile_ids | tr '\n' '|' | sed 's/|$//')"; }
flowconf_check() {  # lib/flows.conf: every line flow.KEY=value with a known key, no key twice, a known step name
  local bad
  [ -f "$FLOWCONF" ] || die "internal: lib/flows.conf is missing: re-download the installer"
  bad=$(awk '
    { sub(/\r$/, "") }
    /^#/ || /^$/ { next }
    !/^[a-z0-9-]+\.(KEYS|STATE|START|TOOLS|NOT_NEEDED|NOT_YET|NEEDS|CMD_[A-Z]+)=[A-Za-z0-9 ._:\/,-]*$/ { print NR; exit }
    /\.(START|CMD_[A-Z]+)=/ && !/=(cmd|stick|play6|abf)_[a-z0-9_]+( [0-9]+)?$/ { print NR; exit }
    { k = substr($0, 1, index($0, "=") - 1); if (k in seen) { print NR; exit }; seen[k] = 1 }' "$FLOWCONF")
  [ -z "$bad" ] || die "internal: lib/flows.conf line $bad is not valid: re-download the installer"
}
flow_get() {  # flow KEY -> value (nothing without lib/flows.conf)
  [ -n "$1" ] && [ -f "$FLOWCONF" ] || return 0
  awk -v k="$1.$2=" '{ sub(/\r$/, "") } index($0, k) == 1 { print substr($0, length(k) + 1); exit }' "$FLOWCONF"
}
profile_of() {  # model board -> the id of the profile that has both (nothing: unknown); a profile's model codes are
  # its MODEL, the models of its MODELS_OK and its MODELS_NOT_YET
  [ -f "$DEVCONF" ] || return 0
  awk -v m="$1" -v b="$2" '{ sub(/\r$/, "") }
    /^[a-z0-9]+\.MODEL=/ { i = substr($0, 1, index($0, ".") - 1); mo[i] = substr($0, index($0, "=") + 1); o[++n] = i }
    /^[a-z0-9]+\.BOARD=/ { i = substr($0, 1, index($0, ".") - 1); bo[i] = substr($0, index($0, "=") + 1) }
    /^[a-z0-9]+\.(MODELS_OK|MODELS_NOT_YET)=/ { i = substr($0, 1, index($0, ".") - 1)
      k = split(substr($0, index($0, "=") + 1), a, " "); for (x = 1; x <= k; x++) { sub(/:.*/, "", a[x]); ex[i] = ex[i] " " a[x] } }
    END { for (j = 1; j <= n; j++) if ((mo[o[j]] == m || index(ex[o[j]] " ", " " m " ") > 0) && bo[o[j]] == b) { print o[j]; exit } }' "$DEVCONF"
}
profiles_named() {  # "XGIMI Z9X (model) and XGIMI Play 6 (...)": every projector of lib/devices.conf whose flow is not
  # ab-fastbootd (a FLOW=ab-fastbootd one is named by its own path, abf_other_hint)
  awk -v conj="$(m and)" '{ sub(/\r$/, "") }
    /^[a-z0-9]+\.NAME=/ { i = substr($0, 1, index($0, ".") - 1); nm[i] = substr($0, index($0, "=") + 1); o[++n] = i }
    /^[a-z0-9]+\.MODEL=/ { i = substr($0, 1, index($0, ".") - 1); mo[i] = substr($0, index($0, "=") + 1) }
    /^[a-z0-9]+\.FLOW=ab-fastbootd$/ { fl[substr($0, 1, index($0, ".") - 1)] = 1 }
    END { for (j = 1; j <= n; j++) if (!(o[j] in fl)) b[++m] = o[j]
          for (j = 1; j <= m; j++) s = s (j > 1 ? (j == m ? " " conj " " : ", ") : "") nm[b[j]] " (" mo[b[j]] ")"; print s }' "$DEVCONF"
}
p_var() {  # a key of lib/devices.conf -> the variable load_profile keeps it in
  case "$1" in IMAGE_TOKEN) echo P_TOKEN ;; IMAGE_FS) echo P_FS ;; SUPER_RESERVE) echo P_RESERVE ;; *) echo "P_$1" ;; esac
}
load_profile() {  # id: the P_* values of that projector
  local k v
  devconf_check
  [ -n "$(devconf_get "$1" NAME)" ] || die "internal: lib/devices.conf has no projector '$1': re-download the installer"
  while IFS='=' read -r k v; do
    case "$k" in
      NAME) P_NAME=$v ;; MODEL) P_MODEL=$v ;; BOARD) P_BOARD=$v ;; FB_PRODUCT) P_FB_PRODUCT=$v ;;
      VENDOR_OK) P_VENDOR_OK=$v ;; CERT) P_CERT=$v ;; IMAGE_TOKEN) P_TOKEN=$v ;; IMAGE_FS) P_FS=$v ;;
      VERSION_PROP) P_VERSION_PROP=$v ;; LUMEN_PROP) P_LUMEN_PROP=$v ;; LOGICAL) P_LOGICAL=$v ;;
      SUPER_RESERVE) P_RESERVE=$v ;; GUIDE) P_GUIDE=$v ;; RESTORE_BIN) P_RESTORE_BIN=$v ;; RESTORE_MD5) P_RESTORE_MD5=$v ;;
      RESTORE_URL) P_RESTORE_URL=$v ;;
      FLOW) P_FLOW=$v ;; SLOTS) P_SLOTS=$v ;; KEEP) P_KEEP=$v ;; WIPE_PARTS) P_WIPE=$v ;; SYSTEM_MAX) P_SYSMAX=$v ;;
      BLOBS_ALLOW) P_BLOBS_ALLOW=$v ;; GAPPS_ALLOW) P_GAPPS_ALLOW=$v ;; GAPPS_IMAGES) P_GAPPS_IMAGES=$v ;;
      BLOBS_SIZE) P_BLOBS_SIZE=$v ;; FAMILY) P_FAMILY=$v ;; SLOT_CHECK) P_SLOT_CHECK=$v ;; WIPE_FS) P_WIPE_FS=$v ;;
      MODELS_OK) P_MODELS_OK=$v ;; PRODUCT_NAMES) P_PRODUCT_NAMES=$v ;; DEVICE) P_DEVICE=$v ;;
      MODEL_CONFIG) P_MODEL_CONFIG=$v ;; MODELS_NOT_YET) P_MODELS_NOT_YET=$v ;; MODELS_CHECK_ONLY) P_MODELS_CHECK_ONLY=$v ;;
      ABI) P_ABI=$v ;;
    esac
  done <<EOF
$(awk -v id="$1." '{ sub(/\r$/, "") } index($0, id) == 1 { print substr($0, length(id) + 1) }' "$DEVCONF")
EOF
  # TODO (lib/devices.conf header): only with FLOW=ab-fastbootd and only for the facts a port may still lack
  # (TODO_KEYS). Such a profile is not finished: their checks below are skipped, abf_main and abf_check stop it
  P_TODO=$(devconf_todo "$1")
  for k in $P_TODO; do
    case " $TODO_KEYS " in *" $k "*) [ "$P_FLOW" = ab-fastbootd ] || die "internal: lib/devices.conf: $1.$k=TODO" ;; *) die "internal: lib/devices.conf: $1.$k=TODO" ;; esac
  done
  # ABI (optional, with FLOW=ab-fastbootd): the userspace of its stock vendor, checked in the image (abf_check_image)
  case "$P_ABI" in '') ;; arm32|arm64) [ "$P_FLOW" = ab-fastbootd ] || die "internal: lib/devices.conf: $1.ABI" ;; *) die "internal: lib/devices.conf: $1.ABI" ;; esac
  # FAMILY: the fastbootd 'product' of the projector whose XGIMI product image this one shares (never its own
  # FB_PRODUCT as well; that projector names its own and has no FAMILY): MODEL and BOARD tell the two apart
  if [ -n "$P_FAMILY" ]; then
    { [ -z "$P_FB_PRODUCT" ] && [ "$P_FAMILY" != "$1" ] && [ -z "$(devconf_get "$P_FAMILY" FAMILY)" ] \
      && [ "$(devconf_get "$P_FAMILY" MODEL)" != "$P_MODEL" ]; } || die "internal: lib/devices.conf: $1.FAMILY"
    P_FB_PRODUCT=$(devconf_get "$P_FAMILY" FB_PRODUCT)
    [ -n "$P_FB_PRODUCT" ] || die "internal: lib/devices.conf: $1.FAMILY"
  fi
  # FLOW: one of lib/flows.conf, whose KEYS this profile must have
  flowconf_check
  [ -n "$(flow_get "$P_FLOW" KEYS)" ] || die "internal: lib/devices.conf: $1.FLOW=$P_FLOW"
  for k in $(flow_get "$P_FLOW" KEYS); do
    v=$(p_var "$k"); [ -n "${!v}" ] || die "internal: lib/devices.conf: '$1' has no $k: re-download the installer"
  done
  case "$P_RESERVE" in *[!0-9]*) die "internal: lib/devices.conf: $1.SUPER_RESERVE" ;; esac
  case "$P_LUMEN_PROP" in ''|*" "*) ;; *) die "internal: lib/devices.conf: $1.LUMEN_PROP" ;; esac
  # SLOT_CHECK (optional): only 'yes', only with FLOW=ab-fastbootd, and only with RESTORE_BIN (its texts name the way back)
  if [ -n "$P_SLOT_CHECK" ]; then
    { [ "$P_SLOT_CHECK" = yes ] && [ "$P_FLOW" = ab-fastbootd ] && [ -n "$P_RESTORE_BIN" ] && ! is_todo "$P_RESTORE_BIN"; } || die "internal: lib/devices.conf: $1.SLOT_CHECK"
  fi
  # WIPE_FS (optional, only with FLOW=ab-fastbootd): ext4 or f2fs, as the projector's stock fstab mounts userdata and metadata
  if [ -n "$P_WIPE_FS" ]; then
    { [ "$P_FLOW" = ab-fastbootd ] && { [ "$P_WIPE_FS" = ext4 ] || [ "$P_WIPE_FS" = f2fs ]; }; } || die "internal: lib/devices.conf: $1.WIPE_FS"
  fi
  # the model x firmware table (optional, only with FLOW=ab-fastbootd)
  if [ -n "$P_MODELS_OK$P_PRODUCT_NAMES$P_DEVICE$P_MODEL_CONFIG$P_MODELS_NOT_YET$P_MODELS_CHECK_ONLY" ]; then
    [ "$P_FLOW" = ab-fastbootd ] || die "internal: lib/devices.conf: $1.MODELS_OK"
    abf_table_check "$1"
  fi
  [ "$P_FLOW" = ab-fastbootd ] || return 0
  [ -n "$P_WIPE_FS" ] || P_WIPE_FS=ext4
  # FLOW=ab-fastbootd: every one of its keys; what they may name is narrowed here (fb_allowed narrows it again)
  for k in SLOTS:P_SLOTS KEEP:P_KEEP WIPE_PARTS:P_WIPE SYSTEM_MAX:P_SYSMAX BLOBS_ALLOW:P_BLOBS_ALLOW; do
    v=${k#*:}; [ -n "${!v}" ] || die "internal: lib/devices.conf: '$1' has no ${k%%:*}: re-download the installer"
  done
  case "$P_SYSMAX" in TODO*) ;; *[!0-9]*) die "internal: lib/devices.conf: $1.SYSTEM_MAX" ;; esac
  for k in $P_SLOTS; do case "$k" in a|b) ;; *) die "internal: lib/devices.conf: $1.SLOTS" ;; esac; done
  for k in $P_WIPE; do case "$k" in userdata|metadata) ;; *) die "internal: lib/devices.conf: $1.WIPE_PARTS" ;; esac; done
  if ! is_todo "$P_BLOBS_ALLOW"; then
    case "$P_BLOBS_ALLOW" in lib/*.txt) ;; *) die "internal: lib/devices.conf: $1.BLOBS_ALLOW" ;; esac
    case "$P_BLOBS_ALLOW" in *..*) die "internal: lib/devices.conf: $1.BLOBS_ALLOW" ;; esac
  fi
  # its z9x_blobs partition (optional): bytes, whole MiB from 4 to 64; without it the 4 MiB of BLOB_SIZE
  if [ -n "$P_BLOBS_SIZE" ] && ! is_todo "$P_BLOBS_SIZE"; then
    case "$P_BLOBS_SIZE" in *[!0-9]*) die "internal: lib/devices.conf: $1.BLOBS_SIZE" ;; esac
    { [ $((P_BLOBS_SIZE % 1048576)) = 0 ] && [ "$P_BLOBS_SIZE" -ge 4194304 ] && [ "$P_BLOBS_SIZE" -le 67108864 ]; } \
      || die "internal: lib/devices.conf: $1.BLOBS_SIZE"
    BLOB_SIZE=$P_BLOBS_SIZE
  fi
  [ "$P_FS" = erofs ] || die "internal: lib/devices.conf: $1.IMAGE_FS (ab-fastbootd: erofs)"
  # the Google services add-on: both lists or neither (then gapps, gsf and --gapps-zip are not there for it)
  if [ -n "$P_GAPPS_ALLOW$P_GAPPS_IMAGES" ]; then
    for k in GAPPS_ALLOW:P_GAPPS_ALLOW GAPPS_IMAGES:P_GAPPS_IMAGES; do
      v=${k#*:}
      case "${!v}" in lib/*.txt) ;; *) die "internal: lib/devices.conf: $1.${k%%:*}" ;; esac
      case "${!v}" in *..*) die "internal: lib/devices.conf: $1.${k%%:*}" ;; esac
    done
    GAPPS_ALLOW_F=$HERE/$P_GAPPS_ALLOW; GAPPS_IMAGES_F=$HERE/$P_GAPPS_IMAGES
  fi
}
abf_table_check() {  # id: MODELS_OK and its companion keys are one consistent table, else an internal STOP
  # MODELS_OK: 'G0001:v1.2.3,v1.3.0 G0002:v1.3.0', every model once, its first model is MODEL, every
  # firmware in VENDOR_OK and every firmware of VENDOR_OK some model's; PRODUCT_NAMES: 'G0001:Product_x ...' for
  # exactly those models; DEVICE: one word; MODEL_CONFIG: a folder under /mnt/vendor/; MODELS_NOT_YET: model codes
  # not in MODELS_OK; MODELS_CHECK_ONLY: models of MODELS_OK, each once
  local e m v l first="" mods="" fws="" bad="internal: lib/devices.conf: $1.MODELS_OK"
  { [ -n "$P_MODELS_OK" ] && [ -n "$P_PRODUCT_NAMES" ] && [ -n "$P_DEVICE" ] && [ -n "$P_MODEL_CONFIG" ]; } || die "$bad"
  for e in $P_MODELS_OK; do
    m=${e%%:*}; l=${e#*:}
    case "$m" in G[0-9][0-9][0-9][0-9]) ;; *) die "$bad" ;; esac
    case "$l" in ''|*:*|,*|*,|*,,*) die "$bad" ;; esac
    case " $mods " in *" $m "*) die "$bad" ;; esac
    [ -n "$first" ] || first=$m
    mods="$mods $m"
    for v in $(printf '%s' "$l" | tr ',' ' '); do
      case "$v" in v[0-9]*) ;; *) die "$bad" ;; esac
      case "$v" in *[!v0-9.]*) die "$bad" ;; esac
      case " $P_VENDOR_OK " in *" $v "*) ;; *) die "$bad ($m:$v is not in VENDOR_OK)" ;; esac
      fws="$fws $v"
    done
  done
  [ "$first" = "$P_MODEL" ] || die "$bad (its first model is not MODEL)"
  for v in $P_VENDOR_OK; do case " $fws " in *" $v "*) ;; *) die "$bad (VENDOR_OK $v is no model's)" ;; esac; done
  l=0
  for e in $P_PRODUCT_NAMES; do
    m=${e%%:*}; v=${e#*:}
    { [ "$m:$v" = "$e" ] && [ -n "$v" ]; } || die "internal: lib/devices.conf: $1.PRODUCT_NAMES"
    case " $mods " in *" $m "*) ;; *) die "internal: lib/devices.conf: $1.PRODUCT_NAMES" ;; esac
    l=$((l + 1))
  done
  [ "$l" = "$(printf '%s\n' $mods | wc -l | tr -d ' ')" ] || die "internal: lib/devices.conf: $1.PRODUCT_NAMES"
  case "$P_DEVICE" in *" "*) die "internal: lib/devices.conf: $1.DEVICE" ;; esac
  case "$P_MODEL_CONFIG" in /mnt/vendor/?*) ;; *) die "internal: lib/devices.conf: $1.MODEL_CONFIG" ;; esac
  case "$P_MODEL_CONFIG" in *..*|*" "*|*/) die "internal: lib/devices.conf: $1.MODEL_CONFIG" ;; esac
  for m in $P_MODELS_NOT_YET; do
    case "$m" in G[0-9][0-9][0-9][0-9]) ;; *) die "internal: lib/devices.conf: $1.MODELS_NOT_YET" ;; esac
    case " $mods " in *" $m "*) die "internal: lib/devices.conf: $1.MODELS_NOT_YET" ;; esac
  done
  l=""
  for m in $P_MODELS_CHECK_ONLY; do
    case " $mods " in *" $m "*) ;; *) die "internal: lib/devices.conf: $1.MODELS_CHECK_ONLY" ;; esac
    case " $l " in *" $m "*) die "internal: lib/devices.conf: $1.MODELS_CHECK_ONLY" ;; esac
    l="$l $m"
  done
}
pair_get() {  # "K:V K2:V2 ..." K -> V (status 1: no such K)
  local e
  for e in $1; do [ "${e%%:*}" = "$2" ] && { printf '%s' "${e#*:}"; return 0; }; done
  return 1
}
abf_is_model() {  # model: 0 = a model code of this profile (one of MODELS_OK, else MODEL)
  [ -n "$1" ] || return 1
  [ -n "$P_MODELS_OK" ] || { [ "$1" = "$P_MODEL" ]; return; }
  pair_get "$P_MODELS_OK" "$1" >/dev/null
}
abf_model_fw() {  # model -> the firmware it may run, space separated (its MODELS_OK entry, else VENDOR_OK)
  if [ -n "$P_MODELS_OK" ]; then pair_get "$P_MODELS_OK" "$1" | tr ',' ' '; else printf '%s' "$P_VENDOR_OK"; fi
}
abf_check_only() {  # 0 = the model abf_identity found is one of MODELS_CHECK_ONLY: 'check' and 'blobs', no write yet
  [ -n "$ABF_MODEL" ] && case " $P_MODELS_CHECK_ONLY " in *" $ABF_MODEL "*) true ;; *) false ;; esac
}
abf_live_gate() {  # every write ('flash', 'all', 'gapps --zip', 'gapps --remove') once abf_device ran: a model of
  # MODELS_CHECK_ONLY is refused before anything is written. It mirrors the live switch of the installers that run on
  # the projector itself (a check and a dry run only, until one live test on that model)
  abf_check_only || return 0
  die "$(m support_testing_lumen_os_goes "$ABF_MODEL" "$P_NAME")"
}
abf_fw_names() {  # "v1.2.3 v1.3.0" -> "V1.2.3 and V1.3.0"
  printf '%s\n' "$1" | awk -v conj="$(m and)" '{ for (i = 1; i <= NF; i++) s = s (i > 1 ? (i == NF ? " " conj " " : ", ") : "") "V" substr($i, 2); print s }'
}
abf_matrix() {  # "G0001 (V1.2.3, V1.3.0) and G0002 (V1.3.0)": the models and the firmware of each
  printf '%s\n' "$P_MODELS_OK" | awk -v conj="$(m and)" '{ for (i = 1; i <= NF; i++) { m = $i; sub(/:.*/, "", m); f = $i; sub(/^[^:]*:/, "", f)
      k = split(f, a, ","); f = ""; for (j = 1; j <= k; j++) f = f (j > 1 ? ", " : "") "V" substr(a[j], 2)
      s = s (i > 1 ? (i == NF ? " " conj " " : ", ") : "") m " (" f ")" } print s }'
}
other_hint() {  # model board: the Z9X check met another projector this installer knows: its way in
  local id; id=$(profile_of "$1" "$2")
  case "$(profile_flow "$id")" in
    play6-wifi) printf ' %s' "$(m installs_over_wi_fi_run "$(dev_name "$id")")" ;;
    ab-fastbootd) abf_other_hint "$1" "$2" ;;
  esac
  return 0
}
p6_unfinished() {  # 0 = a Play 6 install is saved that was checked and has not reached the wipe yet
  local f; f=$(state_file_of "$(flow_id play6-wifi)")
  [ -f "$f" ] && grep -qx 'step_check=done' "$f" && ! grep -q '^step_wipe=.' "$f"
}
z9x_unfinished() {
  local f; f=$(state_file_of "$(flow_id z9x-usb)")
  [ -f "$f" ] && grep -qx 'step_check=done' "$f" && ! grep -qx 'step_flash=done' "$f"
}
resolve_device() {  # the projector: --device, else --ip or 'stick' (Play 6), this download, an unfinished own build
  local usb wifi
  devconf_check
  [ -z "$DEVICE_OPT" ] || profile_is "$DEVICE_OPT" || die "$(device_opt_text)"
  [ -z "$REL_DEVICE" ] || profile_is "$REL_DEVICE" || die "internal: release.conf DEVICE=$REL_DEVICE: re-download the installer"
  usb=$(flow_id z9x-usb); wifi=$(flow_id play6-wifi)
  # A download names its projector (release.conf DEVICE, none = the Z9X), so a Z9X download behaves exactly as it
  # always did, also on a computer that keeps an unfinished Play 6 install. Only your own builds
  # (--unsigned-image, not bound to a download) let an unfinished install name the projector.
  # A download for a FLOW=ab-fastbootd projector means it with --ip and 'stick' too (never the Play 6).
  PROJECTOR=$usb
  if [ -n "$DEVICE_OPT" ]; then PROJECTOR=$DEVICE_OPT
  elif [ "$(profile_flow "$REL_DEVICE")" = ab-fastbootd ]; then PROJECTOR=$REL_DEVICE
  elif [ "$IP_GIVEN" = 1 ] || [ "${CMD:-all}" = stick ]; then PROJECTOR=$wifi
  elif [ -n "$REL_DEVICE" ]; then PROJECTOR=$REL_DEVICE
  elif [ "$UNSIGNED" = 1 ] && p6_unfinished; then
    if z9x_unfinished; then
      die "$(m two_unfinished_installs_saved "$(dev_name "$wifi")")"
    fi
    PROJECTOR=$wifi
  fi
  if [ "$(profile_flow "$PROJECTOR")" = z9x-usb ] && [ "$IP_GIVEN" = 1 ]; then
    die "$(m ip_xgimi_z9x_installs_over "$(dev_name "$wifi")")"
  fi
  load_profile "$PROJECTOR"
  SD=$(state_dir_of "$PROJECTOR")
  if [ -n "$P_TOKEN" ]; then IMG_GLOB="lumen-os-$P_TOKEN-*-system.img"; fi
}
release_guard() {  # the image of this download belongs to one projector (release.conf DEVICE; none = the Z9X)
  local rel=${REL_DEVICE:-$(flow_id z9x-usb)}
  [ "$rel" = "$PROJECTOR" ] && return 0
  if [ "$UNSIGNED" = 1 ]; then
    warn "$(m installer_folder_came_release_own "$(dev_name "$rel")")"
    return 0
  fi
  die "$(m download_installer_not_download "$(dev_name "$rel")" "$(dev_name "$PROJECTOR")")"
}

# ------------------------------------------------------------------ XGIMI Play 6
# adb runs over Wi-Fi only (ADB_T = ADDR:PORT): the projector's one USB-A port is a host port under Android.
# fastbootd is reached with 'adb reboot fastboot' sent over Wi-Fi and a USB A-to-A cable in that port; the
# projector does not start while that cable is in, so it goes in only for the write. Steps: check (with the
# pre-checks: Wi-Fi chip, serial, firmware, power, the USB port; logged in $STATE_DIR/play6/install.log) ->
# backup (asked) -> write (fastbootd: system, then the erase request for XGIMI's recovery in misc, the same
# 2 KiB that Android's factory reset writes there) -> boot_fresh (XGIMI's recovery formats userdata and cache,
# as for Settings > Reset, BEFORE the first start of Lumen OS, so that first start is a light one on clean
# data; the computer loses the projector with the Wi-Fi network) -> verify (optional, later, after the user
# joined Wi-Fi and turned debugging on). Since 2026-10-31: the first start on the old stock data was the
# heaviest start of all, and the Play 6 cuts its power when its drivers come up too late
# (H1). The earlier way stays as the fallback, and for --keep-data and
# installs begun by an older installer: boot1 (the first start of Lumen OS on the old data, over Wi-Fi) ->
# wipe (Android's own factory reset, asked for as root over adb). Progress: $STATE_DIR/play6/state.
P6_APP=org.lumen.projector                          # the projector app of the image (1.0.8 and later)
P6_APP_OLD=org.z9x.projector                       # its name up to 1.0.7 (status of an older install)
P6_TUNER=com.zacharee1.systemuituner              # installed on stock to turn debugging on; a wipe removes it
P6_MARKER=/data/local/tmp/lumen-install-wipe      # written before the wipe; a real wipe removes it
# the erase request (struct bootloader_message, 2048 bytes): command "boot-recovery" at 0, recovery
# "recovery\n--wipe_data\n--reason=lumen-install\n" at 64, every other byte zero. Byte for byte what Android's
# factory reset (RecoverySystem through uncrypt) writes at offset 0 of misc, without its --locale line; init
# writes the same 2 KiB for every 'adb reboot fastboot'. XGIMI's recovery reads it at the next start, formats
# userdata and cache, clears it and starts the system. Nothing else in misc is written.
P6_BCB_ARGS='recovery\n--wipe_data\n--reason=lumen-install\n'
P6_BCB_SHA=aa5a031e5dcedc6346b920fd73cc9ccf623ec0a795f31c246f77fd6c26ef16fc
P6_MIN_BATTERY=50                                 # percent, before any write and before the erase
# the USB devices on the running system: "<name> <vid>:<pid> <class of interface 0>" (no double quotes: PowerShell
# 5.1 does not pass them to adb.exe intact; the internal Wi-Fi and
# Bluetooth chip sits on its own bus, the one USB-A port on another; root hubs are 1d6b)
P6_USB_CMD='for d in /sys/bus/usb/devices/*; do [ -f $d/idVendor ] || continue; echo ${d##*/} $(cat $d/idVendor):$(cat $d/idProduct) $(cat $d/*:1.0/bInterfaceClass 2>/dev/null | head -n 1); done'
# XGIMI's power daemon logs the battery level and the adapter (stock Android itself says "battery present: false")
P6_PWR_CMD="logcat -d -v brief 2>/dev/null | grep -E 'POWER_BATTERY_LEVEL|IsAdapterPowered|AdapterOut|AdapterInsert|P6Battery' | tail -n 50"
P6_MNT_CMD="grep ' /mnt/media_rw/' /proc/mounts"
# the vendor's own AIC8800 driver (the last firmware of VENDOR_OK: its wlan_dongle_detect loads it when dongle.cfg maps the chip)
P6_AIC_CMD="grep -c '^[^#]*:368b:8d90:' /vendor/lib/firmware/dongle.cfg"

p6_lprop() { printf '%s' "${P_LUMEN_PROP%% *}"; }  # the property that names the device on Lumen OS
p6_lval() { printf '%s' "${P_LUMEN_PROP#* }"; }    # and its value for this projector
p6_guide() { if [ "$LANG_UI" = ru ]; then printf '%s/%s/ru.md' "$GUIDE_BASE" "$P_GUIDE"; else printf '%s/%s/en.md' "$GUIDE_BASE" "$P_GUIDE"; fi; }
P6_ST=""             # what 'adb get-state' said last in wifi_connect (unauthorized: the allow prompt was not accepted)
p6_noconn_text() {  # [seconds]: why wifi_connect gave up, as the user can act on it
  local w
  case "${1:-60}" in 60) w=$(m within_minute) ;; *) w=$(m within_minutes "$(( ${1} / 60 ))") ;; esac
  case "$P6_ST" in
    *unauthorized*)
      m projector_did_not_allow_computer "$ADB_T" "$w" ;;
    *)
      m no_adb_connection_over_wi "$ADB_T" "$w" "$P_NAME" ;;
  esac
}

p6_discover() {  # [serial]: ADDR:PORT of the one projector that 'adb mdns services' lists (with that serial)
  local l
  l=$(adb mdns services 2>/dev/null | tr -d '\r' \
      | awk -v s="${1:+adb-$1-}" '$1 ~ /^adb-/ && $2 ~ /^_adb\._tcp\.?$/ && (s == "" || index($1, s) == 1) { print $NF }' | sort -u)
  [ "$(printf '%s\n' "$l" | grep -c .)" = 1 ] && printf '%s\n' "$l"
  return 0
}

wifi_connect() {  # seconds: adb to ADB_T until it answers as 'device' (0) or the time is up (1)
  # The time counts both the pauses and the real clock (adb connect and get-state can take seconds each on a
  # network that drops packets), so "a minute" stays about a minute.
  local i=0 st said=0 a t0=$SECONDS
  while :; do
    adb connect "$ADB_T" >/dev/null 2>&1
    st=$(adb -s "$ADB_T" get-state 2>&1 | tr -d '\r')
    P6_ST=$st
    case "$st" in
      device) return 0 ;;
      *unauthorized*)
        [ "$said" = 1 ] || say "$(m projector_allow_debugging_computer)"
        said=1 ;;
      *offline*) adb disconnect "$ADB_T" >/dev/null 2>&1 ;;
    esac
    [ "$i" -lt "$1" ] && [ $((SECONDS - t0)) -lt "$1" ] || return 1
    # the address may have changed (DHCP): the projector's mDNS name carries its serial (only that projector,
    # also with --ip: p6_identity checks the serial again)
    if [ -n "$SERIAL" ] && [ $((i % 30)) = 25 ]; then
      a=$(p6_discover "$SERIAL")
      if [ -n "$a" ] && [ "$a" != "$ADB_T" ]; then
        say "$(m projector_answers_new_address "$a")"
        ADB_T=$a
      fi
    fi
    sleep 5; i=$((i + 5))
  done
}

p6_alive() {  # 0 = the projector answers on a NEW connection (an old one may still say 'device' after a restart)
  adb disconnect "$ADB_T" >/dev/null 2>&1
  adb connect "$ADB_T" >/dev/null 2>&1
  [ "$(adb -s "$ADB_T" get-state 2>/dev/null | tr -d '\r')" = device ]
}
P6_NOMARK=lumen-install-no-check-file
p6_marker() {  # the wipe's check file: 'there' (no wipe since it was written), 'gone' (erased), '' (could not read)
  # Every attempt writes the file before it saves the token and before it asks for the reset, so a file that is
  # there, whatever token it holds, means that no wipe happened after the last request.
  case "$(adbs shell "cat $P6_MARKER 2>/dev/null || echo $P6_NOMARK" 2>/dev/null | tr -d '\r')" in
    "$P6_NOMARK") echo gone ;;
    lumen-*) echo there ;;
  esac
  return 0
}

p6_identity() {  # the device at ADB_T: this projector model, and the saved one (DHCP may give its address away)
  local code board ser
  code=$(prop ro.boot.xgimi.modelname); board=$(prop ro.boot.hardware); ser=$(prop ro.serialno)
  if [ "$code" != "$P_MODEL" ] || [ "$board" != "$P_BOARD" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    if [ "$(profile_flow "$(profile_of "$code" "$board")")" = z9x-usb ]; then
      die "$(m device_xgimi_z9x_installs_over "$ADB_T")"
    fi
    die "$(m projector_not_supported_model "$code" "$board" "$(profiles_named)")"
  fi
  [ -n "$ser" ] || die "$(m projector_reports_no_serial_number "$ADB_T")"
  if [ -n "$SERIAL" ] && [ "$SERIAL" != "$ser" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    if [ "$SERIAL_FROM_STATE" = 1 ]; then
      die "$(m projector_has_serial_but_install "$ADB_T" "$ser" "$SERIAL")"
    fi
    die "$(m projector_has_serial_not_serial "$ADB_T" "$ser" "$SERIAL")"
  fi
  SERIAL=$ser
}

p6_usb_hint() {  # no address for a Play 6: a Z9X on the USB cable means the wrong download or the wrong option
  local l code board
  l=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" && $1 !~ /:/ {print $1}')
  [ "$(printf '%s\n' "$l" | grep -c .)" = 1 ] || return 0
  code=$(adb -s "$l" shell getprop ro.boot.xgimi.modelname 2>/dev/null | tr -d '\r')
  board=$(adb -s "$l" shell getprop ro.boot.hardware 2>/dev/null | tr -d '\r')
  if [ "$(profile_flow "$(profile_of "$code" "$board")")" = z9x-usb ]; then
    if [ "$(profile_flow "$REL_DEVICE")" = play6-wifi ]; then
      die "$(m projector_usb_cable_xgimi_z9x "$P_NAME")"
    fi
    die "$(m projector_usb_cable_xgimi_z9x_2)"
  fi
}

play6_adb() {  # seconds: adb over Wi-Fi to the Play 6 (--ip, the saved address, or mDNS) and who it is; 1 = no answer
  local a
  if [ -z "$ADB_T" ]; then
    a=$(state_get ip)
    if [ -z "$a" ]; then
      a=$(p6_discover)
      if [ -z "$a" ] || ! ask "$(m projector_answers_yours_one "$a")"; then
        p6_usb_hint
        die "$(m give_projectors_wi_fi_address "$P_NAME")"
      fi
    fi
    ADB_T=$a
  fi
  say "$(m connecting_over_wi_fi "$ADB_T")"
  wifi_connect "$1" || return 1
  p6_identity
}

p6_fw_list() {  # "V<first> and V<second>" from VENDOR_OK
  printf '%s\n' "$P_VENDOR_OK" | awk -v conj="$(m and)" '{ for (i = 1; i <= NF; i++) s = s (i > 1 ? (i == NF ? " " conj " " : ", ") : "") "V" substr($i, 2); print s }'
}
p6_fw_first() { printf '%s\n' "$P_VENDOR_OK" | awk '{ print "V" substr($1, 2) }'; }
p6_fw_last() { printf '%s\n' "$P_VENDOR_OK" | awk '{ print "V" substr($NF, 2) }'; }   # the newest (VENDOR_OK is oldest first)
p6_restore_md5() { printf '%s\n' $P_RESTORE_MD5 | awk -F: -v v="$1" '$2 == v { print $1; exit }'; }   # VERSION: md5 of its .bin
p6_restore_url() { printf '%s\n' $P_RESTORE_URL | awk -v v="$1:" 'index($0, v) == 1 { print substr($0, length(v) + 1); exit }'; }
p6_fw_back() {  # CHIP PREFIX: the stock .bin that brings this unit back: the newest firmware for the newer batch (serial
  # XHF or the AIC chip: the first one has no driver for its chip), else the firmware it ran (saved by play6_device)
  local v
  case "$2:$1" in XHF:*|*:368b:*) p6_fw_last; return 0 ;; esac
  v=$(state_get vendor)
  case " $P_VENDOR_OK " in *" $v "*) printf 'V%s\n' "${v#v}" ;; *) p6_fw_first ;; esac
}
p6_vendor_aic() {  # FIRMWARE: 0 = the running vendor drives the AIC chip itself (its dongle.cfg maps 368b:8d90: XGIMI
  # the last firmware of VENDOR_OK). Unreadable file: only the first firmware of VENDOR_OK counts as one without it
  local n
  n=$(adbs shell "$P6_AIC_CMD" 2>/dev/null | tr -d '\r' | tail -n 1)
  case "$n" in
    ''|*[!0-9]*) [ "V${1#v}" != "$(p6_fw_first)" ] ;;
    0) return 1 ;;
    *) return 0 ;;
  esac
}

play6_device() {  # connected: firmware, single slot, stock or Lumen OS; saves serial, address, firmware
  local vend slot lum ver up all first
  vend=$(prop ro.vendor.build.version.incremental); slot=$(prop ro.boot.slot_suffix); lum=$(prop "$(p6_lprop)")
  case " $P_VENDOR_OK " in
    *" $vend "*) ;;
    *) all=$(p6_fw_list); first=$(p6_fw_first)
       die "$(m xgimi_firmware_not_supported_lumen "$vend" "$P_NAME" "$all" "$first")" ;;
  esac
  [ -z "$slot" ] || die "$(m projector_has_second_system_slot "$slot" "$P_NAME")"
  case "$lum" in
    '') state_set dev_lumen 0; state_set dev_version ""; ver=$(m xgimi_stock)
        # a downloaded XGIMI update arms the next restart; the installer's own restart into fastbootd does not
        # run it (adb reboot fastboot writes its own command, fastbootd clears it), a restart by hand would
        up=$(prop xgimi.ota.update.onboot)
        [ -n "$up" ] || up=$(adbs shell settings get system xgimi_ota_update 2>/dev/null | tr -d '\r')
        case "$up" in 1|2) warn "$(m downloaded_xgimi_update_waits_next)" ;; esac ;;
    "$(p6_lval)") ver=$(prop "$P_VERSION_PROP"); state_set dev_lumen 1; state_set dev_version "$ver"; ver="Lumen OS $ver" ;;
    *) die "$(m projector_runs_lumen_os_another "$lum")" ;;
  esac
  state_set serial "$SERIAL"; state_set ip "$ADB_T"; state_set vendor "$vend"
  ok "$P_NAME $SERIAL ($ADB_T), $(m firmware) $vend, $(m system): $ver"
}

# ------------------------------------------------------------------ XGIMI Play 6: pre-checks, log, erase request, help
# The install log ($STATE_DIR/play6/install.log) keeps what the pre-checks saw and every step of the light first start,
# so that a user whose projector does not come back can send it with the boot log of the diagnostic build.
p6_logpath() { printf '%s' "$SD/install.log"; }
p6_log() { mkdir -p "$SD" 2>/dev/null; printf '%s %s\n' "$(date +%Y%m%d-%H%M%S)" "$*" >> "$SD/install.log" 2>/dev/null; return 0; }
p6_chip_name() {  # vid:pid -> the Wi-Fi and Bluetooth chip it is (memory: MediaTek on the first batch, AIC on newer ones)
  case "$1" in
    0e8d:7663) echo "MediaTek MT7663 ($1)" ;;
    0e8d:7961) echo "MediaTek MT7961 ($1)" ;;
    0e8d:*) echo "MediaTek ($1)" ;;
    368b:8d90|368b:8d91) echo "AIC8800 ($1)" ;;
  esac
}
p6_usb_scan() { P6_USB=$(adbs shell "$P6_USB_CMD" 2>/dev/null | tr -d '\r'); p6_usb_parse "$P6_USB"; }
p6_usb_parse() {  # TEXT ("<name> <vid>:<pid> <class>" lines): P6_CHIP/P6_CHIPID, P6_STICK (0/1), P6_OTHER (other ids)
  local name id cls n
  P6_CHIP=""; P6_CHIPID=""; P6_STICK=0; P6_OTHER=""
  while read -r name id cls; do
    [ -n "$id" ] || continue
    case "$id" in 1d6b:*) continue ;; esac   # root hubs
    n=$(p6_chip_name "$id")
    if [ -n "$n" ]; then P6_CHIP=$n; P6_CHIPID=$id; continue; fi
    if [ "$cls" = 08 ]; then P6_STICK=1; else P6_OTHER="$P6_OTHER $id"; fi   # 08: mass storage
  done <<EOF
$1
EOF
  P6_OTHER=${P6_OTHER# }
}
p6_power() { p6_power_parse "$(adbs shell "$P6_PWR_CMD" 2>/dev/null | tr -d '\r')"; }
p6_power_parse() {  # TEXT (XGIMI's power log): P6_LVL (percent) and P6_AD (1 adapter, 0 battery), empty when it does not tell
  local l=$1
  P6_LVL=$(printf '%s\n' "$l" | sed -n -e 's/.*POWER_BATTERY_LEVEL[^0-9]*\([0-9][0-9]*\).*/\1/p' -e 's/.*P6Battery: read level=\([0-9][0-9]*\).*/\1/p' | tail -n 1)
  P6_AD=$(printf '%s\n' "$l" | awk '/IsAdapterPowered:1|AdapterInsert|adapter=true/ { a = 1 } /IsAdapterPowered:0|AdapterOut|adapter=false/ { a = 0 } END { if (a != "") print a }')
}
p6_prechecks() {  # before any write, on the running system over adb: which unit, its power and its USB port (logged)
  local pre model fw disp mnt fs a
  [ "$P6_PRE_DONE" = 1 ] && return 0
  pre=$(printf '%s' "$SERIAL" | cut -c1-3)
  model=$(prop ro.boot.xgimi.modelname); fw=$(prop ro.vendor.build.version.incremental); disp=$(prop ro.build.display.id)
  p6_usb_scan; p6_power
  mnt=$(adbs shell "$P6_MNT_CMD" 2>/dev/null | tr -d '\r'); fs=$(printf '%s\n' "$mnt" | awk 'NF >= 3 { print $3; exit }')
  state_set chip "${P6_CHIPID:-unknown}"; state_set serial_prefix "$pre"
  p6_log "pre-check: serial $SERIAL ($pre), model $model, firmware $fw${disp:+ ($disp)}, chip ${P6_CHIP:-unknown}, battery ${P6_LVL:-?}, adapter ${P6_AD:-?}, usb [$(printf '%s' "$P6_USB" | tr '\n' ';')], stick ${fs:--}"
  ok "$(m unit): $(m serial) $SERIAL ($pre), $(m model) $model, $(m firmware) $fw${disp:+ ($disp)}"
  if [ -n "$P6_CHIP" ]; then ok "$(m wi_fi_bluetooth_chip): $P6_CHIP"
  else warn "$(m wi_fi_bluetooth_chip_not) ($(printf '%s\n' "$P6_USB" | awk 'NF >= 2 { printf "%s%s", s, $2; s = " " }'))"; fi
  # the AIC chip runs only on XGIMI's own AIC driver (the last firmware of VENDOR_OK): Lumen OS brings none, so the first would leave it
  # without Wi-Fi and Bluetooth. Refused before anything is written, also when an older Lumen OS runs on it
  if [ "${P6_CHIPID%%:*}" = 368b ] && ! p6_vendor_aic "$fw"; then
    p6_log "pre-check: refused, AIC chip $P6_CHIPID on firmware $fw without XGIMI's AIC driver"
    die "$(m aic_chip_firmware_no_driver "$P_NAME" "$P6_CHIP" "$fw" "$(p6_fw_last)" "$(p6_restore_url "$(p6_fw_last)")" "$(p6_restore_md5 "$(p6_fw_last)")")"
  fi
  # power: an erase or a write cut off by an empty battery leaves the projector without a system
  if [ -n "$P6_LVL" ] && [ "$P6_LVL" -lt "$P6_MIN_BATTERY" ]; then
    p6_log "pre-check: refused, battery $P6_LVL"
    die "$(m battery_charge_least_power_adapter "$P6_LVL" "$P6_MIN_BATTERY")"
  fi
  if [ "$P6_AD" = 0 ]; then
    p6_log "pre-check: refused, on battery"
    die "$(m projector_runs_battery_connect)"
  fi
  if [ -n "$P6_LVL" ] && [ "$P6_AD" = 1 ]; then
    ok "$(m battery_power_adapter_connected "$P6_LVL")"
  else
    say "$(m installer_could_not_read_battery)${P6_LVL:+ ($P6_LVL %)}"
    ask "$(m power_adapter_connected_projector "$P6_MIN_BATTERY")" \
      || die "$(m connect_power_adapter_charge "$P6_MIN_BATTERY")"
    p6_log "pre-check: power confirmed by the user"
  fi
  # the newer batch (memory play6-wifi-chip-variants): the first-batch stock .bin kills its Wi-Fi and Bluetooth
  if [ "$pre" = XHF ] || [ "${P6_CHIPID%%:*}" = 368b ]; then
    echo
    m newer_batch_serial_xhf_aic "$P_NAME" "$(p6_fw_first)" "$(p6_fw_last)" "$(p6_restore_url "$(p6_fw_last)")" "$(p6_restore_md5 "$(p6_fw_last)")"; echo
    ask "$(m install_lumen_os_projector)" || die "$(m cancelled_nothing_written)"
    p6_log "pre-check: newer batch confirmed by the user"
  fi
  # what is in the USB port at power-on matters: a FAT32 stick is good, a USB Ethernet adapter or an A-to-A cable is not
  if [ -n "$P6_OTHER" ]; then
    warn "$(m usb_device_not_usb_stick "$P6_OTHER" "$P_NAME")"
    if ask "$(m device_projectors_only_connection)"; then
      say "$(m keep_now_when_installer_asks)"
      p6_log "pre-check: other USB device kept as the network: $P6_OTHER"
    else
      printf '%s ' "$(m take_out_projectors_usb_port)"
      read -r a
      p6_usb_scan
      [ -z "$P6_OTHER" ] || die "$(m usb_device_still_projectors_usb "$P6_OTHER")"
      ok "$(m no_other_device_usb_port)"
      p6_log "pre-check: other USB device removed"
    fi
  fi
  if [ "$P6_STICK" = 1 ] && [ -n "$fs" ] && [ "$fs" != vfat ]; then
    warn "$(m usb_stick_projector_not_fat32 "$fs" "$P_NAME")"
  fi
  say "$(m every_power_during_install_keep)"
  P6_PRE_DONE=1
}

p6_noboot_help() {  # the projector did not come back after a start: what to do, and the way back for its batch
  local chip pre
  chip=$(state_get chip); pre=$(state_get serial_prefix)
  p6_log "no start reported (serial $pre, chip ${chip:-?})"
  echo
  m if_projector_not_come_back "$(p6_logpath)" "$P_NAME"; echo
  case "$pre:$chip" in
    XHF:*|*:368b:*) m projector_serial_chip_newer_batch "$pre" "${chip:-?}" "$(p6_fw_first)" "$(p6_fw_last)" "$(p6_restore_url "$(p6_fw_last)")" "$(p6_restore_md5 "$(p6_fw_last)")" ;;
    *:0e8d:*) m projector_serial_mediatek_chip_bin "$pre" "$chip" "$(p6_fw_back "$chip" "$pre")" ;;
    *) m serial_xhn_mediatek_wi_fi "$(p6_fw_back "" "$pre")" "$(p6_fw_first)" "$(p6_fw_last)" ;;
  esac
  echo
}

p6_marker_write() {  # the check file for 'verify' (a real wipe removes /data/local/tmp); 1 = not written
  local tok got
  tok="lumen-$(date +%Y%m%d%H%M%S)-$$"
  adbs shell "echo $tok > $P6_MARKER" >/dev/null 2>&1
  got=$(adbs shell cat "$P6_MARKER" 2>/dev/null | tr -d '\r')
  [ "$got" = "$tok" ] || return 1
  state_set wipe_marker "$tok"
}
p6_bcb_make() {  # FILE: the erase request, 2048 bytes (see P6_BCB_ARGS)
  dd if=/dev/zero of="$1" bs=2048 count=1 2>/dev/null
  printf 'boot-recovery' | dd of="$1" conv=notrunc 2>/dev/null
  printf "$P6_BCB_ARGS" | dd of="$1" bs=1 seek=64 conv=notrunc 2>/dev/null
}
p6_bcb_write() {  # fastbootd, system written: the erase request into misc; 1 = not written (then the earlier way)
  local f rc
  f=$SD/bcb.bin
  mkdir -p "$SD"; p6_bcb_make "$f"
  [ "$(wc -c < "$f" | tr -d ' ')" = 2048 ] && [ "$(sha256 "$f")" = "$P6_BCB_SHA" ] || { rm -f "$f"; p6_fb_stop "internal: the erase request is not the known one."; }
  say "$(m writing_erase_request_xgimis)"
  P6_BCB_OK=1; fb_do flash misc "$f"; rc=$?; P6_BCB_OK=0
  rm -f "$f"
  if [ "$rc" = 0 ]; then state_set step_bcb done; p6_log "erase request written"; return 0; fi
  state_set step_bcb failed; p6_log "erase request refused by fastbootd"
  warn "$(m fastboot_mode_did_not_take)"
  return 1
}

play6_boot_fresh() {  # the start after the write: XGIMI's recovery erases the data, then the first, light start of Lumen OS
  local a
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  echo
  m projector_now_starts_xgimis; echo
  p6_log "light first start: waiting for the user"
  if ! ask "$(m wait_lumen_os_projector_first)"; then
    p6_log "light first start: not seen"
    p6_noboot_help
    die "$(m when_lumen_os_shows_run)"
  fi
  # erased: the projector is off Wi-Fi now. Still answering with the old data: the recovery did not take the request.
  say "$(m checking_old_data_gone_up)"
  if [ -n "$ADB_T" ] && wifi_connect 60; then
    p6_identity
    case "$(p6_marker)" in
      there)
        p6_log "light first start: the old data is still there"
        warn "$(m projector_still_has_old_data)"
        # from here on the earlier way: its messages ("Nothing was erased.") and a resumed run go straight to boot1
        state_set step_bcb ignored
        play6_boot1
        return 0 ;;
      gone) ok "$(m data_erased_check_file_written)"
            state_set step_wipe done ;;
      *) warn "$(m check_file_could_not_read)"
         state_set step_wipe requested ;;
    esac
  else
    ok "$(m projector_off_wi_fi_data)"
    state_set step_wipe requested
    WIPED_NOW=1
  fi
  p6_log "light first start: done (step_wipe=$(state_get step_wipe))"
  state_set step_boot1 done
}
p6_gapps_fresh_fill() {  # one pass after the light first start: debugging on again, the Google apps in, then one more reset
  local a
  if [ -z "$GAPPS_WITH" ]; then p6_gapps_fill_step; return 0; fi   # asks whether to finish without Google
  echo
  m lumen_os_runs_clean_data "$P_NAME"; echo
  printf '%s ' "$(m press_enter_when_done)"
  read -r a
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  wifi_connect 600 || die "$(p6_noconn_text 600) $(m run_same_command_again_continues)"
  # the fill is followed by Android's factory reset (play6_install): the first start after it sets Google up.
  # The check file of the erase before the first start must not count for that reset.
  state_set step_wipe ""; state_set wipe_marker ""; ERASE_NOW=1
  p6_gapps_fill_step
}

p6_bound_text() {  # version from the name (empty: an own build): what binds the image without reading its content
  if [ -n "$1" ]; then
    m signed_sha256sums_lists_file_lumen "$1"
  else
    m own_build_unsigned_image
  fi
}
p6_image_props() {  # the image's properties in the order init reads them (a later file wins: product is read last)
  local p
  for p in /system/etc/prop.default /system/build.prop /system/system_ext/etc/build.prop /system/product/etc/build.prop; do
    debugfs -R "cat $p" "$IMG" 2>/dev/null
  done
  return 0
}

play6_check_image() {  # the release image of this projector: name, file system, size, signature, checksum, content
  local name dir sums sig cert pub want got a f sz bp v sec nv=""
  find_image
  name=$(basename "$IMG"); dir=$(dirname "$IMG"); sums="$dir/SHA256SUMS"; sig="$sums.sig"; cert="$HERE/$P_CERT"
  case "$name" in *"$P_TOKEN"*) ;; *) die "$(m not_image_name_must_contain "$name" "$P_NAME" "$P_TOKEN")" ;; esac
  case "$name" in *PRIVATE*) die "$(m private_image_never_installed "$name" "$P_NAME")" ;; esac
  if [ "$UNSIGNED" = 0 ]; then
    case "$name" in *-nogms-*) ;; *) die "$(m not_release_file_has_lumen "$name" "$P_NAME")" ;; esac
    # the release version is in the name, and the signed SHA256SUMS lists the image under that name: a file of
    # another release (signed with the same key) next to this installer stops here, before anything is written
    [ -n "${VERSION:-}" ] || die "internal: release.conf has no VERSION: re-download the installer of the $P_NAME release"
    case "$name" in "lumen-os-$P_TOKEN-"*"-nogms-system.img") nv=${name#"lumen-os-$P_TOKEN-"}; nv=${nv%-nogms-system.img} ;; esac
    [ -n "$nv" ] || die "$(m not_named_like_release_file "$name" "$P_TOKEN")"
    [ "$nv" = "$VERSION" ] || die "$(m lumen_os_but_installer_download "$name" "$nv" "$VERSION" "$P_NAME")"
  fi
  # the file system, read without any tool: raw ext4 has 53 ef at byte 1080
  if [ "$P_FS" = ext4 ] && [ "$(od -An -tx1 -j1080 -N2 "$IMG" 2>/dev/null | tr -d ' \n')" != 53ef ]; then
    if [ "$(od -An -tx1 -j1024 -N4 "$IMG" 2>/dev/null | tr -d ' \n')" = e2e1f5e0 ]; then
      die "$(m xgimi_z9x_image_erofs_not "$name" "$P_NAME")"
    elif [ "$(od -An -tx1 -N4 "$IMG" 2>/dev/null | tr -d ' \n')" = 3aff26ed ]; then
      die "$(m sparse_image_not_release_file "$name")"
    fi
    die "$(m not_ext4_system_image_nothing "$name")"
  fi
  sz=$(wc -c < "$IMG" | tr -d ' ')
  [ $((sz % 4096)) = 0 ] || die "$(m has_size_not_multiple_4096 "$name")"
  if [ -f "$sums" ] && [ -f "$sig" ] && [ -f "$cert" ] && command -v openssl >/dev/null 2>&1; then
    pub=$(mktemp)
    openssl x509 -in "$cert" -pubkey -noout > "$pub"
    openssl dgst -sha256 -verify "$pub" -signature "$sig" "$sums" >/dev/null 2>&1 \
      || { rm -f "$pub"; die "$(m sha256sums_not_signed_lumen_os_2 "$P_NAME")"; }
    rm -f "$pub"
    ok "$(m sha256sums_signature_lumen_os_key "$P_NAME")"
  elif [ "$UNSIGNED" = 1 ]; then
    warn "$(m unsigned_image_image_not)"
    if [ "$(state_get unsigned_ok)" != "$name:$(filekey "$IMG")" ]; then
      printf '%s ' "$(m type_unsigned_install_anyway "$name")"
      read -r a
      [ "$a" = UNSIGNED ] || die "$(m cancelled_nothing_written)"
      state_set unsigned_ok "$name:$(filekey "$IMG")"
    fi
    if [ ! -f "$sums" ]; then   # a list that names the image, or the image's own .sha256 (tools/play6/build_test.sh)
      sums=""
      for f in "$dir"/SHA256SUMS* "$IMG.sha256"; do
        case "$f" in *.sig) continue ;; esac
        [ -f "$f" ] && awk -v n="$name" '$2==n || $2=="*"n {f=1} END{exit !f}' "$f" && { sums=$f; break; }
      done
    fi
  else
    [ -f "$sums" ] || die "$(m no_sha256sums_next_image_cannot)"
    [ -f "$sig" ] || die "$(m no_sha256sums_sig_next_image)"
    [ -f "$cert" ] || die "$(m installer_certificate_missing_re "$P_CERT" "$P_NAME")"
    die "$(m openssl_needed_check_image)"
  fi
  if [ -n "$sums" ] && [ -f "$sums" ]; then
    want=$(awk -v f="$name" '$2==f || $2=="*"f {print $1}' "$sums")
    [ -n "$want" ] || die "$(m not_listed "$name" "$(basename "$sums")")"
    if [ "$FORCE_HASH" = 1 ] || [ "$(state_get image_ok)" != "$want:$(filekey "$IMG")" ]; then
      say "$(m checking_image_checksum_about)"
      got=$(sha256 "$IMG")
      [ "$got" = "$want" ] || { state_set image_ok none; die "$(m checksum_mismatch_image_damaged)"; }
      state_set image_ok "$want:$(filekey "$IMG")"
    fi
    ok "$name sha256 ${want:0:16}…"
  else
    warn "$(m unsigned_image_without_any)"
  fi
  # what the image says about itself, when debugfs (e2fsprogs) is installed. Without it the name (its version,
  # checked above against release.conf) and the signed SHA256SUMS (which lists the file under that name) bind the
  # image to this release; this also catches a release built wrong.
  if command -v debugfs >/dev/null 2>&1; then
    bp=$(p6_image_props)
    if [ -n "$bp" ]; then
      v=$(printf '%s\n' "$bp" | sed -n "s/^$(p6_lprop)=//p" | tail -1)
      [ "$v" = "$(p6_lval)" ] || die "$(m not_lumen_os_nothing_written "$name" "$P_NAME" "$(p6_lprop)" "$v")"
      v=$(printf '%s\n' "$bp" | sed -n "s/^$P_VERSION_PROP=//p" | tail -1)
      sec=$(printf '%s\n' "$bp" | sed -n 's/^ro\.adb\.secure=//p' | tail -1)
      if [ "$UNSIGNED" = 0 ]; then
        [ "$v" = "${VERSION:-}" ] || die "$(m holds_lumen_os_release_nothing "$name" "$v" "${VERSION:-?}")"
        [ "$sec" = 1 ] || die "$(m test_build_ro_adb_secure "$name" "$sec")"
      fi
      ok "$(m image_lumen_os "$v" "$P_NAME")"
    else
      warn "$(m debugfs_could_not_read_image "$(p6_bound_text "$nv")")"
    fi
  else
    say "$(m image_content_not_read_debugfs "$(p6_bound_text "$nv")")"
  fi
  state_set image_gms 0
  ok "$(m edition_lumen_os_without_google)"
}

play6_check() {
  local free
  check_tools
  play6_adb 60 || die "$(p6_noconn_text)"
  play6_device
  p6_prechecks
  free=$(df -k "$HOME" | awk 'NR==2{print $4}')
  [ "${free:-0}" -ge 4194304 ] || warn "$(m less_4_gb_free_computer)"
  play6_check_image
  ok "$(m check_passed_nothing_written)"
  state_set step_check done
}

p6_in_fastboot() { [ -n "$SERIAL" ] && fastboot devices 2>/dev/null | awk '{print $1}' | grep -qx "$SERIAL"; }
p6_fb_wait() {  # seconds: the projector (SERIAL) in fastboot mode on the cable
  local i=0
  while [ "$i" -lt "$1" ]; do
    p6_in_fastboot && return 0
    [ "$i" -gt 0 ] && [ $((i % 60)) = 0 ] && say "$(m still_waiting_fastboot_mode)"
    sleep 2; i=$((i + 2))
  done
  return 1
}
p6_fb_stop() {  # message: leave fastbootd for the old system, nothing was written
  fb reboot >/dev/null 2>&1
  die "$1 $(m nothing_written_unplug_cable)"
}
play6_fbd_check() {  # fastbootd: this Play 6 (serial), its firmware and the known layout of super; else STOP
  local why="" l want wantg v fw=0
  GV=$(fb getvar all 2>&1)
  [ "$(gv_val is-userspace)" = yes ] || why=$(m not_fastbootd)
  [ -n "$why" ] || [ "$(gv_val product)" = "$P_FB_PRODUCT" ] || why="$(m fastbootd_not_one "$P_NAME") ($(gv_val product))"
  [ -n "$why" ] || [ "$(gv_val unlocked)" = yes ] || why=$(m bootloader_locked)
  [ -n "$why" ] || [ "$(gv_val serialno)" = "$SERIAL" ] || why="$(m another_projector_fastboot_mode) (serialno $(gv_val serialno), $(m expected) $SERIAL)"
  if [ -z "$why" ]; then case "$(gv_val slot-count)" in ''|0) ;; *) why="slot-count $(gv_val slot-count)" ;; esac; fi
  [ -n "$why" ] || [ -z "$(gv_val current-slot)" ] || why="current-slot $(gv_val current-slot)"
  [ -n "$why" ] || [ "$(gv_val super-partition-name)" = super ] || why="super-partition-name '$(gv_val super-partition-name)'"
  l=$(printf '%s\n' "$GV" | sed -n 's/^(bootloader) is-logical:\([^:]*\):yes.*/\1/p' | LC_ALL=C sort -u | tr '\n' ' ' | sed 's/ $//')
  want=$(printf '%s\n' $P_LOGICAL | LC_ALL=C sort -u | tr '\n' ' ' | sed 's/ $//')
  # the Google services add-on ('gapps') adds one partition of its own
  wantg=$(printf '%s\n' $P_LOGICAL "$P6_GAPPS" | LC_ALL=C sort -u | tr '\n' ' ' | sed 's/ $//')
  [ -n "$why" ] || [ "$l" = "$want" ] || [ "$l" = "$wantg" ] || why="$(m partitions_super_not_known_ones) ($l)"
  if [ -z "$why" ]; then
    for v in $P_VENDOR_OK; do case "$(gv_val vendor-fingerprint)" in */"${v#v}":*) fw=1 ;; esac; done
    [ "$fw" = 1 ] || why="$(m xgimi_firmware_not_supported) ($(gv_val vendor-fingerprint))"
  fi
  [ -z "$why" ] || p6_fb_stop "$(m fastboot_mode_check_failed): $why."
  ok "$(m fastboot_mode "$P_NAME" "$SERIAL")"
}
p6_room() {  # [bytes]: the image must fit into super next to the other logical partitions (each rounded up to 1 MiB);
  # with --gapps-zip the image and lumen_gapps at that size together (an existing lumen_gapps is resized to it)
  local sup p sz used=0 room need
  sup=$(gv_size super)
  [ -n "$sup" ] || p6_fb_stop "$(m fastbootd_did_not_report_size)"
  for p in $P_LOGICAL; do
    [ "$p" = system ] && continue
    sz=$(gv_size "$p")
    [ -n "$sz" ] || p6_fb_stop "$(m fastbootd_did_not_report_size_2 "$p")"
    used=$(( used + (sz + 1048575) / 1048576 * 1048576 ))
  done
  sz=$(gv_size "$P6_GAPPS")   # the Google services add-on ('gapps') keeps its space
  [ -n "${1-}" ] || [ -z "$sz" ] || used=$(( used + (sz + 1048575) / 1048576 * 1048576 ))
  room=$(( sup - used - P_RESERVE ))
  sz=$(wc -c < "$IMG" | tr -d ' ')
  if [ -n "${1-}" ]; then
    need=$(( (sz + 1048575) / 1048576 * 1048576 + ($1 + 1048575) / 1048576 * 1048576 ))
    [ "$need" -le "$room" ] || p6_fb_stop "$(m image_google_services_not_fit "$((room / 1048576))" "$((need / 1048576))")"
    return 0
  fi
  [ "$sz" -le "$room" ] || p6_fb_stop "$(m image_not_fit_super_mb "$((room / 1048576))" "$(( (sz + 1048575) / 1048576 ))")"
}
p6_cable_out() {  # after 'fastboot reboot': the projector does not start while the computer's cable is in
  local a
  echo
  m written_unplug_cable_projector_now "$P_NAME"; echo
  printf '%s ' "$(m press_enter_when_cable_out)"
  read -r a
}

play6_write() {  # check, confirmation, cable, fastbootd, write system, restart (resumes in fastbootd)
  local a infb=0 k
  check_tools
  if [ -n "$(state_get step_fastboot)" ] && [ "$(state_get step_flash)" != done ] && p6_in_fastboot; then
    infb=1
    if [ "$(state_get step_flash_system)" = done ]; then   # fastboot said OKAY, the restart was not sent
      [ -z "$GAPPS_WITH" ] || state_set gapps_plan "$P6G_SHA"
      if [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_part)" != done ]; then play6_fbd_check; p6_gapps_add; fi
      if [ "$KEEP_DATA" = 0 ] && [ "$(state_get wipe_plan)" = bcb ] && [ -z "$(state_get step_bcb)" ]; then play6_fbd_check; p6_bcb_write; fi
      ok "$(m system_already_written_restarting)"
      fb reboot >/dev/null 2>&1
      state_set step_flash done
      p6_cable_out
      return 0
    fi
    say "$(m projector_waits_fastboot_mode)"
  else
    if ! play6_adb 60; then
      # an earlier run sent the projector to fastboot mode: it is neither there (on the cable) nor back in its system
      [ -z "$(state_get step_fastboot)" ] || die "$(m projector_neither_fastboot_mode "$ADB_T" "$P_NAME")"
      die "$(p6_noconn_text)"
    fi
    play6_device
    p6_prechecks
  fi
  FORCE_HASH=1 play6_check_image
  echo
  if [ "$KEEP_DATA" = 1 ]; then
    m writing_lumen_os_system_partition "$P_NAME"; echo
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
    state_set wipe_plan keep
  else
    m writing_lumen_os_system_partition_2 "$P_NAME"; echo
    if [ -n "$GAPPS_WITH" ]; then
      m gapps_zip_same_fastboot_session "$P6_GAPPS" "$((P6G_PART / 1048576))" "$P6G_NAME"; echo
    fi
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
    ERASE_NOW=1
    state_set erase_ok "$(date +%Y%m%d-%H%M%S)"
    state_set wipe_plan bcb
  fi
  # --gapps-zip: the Google services of this install (p6_gapps_fill_step adds them after the first start). A new
  # write takes this run's choice; a write resumed in fastboot mode keeps the saved one unless --gapps-zip is given.
  if [ "$infb" = 0 ] || [ -n "$GAPPS_WITH" ] || [ "$KEEP_DATA" = 1 ]; then state_set gapps_plan "$P6G_SHA"; fi
  if [ "$infb" = 0 ]; then
    for k in step_fastboot step_flash_system step_flash step_boot1 step_wipe step_verify wipe_marker step_gapps_part step_gapps_fill step_bcb; do state_set "$k" ""; done
    # the proof for 'verify' and for the light first start: /data/local/tmp does not survive the erase
    if [ "$KEEP_DATA" = 0 ]; then
      p6_marker_write || die "$(m could_not_write_check_file "$P6_MARKER")"
    fi
    echo
    m take_usb_stick_whatever_out; echo
    printf '%s ' "$(m press_enter_when_cable)"
    read -r a
    say "$(m restarting_fastboot_mode_about_30)"
    # saved first: a restart that happens although adb reports an error (the Wi-Fi connection drops with it) is
    # still continued in fastboot mode by the next run
    state_set step_fastboot "$(date +%Y%m%d-%H%M%S)"
    if ! adbs reboot fastboot >/dev/null 2>&1 && p6_alive; then
      state_set step_fastboot ""
      die "$(m adb_reboot_fastboot_failed)"
    fi
    adb disconnect "$ADB_T" >/dev/null 2>&1
  fi
  p6_fb_wait 180 || die "$(m projector_did_not_appear_cable)"
  play6_fbd_check
  p6_room $(p6_plan_part)
  say "$(m writing_system_about_1_minute)"
  fb_do flash system "$IMG" || die "$(m system_not_written_completely)"
  state_set step_flash_system done
  [ -z "$(state_get gapps_plan)" ] || p6_gapps_add
  if [ "$KEEP_DATA" = 0 ] && [ "$(state_get wipe_plan)" = bcb ]; then p6_bcb_write; fi
  fb reboot >/dev/null 2>&1
  state_set step_flash done
  p6_cable_out
}

p6_write_again() {  # the write did not take effect: the next run (or 'flash') writes system again from the start
  local k
  for k in step_fastboot step_flash_system step_flash step_bcb; do state_set "$k" ""; done
}
play6_boot1() {  # the first start of Lumen OS on the old data: Wi-Fi and debugging carry over from stock
  local i=0 t0 v lum
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  [ -n "$ADB_T" ] || die "internal: no saved address of the projector"
  say "$(m waiting_lumen_os_over_wi)"
  if ! wifi_connect 600; then
    case "$P6_ST" in *unauthorized*)
      die "$(m lumen_os_runs_but_did)" ;;
    esac
    p6_noboot_help
    die "$(m lumen_os_did_not_answer "$P_NAME")"
  fi
  p6_identity
  t0=$SECONDS
  while [ "$(prop sys.boot_completed)" != 1 ]; do
    [ "$i" -lt 300 ] && [ $((SECONDS - t0)) -lt 300 ] || die "$(m lumen_os_did_not_finish)"
    sleep 5; i=$((i + 5))
  done
  lum=$(prop "$(p6_lprop)")
  if [ -z "$lum" ]; then
    p6_write_again
    die "$(m projector_still_runs_xgimis_system)"
  fi
  [ "$lum" = "$(p6_lval)" ] || die "$(m projector_runs_lumen_os_another_2 "$lum")"
  v=$(prop "$P_VERSION_PROP")
  if [ "$UNSIGNED" = 0 ] && [ -n "${VERSION:-}" ] && [ "$v" != "$VERSION" ]; then
    if [ "$(state_get dev_lumen)" = 1 ] && [ "$v" = "$(state_get dev_version)" ]; then
      p6_write_again
      die "$(m projector_still_runs_lumen_os "$v")"
    fi
    # the image was checked against this release before the write (name, signature, checksum): only its own
    # version name differs, which does not make the wipe of this projector any less right
    warn "$(m lumen_os_runs_installer_download "$v" "$VERSION")"
  fi
  ok "$(m lumen_os_started "$v") ($SERIAL, $ADB_T)"
  if [ -n "$(adbs shell pidof "$P6_APP" 2>/dev/null | tr -d '\r')" ]; then ok "$(m projector_app_running)"
  else warn "$(m projector_app_not_running_yet "$P6_APP")"; fi
  state_set ip "$ADB_T"
  state_set step_boot1 done
}

p6_root() {  # adb root restarts adbd, so connect again. 0 = root; 1 = this system gives no root (not userdebug);
  # 2 = no answer after 'adb root' (the allow prompt was not accepted, or the network): try again later;
  # 3 = Developer options > Rooted debugging is off (LineageOS adbd asks its adb_root service first and answers
  #     "ADB Root access is disabled by system setting"; Play 6 AIC test unit 2026-10-28)
  local out
  [ "$(prop ro.debuggable)" = 1 ] || return 1
  [ "$(adbs shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && return 0
  out=$(adbs root 2>&1)
  case "$out" in *"disabled by system setting"*) return 3 ;; esac
  sleep 3
  wifi_connect 60 || return 2
  [ "$(adbs shell id -u 2>/dev/null | tr -d '\r')" = 0 ] || return 1
}

play6_wipe() {  # Android's own factory reset on the first Lumen OS start; XGIMI's recovery carries it out
  local tok got out i=0 t0 a rc
  [ "$(state_get step_boot1)" = done ] || die "internal: no factory reset before Lumen OS has started"
  [ "$KEEP_DATA" = 0 ] || die "internal: no factory reset with --keep-data"
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  if ! wifi_connect 120; then
    # a factory reset asked for by an earlier run (cut off before it saved that) erases Wi-Fi and turns debugging off
    if [ -n "$(state_get wipe_marker)" ]; then
      case "$P6_ST" in *unauthorized*) ;; *)
        die "$(m projector_not_answer_over_wi "$ADB_T" "$P_NAME")" ;;
      esac
    fi
    die "$(p6_noconn_text 120)"
  fi
  p6_identity
  [ "$(prop "$(p6_lprop)")" = "$(p6_lval)" ] || die "$(m projector_not_run_lumen_os_2 "$P_NAME")"
  # an earlier run asked for the reset and was cut off before it saved that: the check file tells what happened
  if [ -n "$(state_get wipe_marker)" ] && [ "$(p6_marker)" = gone ]; then
    ok "$(m data_already_erased_check_file)"
    state_set step_wipe done
    return 0
  fi
  if [ "$ERASE_NOW" != 1 ]; then
    echo
    if [ "$(state_get step_bcb)" = done ] && [ "$(state_get step_gapps_fill)" = done ]; then
      m google_apps_copied_next_androids; echo
    else
      m lumen_os_runs_old_data; echo
    fi
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_erased)"
    ERASE_NOW=1
  fi
  # the proof for 'verify': /data/local/tmp does not survive a real wipe
  p6_marker_write || die "$(m could_not_write_check_file_2 "$P6_MARKER")"
  p6_root; rc=$?
  case "$rc" in
    0) ;;
    2) die "$(m after_adb_root_projector_did)" ;;
    *) [ "$rc" = 3 ] && say "$(m root_debugging_off_settings_device)"
       warn "$(m adb_root_not_possible_system_2)"
       say "$(m projector_settings_device)"
       state_set step_wipe manual
       return 0 ;;
  esac
  say "$(m asking_android_factory_reset)"
  # --user 0: MasterClearReceiver wipes only the sending user when that is not user 0
  out=$(adbs shell am broadcast --user 0 -a android.intent.action.FACTORY_RESET -p android --receiver-foreground \
        --es android.intent.extra.REASON lumen-install 2>&1 | tr -d '\r')
  case "$out" in
    *"Broadcast completed"*) ;;
    *) die "$(m factory_reset_not_accepted_data "$out")" ;;
  esac
  # the projector restarts into XGIMI's recovery: it stops answering. Asked on a new connection each time, since
  # the computer's old one can still say 'device' for a while after the projector went away.
  t0=$SECONDS
  while p6_alive; do
    [ "$i" -lt 60 ] && [ $((SECONDS - t0)) -lt 60 ] || die "$(m projector_still_answers_minute)"
    sleep 5; i=$((i + 5))
  done
  adb disconnect "$ADB_T" >/dev/null 2>&1
  state_set step_wipe requested
  WIPED_NOW=1
  echo
  m projector_restarts_xgimis_recovery; echo
  if ask "$(m wait_lumen_home_projector_up)"; then
    ok "$(m lumen_os_started_clean)"
  else
    warn "$(m press_power_button_once_if "$P_NAME")"
    p6_noboot_help
  fi
}

play6_verify() {  # [seconds]: over Wi-Fi, after the wipe (the user joined Wi-Fi and turned debugging on again)
  local v sec n s flags="" st
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  if [ -z "$ADB_T" ] && [ -n "$SERIAL" ]; then ADB_T=$(p6_discover "$SERIAL"); fi   # only this projector, by its serial
  P6_ST=""
  if [ -z "$ADB_T" ] || ! wifi_connect "${1:-60}"; then
    case "$P6_ST" in
      *unauthorized*)
        warn "$(m projector_did_not_allow_computer_2 "$ADB_T")" ;;
      *)
        warn "$(m no_adb_connection_projector_over "$P_NAME")" ;;
    esac
    return 0
  fi
  p6_identity
  [ "$(prop sys.boot_completed)" = 1 ] && ok "$(m started)" || warn "sys.boot_completed=$(prop sys.boot_completed)"
  [ "$(prop "$(p6_lprop)")" = "$(p6_lval)" ] || die "$(m projector_not_run_lumen_os_3 "$P_NAME")"
  v=$(prop "$P_VERSION_PROP"); sec=$(prop ro.adb.secure)
  if [ "$UNSIGNED" = 0 ] && [ -n "${VERSION:-}" ] && [ "$v" != "$VERSION" ]; then warn "Lumen OS $v ($(m expected_2) $VERSION)"
  else ok "Lumen OS $v ($SERIAL, $ADB_T)"; fi
  [ "$sec" = 1 ] && ok "$(m debugging_asks_before_new_computer)" || warn "ro.adb.secure=$sec ($(m test_build))"
  st=$(state_get step_wipe)
  case "$st" in
    requested|manual|done)
      case "$(p6_marker)" in
        there)
          warn "$(m data_not_erased_check_file)"
          if ask "$(m erase_all_data_now_androids)"; then
            ERASE_NOW=0; play6_wipe; return 0
          fi ;;
        gone)
          ok "$(m data_erased_check_file_before)"
          state_set step_wipe done ;;
        *) warn "$(m check_file_factory_reset_could)" ;;
      esac
      [ -z "$(adbs shell pm path "$P6_TUNER" 2>/dev/null | tr -d '\r')" ] && ok "$(m systemui_tuner_stock_gone)" || warn "$(m systemui_tuner_stock_still)"
      n=$(adbs shell pm list packages -3 2>/dev/null | tr -d '\r' | grep -c '^package:')
      [ "$n" = 0 ] && ok "$(m no_apps_before)" || say "$(m user_apps "$n")" ;;
    keep) ok "$(m data_kept_keep_data)" ;;
  esac
  for s in "global device_provisioned" "secure user_setup_complete" "secure tv_user_setup_complete"; do
    flags="$flags$(adbs shell settings get $s 2>/dev/null | tr -d '\r')"
  done
  [ "$flags" = 111 ] && ok "$(m projector_marked_set_up_home)" \
    || warn "$(m projector_not_marked_set_up "$flags")"
  [ -n "$(adbs shell pidof "$P6_APP" "$P6_APP_OLD" 2>/dev/null | tr -d '\r')" ] && ok "$(m projector_app_running)" || warn "$(m projector_app_not_running "$P6_APP")"
  adbs shell dumpsys media.audio_policy 2>/dev/null | grep -q "Output" && ok "$(m audio_outputs_present)" || warn "$(m no_audio_outputs_listed)"
  state_set step_verify done
}

play6_install() {  # write (with the erase request), first start, wipe: each step once, continued where it stopped
  [ "$(state_get step_flash)" = done ] || play6_write
  if [ "$(state_get step_boot1)" != done ]; then
    # the erase request in misc: XGIMI's recovery erases before the first start. Without it (--keep-data, refused by
    # fastboot mode, or an install begun by an older installer): the first start on the old data, the wipe after it
    if [ "$KEEP_DATA" = 0 ] && [ "$(state_get step_bcb)" = done ]; then play6_boot_fresh; else play6_boot1; fi
  fi
  if [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_fill)" != done ]; then
    case "$(state_get step_wipe)" in
      '') p6_gapps_fill_step ;;                                                  # on the old data, as before
      requested|done) [ "$(state_get step_bcb)" = done ] && p6_gapps_fresh_fill ;;  # on clean data
    esac
  fi
  if [ -z "$(state_get step_wipe)" ]; then
    if [ "$KEEP_DATA" = 1 ]; then state_set step_wipe keep; else play6_wipe; fi
  fi
}
play6_flash() {  # the 'flash' command: a new write, unless a write waits in fastbootd (then it continues there)
  if ! { [ -n "$(state_get step_fastboot)" ] && [ "$(state_get step_flash)" != done ] && p6_in_fastboot; }; then
    # a finished earlier write leaves nothing to continue: its step_fastboot must not count for this new write
    [ "$(state_get step_flash)" != done ] || p6_write_again
  fi
  play6_install
  play6_done
}
play6_done() {
  local w
  w=$(state_get step_wipe)
  echo
  case "$w" in
    keep)
      [ "$(state_get step_verify)" = done ] || play6_verify 30
      m done_lumen_os_runs_data "$P_NAME"; echo ;;
    manual)
      m lumen_os_written_finish_factory; echo ;;
    *)
      # a later run: the projector may answer again (Wi-Fi joined, debugging on): then check it
      if [ "$WIPED_NOW" = 0 ] && [ "$(state_get step_verify)" != done ]; then
        [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
        if [ -n "$ADB_T" ] && wifi_connect 0; then play6_verify 0; echo; fi
      fi
      m done_projector_settings_network "$P_NAME"; echo
      if [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_fill)" = done ]; then
        ok "$(m google_services_after_joining_wi "$P_NAME")"
      fi ;;
  esac
  echo "  $(m guide): $(p6_guide)"
}
play6_all() {
  release_guard
  [ -z "$GAPPS_WITH" ] || p6_gapps_plan
  [ "$(state_get step_check)" = done ] || play6_check
  if [ -z "$(state_get step_backup)" ] && ! p6_in_fastboot; then
    if ask "$(m make_backup_apps_files_first)"; then cmd_backup; else state_set step_backup skipped; fi
  fi
  play6_install
  play6_done
}

# ------------------------------------------------------------------ XGIMI Play 6: gapps and gsf (the Google services add-on)
# The user's own download of MindTheGapps for 32-bit Android 14 TV (only a zip whose sha256 is in
# lib/play6/gapps_allow.txt) goes into the logical partition lumen_gapps in super (no slot suffix), which the
# image's /system/etc/xgimi/lumen_gapps.sh mounts read-only at every start and lays over /system
# (sys.lumen.gapps: none | off | mount_failed | bad:<what> | mounted:<n>). 'gapps --zip FILE':
#   1. checks: the zip (sha256, on this computer), the projector over Wi-Fi (Lumen OS for the Play 6 with the
#      add-on support, no Google built in, adb root); lib/play6/gapps_fill.sh, pushed to the projector with
#      the allow-list and the sysconfig layer, checks the rest (tools, room in /data for the zip). Google
#      already on (mounted): it stops, nothing written ('gapps --remove' first to put them in again);
#   2. gapps_fill.sh 'off' writes /cache/lumen_gapps_off, so that the next starts leave the partition unmounted;
#   3. fastboot mode over the A-to-A cable: room in super (else it stops, nothing written in super), create
#      lumen_gapps with the allow-list's part= size, or resize an existing one to it; restart, cable out;
#   4. Lumen OS back over Wi-Fi, adb root: the zip is pushed, gapps_fill.sh formats the partition (the
#      projector's own mke2fs: no e2fsprogs on the computer), fills it with exactly the allow-listed files
#      (each checked by sha256 and size), unmounts, checks it with e2fsck and removes the 'off' file;
#   5. the data wipe of the install (Android's factory reset, carried out by XGIMI's recovery: play6_wipe),
#      after which the first start sets Google up; --keep-data: a restart, then sys.lumen.gapps must say mounted.
# 'gapps --remove': deletes lumen_gapps in fastboot mode over the cable, then the same wipe (--keep-data: not).
# 'gapps': the add-on's state. 'gsf': the Google Services Framework ID for google.com/android/uncertified.
P6_GAPPS=lumen_gapps
P6_GAPPS_LIB=$HERE/lib/play6

p6_gapps_line() { awk -v z="zip=$1" '$1 == z' "$P6_GAPPS_LIB/gapps_allow.txt" 2>/dev/null | head -n 1; }
p6_gapps_push() {  # fill.sh, the allow-list and the sysconfig layer into GAPPS_DIR on the projector
  local f
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  adbs shell mkdir -p "$GAPPS_DIR" >/dev/null 2>&1
  for f in gapps_fill.sh:fill.sh gapps_allow.txt:allow.txt lumen-gapps.xml:lumen-gapps.xml; do
    adbs push "$P6_GAPPS_LIB/${f%%:*}" "$GAPPS_DIR/${f#*:}" >/dev/null 2>&1 || die "adb push lib/play6/${f%%:*}"
  done
}
p6_lumen_only() {  # connected: Lumen OS for this projector runs (play6_device said so), else STOP
  [ "$(state_get dev_lumen)" = 1 ] || die "$(m projector_runs_xgimis_system "$P_NAME")"
}
p6_root_or_die() {  # adb root over Wi-Fi, else STOP (nothing written yet)
  local rc
  p6_root; rc=$?
  case "$rc" in
    0) ;;
    2) die "$(m after_adb_root_projector_did_2)" ;;
    3) die "$(m root_debugging_off_projector_turn)" ;;
    *) die "$(m adb_root_not_possible_system)" ;;
  esac
}
p6_wait_lumen() {  # after a restart: Lumen OS of this projector back over Wi-Fi and started (else STOP with $1)
  local i=0 t0
  say "$(m waiting_lumen_os_over_wi_2)"
  wifi_connect 600 || die "$(m lumen_os_did_not_answer_2) $1"
  p6_identity
  t0=$SECONDS
  while [ "$(prop sys.boot_completed)" != 1 ]; do
    [ "$i" -lt 300 ] && [ $((SECONDS - t0)) -lt 300 ] || die "$(m lumen_os_did_not_finish_2) $1"
    sleep 5; i=$((i + 5))
  done
  [ "$(prop "$(p6_lprop)")" = "$(p6_lval)" ] || die "$(m projector_not_run_lumen_os_3 "$P_NAME")"
}
p6_to_fastboot() {  # Lumen OS runs: cable in, restart into fastboot mode, check it there
  local a
  echo
  m take_usb_stick_out_projectors; echo
  printf '%s ' "$(m press_enter_when_cable)"
  read -r a
  say "$(m restarting_fastboot_mode_about_30)"
  if ! adbs reboot fastboot >/dev/null 2>&1 && p6_alive; then
    die "$(m adb_reboot_fastboot_failed_2)"
  fi
  adb disconnect "$ADB_T" >/dev/null 2>&1
  p6_fb_wait 180 || die "$(m projector_did_not_appear_cable_2)"
  play6_fbd_check
}
p6_super_free() {  # bytes of super that no logical partition uses (each rounded up to 1 MiB), minus SUPER_RESERVE
  local sup p sz used=0
  sup=$(gv_size super)
  [ -n "$sup" ] || { echo 0; return; }
  for p in $(printf '%s\n' "$GV" | sed -n 's/^(bootloader) is-logical:\([^:]*\):yes.*/\1/p' | sort -u); do
    sz=$(gv_size "$p"); [ -n "$sz" ] || continue
    used=$(( used + (sz + 1048575) / 1048576 * 1048576 ))
  done
  echo $(( sup - used - P_RESERVE ))
}
p6_gapps_wipe() {  # the data wipe of the install (Android's factory reset), right after a 'gapps' change
  # play6_wipe works on Lumen OS that has started (step_boot1) and asks again unless ERASE was typed (ERASE_NOW).
  # A check file of an earlier wipe must not count for this one.
  state_set step_boot1 done; state_set wipe_marker ""; state_set step_wipe ""; state_set step_verify ""
  ERASE_NOW=1
  play6_wipe
}

p6_gapps_files() {  # FILE: the zip is there, and the installer's own files for it
  local f
  [ -f "$1" ] || die "$(m no_file "$1")"
  for f in gapps_allow.txt gapps_fill.sh lumen-gapps.xml; do
    [ -f "$P6_GAPPS_LIB/$f" ] || die "internal: lib/play6/$f is missing: re-download the installer of the $P_NAME release"
  done
}
p6_gapps_zipcheck() {  # FILE: the zip on this computer against lib/play6/gapps_allow.txt; sets P6G_SHA, P6G_NAME, P6G_PART
  local zl sc
  say "$(m checking "$(basename "$1")")"
  P6G_SHA=$(sha256 "$1")
  zl=$(p6_gapps_line "$P6G_SHA")
  [ -n "$zl" ] || die "$(m not_mindthegapps_file_installer_2 "$(basename "$1")" "$P_NAME" "${P6G_SHA:0:16}"). $(m nothing_written)"
  P6G_NAME=$(printf '%s\n' "$zl" | cut -d' ' -f2); P6G_PART=$(gapps_field "$zl" part); sc=$(gapps_field "$zl" sysconfig)
  case "$P6G_PART" in ''|*[!0-9]*) die "internal: lib/play6/gapps_allow.txt part=" ;; esac
  [ "$(sha256 "$P6_GAPPS_LIB/lumen-gapps.xml")" = "$sc" ] || die "internal: lib/play6/lumen-gapps.xml is not the allow-listed one: re-download the installer"
  ok "$P6G_NAME, sha256 ${P6G_SHA:0:16}..."
}
p6_gapps_part() {  # part: fastboot mode (GV current): lumen_gapps created, or resized to part; 1 = not (why in P6G_WHY)
  local free cur mb=$(($1 / 1048576))
  free=$(p6_super_free); cur=$(gv_size "$P6_GAPPS"); cur=$(( ${cur:-0} ))
  if [ $(( free + (cur + 1048575) / 1048576 * 1048576 )) -lt "$1" ]; then
    P6G_WHY="$(m not_enough_free_space_super_3 "$(( (free + cur) / 1048576 ))" "$mb")"
    return 1
  fi
  if [ "$cur" = 0 ]; then
    say "$(m creating_mb "$P6_GAPPS" "$mb")"
    fb_do create "$P6_GAPPS" "$1" || { P6G_WHY="$(m could_not_created_2 "$P6_GAPPS")"; return 1; }
  elif [ "$cur" != "$1" ]; then
    say "$(m resizing_mb "$P6_GAPPS" "$mb")"
    fb_do resize "$P6_GAPPS" "$1" || { P6G_WHY="$(m could_not_resized "$P6_GAPPS")"; return 1; }
  else
    ok "$(m there_mb "$P6_GAPPS" "$mb")"
  fi
}

play6_gapps_install() {
  local zip=$GAPPS_ZIP zsha name part have o a mb i
  p6_gapps_files "$zip"
  check_tools
  p6_gapps_zipcheck "$zip"
  zsha=$P6G_SHA; name=$P6G_NAME; part=$P6G_PART
  mb=$((part / 1048576))
  if ! play6_adb 60; then
    # an earlier run may have stopped while the projector waited in fastboot mode on the cable
    if p6_in_fastboot; then
      fb reboot >/dev/null 2>&1
      die "$(m projector_waiting_fastboot_mode)"
    fi
    die "$(p6_noconn_text)"
  fi
  play6_device
  p6_lumen_only
  have=$(prop sys.lumen.gapps)
  case "$have" in
    '') die "$(m lumen_os_projector_has_no "$P_NAME")" ;;
    mounted:*)
      ok "$(m google_services_already_added)"
      return 0 ;;
    none) [ -z "$(prop ro.com.google.gmsversion)" ] || die "$(m projector_runs_lumen_os_google_2)" ;;
  esac
  p6_root_or_die
  echo
  if [ "$KEEP_DATA" = 1 ]; then
    m adding_google_services_installer "$P6_GAPPS" "$mb" "$name"; echo
    say "$(m keep_data_sign_google_later_2)"
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
  else
    m adding_google_services_installer_2 "$P6_GAPPS" "$mb" "$name"; echo
    if [ "$(state_get step_backup)" != done ] && ask "$(m make_backup_apps_files_first)"; then
      cmd_backup
    fi
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
  fi
  # 1. the fill script's own checks on the projector, then the 'off' file (the next starts leave the partition alone)
  p6_gapps_push
  o=$(gapps_sh pre "$zsha" "$(wc -c < "$zip" | tr -d ' ')")
  case "$o" in "OK pre "*) ;; *) adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1; die "$(m projector_not_ready): ${o#FAIL }. $(m nothing_written)" ;; esac
  o=$(gapps_sh off "$zsha")
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  case "$o" in "OK off"*) ;; *) die "$(m projector_not_ready): ${o#FAIL }. $(m nothing_written)" ;; esac
  # 2. fastboot mode: the partition
  p6_to_fastboot
  p6_gapps_part "$part" || p6_fb_stop "$P6G_WHY"
  fb reboot >/dev/null 2>&1
  p6_cable_out
  # 3. filled over Wi-Fi as root
  p6_wait_lumen "$(m still_empty_lumen_os_starts "$P6_GAPPS")"
  p6_root_or_die
  [ "$(prop sys.lumen.gapps)" = off ] || die "$(m projector_did_not_leave_unmounted "$P6_GAPPS" "$(prop sys.lumen.gapps)")"
  p6_gapps_push
  say "$(m copying_projector_over_wi_fi "$name")"
  adbs push "$zip" "$GAPPS_DIR/gapps.zip" >/dev/null 2>&1 \
    || { adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1; die "$(m adb_push_zip_failed_lumen)"; }
  say "$(m formatting_copying_google_apps "$P6_GAPPS")"
  o=$(gapps_sh fill "$zsha")
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  case "$o" in
    "OK fill "*) ok "$P6_GAPPS: ${o#OK fill } $(m of) $name" ;;
    *) die "$(m google_services_were_not_added): ${o#FAIL }. $(m lumen_os_starts_without_them_2)" ;;
  esac
  state_set gapps "$name"
  # 4. the wipe, so that the first start sets up Google services
  if [ "$KEEP_DATA" = 1 ]; then
    say "$(m restarting)"
    adbs reboot >/dev/null 2>&1
    i=0; while p6_alive && [ "$i" -lt 60 ]; do sleep 5; i=$((i + 5)); done   # until it goes away
    adb disconnect "$ADB_T" >/dev/null 2>&1
    p6_wait_lumen ""
    case "$(prop sys.lumen.gapps)" in
      mounted:*) ok "$(m google_services_sign_settings)" ;;
      *) warn "$(m projector_not_use_google_services "$(prop sys.lumen.gapps)")" ;;
    esac
  else
    p6_gapps_wipe
    ok "$(m done_join_wi_fi_projector "$P_NAME")"
  fi
  state_set step_gapps done
}

play6_gapps_remove() {
  local a removed=0
  check_tools
  play6_adb 60 || die "$(p6_noconn_text)"
  play6_device
  p6_lumen_only
  case "$(prop sys.lumen.gapps)" in
    none|'') ok "$(m projector_has_no_nothing_remove "$P6_GAPPS")"
             state_set gapps ""; return 0 ;;
  esac
  echo
  if [ "$KEEP_DATA" = 1 ]; then
    m removing_google_services_added_3 "$P6_GAPPS"; echo
    printf '%s ' "$(m type_write_continue)"
    read -r a
    [ "$a" = WRITE ] || die "$(m cancelled_nothing_written)"
  else
    m removing_google_services_added_4 "$P6_GAPPS"; echo
    printf '%s ' "$(m type_erase_continue)"
    read -r a
    [ "$a" = ERASE ] || die "$(m cancelled_nothing_written)"
  fi
  p6_to_fastboot
  if [ -n "$(gv_size "$P6_GAPPS")" ]; then
    fb_do delete "$P6_GAPPS" || p6_fb_stop "$(m could_not_deleted "$P6_GAPPS")"
    removed=1; ok "$(m deleted "$P6_GAPPS")"
  else
    ok "$(m no_super_nothing_remove "$P6_GAPPS")"
  fi
  fb reboot >/dev/null 2>&1
  p6_cable_out
  state_set gapps ""
  if [ "$removed" = 1 ] && [ "$KEEP_DATA" = 0 ]; then
    p6_wait_lumen ""
    p6_gapps_wipe
  fi
  ok "$(m projector_starts_lumen_os_without)"
}

play6_gapps_status() {
  local v
  play6_adb 60 || die "$(p6_noconn_text)"
  play6_device
  p6_lumen_only
  v=$(prop sys.lumen.gapps)
  case "$v" in
    mounted:*) ok "$(m google_services_added) ($(prop ro.com.google.gmsversion))" ;;
    none)
      if [ -n "$(prop ro.com.google.gmsversion)" ]; then ok "$(m lumen_os_has_google_services)"
      else say "$(m no_google_services_added_add_2 "$P_NAME")"; fi ;;
    off) warn "$(m google_services_off_gapps_zip)" ;;
    '') say "$(m lumen_os_has_no_google_2)" ;;
    *) warn "$(m google_services_added_cannot_used "$v")" ;;
  esac
}

play6_gapps() {
  [ -z "$GAPPS_ZIP" ] || [ "$GAPPS_REMOVE" = 0 ] || die "gapps: --zip or --remove, not both"
  if [ "$GAPPS_REMOVE" = 1 ]; then play6_gapps_remove
  elif [ -n "$GAPPS_ZIP" ]; then play6_gapps_install
  else play6_gapps_status; fi
}

play6_gsf() {
  local id
  check_tools
  play6_adb 60 || die "$(p6_noconn_text)"
  play6_device
  p6_lumen_only
  if [ -z "$(adbs shell pm path com.google.android.gsf 2>/dev/null | tr -d '\r')" ]; then
    ok "$(m lumen_os_has_no_google)"
    say "$(m add_them_bash_lumen_install_2 "$P_NAME")"
    return 0
  fi
  p6_root_or_die
  id=$(adbs shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db \"select value from main where name='android_id';\"" 2>/dev/null | tr -d '\r')
  [ -n "$id" ] || die "$(m no_gsf_id_yet_connect)"
  echo "GSF ID: $id"
  echo "$(m register_https_www_google_com)"
}

# ------------------------------------------------------------------ stick (read-only check of a restore stick)
p6_md5() { if command -v md5sum >/dev/null 2>&1; then md5sum "$1" | cut -d' ' -f1; else md5 -q "$1"; fi; }
stick_check() {  # DIR: the stock-restore USB stick of this projector, read only (it never formats anything)
  local m=$STICK n f bad=0 sum ver dev fs pt parts src disk
  [ -n "$P_RESTORE_BIN" ] || die "$(m stick_checks_restore_stick_bash "$(dev_name "$(flow_id play6-wifi)")")"
  [ -n "$m" ] || die "$(m which_stick_bash_lumen_install "${USER:-you}")"
  [ -d "$m" ] || die "$(m not_folder "$m")"
  say "$(m checking_stock_restore_stick_read "$m")"
  # the partition table and the file system (MBOOT reads only MBR + one FAT32 partition)
  if command -v diskutil >/dev/null 2>&1; then
    dev=$(diskutil info "$m" 2>/dev/null | sed -n 's/^ *Part of Whole: *//p' | head -1)
    fs=$(diskutil info "$m" 2>/dev/null | sed -n 's/^ *File System Personality: *//p' | head -1)
    pt=$(diskutil info "$dev" 2>/dev/null | sed -n 's/^ *Content (IOContent): *//p' | head -1)
    parts=$(diskutil list "$dev" 2>/dev/null | grep -cE '^ +[1-9][0-9]*:')
    case "$pt" in FDisk_partition_scheme) pt=mbr ;; GUID_partition_scheme) pt=gpt ;; esac
    case "$fs" in *FAT32*) fs=fat32 ;; esac
  elif command -v lsblk >/dev/null 2>&1 && command -v findmnt >/dev/null 2>&1; then
    src=$(findmnt -no SOURCE --target "$m" 2>/dev/null | head -1)
    disk=$(lsblk -no PKNAME "$src" 2>/dev/null | head -1)
    pt=$(lsblk -no PTTYPE "$src" 2>/dev/null | head -1)
    fs="$(lsblk -no FSTYPE "$src" 2>/dev/null | head -1) $(lsblk -no FSVER "$src" 2>/dev/null | head -1)"
    parts=$(lsblk -nlo TYPE "/dev/$disk" 2>/dev/null | grep -c '^part')
    case "$pt" in dos) pt=mbr ;; esac
    case "$fs" in *FAT32*|"vfat ") fs=fat32 ;; esac
  else
    pt=""
  fi
  if [ -z "$pt" ]; then warn "$(m partition_table_could_not_read)"
  else
    [ "$pt" = mbr ] && ok "$(m partition_table_mbr)" || { warn "$(m partition_table_must_mbr_master "$pt")"; bad=1; }
    [ "$parts" = 1 ] && ok "$(m one_partition)" || { warn "$(m partitions_there_must_exactly_one "$parts")"; bad=1; }
    [ "$fs" = fat32 ] && ok "$(m file_system_fat32)" || { warn "$(m file_system_must_fat32 "$fs")"; bad=1; }
  fi
  # exactly the firmware file and an empty factory.txt (folders the computer makes itself do no harm)
  for f in "$m"/* "$m"/.[!.]* "$m"/..?*; do
    [ -e "$f" ] || continue
    n=$(basename "$f")
    case "$n" in
      "$P_RESTORE_BIN"|factory.txt|.fseventsd|.Spotlight-V100|.Trashes|.TemporaryItems|"System Volume Information") ;;
      ._*) warn "$(m macos_helper_file_delete_copy "$n")"; bad=1 ;;
      *) warn "$(m nothing_else_may_stick "$n")"; bad=1 ;;
    esac
  done
  if [ -f "$m/factory.txt" ]; then
    [ -s "$m/factory.txt" ] && { warn "$(m factory_txt_must_empty)"; bad=1; } || ok "$(m factory_txt_empty)"
  else warn "$(m no_factory_txt_create_empty)"; bad=1; fi
  if [ -f "$m/$P_RESTORE_BIN" ]; then
    say "$(m checking_md5_about_minute "$P_RESTORE_BIN")"
    sum=$(p6_md5 "$m/$P_RESTORE_BIN")
    ver=$(printf '%s\n' $P_RESTORE_MD5 | awk -F: -v s="$sum" '$1 == s { print $2 }')
    if [ -n "$ver" ]; then ok "$P_RESTORE_BIN: $(m xgimi_firmware) $ver (md5 $sum)"
    else warn "$(m not_firmware_file_installer_knows "$P_RESTORE_BIN" "$sum")"; bad=1; fi
  else warn "$(m no_stick_exactly_name "$P_RESTORE_BIN")"; bad=1; fi
  [ "$bad" = 0 ] || die "$(m stick_not_ready_fix_lines "$P_NAME")"
  ok "$(m stick_ready_restore_power_cable)"
}

# ------------------------------------------------------------------ XGIMI Play 6: Lumen OS and Google services in one pass (--gapps-zip FILE)
# The full install with the Google services add-on, from the pieces of the install and of 'gapps' (the user's data is
# erased once, before the first start; the clean system is reset once more after the fill):
#   1. before anything is written: the zip and the installer's files for it (p6_gapps_plan, as 'gapps' checks them);
#   2. the write's fastboot session: p6_room counts the image and lumen_gapps together, system is written, then
#      lumen_gapps is created or resized to the allow-list's part= size (p6_gapps_add). A failed create or resize
#      leaves Google out (warned): the install goes on as a clean one;
#   3. the erase request (p6_bcb_write) and the light first start on clean data (play6_boot_fresh); the user joins
#      Wi-Fi and turns debugging and Rooted debugging on (p6_gapps_fresh_fill tells how), then
#      p6_gapps_fill_step: adb root (Rooted debugging off: the installer waits for the user), a lumen_gapps that an
#      earlier Lumen OS left in use (mounted:*) is turned off first ('off', one restart), then gapps_fill.sh
#      'pre', the zip and 'fill' (it formats the partition itself, so a cut-off fill is simply run again).
#      Without the erase request (refused, or not carried out by the recovery) the first start runs on the old data
#      (play6_boot1; Wi-Fi and debugging carry over from stock) and the fill follows right after it;
#   4. play6_wipe, the factory reset after the fill: the first start after it sets Google up.
# State: gapps_plan (the zip's sha256, saved when ERASE is typed), step_gapps_part, step_gapps_fill. A run without
# --gapps-zip while step 3 is not done asks whether to finish without Google.
P6G_SHA=""; P6G_NAME=""; P6G_PART=""; P6G_WHY=""

p6_plan_part() { gapps_field "$(p6_gapps_line "$(state_get gapps_plan)")" part; }   # lumen_gapps bytes of the saved plan
p6_gapps_plan() {  # --gapps-zip, before anything else: the zip, and that the install saved here can take it
  local plan
  p6_gapps_files "$GAPPS_WITH"
  p6_gapps_zipcheck "$GAPPS_WITH"
  plan=$(state_get gapps_plan)
  [ "$(state_get step_flash)" = done ] || return 0
  if [ -n "$(state_get step_wipe)" ]; then
    [ "$(state_get step_gapps_fill)" = done ] || die "$(m lumen_os_installed_here_without_2)"
  elif [ -z "$plan" ]; then
    die "$(m install_started_without_google)"
  elif [ "$plan" != "$P6G_SHA" ]; then
    die "$(m install_started_another)"
  fi
}
p6_gapps_add() {  # fastboot mode, system written: lumen_gapps for the saved plan; a failure leaves Google out (warned)
  local part
  part=$(p6_plan_part)
  GV=$(fb getvar all 2>&1)   # system has its new size now
  P6G_WHY="internal: lib/play6/gapps_allow.txt part="
  if [ -n "$part" ] && p6_gapps_part "$part"; then state_set step_gapps_part done; return 0; fi
  warn "$P6G_WHY $(m lumen_os_installed_without_google "$P_NAME")"
  state_set gapps_plan ""
}
p6_ne() {  # "Nothing was erased." for the earlier way; nothing after the erase before the first start
  [ "$(state_get step_bcb)" = done ] && return 0
  m nothing_erased
}
p6_root_wait() {  # adb root over Wi-Fi; Rooted debugging off: waits for the user; else STOP (the next run continues here)
  local rc
  while :; do
    p6_root; rc=$?
    case "$rc" in
      0) return 0 ;;
      3) root_setting_wait || die "$(m turn_rooted_debugging_settings_2 "$(p6_ne)")" ;;
      2) die "$(m after_adb_root_projector_did_3 "$(p6_ne)")" ;;
      *) die "$(m adb_root_not_possible_system_3 "$(p6_ne)")" ;;
    esac
  done
}
p6_gapps_fill_step() {  # step 3: on the first start (old data), fill lumen_gapps as root; Google left out: plan cleared
  local o i
  if [ -z "$GAPPS_WITH" ]; then
    say "$(m install_started_google_services)"
    ask "$(m finish_install_without_google_2)" \
      || die "$(m run_same_command_again_gapps_2 "$(p6_ne)")"
    state_set gapps_plan ""; return 0
  fi
  [ -n "$ADB_T" ] || ADB_T=$(state_get ip)
  wifi_connect 120 || die "$(p6_noconn_text 120) $(p6_ne)"
  p6_identity
  [ "$(prop "$(p6_lprop)")" = "$(p6_lval)" ] || die "$(m projector_not_run_lumen_os_4 "$P_NAME" "$(p6_ne)")"
  case "$(prop sys.lumen.gapps)" in
    ''|none)
      warn "$(m lumen_os_shows_no_sys "$P6_GAPPS" "$(prop sys.lumen.gapps)")"
      state_set gapps_plan ""; return 0 ;;
  esac
  p6_root_wait
  case "$(prop sys.lumen.gapps)" in
    mounted:*)
      # Google from an earlier install is in use: the 'off' file, one restart, then the partition is free to format
      say "$(m google_services_earlier_install)"
      p6_gapps_push
      o=$(gapps_sh off "$P6G_SHA")
      adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
      case "$o" in "OK off"*) ;; *) die "$(m projector_not_ready): ${o#FAIL }. $(p6_ne)" ;; esac
      adbs reboot >/dev/null 2>&1
      i=0; while p6_alive && [ "$i" -lt 60 ]; do sleep 5; i=$((i + 5)); done   # until it goes away
      adb disconnect "$ADB_T" >/dev/null 2>&1
      p6_wait_lumen "$(p6_ne)"
      p6_root_wait
      [ "$(prop sys.lumen.gapps)" = off ] || die "$(m projector_did_not_leave_unmounted_2 "$P6_GAPPS" "$(prop sys.lumen.gapps)" "$(p6_ne)")" ;;
  esac
  p6_gapps_push
  o=$(gapps_sh pre "$P6G_SHA" "$(wc -c < "$GAPPS_WITH" | tr -d ' ')")
  case "$o" in "OK pre "*) ;; *) adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1; die "$(m projector_not_ready): ${o#FAIL }. $(p6_ne)" ;; esac
  say "$(m copying_projector_over_wi_fi "$P6G_NAME")"
  adbs push "$GAPPS_WITH" "$GAPPS_DIR/gapps.zip" >/dev/null 2>&1 \
    || { adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1; die "$(m adb_push_zip_failed_run "$(p6_ne)")"; }
  say "$(m formatting_copying_google_apps "$P6_GAPPS")"
  o=$(gapps_sh fill "$P6G_SHA")
  adbs shell rm -rf "$GAPPS_DIR" >/dev/null 2>&1
  case "$o" in
    "OK fill "*) ok "$P6_GAPPS: ${o#OK fill } $(m of) $P6G_NAME" ;;
    *) die "$(m google_services_were_not_added): ${o#FAIL }. $(m run_same_command_again_continues_2 "$(p6_ne)")" ;;
  esac
  state_set gapps "$P6G_NAME"
  state_set step_gapps_fill done
}


# ------------------------------------------------------------------ projectors with FLOW=ab-fastbootd (lib/devices.conf)
# A virtual A/B projector whose stock Android has adb over the network only (port 5555; Developer options > USB
# debugging opens it, stock asks for no allow prompt) and whose fastbootd turns the one USB port into a device
# port: the A-to-A cable goes in when the installer asks, right before 'adb reboot fastboot' (play6_adb's way of
# connecting: ADB_T = ADDR:PORT, SERIAL = ro.serialno, the fastboot serial). What the projector IS comes from its
# profile (P_*); what may be written is fixed in fb_allowed: z9x_blobs<slot> (create, resize, flash) and
# system<slot> of the running slot when SLOTS has it, then 'fastboot format:<WIPE_FS>' of WIPE_PARTS (fastbootd
# reports them as raw, so the type is always given: ext4 made by the host's mke2fs, or f2fs by its make_f2fs, with
# casefold and projid for userdata as on the Z9X), and with GAPPS_ALLOW z9x_gapps<slot>
# (create, resize, flash, delete: the Google services add-on). Never vbmeta (the user types that step: 'vbmeta'),
# never -w, erase, another delete, --slot, set_active, a KEEP partition or the other slot. The slot is the one
# the system runs from and fastbootd reports (a or b: after a Lumen OS update it is the other one). With SLOT_CHECK
# (abf_slot_check) the other slot is shown before the first write (asked when it holds no system: a fall to it starts
# nothing) and the slots are read again after system (still the written slot, not unbootable, the other slot as it
# was, else STOP before the wipe). fastbootd has no
# idle timeout, so one visit writes and wipes. A staged or merging update (stock or Lumen OS) shows in fastbootd
# (snapshot-update-status, *-cow): 'flash' stops there, nothing written, so userdata and metadata are never
# formatted while one exists. Steps: check -> backup (asked) -> vbmeta (asked) -> blobs -> flash; verify later, after
# the setup with debugging on again. Progress: $STATE_DIR/<id>/state (step_system: started | done, so a write cut
# off in the middle is never followed by a restart that would start half a system; step_zblobs: z9x_blobs written
# in this visit, so a resumed run needs no blobs file).
ABF_S=""             # the slot abf_fbd_check found running in fastbootd (_a, _b); fb_allowed writes only there
ABF_UP=0             # adb reached the projector in this run (abf_adb)
ABF_DUMP=""          # dump.erofs that reads the image ('' = none on this computer)
ABF_RESUME=0         # 1 = abf_write continues a visit in fastboot mode: an image STOP goes through abf_fb_stop
ABF_GAPPS=0          # 1 = this run is 'gapps' (its own fastbootd visit): a STOP there goes by ABF_GW
ABF_GW=0             # 1 = 'gapps' changed z9x_gapps in this visit (a STOP then keeps it in fastboot mode)
ABF_GNEED=0          # bytes of super the add-on of this write still needs (--gapps-zip; abf_room counts them)

abf_other() { case "$1" in a) echo b ;; *) echo a ;; esac; }   # slot -> the other one (slot-count is 2); the slot checks of SLOT_CHECK=yes
abf_refuse_opts() {  # options of the Z9X and Play 6 ways that this flow does not have
  [ "$KEEP_DATA" = 0 ] || not_yet --keep-data
  [ "$WIPE" = format ] || not_yet "--wipe=$WIPE"
  [ -z "$GAPPS_WITH" ] || abf_gapps_on || not_yet --gapps-zip
}
abf_gapps_on() { [ -n "$P_GAPPS_ALLOW" ]; }   # the profile has the Google services add-on (GAPPS_ALLOW, GAPPS_IMAGES)
abf_gv() { printf '%s\n' "$GV" | sed -n "s/^(bootloader) $1: *//p" | head -n 1 | tr -d ' \r'; }   # getvar all, a key with ':' too
abf_hint() { printf '%s' "$(m if_hangs_cable_pull_cable)"; }
abf_dot() {  # a message that another sentence follows ends in a full stop
  case "$1" in *[.!?]) printf '%s' "$1" ;; *) printf '%s.' "$1" ;; esac
}
abf_fb_stop() {  # message: leave fastbootd for the old system (nothing was written), or stay once system was begun
  set -- "$(abf_dot "$1")"
  if [ "$ABF_GAPPS" = 1 ]; then   # 'gapps': its own visit decides, the install's steps are not this visit's
    [ "$ABF_GW" = 0 ] || die "$1 $(m z9x_gapps_changed_visit_projector)"
    state_set gapps_fastboot ""
    fb reboot >/dev/null 2>&1
    die "$1 $(m nothing_written_projector_starts) $(abf_hint)"
  fi
  case "$(state_get step_system)" in   # a restart now would start half a system, or Lumen OS on the old data
    started|done) die "$1 $(m write_install_not_finished)" ;;
  esac
  # this visit is over: a later fastboot mode (entered by hand) is never taken for it, so no run skips the checks
  state_set step_fastboot ""
  fb reboot >/dev/null 2>&1
  die "$1 $(m nothing_written_projector_starts_2) $(abf_hint)"
}
abf_img_stop() {  # message [1 = it ends in 'Nothing was written.']: an image STOP, in a fastboot visit by abf_fb_stop
  [ "$ABF_RESUME" = 0 ] || abf_fb_stop "$1"
  [ "${2-}" != 1 ] || die "$(abf_dot "$1") $(m nothing_written)"
  die "$1"
}

abf_other_hint() {  # model board: another projector this installer knows, and its way in
  local id; id=$(profile_of "$1" "$2")
  case "$(profile_flow "$id")" in
    '') ;;
    z9x-usb) printf ' %s' "$(m xgimi_z9x_installs_over_usb)" ;;
    play6-wifi) printf ' %s' "$(m installs_installer_own_release "$(dev_name "$id")")" ;;
    *) if [ -n "$(devconf_todo "$id")" ]; then   # its profile is not finished (TODO): no way in yet
         printf ' %s' "$(m lumen_os_not_support_yet "$(dev_name "$id")")"
       else
         printf ' %s' "$(m installs_installer_own_release_2 "$(dev_name "$id")" "$id")"
       fi ;;
  esac
}
abf_identity() {  # the device at ADB_T: this projector model, and the saved one (DHCP may give its address away)
  local code board ser
  code=$(prop ro.boot.xgimi.modelname); board=$(prop ro.boot.hardware); ser=$(prop ro.serialno)
  if ! abf_is_model "$code" || [ "$board" != "$P_BOARD" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    # a model code of this projector that is not supported yet (MODELS_NOT_YET): named as such, never 'not an HB'
    if [ "$board" = "$P_BOARD" ] && [ -n "$code" ] && case " $P_MODELS_NOT_YET " in *" $code "*) true ;; *) false ;; esac; then
      die "$(m model_not_supported_yet_lumen "$P_NAME" "$code" "$(abf_matrix)")"
    fi
    die "$(m not_model_board_nothing_done "$P_NAME" "$code" "$board")$(abf_other_hint "$code" "$board")"
  fi
  [ -n "$ser" ] || die "$(m projector_reports_no_serial_number "$ADB_T")"
  if [ -n "$SERIAL" ] && [ "$SERIAL" != "$ser" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    if [ "$SERIAL_FROM_STATE" = 1 ]; then
      die "$(m projector_has_serial_but_install_2 "$ADB_T" "$ser" "$SERIAL" "$PROJECTOR")"
    fi
    die "$(m projector_has_serial_not_serial "$ADB_T" "$ser" "$SERIAL")"
  fi
  SERIAL=$ser; ABF_MODEL=$code
  [ -z "$P_MODELS_OK" ] || abf_revision "$code"
}
abf_revision() {  # model: its stock really is that model's (MODELS_OK profiles; read only): the stock ro.product.device
  # (not asked on a running Lumen OS of this projector, whose system names the device itself), the stock product
  # name of that model (another one = the firmware of another model: a cross-flashed unit) and the stock config
  # folder of that model (MODEL_CONFIG/<model>/system.ini). The shell may list that folder but not stat its files
  # (the stock SELinux policy: shell has dir access to mnt_xgimiconfig_file only; 'ls FILE' is denied), so the
  # folder is listed and system.ini must be one of its names
  local dev prod want cfg got
  dev=$(prop ro.product.device)
  if [ "$(prop "${P_LUMEN_PROP%% *}")" != "${P_LUMEN_PROP#* }" ] && [ "$dev" != "$P_DEVICE" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    die "$(m not_stock_reports_device_not "$P_NAME" "$dev" "$P_DEVICE")"
  fi
  want=$(pair_get "$P_PRODUCT_NAMES" "$1"); prod=$(prop ro.vendor.xgimi.product.name)
  if [ -z "$prod" ]; then
    warn "$(m projector_reports_no_stock_product "$want")"
  elif [ "$prod" != "$want" ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    die "$(m model_reports_stock_product_not "$P_NAME" "$1" "$prod" "$want")"
  fi
  cfg="$P_MODEL_CONFIG/$1/system.ini"
  got=$(adbs shell ls "$P_MODEL_CONFIG/$1" 2>/dev/null | tr -d '\r' | grep -cx 'system\.ini')
  if [ "$got" != 1 ]; then
    adb disconnect "$ADB_T" >/dev/null 2>&1
    die "$(m xgimi_firmware_has_no_settings "$P_NAME" "$1" "$cfg")"
  fi
}
abf_adb() {  # seconds: adb over the network (--ip, the saved address, or mDNS) and who it is; 1 = no answer
  local a
  if [ -z "$ADB_T" ]; then
    a=$(state_get ip)
    if [ -z "$a" ]; then
      a=$(p6_discover)
      if [ -z "$a" ] || ! ask "$(m projector_answers_yours_one "$a")"; then
        die "$(m give_projectors_network_address "$P_NAME")"
      fi
    fi
    ADB_T=$a
  fi
  say "$(m connecting_over_network "$ADB_T")"
  wifi_connect "$1" || return 1
  abf_identity
  ABF_UP=1
}
abf_device() {  # connected: firmware, slot, virtual A/B, stock or Lumen OS; saves serial, address, firmware, slot
  local vend slot vab lum ver fv
  vend=$(prop ro.vendor.build.version.incremental); slot=$(prop ro.boot.slot_suffix); vab=$(prop ro.virtual_ab.enabled)
  lum=$(prop "$(p6_lprop)")
  # a projector that shares its XGIMI product image with another (FAMILY) could run that one's Lumen OS: it names
  # itself by the family's version property, never by ours
  if [ -z "$lum" ] && [ -n "$P_FAMILY" ]; then
    fv=$(devconf_get "$P_FAMILY" VERSION_PROP)
    if [ -n "$fv" ] && [ "$fv" != "$P_VERSION_PROP" ] && [ -n "$(prop "$fv")" ]; then lum=$P_FAMILY; fi
  fi
  if [ -n "$P_MODELS_OK" ]; then
    # the model x firmware table: this model's own list; a firmware only other models of this projector run is the
    # firmware of another model on it (a cross-flash), and the way out is its own stock, never Lumen OS
    fv=$(abf_model_fw "$ABF_MODEL")
    case " $fv " in
      *" $vend "*) ;;
      *) case " $P_VENDOR_OK " in
           *" $vend "*) die "$(m xgimi_firmware_not_firmware_model "$vend" "$P_NAME" "$ABF_MODEL" "$(abf_fw_names "$fv")" "$(abf_matrix)" "$(abf_fw_names "${fv%% *}")")" ;;
           *) die "$(m xgimi_firmware_not_supported_lumen_2 "$vend" "$P_NAME" "$ABF_MODEL" "$(abf_fw_names "$fv")")" ;;
         esac ;;
    esac
  else
    case " $P_VENDOR_OK " in
      " TODO"*) ;;   # not known yet: the profile is not finished, abf_check stops after its reads (abf_todo_stop)
      *" $vend "*) ;;
      *) die "$(m xgimi_firmware_not_supported_lumen_3 "$vend" "$P_NAME" "$(p6_fw_list)")" ;;
    esac
  fi
  case "$slot" in
    _?) case " $P_SLOTS " in *" ${slot#_} "*) ;; *) die "$(m runs_slot_lumen_os_installs "$P_NAME" "${slot#_}" "$P_SLOTS")" ;; esac ;;
    *) die "$(m projector_reports_no_system_slot "$slot" "$P_NAME")" ;;
  esac
  [ "$vab" = true ] || die "$(m projector_has_no_virtual_b "$vab" "$P_NAME")"
  case "$lum" in
    '') state_set dev_lumen 0; state_set dev_version ""; ver=$(m xgimi_stock) ;;
    "$(p6_lval)") ver=$(prop "$P_VERSION_PROP"); state_set dev_lumen 1; state_set dev_version "$ver"; ver="Lumen OS $ver" ;;
    *) die "$(m projector_runs_lumen_os_another "$lum")" ;;
  esac
  state_set serial "$SERIAL"; state_set ip "$ADB_T"; state_set vendor "$vend"; state_set slot "$slot"
  ok "$P_NAME $SERIAL ($ADB_T), $(m firmware) $vend, $(m slot) ${slot#_}, $(m system): $ver"
}

# The vbmeta step, read only: with verification off first-stage init sets up no dm-verity, so /dev/block/mapper
# has no '*-verity' device (stock with its own vbmeta shows system-verity, vendor-verity...): check_vbmeta_effective
# of the Z9X, for every *-verity. 'check' reports it; 'flash' stops without it (a system written over a verified
# slot fails dm-verity at every start, and only the stock restore helps then).
abf_vbmeta_state() {  # 0 = verification off on the running slot, 1 = on, 2 = could not read
  local m
  m=$(adbs shell ls /dev/block/mapper/ 2>/dev/null | tr -d '\r')
  [ -n "$m" ] || return 2
  printf '%s\n' "$m" | grep -q -- '-verity$' && return 1
  return 0
}
abf_vbmeta_check() {  # check|flash
  local s; s=$(state_get slot)
  abf_vbmeta_state; case $? in
    0) ok "$(m vbmeta_verification_off_slot_no_2 "${s#_}")"; state_set step_vbmeta done ;;
    1) state_set step_vbmeta ""
       [ "$1" = flash ] && die "$(m running_slot_still_verifies_system_2 "${s#_}")"
       warn "$(m vbmeta_slot_still_verifies_system "${s#_}")" ;;
    *) [ "$1" = flash ] && die "$(m could_not_read_dev_block_2)"
       warn "$(m could_not_read_dev_block)" ;;
  esac
}
abf_vbmeta() {  # the one manual step; the installer never runs any vbmeta command
  local a=${ADB_T:-$(state_get ip)}
  a=${a:-ADDRESS:5555}
  echo
  m one_manual_step_vbmeta_type "$P_NAME" "$a" "$(abf_hint)"
  echo "  Google: $GOOGLE_GSI"
  echo "  $(m guide): $(p6_guide)#vbmeta"
  echo
  if ask "$(m have_done_vbmeta_step_did_2)"; then
    state_set step_vbmeta done
  else
    die "$(m vbmeta_step_first_run_installer_2)"
  fi
}

abf_image_props() {  # the image's properties in the order init reads them (a later file wins: product is read last)
  local p
  for p in /system/etc/prop.default /system/build.prop /system/system_ext/etc/build.prop /system/product/etc/build.prop; do
    "$ABF_DUMP" --cat --path="$p" "$IMG" 2>/dev/null
  done
  return 0
}
abf_image_has() {  # path: 0 = the image holds that file with content
  local sz
  sz=$("$ABF_DUMP" --path="$1" "$IMG" 2>/dev/null | sed -n 's/^Size: \([0-9]*\).*/\1/p' | head -n 1)
  [ -n "$sz" ] && [ "$sz" -gt 0 ]
}
abf_dump_find() {  # dump.erofs: the installer's own (tools/erofs-utils, checked, or --erofs-utils), else one on PATH
  local plat
  [ -z "$ABF_DUMP" ] || return 0
  plat=$(eu_plat)
  if [ -n "$EU_OPT" ] || { [ -n "$plat" ] && [ -d "$HERE/tools/erofs-utils/$plat" ]; }; then eu_setup; ABF_DUMP=$EU_DIR/dump.erofs
  elif command -v dump.erofs >/dev/null 2>&1; then ABF_DUMP=$(command -v dump.erofs); fi
  return 0
}
abf_check_image() {  # the release image of this projector: name, erofs, size, signature, checksum, content
  local name dir sums sig cert pub want got a f sz bp v sec rel max g nv="" own=0
  find_image
  name=$(basename "$IMG"); dir=$(dirname "$IMG"); sums="$dir/SHA256SUMS"; sig="$sums.sig"; cert="$HERE/$P_CERT"
  case "$name" in *"$P_TOKEN"*) ;; *) abf_img_stop "$(m not_image_name_must_contain_2 "$name" "$P_NAME" "$P_TOKEN")" 1 ;; esac
  case "$name" in *PRIVATE*) abf_img_stop "$(m private_image_never_installed_2 "$name" "$P_NAME")" 1 ;; esac
  if [ "$UNSIGNED" = 0 ]; then
    case "$name" in *-nogms-*) ;; *) abf_img_stop "$(m not_release_file_has_lumen_2 "$name" "$P_NAME")" 1 ;; esac
    # the release version is in the name, and the signed SHA256SUMS lists the image under that name
    [ -n "${VERSION:-}" ] || die "internal: release.conf has no VERSION: re-download the installer of the $P_NAME release"
    case "$name" in "lumen-os-$P_TOKEN-"*"-nogms-system.img") nv=${name#"lumen-os-$P_TOKEN-"}; nv=${nv%-nogms-system.img} ;; esac
    [ -n "$nv" ] || abf_img_stop "$(m not_named_like_release_file_2 "$name" "$P_TOKEN")" 1
    [ "$nv" = "$VERSION" ] || abf_img_stop "$(m lumen_os_but_installer_download_2 "$name" "$nv" "$VERSION" "$P_NAME")" 1
  fi
  # the file system, read without any tool: erofs has e2 e1 f5 e0 at byte 1024 (the fstab mounts nothing else)
  if [ "$(od -An -tx1 -j1024 -N4 "$IMG" 2>/dev/null | tr -d ' \n')" != e2e1f5e0 ]; then
    if [ "$(od -An -tx1 -j1080 -N2 "$IMG" 2>/dev/null | tr -d ' \n')" = 53ef ]; then
      abf_img_stop "$(m ext4_image_another_projectors_not "$name" "$P_NAME")" 1
    elif [ "$(od -An -tx1 -N4 "$IMG" 2>/dev/null | tr -d ' \n')" = 3aff26ed ]; then
      abf_img_stop "$(m sparse_image_not_release_file_2 "$name")" 1
    fi
    abf_img_stop "$(m not_erofs_system_image "$name")" 1
  fi
  sz=$(wc -c < "$IMG" | tr -d ' ')
  [ $((sz % 4096)) = 0 ] || abf_img_stop "$(m has_size_not_multiple_4096_2 "$name")" 1
  # with blobs the new z9x_blobs and the reserve of super come off the budget too (as abf_room counts them later);
  # SYSTEM_MAX TODO: the budget is not known yet (the profile is not finished, abf_check stops after its reads)
  if ! is_todo "$P_SYSMAX"; then
    max=$P_SYSMAX; [ "$BLOBS" != partition ] || max=$(( P_SYSMAX - BLOB_SIZE - P_RESERVE ))
    [ "$sz" -le "$max" ] || abf_img_stop "$(m too_big_mb_most_mb "$name" "$P_NAME" "$(( (sz + 1048575) / 1048576 ))" "$((max / 1048576))")" 1
    # with --gapps-zip the add-on image (rounded up to 1 MiB) comes off it as well, as abf_room counts it in super
    g=$(abf_gapps_need)
    if [ "$g" -gt 0 ] && [ $(( (sz + 1048575) / 1048576 * 1048576 + g )) -gt "$max" ]; then
      abf_img_stop "$(m google_services_not_fit_together "$name" "$P_NAME" "$(( (sz + 1048575) / 1048576 ))" "$((g / 1048576))" "$((max / 1048576))")" 1
    fi
  fi
  if [ -f "$sums" ] && [ -f "$sig" ] && [ -f "$cert" ] && command -v openssl >/dev/null 2>&1; then
    pub=$(mktemp)
    openssl x509 -in "$cert" -pubkey -noout > "$pub"
    openssl dgst -sha256 -verify "$pub" -signature "$sig" "$sums" >/dev/null 2>&1 \
      || { rm -f "$pub"; abf_img_stop "$(m sha256sums_not_signed_lumen_os_2 "$P_NAME")"; }
    rm -f "$pub"
    ok "$(m sha256sums_signature_lumen_os_key "$P_NAME")"
  elif [ "$UNSIGNED" = 1 ]; then
    warn "$(m unsigned_image_image_not)"
    if [ "$(state_get unsigned_ok)" != "$name:$(filekey "$IMG")" ]; then
      printf '%s ' "$(m type_unsigned_install_anyway "$name")"
      read -r a
      if [ "$a" != UNSIGNED ]; then
        [ "$ABF_RESUME" = 0 ] || abf_fb_stop "$(m cancelled)"
        die "$(m cancelled_nothing_written)"
      fi
      state_set unsigned_ok "$name:$(filekey "$IMG")"
    fi
    if [ ! -f "$sums" ]; then   # a list that names the image, or the image's own .sha256
      sums=""
      for f in "$dir"/SHA256SUMS* "$IMG.sha256"; do
        case "$f" in *.sig) continue ;; esac
        [ -f "$f" ] && awk -v n="$name" '$2==n || $2=="*"n {f=1} END{exit !f}' "$f" && { sums=$f; break; }
      done
    fi
  else
    [ -f "$sums" ] || abf_img_stop "$(m no_sha256sums_next_image_cannot)"
    [ -f "$sig" ] || abf_img_stop "$(m no_sha256sums_sig_next_image)"
    [ -f "$cert" ] || abf_img_stop "$(m installer_certificate_missing_re "$P_CERT" "$P_NAME")"
    abf_img_stop "$(m openssl_needed_check_image)"
  fi
  if [ -n "$sums" ] && [ -f "$sums" ]; then
    want=$(awk -v f="$name" '$2==f || $2=="*"f {print $1}' "$sums")
    [ -n "$want" ] || abf_img_stop "$(m not_listed "$name" "$(basename "$sums")")"
    if [ "$FORCE_HASH" = 1 ] || [ "$(state_get image_ok)" != "$want:$(filekey "$IMG")" ]; then
      say "$(m checking_image_checksum_about)"
      got=$(sha256 "$IMG")
      [ "$got" = "$want" ] || { state_set image_ok none; abf_img_stop "$(m checksum_mismatch_image_damaged)"; }
      state_set image_ok "$want:$(filekey "$IMG")"
    fi
    ok "$name sha256 ${want:0:16}…"
  else
    warn "$(m unsigned_image_without_any)"
  fi
  # what the image says about itself, when a dump.erofs is there; without one the name (its version, checked
  # above) and the signed SHA256SUMS (which lists the file under that name) bind the image to this release
  abf_dump_find
  if [ -n "$ABF_DUMP" ]; then
    bp=$(abf_image_props)
    if [ -n "$bp" ]; then
      v=$(printf '%s\n' "$bp" | sed -n "s/^$(p6_lprop)=//p" | tail -1)
      [ "$v" = "$(p6_lval)" ] || abf_img_stop "$(m not_lumen_os "$name" "$P_NAME" "$(p6_lprop)" "$v")" 1
      # ABI: a system of the other userspace never starts on this projector's stock vendor (an arm64 image on a
      # 32-bit-only vendor finds none of its 64-bit libraries); the image says it in ro.system.product.cpu.abilist
      if [ -n "$P_ABI" ]; then
        v=$(printf '%s\n' "$bp" | sed -n 's/^ro\.system\.product\.cpu\.abilist=//p' | tail -1 | tr -d ' \r')
        case ",$v," in *,arm64-v8a,*) a=arm64 ;; *,armeabi-v7a,*) a=arm32 ;; *) a="" ;; esac
        [ "$a" = "$P_ABI" ] || abf_img_stop "$(m not_built_needs_system_image "$name" "$P_NAME")" 1
      fi
      v=$(printf '%s\n' "$bp" | sed -n "s/^$P_VERSION_PROP=//p" | tail -1)
      sec=$(printf '%s\n' "$bp" | sed -n 's/^ro\.adb\.secure=//p' | tail -1)
      if [ "$UNSIGNED" = 0 ]; then
        [ "$v" = "${VERSION:-}" ] || abf_img_stop "$(m holds_lumen_os_release "$name" "$v" "${VERSION:-?}")" 1
        [ "$sec" = 1 ] || abf_img_stop "$(m test_build_ro_adb_secure_2 "$name" "$sec")" 1
      fi
      # a public image holds none of the allow-list's files at /system/<path in the tar>: they come from 'blobs'
      # (BLOBS_ALLOW TODO: no list yet, the profile is not finished)
      is_todo "$P_BLOBS_ALLOW" || while read -r a rel f; do
        case "$a" in ''|\#*|set=*) continue ;; esac
        abf_image_has "/system/$rel" && own=1
      done < "$HERE/$P_BLOBS_ALLOW"
      if [ "$own" = 1 ] && [ "$BLOBS" = partition ]; then
        abf_img_stop "$(m carries_mediatek_xgimi_files "$name")"
      elif [ "$own" = 0 ] && [ "$BLOBS" = embedded ]; then
        abf_img_stop "$(m public_image_mediatek_xgimi_files "$name")"
      fi
      ok "$(m image_lumen_os "$v" "$P_NAME")"
    else
      warn "$(m dump_erofs_could_not_read "$(p6_bound_text "$nv")")"
    fi
  else
    say "$(m image_content_not_read_dump "$(p6_bound_text "$nv")")"
  fi
  [ "$BLOBS" != none ] || warn "$(m no_blobs_no_sound_no_2)"
  state_set image_gms 0
  ok "$(m edition_lumen_os_without_google)"
}
abf_check() {
  local free
  abf_refuse_opts
  check_tools
  abf_adb 60 || die "$(p6_noconn_text)"
  abf_device
  abf_vbmeta_check check
  free=$(df -k "$HOME" | awk 'NR==2{print $4}')
  [ "${free:-0}" -ge 4194304 ] || warn "$(m less_4_gb_free_computer)"
  abf_check_image
  # MODELS_CHECK_ONLY: the check passes, but the model is not written yet (abf_live_gate), so the check is never kept
  # as a step done ('all' checks again and stops there)
  abf_check_only && warn "$(m model_supported_after_live_test "$P_NAME" "$ABF_MODEL")"
  # not finished (TODO values): what could be read was read and nothing was written; the check never passes
  [ -z "$P_TODO" ] || abf_todo_stop
  ok "$(m check_passed_nothing_written)"
  abf_check_only || state_set step_check done
}

# blobs: the files of the profile's allow-list (BLOBS_ALLOW, the image's /system/etc/z9x/blobs_allow.txt) pulled
# from this projector's own system, each checked by sha256 (or derived by its from= line deletes), into the ustar
# tar of BLOB_SIZE (4 MiB, or the profile's BLOBS_SIZE) that the image's blobs reader reads from z9x_blobs<slot>;
# built by the rules of cmd_blobs. need=eye lines (the eye-protection drawer of the image, e.g. a stock native-UI
# program and its library) are files like the others, but such a profile is never installed without them: no
# --no-blobs, no --blobs=embedded (abf_main), and a file that does not match stops for good.
abf_eye() {  # 0 = the profile's allow-list has need=eye lines
  grep -q -E '^[0-9a-f]{64} [^ ]+ [^ ]+ (.* )?need=eye( |$)' "$HERE/$P_BLOBS_ALLOW" 2>/dev/null
}
abf_blobs_file() { [ "$BLOBS" = partition ] && printf '%s' "$BACKUP_ROOT/$PROJECTOR-blobs-$SERIAL.img"; return 0; }
abf_blobs_ok() {  # 0 = the blobs tar of this projector is ready (or none is needed)
  local f; f=$(abf_blobs_file)
  [ -n "$f" ] || return 0
  [ "$(state_get step_blobs)" = done ] && [ "$(state_get blobs_img)" = "$f" ] && [ -f "$f" ] \
    && [ "$(wc -c < "$f" | tr -d ' ')" = "$BLOB_SIZE" ]
}
abf_blobs() {
  if [ "$BLOBS" = none ]; then
    warn "$(m no_blobs_mediatek_xgimi_files)"
    state_set step_blobs skip; return 0
  fi
  if [ "$BLOBS" != partition ]; then
    ok "$(m image_carries_mediatek_xgimi_files)"
    state_set step_blobs skip; return 0
  fi
  [ "$ABF_UP" = 1 ] || abf_adb 60 || die "$(p6_noconn_text)"
  local allow="$HERE/$P_BLOBS_ALLOW" w="$SD/blobs" set h rel src opt o p from froms alts a fw got n=0 img out
  [ -f "$allow" ] || die "internal: $P_BLOBS_ALLOW is missing: re-download the installer"
  rm -rf "$w"; mkdir -p "$w/x"
  set=$(sed -n 's/^set=//p' "$allow")
  printf 'set=%s\n' "$set" > "$w/x/MANIFEST"
  fw=$(prop ro.vendor.build.version.incremental)
  while read -r h rel src opt; do
    case "$h" in ''|\#*|set=*) continue ;; esac
    # from= may be given several times: one stock original per XGIMI firmware (each with its own line deletes);
    # the one whose sha256 the pulled file has is used, and the result must still be <sha256>
    froms=""; alts=""
    for o in $opt; do case "$o" in from=*) from=${o#from=}
      case "$from" in *:*) ;; *) die "internal: $rel: from= needs <sha256>:<deletes>" ;; esac
      case "${from#*:}" in *[!0-9,d\;]*) die "internal: $rel: from= may only delete lines" ;; esac
      froms="$froms $from" ;;
      # alt=<firmware>:<sha256>: that stock firmware's own copy, taken as it is only on that firmware (this
      # projector's ro.vendor.build.version.incremental); the image's reader checks it the same way
      alt=*) alts="$alts ${o#alt=}" ;; esac; done
    mkdir -p "$w/x/$(dirname "$rel")"
    got=""
    for p in "$src" "/system/$rel"; do
      adbs pull "$p" "$w/x/$rel" >/dev/null 2>&1 || continue
      got=$(sha256 "$w/x/$rel")
      [ "$got" = "$h" ] && break
      for a in $alts; do [ -n "$fw" ] && [ "$a" = "$fw:$got" ] && h=$got; done
      [ "$got" = "$h" ] && break
      for from in $froms; do
        [ "$got" = "${from%%:*}" ] || continue
        sed "${from#*:}" "$w/x/$rel" > "$w/x/$rel.new" && mv "$w/x/$rel.new" "$w/x/$rel"
        got=$(sha256 "$w/x/$rel")
        break
      done
      [ "$got" = "$h" ] && break
      got="bad"
    done
    if [ "$got" != "$h" ] && abf_eye; then
      die "$(m projector_not_match_allow_list_2 "$rel" "$got" "$P_NAME")"
    fi
    [ "$got" = "$h" ] || die "$(m projector_not_match_allow_list_3 "$rel" "$got")"
    printf '%s %s %s\n' "$rel" "$h" "$(wc -c < "$w/x/$rel" | tr -d ' ')" >> "$w/x/MANIFEST"
    n=$((n + 1))
  done < "$allow"
  img="$w/z9x_blobs.img"
  find "$w/x" -exec env TZ=UTC0 touch -t 197001010000 {} +
  if tar --version 2>/dev/null | grep -q bsdtar; then
    (cd "$w/x" && find . -mindepth 1 | sed 's|^\./||' | LC_ALL=C sort | tar --format ustar --uid 0 --gid 0 --uname root --gname root -n -cf "$img" -T -)
  else
    (cd "$w/x" && find . -mindepth 1 | sed 's|^\./||' | LC_ALL=C sort | tar --format=ustar --owner=0 --group=0 --numeric-owner --mtime=@0 --no-recursion -cf "$img" -T -)
  fi
  [ "$(wc -c < "$img")" -le "$BLOB_SIZE" ] || die "blobs tar larger than $((BLOB_SIZE / 1048576)) MiB"
  dd if=/dev/null of="$img" bs=1 seek="$BLOB_SIZE" 2>/dev/null
  [ "$(wc -c < "$img" | tr -d ' ')" = "$BLOB_SIZE" ] || die "cannot pad $img"
  mkdir -p "$BACKUP_ROOT"
  out=$(abf_blobs_file)
  cp "$img" "$out"
  state_set blobs_img "$out"
  state_set step_blobs done
  ok "$(m mediatek_xgimi_files_projector_set "$n" "$set" "$out")"
}

abf_fbd_check() {  # fastbootd, one getvar all: this projector, its running slot and the known layout; else STOP
  local why="" cs k sz s v
  ABF_S=""
  GV=$(fb getvar all 2>&1)
  # the HB fastbootd answers 'getvar slot-successful:<slot>' but may leave it out of 'getvar all' (2026-11-05, a
  # user's projector stopped with "slot-successful:a ''"): when 'all' names no slot's mark, ask each slot, read only
  if ! printf '%s\n' "$GV" | grep -q '^(bootloader) slot-successful:'; then
    for s in $P_SLOTS; do
      v=$(fb getvar "slot-successful:$s" 2>&1 | sed -n "s/^slot-successful:$s: *//p" | head -n 1 | tr -d ' \r')
      case $v in yes|no) GV="$GV
(bootloader) slot-successful:$s:$v" ;; esac
    done
  fi
  [ "$(abf_gv is-userspace)" = yes ] || why=$(m not_fastbootd)
  [ -n "$why" ] || [ "$(abf_gv product)" = "$P_FB_PRODUCT" ] || why="$(m fastbootd_not_one "$P_NAME") ($(abf_gv product))"
  [ -n "$why" ] || [ "$(abf_gv serialno)" = "$SERIAL" ] || why="$(m another_projector_fastboot_mode) (serialno $(abf_gv serialno), $(m expected) $SERIAL)"
  [ -n "$why" ] || [ "$(abf_gv unlocked)" = yes ] || why=$(m bootloader_locked)
  cs=$(abf_gv current-slot)
  if [ -z "$why" ]; then case " $P_SLOTS " in *" $cs "*) ;; *) why="current-slot '$cs'" ;; esac; fi
  [ -n "$why" ] || [ "_$cs" = "$(state_get slot)" ] || why="current-slot $cs, $(m system_ran) $(state_get slot)"
  [ -n "$why" ] || [ "$(abf_gv slot-count)" = 2 ] || why="slot-count '$(abf_gv slot-count)'"
  [ -n "$why" ] || [ "$(abf_gv "slot-successful:$cs")" = yes ] || why="slot-successful:$cs '$(abf_gv "slot-successful:$cs")'"
  if [ -n "$why" ]; then :
  elif [ "$(state_get dev_lumen)" = 1 ]; then   # a Lumen OS update: it finishes only while Lumen OS runs
    [ "$(abf_gv snapshot-update-status)" = none ] || why="$(m update_finishing) (snapshot-update-status $(abf_gv snapshot-update-status)): $(m start_lumen_os_wait_10)"
    if [ -z "$why" ] && printf '%s\n' "$GV" | grep -qE ':[a-z0-9_]+-cow(:|$)'; then
      why="$(m cow_partitions_update_finishing)"
    fi
  else
    [ "$(abf_gv snapshot-update-status)" = none ] || why="$(m xgimi_update_staged_not_finished) (snapshot-update-status $(abf_gv snapshot-update-status)): $(m let_projector_start_turn_automatic)"
    if [ -z "$why" ] && printf '%s\n' "$GV" | grep -qE ':[a-z0-9_]+-cow(:|$)'; then
      why="$(m cow_partitions_xgimi_update_not)"
    fi
  fi
  [ -n "$why" ] || [ "$(abf_gv super-partition-name)" = super ] || why="super-partition-name '$(abf_gv super-partition-name)'"
  [ -n "$why" ] || [ "$(abf_gv "is-logical:system_$cs")" = yes ] || why="is-logical:system_$cs '$(abf_gv "is-logical:system_$cs")'"
  for k in $P_KEEP; do
    [ -z "$why" ] || break
    sz=$(gv_size "${k}_$cs")
    [ -n "$sz" ] && [ $((sz)) -gt 0 ] || why="$(m partition_missing "$k" "$cs") (partition-size '$sz')"
  done
  [ -z "$why" ] || abf_fb_stop "$(m fastboot_mode_check_failed): $why."
  ABF_S=_$cs
  ok "$(m fastboot_mode_slot "$P_NAME" "$SERIAL" "$cs")"
}
abf_room() {  # [blobs file]: the new z9x_blobs and the image (and the add-on of ABF_GNEED) fit into super; else STOP
  local free sys old img room need blob=0 gold g
  [ -n "$(gv_size super)" ] || abf_fb_stop "$(m fastbootd_did_not_report_size)"
  free=$(super_free); sys=$(gv_size "system$ABF_S"); old=$(gv_size "z9x_blobs$ABF_S")
  if [ -n "${1-}" ]; then blob=$(( BLOB_SIZE - ${old:-0} )); [ "$blob" -gt 0 ] || blob=0; fi
  [ "$free" -ge $(( blob + P_RESERVE )) ] || abf_fb_stop "$(m no_room_super_z9x_blobs "$ABF_S" "$((free / 1048576))" "$(( (blob + P_RESERVE) / 1048576 ))")"
  # the write resizes system to the image: the space it has now counts for it
  room=$(( free + (${sys:-0} + 1048575) / 1048576 * 1048576 - (blob + 1048575) / 1048576 * 1048576 - P_RESERVE ))
  img=$(wc -c < "$IMG" | tr -d ' '); need=$(( (img + 1048575) / 1048576 * 1048576 ))
  [ "$need" -le "$room" ] || abf_fb_stop "$(m image_not_fit_super_mb "$((room / 1048576))" "$((need / 1048576))")"
  # --gapps-zip: z9x_gapps is made or resized to the add-on image after system; what it adds to its space counts too
  [ "$ABF_GNEED" -gt 0 ] || return 0
  gold=$(gv_size "z9x_gapps$ABF_S"); gold=$(( (${gold:-0} + 1048575) / 1048576 * 1048576 ))
  g=$(( ABF_GNEED - gold )); [ "$g" -gt 0 ] || g=0
  [ $(( need + g )) -le "$room" ] || abf_fb_stop "$(m image_google_services_not_fit_2 "$((room / 1048576))" "$(( (need + g) / 1048576 ))")"
}
# SLOT_CHECK=yes (the X50): XGIMI ships the other slot's logical partitions empty (system_b, vendor_b, product_b,
# system_ext_b of size 0 in lpdump) while the boot loader keeps that slot bootable (misc: priority 14, 3 tries, not
# unbootable), so failed starts of the written slot can end on a slot where nothing starts. As on the HB (and the
# Z9X way), the installer writes only the slot the system runs from and never the other one, never switches slots
# (no set_active, no --slot), and abf_fbd_check has already required that slot to be the saved one and marked
# successful. 'before' (in fastbootd, before the first write of a visit, from its getvar all): the written slot not
# marked unbootable, the other slot's system size saved (state other_sys), and when that slot holds no system the
# user is told what a fall to it means and asked; 'no' leaves fastbootd with nothing written. 'after' (system
# written and its size checked, before the wipe): one more getvar all; fastbootd must still run the written slot,
# not marked unbootable, and the other slot's system must be the size saved before, else STOP there (no wipe, no
# restart: a restart could start the other slot). A written slot that lost its successful mark (the boot loader
# now counts its tries down) is told. abf_slot_note repeats the empty other slot after the restart. Reads only.
abf_slot_check() {  # before|after
  local cs o osz o2 u r suc cur v
  cs=${ABF_S#_}; o=$(abf_other "$cs")
  if [ "$1" = before ]; then
    [ "$(abf_gv "slot-unbootable:$cs")" != yes ] || abf_fb_stop "$(m boot_loader_marks_slot_one "$cs")"
    osz=$(gv_size "system_$o"); osz=$(( ${osz:-0} ))
    state_set other_sys "$osz"
    if [ "$osz" -gt 0 ]; then
      ok "$(m slot_system_holds_system_mb "$o" "$((osz / 1048576))")"
      return 0
    fi
    u=$(abf_gv "slot-unbootable:$o"); r=$(abf_gv "slot-retry-count:$o")
    echo
    m slot_holds_no_system_system "$o" "$P_NAME" "$cs" "$u" "$r" "$P_RESTORE_BIN"; echo
    ask "$(m write_slot_anyway "$cs")" || abf_fb_stop "$(m cancelled)"
    return 0
  fi
  GV=$(fb getvar all 2>&1)
  # as in abf_fbd_check: a fastbootd that leaves the mark out of 'getvar all' is asked for this slot alone
  if ! printf '%s\n' "$GV" | grep -q "^(bootloader) slot-successful:$cs:"; then
    v=$(fb getvar "slot-successful:$cs" 2>&1 | sed -n "s/^slot-successful:$cs: *//p" | head -n 1 | tr -d ' \r')
    case $v in yes|no) GV="$GV
(bootloader) slot-successful:$cs:$v" ;; esac
  fi
  cur=$(abf_gv current-slot); suc=$(abf_gv "slot-successful:$cs"); u=$(abf_gv "slot-unbootable:$cs")
  r=$(abf_gv "slot-retry-count:$cs")
  o2=$(gv_size "system_$o"); o2=$(( ${o2:-0} )); osz=$(state_get other_sys)
  if [ "$cur" != "$cs" ] || [ "$u" = yes ] || { [ -n "$osz" ] && [ "$o2" != "$osz" ]; }; then
    die "$(m after_write_fastboot_mode_reports "$cur" "$cs" "$u" "$o" "$o2" "${osz:-?}")"
  fi
  ok "$(m slot_stays_one_starts_current "$cs" "$cur" "$o")"
  [ "$suc" != yes ] || return 0
  if [ "$o2" -gt 0 ]; then
    warn "$(m slot_no_longer_marked_successful "$cs" "$suc" "$r" "$o")"
  else
    warn "$(m slot_no_longer_marked_successful_2 "$cs" "$suc" "$r" "$o" "$P_RESTORE_BIN")"
  fi
}
abf_slot_note() {  # SLOT_CHECK, the end of a write: the other slot that holds no system, told once more
  local cs o
  [ "$P_SLOT_CHECK" = yes ] && [ "$(state_get other_sys)" = 0 ] || return 0
  cs=${ABF_S#_}; o=$(abf_other "$cs")
  m slot_starts_lumen_os_slot "$cs" "$o" "$P_RESTORE_BIN"; echo
}
abf_infb() { [ -n "$(state_get step_fastboot)" ] && [ "$(state_get step_flash)" != done ] && p6_in_fastboot; }
abf_write() {  # stock or Lumen OS running: checks, ERASE, cable, fastbootd: z9x_blobs, system, the wipe, restart
  local a infb=0 blobs sz got p k
  abf_refuse_opts
  check_tools
  if abf_infb; then
    infb=1; ABF_RESUME=1
    say "$(m projector_waits_fastboot_mode)"
  else
    if [ "$ABF_UP" != 1 ] && ! abf_adb 60; then
      [ -z "$(state_get step_fastboot)" ] || [ "$(state_get step_flash)" = done ] || die "$(m projector_neither_fastboot_mode_2 "$ADB_T")"
      die "$(p6_noconn_text)"
    fi
    abf_device
    abf_live_gate
    abf_vbmeta_check flash
  fi
  FORCE_HASH=1 abf_check_image
  blobs=$(abf_blobs_file)
  # z9x_blobs written in this visit already (step_zblobs): only system and the wipe are left, the file is not needed
  [ "$infb" = 0 ] || [ "$(state_get step_zblobs)" != done ] || blobs=""
  if [ -n "$blobs" ] && { [ "$infb" = 0 ] || [ "$(state_get step_system)" != done ]; } && ! abf_blobs_ok; then
    if [ "$infb" = 1 ] && [ "$(state_get step_system)" = started ]; then
      abf_fb_stop "$(m mediatek_xgimi_files_projector "$blobs")"
    fi
    [ "$infb" = 0 ] || abf_fb_stop "$(m mediatek_xgimi_files_projector_2 "$blobs")"
    abf_blobs
  fi
  # --gapps-zip (one visit, one wipe, as on the Z9X): the add-on image built and checked here, before the ERASE
  # question. A visit resumed in fastboot mode keeps its saved add-on only with --gapps-zip given again
  if [ -n "$GAPPS_WITH" ]; then
    z9x_gapps_prepare
  elif [ "$infb" = 1 ] && [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_flash)" != done ]; then
    say "$(m install_started_google_services_2)"
    ask "$(m finish_install_without_google)" \
      || abf_fb_stop "$(m run_same_command_again_gapps_3)"
    state_set gapps_plan ""
  fi
  local s; s=$(state_get slot); s=${s#_}
  echo
  if [ -n "$blobs" ]; then
    m writing_lumen_os_slot_erasing_2 "$s" "$P_NAME"; echo
  else
    m writing_lumen_os_slot_erasing_3 "$s" "$P_NAME"; echo
  fi
  if [ -n "$GAPPS_WITH" ]; then
    m gapps_zip_same_visit_also "$GZ_NAME" "$s" "$(( (GI_SIZE + 1048575) / 1048576 ))"; echo
  fi
  printf '%s ' "$(m type_erase_continue)"
  read -r a
  if [ "$a" != ERASE ]; then
    [ "$infb" = 0 ] || abf_fb_stop "$(m cancelled)"
    die "$(m cancelled_nothing_written)"
  fi
  ERASE_NOW=1
  # the add-on of this write (abf_gapps_write); a new write without --gapps-zip forgets what an earlier one saved
  if [ "$infb" = 0 ] && { [ -n "$GAPPS_WITH" ] || [ -n "$(state_get gapps_plan)$(state_get step_gapps_flash)" ]; }; then
    state_set gapps_plan "$GZ_SHA"; state_set gapps_img "$GI_SHA"; state_set step_gapps_flash ""
  elif [ -n "$GAPPS_WITH" ] && [ "$(state_get gapps_plan)" != "$GZ_SHA" ]; then
    state_set gapps_plan "$GZ_SHA"; state_set gapps_img "$GI_SHA"; state_set step_gapps_flash ""
  fi
  ABF_GNEED=0
  if [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_flash)" != done ]; then
    ABF_GNEED=$(( (${GI_SIZE:-0} + 1048575) / 1048576 * 1048576 ))
  fi
  if [ "$infb" = 0 ]; then
    for k in step_fastboot step_zblobs step_system step_wipe step_flash step_verify; do state_set "$k" ""; done
    echo
    m connect_projectors_usb_port; echo
    printf '%s ' "$(m press_enter_when_cable)"
    read -r a
    say "$(m restarting_fastboot_mode_about_30)"
    # saved first: a restart that happens although adb reports an error is still continued by the next run
    state_set step_fastboot "$(date +%Y%m%d-%H%M%S)"
    if ! adbs reboot fastboot >/dev/null 2>&1 && p6_alive; then
      state_set step_fastboot ""
      die "$(m adb_reboot_fastboot_failed_3)"
    fi
    adb disconnect "$ADB_T" >/dev/null 2>&1
  fi
  p6_fb_wait 180 || die "$(m projector_did_not_appear_cable_3)"
  abf_fbd_check
  if [ -z "$(state_get gapps_plan)" ] && [ -n "$(gv_size "z9x_gapps$ABF_S")" ]; then
    say "$(m z9x_gapps_google_services_added "$ABF_S")"
  fi
  if [ "$(state_get step_system)" != done ]; then
    [ "$P_SLOT_CHECK" != yes ] || abf_slot_check before
    abf_room "$blobs"
    if [ -n "$blobs" ]; then
      if [ -n "$(gv_size "z9x_blobs$ABF_S")" ]; then fb_do resize "z9x_blobs$ABF_S" "$BLOB_SIZE"
      else fb_do create "z9x_blobs$ABF_S" "$BLOB_SIZE"; fi \
        || abf_fb_stop "$(m partition_z9x_blobs_could_not "$ABF_S")"
      fb_do flash "z9x_blobs$ABF_S" "$blobs" || abf_fb_stop "$(m z9x_blobs_not_written_system "$ABF_S")"
      state_set step_zblobs done
      ok "z9x_blobs$ABF_S"
    fi
    say "$(m writing_system_about_1_minute)"
    state_set step_system started
    fb_do flash "system$ABF_S" "$IMG" || die "$(m system_not_written_completely)"
  fi
  sz=$(wc -c < "$IMG" | tr -d ' ')
  got=$(fb getvar "partition-size:system$ABF_S" 2>&1 | sed -n "s/^partition-size:system$ABF_S: *//p" | head -n 1 | tr -d ' \r')
  case "$got" in 0x[0-9a-fA-F]*) got=$((got)) ;; *) got="" ;; esac
  if [ "$got" != "$sz" ]; then
    state_set step_system started
    die "$(m after_write_system_has_bytes "$ABF_S" "${got:-?}" "$sz")"
  fi
  state_set step_system done
  ok "system$ABF_S ($((sz / 1048576)) MB)"
  [ "$P_SLOT_CHECK" != yes ] || abf_slot_check after
  if [ -n "$(state_get gapps_plan)" ] && [ "$(state_get step_gapps_flash)" != done ]; then abf_gapps_write || state_set gapps_plan ""; fi
  say "$(m wiping_data_format "$P_WIPE_FS" "$P_WIPE")"
  for p in $P_WIPE; do
    fb_do format "$p" || die "$(m wipe_failed_projector_stays "$p")"
  done
  state_set step_wipe done
  fb reboot >/dev/null 2>&1
  state_set step_flash done
  echo
  if [ "$(state_get step_gapps_flash)" = done ]; then
    m written_projector_starts_lumen_os "$(abf_hint)"; echo
    abf_slot_note
    return 0
  fi
  m written_projector_starts_lumen_os_2 "$(abf_hint)"; echo
  abf_slot_note
}
# The Google services add-on of a profile with GAPPS_ALLOW (its own allow-list, the same file as the image's
# /system/etc/z9x/gapps_allow.txt, and GAPPS_IMAGES, the add-on images checked for it), the Z9X way: the user's own
# MindTheGapps zip becomes, on this computer, the erofs image the list names (gi_build: every file checked, the
# image bit for bit the checked one), written into z9x_gapps_<slot> in fastbootd; the image's unchanged
# z9x_gapps.sh checks it and lays it over /system at every start (sys.z9x.gapps). With --gapps-zip that is part of
# the install's one visit (abf_gapps_write: after system, before the wipe); not enough room or a failed write there
# leaves Google out (warned, the partition deleted again), as on the Z9X. 'gapps --zip FILE' on Lumen OS of this
# projector: the same image, one fastbootd visit of its own (z9x_gapps, then the format of WIPE_PARTS), nothing
# filled over adb and no root needed. 'gapps --remove': z9x_gapps deleted in such a visit, the same wipe. 'gapps':
# the add-on's state. 'gsf': the Google Services Framework ID (adb root). A 'gapps' visit cut off after z9x_gapps
# was changed stays in fastboot mode (gapps_fastboot in the state) and the same command continues there.
abf_gapps_write() {  # fastbootd, system written: z9x_gapps_<slot> sized to the add-on image and flashed; 1 = left out
  # (warned; the partition is deleted again, so the system starts plainly without Google)
  local p=z9x_gapps$ABF_S need free cur why
  need=$(( (GI_SIZE + 1048575) / 1048576 * 1048576 ))
  why=$(m install_goes_without_google)
  GV=$(fb getvar all 2>&1)   # system and z9x_blobs have their new sizes now
  free=$(super_free); cur=$(gv_size "$p"); cur=$(( ${cur:-0} ))
  if [ $(( free + cur )) -lt "$need" ]; then
    [ "$cur" -gt 0 ] && fb_do delete "$p" >/dev/null 2>&1
    warn "$(m not_enough_free_space_super_2 "$(( (free + cur) / 1048576 ))" "$((need / 1048576))" "$why")"
    state_set step_gapps_flash skipped:room; return 1
  fi
  say "$(m writing_google_services_mb_about "$p" "$((need / 1048576))")"
  if [ "$cur" -gt 0 ]; then fb_do resize "$p" "$need"; else fb_do create "$p" "$need"; fi \
    || { fb_do delete "$p" >/dev/null 2>&1; warn "$(m could_not_created "$p" "$why")"; state_set step_gapps_flash skipped:create; return 1; }
  if ! fb_do flash "$p" "$GI_IMG"; then
    p6_in_fastboot || die "$(m fastboot_mode_ended_while_being "$p")"
    fb_do delete "$p" >/dev/null 2>&1
    warn "$(m could_not_written_removed_again "$p" "$why")"
    state_set step_gapps_flash skipped:flash; return 1
  fi
  state_set step_gapps_flash done; state_set gapps "$GZ_NAME"; state_set gapps_img "$GI_SHA"
  ok "$p: $GZ_NAME"
}
abf_gapps_need() {  # bytes of super the --gapps-zip add-on takes (its checked image rounded up to 1 MiB), 0 without one
  local sz=""
  [ -n "$GAPPS_WITH" ] && [ -n "$GZ_SHA" ] \
    && sz=$(awk -v z="$GZ_SHA" -v v="$EU_VER" '$1 == z && $2 == v && $3 == "plain" { print $5; exit }' "$GAPPS_IMAGES_F" 2>/dev/null)
  case "$sz" in ''|*[!0-9]*) echo 0 ;; *) echo $(( (sz + 1048575) / 1048576 * 1048576 )) ;; esac
}
abf_gapps_cable() {  # Lumen OS runs: cable in, restart into fastboot mode for the 'gapps' visit, wait for it there
  local a
  echo
  m connect_projectors_usb_port; echo
  printf '%s ' "$(m press_enter_when_cable)"
  read -r a
  say "$(m restarting_fastboot_mode_about_30)"
  state_set gapps_fastboot "$(date +%Y%m%d-%H%M%S)"
  if ! adbs reboot fastboot >/dev/null 2>&1 && p6_alive; then
    state_set gapps_fastboot ""
    die "$(m adb_reboot_fastboot_failed_3)"
  fi
  adb disconnect "$ADB_T" >/dev/null 2>&1
  p6_fb_wait 180 || die "$(m projector_did_not_appear_cable_4)"
}
abf_gapps_wipe() {  # the 'gapps' visit, z9x_gapps changed: the format of WIPE_PARTS, then the restart
  local p
  say "$(m wiping_data_format "$P_WIPE_FS" "$P_WIPE")"
  for p in $P_WIPE; do
    fb_do format "$p" || die "$(m wipe_failed_projector_stays_2 "$p")"
  done
  fb reboot >/dev/null 2>&1
  state_set gapps_fastboot ""
}
abf_gapps_lumen() {  # connected: Lumen OS of this projector without Google built in, else STOP
  p6_lumen_only
  [ "$(prop ro.z9x.gms)" = 0 ] || die "$(m projector_runs_lumen_os_google_2)"
}
abf_gapps_install() {
  local zip=$GAPPS_ZIP a s have infb=0 p
  abf_refuse_opts
  [ -f "$zip" ] || die "$(m no_file "$zip")"
  check_tools
  gapps_zipcheck "$zip"
  if [ -n "$(state_get gapps_fastboot)" ] && p6_in_fastboot; then
    infb=1
    say "$(m projector_waits_fastboot_mode)"
  else
    abf_adb 60 || die "$(p6_noconn_text)"
    abf_device
    abf_live_gate
    abf_gapps_lumen
    # the same add-on, on: writing it again would only cost the Google apps their data (one start without them)
    have=$(prop sys.z9x.gapps)
    if [ "$have" = ok ] && [ "$(prop sys.z9x.gapps.zip)" = "$GZ_SHA" ]; then
      ok "$(m these_google_services_already "$GZ_NAME")"
      return 0
    fi
    # the Lumen OS on the projector takes this zip as an erofs add-on (its own files, read without root)
    if ! adbs shell cat /system/etc/z9x/gapps_allow.txt 2>/dev/null | tr -d '\r' | grep -q "^zip=$GZ_SHA " \
       || ! adbs shell cat /system/etc/z9x/z9x_gapps.sh 2>/dev/null | tr -d '\r' | grep -q '^# lumen-gapps-fs:.* erofs'; then
      die "$(m lumen_os_projector_not_accept)"
    fi
  fi
  eu_setup
  gi_build "$zip"
  s=$(state_get slot); s=${s#_}
  echo
  m adding_google_services_slot_one "$s" "$(( (GI_SIZE + 1048575) / 1048576 ))" "$GZ_NAME"; echo
  if [ "$infb" = 0 ] && [ "$(state_get step_backup)" != done ] && ask "$(m make_backup_apps_files_first)"; then
    cmd_backup
  fi
  printf '%s ' "$(m type_erase_continue)"
  read -r a
  ABF_GAPPS=1; ABF_GW=0
  if [ "$a" != ERASE ]; then
    [ "$infb" = 0 ] || abf_fb_stop "$(m cancelled)"
    die "$(m cancelled_nothing_written)"
  fi
  ERASE_NOW=1
  [ "$infb" = 1 ] || abf_gapps_cable
  abf_fbd_check
  p=z9x_gapps$ABF_S
  # room: super's free space and what the partition has now
  local need free cur
  need=$(( (GI_SIZE + 1048575) / 1048576 * 1048576 ))
  free=$(super_free); cur=$(gv_size "$p"); cur=$(( ${cur:-0} ))
  [ $(( free + cur )) -ge $(( need + P_RESERVE )) ] || abf_fb_stop "$(m not_enough_free_space_super_3 "$(( (free + cur) / 1048576 ))" "$(( (need + P_RESERVE) / 1048576 ))")"
  say "$(m writing_google_services_mb_about "$p" "$((need / 1048576))")"
  if [ "$cur" -gt 0 ]; then fb_do resize "$p" "$need"; else fb_do create "$p" "$need"; fi \
    || abf_fb_stop "$(m could_not_made "$p")"
  ABF_GW=1
  if ! fb_do flash "$p" "$GI_IMG"; then
    p6_in_fastboot || die "$(m fastboot_mode_ended_while_being_2 "$p")"
    fb_do delete "$p" >/dev/null 2>&1 || die "$(m could_not_written_projector_stays "$p")"
    state_set gapps_fastboot ""
    fb reboot >/dev/null 2>&1
    die "$(m could_not_written_removed_again_2 "$p") $(abf_hint)"
  fi
  ok "$p: $GZ_NAME"
  state_set gapps "$GZ_NAME"; state_set gapps_img "$GI_SHA"
  abf_gapps_wipe
  state_set step_gapps done
  echo
  m done_projector_starts_lumen_os "$(abf_hint)"; echo
}
abf_gapps_remove() {
  local a p infb=0
  abf_refuse_opts
  check_tools
  if [ -n "$(state_get gapps_fastboot)" ] && p6_in_fastboot; then
    infb=1
    say "$(m projector_waits_fastboot_mode)"
  else
    abf_adb 60 || die "$(p6_noconn_text)"
    abf_device
    abf_live_gate
    abf_gapps_lumen
    if [ "$(prop sys.z9x.gapps)" = none ]; then
      ok "$(m slot_has_no_z9x_gapps "$(state_get slot | tr -d _)")"
      return 0
    fi
  fi
  echo
  m removing_google_services_added_one; echo
  printf '%s ' "$(m type_erase_continue)"
  read -r a
  ABF_GAPPS=1; ABF_GW=0
  if [ "$a" != ERASE ]; then
    [ "$infb" = 0 ] || abf_fb_stop "$(m cancelled)"
    die "$(m cancelled_nothing_written)"
  fi
  ERASE_NOW=1
  [ "$infb" = 1 ] || abf_gapps_cable
  abf_fbd_check
  p=z9x_gapps$ABF_S
  if [ -n "$(gv_size "$p")" ]; then
    ABF_GW=1
    fb_do delete "$p" || die "$(m could_not_deleted_projector_stays "$p")"
    ok "$(m deleted "$p")"
  elif [ "$infb" = 0 ]; then
    abf_fb_stop "$(m slot_has_no_nothing_remove_2 "${ABF_S#_}" "$p")"
  fi
  abf_gapps_wipe
  state_set gapps ""; state_set gapps_img ""
  ok "$(m projector_starts_lumen_os_without_2) $(abf_hint)"
}
abf_gapps_status() {
  local v z
  check_tools
  abf_adb 60 || die "$(p6_noconn_text)"
  abf_device
  p6_lumen_only
  v=$(prop sys.z9x.gapps); z=$(prop sys.z9x.gapps.zip)
  case "$v" in
    ok) ok "$(m google_services_added) ($(gapps_line "$z" | cut -d' ' -f2))" ;;
    none|'') say "$(m no_google_services_added_add)" ;;
    bad:slow) warn "$(m last_start_check_google_services)" ;;
    off)
      warn "$(m google_services_were_turned_off)"
      if ask "$(m turn_them_again_projector_restarts)"; then
        p6_root_or_die
        adbs shell rm -rf /metadata/z9x_gapps >/dev/null 2>&1
        adbs reboot >/dev/null 2>&1
        ok "$(m turned_projector_restarts)"
      fi ;;
    *) warn "$(m google_services_added_cannot_used "$v")" ;;
  esac
}
abf_gapps() {
  [ -z "$GAPPS_ZIP" ] || [ "$GAPPS_REMOVE" = 0 ] || die "gapps: --zip or --remove, not both"
  if [ "$GAPPS_REMOVE" = 1 ]; then abf_gapps_remove
  elif [ -n "$GAPPS_ZIP" ]; then abf_gapps_install
  else abf_gapps_status; fi
}
abf_gsf() {  # the Google Services Framework ID of the projector's Lumen OS, for google.com/android/uncertified
  local id
  check_tools
  abf_adb 60 || die "$(p6_noconn_text)"
  abf_device
  p6_lumen_only
  if [ -z "$(adbs shell pm path com.google.android.gsf 2>/dev/null | tr -d '\r')" ]; then
    ok "$(m lumen_os_has_no_google)"
    say "$(m add_them_bash_lumen_install)"
    return 0
  fi
  p6_root_or_die
  id=$(adbs shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db \"select value from main where name='android_id';\"" 2>/dev/null | tr -d '\r')
  adbs unroot >/dev/null 2>&1
  [ -n "$id" ] || die "$(m no_gsf_id_yet_connect)"
  echo "GSF ID: $id"
  echo "$(m register_https_www_google_com)"
}
abf_verify() {  # after the setup, with debugging on again: Lumen OS of this projector started, with its files
  local v bl good=1
  check_tools
  abf_adb 60 || die "$(p6_noconn_text)"
  [ "$(prop sys.boot_completed)" = 1 ] || die "$(m system_has_not_finished_starting)"
  v=$(prop "$(p6_lprop)")
  [ "$v" = "$(p6_lval)" ] || die "$(m projector_not_run_lumen_os_5 "$P_NAME" "$(p6_lprop)" "$v")"
  v=$(prop "$P_VERSION_PROP")
  if [ -n "$v" ]; then ok "Lumen OS $v $(m on_the) $P_NAME $SERIAL"; else warn "$(m missing "$P_VERSION_PROP")"; good=0; fi
  if [ "$BLOBS" = partition ]; then
    bl=$(prop sys.z9x.blobs)
    if [ "$bl" = ok ]; then ok "$(m mediatek_xgimi_files_z9x_blobs "$(state_get slot)")"
    else warn "sys.z9x.blobs=$bl ($(m no_sound_no_mediatek_video))"; good=0; fi
  fi
  if abf_gapps_on; then
    case "$(prop sys.z9x.gapps)" in
      ok) ok "$(m google_services_added_gapps)" ;;
      off|bad:*) warn "$(m google_services_add_not_use "$(prop sys.z9x.gapps)")" ;;
    esac
  fi
  if [ "$good" = 1 ]; then state_set step_verify done; ok "$(m verify_passed)"; fi
}
abf_done() {
  echo
  m done_projector_lumen_os_setup "$P_NAME"; echo
  if [ "$(state_get step_gapps_flash)" = done ]; then
    m google_sign_if_google_refuses; echo
  fi
  echo "  $(m guide): $(p6_guide)"
}
abf_flash() {  # the 'flash' command: a new write, unless one waits in fastboot mode (then it continues there)
  abf_write
  abf_done
}
abf_all() {  # check, backup (asked), vbmeta (asked), blobs, flash: each step once, continued where it stopped
  abf_refuse_opts
  [ -z "$GAPPS_WITH" ] || z9x_gapps_plan   # --gapps-zip: the zip, and that the install saved here can take it
  if ! abf_infb; then
    [ "$(state_get step_check)" = done ] || abf_check
    abf_live_gate
    # --gapps-zip: the add-on image built before the backup question, so that a refusal comes first
    [ -z "$GAPPS_WITH" ] || [ "$(state_get step_flash)" = done ] || z9x_gapps_prepare
    if [ -z "$(state_get step_backup)" ]; then
      if ask "$(m make_backup_apps_files_first)"; then cmd_backup; else state_set step_backup skipped; fi
    fi
    [ "$(state_get step_flash)" = done ] || [ "$(state_get step_vbmeta)" = done ] || abf_vbmeta
    [ "$(state_get step_flash)" = done ] || abf_blobs_ok || abf_blobs
  fi
  [ "$(state_get step_flash)" = done ] || abf_write
  abf_done
}
abf_todo_stop() {  # a profile with TODO values (lib/devices.conf header): nothing is ever written to that projector
  die "$(m lumen_os_cannot_installed_computer "$P_NAME")"
}
abf_main() {  # what every command of a FLOW=ab-fastbootd projector checks first (lib/flows.conf START)
  # a profile that is not finished (TODO values): only 'check' runs (it reads, then stops); every other command
  # stops here, before adb or fastboot
  [ -z "$P_TODO" ] || [ "${CMD:-all}" = check ] || abf_todo_stop
  if abf_eye && [ "$BLOBS" != partition ]; then   # its eye-protection drawer comes from its own files (abf_blobs)
    die "$(m draws_eye_protection_mask_files "$P_NAME")"
  fi
}

# ------------------------------------------------------------------ main
z9x_new_install_if_stock() {  # the saved progress says Lumen OS was written, but the projector runs XGIMI's stock
  # system again (restored from a USB stick since): that progress belongs to the earlier install, start over.
  # The vbmeta step stays recorded: the check below (check_vbmeta_effective) still proves it for this slot.
  [ "$(state_get step_flash)" = done ] || return 0
  local list was=$SERIAL k
  list=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}')
  [ -n "$SERIAL" ] || { [ "$(printf '%s\n' "$list" | grep -c .)" = 1 ] || return 0; SERIAL=$list; }
  if [ -n "$(prop ro.boot.slot_suffix)" ] && [ -z "$(prop ro.z9x.version)" ]; then
    say "$(m projector_runs_stock_system_again)"
    for k in step_check step_backup step_blobs step_flash step_wipe step_verify step_gapps_part step_gapps_fill step_gapps_flash gapps_plan gapps_img image_ok; do
      state_set "$k" ""
    done
  fi
  SERIAL=$was
}

flow_step() {  # flow KEY -> its step, a function of this script with its arguments (lib/flows.conf)
  local step; step=$(flow_get "$1" "$2")
  [ -z "$step" ] || declare -F "${step%% *}" >/dev/null || die "internal: lib/flows.conf: $1.$2"
  printf '%s' "$step"
}
flow_run() {  # the command of this projector's flow, as lib/flows.conf says
  local cmd=${CMD:-all} step need v
  step=$(flow_step "$P_FLOW" START); [ -z "$step" ] || $step
  case " $(flow_get "$P_FLOW" NOT_NEEDED) " in *" $cmd "*) die "$(m not_needed_nothing_done "$cmd" "$P_NAME")" ;; esac
  case " $(flow_get "$P_FLOW" NOT_YET) " in *" $cmd "*) not_yet "$cmd" ;; esac
  if need=$(pair_get "$(flow_get "$P_FLOW" NEEDS)" "$cmd"); then
    v=$(p_var "$need"); [ -n "${!v}" ] || not_yet "$cmd"
  fi
  case "$cmd" in *[!a-z]*) step="" ;; *) step=$(flow_step "$P_FLOW" "CMD_$(printf '%s' "$cmd" | tr 'a-z' 'A-Z')") ;; esac
  [ -n "$step" ] || die "unknown command $CMD (see --help)"
  case " $(flow_get "$P_FLOW" TOOLS) " in *" $cmd "*) pt_setup ;; esac
  $step
}

cmd_all() {
  z9x_new_install_if_stock
  [ -z "$GAPPS_WITH" ] || z9x_gapps_plan
  [ "$(state_get step_check)" = done ] || cmd_check
  if [ -n "$GAPPS_WITH" ] && [ "$(state_get image_gms)" != 0 ]; then
    die "$(m image_has_google_services_built)"
  fi
  [ -z "$GAPPS_WITH" ] || [ "$(state_get step_flash)" = done ] || z9x_gapps_prepare   # before the backup question: a refusal comes first
  if [ "$(state_get step_backup)" != done ]; then
    if ask "$(m make_backup_apps_files_first)"; then cmd_backup; else state_set step_backup skipped; fi
  fi
  [ "$BLOBS" != partition ] || [ "$(state_get step_blobs)" = done ] || cmd_blobs
  [ "$(state_get step_vbmeta)" = done ] || cmd_vbmeta
  [ "$(state_get step_flash)" = done ] || cmd_flash
  if [ "$(state_get step_gapps_part)" = done ] && [ -z "$(state_get step_wipe)" ]; then z9x_gapps_finish; fi
  # After a wipe USB debugging is off on the fresh system, so waiting for adb only hangs for 5 minutes:
  # the install is finished, the projector shows its setup. 'verify' stays available as its own command.
  if [ "$(state_get step_wipe)" = done ]; then
    state_set step_verify skipped:wiped
  else
    [ "$(state_get step_verify)" = done ] || cmd_verify
  fi
  echo
  if [ "$(state_get image_gms)" = 0 ] && [ "$(state_get step_gapps_fill)" != done ] && [ "$(state_get step_gapps_flash)" != done ]; then
    m done_projector_choose_language_wi; echo
  else
    m done_projector_choose_language_wi_2; echo
  fi
}

CMD=""
while [ $# -gt 0 ]; do
  case "$1" in
    --image) IMG=$2; shift ;;
    --image=*) IMG=${1#*=} ;;
    --serial) SERIAL=$2; shift ;;
    --keep-data) KEEP_DATA=1 ;;
    --wipe=*) WIPE=${1#*=} ;;
    --lang) LANG_UI=$2; shift ;;
    --lang=*) LANG_UI=${1#*=} ;;
    --yes) YES=1 ;;
    --blobs=*) BLOBS=${1#*=} ;;
    --no-blobs) BLOBS=none ;;
    --unsigned-image) UNSIGNED=1 ;;
    --zip) GAPPS_ZIP=$2; shift ;;
    --zip=*) GAPPS_ZIP=${1#*=} ;;
    --remove) GAPPS_REMOVE=1 ;;
    --gapps-zip) GAPPS_WITH=${2-}; shift ;;
    --gapps-zip=*) GAPPS_WITH=${1#*=} ;;
    --ip) IP=${2-}; IP_GIVEN=1; shift ;;
    --ip=*) IP=${1#*=}; IP_GIVEN=1 ;;
    --device) DEVICE_OPT=${2-}; shift ;;
    --device=*) DEVICE_OPT=${1#*=} ;;
    --platform-tools) PT_OPT=${2-}; shift ;;
    --platform-tools=*) PT_OPT=${1#*=} ;;
    --erofs-utils) EU_OPT=${2-}; shift ;;
    --erofs-utils=*) EU_OPT=${1#*=} ;;
    -h|--help) awk 'NR == 1 { next } /^set -u$/ { exit } { print }' "$0"; exit 0 ;;
    -*) die "unknown option $1" ;;
    *) if [ "$CMD" = stick ] && [ -z "$STICK" ]; then STICK=$1; else CMD=$1; fi ;;
  esac
  shift
done
[ "$MSG_LANG" = "$LANG_UI" ] || msg_load
case "$WIPE" in format|factory-reset) ;; *) die "--wipe=format|factory-reset" ;; esac
case "$BLOBS" in partition|embedded|none) ;; *) die "--blobs=partition|embedded|none (--no-blobs)" ;; esac
case "${CMD:-all}" in gapps) ;; *) [ -z "$GAPPS_ZIP" ] && [ "$GAPPS_REMOVE" = 0 ] || die "--zip / --remove belong to 'gapps'" ;; esac
[ -z "$DEVICE_OPT" ] || [ ! -f "$DEVCONF" ] || profile_is "$DEVICE_OPT" || die "$(device_opt_text)"
if [ -n "$GAPPS_WITH" ]; then   # one pass, one wipe: the full install only
  [ -z "$CMD" ] || die "$(m gapps_zip_belongs_full_install)"
  [ "$KEEP_DATA" = 0 ] || die "$(m gapps_zip_installs_one_data)"
  [ "$WIPE" = format ] || die "$(m gapps_zip_wipes_data_itself)"
fi
if [ "$IP_GIVEN" = 1 ]; then
  case "$IP" in ''|*[!A-Za-z0-9.:_-]*|:*|*:) die "$(m ip_not_address_example_ip "$IP")" ;; esac
  case "$IP" in *:*) ADB_T=$IP ;; *) ADB_T=$IP:5555 ;; esac
fi
case "${CMD:-all}" in   # the saved progress only: no projector, no profile needed
  status)
    shown=0
    for id in $(profile_ids); do   # the projector whose progress is the root folder first, as it is, the others by name
      f=$(state_file_of "$id")
      case "$DEVICE_OPT" in ''|"$id") [ -f "$f" ] || continue ;; *) continue ;; esac
      if [ "$f" = "$STATE_DIR/state" ]; then cat "$f"; else echo "$(dev_name "$id"):"; sed 's/^/  /' "$f"; fi
      shown=1
    done
    [ "$shown" = 1 ] || echo "$(m no_saved_progress)"
    exit 0 ;;
  reset)
    if [ -z "$DEVICE_OPT" ]; then rm -rf "$STATE_DIR"
    elif [ "$(state_dir_of "$DEVICE_OPT")" = "$STATE_DIR" ]; then   # every other projector's folder stays
      keep=""; for id in $(profile_ids); do [ "$id" = "$DEVICE_OPT" ] || keep="$keep ! -name $id"; done
      [ -d "$STATE_DIR" ] && find "$STATE_DIR" -mindepth 1 -maxdepth 1 $keep -exec rm -rf {} +
    else rm -rf "${STATE_DIR:?}/$DEVICE_OPT"; fi   # a projector of lib/devices.conf (checked above)
    echo "$(m progress_forgotten)"
    exit 0 ;;
esac
resolve_device
SERIAL_FROM_STATE=0
if [ -z "$SERIAL" ]; then SERIAL=$(state_get serial); [ -n "$SERIAL" ] && SERIAL_FROM_STATE=1; fi
case "${CMD:-all}" in all|check|flash) release_guard ;; esac
flow_run
