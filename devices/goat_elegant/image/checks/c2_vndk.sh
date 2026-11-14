#!/bin/bash
# MediaTek's VNDK libstagefright_foundation.so for the vendor codec2 HAL (hb_media.rc): the base's
# com.android.vndk.v33.apex must hold the pinned GSI copy hb_media.rc covers, and the stock copy must export every
# dynamic symbol of the GSI copy plus the 11 MEDIA_MIMETYPE_VIDEO_* that libcodec2.MTK.so needs.
#   c2_vndk.sh BASE_MOUNT WORKDIR SF_STOCK SF_GSI_SHA
set -euo pipefail
MB=$1 SFW=$2/c2chk SF_STOCK=$3 SF_GSI_SHA=$4
die() { echo "ERROR: $*" >&2; exit 1; }
mkdir -p "$SFW"
python3 -I -c 'import sys, zipfile; open(sys.argv[2], "wb").write(zipfile.ZipFile(sys.argv[1]).read("apex_payload.img"))' \
  "$MB/system/system_ext/apex/com.android.vndk.v33.apex" "$SFW/v33_payload.img" || die "c2: cannot read the base VNDK v33 APEX payload"
DBG=$(command -v debugfs || echo /sbin/debugfs)
"$DBG" -R "dump /lib/libstagefright_foundation.so $SFW/gsi.so" "$SFW/v33_payload.img" > /dev/null 2>&1
[ "$(sha256sum "$SFW/gsi.so" 2>/dev/null | awk '{print $1}')" = "$SF_GSI_SHA" ] || die "c2: the base VNDK v33 APEX has no pinned /lib/libstagefright_foundation.so ($SF_GSI_SHA)"
syms() { readelf -W --dyn-syms "$1" | awk '$7 != "UND" && $5 != "LOCAL" && NF >= 8 {print $8}' | LC_ALL=C sort -u; }
syms "$SFW/gsi.so" > "$SFW/gsi.syms"; syms "$SF_STOCK" > "$SFW/stock.syms"
miss=$(LC_ALL=C comm -23 "$SFW/gsi.syms" "$SFW/stock.syms" | wc -l)
[ "$miss" = 0 ] || die "c2: the stock copy lacks $miss exports of the GSI copy"
for m in WMV AVS AVS2 FLV VC1 SHVC DIVX4 DIVX5 DIVX_AVC DIVX_HEVC REAL_VIDEO; do
  n="MEDIA_MIMETYPE_VIDEO_$m"
  grep -qx "_ZN7android${#n}${n}E" "$SFW/stock.syms" || die "c2: the stock copy does not export android::$n"
  ! grep -qx "_ZN7android${#n}${n}E" "$SFW/gsi.syms" || die "c2: the GSI copy exports android::$n: the premise of hb_media.rc changed"
done
echo "c2: stock libstagefright_foundation.so $(wc -l < "$SFW/stock.syms") exports, GSI v33 copy $(wc -l < "$SFW/gsi.syms"), none missing, the 11 MTK video MIME types only in the stock copy"
rm -rf "$SFW"
