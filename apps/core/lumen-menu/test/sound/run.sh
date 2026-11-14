#!/bin/sh
# Sound page state and audio port rules (a test Z9X trial 2026-11-11): sh test/sound/run.sh
# SoundStateTest: the Sound enhancement row shows the STORED process as stock does (DTS stays DTS before the output
# path is reported, so Harman Kardon is always a way out), hints and the equalizer agree with it; AudioPortRules:
# probes with back-off, never the framework's 100 ms loop, the trial notice condition. The greps pin the wiring.
set -eu
T=$(cd "$(dirname "$0")" && pwd)                 # this suite (apps/core/lumen-menu/test/<suite>)
G=$(cd "$T/../../../../.." && pwd)                 # gsi/
# the suite runs against the ASSEMBLED app: lumen-menu + the device app (default the Z9X)
TREE=$(mktemp -d "${TMPDIR:-/tmp}/lumen_menu_tree.XXXXXX")
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --quiet --device "${MENU_DEVICE:-horse_popular}" \
  --app "${MENU_APP:-Z9xProjector}" --out "$TREE/app" || { echo "FAIL: the app does not assemble"; rm -rf "$TREE"; exit 1; }
HERE=$TREE/app
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT" "$TREE"' EXIT
SRC="$HERE/src/org/lumen/projector"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$SRC/panel/SoundState.java" "$SRC/audio/AudioPortRules.java" \
  "$T/AudioPortRulesProbe.java" "$T/SoundStateTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.SoundStateTest
QPC="$SRC/panel/Z9xMenuRules.java"            # the Z9X rules of the shared menu (1.0.10 QuickPanelController rules)
EQP="$SRC/panel/Z9xMenuPages.java"
ZF="$SRC/panel/Z9xSoundRows.java"                # the sound section of the Z9X adapter (Z9xFeatures)
AP="$SRC/audio/AudioPorts.java"
bad=0
fail() { echo "FAIL $1"; bad=1; }
has() { grep -qF -- "$2" "$1" || fail "$3"; }
# ---- the Sound page reads SoundState (stock mapping), not the old path-gated SoundProfiles.fromState
has "$QPC" 'int row = SoundState.enhancementRow(in.soundProcess, in.dtsOn);' "Sound enhancement row not from SoundState.enhancementRow"
grep -q 'SoundProfiles.fromState(' "$QPC" "$ZF" "$SRC/panel/Z9xFeatures.java" && fail "the menu still maps DTS to Harman before the path is reported"
has "$EQP" 'boolean amp = ok && SoundState.eqUsable(in.soundProcess, in.dtsOn);' "equalizer not from SoundState"
has "$EQP" 'boolean dts = ok && SoundState.dtsStored(in.soundProcess, in.dtsOn);' "equalizer DTS value not from SoundState"
has "$QPC" 'boolean dtsStored = SoundState.dtsStored(in.soundProcess, in.dtsOn);' "equalizer entry DTS value not from SoundState"
has "$QPC" 'int hk = ok ? SoundState.hint(in.soundProcess, in.dtsOn, in.soundPathActive, in.soundPath, SPEAKER_PATH) : SoundState.HINT_NONE;' "hint not from SoundState"
has "$ZF" 'SoundResult r = SoundProfiles.apply(app, g, prof);' "the choice no longer goes through SoundProfiles.apply (44 + 27)"
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
has "$AP" 'if (!AudioPortRules.trialNoSound(prop("ro.gsid.image_running"), prop("sys.z9x.blobs"), false)) return;' \
  "trial notice not on the trial + blobs condition"
has "$AP" 'if (p.getBoolean(key, false)) return;' "trial notice shown more than once per build"
has "$AP" 'if (!setupDone()) {' "trial notice over the setup wizard"
has "$AP" 'Notify.show(sApp, sApp.getString(R.string.trial_no_sound_title), sApp.getString(R.string.trial_no_sound_text));' \
  "trial notice text"
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
HB="$G/devices/goat_elegant/app/HbProjector.layers"
for l in 'drop      src/org/lumen/projector/audio/AudioPorts.java' 'drop      src/org/lumen/projector/audio/AudioPortRules.java' \
         'drop      res/values*/strings_trial.xml'; do
  grep -qxF -- "$l" "$HB" || fail "HB recipe: missing '$l'"
done
[ $bad = 0 ] && echo "sound wiring: ok"
exit $bad
