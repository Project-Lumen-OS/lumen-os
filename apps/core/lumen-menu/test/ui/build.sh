#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Builds the TEST APK of the shared menu key walks (never shipped): the assembled app of a model (its merged menu
# profile, strings and modules) with test/ui/MenuWalkActivity, the menu's PanelActivity and the Remote buttons screen
# as its activities, signed with the public test platform key of apps/sdk. Needs the Android SDK (build-tools 36,
# platform 34) and a JDK 17+ (. ~/lumen-tools/env.sh on llm).
#   bash apps/core/lumen-menu/test/ui/build.sh OUT DEVICE APP   -> OUT/walk.apk, OUT/package
# WALK=QuickWalkActivity builds the quick menu walk (test/ui/quick_walk.py) instead; WITHOUT="id ..." removes those
# features from the model's profile in the test APK (a model without them, e.g. picture.memc). EXTRA_SRC="path ..." also
# compiles those sources of the assembled tree (classes a walk reaches only by reflection), EXTRA_PERMS="perm ..." asks
# those permissions too (the power walk: the sleep and the overlays of the power key).
set -eu
OUT=$1 DEV=$2 APPNAME=$3 WALK=${WALK:-MenuWalkActivity}
G=$(cd "$(dirname "$0")/../../../../.." && pwd)
S=$(cd "$(dirname "$0")" && pwd)
BT=$ANDROID_SDK/build-tools/36.0.0; AJ=$ANDROID_SDK/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
APP=$OUT/tree; B=$OUT/b
rm -rf "$APP" "$B"; mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/src/org/lumen/menu/walk"
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device "$DEV" --app "$APPNAME" --out "$APP" --quiet
if [ -n "${WITHOUT:-}" ]; then
  python3 - "$APP/assets/lumen_menu/menu.json" $WITHOUT <<'EOP'
import json, sys
path, gone = sys.argv[1], set(sys.argv[2:])
def strip(o):
    if isinstance(o, dict): return {k: strip(v) for k, v in o.items() if k not in gone}
    if isinstance(o, list): return [strip(v) for v in o if not (isinstance(v, str) and v in gone)]
    return o
d = strip(json.load(open(path)))
json.dump(d, open(path, "w"), indent=1)
EOP
fi
PKG=$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$APP/AndroidManifest.xml" | head -1)
echo "$PKG" > "$OUT/package"
# the real app's package visibility and the Lumen permissions it asks (the HDMI app's port state, Lumen Home's
# parental state): its <queries> block and QUERY_ALL_PACKAGES as they are, so the walk APK sees what the real app sees
SEES=$(python3 - "$APP/AndroidManifest.xml" <<'EOP'
import re, sys
m = open(sys.argv[1], encoding="utf-8").read()
m = re.sub(r"<!--.*?-->", "", m, flags=re.S)
out = re.findall(r"<queries>.*?</queries>", m, re.S)
if 'android:sharedUserId="android.uid.system"' in m:      # the system uid sees every package
    m += ' android:name="android.permission.QUERY_ALL_PACKAGES"'
for p in ("android.permission.QUERY_ALL_PACKAGES", "org.lumen.parental.permission.SETTINGS",
          "org.lumen.tvinput.permission.HDMI_STATE"):
    if 'android:name="%s"' % p in m:
        out.append('<uses-permission android:name="%s" />' % p)
print(" ".join(" ".join(x.split()) for x in out))
EOP
)

# an old screen's address that only redirects to a menu page, with the app's menu request receiver (MenuWalkActivity
# Request, exported as the real one is behind its signature permission): Back to the XGIMI system of lumen-about, the
# parental controls' address of Lumen Home
ABOUT=""
[ -f "$APP/src/org/lumen/about/XgimiSystemActivity.java" ] && ABOUT='<activity android:name="org.lumen.about.XgimiSystemActivity" android:exported="true" android:theme="@android:style/Theme.NoDisplay" />'
ABOUT="$ABOUT"'
    <receiver android:name="org.lumen.menu.walk.MenuWalkActivity$Request" android:exported="true"><intent-filter><action android:name="org.lumen.projector.action.SHOW_PANEL" /></intent-filter></receiver>'

cat > "$B/AndroidManifest.xml" <<EOM
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG"
    android:versionCode="9000" android:versionName="menu-walk">
  <uses-sdk android:minSdkVersion="34" android:targetSdkVersion="34" />
  <!-- the quick menu is stored in Settings.Secure (SecureQuickStore): pm grant on the emulator -->
  <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
$(for p in ${EXTRA_PERMS:-}; do echo "  <uses-permission android:name=\"$p\" />"; done)
  $SEES
  <application android:label="menu walk" android:hasCode="true">
    <activity android:name="org.lumen.menu.walk.$WALK" android:exported="true" android:launchMode="singleTask"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen" />
    <activity android:name="org.lumen.projector.panel.TextEditActivity" android:exported="false"
        android:excludeFromRecents="true" android:noHistory="true" android:taskAffinity="org.lumen.projector.textedit"
        android:windowSoftInputMode="stateAlwaysVisible|adjustNothing"
        android:theme="@android:style/Theme.Translucent.NoTitleBar.Fullscreen" />
    <activity android:name="org.lumen.projector.ui.PanelActivity" android:exported="false" android:noHistory="true"
        android:theme="@android:style/Theme.Translucent.NoTitleBar.Fullscreen" />
    $ABOUT
    <activity android:name="org.lumen.remote.keys.KeyBindActivity" android:exported="false"
        android:launchMode="singleTask" android:theme="@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen" />
  </application>
</manifest>
EOM
cp "$S/$WALK.java" "$B/src/org/lumen/menu/walk/"
"$BT/aapt2" compile --dir "$APP/res" -o "$B/res.zip"
XP=$(grep -v '^#' "$APP/R_EXTRA_PACKAGES" 2>/dev/null | grep . | tr '\n' ':' | sed 's/:$//')
"$BT/aapt2" link -o "$B/base.apk" -I "$AJ" --manifest "$B/AndroidManifest.xml" -A "$APP/assets" --custom-package "$PKG" \
  --java "$B/gen" --min-sdk-version 34 --target-sdk-version 34 ${XP:+--extra-packages "$XP"} "$B/res.zip"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -implicit:class \
  -cp "$HJ:$AJ" -sourcepath "$APP/src:$B/src:$B/gen" -d "$B/classes" \
  "$B/src/org/lumen/menu/walk/$WALK.java" "$APP/src/org/lumen/remote/keys/KeyBindActivity.java" \
  "$APP/src/org/lumen/projector/panel/TextEditActivity.java" \
  $(ls "$APP/src/org/lumen/projector/cec/CecPage.java" "$APP/src/org/lumen/about/XgimiSystemActivity.java" 2>/dev/null) \
  $(for f in ${EXTRA_SRC:-}; do ls "$APP/src/$f" 2>/dev/null; done) \
  $(find "$B/gen" -name R.java) > "$B/javac.log" 2>&1 || { head -40 "$B/javac.log"; exit 1; }
"$BT/d8" --min-api 34 --lib "$AJ" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/u.apk"
(cd "$B/dex" && zip -q -0 "$B/u.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$B/u.apk" "$B/a.apk"
"$BT/apksigner" sign --key "$G/apps/sdk/platform.pk8" --cert "$G/apps/sdk/platform.x509.pem" --out "$OUT/walk.apk" "$B/a.apk"
ls -la "$OUT/walk.apk"
