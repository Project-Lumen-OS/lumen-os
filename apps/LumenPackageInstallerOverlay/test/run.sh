#!/bin/bash
# Checks of apps/LumenPackageInstallerOverlay (run with bash, on xgimi-llm or wherever the SDK is):
#   1. wiring: every image recipe ships it (build.conf apps=, device.conf apps= where a model lists apps, the steps file
#      installs it into product/overlay, the HB and X50 tree verifiers allow the added file, the Play 6 build script
#      builds it from its recipe);
#   2. the APK (assembled from app.layers, built by apps/build_apk.sh with a throwaway key): a static overlay on
#      com.android.packageinstaller above the GSI's auto generated RRO (priority 1), no code, no strings, the Inter
#      font and its licence inside, and the two TV themes in BOTH television and television-night (aapt2 dedupes
#      identical configs, and then the GSI's RRO keeps the broken floating dialog in night mode);
#   3. with PI_APK=<the image's PackageInstaller_tv.apk>: stub/ids.txt equals the real ids of that APK.
# Env: ANDROID_SDK (default ~/lumen-tools/android-sdk, else ~/Library/Android/sdk), JAVA_HOME as build_apk.sh wants.
set -u
H=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$H/../.." && pwd)
APP=LumenPackageInstallerOverlay
F=0
bad() { echo "FAIL $*"; F=$((F + 1)); }
ok() { echo "ok   $*"; }

# 1. wiring
for c in horse_popular goat_elegant dragon_healthy g0155 g0111 g0100 h0062 h0063; do
  grep -qE "^apps=.*\b$APP\b" "$G/devices/$c/build.conf" && ok "$c build.conf" || bad "$c build.conf apps= lacks $APP"
  if grep -q '^apps=' "$G/devices/$c/device.conf"; then
    grep -qE "^apps=.*\b$APP\b" "$G/devices/$c/device.conf" && ok "$c device.conf" || bad "$c device.conf apps= lacks $APP"
  fi
  s=$(sed -n 's/^steps=//p' "$G/devices/$c/build.conf")
  grep -qE "(\{sys\}/|system/)product/overlay/$APP\.apk" "$G/$s" && ok "$c $s installs it" \
    || bad "$c: $s does not install $APP into product/overlay"
done
grep -q "system/product/overlay/$APP.apk" "$G/devices/g0111/image/verify_tree_x50.py" && ok "X50 verifier" || bad "verify_tree_x50.py does not allow $APP"
grep -q "system/product/overlay/$APP.apk" "$G/devices/goat_elegant/image/verify_tree.py" && ok "HB verifier" || bad "HB verify_tree.py does not allow $APP"
grep -q "same.*product/overlay/$APP.apk" "$G/devices/g0155/image/steps.tsv" && ok "RS30 image check" || bad "g0155 steps do not check $APP in the image"
grep -q -- "--app $APP " "$G/tools/play6/build_play6.sh" && ok "Play 6 build" || bad "build_play6.sh does not build $APP"

# 2. the APK
SDK=${ANDROID_SDK:-$HOME/lumen-tools/android-sdk}; [ -d "$SDK" ] || SDK=$HOME/Library/Android/sdk
BT=${BUILD_TOOLS:-$SDK/build-tools/36.0.0}
if [ ! -x "$BT/aapt2" ]; then
  echo "skip the APK checks: no aapt2 under $BT"
else
  W=$(mktemp -d "${TMPDIR:-/tmp}/pkgi_ovl.XXXXXX"); trap 'rm -rf "$W"' EXIT
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$W/k.pem" -out "$W/c.pem" -days 30 -subj /CN=throwaway 2>/dev/null
  openssl pkcs8 -topk8 -outform DER -nocrypt -in "$W/k.pem" -out "$W/k.pk8"
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" "$H/app.layers" --out "$W/tree" --quiet || bad "the recipe does not assemble"
  if ANDROID_SDK=$SDK KEY_PK8="$W/k.pk8" KEY_PEM="$W/c.pem" BUILD_DIR="$W/b" sh "$G/apps/build_apk.sh" "$W/tree" "$W/o.apk" > "$W/build.log" 2>&1; then
    ok "build_apk.sh"
    R=$("$BT/aapt2" dump resources "$W/o.apk")
    X=$("$BT/aapt2" dump xmltree --file AndroidManifest.xml "$W/o.apk")
    for st in Theme.AlertDialogActivity Theme.AlertDialogActivity.NoActionBar; do
      blk=$(printf '%s\n' "$R" | awk -v n="style/$st" '$1 == "resource" { on = ($3 == n); next } on')
      for cf in television television-night; do
        printf '%s\n' "$blk" | grep -q "($cf) (style)" && ok "$st ($cf)" \
          || bad "$st has no ($cf) value: aapt2 deduped it or it is missing"
      done
    done
    printf '%s\n' "$X" | grep -q 'targetPackage.*"com.android.packageinstaller"' && ok "target" || bad "not an overlay on com.android.packageinstaller"
    printf '%s\n' "$X" | grep -q 'isStatic.*=true' && ok "static" || bad "not static"
    p=$(printf '%s\n' "$X" | sed -n 's/.*priority(0x[0-9a-f]*)=\([0-9]*\).*/\1/p' | head -1)
    [ -n "$p" ] && [ "$p" -gt 1 ] && ok "priority $p" || bad "priority '${p}' not above the GSI's auto generated RRO (1)"
    printf '%s\n' "$R" | grep -qE "^ +resource 0x[0-9a-f]+ string/" && bad "carries strings (the installer's own translations must stay)" || ok "no strings"
    unzip -l "$W/o.apk" | grep -q classes.dex && bad "carries code" || ok "no code"
    unzip -l "$W/o.apk" | grep -q "res/font/lumen_inter.ttf" && ok "Inter font" || bad "no res/font/lumen_inter.ttf"
    unzip -l "$W/o.apk" | grep -q "assets/OFL-Inter.txt" && ok "font licence" || bad "no assets/OFL-Inter.txt"
  else
    tail -5 "$W/build.log"; bad "build_apk.sh failed"
  fi
fi

# 3. the stub ids against the image's installer
if [ -n "${PI_APK:-}" ] && [ -x "$BT/aapt2" ]; then
  while IFS= read -r line; do
    n=$(printf '%s' "$line" | sed 's/^com.android.packageinstaller:\([^ ]*\) = 0x00\(.*\)$/\1 7f\2/')
    set -- $n
    "$BT/aapt2" dump resources "$PI_APK" | grep -qE "resource 0x$2 $1\$" && ok "stub $1 = 0x$2" || bad "stub $1 is not 0x$2 in $PI_APK"
  done < "$H/stub/ids.txt"
fi

[ $F = 0 ] && echo "PASS lumen-pkginstaller-overlay" || { echo "$F FAILED"; exit 1; }
