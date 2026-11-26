#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# verify_image.sh CODENAME tN [TREE]: read-only content check of a composed RS30 family trial image (devices/g0155/image/
# steps.tsv: G0155, G0170, the RS30 old line, MIRA and AURA 3) and its DSU copy on the build host, against the tree it was
# built from (TREE, default ~/lumen-src-rs30lines). One check for every model of the family: what differs per model
# (identity, product, gmpf hashes, menu profile) comes from the tree through verify_expect.py, never from this file.
# The checks: the debloat and prop rules, the key layouts, every Lumen app present, signed by the model's platform key,
# the menu profile the model's layers give, Lumen Home as the one HOME, screen mirroring, AirPlay, sysconfig, boot
# animation, the first start, public safety (no key material, no adb keys, no updater or installer app, no other
# projector's identity), the DSU copy and its hashtree.
#   bash verify_image.sh g0114 t1        exit 0 = every check passed
set -u
C=${1:?usage: verify_image.sh CODENAME tN [TREE]}
V=${2:?usage: verify_image.sh CODENAME tN [TREE]}
R=${3:-$HOME/lumen-src-rs30lines}
HERE=$(cd "$(dirname "$0")" && pwd)
EX=$(python3 -B "$HERE/verify_expect.py" "$R" "$C") || { echo "verify_image: no expectations for $C"; exit 2; }
eval "$EX"
TAG=$TAGP-$V-net
O=$HOME/$OUT
IMG=$O/$TAG/system_$TAG.img
D=$O/$V/system_dsu.img
[ -f "$D" ] || D=$O/$TAGP-$V-dsu/system_dsu.img
B=$HOME/rs30-base/out/host/linux-x86/bin
LABELS=$HOME/rs30-base/src/frameworks/native/libs/input/InputEventLabels.cpp
M=$(mktemp -d); X=$(mktemp -d); trap 'sudo umount "$M" 2>/dev/null; rmdir "$M"; rm -rf "$X" "$PROFILE"' EXIT
bad=0
ok() { echo "ok   $*"; }
no() { echo "FAIL $*"; bad=$((bad + 1)); }
chk() { if eval "$2"; then ok "$1"; else no "$1"; fi; }
[ -f "$IMG" ] && [ -f "$D" ] || { echo "verify_image: no $IMG or $D"; exit 2; }
sudo mount -t erofs -o loop,ro "$IMG" "$M"
S=$M/system
echo "== $C $MODEL ($NAME) image $TAG $(stat -c %s "$IMG") B $(sha256sum "$IMG" | cut -c1-64), tree $(git -C "$R" rev-parse --short=8 HEAD)"
chk "BUILD_INFO sha256 = the image" '[ "$(sed -n "s/^sha256=//p" "$O/$TAG/BUILD_INFO.txt")" = "$(sha256sum "$IMG" | cut -c1-64)" ]'
chk "fsck.erofs clean" '"$B/fsck.erofs" "$IMG" > /dev/null 2>&1'
# --- the t2 rules
chk "no .capex in the image" '[ -z "$(sudo find "$S" -name "*.capex")" ]'
for p in system_ext/priv-app/LineageSetupWizard system_ext/etc/permissions/privapp_whitelist_org.lineageos.setupwizard.xml \
         product/priv-app/TvSampleLeanbackLauncher system_ext/priv-app/Updater system_ext/priv-app/EmulatorRadioConfig \
         product/overlay/framework-res__emulator__auto_generated_characteristics_rro.apk product/overlay/EmulationPixel8 \
         product/overlay/SystemUIEmulationPixel8 product/overlay/LineageGoogleSetupWraithOverlay \
         product/priv-app/TVLauncherNoGMS product/priv-app/TVRecommendationsNoGMS product/priv-app/LineageCustomizer \
         product/priv-app/TvProvision; do
  chk "debloated $p" '! sudo test -e "$S/$p"'
done
chk "no LineageSetupWizard RRO" '[ -z "$(ls "$S/product/overlay" | grep LineageSetupWizard)" ]'
for p in system_ext/apex/com.android.vndk.v34.apex; do chk "kept $p" 'sudo test -e "$S/$p"'; done
PP=$S/product/etc/build.prop
for kv in persist.sys.usb.config=mtp ro.build.characteristics=tv ro.lumen.device=$C ro.lumen.model=$MODEL \
          ro.dalvik.vm.enable_uffd_gc=true ro.xgimi.product.name=$PRODUCT; do
  chk "product prop $kv" 'sudo grep -qx "$kv" "$PP"'
