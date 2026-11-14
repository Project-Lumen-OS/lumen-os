#!/bin/sh
# z9x_gate.sh: check G of the design notes (2.5). Proves that Play 6 work left the Z9X build of
# Lumen OS unchanged: every pinned Z9X APK still rebuilds byte for byte from its sources, the synced
# copies match apps/common, and the Z9X language and HAL caller checks pass.
#
#   sh tools/play6/z9x_gate.sh [--full] [--record]        (POSIX sh: sh, dash or bash all run it)
#
#   --full     rebuild every pinned APK of overlay/apps_v1 (default: only those whose inputs changed since
#              the recorded green run; all of them when nothing is recorded yet)
#   --record   on green, write build/play6/z9x_inputs.ok (sha256 of every Z9X input of this run). Only at
#              setup step 0, or after the maintainer's explicit OK. It never records over a red run: to
#              re-record after the maintainer's OK, keep the old record under a dated name
#              (z9x_inputs.ok.YYYYMMDD-HHMMSS) and run --full --record (the step 0 path).
#
# Nothing expected is written here: pins (overlay/apps_v1/*.apk, PINS.sha256), versions (aapt2 dump badging
# of each pinned APK) and the input list are read at run time. The Z9X tree is only read: every build runs
# in a scratch dir under TMPDIR (BUILD_DIR set, so no apps/Z9x*/build is touched). On red the script exits 1
# with the report and never edits, reverts or re-records anything; the scratch dir is kept for a look.
# Z9X inputs: apps/Z9x*/{AndroidManifest.xml,src,res,assets,lib,stub,jni}, apps/common, apps/core (the modules a Z9X
# recipe devices/horse_popular/app/*.layers assembles, with tools/devices/assemble_app.py), apps/i18n,
# apps/build_apk.sh, apps/check_hal_callers.py, apps/check_global_keys.py, apps/sdk, overlay/v1,
# overlay/apps_v1, overlay/debug (Z9X DIAG images; without lumen_escape.*, a Play 6 test-build helper),
# overlay/v65 (tools/lumen_v1.sh compares z9x_setup.rc with it), tools/lumen_v1.sh, tools/compose_tv.py,
# tools/lumen, tools/sign, tools/ota; without __pycache__/, *.pyc and .DS_Store.
# Red without a rebuild, because no APK rebuild can prove them (Z9X work in progress, for the maintainer to pin
# or re-record): a changed image input (overlay/v1, overlay/debug, overlay/v65, tools/...), a changed
# apps/Z9x*/jni of a pinned app (build_apk.sh packs the prebuilt lib/ and never compiles jni/), and any
# change in overlay/apps_v1 (a new pin is the maintainer's to confirm; a changed APK is still rebuilt, so the
# report shows whether it matches its source).
# Next-release builds (tools/play6/z9x_next.tsv, itself a gate input): a pinned app whose sources already hold reviewed
# commits for the next Z9X release no longer rebuilds its pin from HEAD. Its row names the commit that recorded the
# pin and the sha256 HEAD builds now. Such an app is green only when HEAD builds exactly that sha256 AND the pin
# rebuilds byte for byte from the named commit (git archive of apps/ there, the same build), so the released APK
# stays proven and any further change of its sources is red again. A row is added or changed only with the maintainer's
# OK, followed by a re-record.
# Log of the last run: build/play6/z9x_gate.log.
set -u
FULL=0; RECORD=0
for a in "$@"; do
  case $a in
    --full) FULL=1 ;;
    --record) RECORD=1 ;;
    *) echo "usage: z9x_gate.sh [--full] [--record]" >&2; exit 2 ;;
  esac
done
G=$(cd "$(dirname "$0")/../.." && pwd)
cd "$G" || exit 2
OK_FILE=build/play6/z9x_inputs.ok
LOG=build/play6/z9x_gate.log
mkdir -p build/play6
: > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
RED=0
red() { RED=1; say "RED: $*"; }

