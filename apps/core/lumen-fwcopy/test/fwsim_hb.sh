#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# fwsim_hb.sh: the shared firmware copy (apps/core/lumen-fwcopy) on the two real halves of a Z9X HB after XGIMI's own
# update, on a computer.
#
#   bash apps/core/lumen-fwcopy/test/fwsim_hb.sh SLOTSET [WORK]
#
#   SLOTSET  name_a.img and name_b.img of the twelve A/B pairs as read from one projector (G0130 on 2026-11-17: stock
#            6.17.12 runs in half b, half a still holds the whole 6.16.24 set, every pair differs, mboot too:
#            01.01.260428 in a, 01.01.260728 in b; rom_emmc_boot_a/_b are the two eMMC boot areas, 6.16.24 and 6.17.12)
#            and last/ext_csd.txt (EXT_CSD as the kernel's debugfs printed it on that projector: [179] = 0x10, area 2).
#            Read only, never changed: the test works on a copy.
#   WORK     scratch dir (default $TMPDIR/fwsim_hb), about 1.4 GB, removed at the end
#   RUNNING  the half stock runs from (default _b), MODEL the model code (default G0130)
#   VENDOR   the running half's vendor tree, for bin/rom_emmc_boot.bin and bin/postinst_mtk (default the G0148 6.17.12
#            tree on xgimi-builder: its two files are the official 6.17.12 ones, 7e6fcd96 and d57af229)
#   EXTCSD   default SLOTSET/last/ext_csd.txt
#
# The owner's rules of 2026-11-17 and 2026-11-18: Lumen OS runs on exactly the firmware stock runs on, so the install
# copies the whole XGIMI set of the running half, the MBoot bootloader too, and the first boot stage the way XGIMI's
# post-install step writes it. The script proves on the real bytes:
#   0. the running area is the running vendor's rom_emmc_boot.bin outside the HB's own ranges (printed);
#   1. plan: not refused, exactly the ten partitions of the stock list and then the first stage are planned, vbmeta
#      left out;
#   2. dry run: every source read twice, nothing written (every file the same sha256 as before), force_ro never touched;
#   3. for each power cut (inside mboot, inside tvconfig, in the last partition, inside the first stage): stopped, the
#      other half unbootable, a new plan made from the bytes alone (the journal removed) sees the unfinished parts, the
#      run again ends with the set the same in both halves and the other area holding the running vendor's file, a
#      third run copies nothing, force_ro of both areas 1;
#   4. the running half (its area too) and vbmeta of both halves: byte for byte as before, after every run; the other
#      area past the file as before;
#   5. the copied mboot is the running one (its version string too).
# Exit 0 only when every step holds. Host only, never a projector.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(cd "$HERE/.." && pwd)
SRC=${1:?usage: fwsim_hb.sh SLOTSET [WORK]}
W=${2:-${TMPDIR:-/tmp}/fwsim_hb}
JH=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
S=${RUNNING:-_b}
MODEL=${MODEL:-G0130}
VENDOR=${VENDOR:-$HOME/devices/g0148_mt9660/work/fs/vendor}
EXTCSD=${EXTCSD:-$SRC/last/ext_csd.txt}
[ "$S" = _a ] && T=_b || T=_a
SET="armfw boot bootdata dtbo mboot optee tvconfig tvservice vbmeta_system xgimiconfig"
STAGE=rom_emmc_boot
OTHER="vbmeta"
FILE=$VENDOR/bin/rom_emmc_boot.bin
FAILS=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; FAILS=$((FAILS + 1)); }
sim() { "$JH/bin/java" -cp "$W/cls" org.lumen.fwcopy.FwSim "$W/dev" "$MODEL" "$S" "$@"; }
shas() { (cd "$W/dev" && sha256sum *.img | sort -k2); }
size() { stat -c %s "$1"; }
boot_id() { grep -a -o 'androidboot.bootloader=[0-9.]*' "$1" | sort -u | paste -sd, -; }
# byte ranges where an area differs from the file (inside the file's length) and whether it is zero after it
ranges() {
  python3 - "$1" "$2" <<'EOF'
import sys
f = open(sys.argv[1], 'rb').read(); a = open(sys.argv[2], 'rb').read()
r = []; i = 0
while i < len(f):
    if f[i] != a[i]:
        j = i
        while j < len(f) and f[j] != a[j]: j += 1
        if r and i - r[-1][1] <= 16: r[-1] = (r[-1][0], j - 1)
        else: r.append((i, j - 1))
        i = j
    else: i += 1
print(' '.join('%x-%x' % x for x in r) or 'none', '| zero after the file:', a[len(f):].count(0) == len(a) - len(f))
EOF
}
# every range inside the HB's own ranges (PostWrite.HB_FIRST_STAGE: 0x46000-0x46300, 0x6cc00-0x6d000)
inside() {
  local r s e
  for r in $1; do
    [ "$r" = none ] && continue
    s=$((16#${r%-*})); e=$((16#${r#*-}))
    { [ $s -ge $((0x46000)) ] && [ $e -lt $((0x46300)) ]; } || { [ $s -ge $((0x6cc00)) ] && [ $e -lt $((0x6d000)) ]; } || return 1
  done
}
# fresh halves: the other half as the projector had it
fresh() {
  rm -rf "$W/dev" && mkdir -p "$W/dev/vendor/bin" || exit 1
  for n in $SET $STAGE $OTHER; do cp "$SRC/${n}_a.img" "$SRC/${n}_b.img" "$W/dev/" || exit 1; done
  cp "$FILE" "$VENDOR/bin/postinst_mtk" "$W/dev/vendor/bin/" && cp "$EXTCSD" "$W/dev/ext_csd.txt" || exit 1
}
# what a run must never touch: the running half (its first stage too) and both halves of vbmeta
untouched() {
  shas > "$W/now.txt"
  local n f
  for f in $(for n in $SET $STAGE $OTHER; do echo "${n}${S}.img"; done) $(for n in $OTHER; do echo "${n}${T}.img"; done); do
    [ "$(grep " $f\$" "$W/before.txt")" = "$(grep " $f\$" "$W/now.txt")" ] || bad "$1: $f changed"
  done
  ok "$1: the running half (its first stage too) and vbmeta of both halves byte for byte as before"
  for n in mmcblk0boot0 mmcblk0boot1; do
    [ ! -f "$W/dev/force_ro_$n" ] || [ "$(cat "$W/dev/force_ro_$n")" = 1 ] || bad "$1: force_ro of $n is $(cat "$W/dev/force_ro_$n")"
  done
}
L=$(size "$FILE")

rm -rf "$W" && mkdir -p "$W/cls" || exit 1
trap 'rm -rf "$W"' EXIT
"$JH/bin/javac" -nowarn -encoding UTF-8 -d "$W/cls" "$MOD"/src/org/lumen/fwcopy/*.java "$HERE/FwSim.java" || exit 1
for n in $SET $STAGE $OTHER; do
  [ -f "$SRC/${n}_a.img" ] && [ -f "$SRC/${n}_b.img" ] || { echo "FAIL no ${n}_a.img / ${n}_b.img in $SRC"; exit 1; }
done
[ -f "$FILE" ] && [ -f "$VENDOR/bin/postinst_mtk" ] && [ -f "$EXTCSD" ] || { echo "FAIL no $FILE, postinst_mtk or $EXTCSD"; exit 1; }
(cd "$SRC" && for n in $SET $STAGE $OTHER; do sha256sum "${n}_a.img" "${n}_b.img"; done) > "$W/fixture.txt"
fresh
echo "--- the halves as read from the projector (bytes, sha256 a / b)"
for n in $SET $STAGE $OTHER; do
  a=$(sha256sum < "$W/dev/${n}_a.img" | cut -c1-16); b=$(sha256sum < "$W/dev/${n}_b.img" | cut -c1-16)
  printf '  %-14s %10s  %s  %s  %s\n' "$n" "$(size "$W/dev/${n}_a.img")" "$a" "$b" "$([ "$a" = "$b" ] && echo same || echo differs)"
  [ "$a" != "$b" ] || bad "$n is the same in both halves: this is not the projector after its stock update"
  [ "$(size "$W/dev/${n}_a.img")" = "$(size "$W/dev/${n}_b.img")" ] || bad "$n: the halves differ in size"
done
echo "  mboot$S (running): $(boot_id "$W/dev/mboot$S.img")   mboot$T: $(boot_id "$W/dev/mboot$T.img")"
[ "$(boot_id "$W/dev/mboot$S.img")" != "$(boot_id "$W/dev/mboot$T.img")" ] && ok "two MBoot versions" || bad "one MBoot version in both halves"
echo "=== 0. the first stage: the running vendor's file ($L bytes, sha256 $(sha256sum < "$FILE" | cut -c1-16)) against both areas"
RUN_R=$(ranges "$FILE" "$W/dev/$STAGE$S.img"); OTH_R=$(ranges "$FILE" "$W/dev/$STAGE$T.img")
echo "  running area $STAGE$S differs at: $RUN_R"
echo "  other area   $STAGE$T differs at: $(echo "$OTH_R" | cut -c1-160)"
inside "${RUN_R%% |*}" && [ "${RUN_R##*: }" = True ] && ok "the running area is the running vendor's file outside the HB's own ranges, zero after it" \
  || bad "the running area is not the running vendor's file: $RUN_R"
inside "${OTH_R%% |*}" && bad "the other area already holds the running file" || ok "the other area holds another first stage"
shas > "$W/before.txt"

echo "=== 1. plan"
sim plan > "$W/plan.txt"; rc=$?
cat "$W/plan.txt"
[ $rc = 0 ] && ! grep -q REFUSED "$W/plan.txt" && ok "the plan is not refused" || bad "the plan was refused (rc $rc)"
want="$(echo $SET | sed 's/ /, /g'), $STAGE"
grep -qF "copy: [$want]," "$W/plan.txt" && ok "exactly the stock list and then the first stage: $want" || bad "planned $(grep '^copy:' "$W/plan.txt"), want [$want]"
grep -q "^  vbmeta LEFT_OUT" "$W/plan.txt" && ok "vbmeta is on the stock list and left out" || bad "vbmeta not left out"
grep -qP "^  FW\tvbmeta\t" "$W/plan.txt" && bad "vbmeta is a part of the plan" || ok "vbmeta is not a part of the plan"
grep -q "^  mboot COPY" "$W/plan.txt" && ok "mboot is a copied part of the set" || bad "mboot is not COPY: $(grep '^  mboot ' "$W/plan.txt")"
grep -qF "  $STAGE COPY (XGIMI's update writes it from the new vendor's post-install step)" "$W/plan.txt" && ok "the first stage is in the set as the post-install write" \
  || bad "the first stage line: $(grep "^  $STAGE " "$W/plan.txt")"
grep -qP "^  FW\t$STAGE\tcopy\t$L\t/vendor/bin/rom_emmc_boot.bin=" "$W/plan.txt" && ok "the first stage's source is the running vendor's file" || bad "the first stage FW line"
PLAN=$(sed -n 's/^plan \([0-9a-f]\{64\}\).*/\1/p' "$W/plan.txt")
[ -n "$PLAN" ] && ok "plan id $PLAN" || bad "no plan id"

echo "=== 2. dry run"
sim dry > "$W/dry.txt"; rc=$?
grep -E "^result|WOULD: mark|WOULD: copy mboot|WOULD: write /vendor" "$W/dry.txt" | head -5
[ $rc = 0 ] && grep -q "0 bytes written" "$W/dry.txt" && ok "dry run: verified, 0 bytes written" || bad "dry run rc $rc"
shas | diff -q - "$W/before.txt" > /dev/null && ok "dry run: every file as before" || bad "dry run changed a file"
[ ! -f "$W/dev/fwcopy.state" ] && [ ! -f "$W/dev/halves.txt" ] && ! ls "$W/dev"/force_ro_* > /dev/null 2>&1 \
  && ok "dry run: no journal, no boot record write, force_ro never touched" || bad "dry run wrote the journal, the boot record or force_ro"

# the bytes written before the middle of a partition: every fence of the set (4 KiB each), every partition before it,
# half of it; the first stage comes after all of them with its own fence
cut_in() {
  local sum=$(( $(echo $SET | wc -w) * 4096 )) n
  for n in $SET; do
    [ "$n" = "$1" ] && { echo $(( sum + $(size "$W/dev/${n}$S.img") / 2 )); return; }
    sum=$(( sum + $(size "$W/dev/${n}$S.img") ))
  done
  echo $(( sum + 4096 + L / 2 ))
}
K=2
for part in mboot tvconfig xgimiconfig $STAGE; do
  K=$((K + 1))
  CUT=$(cut_in $part)
  echo "=== $K. real run, the power goes off inside $part (after $CUT written bytes)"
  fresh
  sim real "$CUT" > "$W/cut.txt"; rc=$?
  grep -E "^result|^halves|STOP|copied and read back|written and read back|force_ro" "$W/cut.txt"
  [ $rc = 2 ] && grep -q "power off" "$W/cut.txt" && ok "stopped by the power cut" || bad "the cut run rc $rc"
  grep -q "${T}=unbootable" "$W/cut.txt" && ok "half ${T#_} unbootable" || bad "half ${T#_} not unbootable"
  grep -q "${S}=bootable" "$W/cut.txt" && ok "half ${S#_} (stock) still bootable" || bad "half ${S#_} not bootable"
  [ "$(head -c 4096 "$W/dev/${part}$T.img" | tr -d '\0' | wc -c)" = 0 ] && ok "$part$T starts with 4 KiB of zeros (unfinished)" || bad "$part$T has no fence"
  untouched "after the cut"
  rm -f "$W/dev/fwcopy.state" "$W/dev"/force_ro_*   # the journal lost too, the projector started again: the bytes alone must tell
  sim plan > "$W/replan.txt"
  grep "^copy:" "$W/replan.txt"
  grep -E "unfinished in half ${T#_}: \[.*\b$part\b.*\]" "$W/replan.txt" > /dev/null && ok "a new plan from the bytes alone sees $part unfinished" || bad "$part not seen as unfinished"
  grep -q REFUSED "$W/replan.txt" && bad "the new plan is refused: $(grep REFUSED "$W/replan.txt")"
  sim real > "$W/again.txt"; rc=$?
  grep -E "^result" "$W/again.txt"
  [ $rc = 0 ] && ok "the run again: verified" || bad "the second run rc $rc"
  sim verify > "$W/verify.txt"; rc=$?
  tail -1 "$W/verify.txt"
  [ $rc = 0 ] && ok "verifyEqual: the set the same in both halves, the first stage the running file" || bad "verifyEqual rc $rc"
  for n in $SET; do cmp -s "$W/dev/${n}_a.img" "$W/dev/${n}_b.img" || bad "${n}$T differs from ${n}$S"; done
  for n in $SET; do
    [ "$(sha256sum < "$W/dev/${n}$T.img" | cut -d' ' -f1)" = "$(grep " ${n}$S.img\$" "$W/fixture.txt" | cut -d' ' -f1)" ] || bad "${n}$T is not the projector's ${n}$S"
  done
  ok "every set partition of half ${T#_} is the projector's running one"
  cmp -s -n "$L" "$FILE" "$W/dev/$STAGE$T.img" && ok "$STAGE$T holds the running vendor's file byte for byte" || bad "$STAGE$T is not the running vendor's file"
  cmp -s <(tail -c +$((L + 1)) "$W/dev/$STAGE$T.img") <(tail -c +$((L + 1)) "$SRC/$STAGE$T.img") && ok "$STAGE$T past the file as before" || bad "$STAGE$T changed past the file"
  NOW_R=$(ranges "$W/dev/$STAGE$T.img" "$W/dev/$STAGE$S.img")
  inside "${NOW_R%% |*}" && ok "the two areas now differ only inside the HB's own ranges: ${NOW_R%% |*}" || bad "the areas differ outside the ranges: $NOW_R"
  for n in $OTHER; do cmp -s "$W/dev/${n}_a.img" "$W/dev/${n}_b.img" && bad "$n became the same in both halves"; done
  [ "$(boot_id "$W/dev/mboot$T.img")" = "$(boot_id "$SRC/mboot$S.img")" ] && ok "mboot$T is now $(boot_id "$W/dev/mboot$T.img")" || bad "mboot$T is $(boot_id "$W/dev/mboot$T.img")"
  sim real > "$W/third.txt"; rc=$?
  grep -q "nothing to copy" "$W/third.txt" && grep -q "0 bytes written" "$W/third.txt" && [ $rc = 0 ] && ok "a third run: nothing to copy, nothing written" || bad "the third run rc $rc"
  untouched "after the copy"
done

echo "=== the fixture itself"
(cd "$SRC" && for n in $SET $STAGE $OTHER; do sha256sum "${n}_a.img" "${n}_b.img"; done) | diff -q - "$W/fixture.txt" > /dev/null && ok "the images of the projector were not changed" || bad "the fixture changed"
echo "fwsim_hb: $FAILS failed"
[ $FAILS = 0 ]
