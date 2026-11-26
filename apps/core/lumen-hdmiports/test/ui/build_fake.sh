#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Builds the fake HDMI port app of the emulator walks (never shipped): FakePortsProvider answers as org.lumen.tvinput's
# hdmi_state provider. Needs the Android SDK (build-tools 36, platform 34) and a JDK 17+ (. ~/lumen-tools/env.sh on llm).
#   bash apps/core/lumen-hdmiports/test/ui/build_fake.sh OUT   -> OUT/fakeports.apk
set -eu
OUT=$1
S=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$S/../../../../.." && pwd)
BT=$ANDROID_SDK/build-tools/36.0.0; AJ=$ANDROID_SDK/platforms/android-34/android.jar
B=$OUT/fake
rm -rf "$B"; mkdir -p "$B/classes" "$B/dex"
cat > "$B/AndroidManifest.xml" <<EOM
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.lumen.hdmiports.fake"
    android:versionCode="1" android:versionName="fake-ports">
  <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34" />
  <application android:label="fake ports" android:hasCode="true">
    <provider android:name=".FakePortsProvider" android:authorities="org.lumen.tvinput.hdmistate" android:exported="true" />
    <receiver android:name=".FakePortsReceiver" android:exported="true" />
    <activity android:name=".FakePortsFront" android:exported="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen" />
  </application>
</manifest>
EOM
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" --manifest "$B/AndroidManifest.xml" --min-sdk-version 34 --target-sdk-version 34
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -cp "$AJ" -d "$B/classes" "$S"/FakePorts*.java
"$BT/d8" --min-api 34 --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/u.apk"
(cd "$B/dex" && zip -q -0 "$B/u.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$B/u.apk" "$B/a.apk"
"$BT/apksigner" sign --key "$G/apps/sdk/platform.pk8" --cert "$G/apps/sdk/platform.x509.pem" --out "$OUT/fakeports.apk" "$B/a.apk"
