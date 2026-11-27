#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# publish.sh CODENAME VERSION KIT [--dry-run] (builder-llm; the token comes from the Mac in process memory only:
#   gh auth token | ssh xgimi-llm 'export GH_TOKEN=$(cat); bash <tree>/tools/release/publish.sh g0111 1.0.10-beta13 <kit>')
# One model's beta, whatever the model: KIT holds the release files named by tools/release/model.py ASSETS (images may
# be hard links into the build output) and, for a model with updates, KIT/channel/ with the two signed channel
# manifests and KIT/notes.md. Steps, each resumable (a rerun keeps what is already uploaded with the right size):
#   1. preflight: tools/release/verify.sh on KIT, Latest is LATEST (default z9x-1.0.10), the tag is free or our own
#      pre-release, the live channel files saved in KIT/live_before, no channel file of KIT goes below its live
#      version_code (channel_guard.py, again right before the channel upload)
#   2. pre-release TAG (target main, never Latest, never draft, body notes.md), upload every asset
#   3. download back every asset from the plain public URL, sha256 equal, verify.sh on the downloads
#   4. a model with updates: its channel files to MANIFEST_TAG, public bytes equal, signatures
#   5. Latest unchanged; PUBLISHED <url> and VERIFY_GREEN
# --dry-run uploads nothing and needs no token: steps 1 and, when TAG is already live, 3 on the live files (the check
# of a published release). The catalog of Lumen Installer is the next step (tools/release/README.md).
set -uo pipefail
C=${1:?CODENAME}; V=${2:?VERSION}; K=$(cd "${3:?KIT}" && pwd); MODE=real; [ "${4:-}" = --dry-run ] && MODE=dry
H=$(cd "$(dirname "$0")" && pwd)
eval "$(python3 -B "$H/model.py" "$C" "$V")"
# the release's deltas (tools/release/delta.py) when the kit holds them: assets beside the full package
DELTAS=$(cd "$K" && ls -- *-nogms-from-*-ota.zip *-nogms-from-*-payload_metadata.bin 2> /dev/null | tr '\n' ' ')
ASSETS="$ASSETS${DELTAS:+ $DELTAS}"
LATEST=${LATEST:-z9x-1.0.10}
B=https://github.com/$REPO/releases/download
R=$K/publish_$MODE.log; : > "$R"
export GH_PROMPT_DISABLED=1 GH_NO_UPDATE_NOTIFIER=1 GH_SPINNER_DISABLED=1 NO_COLOR=1
log() { echo "$(date -u +%T) $*" | tee -a "$R"; }
die() { log "PUBLISH_STOPPED: $*"; exit 1; }
FAIL=0; bad() { log "FAIL: $*"; FAIL=1; }
sha() { sha256sum < "$1" | cut -c1-64; }
api() { if [ -n "${GH_TOKEN:-}" ]; then gh api "repos/$REPO/$1"; else curl -sfL -m 60 "https://api.github.com/repos/$REPO/$1"; fi; }
latest() { api releases/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])'; }
asset_ok() {  # TAG NAME FILE: uploaded with this size
  api "releases/tags/$1" | python3 -c '
import json, sys
m = [a for a in json.load(sys.stdin)["assets"] if a["name"] == sys.argv[1]]
sys.exit(0 if m and m[0]["state"] == "uploaded" and m[0]["size"] == int(sys.argv[2]) else 1)' "$2" "$(stat -L -c %s "$3")"
}
fetch() {  # URL OUT: retried, cache-busting query for the small channel files
  local i; for i in $(seq 1 10); do curl -sfL -m 1800 -o "$2" "$1" && return 0; sleep 15; done; return 1
}

log "== preflight $TAG ($MODE, $KIND)"
st=$(api "releases/tags/$TAG" 2>/dev/null | python3 -c 'import json,sys; r=json.load(sys.stdin); print(r["prerelease"], r["draft"])' 2>/dev/null || echo none)
if [ $MODE = dry ] && [ "$st" != none ]; then
  log "$TAG is live ($st): the dry run checks the published files"
else
  for a in $ASSETS; do [ -f "$K/$a" ] || die "no $K/$a"; done
  bash "$H/verify.sh" "$C" "$V" "$K" >> "$R" 2>&1 || die "verify.sh on the kit (see $R)"
  log "ok kit: $(tail -1 "$R")"
