#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The stock-facts rule (tools/devices/test/stock_facts.py) and the HIDL tables derived by method name
# (tools/hal/derive.py) against their mutants: each edit gives a model a number, row, file or value its stock package
# does not have (or drops one it has). Every mutant must turn the rule red. Reads only this repository's files.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/stock_facts.XXXXXX")
trap 'rm -rf "$T"' EXIT
bad=0
python3 -B "$G/tools/devices/test/stock_facts.py" "$G" > "$T/base.out" 2>&1 || { echo "FAIL stock facts red on the tree"; tail -5 "$T/base.out"; bad=1; }
FILES="devices tools/devices tools/hal tools/play6/javascan.py apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/hal/Z9xCodes.java
  apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/hal/Gmpf2Client.java"
mk() {   # mk NAME FILE OLD NEW: a copy of the files the rule reads with one change, the rule must fail
  rm -rf "$T/r"; mkdir -p "$T/r"
  (cd "$G" && tar cf - $FILES) | tar xf - -C "$T/r"
  if ! python3 -B - "$T/r/$2" "$3" "$4" <<'PY'
import sys
p, old, new = sys.argv[1:4]
old, new = old.replace("\\t", "\t").replace("\\n", "\n"), new.replace("\\t", "\t").replace("\\n", "\n")
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit("edit: %r found %d times" % (old, s.count(old)))
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  then echo "FAIL stock facts mutant $1: the change did not apply"; bad=1; return; fi
  if python3 -B "$T/r/tools/devices/test/stock_facts.py" "$T/r" > "$T/out" 2>&1; then
    echo "FAIL stock facts mutant $1 survived"; bad=1
  else echo "ok   stock facts mutant $1 killed ($(grep -m1 '^FAIL' "$T/out" | cut -c1-110))"; fi
}
H330=devices/_family/rs20-hal330
H312=devices/_family/rs20-hal312
# a wrong method -> code mapping: the Z9X number of setKstPrepare in the RS 20 table
mk wrong_code "$H330/hal/codes.tsv" 'GMPF2_SET_KST_PREPARE\t12\t' 'GMPF2_SET_KST_PREPARE\t18\t'
# the derivation by position (the reference code) instead of by method name
mk by_position tools/hal/derive.py 'have = st.get(r["method"])' 'have = next(((cc, aa) for mm, (cc, aa) in st.items() if cc == got[0]), None)'
# the stock table of the other library
mk other_library "$H312/hal/codes.from" 'stock\tIGmpf2\tdevices/_hal/gmpf-hidl/rs20-v1.6.52.tsv' 'stock\tIGmpf2\tdevices/_hal/gmpf-hidl/rs20-v1.6.55.tsv'
# a stock table edited by hand: two methods swap their codes
mk table_edit devices/_hal/gmpf-hidl/rs20-v1.6.52.tsv '_hidl_setGameModeType(' '_hidl_setGameModeTypeX('
# the generated map keeps a call the HAL lacks
mk map_keeps_absent "$H312/hal/app/src/org/lumen/projector/hal/Z9xCodes.java" '            .put(SET_LENS_DISP_MODE,' '            .put(SET_KST_PREPARE, HalCodes.GMPF2_SET_KST_PREPARE)\n            .put(SET_LENS_DISP_MODE,'
# the shared client names an IGmpf2 code directly again (the 312 table cannot build it: '# blocked')
mk client_direct_code apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/hal/Gmpf2Client.java 'key(SET_KST_PREPARE)' 'HalCodes.GMPF2_SET_KST_PREPARE'
# the optional zoom limit calls gate the zoom row again (the 330 table would hide a zoom its HAL has)
mk zoom_limit_needed devices/_hal/gmpf-hidl/needs.tsv '\tsetKstPrepare setPushPullScreenState\t' '\tsetKstPrepare setPushPullScreenState GetCurrentZoomLimitStep\t'
# a row the HAL cannot serve is shown (the generated layer lost its off row)
mk manual_keystone_shown "$H312/rs20-hal312.menu.json" '"off": {' '"offx": {'
# a picture mode the stock RS 20 has not
mk standard_mode devices/_family/rs20/rs20.menu.json '"performance"\n' '"performance",\n        "standard"\n'
# the RS 20 zoom row without its stock node (zoom_and_optical_shift)
mk zoom_node_lost devices/_menu/stock_nodes.tsv 'zoom\tzoom_and_move zoom_and_optical_shift\t' 'zoom\tzoom_and_move\t'
# a restore file of another model, of another version, or a sibling's stock
mk restore_other_bin devices/g0088/catalog.conf 'restore_file=XGIMI_HORSEPOPULAR_XR04A.bin' 'restore_file=XGIMI_HORSEPOPULAR_XR07A.bin'
mk restore_other_version devices/g0063/catalog.conf 'restore_files=v1.6.52:' 'restore_files=v1.6.50:'
mk restore_stock_newer devices/g0087/catalog.conf 'restore_stock=G0087:v1.6.55>v1.6.55' 'restore_stock=G0087:v1.6.55>v1.6.56'
# a firmware the model's HAL table was not read from
mk wrong_firmware devices/_family/rs20-1652/rs20-1652.device.conf 'vendor_firmware=v1.6.52' 'vendor_firmware=v1.6.52 v1.6.55'
# the stock remote of a model is lost from the remote table
mk remote_lost devices/_remotes/remotes.tsv 'hid_audio\tgatt_bas\txgimi\tG0058,G0063,G0067,G0077,G0078,G0079,G0082,G0086,G0087,G0088,' 'hid_audio\tgatt_bas\txgimi\tG0058,G0063,G0067,G0077,G0078,G0079,G0082,G0086,G0087,'
# a stock row neither shown nor listed as a gap
mk gap_lost devices/_family/rs20/stock.tsv 'gap\tpicture_filmmaker_mode\t' 'gapx\tpicture_filmmaker_mode\t'
# a trial-only model gets an update channel
mk trial_channel devices/_family/rs20/rs20.device.conf 'channel=none' 'channel=beta'
# the stub order witness of hidl_stock.py: a library whose stub order differs from the proxy order is refused
cat > "$T/nm" <<'SH'
#!/bin/sh
cat <<'EOF'
0000000000000100 T x::BpHwGmpf::_hidl_a(android::hardware::IInterface*, android::hardware::details::HidlInstrumentor*)
0000000000000200 T x::BpHwGmpf::_hidl_b(android::hardware::IInterface*, android::hardware::details::HidlInstrumentor*, int)
0000000000000300 T x::BnHwGmpf::_hidl_b(android::hidl::base::V1_0::BnHwBase*)
0000000000000400 T x::BnHwGmpf::_hidl_a(android::hidl::base::V1_0::BnHwBase*)
EOF
SH
chmod +x "$T/nm"; : > "$T/lib.so"
if python3 -B "$G/tools/hal/hidl_stock.py" "$T/lib.so" --nm "$T/nm" --iface IGmpf > /dev/null 2>&1; then
  echo "FAIL hidl_stock mutant stub_order survived"; bad=1
else echo "ok   hidl_stock mutant stub_order killed (refused)"; fi
[ $bad = 0 ] && echo "stock_facts_test: GREEN" || echo "stock_facts_test: RED"
exit $bad
