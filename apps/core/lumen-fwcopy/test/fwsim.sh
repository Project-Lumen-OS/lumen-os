#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# fwsim.sh: the shared firmware copy (apps/core/lumen-fwcopy) on real XGIMI partition images, on a computer.
#
#   bash apps/core/lumen-fwcopy/test/fwsim.sh DUMP OTA_IMG [WORK]
#
#   DUMP     a stock dump with name_a.img and name_b.img of every partition of the XGIMI set (X50 G0111 v1.2.64,
#            running half a, from ro.boot.slot_suffix of its cmdline)
#   OTA_IMG  the partition images of an older XGIMI update of the same build line (H0062 v1.2.54 payload, extracted)
#   WORK     scratch dir (default $TMPDIR/fwsim), about 1.5 GB, removed at the end
#
# The case of the owner's question (2026-11-15): XGIMI's own update left the other half on another version, so the two
# halves differ in the boot chain too (boot, dtbo, mboot, tvconfig, tvservice, bootdata) and not only in optee, owl,
# satf, xgimiconfig, vbmeta_system. The script writes the old images over half b of a copy of the dump (as
# update_engine does: the image bytes, the rest of the partition as it was), then:
#   1. plan: every set partition that differs is planned, nothing else;
#   2. dry run: every source read twice, nothing written (every file the same sha256 as before);
#   3. real run with the power going off in the middle: stopped, half b unbootable, fenced parts seen by a new plan
#      made from the bytes alone (the journal removed);
#   4. the run again: the set the same in both halves, verifyEqual clean;
#   5. a third run: nothing to copy (idempotent);
#   6. the running half, vbmeta_a/_b, rom_emmc_boot, misc: byte for byte as before.
# Exit 0 only when every step holds. Host only, never a projector.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(cd "$HERE/.." && pwd)
DUMP=${1:?usage: fwsim.sh DUMP OTA_IMG [WORK]}
OTA=${2:?usage: fwsim.sh DUMP OTA_IMG [WORK]}
W=${3:-${TMPDIR:-/tmp}/fwsim}
JH=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
SET="boot bootdata dtbo mboot optee owl satf tvconfig tvservice vbmeta_system xgimiconfig"
OTHER="vbmeta rom_emmc_boot"
FAILS=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; FAILS=$((FAILS + 1)); }
sim() { "$JH/bin/java" -cp "$W/cls" org.lumen.fwcopy.FwSim "$W/dev" G0111 _a "$@"; }
shas() { (cd "$W/dev" && sha256sum *.img | sort -k2); }

