#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Static test: the XGIMI Z9X (registry horse_popular, model G0082) and the XGIMI X50 Ultra (registry g0111, model
# G0111) stay apart everywhere the tree names them. Both report ro.product.device horse_popular and their fastbootd
# answers the same product, so every tool must key on the model (ro.boot.xgimi.modelname), never on those. Reads the
# tree only (no projector, no network, no key folder):
#   bash tools/devices/test/x50_z9x_separation.sh
# registry   identify (horse_popular, G0111) = g0111, (horse_popular, G0082) = horse_popular, another model of
#            horse_popular = none (never the Z9X); models, hardware, installer ids, key folders and prop namespaces
#            apart; the fastbootd product is shared (so it is never the key)
# installer  the x50 and z9x profiles: model, board, own certificate, image token, LUMEN_PROP; x50 on the Z9X's
#            product image by FAMILY (no FB_PRODUCT of its own); restore file XR04E (the Z9X's is XR03D);
#            check-installer passes
# keys       devices/g0111/certs: platform, ota, ota_next three own keys (public key, not only the certificate,
#            differs from every other certificate of the tree: the Z9X release set, the test keys, the HB, the
#            Play 6, the installer's); the installer's x50 certificate is devices/g0111's ota one; FINGERPRINTS.txt
#            is right; no private key in the tree
# image      x50_prop.txt names the X50 (ro.lumen.device g0111 = the profile's LUMEN_PROP, ro.lumen.model G0111,
#            ro.z9x.device not z9x), takes away the Z9X OTA (manifest URL, partition list) and remove.txt the
#            Z9X updater
# OTA        g0111 has no OTA (ota_engine, channel, wire id none; the OTA tools refuse to publish for it); the Z9X
#            manifests and the Z9X updater name only the device z9x; check-wire passes
# Exit status 0 only when every check passes.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
CONF=$G/installer/lib/devices.conf
PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
no() { FAIL=$((FAIL + 1)); echo "FAIL $1"; }
check() {  # name command...: passes when the command exits 0
  local n=$1; shift
  if "$@"; then ok "$n"; else no "$n"; fi
}
py() { python3 -B "$G/tools/devices/devices.py" --root "$G" "$@"; }
reg() { py get "$1" "$2"; }
conf() { awk -v k="$1.$2=" '{ sub(/\r$/, "") } index($0, k) == 1 { print substr($0, length(k) + 1) }' "$CONF"; }
X=$(reg g0111 installer_id); Z=$(reg horse_popular installer_id)
[ "$X" = x50 ] && [ "$Z" = z9x ] || { echo "installer ids: g0111 '$X', horse_popular '$Z'"; exit 1; }

# ---------------------------------------------------------------- registry
check identify_x50 test "$(py identify horse_popular G0111)" = "$(printf 'g0111\tsupported')"
check identify_z9x test "$(py identify horse_popular G0082)" = "$(printf 'horse_popular\tsupported')"
check identify_other_model_none eval '! py identify horse_popular G0000 >/dev/null 2>&1'
check registry_same_family test "$(reg g0111 family)" = horse_popular
check registry_models_apart test "$(reg g0111 models)" = G0111 -a "$(reg horse_popular models)" = G0082
check registry_hardware_apart test "$(reg g0111 hardware)" != "$(reg horse_popular hardware)"
check registry_fastboot_product_shared test "$(reg g0111 fastboot_product)" = "$(reg horse_popular fastboot_product)"
check registry_keys_apart test "$(reg g0111 keys_dir)" = "~/.lumen-keys/g0111" -a "$(reg horse_popular keys_dir)" != "$(reg g0111 keys_dir)"
check registry_validate eval 'py validate >/dev/null'

# ---------------------------------------------------------------- installer profiles
check profile_models test "$(conf "$X" MODEL) $(conf "$Z" MODEL)" = "G0111 G0082"
check profile_boards test "$(conf "$X" BOARD)" = "$(reg g0111 hardware)" -a "$(conf "$Z" BOARD)" = "$(reg horse_popular hardware)"
check profile_x50_family test "$(conf "$X" FAMILY)" = "$Z" -a -z "$(conf "$X" FB_PRODUCT)" -a -n "$(conf "$Z" FB_PRODUCT)"
check profile_z9x_no_family test -z "$(conf "$Z" FAMILY)"
check profile_x50_restore_file test "$(conf "$X" RESTORE_BIN)" = XGIMI_HORSEPOPULAR_XR04E.bin
check profile_no_other_xr04e eval '! grep -v -e "^#" -e "^$X\." "$CONF" | grep -q XR04E'
check profile_lumen_prop test "$(conf "$X" LUMEN_PROP)" = "ro.lumen.device g0111" -a -z "$(conf "$Z" LUMEN_PROP)"
check profile_version_props_apart test "$(conf "$X" VERSION_PROP)" != "$(conf "$Z" VERSION_PROP)"
check profile_image_token test "$(conf "$X" IMAGE_TOKEN)" = x50
check profile_own_cert test "$(conf "$X" CERT)" = certs/x50/ota.x509.pem -a "$(conf "$Z" CERT)" = certs/ota.x509.pem
check profile_keep_product_system_ext test "$(conf "$X" KEEP)" = "vendor product system_ext"
check profile_blobs test "$(conf "$X" BLOBS_ALLOW) $(conf "$X" BLOBS_SIZE)" = "lib/x50/blobs_allow.txt 16777216"
check check_installer eval 'py check-installer >/dev/null'

