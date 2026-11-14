#!/bin/bash
# The X50 composer (g0111 X50 Ultra, g0100 X50 Ultra Max) on the build host: the one image builder with the staged
# descriptor (devices/<codename>/build.conf, steps devices/g0111/image/steps.tsv), staged by tools/build/build.py run.
#   bash ~/x50-out/in/compose_x50.sh tN|X.Y.Z[-betaN] [--stage-only|--image-only] [--dry-run]
# X50_CODENAME, when set, must name the staged model. Environment as before: LUMEN_IMAGE_DIR, BUILD_ID (the date
# part), SOURCE_DATE_EPOCH, X50_DUMP, X50_BOOT.
LUMEN_CODENAME=${X50_CODENAME:-} exec python3 -I "$(dirname "$0")/build.py" compose "$@"
