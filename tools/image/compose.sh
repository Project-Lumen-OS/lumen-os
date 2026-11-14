#!/bin/bash
# compose.sh: stage a device's image inputs on its build host and build the image variants there, with the one
# image builder (tools/build/README.md). Kept for its old command lines:
#   bash tools/image/compose.sh CODENAME VERSION [VARIANT]    every variant of devices/<codename>/build.conf, or one
# Environment: IMAGE_HOST overrides the host; BUILD_ID and SOURCE_DATE_EPOCH are passed on to the builder.
exec python3 -B "$(cd "$(dirname "$0")/../.." && pwd)/tools/build/build.py" run "$@"
