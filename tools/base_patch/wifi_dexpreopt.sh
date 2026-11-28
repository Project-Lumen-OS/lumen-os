#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# wifi_dexpreopt.sh HOSTOUT BOOT IN JAR OUT: the base build's soong dexpreopt of the APEX system server jar
# /apex/com.android.wifi/javalib/service-wifi.jar (LineageOS 21 build/soong/dexpreopt, the command of the base build's
# ninja file for service-wifi, read back from it), so an image that swaps the jar ships its odex and vdex as the base
# does and the first start compiles nothing (odrefresh accepts /system artifacts by their presence; when one is missing,
# odsign holds odsign.key.done until the compile ends and init's post-fs-data waits for that key).
#   HOSTOUT  the base build's host/linux-x86 (dex2oatd64, deapexer, debugfs_static, fsck.erofs)
#   BOOT     its soong dexpreopt_arm64 folder (dex_bootjars, dex_bootjars_input, dex_mainlinejars ...)
#   IN       the system server class loader jars of the image the odex is for: com.android.location.provider.jar,
#            services.jar, org.lineageos.platform.jar and the APEXes (.apex or .capex) that hold the other ten (CLC
#            below). The odex records each jar's checksum and ART uses it only when they match the device's, so an
#            image takes its own services.jar (tools/base_patch/framework.tsv), the proof run the base's.
#   JAR      the service-wifi.jar to compile; OUT gets oat/arm64/service-wifi.{odex,vdex}
# The stored class loader context, boot class path (services_bcp.txt) and flags are soong's, paths aside.
# Proof: with the base's own jar and class loader jars it gives the base's shipped odex and vdex byte for byte
# (tools/base_patch/README.md "service-wifi.jar").
set -euo pipefail
[ $# -eq 5 ] || { echo "usage: wifi_dexpreopt.sh HOSTOUT BOOT IN JAR OUT" >&2; exit 1; }
H=$1; B=$2; IN=$3; JAR=$4; OUT=$5
T=$(cd "$(dirname "$0")" && pwd)
: "${OUT:?}"
W=$OUT/work; CL=$W/clc
rm -rf "$W"; mkdir -p "$CL" "$OUT/oat/arm64" "$W/empty"
BIN=$H/bin
# the stored class loader context of the base build (order matters: each name is its device location)
CLC="/system/framework/com.android.location.provider.jar /system/framework/services.jar
/system/framework/org.lineageos.platform.jar /apex/com.android.adservices/javalib/service-adservices.jar
/apex/com.android.adservices/javalib/service-sdksandbox.jar /apex/com.android.appsearch/javalib/service-appsearch.jar
/apex/com.android.art/javalib/service-art.jar
/apex/com.android.configinfrastructure/javalib/service-configinfrastructure.jar
/apex/com.android.healthfitness/javalib/service-healthfitness.jar /apex/com.android.media/javalib/service-media-s.jar
/apex/com.android.ondevicepersonalization/javalib/service-ondevicepersonalization.jar
/apex/com.android.permission/javalib/service-permission.jar /apex/com.android.rkpd/javalib/service-rkp.jar"
HOSTCLC=; DEVCLC=
for loc in $CLC; do
  n=${loc##*/}
  case $loc in
    /system/framework/*) cp "$IN/$n" "$CL/$n" ;;
    /apex/*)
      a=${loc#/apex/}; a=${a%%/*}
      if [ ! -d "$W/apex/$a" ]; then
        src=$IN/$a.apex
        if [ ! -f "$src" ]; then
          src=$W/$a.apex
          "$BIN/deapexer" decompress --input "$IN/$a.capex" --output "$src" >/dev/null
        fi
        mkdir -p "$W/apex"
        "$BIN/deapexer" --debugfs_path "$BIN/debugfs_static" --fsckerofs_path "$BIN/fsck.erofs" \
          extract "$src" "$W/apex/$a" >/dev/null
      fi
      cp "$W/apex/$a/javalib/$n" "$CL/$n" ;;
  esac
  HOSTCLC=${HOSTCLC:+$HOSTCLC:}$CL/$n; DEVCLC=${DEVCLC:+$DEVCLC:}$loc
done
BCP_HOST=$(sed -n 1p "$T/services_bcp.txt" | sed "s#@BOOT@#$B#g")
BCP_DEV=$(sed -n 2p "$T/services_bcp.txt")
cp "$JAR" "$W/service-wifi.jar"
ANDROID_LOG_TAGS='*:e' "$BIN/dex2oatd64" --avoid-storing-invocation \
  --runtime-arg -Xms64m --runtime-arg -Xmx512m \
  --runtime-arg -Xbootclasspath:"$BCP_HOST" --runtime-arg -Xbootclasspath-locations:"$BCP_DEV" \
  --class-loader-context="PCL[];PCL[$HOSTCLC]" --stored-class-loader-context="PCL[];PCL[$DEVCLC]" \
  --boot-image="$B/dex_bootjars/android/system/framework/boot.art:$B/dex_mainlinejars/android/system/framework/boot-framework-adservices.art" \
  --dex-file="$W/service-wifi.jar" --dex-location=/apex/com.android.wifi/javalib/service-wifi.jar \
  --oat-file="$W/javalib.odex" --android-root="$W/empty" --instruction-set=arm64 --instruction-set-variant=generic \
  --instruction-set-features=default --no-generate-debug-info --generate-build-id --abort-on-hard-verifier-error \
  --force-determinism --no-inline-from=core-oj.jar --runtime-arg -Xgc:CMC --copy-dex-files=false \
  --compiler-filter=speed --generate-mini-debug-info --compilation-reason=prebuilt
cp "$W/javalib.odex" "$OUT/oat/arm64/service-wifi.odex"; cp "$W/javalib.vdex" "$OUT/oat/arm64/service-wifi.vdex"
for x in odex vdex; do [ -s "$OUT/oat/arm64/service-wifi.$x" ] || { echo "no $x" >&2; exit 1; }; done
echo "wifi_dexpreopt: done"
