#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Checks of apps/LumenSystemUIOverlay (run with bash, on xgimi-llm or wherever the SDK is):
#   1. wiring: every model with a build.conf ships it (build.conf apps=, device.conf apps= where a model lists apps,
#      its steps file installs it into product/overlay, the HB and X50 tree verifiers allow the added file, the RS30
#      steps compare it in the image, the Play 6 build script builds it and its tests list the output);
#   2. the APK (built by apps/build_apk.sh with a throwaway key): a static overlay on com.android.systemui, no code,
#      exactly one resource, integer/volume_dialog_gravity, whose side android.view.Gravity resolves to RIGHT for both
#      layout directions (never LEFT: VolumeDialogImpl.initDialog rotates the expand arrow the TV layout lacks);
#   3. with SYSUI_APK=<the image's TvSystemUI.apk>: the target has the resource, and for a left-to-right language the
#      overlay value lands on the same side as the target's own value (nothing changes for those users).
# Env: ANDROID_SDK (default ~/lumen-tools/android-sdk, else ~/Library/Android/sdk), JAVA_HOME as build_apk.sh wants.
set -u
H=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$H/../.." && pwd)
APP=LumenSystemUIOverlay
F=0
bad() { echo "FAIL $*"; F=$((F + 1)); }
ok() { echo "ok   $*"; }

# android.view.Gravity.getAbsoluteGravity, then VolumeDialogImpl.isWindowGravityLeft: prints "<ltr> <rtl> <left?>"
sides() {
  python3 - "$1" <<'EOF'
import sys
g = int(sys.argv[1], 0)
REL, LEFT, RIGHT, START, END = 0x800000, 0x03, 0x05, 0x800003, 0x800005
def absolute(g, rtl):
    r = g
    if r & REL:
        if r & START == START:
            r &= ~START
            r |= RIGHT if rtl else LEFT
        elif r & END == END:
            r &= ~END
            r |= LEFT if rtl else RIGHT
        r &= ~REL
    return r
ltr, rtl = absolute(g, False), absolute(g, True)
print(ltr, rtl, "left" if any(x & LEFT == LEFT for x in (ltr, rtl)) else "right")
EOF
}

