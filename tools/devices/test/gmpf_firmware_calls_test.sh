#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The firmware call rule (tools/devices/test/gmpf_firmware_calls.py) and its mutants: each edit lets Gmpf send a call
# on a firmware whose HAL table has another method (or none) at that code, above all the lens memory writer 107 on the
# RS30 Ultra v1.2.5, where 107 is the shift page flag. Every mutant must turn the rule red. Run from the repository root.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/gmpf_fw_calls.XXXXXX")
trap 'rm -rf "$T"' EXIT
bad=0
python3 -B "$G/tools/devices/test/gmpf_firmware_calls.py" "$G" || bad=1
P=apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector
H=apps/core/lumen-hal/src/org/lumen/hal
mk() {   # mk NAME FILE OLD NEW: a copy of the files the rule reads with one change, the rule must fail
  rm -rf "$T/r"; mkdir -p "$T/r"
  (cd "$G" && tar cf - $P/Gmpf.java $P/GmpfCall.java $P/GmpfIface.java $P/HalCodes.java $H/GmpfHash.java $H/GmpfModel.java \
    $H/GmpfFirmware.java devices/g0111/research/aidl_codes.tsv devices/g0111/research/aidl_codes_v1254.tsv \
    devices/g0111/research/aidl_codes_v1338.tsv devices/h0062/research/aidl_codes_v1299.tsv \
    devices/g0155/research/aidl_codes.tsv devices/g0170/research/aidl_codes_v125.tsv devices/*/device.conf devices/_family tools/devices/layered.py) | tar xf - -C "$T/r"
  if ! python3 -B - "$T/r/$2" "$3" "$4" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit("edit: %r found %d times" % (old, s.count(old)))
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  then echo "FAIL gmpf firmware calls mutant $1: the change did not apply"; bad=1; return; fi
  if python3 -B "$G/tools/devices/test/gmpf_firmware_calls.py" "$T/r" > "$T/out" 2>&1; then
    echo "FAIL gmpf firmware calls mutant $1 survived"; bad=1
  else echo "ok   gmpf firmware calls mutant $1 killed"; fi
}
# the lens memory writer 107 reaches the RS30 Ultra (its 107 is the shift page flag)
mk nimble_apply_unguarded $P/Gmpf.java '        firmwareGuard(null, GmpfFirmware.LENS_MEMORY, GmpfIface.FOCUS, "lensApply");
' ''
mk nimble_on_rs30_ultra $H/GmpfFirmware.java 'LENS_MEMORY(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30)' \
  'LENS_MEMORY(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30, GmpfHash.RS30_ULTRA)'
mk nimble_list_unguarded $P/Gmpf.java '        firmwareGuard(FeatureId.ZOOM_MEMORY, GmpfFirmware.LENS_MEMORY, GmpfIface.FOCUS, "lensList");
' '        extraGuard(FeatureId.ZOOM_MEMORY, "lensList");
'
# the shift page flag: 107 only on the RS30 Ultra, 112 never there
mk flag107_on_rs30 $H/GmpfFirmware.java 'SHIFT_PAGE_107(GmpfHash.RS30_ULTRA, GmpfHash.RS30_OLD)' 'SHIFT_PAGE_107(GmpfHash.RS30, GmpfHash.RS30_OLD)'
mk flag107_on_x50 $H/GmpfFirmware.java 'SHIFT_PAGE_107(GmpfHash.RS30_ULTRA, GmpfHash.RS30_OLD)' 'SHIFT_PAGE_107(GmpfHash.RS30_ULTRA, GmpfHash.RS30_OLD, GmpfHash.X50)'
mk flag112_on_rs30_ultra $H/GmpfFirmware.java 'SHIFT_PAGE(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30)' \
  'SHIFT_PAGE(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30, GmpfHash.RS30_ULTRA)'
mk flag_branch_falls_through $P/Gmpf.java '            call(GmpfCall.GET_UITILT_SHIFT_SWITH_STATUS_V125, p -> p.writeBoolean(on), p -> null);
            return;' '            call(GmpfCall.GET_UITILT_SHIFT_SWITH_STATUS_V125, p -> p.writeBoolean(on), p -> null);'
mk flag_112_unguarded $P/Gmpf.java '        firmwareGuard(null, GmpfFirmware.SHIFT_PAGE, GmpfIface.FOCUS, "shiftPage");
' ''
mk flag_calls_swapped $P/Gmpf.java '            call(GmpfCall.GET_UITILT_SHIFT_SWITH_STATUS_V125, p -> p.writeBoolean(on), p -> null);' \
  '            call(GmpfCall.GET_UITILT_SHIFT_SWITH_STATUS, p -> p.writeBoolean(on), p -> null);'
mk flag107_code $P/HalCodes.java 'PF_GET_UITILT_SHIFT_SWITH_STATUS_V125 = 107;' 'PF_GET_UITILT_SHIFT_SWITH_STATUS_V125 = 112;'
# isKeystoneAdjusted 207 is not on the RS30 Ultra table
mk keystone_adjusted_unguarded $P/Gmpf.java '        firmwareGuard(null, GmpfFirmware.KEYSTONE_ADJUSTED, GmpfIface.DISPLAY, "keystoneAdjusted");
' ''
mk keystone_adjusted_rs30_ultra $H/GmpfFirmware.java 'KEYSTONE_ADJUSTED(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30)' \
  'KEYSTONE_ADJUSTED(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30, GmpfHash.RS30_ULTRA)'
# the colour uniformity calls stay off v1.2.54 and the RS30 tables, the model's rule off the X50 hashes
mk uniformity_on_v1254 $H/GmpfFirmware.java 'COLOR_UNIFORMITY(GmpfHash.X50, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299)' \
  'COLOR_UNIFORMITY(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299)'
mk rs30_ultra_wrong_table $H/GmpfHash.java 'RS30_ULTRA = "09e5998d368df12cc6f1662b8a6f3d8a54a5775b"' 'RS30_ULTRA = "09e5998d368df12cc6f1662b8a6f3d8a54a57750"'
# the RS30 Ultra's model row takes another model's hash rule, or the RS30 Ultra Max takes the RS30 Ultra's
mk rs30_ultra_rs30_rule $H/GmpfModel.java 'RS30_ULTRA("g0170", "G0170", GmpfHash.RS30_ULTRA_RULE' 'RS30_ULTRA("g0170", "G0170", GmpfHash.RS30_RULE'
mk rs30_ultra_x50_rule $H/GmpfModel.java 'RS30_ULTRA("g0170", "G0170", GmpfHash.RS30_ULTRA_RULE' 'RS30_ULTRA("g0170", "G0170", GmpfHash.X50_RULE'
mk rs30_max_ultra_rule $H/GmpfModel.java 'RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.RS30_RULE' 'RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.RS30_ULTRA_RULE'
mk rs30_ultra_rule_plus_x50 $H/GmpfHash.java 'RS30_ULTRA_RULE = new HashRule(RS30_ULTRA);' 'RS30_ULTRA_RULE = new HashRule(RS30_ULTRA, X50_V1254_FIRMWARE, X50_V1254);'
[ $bad = 0 ] && echo "gmpf firmware calls: all green"
exit $bad