# ---------------------------------------------------------------- keys (public certificates only)
CD=$G/devices/g0111/certs
spki() { openssl x509 -in "$1" -noout -pubkey 2>/dev/null | openssl pkey -pubin -outform DER 2>/dev/null | openssl dgst -sha256 -r | cut -d' ' -f1; }
der() { openssl x509 -in "$1" -outform DER 2>/dev/null | openssl dgst -sha256 -r | cut -d' ' -f1; }
bits() { openssl x509 -in "$1" -noout -text 2>/dev/null | sed -n 's/.*Public-Key: (\([0-9]*\) bit).*/\1/p' | head -n 1; }
own_ok=1
for n in platform ota ota_next; do [ -s "$CD/$n.x509.pem" ] && [ -n "$(spki "$CD/$n.x509.pem")" ] || own_ok=0; done
check keys_three_certs test "$own_ok" = 1
check keys_sizes test "$(bits "$CD/platform.x509.pem") $(bits "$CD/ota.x509.pem") $(bits "$CD/ota_next.x509.pem")" = "2048 4096 4096"
check keys_three_apart test "$(for n in platform ota ota_next; do spki "$CD/$n.x509.pem"; done | sort -u | wc -l | tr -d ' ')" = 3
others() {  # every other certificate of the tree: the Z9X release set, test keys, the other devices, the installer's
  { find "$G/apps/sdk" "$G/tools/sign" "$G/tools/play6/certs" -name '*.x509.pem' 2>/dev/null
    find "$G/devices" -path "$G/devices/g0111" -prune -o -name '*.x509.pem' -print 2>/dev/null
    find "$G/installer/certs" -path "$G/installer/certs/x50" -prune -o -name '*.x509.pem' -print 2>/dev/null
  } | LC_ALL=C sort -u
}
n_other=0; shared=""
while read -r f; do
  [ -n "$f" ] || continue
  s=$(spki "$f"); [ -n "$s" ] || continue
  n_other=$((n_other + 1))
  for n in platform ota ota_next; do [ "$s" = "$(spki "$CD/$n.x509.pem")" ] && shared="$shared ${f#"$G"/}=$n"; done
done <<EOF
$(others)
EOF
[ -z "$shared" ] || echo "     | shared public keys:$shared"
check keys_apart_from_every_other_cert test -z "$shared" -a "$n_other" -ge 5
check keys_z9x_release_cert_other test "$(spki "$G/installer/certs/ota.x509.pem")" != "$(spki "$CD/ota.x509.pem")"
check keys_installer_cert_is_the_devices cmp -s "$G/installer/certs/x50/ota.x509.pem" "$CD/ota.x509.pem"
fp() { awk -v k="$1" '$1 == k { print $2 }' "$CD/FINGERPRINTS.txt"; }
check keys_fingerprints test "$(fp platform) $(fp ota/ota) $(fp ota/ota_next)" = \
  "$(der "$CD/platform.x509.pem") $(der "$CD/ota.x509.pem") $(der "$CD/ota_next.x509.pem")"
check keys_no_private_key eval '! grep -rlE "PRIVATE KEY|^-----BEGIN (RSA |EC )?PRIVATE" "$G/devices/g0111" "$G/installer/certs" "$G/installer/lib" >/dev/null 2>&1'
check keys_no_pk8 test -z "$(find "$G/devices/g0111" "$G/installer" -name '*.pk8' -o -name '*.key' 2>/dev/null | head -n 1)"

# ---------------------------------------------------------------- image props
XP=$G/devices/g0111/image/x50_prop.txt
has() { grep -qxF -- "$1" "$XP"; }
check image_lumen_device has "+ro.lumen.device=g0111"
check image_lumen_model has "+ro.lumen.model=G0111"
check image_z9x_device_not_z9x eval '[ -n "$(sed -n "s/^ro\.z9x\.device=//p" "$XP")" ] && ! has "ro.z9x.device=z9x"'
check image_no_z9x_manifest has "-ro.z9x.ota.manifest_url"
check image_no_ab_ota_partitions has "-ro.product.ab_ota_partitions"
check image_no_z9x_updater grep -q '^system/app/Z9xUpdater[[:space:]]' "$G/devices/g0111/image/remove.txt"

# ---------------------------------------------------------------- OTA
check ota_none test "$(reg g0111 ota_engine) $(reg g0111 channel) $(reg g0111 ota_wire_id)" = "none none none"
check ota_tools_refuse_x50 eval '! (cd "$G/tools/devices/ota" && python3 -B -c "import sys, ota_conf; ota_conf.device(sys.argv[1], \"g0111\", publish=True)" "$G") >/dev/null 2>&1'
check ota_z9x_manifest_device grep -q '"device": "z9x"' "$G/tools/ota/make_manifest.py"
check ota_z9x_updater_device grep -q '!"z9x".equals(device)' "$G/apps/Z9xUpdater/src/org/lumen/updater/Preflight.java"
check ota_check_wire eval 'py check-wire >/dev/null'

echo "x50 / z9x separation: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
