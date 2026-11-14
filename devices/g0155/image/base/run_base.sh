#!/bin/bash
# builder-llm: the RS30 (g0155) arm32 base = LineageOS 21 lineage_gsi_tv_arm from the pinned Lumen manifest,
# the HB/Play 6 patch set (lineage/patches/play6 0001-0006) plus the MTK Codec2 store patch, built WITH uffd GC
# (the RS30 kernel is arm64 with CONFIG_USERFAULTFD=y; ART 14 then always runs CMC, so a CC boot image would
# make odrefresh compile before zygote and the vendor PWM wait reboots the projector).
# Usage: nohup bash ~/rs30-base/run_base.sh > ~/rs30-base/run.log 2>&1 < /dev/null &
set -euo pipefail
B=~/rs30-base
IMG=lineage-builder
MEM=${MEM:-40g}
JOBS=${JOBS:-18}
FLOOR_MB=${FLOOR_MB:-6144}
log() { echo "=== $(date '+%F %T') $*"; }

guard() {
  sleep 30
  while sleep 5; do
    docker ps -q --filter "name=^$1$" | grep -q . || return 0
    a=$(awk '/^MemAvailable:/{a=$2} /^SwapFree:/{s=$2} END{print int((a+s)/1024)}' /proc/meminfo)
    if [ "$a" -lt "$FLOOR_MB" ]; then log "host memory $a MB < $FLOOR_MB MB: stopping $1"; docker stop "$1"; return 0; fi
  done
}

mkdir -p "$B/src" "$B/out"
log "sync start"
docker rm -f rs30-sync >/dev/null 2>&1 || true
docker run --rm --name rs30-sync --memory="$MEM" -v "$B/src:/src" -v "$B/in:/in:ro" "$IMG" bash -c '
  set -e; cd /src
  M=/src/.lumen_manifest
  if [ ! -e .repo/.lumen_pinned ]; then
    rm -rf .repo "$M"; mkdir -p "$M"; cd "$M"; git init -q -b main
    sed "s#fetch=\"\.\.\"#fetch=\"https://github.com/\"#" /in/lumen-lineage-21.xml > default.xml
    git add default.xml; git commit -qm pinned; cd /src
    repo init -q -u "$M" -b main -m default.xml --depth=1 --git-lfs
    touch .repo/.lumen_pinned
  fi
  for i in 1 2 3 4; do repo sync -q -c -j16 --no-tags --no-clone-bundle --force-sync && exit 0; echo "sync retry $i"; sleep 30; done
  exit 1' &
D=$!; guard rs30-sync & wait $D
log "sync done: $(du -sh "$B/src" | cut -f1)"

log "patches"
docker run --rm -v "$B/src:/src" -v "$B/in:/in:ro" "$IMG" bash -c '
  set -e; cd /src
  if [ ! -e .lumen_patched ]; then
    for p in /in/patches/play6/*.patch; do echo "apply $p"; patch -p1 --no-backup-if-mismatch -s < "$p"; done
    (cd frameworks/av && patch -p1 --no-backup-if-mismatch -s < /in/patches/frameworks_av_C2Store_igba_hidl.patch)
    touch .lumen_patched
  fi'

log "build start (JOBS=$JOBS MEM=$MEM)"
docker rm -f rs30-build >/dev/null 2>&1 || true
docker run --name rs30-build --memory="$MEM" --memory-swap="$MEM" -e JOBS="$JOBS" \
  -v "$B/src:/src" -v "$B/out:/out" -v "$B/in/build_loop.sh:/build_loop.sh:ro" "$IMG" bash /build_loop.sh &
D=$!; guard rs30-build & wait $D || true
rc=$(docker inspect -f '{{.State.ExitCode}}' rs30-build)
log "build exit $rc"
[ "$rc" = 0 ] || exit "$rc"
P=$B/out/target/product/generic
sha256sum "$P/system.img" | tee "$B/system.img.sha256"
cat "$B/out/soong/dexpreopt/uffd_gc_flag.txt" 2>/dev/null | sed 's/^/uffd_gc_flag: /' || true
grep -h 'enable_uffd_gc' "$P/system/build.prop" "$P/system/product/etc/build.prop" 2>/dev/null || true
log "DONE"
