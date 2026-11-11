#!/bin/sh
# Sound page state and audio port rules (a test Z9X trial 2026-11-11): sh test/sound/run.sh
# SoundStateTest: the Sound enhancement row shows the STORED process as stock does (DTS stays DTS before the output
# path is reported, so Harman Kardon is always a way out), hints and the equalizer agree with it; AudioPortRules:
# probes with back-off, never the framework's 100 ms loop. The greps pin the wiring.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC="$HERE/src/org/lumen/projector"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$SRC/panel/SoundState.java" "$SRC/audio/AudioPortRules.java" \
  "$HERE/test/sound/AudioPortRulesProbe.java" "$HERE/test/sound/SoundStateTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.SoundStateTest
QPC="$SRC/panel/QuickPanelController.java"
AP="$SRC/audio/AudioPorts.java"
bad=0
fail() { echo "FAIL $1"; bad=1; }
has() { grep -qF -- "$2" "$1" || fail "$3"; }
# ---- the Sound page reads SoundState (stock mapping), not the old path-gated SoundProfiles.fromState
has "$QPC" 'setChoice(soundEnh, ok ? SoundState.enhancementRow(sn.soundProcess, sn.dtsOn) : -1, ok && sn.soundProcess != null, true);' \
  "Sound enhancement row not from SoundState.enhancementRow"
grep -q 'SoundProfiles.fromState(' "$QPC" && fail "QuickPanelController still maps DTS to Harman before the path is reported"
has "$QPC" 'boolean amp = ok && SoundState.eqUsable(sn.soundProcess, sn.dtsOn);' "equalizer not from SoundState"
has "$QPC" 'boolean dts = ok && SoundState.dtsStored(sn.soundProcess, sn.dtsOn);' "equalizer DTS value not from SoundState"
has "$QPC" 'int hk = ok ? SoundState.hint(sn.soundProcess, sn.dtsOn, sn.soundPathActive, sn.soundPath,' "hint not from SoundState"
has "$QPC" 'SoundProfiles.Result r = SoundProfiles.apply(app, g, prof);' "the choice no longer goes through SoundProfiles.apply (44 + 27)"
# ---- audio device callbacks only through AudioPorts (no 100 ms listAudioPorts loop while the policy is down)
users=$(grep -rlF 'registerAudioDeviceCallback(' "$SRC" --include=*.java | sed "s|$SRC/||" | sort | tr '\n' ' ')
# (cec/EarcLink registers its own, but EarcLink.install runs only from App through AudioPorts.whenUp, checked below)
[ "$users" = "audio/AudioPorts.java cec/EarcLink.java " ] || fail "registerAudioDeviceCallback outside AudioPorts: $users"
callers=$(grep -rlF 'EarcLink.install(' "$SRC" --include=*.java | sed "s|$SRC/||" | sort | tr '\n' ' ')
[ "$callers" = "App.java " ] || fail "EarcLink.install called from: $callers(want App.java, through AudioPorts.whenUp)"
grep -rlF 'registerAudioPortUpdateListener(' "$SRC" --include=*.java && fail "an audio port listener outside AudioPorts"
n=$(grep -c 'AudioPorts.registerDeviceCallback(sApp, new AudioDeviceCallback() {' "$SRC/audio/AudioPathReporter.java" "$SRC/audio/AudioOutputChoice.java" | awk -F: '{s+=$2} END {print s}')
[ "$n" = 2 ] || fail "AudioPathReporter / AudioOutputChoice: $n callbacks through AudioPorts (want 2)"
grep -A2 'org.lumen.projector.audio.AudioPorts.whenUp(this, new android.os.Handler(android.os.Looper.getMainLooper()),' "$SRC/App.java" \
  | grep -qF '"EarcLink", () -> org.lumen.projector.cec.EarcLink.install(this));' || fail "EarcLink installed before the audio policy answers"
n=$(grep -c 'EarcLink.install(' "$SRC/App.java" || true)
[ "$n" = 1 ] && grep 'EarcLink.install(' "$SRC/App.java" | grep -qF '"EarcLink", () -> org.lumen.projector.cec.EarcLink.install(this));' \
  || fail "EarcLink installed outside AudioPorts.whenUp ($n calls in App)"
has "$AP" 'next = AudioPortRules.backoffMs(sFailures);' "probe delay not from AudioPortRules.backoffMs"
has "$AP" 'boolean up = AudioPortRules.policyUp(outputs);' "policy check not from AudioPortRules.policyUp"
has "$AP" 'sProbe.postDelayed(AudioPorts::probe, next);' "no back-off between probes"
python3 - "$HERE/res" <<'EOF' || bad=1
import os, re, sys, xml.etree.ElementTree as ET
res = sys.argv[1]; bad = 0
dirs = sorted(d for d in os.listdir(res) if d.startswith('values-') and os.path.exists(os.path.join(res, d, 'strings.xml')))
for d in ['values'] + dirs:
    p = os.path.join(res, d, 'strings_trial.xml')
    if not os.path.exists(p):
        print('FAIL strings: no %s' % p); bad = 1; continue
    s = {e.get('name'): ''.join(e.itertext()) for e in ET.parse(p).getroot().iter('string')}
    if set(s) != {'trial_no_sound_title', 'trial_no_sound_text'}:
        print('FAIL strings: %s keys %s' % (d, sorted(s))); bad = 1
    for k, v in s.items():
        if not v.strip() or re.search('[\u2013\u2014;]', v) or ' - ' in v:
            print('FAIL strings: %s %s %r' % (d, k, v)); bad = 1
        if k == 'trial_no_sound_text' and 'Lumen OS' not in v:
            print('FAIL strings: %s does not name Lumen OS' % d); bad = 1
print('trial strings: %d dirs' % (len(dirs) + 1)) if not bad else None
sys.exit(bad)
EOF
# ---- HB: these Z9X files stay out of the HB app (the HB keeps its own forks)
HB="$HERE/../../devices/goat_elegant/app/HbProjector.layers"
for l in 'drop      src/org/lumen/projector/audio/AudioPorts.java' 'drop      src/org/lumen/projector/audio/AudioPortRules.java' \
         'drop      src/org/lumen/projector/panel/SoundState.java' 'drop      res/values*/strings_trial.xml' 'drop      test/sound/'; do
  grep -qxF -- "$l" "$HB" || fail "HB recipe: missing '$l'"
done
[ $bad = 0 ] && echo "sound wiring: ok"
exit $bad