done
chk "no adb USB config in product" '! sudo grep -q "^persist.sys.usb.config=adb" "$PP"'
echo "   build id: $(sudo grep '^ro.lumen.build_id=' "$PP")"
DU=$(sudo sed -n 's/^ro.build.date.utc=//p' "$S/build.prop")
chk "files dated at the build (mtime = ro.build.date.utc $DU, not a fixed old epoch)" '[ "$(stat -c %Y "$S/build.prop")" = "$DU" ] && [ "$(stat -c %Y "$S/priv-app/X50Projector/X50Projector.apk")" = "$DU" ] && [ "$DU" -gt 1767225600 ]'
chk "arm32 only (abilist armeabi-v7a,armeabi, no lib64)" 'sudo grep -qx "ro.system.product.cpu.abilist=armeabi-v7a,armeabi" "$S/build.prop" && ! sudo test -e "$S/lib64"'
K=$S/product/usr/keylayout
chk "14 Lumen key layouts + the base's Xbox one" '[ "$(ls "$K" | wc -l)" = 15 ]'
chk "8 input configs" '[ "$(ls "$S/product/usr/idc" | wc -l)" = 8 ]'
L=$K/Vendor_000d_Product_3840.kl
for kv in "113 STB_POWER WAKE" "185 STB_POWER WAKE" "66 SETTINGS" "102 HOME" "127 TV_MEDIA_CONTEXT_MENU" "28 DPAD_CENTER" "1 BACK"; do
  sc=${kv%% *} key=${kv#* }; key=${key// /[[:space:]]+}
  chk "3840 $sc = ${kv#* }" 'sudo grep -qE "^key[[:space:]]+$sc[[:space:]]+$key([[:space:]]|$)" "$L"'
done
for kv in "172 TV_ZOOM_MODE" "217 TV_TIMER_PROGRAMMING" "234 TV_NUMBER_ENTRY"; do
  sc=${kv%% *} key=${kv#* }
  chk "3840 $sc = $key (rs30_menu_keys.py, the menu's picture keys)" 'sudo grep -qE "^key[[:space:]]+$sc[[:space:]]+$key([[:space:]]|$)" "$L"'
done
# key-bind (Remote buttons): the RS30 remote's two custom keys are MACRO_1 / MACRO_2 (overlay/v1/keylayout), unmapped
# up to beta 4
for kv in "135 MACRO_1" "136 MACRO_2"; do
  sc=${kv%% *} key=${kv#* }
  chk "3840 $sc = $key (Remote buttons)" 'sudo grep -qE "^key[[:space:]]+$sc[[:space:]]+$key([[:space:]]|$)" "$L"'
done
# rs30-power: STB_POWER only on the remote power scancodes 113 / 185 (x50_power_keys.py REMOTE), always with WAKE
kl() { sudo cat "$K"/*.kl | sed 's/#.*//'; }
chk "STB_POWER only on 113 / 185" '[ -z "$(kl | awk '"'"'$1 == "key" && $3 == "STB_POWER" && $2 != 113 && $2 != 185'"'"')" ]'
chk "every STB_POWER key wakes" '[ -z "$(kl | awk '"'"'$1 == "key" && $3 == "STB_POWER" && $4 != "WAKE"'"'"')" ]'
known=$(grep -oE 'DEFINE_KEYCODE\(([A-Z0-9_]+)\)' "$LABELS" | sed 's/DEFINE_KEYCODE(\(.*\))/\1/' | sort -u)
unknown=$(sudo cat "$K"/*.kl | sed 's/#.*//' | awk '$1=="key" && $2 ~ /^[0-9]+$/ {print $3} $1=="key" && $2=="usage" {print $4}' | sort -u | comm -23 - <(echo "$known"))
chk "every key label is known to the framework" '[ -z "$unknown" ]'
# --- every Lumen app of the image: present, = the signed stage of the Mac (sha256 list copied next to the image), one
#     signer (the model's platform certificate of the tree)
WANT=$(openssl x509 -in "$R/devices/$C/certs/platform.x509.pem" -outform DER | sha256sum | cut -c1-64)
APPS="priv-app/Z9xSetup/Z9xSetup.apk app/X50EyeGuard/X50EyeGuard.apk priv-app/X50Projector/X50Projector.apk product/overlay/X50PowerOverlay.apk
app/X50TvInput/X50TvInput.apk priv-app/Z9xHome/Z9xHome.apk priv-app/LumenMiracast/LumenMiracast.apk
product/overlay/LumenWifiP2pOverlay.apk app/Z9xAirPlay/Z9xAirPlay.apk"
for a in $APPS; do
  n=$(basename "$a" .apk)
  if sudo test -s "$S/$a"; then sudo cp "$S/$a" "$X/$n.apk"; sudo chown "$(id -u)" "$X/$n.apk"; else no "$a missing"; continue; fi
  got=$("$B/apksigner" verify --print-certs "$X/$n.apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  chk "$n: $C platform signer, verified" '[ "$got" = "$WANT" ]'
done
pkg() { "$B/aapt2" dump packagename "$X/$1.apk" 2>/dev/null; }
for kv in Z9xSetup=org.lumen.setup X50Projector=org.lumen.x50.projector X50EyeGuard=org.lumen.x50.eyeguard X50TvInput=org.lumen.tvinput \
          Z9xHome=org.lumen.home LumenMiracast=org.lumen.miracast Z9xAirPlay=org.lumen.airplay \
          LumenWifiP2pOverlay=org.lumen.overlay.wifip2p; do
  chk "${kv%%=*} is ${kv#*=}" '[ "$(pkg "${kv%%=*}")" = "${kv#*=}" ]'
done
badging() { "$B/aapt2" dump badging "$X/$1.apk" 2>/dev/null; }
chk "LumenMiracast version 111 1.0.11" 'badging LumenMiracast | grep -q "versionCode=.111. versionName=.1.0.11."'
xt() { "$B/aapt2" dump xmltree --file AndroidManifest.xml "$X/$1.apk" 2>/dev/null; }
chk "Lumen Home has a HOME filter" 'xt Z9xHome | grep -q "android.intent.category.HOME"'
chk "Wi-Fi Direct overlay: static, on com.android.wifi.resources" 'xt LumenWifiP2pOverlay | grep -q "com.android.wifi.resources" && xt LumenWifiP2pOverlay | grep -qE "isStatic\(0x[0-9a-f]+\)=(true|\(type 0x12\)0xffffffff)"'
chk "Wi-Fi Direct device type television (7-0050F204-1)" '"$B/aapt2" dump resources "$X/LumenWifiP2pOverlay.apk" 2>/dev/null | grep -A1 config_wifi_p2p_device_type | grep -q "7-0050F204-1"'
chk "AirPlay armeabi-v7a library stored, page-aligned" 'python3 -B "$R/devices/_image/checks/airplay_libs.py" "$X/Z9xAirPlay.apk"'
dexs() { unzip -p "$X/$1.apk" 'classes*.dex' 2>/dev/null; }
chk "Lumen Setup carries the first-start language (FirstLocale) and the remote step" 'dexs Z9xSetup | grep -aq "Lorg/lumen/setup/FirstLocale;" && dexs Z9xSetup | grep -aq "Lorg/lumen/setup/steps/RemoteStep;"'
chk "X50Projector carries the statistics client" '[ "$(dexs X50Projector | grep -ac "Lorg/lumen/stats/StatsClient;")" -gt 0 ]'
# the profile the APK ships is the model's effective one (its layers merged by menu_layers.py, never the raw layer file)
chk "X50Projector menu profile = the effective profile of devices/$C/menu.json (mirroring listed)" 'unzip -p "$X/X50Projector.apk" assets/lumen_menu/menu.json | python3 -c "import json,sys; sys.exit(0 if json.load(sys.stdin) == json.load(open(sys.argv[1])) else 1)" "$PROFILE" && grep -q "\"mirroring\"" "$PROFILE"'
chk "X50Projector carries the game menu and the quick menu D" 'dexs X50Projector | grep -aq "Lorg/lumen/projector/panel/GameMenu;" && dexs X50Projector | grep -aq "Lorg/lumen/projector/panel/QpdMirror;"'
# Lumen Home picks the projector app by the image: ro.lumen.device of every RS30 family image (ProjectorTarget) ->
# org.lumen.x50.projector. The prop lives in the product build.prop of this recipe (the image has no ro.z9x.device).
chk "Lumen Home knows this image (ro.lumen.device $C -> org.lumen.x50.projector)" 'dexs Z9xHome | grep -aq "$C" && dexs Z9xHome | grep -aq "org.lumen.x50.projector" && sudo grep -qx "ro.lumen.device=$C" "$S/product/etc/build.prop" && ! sudo grep -q "^ro.z9x.device=" "$S/build.prop" "$S/product/etc/build.prop"'
# rs30-power and rs30-menu: the power key rule, Turn off screen, the RS30 picture rows, the screen shift with the head
chk "X50Projector carries the power key rule and the power menu" 'dexs X50Projector | grep -aq "Lorg/lumen/menu/PowerPress;" && dexs X50Projector | grep -aq "Lorg/lumen/projector/panel/PowerMenu;"'
# beta2 (t6): the HB power rule (short = sleep, held 700 ms = the menu), the eye guard's detector per model and its reason card
chk "PowerPress is the HB rule (HOLD_MS, no stock HOLD_REPEATS)" 'dexs X50Projector | grep -aq "HOLD_MS" && ! dexs X50Projector | grep -aq "HOLD_REPEATS"'
chk "X50EyeGuard names the reason (BlankCause) and reads the detector from the model table" 'dexs X50EyeGuard | grep -aq "Lorg/lumen/x50/eyeguard/BlankCause;" && dexs X50EyeGuard | grep -aq "inside xgimi_middle"'
chk "X50EyeGuard reason texts (eye_reason_late, eye_reason_detector_missing)" '"$B/aapt2" dump resources "$X/X50EyeGuard.apk" 2>/dev/null | grep -q "string/eye_reason_late" && "$B/aapt2" dump resources "$X/X50EyeGuard.apk" 2>/dev/null | grep -q "string/eye_reason_detector_missing"'
chk "X50Projector carries the RS30 picture rows and the pan-tilt screen shift" 'dexs X50Projector | grep -aq "Lorg/lumen/x50/projector/PictureSequences;" && dexs X50Projector | grep -aq "Lorg/lumen/x50/projector/PanTiltMove;"'
# beta3 (t8): the shared remote module, mouse clicks on Lumen Home, the Setup pages through lumen-system
chk "X50Projector pairs through the shared remote module (RemotePairing, RemoteAdvert), no X50 copy" 'dexs X50Projector | grep -aq "Lorg/lumen/remote/RemotePairing;" && dexs X50Projector | grep -aq "Lorg/lumen/remote/RemoteAdvert;" && ! dexs X50Projector | grep -aq "Lorg/lumen/x50/remote/"'
# Home 2.0 (6b753ff4, 2026-11-18) dropped TapGesture: the click target search is Hit
chk "Lumen Home takes mouse and touch clicks (Hit)" 'dexs Z9xHome | grep -aq "Lorg/lumen/home/ui/Hit;"'
chk "Lumen Setup opens its Android pages through lumen-system (SystemPages)" 'dexs Z9xSetup | grep -aq "Lorg/lumen/system/SystemPages;"'
# beta4 (t9): the light rows from each model's stock (StockLight) and the RS30 iris row (GmTvManager 199 / 200)
chk "X50Projector carries StockLight and IrisRule, no Menu.laserRows left" 'dexs X50Projector | grep -aq "Lorg/lumen/x50/projector/StockLight;" && dexs X50Projector | grep -aq "Lorg/lumen/menu/IrisRule;" && ! dexs X50Projector | grep -aq "laserRows"'
chk "X50Projector calls the iris (SetIrisLevel 0;20;30;35;40;50;55;70 with the single iris, GetIrisLevel)" 'dexs X50Projector | grep -aq "0;20;30;35;40;50;55;70" && dexs X50Projector | grep -aq "GetIrisLevel"'
hashes_in() { [ -n "$HASHES" ] || return 1; local h; for h in $HASHES; do dexs X50Projector | grep -aq "$h" || return 1; done; }
mj() { unzip -p "$X/X50Projector.apk" assets/lumen_menu/menu.json; }
# the light rows of the shipped profile: the level 1..10 and Turn off screen wherever the model's stock has the light
# level, its iris options (if any) as the profile gives them (StockLight and stock_picture.py hold them against the stock)
chk "the image menu profile offers the light level 1..10 and Turn off screen" 'mj | python3 -c "import json,sys; f=json.load(sys.stdin)[\"features\"]; sys.exit(0 if f.get(\"lamp\")=={\"min\": 1, \"max\": 10} and \"lamp.off\" in f else 1)"'
echo "   iris: $(mj | python3 -c 'import json,sys; print(json.load(sys.stdin)["features"].get("picture.iris", {}).get("options", "none"))')"
chk "X50Projector carries the gmpf hashes of the $MODEL rule ($HASHES) and the 107 page flag guard" 'hashes_in && dexs X50Projector | grep -aq "SHIFT_PAGE_107"'
chk "the iris row title is Диафрагма in Russian" '"$B/aapt2" dump resources "$X/X50Projector.apk" 2>/dev/null | grep -A30 "string/lumen_m_iris\$" | grep -qF "(ru) \"Диафрагма\""'
sudo cp "$S/system_ext/priv-app/TvSettingsTwoPanel/TvSettingsTwoPanel.apk" "$X/TvSettingsTwoPanel.apk" 2>/dev/null && sudo chown "$(id -u)" "$X/TvSettingsTwoPanel.apk"
chk "TvSettings has Remotes & Accessories for Setup (SLICE_SETTINGS + the accessories slice provider)" 'xt TvSettingsTwoPanel | grep -q "\"android.settings.SLICE_SETTINGS\"" && xt TvSettingsTwoPanel | grep -q "\"com.android.tv.settings.accessories.sliceprovider\""'
# one line per <key> of the overlay: "keyCode component dispatchWhenNonInteractive" (aapt2 sorts the attributes)
gk() { "$B/aapt2" dump xmltree --file res/xml/global_keys.xml "$X/X50PowerOverlay.apk" 2>/dev/null | awk '
  function out() { if (k != "") print k, c, (d == "" ? "-" : d) }
  /E: key/ { out(); k = ""; c = ""; d = ""; next }
  /A: keyCode=/ { split($0, a, "\""); k = a[2] }
  /A: component=/ { split($0, a, "\""); c = a[2] }
  /A: dispatchWhenNonInteractive=/ { d = "true" }
  END { out() }'; }
for k in STB_POWER TV_ZOOM_MODE TV_TIMER_PROGRAMMING TV_NUMBER_ENTRY SETTINGS; do
  chk "global key KEYCODE_$k -> org.lumen.x50.projector" '[ "$(gk | awk -v k=KEYCODE_$k '"'"'$1 == k {print $2}'"'"')" = "org.lumen.x50.projector/.KeyReceiver" ]'
done
chk "KEYCODE_STB_POWER not dispatched while asleep (it only wakes)" '[ "$(gk | awk '"'"'$1 == "KEYCODE_STB_POWER" {print $3}'"'"')" = "-" ]'
# --- permissions, sysconfig, boot animation
for f in etc/permissions/privapp-permissions-z9xsetup.xml etc/permissions/privapp-permissions-z9xhome.xml etc/permissions/privapp-permissions-lumen-miracast.xml \
         product/etc/default-permissions/z9x-home.xml product/etc/default-permissions/lumen-miracast.xml \
         product/etc/default-permissions/z9x-airplay.xml product/etc/default-permissions/x50-projector.xml; do
  chk "present $f" 'sudo test -s "$S/$f"'
done
chk "sysconfig z9x.xml = the tree's" 'sudo cmp -s "$S/etc/sysconfig/z9x.xml" "$R/overlay/v1/z9x-sysconfig-nogms.xml"'
chk "sysconfig rs30.xml = the tree's" 'sudo cmp -s "$S/etc/sysconfig/rs30.xml" "$R/devices/g0155/image/rs30-sysconfig.xml"'
for p in org.lumen.setup org.lumen.x50.projector org.lumen.x50.eyeguard org.lumen.home org.lumen.miracast org.lumen.airplay org.lumen.tvinput; do
  chk "$p starts not stopped" 'sudo cat "$S"/etc/sysconfig/*.xml | grep -q "initial-package-state package=\"$p\" stopped=\"false\""'
done
chk "Lumen boot animation (= the tree's)" 'sudo cmp -s "$S/product/media/bootanimation.zip" "$R/overlay/v1/bootanimation.zip"'
chk "the dark boot animation link points at it" '[ "$(sudo readlink "$S/product/media/bootanimation-dark.zip")" = bootanimation.zip ]'
# --- HOME: exactly one launcher besides provisioning and the settings fallback
homes=""
for a in $(sudo find "$S" -path '*priv-app/*/*.apk' 2>/dev/null); do
  sudo cp "$a" "$X/h.apk"; sudo chown "$(id -u)" "$X/h.apk"
  "$B/aapt2" dump xmltree --file AndroidManifest.xml "$X/h.apk" 2>/dev/null | grep -q "android.intent.category.HOME" && homes="$homes ${a#$S/}"
done
echo "   HOME filters:$homes"
# besides Lumen Setup and Lumen Home only ManagedProvisioning (its HOME activity disabled in the manifest) and the
# settings fallback (TvSettingsTwoPanel FallbackHome) have a HOME filter: no second launcher, no TvProvision
chk "HOME only in Lumen Setup, Lumen Home, ManagedProvisioning and the settings fallback" '[ -z "$(echo $homes | tr " " "\n" | grep -vE "^(priv-app/Z9xSetup/|priv-app/Z9xHome/|priv-app/ManagedProvisioning/|system_ext/priv-app/TvSettings(TwoPanel)?/)")" ] && echo " $homes " | grep -q " priv-app/Z9xHome/Z9xHome.apk " && echo " $homes " | grep -q " priv-app/Z9xSetup/Z9xSetup.apk "'
# the builder's rule on the mounted image: Setup 10, Home 3, no live HOME filter of another priv-app at 3 or above
chk "HOME resolution: no tie with Lumen Home (tree.py apk-roles, the beta2 chooser)" 'sudo python3 -I "$R/tools/build/tree.py" apk-roles "$S" "$R/tools/lumen" priv-app/Z9xSetup/Z9xSetup.apk:org.lumen.setup:setup priv-app/Z9xHome/Z9xHome.apk:org.lumen.home:home > "$X/roles.log" 2>&1'
sed 's/^/     /' "$X/roles.log"
# --- beta3: the one first-start setting (English over the vendor's zh-CN, no fixed zone over Asia/Shanghai)
chk "first start: first_start.py check over the image" 'sudo python3 -B "$R/devices/_image/checks/first_start.py" check "$R/devices/_image/first_start_prop.txt" "$S" > "$X/fs.log" 2>&1'
lastv() { sudo grep "^$1=" "$PP" | tail -1 | cut -d= -f2-; }
chk "first start: ro.product.locale en-US, persist.sys.language / country empty last in product" '[ "$(lastv ro.product.locale)" = en-US ] && [ -z "$(lastv persist.sys.language)" ] && [ -z "$(lastv persist.sys.country)" ] && sudo grep -q "^persist.sys.language=" "$PP"'
chk "first start: no fixed time zone in the image" '! sudo grep -rqs "^persist.sys.timezone=." "$S/build.prop" "$PP" "$S/system_ext/etc/build.prop"'
# --- public safety (check_public style)
chk "no private key material in text files" '[ -z "$(sudo grep -rlE "BEGIN (RSA |EC |ENCRYPTED )?PRIVATE KEY" "$S/etc" "$S/product/etc" "$S/system_ext/etc" 2>/dev/null)" ]'
chk "no .pk8 / .key files" '[ -z "$(sudo find "$S" \( -name "*.pk8" -o -name "*.key" \) 2>/dev/null)" ]'
chk "no adb keys in the image" '[ -z "$(sudo find "$S" -name "adb_keys*" 2>/dev/null)" ]'
for a in Z9xUpdater LumenUpdater Play6Updater LumenDsuInstall LumenInstaller LumenFlasher Z9xProjector HbProjector Play6Projector GmsCore Phonesky; do
  chk "no $a" '[ -z "$(sudo find "$S" -maxdepth 4 -type d -name "$a" 2>/dev/null)" ]'
done
chk "identity only $C (no other ro.lumen.device)" '[ "$(sudo grep -rh "^ro.lumen.device=" "$S" --include=build.prop | sort -u)" = "ro.lumen.device=$C" ]'
chk "no OTA certificate of a Lumen channel (no updater, trial only)" '! sudo test -e "$S/etc/security/otacerts.zip" || [ "$(sudo unzip -l "$S/etc/security/otacerts.zip" 2>/dev/null | grep -c lumen)" = 0 ]'
sudo umount "$M"
# --- the DSU copy
echo "== DSU copy"
n=$(stat -c %s "$IMG")
chk "DSU copy starts with the image ($n B)" '[ "$(head -c "$n" "$D" | sha256sum | cut -c1-64)" = "$(sha256sum "$IMG" | cut -c1-64)" ]'
ln -sf "$D" "$X/system.img"
chk "DSU hashtree verifies (avbtool verify_image)" '"$B/avbtool" verify_image --image "$X/system.img" > "$X/avb.log" 2>&1'
"$B/avbtool" info_image --image "$D" 2>&1 | grep -E 'Image size|Original image size|Partition Name|Hash Algorithm|Algorithm:' | sed 's/^/     /'
echo "   dsu $(stat -c %s "$D") B $(sha256sum "$D" | cut -c1-64)"
echo "verify_image $C: $bad failed"
[ $bad = 0 ]