fi
[ "$(latest)" = "$LATEST" ] || die "Latest is not $LATEST"
if [ "$KIND" = ota ]; then
  mkdir -p "$K/live_before"
  for f in $CHANNELS; do fetch "$B/$MANIFEST_TAG/$f" "$K/live_before/$f" || die "live $MANIFEST_TAG/$f"; done
  log "ok live $MANIFEST_TAG channel files saved in live_before (the rollback)"
  if [ -d "$K/channel" ]; then
    python3 -B "$H/channel_guard.py" $(for f in $CHANNELS; do case $f in *.json) echo "$K/channel/$f" ;; esac; done) \
      --live-dir "$K/live_before" >> "$R" 2>&1 || die "channel_guard: a channel would go below its live version_code (see $R)"
    log "ok no channel goes back (channel_guard)"
  fi
fi
case "$st" in none) log "ok $TAG has no release yet" ;; "True False") log "note: $TAG is a pre-release already" ;; *) die "$TAG exists: $st" ;; esac
if [ $MODE = real ]; then
  [ -n "${GH_TOKEN:-}" ] || die "GH_TOKEN is not set"
  [ -f "$K/notes.md" ] || die "no notes.md"
  log "== release $TAG"
  if [ "$st" = none ]; then
    gh api -X POST "repos/$REPO/releases" -f tag_name="$TAG" -f target_commitish=main -f name="$TITLE" \
      -F body=@"$K/notes.md" -F draft=false -F prerelease=true -f make_latest=false > "$K/created.json" 2>> "$R" || die "create $TAG"
  fi
  for a in $ASSETS; do
    asset_ok "$TAG" "$a" "$K/$a" && { log "ok $TAG/$a already there"; continue; }
    ok=0; for i in 1 2 3 4; do gh release upload "$TAG" "$K/$a" --repo "$REPO" --clobber >> "$R" 2>&1 && asset_ok "$TAG" "$a" "$K/$a" && ok=1 && break; sleep 20; done
    [ $ok = 1 ] || die "upload $TAG/$a (a rerun resumes)"; log "ok uploaded $TAG/$a"
  done
elif [ "$st" = none ]; then
  log "PUBLISH_DRY_RUN_OK $TAG (not live yet: kit verified, nothing uploaded)"; exit 0
fi
log "== download back $TAG"
DL=$K/dl_$MODE; rm -rf "$DL"; mkdir -p "$DL/channel"
for a in $ASSETS; do
  fetch "$B/$TAG/$a" "$DL/$a" || die "download $TAG/$a"
  if [ -f "$K/$a" ]; then [ "$(sha "$DL/$a")" = "$(sha "$K/$a")" ] && log "ok download back $a = $(sha "$K/$a")" || die "download back $a differs"
  else log "ok downloaded $a $(sha "$DL/$a")"; fi
done
if [ "$KIND" = ota ]; then
  if [ $MODE = real ]; then
    log "== $MANIFEST_TAG: the channel files"
    # the channels again right before the upload: another publish may have moved them since the preflight
    python3 -B "$H/channel_guard.py" $(for f in $CHANNELS; do case $f in *.json) echo "$K/channel/$f" ;; esac; done) \
      --live-base "$B/$MANIFEST_TAG" >> "$R" 2>&1 || die "channel_guard: a channel would go below its live version_code (see $R)"
    for f in $CHANNELS; do
      ok=0; for i in 1 2 3 4; do gh release upload "$MANIFEST_TAG" "$K/channel/$f" --repo "$REPO" --clobber >> "$R" 2>&1 && asset_ok "$MANIFEST_TAG" "$f" "$K/channel/$f" && ok=1 && break; sleep 15; done
      [ $ok = 1 ] || die "upload $MANIFEST_TAG/$f (rollback: gh release upload $MANIFEST_TAG $K/live_before/* --clobber)"
    done
  fi
  for f in $CHANNELS; do
    ok=0; for i in $(seq 1 40); do curl -sfL -m 60 -o "$DL/channel/$f" "$B/$MANIFEST_TAG/$f?t=$i$RANDOM" \
      && { [ $MODE = dry ] || [ "$(sha "$DL/channel/$f")" = "$(sha "$K/channel/$f")" ]; } && ok=1 && break; sleep 10; done
    [ $ok = 1 ] && log "ok public $MANIFEST_TAG/$f $(sha "$DL/channel/$f")" || bad "public $MANIFEST_TAG/$f is not the new bytes"
  done
fi
bash "$H/verify.sh" "$C" "$V" "$DL" "$DL/channel" >> "$R" 2>&1 && log "ok downloads: $(tail -1 "$R")" || bad "verify.sh on the downloads (see $R)"
[ "$(latest)" = "$LATEST" ] && log "ok Latest still $LATEST" || bad "Latest changed"
rm -rf "$DL"
[ $FAIL = 0 ] || die "verification red"
[ $MODE = dry ] && { log "PUBLISH_DRY_RUN_OK $TAG (live release verified, nothing uploaded)"; exit 0; }
log "PUBLISHED https://github.com/$REPO/releases/tag/$TAG"
log VERIFY_GREEN
