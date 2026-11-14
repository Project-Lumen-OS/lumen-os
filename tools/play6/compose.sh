#!/bin/bash
# The Play 6 composer in the lineage-builder container: the one image builder with the staged descriptor
# (devices/dragon_healthy/build.conf, steps devices/dragon_healthy/image/steps.tsv). Staged by tools/play6/build_test.sh
# (or tools/build/build.py run dragon_healthy):
#   docker run --rm -e PYTHONDONTWRITEBYTECODE=1 -v ~/lineage:/src:ro -v ~/play6-out:/out:ro -v ~/play6-work:/work \
#     lineage-builder bash /work/in/compose.sh VARIANT [--diag] [--usb-adb] [--exp[=N,...]] [--no-dexpreopt] [--gms] [--ota]
# Output /work/VARIANT/system_play6_VARIANT.img (ext4, the base build's build_image). BUILD_ID, when set, is the
# whole ro.lumen.build_id (default play6-VARIANT-<UTC minute>).
LUMEN_CODENAME=dragon_healthy LUMEN_IMAGE_DIR=${LUMEN_IMAGE_DIR:-/work} exec python3 -I "$(dirname "$0")/build.py" compose "$@"
