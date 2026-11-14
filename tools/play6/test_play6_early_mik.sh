#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host test of overlay/play6/play6_early_mik.rc (every Play 6 image):
#   - rc syntax: comments, one 'on' block, 4-space commands, only insmod of /vendor/lib/modules/*.ko and two
#     'write /proc/boottime' marks, no service, no exec, no setprop, no other trigger
#   - the trigger is late-fs AND ro.boot.xgimi.modelname=G0058 (no other projector runs it)
#   - the insmod list equals the vendor's own order in init.fusion.rc from kheaders.ko to mik.ko (lines 28-49 of
#     the stock vendor), checked against the pinned list below and, when the private stock dump is there, against
#     that file itself
#   - the image recipe installs it in EVERY variant (test, diag, ota, gms, no-dexpreopt, usb-adb), not only diag:
#     the release images are made from the test image (tools/play6/release_image.sh)
#   - every XGIMI firmware the registry (vendor_firmware) or the installer (play6.VENDOR_OK) accepts has its
#     watchdog boot path pinned in tools/play6/early_mik_stock.tsv, with an init.fusion.rc whose chain was compared
#   - every module exists in the stock vendor dump, and the dump's files equal the pins of its firmware (when there)
#   - with HOST_INIT_VERIFIER=<path> (the base build's out/host/linux-x86/bin/host_init_verifier), Android's own
#     init parser reads the file too
# Run: bash tools/play6/test_play6_early_mik.sh   (P6_STOCK_VENDOR=<dump>/fs/vendor for the dump checks)
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
RC=$G/overlay/play6/play6_early_mik.rc
V=${P6_STOCK_VENDOR:-}   # the private stock dump: logs/play6_<serial>/v1.7.116/fs/vendor, found below
if [ -z "$V" ]; then for d in "$G"/logs/play6_*/v1.7.116/fs/vendor; do [ -f "$d/etc/init/init.fusion.rc" ] && { V=$d; break; }; done; fi
fails=0; n=0
check() { n=$((n + 1)); if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails + 1)); fi; }
PINNED="kheaders kdrv_platform kdrv_xc kdrv_alg kdrv_dolby_vision libdmdbraslia utpa2k iniparser hdcp1x hdcp2x xcker mwgifker misck mik"

body=$(grep -v -e '^#' -e '^[[:space:]]*$' "$RC")
check "file exists, not empty" '[ -s "$RC" ]'
check "no tabs, no trailing spaces, ends with a newline" '! grep -q "$(printf "\t")" "$RC" && ! grep -q " $" "$RC" && [ -z "$(tail -c 1 "$RC")" ]'
check "exactly one block, and it is an 'on' block" '[ "$(echo "$body" | grep -c "^[^ ]")" = 1 ] && echo "$body" | head -1 | grep -q "^on "'
check "trigger: late-fs and the Play 6 model" '[ "$(echo "$body" | head -1)" = "on late-fs && property:ro.boot.xgimi.modelname=G0058" ]'
check "commands indented by 4 spaces" '[ -z "$(echo "$body" | tail -n +2 | grep -v "^    [^ ]")" ]'
cmds=$(echo "$body" | tail -n +2 | sed 's/^    //')
check "only insmod of vendor modules and boottime marks" '[ -z "$(echo "$cmds" | grep -v -E "^(insmod /vendor/lib/modules/[a-z0-9_]+\.ko|write /proc/boottime \"lumen mik chain (start|end)\")$")" ]'
check "marks first and last" '[ "$(echo "$cmds" | head -1)" = "write /proc/boottime \"lumen mik chain start\"" ] && [ "$(echo "$cmds" | tail -1)" = "write /proc/boottime \"lumen mik chain end\"" ]'
check "no service, exec, setprop, start, trigger" '! grep -q -E "^(service|import)|^ *(exec|exec_start|setprop|start|stop|restart|trigger|class_start) " "$RC"'
mods=$(echo "$cmds" | sed -n 's#^insmod /vendor/lib/modules/\(.*\)\.ko$#\1#p' | tr '\n' ' ' | sed 's/ $//')
check "module list equals the pinned vendor order" '[ "$mods" = "$PINNED" ]'
check "no module twice" '[ -z "$(echo "$mods" | tr " " "\n" | sort | uniq -d)" ]'

# the recipe: one need and one install in every variant
P6PLAN() { python3 -B "$G/tools/build/build.py" plan dragon_healthy t41 "$@" 2>&1; }
for o in none --diag --ota --gms --no-dexpreopt --usb-adb; do
  if [ "$o" = none ]; then plan=$(P6PLAN); else plan=$(P6PLAN "$o"); fi
  ni=$(printf '%s\n' "$plan" | grep -c -E ' install 0644 play6_early_mik\.rc [^ ]*/system/etc/init/play6_early_mik\.rc$')
  nn=$(printf '%s\n' "$plan" | grep -E '^ *[0-9]+ need ' | grep -c -E ' play6_early_mik\.rc( |$)')
  check "recipe installs it once in variant t41 $o" '[ "$ni" = 1 ] && [ "$nn" = 1 ]'
