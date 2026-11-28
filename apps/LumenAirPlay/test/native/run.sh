#!/bin/sh
# Lumen AirPlay native host checks: the audio timeline simulation (timeline_sim.cpp) against
# jni/glue/pcm_timeline.h and the video feed pacing simulation (pacing_sim.cpp) against
# jni/glue/video_pacing.h, the same headers the receiver library is built from.
# sh test/native/run.sh ; exit 0 = all passed.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
CXX=${CXX:-$(command -v clang++ || command -v g++)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/airplay_sim.XXXXXX")
: "${OUT:?}"
trap 'rm -rf "$OUT"' EXIT
"$CXX" -std=c++17 -O2 -Wall -Wextra -Werror -o "$OUT/timeline_sim" "$HERE/timeline_sim.cpp"
"$OUT/timeline_sim" -q
"$CXX" -std=c++17 -O2 -Wall -Wextra -Werror -o "$OUT/pacing_sim" "$HERE/pacing_sim.cpp"
"$OUT/pacing_sim" -q
