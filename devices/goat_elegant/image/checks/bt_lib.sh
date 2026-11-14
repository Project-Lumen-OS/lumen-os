#!/bin/bash
# Bluetooth (hb_bt.rc): the base's libbluetooth_jni.so with SBC fed at 44.1 kHz (hb_bt_sbc.py: the
# pinned base copy in, the pinned HB copy out) into WORKDIR/btchk/libbluetooth_jni.so. Our own build, no XGIMI file.
#   bt_lib.sh BASE_MOUNT WORKDIR IN
set -euo pipefail
MB=$1 BTW=$2/btchk IN=$3
die() { echo "ERROR: $*" >&2; exit 1; }
mkdir -p "$BTW"
python3 -I -c 'import sys, zipfile; open(sys.argv[2], "wb").write(zipfile.ZipFile(sys.argv[1]).read("apex_payload.img"))' \
  "$MB/system/apex/com.android.btservices.apex" "$BTW/bt_payload.img" || die "bt: cannot read the base btservices APEX payload"
"$(command -v debugfs || echo /sbin/debugfs)" -R "dump /lib/libbluetooth_jni.so $BTW/base.so" "$BTW/bt_payload.img" > /dev/null 2>&1
python3 -I "$IN/hb_bt_sbc.py" "$BTW/base.so" "$BTW/libbluetooth_jni.so" || die "bt: hb_bt_sbc.py refused the base copy"