ANDROID_SDK=${ANDROID_SDK:-$HOME/Library/Android/sdk}
AAPT2=${BUILD_TOOLS:-$ANDROID_SDK/build-tools/36.0.0}/aapt2
[ -x "$AAPT2" ] || { say "z9x_gate: missing $AAPT2"; exit 2; }
# the pins are built with JDK 21 (apps/sdk/system-modules-android-34-jdk21): another javac compiles other bytecode
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || { say "z9x_gate: needs JDK 21 (/usr/libexec/java_home -v 21)"; exit 2; }
else   # a Linux build host: JAVA_HOME must already be a JDK 21 (~/lumen-tools/env.sh)
  "${JAVA_HOME:-/nonexistent}/bin/javac" -version 2>&1 | grep -q '^javac 21\.' || { say "z9x_gate: needs JAVA_HOME = JDK 21"; exit 2; }
fi
export JAVA_HOME
W=$(mktemp -d "${TMPDIR:-/tmp}/z9x_gate.XXXXXX") || exit 2

say "z9x_gate: $(date '+%Y-%m-%d %H:%M:%S') full=$FULL record=$RECORD scratch=$W"

# ---------------------------------------------------------------- 1. inputs
list_inputs() {
  {
    for a in apps/Z9x*/; do
      a=${a%/}
      [ -f "$a/AndroidManifest.xml" ] && echo "$a/AndroidManifest.xml"
      for s in src res assets lib stub jni; do
        [ -d "$a/$s" ] && find "$a/$s" -type f
      done
    done
    find apps/common apps/core apps/i18n apps/sdk apps/LumenBridge overlay/v1 overlay/apps_v1 overlay/debug overlay/v65 \
      tools/lumen tools/sign tools/ota devices/horse_popular/app -type f
    for f in apps/build_apk.sh apps/check_hal_callers.py apps/check_global_keys.py tools/lumen_v1.sh tools/compose_tv.py \
        tools/play6/z9x_next.tsv tools/devices/assemble_app.py tools/devices/devices.py devices/horse_popular/menu.json; do
      echo "$f"
    done
  } | grep -v -E '(^|/)__pycache__/|\.pyc$|(^|/)\.DS_Store$|^overlay/debug/lumen_escape\.' | LC_ALL=C sort -u
}
hash_inputs() { # out
  list_inputs > "$W/list"
  while IFS= read -r p; do [ -f "$p" ] || echo "missing  $p"; done < "$W/list" > "$W/missing"
  sed 's/^missing  //' "$W/missing" > "$W/missing.paths"
  grep -v -x -F -f "$W/missing.paths" "$W/list" 2>/dev/null | tr '\n' '\0' \
    | xargs -0 shasum -a 256 | cat - "$W/missing" | LC_ALL=C sort -k2 > "$1"
}
hash_inputs "$W/inputs.now"
say "inputs: $(wc -l < "$W/inputs.now" | tr -d ' ') files"
if [ -s "$W/missing" ]; then
  while IFS= read -r l; do red "input missing: ${l#missing  }"; done < "$W/missing"
fi

APKS=$(cd overlay/apps_v1 && ls *.apk 2>/dev/null | sed 's/\.apk$//' | LC_ALL=C sort)
is_pinned() { printf '%s\n' "$APKS" | grep -qx "$1"; }
REBUILD=""
add() { case " $REBUILD " in *" $1 "*) ;; *) REBUILD="$REBUILD $1" ;; esac; }

