#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Golden test of the installers' messages (installer/lib/messages.tsv): the installer suites print the same thing,
# byte for byte, before and after a change that moves texts around.
#
#   bash tools/devices/test/installer_msgs_golden.sh capture REV|.|DIR OUTDIR [SUITE...]
#   bash tools/devices/test/installer_msgs_golden.sh compare OUTDIR_A OUTDIR_B
#
# capture: copies the tree of git revision REV (or '.', the working tree with its uncommitted changes) into a temp
# dir, makes every suite there keep a copy of each installer output ($C/out after each run of lumen-install.sh /
# .ps1), runs the suites and stores the outputs under OUTDIR/<suite>/NNNNN (the suite's temp dir written as <W>, the
# clock of each line as HH:MM:SS) and the suite's own log as OUTDIR/<suite>.log. The suites run one after another;
# SUITE names a subset (default: every installer suite of the tree; the ps1 ones say 'skip' without pwsh).
# (installer_x50_image.sh needs a real X50 image folder: run it on its own.)
# Each capture is the output, its exit status and the fake adb and fastboot call logs of the suite at that moment ($FB/adb.log,
# $FB/fb.log), so a refactor proves both the words the user saw and the calls made to the projector.
# compare: every captured output of A must equal the one of B with the same number (the clock, dates in file names,
# hex values of 16 or more digits, mktemp names and the name of the temp copy of the tree written alike on both
# sides); exit status 0 only then. A suite's own pass/fail is its log.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
SUITES_DEFAULT="tools/ota/test/installer_edition.sh tools/ota/test/installer_blobs.sh tools/ota/test/installer_gapps.sh
tools/ota/test/installer_profiles.sh tools/ota/test/installer_play6.sh tools/ota/test/installer_play6_gapps.sh
tools/devices/test/installer_hb.sh tools/devices/test/installer_x50.sh tools/devices/test/installer_x50_blobs.sh
tools/devices/test/installer_ps1_hb.sh tools/ota/test/installer_ps1_play6.sh
tools/ota/test/installer_ps1_gapps.sh"

# the capture hook, put right after every line that runs the installer into $C/out
HOOK='__rc=$?; [ -z "${LUMEN_TEST_CAPTURE:-}" ] || { __n=$(ls "$LUMEN_TEST_CAPTURE" | wc -l | tr -d " "); { cat "$C/out"; echo "--- exit $__rc"; for __l in adb fb; do [ ! -f "${FB:-/nonexistent}/$__l.log" ] || { echo "--- $__l.log"; cat "$FB/$__l.log"; }; done; } | sed -e "s|$W|<W>|g" -e "s/^[0-9][0-9]:[0-9][0-9]:[0-9][0-9] /HH:MM:SS /" > "$LUMEN_TEST_CAPTURE/$(printf %05d $((__n + 1)))"; }; (exit $__rc)'

hook_suite() {  # file: insert HOOK after each installer run that writes "$C/out"
  awk -v hook="$HOOK" '
    { print }
    /lumen-install\.(sh|ps1)" .*> "\$C\/out" 2>&1$/ { print "    " hook }
  ' "$1" > "$1.hooked" && mv "$1.hooked" "$1"
  # the --help runs: "... > "$C/out" 2>&1; RC=$?" on one line
  sed -e 's|> "\$C/out" 2>&1; RC=\$?$|> "$C/out" 2>\&1; '"$(printf '%s' "$HOOK" | sed 's/[&|]/\\&/g')"'; RC=$?|' "$1" > "$1.hooked" && mv "$1.hooked" "$1"
}

