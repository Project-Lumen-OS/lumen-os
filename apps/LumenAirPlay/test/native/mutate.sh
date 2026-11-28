#!/bin/sh
# Mutants of the audio timeline rules (jni/glue/pcm_timeline.h) and of the video feed pacing
# (jni/glue/video_pacing.h): each one breaks one rule in a copy of the header; timeline_sim /
# pacing_sim must fail on every one, and pass unmutated first.
# sh test/native/mutate.sh ; exit 0 = every mutant killed.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
H="$HERE/../../jni/glue/pcm_timeline.h"
CXX=${CXX:-$(command -v clang++ || command -v g++)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/airplay_mut.XXXXXX")
: "${OUT:?}"
trap 'rm -rf "$OUT"' EXIT
build() {  # build header -> run, exit status of the simulation
  "$CXX" -std=c++17 -O2 -w -DTIMELINE_HEADER="\"$1\"" -o "$OUT/sim" "$HERE/timeline_sim.cpp" && "$OUT/sim" -q > "$OUT/log" 2>&1
}
build "$H" || { echo "mutate: unmutated simulation fails"; cat "$OUT/log"; exit 1; }
killed=0; total=0
while IFS='|' read -r name from to; do
  [ -n "$name" ] || continue
  total=$((total + 1))
  python3 - "$H" "$OUT/m.h" "$from" "$to" <<'PY'
import sys
s = open(sys.argv[1]).read()
if s.count(sys.argv[3]) != 1:
    sys.exit("mutate: pattern not unique: " + sys.argv[3])
open(sys.argv[2], "w").write(s.replace(sys.argv[3], sys.argv[4]))
PY
  if build "$OUT/m.h"; then
    echo "SURVIVED $name"
  else
    killed=$((killed + 1))
    echo "killed   $name: $(grep -m1 FAIL "$OUT/log" || echo build)"
  fi
done <<'EOF'
per-packet steering (no median)|const int64_t m = mScratch[mDevN / 2];|const int64_t m = d;
no steering rate limit|const int64_t next = mPub + std::clamp(m - mPub, -room, room);|const int64_t next = m;
no steering at all|constexpr int64_t kSteerPpm = 4000;|constexpr int64_t kSteerPpm = 0;
missing audio not concealed|                w = conceal(w, missingFrames);|                (void) 0;
missing audio concealed twice|                w = conceal(w, missingFrames);|                w = conceal(conceal(w, missingFrames), missingFrames);
no clock step rule|} else if (counted - mStepStart >= kStepHoldNs && publish(w, t, false)) {|} else if (false) {
clock step rule after 100 ms|constexpr int64_t kStepHoldNs = 2000 * kMs;|constexpr int64_t kStepHoldNs = 100 * kMs;
step threshold 20 ms|constexpr int64_t kStepNs = 150 * kMs;|constexpr int64_t kStepNs = 20 * kMs;
short median (8 packets)|constexpr int kDevWindow = 512;|constexpr int kDevWindow = 8;
step anchor soft|} else if (counted - mStepStart >= kStepHoldNs && publish(w, t, false)) {|} else if (counted - mStepStart >= kStepHoldNs && publish(w, t, true)) {
EOF
# the video feed pacing rules (jni/glue/video_pacing.h) against pacing_sim.cpp
P="$HERE/../../jni/glue/video_pacing.h"
buildp() {
  "$CXX" -std=c++17 -O2 -w -DPACING_HEADER="\"$1\"" -o "$OUT/psim" "$HERE/pacing_sim.cpp" && "$OUT/psim" -q > "$OUT/log" 2>&1
}
buildp "$P" || { echo "mutate: unmutated pacing simulation fails"; cat "$OUT/log"; exit 1; }
while IFS='|' read -r name from to; do
  [ -n "$name" ] || continue
  total=$((total + 1))
  python3 - "$P" "$OUT/m.h" "$from" "$to" <<'PY'
import sys
s = open(sys.argv[1]).read()
if s.count(sys.argv[3]) != 1:
    sys.exit("mutate: pattern not unique: " + sys.argv[3])
open(sys.argv[2], "w").write(s.replace(sys.argv[3], sys.argv[4]))
PY
  if buildp "$OUT/m.h"; then
    echo "SURVIVED $name"
  else
    killed=$((killed + 1))
    echo "killed   $name: $(grep -m1 FAIL "$OUT/log" || echo build)"
  fi
done <<'EOF'
no buffer cap|        if (mAhead.size() >= kMaxAhead) t = std::max(t, mAhead.front());  /* wait for a free buffer */|        (void) 0;
1.0.12 lead (fixed 15 ms margin, no peak)|        return std::clamp(peakNs() + kMarginNs, kLeadMinNs, kLeadMaxNs);|        return std::clamp(peakNs() / 2 + 15 * kMs, 25 * kMs, kLeadMaxNs);
peak of the last frame only|        mCurMax = std::max(mCurMax, latNs);|        mCurMax = latNs;
no margin|    static constexpr int64_t kMarginNs = 25 * kMs;|    static constexpr int64_t kMarginNs = 0;
lead floor 10 ms|    static constexpr int64_t kLeadMinNs = 60 * kMs;|    static constexpr int64_t kLeadMinNs = 10 * kMs;
no lead (feed at the play time)|        int64_t t = target - leadNs(fixedNs);|        int64_t t = target;
fed frames not counted|        if (target > now) mAhead.push_back(target);|        (void) target;
EOF
echo "mutate: $killed/$total killed"
[ "$killed" = "$total" ]
