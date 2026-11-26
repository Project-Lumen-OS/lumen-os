#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# test_build.sh CODENAME APP [OUT_APK]: compile check of a recipe-built app with a THROWAWAY test key (never the image
# keys), the build suite of the Z9X HB projector app (tools/arch/suites.sh: goat_elegant HbProjector).
#   1. the fork rows of the model (tools/devices/check_forks.py --device CODENAME);
#   2. the tree from the recipe into build/test_build/CODENAME.APP/assembled (tools/devices/assemble_app.py);
#   3. build_apk.sh on it with a key pair generated once into build/testkey/ (RSA 2048, self-signed, a throwaway);
#   4. the HAL gate over the sources and the compiled dex (tools/devices/check_hal.py --device CODENAME --dex);
#   5. the plain-Java host tests of the assembled tree (test/*/run.sh, LUMEN_GSI set).
# A red step fails the build. The image build signs with the model's platform key (tools/devices/build_apps.sh).
set -eu
G=$(cd "$(dirname "$0")/../.." && pwd)
C=$1 A=$2
W=$G/build/test_build/$C.$A
K=$G/build/testkey
T=$W/assembled
OUT=${3:-$W/$A-testkey.apk}
python3 -B "$G/tools/devices/check_forks.py" --device "$C"
python3 -B "$G/tools/devices/assemble_app.py" --device "$C" --app "$A" --out "$T"
mkdir -p "$K"
if [ ! -f "$K/testkey.pk8" ] || [ ! -f "$K/testkey.x509.pem" ]; then
  echo "test_build.sh: generating throwaway test key in $K"
  (umask 077
   openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=Lumen throwaway test key" \
     -keyout "$K/testkey.key.pem" -out "$K/testkey.x509.pem" 2>/dev/null
   openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -in "$K/testkey.key.pem" -out "$K/testkey.pk8"
   rm -f "$K/testkey.key.pem")
fi
KEY_PK8=$K/testkey.pk8 KEY_PEM=$K/testkey.x509.pem BUILD_DIR=$W/out \
  sh "$G/apps/build_apk.sh" "$T" "$OUT"
python3 -B "$G/tools/devices/check_hal.py" --device "$C" --dex "$OUT"
for t in "$T"/test/*/run.sh; do [ -f "$t" ] || continue; LUMEN_GSI=$G sh "$t"; done
