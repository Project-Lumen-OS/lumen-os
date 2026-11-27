#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# services_dexpreopt.sh ISA GC DEX2OAT OUTROOT JAR OUT: the base build's soong dexpreopt of /system/framework/services.jar
# (LineageOS 21 build/soong/dexpreopt, the command of the RS30 base build's ninja file for services, read back from it),
# run in a plain container by services.py with the base build's host tools at /hostout, its soong dexpreopt_<arch>
# folder at /boot, the frameworks/base/services profile listings at /prof and the class loader jar at /loc:
#   profman art-profile -> services.jar.prof, profman art-profile-boot -> services.jar.bprof,
#   dex2oat speed-profile with an app image -> oat/ISA/services.{odex,vdex,art}
# Every path is the one of the soong build (/out of the container): the oat file records its own name (javalib.odex,
# installed as services.odex) and the app image the host paths of its inputs.
# GC: cmc (the base was built with uffd GC: -Xgc:CMC) or cc. The boot class path is services_bcp.txt (host jars below
# @BOOT@, then the device locations), the same on every Lumen base. Same flags as soong, paths aside.
set -euo pipefail
[ $# -eq 6 ] || { echo "usage: services_dexpreopt.sh ISA GC DEX2OAT OUTROOT JAR OUT" >&2; exit 1; }
ISA=$1; GC=$2; D2O=$3; O=$4; JAR=$5; OUT=$6
T=$(dirname "$0")
case $ISA in arm|arm64) ;; *) echo "isa $ISA" >&2; exit 1 ;; esac
case $GC in cmc) GCF=(--runtime-arg -Xgc:CMC) ;; cc) GCF=() ;; *) echo "gc $GC" >&2; exit 1 ;; esac
# the soong paths below the base build's OUT_DIR (OUTROOT): the app image records the host path of the class loader jar
case $O in /out|out) ;; *) echo "outroot $O" >&2; exit 1 ;; esac
cd /   # a relative OUT_DIR (out) is below the tree root, here /
B=$O/soong/dexpreopt_$ISA; M=$O/soong/.intermediates/frameworks/base/services/services/android_common/caa8a650e05b51f5bf21a4b2271e7984
D=$M/dexpreopt/services; CL=$O/soong/system_server_dexjars
mkdir -p "$O/host" "$O/soong" "$M/aligned" "$D/oat/$ISA" "$CL" "$O/empty"
ln -sfn /hostout "$O/host/linux-x86"; ln -sfn /boot "$B"
cp /loc/com.android.location.provider.jar "$CL/"; cp "$JAR" "$CL/services.jar"; cp "$JAR" "$M/aligned/services.jar"
BCP_HOST=$(sed -n 1p "$T/services_bcp.txt" | sed "s#@BOOT@#$B#g")
BCP_DEV=$(sed -n 2p "$T/services_bcp.txt")
[ -n "$BCP_HOST" ] && [ -n "$BCP_DEV" ] || { echo "services_bcp.txt" >&2; exit 1; }
H=$O/host/linux-x86/bin
ANDROID_LOG_TAGS='*:e' "$H/profman" --create-profile-from=/prof/art-profile --output-profile-type=app \
  --apk="$M/aligned/services.jar" --dex-location=/system/framework/services.jar --reference-profile-file="$D/profile.prof"
ANDROID_LOG_TAGS='*:e' "$H/profman" --create-profile-from=/prof/art-profile-boot --output-profile-type=bprof \
  --apk="$M/aligned/services.jar" --dex-location=/system/framework/services.jar --reference-profile-file="$D/profile.bprof"
ANDROID_LOG_TAGS='*:e' "$H/$D2O" --avoid-storing-invocation \
  --runtime-arg -Xms64m --runtime-arg -Xmx512m \
  --runtime-arg -Xbootclasspath:"$BCP_HOST" --runtime-arg -Xbootclasspath-locations:"$BCP_DEV" \
  --class-loader-context="PCL[$CL/com.android.location.provider.jar]" \
  --stored-class-loader-context="PCL[/system/framework/com.android.location.provider.jar]" \
  --boot-image="$B/dex_bootjars/android/system/framework/boot.art:$B/dex_mainlinejars/android/system/framework/boot-framework-adservices.art" \
  --dex-file="$M/aligned/services.jar" --dex-location=/system/framework/services.jar --oat-file="$D/oat/$ISA/javalib.odex" \
  --android-root="$O/empty" --instruction-set="$ISA" --instruction-set-variant=generic \
  --instruction-set-features=default --no-generate-debug-info --generate-build-id --abort-on-hard-verifier-error \
  --force-determinism --no-inline-from=core-oj.jar "${GCF[@]}" --copy-dex-files=false --compiler-filter=speed-profile \
  --generate-mini-debug-info --compilation-reason=prebuilt --app-image-file="$D/oat/$ISA/javalib.art" \
  --image-format=lz4 --resolve-startup-const-strings=true --profile-file="$D/profile.prof"
rm -rf "$OUT/oat" "$OUT/services.jar.prof" "$OUT/services.jar.bprof"; mkdir -p "$OUT/oat/$ISA"
cp "$D/profile.prof" "$OUT/services.jar.prof"; cp "$D/profile.bprof" "$OUT/services.jar.bprof"
for x in odex vdex art; do cp "$D/oat/$ISA/javalib.$x" "$OUT/oat/$ISA/services.$x"; done
for f in services.jar.prof services.jar.bprof "oat/$ISA/services.odex" "oat/$ISA/services.vdex" "oat/$ISA/services.art"; do
  [ -s "$OUT/$f" ] || { echo "no $f" >&2; exit 1; }
done
echo "services_dexpreopt: $ISA $GC $D2O done"