# 1. wiring
for b in "$G"/devices/*/build.conf; do
  c=$(basename "$(dirname "$b")")
  B=$(python3 "$G/tools/devices/layered.py" "$b") D=
  [ -f "$G/devices/$c/device.conf" ] && D=$(python3 "$G/tools/devices/layered.py" "$G/devices/$c/device.conf")
  echo "$B" | grep -qE "^apps=.*\b$APP\b" && ok "$c build.conf" || bad "$c build.conf apps= lacks $APP"
  if echo "$D" | grep -q '^apps='; then
    echo "$D" | grep -qE "^apps=.*\b$APP\b" && ok "$c device.conf" || bad "$c device.conf apps= lacks $APP"
  fi
  s=$(echo "$B" | sed -n 's/^steps=//p')
  grep -vE '^#' "$G/$s" | grep -P '\t(place|install|tar_add)\t' | grep -qE "(\{sys\}/|system/)product/overlay/$APP\.apk" \
    && ok "$c $s installs it" \
    || bad "$c: $s does not install $APP into product/overlay"
done
grep -q "system/product/overlay/$APP.apk" "$G/devices/g0111/image/verify_tree_x50.py" && ok "X50 verifier" || bad "verify_tree_x50.py does not allow $APP"
grep -q "system/product/overlay/$APP.apk" "$G/devices/goat_elegant/image/verify_tree.py" && ok "HB verifier" || bad "HB verify_tree.py does not allow $APP"
grep -q "same.*product/overlay/$APP.apk" "$G/devices/g0155/image/steps.tsv" && ok "RS30 image check" || bad "g0155 steps do not check $APP in the image"
grep -q "apps/$APP\" \"\$OUT/$APP.apk\"" "$G/tools/play6/build_play6.sh" && ok "Play 6 build" || bad "build_play6.sh does not build $APP"
grep -q "build/play6/$APP.apk" "$G/tools/play6/build_test.sh" && ok "Play 6 build test" || bad "build_test.sh does not list $APP"

# 2. the APK
SDK=${ANDROID_SDK:-$HOME/lumen-tools/android-sdk}; [ -d "$SDK" ] || SDK=$HOME/Library/Android/sdk
BT=${BUILD_TOOLS:-$SDK/build-tools/36.0.0}
if [ ! -x "$BT/aapt2" ]; then
  echo "skip the APK checks: no aapt2 under $BT"
else
  W=$(mktemp -d "${TMPDIR:-/tmp}/sysui_ovl.XXXXXX"); trap 'rm -rf "$W"' EXIT
  openssl req -x509 -newkey rsa:2048 -nodes -keyout "$W/k.pem" -out "$W/c.pem" -days 30 -subj /CN=throwaway 2>/dev/null
  openssl pkcs8 -topk8 -outform DER -nocrypt -in "$W/k.pem" -out "$W/k.pk8"
  if ANDROID_SDK=$SDK KEY_PK8="$W/k.pk8" KEY_PEM="$W/c.pem" BUILD_DIR="$W/b" sh "$G/apps/build_apk.sh" "$H" "$W/o.apk" > "$W/build.log" 2>&1; then
    ok "build_apk.sh"
    R=$("$BT/aapt2" dump resources "$W/o.apk")
    X=$("$BT/aapt2" dump xmltree --file AndroidManifest.xml "$W/o.apk")
    printf '%s\n' "$X" | grep -q 'targetPackage.*"com.android.systemui"' && ok "target" || bad "not an overlay on com.android.systemui"
    printf '%s\n' "$X" | grep -q 'isStatic.*=true' && ok "static" || bad "not static"
    unzip -l "$W/o.apk" | grep -q classes.dex && bad "carries code" || ok "no code"
    n=$(printf '%s\n' "$R" | grep -cE '^ +resource 0x')
    [ "$n" = 1 ] && ok "one resource" || bad "$n resources, want only integer/volume_dialog_gravity"
    v=$(printf '%s\n' "$R" | awk '$1 == "resource" { on = ($3 == "integer/volume_dialog_gravity"); next } on && $1 == "()" { print $2; exit }')
    if [ -z "$v" ]; then
      bad "no integer/volume_dialog_gravity"
    else
      set -- $(sides "$v")
      [ "$3" = right ] && [ "$1" = 21 ] && [ "$2" = 21 ] && ok "gravity $v: right|center_vertical for LTR and RTL" \
        || bad "gravity $v resolves to $1 (LTR) / $2 (RTL), a left side or not right|center_vertical"
    fi
  else
    tail -5 "$W/build.log"; bad "build_apk.sh failed"
  fi
fi

# 3. the target in the image
if [ -n "${SYSUI_APK:-}" ] && [ -x "$BT/aapt2" ] && [ -n "${v:-}" ]; then
  T=$("$BT/aapt2" dump resources "$SYSUI_APK")
  t=$(printf '%s\n' "$T" | awk '$1 == "resource" { on = ($3 == "integer/volume_dialog_gravity"); next } on && $1 == "()" { print $2; exit }')
  if [ -z "$t" ]; then
    bad "$SYSUI_APK has no integer/volume_dialog_gravity: the overlay maps nothing"
  else
    set -- $(sides "$t"); tl=$1 tr=$2
    set -- $(sides "$v")
    [ "$tl" = "$1" ] && ok "LTR side unchanged ($t -> $v both $tl)" || bad "LTR side moves: target $t gives $tl, overlay $v gives $1"
    echo "     target $t resolves to $tl (LTR) / $tr (RTL)"
  fi
fi

[ $F = 0 ] && echo "PASS lumen-systemui-overlay" || { echo "$F FAILED"; exit 1; }
