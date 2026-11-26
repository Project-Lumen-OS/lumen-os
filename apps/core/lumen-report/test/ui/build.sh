#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Builds the TEST APK of the report screen key walk (test/ui/report_walk.py, never shipped): the assembled projector app
# of a model (its strings, modules, Inter font) with the REAL org.lumen.report.ReportActivity and ReportService, and a
# network security config that trusts the fake server's throwaway certificate for 10.0.2.2 only, signed with the public
# test key of apps/sdk.
# Needs the Android SDK (build-tools 36, platform 34) and a JDK 17+ (. ~/lumen-tools/env.sh on llm).
#   bash apps/core/lumen-report/test/ui/build.sh OUT DEVICE APP CA_PEM   -> OUT/walk.apk, OUT/package
set -eu
OUT=$1 DEV=$2 APPNAME=$3 CA=$4
G=$(cd "$(dirname "$0")/../../../../.." && pwd)
S=$(cd "$(dirname "$0")" && pwd)
BT=$ANDROID_SDK/build-tools/36.0.0; AJ=$ANDROID_SDK/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
APP=$OUT/tree; B=$OUT/b
rm -rf "$APP" "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex"
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device "$DEV" --app "$APPNAME" --out "$APP" --quiet
PKG=$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$APP/AndroidManifest.xml" | head -1)
echo "$PKG" > "$OUT/package"
mkdir -p "$APP/res/raw" "$APP/res/xml"; cp "$CA" "$APP/res/raw/lumen_report_walk_ca.pem"
cat > "$APP/res/xml/lumen_report_walk_nsc.xml" <<'EON'
<network-security-config>
  <!-- no trust anchors outside 10.0.2.2: an address that falls back to a real server cannot finish TLS -->
  <base-config cleartextTrafficPermitted="false"><trust-anchors /></base-config>
  <domain-config><domain includeSubdomains="false">10.0.2.2</domain>
    <trust-anchors><certificates src="@raw/lumen_report_walk_ca" /></trust-anchors></domain-config>
</network-security-config>
EON
cat > "$B/AndroidManifest.xml" <<EOM
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG"
    android:versionCode="9000" android:versionName="report-walk">
  <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34" />
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
  <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
  <application android:label="report walk" android:hasCode="true" android:supportsRtl="true"
      android:networkSecurityConfig="@xml/lumen_report_walk_nsc">
    <!-- Fullscreen: the emulator image has a phone status bar, a projector (TV) has none -->
    <activity android:name="org.lumen.report.ReportActivity" android:exported="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen" android:launchMode="singleTop"
        android:configChanges="keyboard|keyboardHidden|navigation" />
    <service android:name="org.lumen.report.ReportService" android:exported="true"
        android:permission="android.permission.BIND_JOB_SERVICE" />
  </application>
</manifest>
EOM
"$BT/aapt2" compile --dir "$APP/res" -o "$B/res.zip"
XP=$(grep -v '^#' "$APP/R_EXTRA_PACKAGES" 2>/dev/null | grep . | tr '\n' ':' | sed 's/:$//')
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" --manifest "$B/AndroidManifest.xml" -A "$APP/assets" --custom-package "$PKG" \
  --java "$B/gen" --min-sdk-version 34 --target-sdk-version 34 ${XP:+--extra-packages "$XP"} "$B/res.zip"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -implicit:class \
  -cp "$HJ:$AJ" -sourcepath "$APP/src:$B/gen" -d "$B/classes" \
  "$APP/src/org/lumen/report/ReportActivity.java" \
  "$APP/src/org/lumen/report/ReportService.java" $(find "$B/gen" -name R.java) > "$B/javac.log" 2>&1 \
  || { head -40 "$B/javac.log"; exit 1; }
"$BT/d8" --min-api 34 --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/u.apk"
(cd "$B/dex" && zip -q -0 "$B/u.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$B/u.apk" "$B/a.apk"
"$BT/apksigner" sign --key "$G/apps/sdk/platform.pk8" --cert "$G/apps/sdk/platform.x509.pem" --out "$OUT/walk.apk" "$B/a.apk"
ls -la "$OUT/walk.apk"
