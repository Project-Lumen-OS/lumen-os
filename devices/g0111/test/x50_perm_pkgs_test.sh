#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# x50_perm_pkgs_test.sh: host tests of devices/g0111/image/x50_perm_pkgs.py (t11 review P0.1d) on small trees built
# from the real X50Projector APK and its real default-permissions file, and its hook in verify_tree_x50.py.
#   sh devices/g0111/test/x50_perm_pkgs_test.sh [X50Projector.apk]     (default build/g0111/stage/X50Projector.apk)
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
APK=${1:-$G/build/g0111/stage/X50Projector.apk}
PY=$G/devices/g0111/image/x50_perm_pkgs.py
XML=$G/apps/X50Projector/image/x50-projector.xml
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_perm.XXXXXX") || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; sed 's/^/     | /' "$W/out" | tail -6; }
expect() {  # name want-rc text
  if [ "$rc" = "$2" ] && grep -qF -- "$3" "$W/out"; then ok "$1"; else bad "$1 (exit $rc, want $2, '$3')"; fi
}
[ -f "$APK" ] || { echo "no APK $APK (sh tools/devices/build_apps.sh g0111 build/g0111/stage)"; exit 1; }
tree() {  # dir: the X50Projector APK and its default-permissions file
  mkdir -p "$1/system/priv-app/X50Projector" "$1/system/product/etc/default-permissions" "$1/system/etc/permissions"
  cp "$APK" "$1/system/priv-app/X50Projector/X50Projector.apk"
  cp "$XML" "$1/system/product/etc/default-permissions/x50-projector.xml"
}
run() { python3 -I "$PY" "$@" > "$W/out" 2>&1; rc=$?; }
perm() {  # file kind package
  if [ "$2" = exception ]; then
    printf '<exceptions>\n  <exception package="%s">\n    <permission name="android.permission.BLUETOOTH_SCAN" fixed="true"/>\n  </exception>\n</exceptions>\n' "$3" > "$1"
  else
    printf '<permissions>\n  <privapp-permissions package="%s">\n    <permission name="android.permission.REBOOT"/>\n  </privapp-permissions>\n</permissions>\n' "$3" > "$1"
  fi
}

tree "$W/ok"; run "$W/ok"; expect real_file_names_the_app 0 "x50_perm_pkgs: OK"
grep -q 'package="org.lumen.x50.projector"' "$XML" && ok "the shipped file names org.lumen.x50.projector" \
  || { echo "x50-projector.xml" > "$W/out"; bad "the shipped file names org.lumen.x50.projector"; }

tree "$W/old"; sed 's/org\.lumen\.x50\.projector/org.lumen.x50.remote/' "$XML" > "$W/old/system/product/etc/default-permissions/x50-projector.xml"
run "$W/old"; expect merged_app_old_name 1 "names org.lumen.x50.remote, which is not in the image"

tree "$W/priv"; perm "$W/priv/system/etc/permissions/privapp-x50.xml" privapp org.lumen.absent
run "$W/priv"; expect privapp_names_missing 1 "(privapp) names org.lumen.absent"

tree "$W/fw"; perm "$W/fw/system/etc/permissions/fw.xml" privapp android
run "$W/fw"; expect android_counts_as_present 0 "x50_perm_pkgs: OK"

tree "$W/base"; tree "$W/new"
perm "$W/base/system/etc/permissions/google.xml" privapp com.google.android.absent
cp "$W/base/system/etc/permissions/google.xml" "$W/new/system/etc/permissions/google.xml"
run "$W/new" "$W/base"; expect base_file_kept_as_is 0 "1 names there not in the image"
perm "$W/new/system/etc/permissions/google.xml" exception com.google.android.absent
run "$W/new" "$W/base"; expect base_file_changed_is_checked 1 "names com.google.android.absent"

tree "$W/broken"; echo '<exceptions><exception' > "$W/broken/system/product/etc/default-permissions/b.xml"
run "$W/broken"; expect unreadable_xml 1 "not readable XML"

python3 -I "$PY" > "$W/out" 2>&1; rc=$?; expect usage 2 "NEW_ROOT"
mkdir -p "$W/empty"; run "$W/empty"; expect not_a_tree_refused 1 "no APK found"
run "$W/ok" "$W/empty"; expect not_a_base_refused 1 "not the base tree"

V=$G/devices/g0111/image/verify_tree_x50.py
if grep -q 'x50_perm_pkgs.problems(NEW_ROOT, BASE_ROOT)' "$V" && ! grep -q 'x50_perm_pkgs.problems(new' "$V" && grep -q 'x50_perm_pkgs.py' "$G/devices/g0111/image.conf" \
    && grep -q 'tools/lumen/apkinfo.py' "$G/devices/g0111/image.conf"; then ok "verify_tree_x50 runs it, image.conf stages it"
else echo "hook" > "$W/out"; bad "verify_tree_x50 runs it, image.conf stages it"; fi
echo "x50_perm_pkgs_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