if [ -f "$OK_FILE" ]; then
  grep -v '^#' "$OK_FILE" | LC_ALL=C sort -k2 > "$W/inputs.ok"
  # changed: M modified, A added, D removed (path in column 2)
  join -1 2 -2 2 -a 1 -a 2 -e NONE -o 0,1.1,2.1 "$W/inputs.ok" "$W/inputs.now" \
    | awk '$2 != $3 { s = ($2 == "NONE") ? "A" : (($3 == "NONE") ? "D" : "M"); print s, $1 }' > "$W/changed"
  NCH=$(wc -l < "$W/changed" | tr -d ' ')
  say "changed since the recorded green run ($(head -1 "$OK_FILE" | sed 's/^# //')): $NCH"
  ALL=0
  while read -r st p; do
    case $p in
      apps/common/*|apps/i18n/*|apps/sdk/*|apps/build_apk.sh|apps/check_hal_callers.py|apps/check_global_keys.py)
        say "  $st $p  (shared build input: every pinned APK is rebuilt)"; ALL=1 ;;
      overlay/apps_v1/*.apk)
        n=${p#overlay/apps_v1/}; n=${n%.apk}
        if [ "$st" = D ]; then say "  $st $p"; red "pinned APK $n.apk removed"
        else say "  $st $p  (rebuild $n, for the report)"; add "$n"
          red "pin changed since the recorded green run: $p (the owner confirms the new pin, then re-record)"; fi ;;
      overlay/apps_v1/*)
        say "  $st $p  (pin list: every pinned APK is rebuilt, for the report)"; ALL=1
        red "pin list changed since the recorded green run: $p (the owner confirms it, then re-record)" ;;
      apps/core/*|devices/horse_popular/app/*|devices/horse_popular/menu.json|tools/devices/assemble_app.py|tools/devices/devices.py)
        say "  $st $p  (build input of the assembled apps: every pinned APK is rebuilt)"; ALL=1 ;;
      apps/LumenBridge/*)
        say "  $st $p  (rebuild every LumenBridge*)"
        for n in $APKS; do case $n in LumenBridge*) add "$n" ;; esac; done ;;
      apps/Z9x*/jni/*)
        n=${p#apps/}; n=${n%%/*}
        if is_pinned "$n"; then say "  $st $p"
          red "native source of $n changed, no rebuild can prove it (build_apk.sh never compiles jni/): $p"
        else say "  $st $p  (not a pinned APK, not in the image)"; fi ;;
      apps/Z9x*/*)
        n=${p#apps/}; n=${n%%/*}
        if is_pinned "$n"; then say "  $st $p  (rebuild $n)"; add "$n"
        else say "  $st $p  (not a pinned APK, not in the image)"; fi ;;
      *)
        say "  $st $p"; red "image input changed, no rebuild can prove it: $p" ;;
    esac
  done < "$W/changed"
  [ $ALL = 1 ] && FULL=1
else
  say "no recorded green run ($OK_FILE): every pinned APK is rebuilt"
  [ $RECORD = 1 ] || red "nothing recorded yet: run --full --record (plan step 0, or after the owner's OK)"
  FULL=1
fi
[ $FULL = 1 ] && REBUILD=$APKS

