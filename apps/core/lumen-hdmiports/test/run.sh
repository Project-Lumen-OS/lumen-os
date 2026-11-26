#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-hdmiports: the guard of one HDMI port state with its mutants (check_one_state.py); the chip rule with fake
# ports runs with Lumen Home's data tests (apps/LumenHome/test/org/lumen/home/data/PortChipTest.java).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
exec python3 -B "$HERE/check_one_state.py" "$(cd "$HERE/../../../.." && pwd)" --mutants
