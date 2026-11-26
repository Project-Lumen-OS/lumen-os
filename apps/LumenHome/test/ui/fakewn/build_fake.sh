#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Builds the fake Continue watching app of Home's key walk (never shipped): FakeWnSeed puts Watch Next rows into
# TvProvider, FakeWnPlayer is what a card opens, FakeWnDrop drops a row as the provider
# change of a Delete on a projector. Needs the Android SDK
# (build-tools 36, platform 34) and a JDK 17+.
#   bash apps/LumenHome/test/ui/fakewn/build_fake.sh OUT   -> OUT/fakewn.apk
set -eu
OUT=$1
S=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$S/../../../../.." && pwd)
BT=$ANDROID_SDK/build-tools/36.0.0; AJ=$ANDROID_SDK/platforms/android-34/android.jar
B=$OUT/fakewn
rm -rf "$B"; mkdir -p "$B/classes" "$B/dex"
cat > "$B/AndroidManifest.xml" <<EOM
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.lumen.home.fakewn"
    android:versionCode="1" android:versionName="fake-wn">
  <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34" />
  <uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
  <application android:label="fake continue" android:hasCode="true">
    <activity android:name=".FakeWnSeed" android:exported="true" android:theme="@android:style/Theme.NoDisplay" />
    <activity android:name=".FakeWnPlayer" android:exported="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen" />
    <receiver android:name=".FakeWnDrop" android:exported="true" />
  </application>
</manifest>
EOM
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" --manifest "$B/AndroidManifest.xml" --min-sdk-version 34 --target-sdk-version 34
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -cp "$AJ" -d "$B/classes" "$S"/FakeWn*.java
"$BT/d8" --min-api 34 --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/u.apk"
(cd "$B/dex" && zip -q -0 "$B/u.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$B/u.apk" "$B/a.apk"
"$BT/apksigner" sign --key "$G/apps/sdk/platform.pk8" --cert "$G/apps/sdk/platform.x509.pem" --out "$OUT/fakewn.apk" "$B/a.apk"