# ---------------------------------------------------------------- 2. pins and rebuilds
pin_of() { # name -> sha256 from PINS.sha256 (empty if not listed)
  grep -v '^#' overlay/apps_v1/PINS.sha256 2>/dev/null | awk -v f="$1.apk" '$2 == f { print $1 }' | tail -1
}
next_of() { # name column -> the field of its tools/play6/z9x_next.tsv row (2 pin commit, 3 accepted HEAD sha256)
  [ -f tools/play6/z9x_next.tsv ] || return 0
  grep -v '^#' tools/play6/z9x_next.tsv | awk -F'\t' -v n="$1" -v c="$2" '$1 == n { print $c }' | tail -1
}
# the source dir build_apk.sh builds for app $2 of the tree $1: its Z9X recipe assembled into $3 (apps/core modules,
# as tools/devices/build_apps.sh builds the image's APK), or apps/<name> itself when it has no recipe there
src_of() { # tree name scratch
  r=$(cd "$1" && [ -f tools/devices/assemble_app.py ] && python3 -B -c 'import sys; sys.path.insert(0, "tools/devices")
import assemble_app as a
r = a.find_recipe(".", "horse_popular", sys.argv[1])
print(r if r and a.parse(".", r)["identical"] != "allow" else "")' "$2") || return 1
  if [ -z "$r" ]; then echo "$1/apps/$2"; return 0; fi
  ( cd "$1" && python3 -B tools/devices/assemble_app.py "$r" --out "$3" --quiet ) > "$3.log" 2>&1 || return 1
  echo "$3"
}
pin_at_commit() { # name commit version_code version_name tmdb_key -> sha256 of the build of apps/<name> at commit
  d=$W/at_$2
  if [ ! -d "$d" ]; then
    mkdir -p "$d" && git archive "$2" apps tools/devices devices/horse_popular | tar -x -C "$d" || return 1
    # ignored inputs of a pinned app (the AirPlay native libs) come from this checkout, as for the HEAD build
    [ -d apps/Z9xAirPlay/lib ] && [ -d "$d/apps/Z9xAirPlay" ] && cp -R apps/Z9xAirPlay/lib "$d/apps/Z9xAirPlay/"
  fi
  src=$(src_of "$d" "$1" "$W/at_tree_$1") || return 1
  ( cd "$d" && BUILD_DIR="$W/at_$1" VERSION_CODE="$3" VERSION_NAME="$4" TMDB_KEY_FILE="$5" \
      sh apps/build_apk.sh "$src" "$W/$1.at.apk" ) > "$W/$1.at.log" 2>&1 || return 1
  shasum -a 256 "$W/$1.at.apk" | cut -d' ' -f1
}
for n in $APKS; do
  want=$(pin_of "$n")
  [ -z "$want" ] && continue
  have=$(shasum -a 256 "overlay/apps_v1/$n.apk" | cut -d' ' -f1)
  [ "$want" = "$have" ] || red "overlay/apps_v1/$n.apk is $have, PINS.sha256 says $want"
done
NB=0
for n in $REBUILD; do
  pin=overlay/apps_v1/$n.apk
  case $n in
    LumenBridge*)   # the 1.0.8 bridges: one source (apps/LumenBridge), one APK per old name
      if [ ! -d "$W/bridges" ] && ! sh apps/LumenBridge/build_bridges.sh "$W/bridges" z9x > "$W/bridges.log" 2>&1; then
        red "$n: build_bridges.sh failed ($W/bridges.log)"; continue
      fi
      b=$(printf '%s' "${n#LumenBridge}" | tr 'A-Z' 'a-z')
      NB=$((NB + 1))
      got=$(shasum -a 256 "$W/bridges/LumenBridge_$b.apk" 2>/dev/null | cut -d' ' -f1)
      have=$(shasum -a 256 "$pin" | cut -d' ' -f1)
      if [ -n "$got" ] && [ "$got" = "$have" ]; then say "  $n: identical to the pin ($got)"
      else red "$n: rebuilt ${got:-nothing}, pinned $have"; fi
      continue ;;
  esac
  if [ ! -f "apps/$n/AndroidManifest.xml" ]; then red "$n: no source tree apps/$n"; continue; fi
  badge=$("$AAPT2" dump badging "$pin" 2>/dev/null | head -1)
  vc=$(printf '%s' "$badge" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")
  vn=$(printf '%s' "$badge" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")
  if [ -z "$vc" ] || [ -z "$vn" ]; then red "$n: cannot read the version of $pin"; continue; fi
  out=$W/$n.apk
  # Lumen Home 1.0.5 carries the TMDB key as an asset (build_apk.sh TMDB_KEY_FILE; the key never enters the
  # tree): a pin with that asset is rebuilt with the same key file, so it can match byte for byte.
  key=
  if [ "$n" = Z9xHome ] && unzip -l "$pin" assets/tmdb_key >/dev/null 2>&1; then
    key=${TMDB_KEY_FILE:-$HOME/.lumen-keys/tmdb.txt}
    [ -r "$key" ] || { red "$n: the pin carries a TMDB key, but $key is not readable"; continue; }
  fi
  if ! src=$(src_of "$G" "$n" "$W/tree_$n"); then red "$n: its recipe does not assemble ($W/tree_$n.log)"; continue; fi
  if ! BUILD_DIR="$W/$n" VERSION_CODE="$vc" VERSION_NAME="$vn" TMDB_KEY_FILE="$key" sh apps/build_apk.sh "$src" "$out" > "$W/$n.log" 2>&1; then
    red "$n: build failed ($W/$n.log)"; tail -5 "$W/$n.log" | sed 's/^/    /' | tee -a "$LOG"; continue
  fi
  NB=$((NB + 1))
  got=$(shasum -a 256 "$out" | cut -d' ' -f1)
  have=$(shasum -a 256 "$pin" | cut -d' ' -f1)
  want=$(pin_of "$n")
  nxt=$(next_of "$n" 3); ncm=$(next_of "$n" 2)
  if [ "$got" = "$have" ] && { [ -z "$want" ] || [ "$got" = "$want" ]; }; then
    say "  $n $vn/$vc: identical to the pin ($got)${want:+, PINS.sha256 too}"
    [ -n "$nxt" ] && red "$n: HEAD rebuilds the pin again, its tools/play6/z9x_next.tsv row is stale (remove it, re-record)"
  elif [ -n "$nxt" ] && [ "$got" = "$nxt" ]; then
    at=$(pin_at_commit "$n" "$ncm" "$vc" "$vn" "$key")
    if [ -n "$at" ] && [ "$at" = "$have" ] && { [ -z "$want" ] || [ "$at" = "$want" ]; }; then
      say "  $n $vn/$vc: HEAD builds the accepted next-release APK ($got, z9x_next.tsv); the pin rebuilds from $ncm ($at)"
    else
      red "$n $vn/$vc: HEAD builds the accepted next-release APK, but $ncm builds ${at:-nothing ($W/$n.at.log)}, pinned $have"
    fi
  else
    if unzip -l "$pin" classes.dex >/dev/null 2>&1 || unzip -l "$out" classes.dex >/dev/null 2>&1; then
      a=$(unzip -p "$pin" classes.dex 2>/dev/null | shasum -a 256 | cut -d' ' -f1)
      b=$(unzip -p "$out" classes.dex 2>/dev/null | shasum -a 256 | cut -d' ' -f1)
      if [ "$a" = "$b" ]; then what="resources / signature (classes.dex identical)"; else what="code (classes.dex differs)"; fi
    else
      what="resources / signature (no code)"
    fi
    red "$n $vn/$vc: rebuilt $got, pinned $have${want:+, PINS.sha256 $want}${nxt:+, accepted next-release $nxt}: $what"
  fi
done
say "rebuilt: $NB of $(printf '%s\n' $REBUILD | grep -c .) planned ($(printf '%s\n' $APKS | grep -c .) pinned APKs)"

# ---------------------------------------------------------------- 3. synced copies
sync_check() { # app items...
  out=$(sh apps/common/sync.sh --check "$@" 2>&1); rc=$?
  if [ $rc = 0 ]; then say "  sync --check $*: OK"; else red "sync --check $*: $out"; fi
}
PITEMS=hal
[ -f apps/common/projector/FILES ] && PITEMS="hal projector"
sync_check apps/Z9xProjector $PITEMS
# the Play 6 projector app holds no shared copies since the design notes: it is assembled from
# devices/dragon_healthy/app/Play6Projector.layers, which refuses a copy of a shared file
out=$(python3 -B tools/devices/assemble_app.py --device dragon_healthy --app Play6Projector 2>&1); rc=$?
if [ $rc = 0 ]; then say "  assemble Play6Projector: OK"; else red "assemble Play6Projector: $out"; fi
if [ -d apps/common/overlay ]; then
  sync_check apps/Z9xFrameworkKeysOverlay keys
  sync_check apps/Play6FrameworkOverlay keys
else
  say "  sync item keys: not set up yet (apps/common/overlay, plan step 5)"
fi
[ -f apps/common/projector/FILES ] || say "  sync item projector: not set up yet (apps/common/projector/FILES, plan step 5)"

# ---------------------------------------------------------------- 4. languages, 5. Z9X HAL callers
out=$(python3 -B apps/i18n/gen.py --check-only 2>&1); rc=$?
if [ $rc = 0 ]; then say "  gen.py --check-only: $(printf '%s' "$out" | tail -1)"; else red "gen.py --check-only: $out"; fi
out=$(python3 -B apps/check_hal_callers.py apps/Z9xProjector/src 2>&1); rc=$?
if [ $rc = 0 ]; then say "  $out"; else red "$out"; fi

# ---------------------------------------------------------------- inputs unchanged during the run
hash_inputs "$W/inputs.end"
if ! cmp -s "$W/inputs.now" "$W/inputs.end"; then
  red "Z9X inputs changed during the run:"
  diff "$W/inputs.now" "$W/inputs.end" | sed 's/^/    /' | tee -a "$LOG"
fi

if [ $RED = 1 ]; then
  say "z9x_gate: RED (nothing edited, reverted or recorded; scratch kept: $W)"
  exit 1
fi
if [ $RECORD = 1 ]; then
  { echo "# z9x_gate.sh --record $(date '+%Y-%m-%d %H:%M:%S'), full=$FULL, $(wc -l < "$W/inputs.now" | tr -d ' ') inputs"
    cat "$W/inputs.now"; } > "$OK_FILE.tmp" && mv "$OK_FILE.tmp" "$OK_FILE"
  say "recorded: $OK_FILE"
fi
rm -rf "$W"
say "z9x_gate: GREEN"
exit 0