case "${1:-}" in
  capture)
    [ $# -ge 3 ] || { echo "usage: $0 capture REV|. OUTDIR [SUITE...]"; exit 2; }
    rev=$2; out=$3; shift 3
    mkdir -p "$out" && out=$(cd "$out" && pwd) || exit 1
    T=$(mktemp -d "${TMPDIR:-/tmp}/lumen-golden.XXXXXX") && T=$(cd "$T" && pwd) || exit 1
    trap 'rm -rf "$T"' EXIT
    if [ "$rev" = . ]; then
      (cd "$G" && git ls-files -z -co --exclude-standard | tar --null -T - -cf - 2>/dev/null) | tar -xf - -C "$T" || exit 1
    elif [ -d "$rev" ]; then   # a plain copy of a tree (a builder without the git repository)
      cp -R "$rev/." "$T/" || exit 1
    else
      (cd "$G" && git archive "$rev") | tar -xf - -C "$T" || exit 1
    fi
    [ -d "$G/installer/tools/erofs-utils" ] && { mkdir -p "$T/installer/tools"; cp -R "$G/installer/tools/erofs-utils" "$T/installer/tools/"; }
    suites=${*:-$SUITES_DEFAULT}
    for s in $suites; do
      [ -f "$T/$s" ] || { echo "no $s in $rev"; continue; }
      hook_suite "$T/$s"
      n=$(basename "$s" .sh)
      rm -rf "${out:?}/$n"; mkdir -p "$out/$n"
      ( cd "$T" && LUMEN_TEST_CAPTURE="$out/$n" bash "$s" > "$out/$n.log" 2>&1 ); echo "rc=$?" >> "$out/$n.log"
      echo "$n: $(ls "$out/$n" | wc -l | tr -d ' ') outputs, $(tail -n 1 "$out/$n.log")"
    done ;;
  compare)
    [ $# = 3 ] || { echo "usage: $0 compare OUTDIR_A OUTDIR_B"; exit 2; }
    a=$2; b=$3; bad=0
    T=$(mktemp "${TMPDIR:-/tmp}/lumen-golden-cmp.XXXXXX") || exit 1
    trap 'rm -f "$T" "$T.a" "$T.b"' EXIT
    norm() { sed -E -e 's/[0-9]{8}-[0-9]{6}/<date>/g' -e 's/[0-9]{2}:[0-9]{2}:[0-9]{2}/HH:MM:SS/g' -e 's/[0-9a-f]{16,}/<hex>/g' \
      -e 's/lumen-golden\.[A-Za-z0-9]+/lumen-golden.X/g' -e 's/(tmp|pack|packchk)\.[A-Za-z0-9]{6,}/\1.X/g' \
      -e 's/lumen-[0-9]{14}-[0-9]+/lumen-<stamp>/g' "$1"; }
    for d in "$a"/*/; do
      n=$(basename "$d")
      [ -d "$b/$n" ] || { echo "FAIL $n: not captured in $b"; bad=1; continue; }
      na=$(ls "$a/$n" | wc -l | tr -d ' '); nb=$(ls "$b/$n" | wc -l | tr -d ' ')
      [ "$na" = "$nb" ] || { echo "FAIL $n: $na outputs in A, $nb in B"; bad=1; }
      [ "$na" -gt 0 ] || { echo "ok   $n: runs no installer into \$C/out (its log only)"; continue; }
      diffs=0
      for f in "$a/$n"/*; do
        g=$b/$n/$(basename "$f")
        [ -f "$g" ] || { diffs=$((diffs + 1)); continue; }
        # the clock anywhere in a line (a prompt runs into the next line's time) and long hex values (the sha256 of
        # test files built anew on every run) are not words the user reads
        norm "$f" > "$T.a"; norm "$g" > "$T.b"
        cmp -s "$T.a" "$T.b" || { diffs=$((diffs + 1)); [ "$diffs" -le 3 ] && diff "$T.a" "$T.b" | head -n 8 | sed "s|^|  $n/$(basename "$f"): |"; }
      done
      if [ "$diffs" = 0 ]; then echo "ok   $n: $na outputs equal"; else echo "FAIL $n: $diffs of $na outputs differ"; bad=1; fi
    done
    exit $bad ;;
  *) echo "usage: $0 capture REV|. OUTDIR [SUITE...] | compare OUTDIR_A OUTDIR_B"; exit 2 ;;
esac
