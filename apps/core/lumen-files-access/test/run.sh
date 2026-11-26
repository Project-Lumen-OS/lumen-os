#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-files-access host tests: sh apps/core/lumen-files-access/test/run.sh
#  1. FilesAccessPolicyTest: who gets all files access and when;
#  2. every projector recipe names the module, every projector manifest declares the permission and the provider;
#  3. the Android side applies the policy and asks only for the app ops and media permissions of the table.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_files_access.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
bad=0
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/filesaccess/FilesAccessPolicy.java" "$HERE/test/FilesAccessPolicyTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.filesaccess.FilesAccessPolicyTest || bad=1
n=0
# every model's projector app recipe, its own or its family's (tools/devices/assemble_app.py --recipes), once per model
T=$(mktemp "${TMPDIR:-/tmp}/files_access.XXXXXX")
for c in $(cd "$G/devices" && ls); do
  [ -f "$G/devices/$c/device.conf" ] && python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --recipes "$c"
done | awk -F'\t' '$1 ~ /Projector$/ {print $2}' > "$T"
while IFS= read -r rel; do
  n=$((n + 1))
  if grep -qE '^module +lumen-files-access$' "$G/$rel"; then
    echo "ok   $rel: names lumen-files-access"
  else
    echo "FAIL $rel: no module lumen-files-access"; bad=1
  fi
done < "$T"
rm -f "$T"
[ "$n" -ge 8 ] || { echo "FAIL only $n projector recipes found"; bad=1; }
# the HB manifest is the core one plus its patch
HBM=$(mktemp -d); python3 -B "$G/tools/devices/patch_file.py" "$G/apps/LumenProjector/core/AndroidManifest.xml" "$G/devices/goat_elegant/app/HbProjector.manifest.diff" > "$HBM/AndroidManifest.xml"
for m in LumenProjector/core HbProjector LumenProjector/host/activity Play6Projector; do
  f="$G/apps/$m/AndroidManifest.xml"
  [ "$m" = HbProjector ] && f=$HBM/AndroidManifest.xml
  [ "$m" = Play6Projector ] && f=$G/devices/dragon_healthy/app/Play6Projector.manifest.xml
  if grep -q 'android:name="android.permission.MANAGE_APP_OPS_MODES"' "$f" \
     && grep -q 'android:name="android.permission.GRANT_RUNTIME_PERMISSIONS"' "$f" \
     && grep -q 'android:name="org.lumen.filesaccess.FilesAccessStart"' "$f"; then
    echo "ok   apps/$m: permissions and provider"
  else
    echo "FAIL apps/$m: MANAGE_APP_OPS_MODES, GRANT_RUNTIME_PERMISSIONS or FilesAccessStart missing"; bad=1
  fi
done
A="$HERE/src/org/lumen/filesaccess/FilesAccess.java"
if grep -q 'FilesAccessPolicy.shouldGrant(pkg, signers, requested, allowed, pi.lastUpdateTime, handled)' "$A" \
   && grep -q 'getApkContentsSigners()' "$A" && ! grep -q 'getSigningCertificateHistory' "$A"; then
  echo "ok   FilesAccess: current signers only, decision by the policy"
else
  echo "FAIL FilesAccess: the grant does not go through FilesAccessPolicy with the current signers"; bad=1
fi
if grep -q 'FilesAccessPolicy.shouldGrantRuntime(pkg, signers, requested, granted, fixed, pi.lastUpdateTime, handled)' "$A" \
   && grep -q 'for (String perm : FilesAccessPolicy.runtimeGrants()) {' "$A" && grep -q 'pm.grantRuntimePermission(pkg, perm, user);' "$A"; then
  echo "ok   FilesAccess: the media read permissions go through FilesAccessPolicy"
else
  echo "FAIL FilesAccess: the media read permissions do not go through FilesAccessPolicy"; bad=1
fi
if grep -nE 'Log\.[a-z]\(.*(signers|requestedPermissions)' "$A"; then
  echo "FAIL FilesAccess: logs package internals"; bad=1
fi
[ $bad = 0 ] && echo "lumen-files-access: all green"
exit $bad
