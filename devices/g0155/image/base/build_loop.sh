#!/bin/bash
# lineage_gsi_tv_arm systemimage for the RS30 (g0155): lineage/play6_build_loop.sh with uffd GC ON.
cd /src
export OUT_DIR=/out PYTHONDONTWRITEBYTECODE=1
source build/envsetup.sh
export WITH_DEXPREOPT=true WITH_DEXPREOPT_BOOT_IMG_AND_SYSTEM_SERVER_ONLY=true SKIP_ABI_CHECKS=true ALLOW_MISSING_DEPENDENCIES=true
export OVERRIDE_ENABLE_UFFD_GC=true
breakfast gsi_tv_arm || { echo "EXIT=2 breakfast $(date "+%F %T")"; exit 2; }
last=""; same=0
for attempt in $(seq 1 6); do
  echo "=== ATTEMPT $attempt $(date "+%F %T")"
  m -j${JOBS:-18} systemimage apksigner 2>&1 | tee /out/attempt.log
  if [ "${PIPESTATUS[0]}" = 0 ]; then echo "EXIT=0 $(date "+%F %T")"; exit 0; fi
  failed=$(grep -m1 "^FAILED: " /out/attempt.log | cut -c1-300)
  echo "=== FAILED STEP: $failed"
  if [ -n "$failed" ] && [ "$failed" = "$last" ]; then same=$((same+1)); else same=1; fi
  last="$failed"
  if [ $same -ge 2 ]; then echo "EXIT=1 repeats: $failed $(date "+%F %T")"; exit 1; fi
done
echo "EXIT=3 out of attempts $(date "+%F %T")"
exit 3