rm -rf "$W" && mkdir -p "$W/cls" "$W/dev" || exit 1
trap 'rm -rf "$W"' EXIT
"$JH/bin/javac" -nowarn -encoding UTF-8 -d "$W/cls" "$MOD"/src/org/lumen/fwcopy/*.java "$HERE/FwSim.java" || exit 1
grep -q "slot_suffix=_a" "$DUMP/cmdline.txt" && ok "the dump ran from half a" || bad "the dump is not from half a"
for n in $SET $OTHER; do cp "$DUMP/${n}_a.img" "$DUMP/${n}_b.img" "$W/dev/" || exit 1; done
cp "$DUMP/misc.img" "$W/dev/misc.img"
# XGIMI's older update in half b: its image bytes, the rest of each partition as the dump had it
for n in $SET; do
  [ -f "$OTA/$n.img" ] || { bad "no $n.img in $OTA"; continue; }
  [ "$(stat -c %s "$OTA/$n.img")" -le "$(stat -c %s "$W/dev/${n}_b.img")" ] || { bad "$n.img is larger than ${n}_b"; continue; }
  dd if="$OTA/$n.img" of="$W/dev/${n}_b.img" conv=notrunc status=none
done
echo "--- the halves now (sha256 a / b)"
for n in $SET; do
  a=$(sha256sum < "$W/dev/${n}_a.img" | cut -c1-16); b=$(sha256sum < "$W/dev/${n}_b.img" | cut -c1-16)
  printf '  %-14s %10s  %s  %s  %s\n' "$n" "$(stat -c %s "$W/dev/${n}_a.img")" "$a" "$b" "$([ "$a" = "$b" ] && echo same || echo differs)"
done
shas > "$W/before.txt"

echo "=== 1. plan"
sim plan > "$W/plan.txt"; rc=$?
cat "$W/plan.txt"
[ $rc = 0 ] && ok "the plan is not refused" || bad "the plan was refused (rc $rc)"
want=$(for n in $SET; do cmp -s "$W/dev/${n}_a.img" "$W/dev/${n}_b.img" || echo "$n"; done | paste -sd, | sed 's/,/, /g')
grep -qF "copy: [$want]," "$W/plan.txt" && ok "every differing set partition planned: $want" || bad "planned $(grep '^copy:' "$W/plan.txt"), want [$want]"
grep -q "vbmeta LEFT_OUT" "$W/plan.txt" && ok "vbmeta left out" || bad "vbmeta not left out"

echo "=== 2. dry run"
sim dry > "$W/dry.txt"; rc=$?
grep -E "^result|WOULD: mark|second|read twice" "$W/dry.txt" | head -5
[ $rc = 0 ] && grep -q "0 bytes written" "$W/dry.txt" && ok "dry run: verified, 0 bytes written" || bad "dry run rc $rc"
shas | diff -q - "$W/before.txt" > /dev/null && ok "dry run: every file as before" || bad "dry run changed a file"
[ ! -f "$W/dev/fwcopy.state" ] && [ ! -f "$W/dev/halves.txt" ] && ok "dry run: no journal, no boot record write" || bad "dry run wrote the journal or the boot record"

echo "=== 3. real run, the power goes off after 300 MiB"
sim real $((300 << 20)) > "$W/cut.txt"; rc=$?
grep -E "^result|^halves|STOP|copied and read back" "$W/cut.txt"
[ $rc = 2 ] && grep -q "power off" "$W/cut.txt" && ok "stopped by the power cut" || bad "the cut run rc $rc"
grep -q "_b=unbootable" "$W/cut.txt" && ok "half b unbootable" || bad "half b not unbootable"
mv "$W/dev/fwcopy.state" "$W/cut.state"   # the journal lost too: the bytes alone must tell
sim plan > "$W/replan.txt"
grep "^copy:" "$W/replan.txt"
grep -qE "unfinished in half b: \[.+\]" "$W/replan.txt" && ok "a new plan from the bytes alone sees the unfinished parts" || bad "no unfinished part seen"
grep -E "^  FW" "$W/replan.txt" | grep -q "unfinished" && ok "the plan lines name them" || bad "no unfinished line"

echo "=== 4. the run again"
sim real > "$W/again.txt"; rc=$?
grep -E "^result|copied and read back" "$W/again.txt"
[ $rc = 0 ] && ok "verified" || bad "the second run rc $rc"
sim verify > "$W/verify.txt"; rc=$?
tail -1 "$W/verify.txt"
[ $rc = 0 ] && ok "verifyEqual: the set the same in both halves" || bad "verifyEqual rc $rc"
for n in $SET; do cmp -s "$W/dev/${n}_a.img" "$W/dev/${n}_b.img" || bad "${n}_b differs from ${n}_a"; done

echo "=== 5. a third run"
sim real > "$W/third.txt"; rc=$?
grep -q "nothing to copy" "$W/third.txt" && grep -q "0 bytes written" "$W/third.txt" && [ $rc = 0 ] && ok "nothing to copy, nothing written" || bad "the third run rc $rc"

echo "=== 6. what is never written"
shas > "$W/after.txt"
for f in $(for n in $SET $OTHER; do echo "${n}_a.img"; done) vbmeta_b.img rom_emmc_boot_b.img misc.img; do
  [ "$(grep " $f\$" "$W/before.txt")" = "$(grep " $f\$" "$W/after.txt")" ] || bad "$f changed"
done
ok "the running half, vbmeta_a/_b, rom_emmc_boot_a/_b, misc: checked byte for byte"
echo "fwsim: $FAILS failed"
[ $FAILS = 0 ]
