#!/bin/sh
# lumen-files-access host tests: sh apps/core/lumen-files-access/test/run.sh
#  1. FilesAccessPolicyTest: who gets all files access and when;
#  2. every projector recipe names the module, every projector manifest declares the permission and the provider;
#  3. the Android side applies the policy and asks only for the app op of the table.
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
for r in "$G"/devices/*/app/*Projector.layers; do
  n=$((n + 1))
  if grep -qE '^module +lumen-files-access$' "$r"; then
    echo "ok   ${r#$G/}: names lumen-files-access"
  else
    echo "FAIL ${r#$G/}: no module lumen-files-access"; bad=1
  fi
done
[ "$n" -ge 8 ] || { echo "FAIL only $n projector recipes found"; bad=1; }
for m in ProjectorBase HbProjector X50Projector Play6Projector; do
  f="$G/apps/$m/AndroidManifest.xml"
  if grep -q 'android:name="android.permission.MANAGE_APP_OPS_MODES"' "$f" \
     && grep -q 'android:name="org.lumen.filesaccess.FilesAccessStart"' "$f"; then
    echo "ok   apps/$m: permission and provider"
  else
    echo "FAIL apps/$m: MANAGE_APP_OPS_MODES or FilesAccessStart missing"; bad=1
  fi
done
A="$HERE/src/org/lumen/filesaccess/FilesAccess.java"
if grep -q 'FilesAccessPolicy.shouldGrant(pkg, signers, requested, allowed, pi.lastUpdateTime, handled)' "$A" \
   && grep -q 'getApkContentsSigners()' "$A" && ! grep -q 'getSigningCertificateHistory' "$A"; then
  echo "ok   FilesAccess: current signers only, decision by the policy"
else
  echo "FAIL FilesAccess: the grant does not go through FilesAccessPolicy with the current signers"; bad=1
fi
if grep -nE 'Log\.[a-z]\(.*(signers|requestedPermissions)' "$A"; then
  echo "FAIL FilesAccess: logs package internals"; bad=1
fi
[ $bad = 0 ] && echo "lumen-files-access: all green"
exit $bad
