#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Home key walk on an emulator or test unit (test/ui/home_ui_test.py): installs the APK, lets it write its
# secure settings and the backup folder as the system image does, then presses keys and checks Home and its panel.
# sh apps/LumenHome/test/ui/run.sh SERIAL APK   (ADB=path/to/adb; never point it at a user's projector)
set -eu
SERIAL=${1:?serial}
APK=${2:?apk}
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
"$ADB" -s "$SERIAL" install -r -g "$APK" >/dev/null
"$ADB" -s "$SERIAL" shell pm grant org.lumen.home android.permission.WRITE_SECURE_SETTINGS
"$ADB" -s "$SERIAL" shell appops set org.lumen.home MANAGE_EXTERNAL_STORAGE allow
# HOME is Home's (the entry walk presses it): the emulator image's own launcher would take the key otherwise
"$ADB" -s "$SERIAL" shell cmd role add-role-holder android.app.role.HOME org.lumen.home 0 >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell pm disable-user --user 0 com.google.android.tvlauncher >/dev/null 2>&1 || true
exec python3 "$HERE/home_ui_test.py" "$SERIAL"
