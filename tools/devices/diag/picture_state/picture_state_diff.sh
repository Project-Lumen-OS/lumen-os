#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# picture_state_diff.sh: what differs between two dumps of picture_state.sh (for example stock and Lumen OS).
#   sh picture_state_diff.sh STOCK.txt LUMEN.txt
# Compares the stable sections ([hal] [props] [surfaceflinger] [settings] [xgimiconfig]) one by one and prints
# the sections that are only informative ([meta] [hal-volatile] [context]) side by side at the end.
# Lines starting with '<' are of the first file, '>' of the second. Exit 0 when the stable sections are equal.
set -u
[ $# = 2 ] || { echo "usage: picture_state_diff.sh A.txt B.txt" >&2; exit 2; }
for f in "$1" "$2"; do
  grep -q '^# picture_state 1' "$f" || { echo "$f: not a picture_state dump" >&2; exit 2; }
  grep -q '^# done' "$f" || echo "WARNING: $f is incomplete" >&2
done
W=$(mktemp -d "${TMPDIR:-/tmp}/picture_state_diff.XXXXXX")
trap 'rm -rf "$W"' EXIT
sec() { awk -v s="[$2]" '$0 == s { on = 1; next } /^\[[a-z-]+\]$/ || /^# done/ { on = 0 } on' "$1" | grep -v -E '^\[(init\.svc_debug_pid|dev\.mnt)\.'; }
RC=0
for s in hal props surfaceflinger settings xgimiconfig; do
  sec "$1" $s > "$W/a"; sec "$2" $s > "$W/b"
  if diff "$W/a" "$W/b" > "$W/d"; then echo "== [$s] same ($(wc -l < "$W/a" | tr -d ' ') lines)"
  else RC=1; echo "== [$s] DIFFERENT ($(grep -c '^[<>]' "$W/d") lines)"; grep '^[<>]' "$W/d"; fi
done
for s in meta hal-volatile context; do
  echo "== [$s] informative, not compared"
  sec "$1" $s | sed 's/^/< /'; sec "$2" $s | sed 's/^/> /'
done
exit $RC