done
check "no diag-only line for it in the steps" '! grep -E "^diag[[:space:]]" "$G/devices/dragon_healthy/image/steps.tsv" | grep -q play6_early_mik'

# the watchdog boot path of every firmware we accept
STOCK=$G/tools/play6/early_mik_stock.tsv
FUSION_OK="002bb22bdc8fff87ddceda226a9b5e496424a011d845fa62f51c13ed408fdddd"   # chain order compared (kheaders..mik)
PINFILES="/vendor/etc/init/init.fusion.rc /vendor/etc/init/hw/init.mt5889.rc /vendor/etc/init/hw/init.xgimi.gmpf.rc /vendor/bin/gmpf_main /vendor/lib/libporting.so /vendor/lib/modules/mik.ko"
fws=$( { sed -n 's/^vendor_firmware=//p' "$G/devices/dragon_healthy/device.conf"; sed -n 's/^play6\.VENDOR_OK=//p' "$G/installer/lib/devices.conf"; } | tr ' ' '\n' | grep . | sort -u | tr '\n' ' ')
check "the registry and the installer name at least one firmware" '[ -n "$fws" ]'
for fw in $fws; do
  have=$(awk -F'\t' -v f="$fw" '$1==f {print $2}' "$STOCK" | sort | tr '\n' ' ')
  want=$(echo $PINFILES | tr ' ' '\n' | sort | tr '\n' ' ')
  check "stock boot path pinned for $fw" '[ "$have" = "$want" ]'
  fu=$(awk -F'\t' -v f="$fw" '$1==f && $2=="/vendor/etc/init/init.fusion.rc" {print $3}' "$STOCK")
  check "init.fusion.rc of $fw has the compared chain" 'case " $FUSION_OK " in *" $fu "*) [ -n "$fu" ] ;; *) false ;; esac'
done
check "pins are 64 hex characters" '[ -z "$(grep -v -e "^#" -e "^\$" "$STOCK" | awk -F"\t" "NF!=3 || \$3 !~ /^[0-9a-f]{64}\$/")" ]'

if [ -n "$V" ] && [ -f "$V/etc/init/init.fusion.rc" ]; then
  F=$V/etc/init/init.fusion.rc
  # the vendor's insmod lines from kheaders.ko to mik.ko, in its early-boot block
  vend=$(awk '$1=="insmod" && $2=="/vendor/lib/modules/kheaders.ko"{on=1} on && $1=="insmod"{m=$2; sub(".*/","",m); sub("[.]ko$","",m); printf "%s ", m; if (m=="mik") exit}' "$F" | sed 's/ $//')
  check "module list equals the stock init.fusion.rc order (kheaders..mik)" '[ "$mods" = "$vend" ]'
  check "the stock lines are 28 (kheaders) and 49 (mik)" '[ "$(grep -n "insmod /vendor/lib/modules/kheaders.ko" "$F" | cut -d: -f1)" = 28 ] && [ "$(grep -n "insmod /vendor/lib/modules/mik.ko" "$F" | cut -d: -f1)" = 49 ]'
  check "the stock loads them at early-boot" '[ "$(awk "/^on /{s=\$0} /insmod \/vendor\/lib\/modules\/mik.ko/{print s}" "$F")" = "on early-boot" ]'
  miss=; for m in $mods; do [ -f "$V/lib/modules/$m.ko" ] || miss="$miss $m"; done
  check "every module is in the stock vendor" '[ -z "$miss" ]'
  # its firmware from its build.prop, else the one firmware whose pins it matches (older dumps have no build.prop)
  inc=$(sed -n 's/^ro\.vendor\.build\.version\.incremental=//p' "$V/build.prop" 2>/dev/null | head -n 1)
  pins_bad() {  # FIRMWARE: the pinned files of the dump that differ (empty = all equal)
    local f want got b=
    for f in $PINFILES; do
      want=$(awk -F'\t' -v a="$1" -v b="$f" '$1==a && $2==b {print $3}' "$STOCK")
      got=$( (sha256sum "$V${f#/vendor}" 2>/dev/null || shasum -a 256 "$V${f#/vendor}" 2>/dev/null) | cut -c1-64)
      [ -n "$want" ] && [ "$want" = "$got" ] || b="$b ${f##*/}"
    done
    printf '%s' "$b"
  }
  if [ -z "$inc" ]; then for fw in $fws; do [ -z "$(pins_bad "$fw")" ] && { inc=$fw; break; }; done; fi
  bad=x; [ -n "$inc" ] && bad=$(pins_bad "$inc")
  check "the dump's boot path (${inc:-no firmware matches}) equals its pins" '[ -z "$bad" ]'
else
  echo "skip stock vendor checks: no stock vendor dump (private; P6_STOCK_VENDOR=DIR)"
fi
if [ -n "${HOST_INIT_VERIFIER:-}" ]; then
  out=$("$HOST_INIT_VERIFIER" "$RC" 2>&1); rc=$?
  check "host_init_verifier reads it (exit 0)" '[ $rc = 0 ]'
  [ $rc = 0 ] || echo "$out" | tail -20
fi
echo "$((n - fails))/$n passed"
[ $fails = 0 ]
