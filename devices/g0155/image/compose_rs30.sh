#!/bin/bash
# The RS30 Ultra Max composer (g0155) on the build host: the one image builder with the staged descriptor
# (devices/g0155/build.conf, steps devices/g0155/image/steps.tsv), staged by tools/build/build.py run
# (tools/image/compose.sh g0155 tN net|unsigned from the Mac).
#   bash ~/rs30-out/in/compose_rs30.sh tN [--unsigned] [--dry-run]
# Environment: LUMEN_IMAGE_DIR, BUILD_ID (the date part), SOURCE_DATE_EPOCH, RS30_BASE, RS30_DUMP.
LUMEN_CODENAME=g0155 exec python3 -I "$(dirname "$0")/build.py" compose "$@"
