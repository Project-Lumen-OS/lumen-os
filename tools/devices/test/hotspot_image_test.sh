#!/bin/bash
# The hotspot image rule (tools/devices/test/hotspot_image.py) and its mutants: dropping the overlay line from any
# recipe, a wrong iface or a wrong default name must turn it red. Run from the repository root (check.sh does).
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/hotspot_image.XXXXXX")
trap 'rm -rf "$T"' EXIT
bad=0
python3 -B "$G/tools/devices/test/hotspot_image.py" "$G" || bad=1
mk() {   # mk NAME FILE SED: a copy of the files the rule reads with one change, the rule must fail
  rm -rf "$T/r"; mkdir -p "$T/r/tools/devices/test"
  (cd "$G" && tar cf - devices/*/menu.json devices/*/device.conf devices/*/build.conf devices/*/image/steps.tsv \
    devices/*/image/debloat.txt devices/_image/debloat_gsi_tv_arm.txt devices/g0111/image/remove.txt 2>/dev/null) | tar xf - -C "$T/r"
  sed -i.bak "$3" "$T/r/$2" && rm -f "$T/r/$2.bak"
  if cmp -s "$G/$2" "$T/r/$2"; then echo "FAIL hotspot image mutant $1: the change did not apply"; bad=1; return; fi
  if python3 -B "$G/tools/devices/test/hotspot_image.py" "$T/r" > "$T/out" 2>&1; then
    echo "FAIL hotspot image mutant $1 survived"; bad=1
  else echo "ok   hotspot image mutant $1 killed"; fi
}
mk z9x_kept devices/horse_popular/image/steps.tsv '/EmulatorTetheringConfigOverlay/d'
mk arm_kept devices/_image/debloat_gsi_tv_arm.txt '/EmulatorTetheringConfigOverlay/d'
mk rs30_kept devices/g0155/image/debloat.txt '/EmulatorTetheringConfigOverlay/d'
mk x50_kept devices/g0111/image/remove.txt '/EmulatorTetheringConfigOverlay/d'
mk x50max_no_list devices/g0100/build.conf 's#devices/g0111/image/remove.txt##'
mk wlan_iface devices/goat_elegant/menu.json 's/"iface": "ap0"/"iface": "wlan0"/'
mk wrong_name devices/h0062/menu.json 's/"Lumen TITAN Noir Pro"/"Lumen TITAN"/'
[ $bad = 0 ] && echo "hotspot image: all green"
exit $bad
