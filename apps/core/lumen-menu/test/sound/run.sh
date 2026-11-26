#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
  "$SRC/audio/EqWrite.java" "$SRC/audio/WakeReapply.java" "$SRC/hal/AudioPath.java" "$SRC/panel/RepaintGate.java" \
  "$T/AudioPortRulesProbe.java" "$T/EqWriteProbe.java" "$T/WakeReapplyProbe.java" "$T/SoundStateTest.java" "$T/RepaintGateTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.SoundStateTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.RepaintGateTest
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
n=$(grep -c 'Family.registerAudioDevices(sApp, new AudioDeviceCallback() {' "$SRC/audio/AudioOutputChoice.java")
[ "$n" = 1 ] || fail "AudioOutputChoice: $n callbacks through the family (want 1)"
# the path reporter is the vendor adapter of lumen-audio PathSwitch: the switch's device callback through AudioPorts too
n=$(grep -c 'Family.registerAudioDevices(app, cb, h, "AudioPathReporter");' "$SRC/audio/AudioPathReporter.java")
[ "$n" = 1 ] || fail "AudioPathReporter: $n device callbacks through the family (want 1)"
# the Z9X family registers it behind the audio policy check (AudioPorts), apps/LumenProjector/vendor/xgimi/hidl-gmpf Family
n=$(grep -c 'AudioPorts.registerDeviceCallback(app, cb, h, tag);' "$G/apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/Family.java")
[ "$n" = 1 ] || fail "Z9X Family: $n device callbacks through AudioPorts (want 1)"
ZFAM="$G/apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/Family.java"
grep -qF 'Family.whenAudioUp(this, "EarcLink", () -> org.lumen.projector.cec.EarcLink.install(this));' "$SRC/App.java" \
  && grep -qF 'AudioPorts.whenUp(app, new Handler(android.os.Looper.getMainLooper()), tag, r);' "$ZFAM" \
  || fail "EarcLink installed before the audio policy answers"
n=$(grep -c 'EarcLink.install(' "$SRC/App.java" || true)
[ "$n" = 1 ] && grep 'EarcLink.install(' "$SRC/App.java" | grep -qF 'Family.whenAudioUp(this, "EarcLink", () -> org.lumen.projector.cec.EarcLink.install(this));' \
  || fail "EarcLink installed outside the family's whenAudioUp ($n calls in App)"
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
# ---- the equalizer page (owner 1.1.0-beta2: the preset blinks and cannot be changed): a repaint never reads again
MC="$SRC/panel/MenuController.java"
has "$MC" 'if (repaint.quiet()) return;' "refresh() reads during a repaint (the shown hook of a repaint reads again)"
has "$MC" 'repaint.repaint(() -> p.refreshPage());' "refreshPage(Page) repaints outside the gate (equalizer page read loop)"
has "$MC" 'repaint.repaint(() -> p.refreshPage(focus));' "refreshPage(Page, focus) repaints outside the gate"
has "$MC" 'repaint.repaint(() -> p.refreshPage(QpdLayout.popupFocus(' "apply() repaints outside the gate"
n=$(grep -cE '(^|[^.])\bpanel\.refreshPage\(' "$MC" || true)
[ "$n" -le 5 ] || fail "MenuController: $n direct panel.refreshPage calls (new repaints must go through refreshPage(Page) or the gate)"
has "$EQP" 'if (!SoundState.eqKeepRows(mode, eqMode, eqBands.isBusy(), eqPreset.isCommitPending())) {' "a read puts the saved preset back over a pending step"
has "$EQP" 'eqPage.setOnShown(() -> { if (menu != null) menu.refresh(); });' "the equalizer page no longer reads when shown"
has "$EQP" 'if (menu != null) menu.refreshPage(eqPage);' "applyEq no longer repaints through MenuController.refreshPage"
# ---- a flat equalizer costs no call stock does not make (42 alone, 44 + 27 alone, 61 alone)
SEQ="$SRC/audio/SoundEq.java"
has "$SEQ" 'if (EqWrite.plan(kind, true, sOnByUs, false) == EqWrite.NOTHING) return WakeReapply.WRITTEN;' "flat curve not planned by EqWrite"
has "$SEQ" 'if (EqWrite.plan(kind, false, sOnByUs, same) == EqWrite.NOTHING) {' "curve dedup not planned by EqWrite"
[ "$(grep -c 'apply(g, why, KIND_VENDOR);' "$SEQ")" = 2 ] || fail "afterVendorCall / afterUserModeChange not KIND_VENDOR (a flat EQ sends 51(false) after 42, 27, 61)"
has "$ZF" 'SoundEq.afterVendorCall(g, "42(" + on + ")");' "volume leveling no longer re-applies a user curve after 42"
# ---- a wake re-applies once (stock restores once): the SCREEN_ON schedules come from WakeReapply, no ARC write
AIS="$SRC/audio/AiSoundEngine.java"
P6EQ="$G/apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/audio/P6SoundEq.java"
grep -qE 'SCREEN_ON(_RESEND)?_MS = \{' "$SEQ" "$AIS" "$P6EQ" && fail "a fixed SCREEN_ON re-apply schedule is back"
has "$SEQ" 'for (long d : WakeReapply.screenOn(VendorSoundWatch.watching())) reapply("screen on", d, 0);' "SoundEq wake re-apply not from WakeReapply"
has "$SEQ" 'if (kind != KIND_FORCE && WakeReapply.skipOutput(AudioPathReporter.confirmedPath())) {' "SoundEq writes the speaker EQ on HDMI ARC"
has "$SEQ" 'if (WakeReapply.retry(outcome, attempt)) reapply(why + " (retry, the HAL refused)", WakeReapply.RETRY_MS, attempt + 1);' "SoundEq retry not from WakeReapply"
has "$AIS" 'for (long d : WakeReapply.screenOn(watching)) resend("screen on +" + d / 1000 + " s", d, true);' "AiSoundEngine wake re-send not from WakeReapply"
has "$AIS" 'resend("HAL reconnected", WakeReapply.SCREEN_ON_MS, false);' "AiSoundEngine re-sends more than once after a HAL reconnect"
has "$AIS" 'if (onlyUnconfirmed && sConfirmed) return;' "AiSoundEngine wake re-send after the sub-mode was already confirmed"
has "$P6EQ" 'for (long d : WakeReapply.screenOn(false)) Hal.runDelayed((g, g2) -> wake(app, g, 0), d);' "Play 6 EQ wake re-apply not from WakeReapply"
has "$P6EQ" 'if (WakeReapply.retry(apply(app, g, false, "screen on" + (attempt > 0 ? " (retry, the HAL refused)" : "")), attempt))' "Play 6 EQ retry not from WakeReapply"
grep -qxF 'src/org/lumen/projector/audio/WakeReapply.java' "$G/devices/dragon_healthy/app/Play6Projector.gmpf" || fail "the Play 6 recipe lacks WakeReapply"
# ---- HB: the audio ports part of the core stays out of the HB app (its feature audio-ports, core.features)
HB="$G/devices/goat_elegant/app/HbProjector.layers"
F="$G/apps/LumenProjector/core.features"
for p in src/org/lumen/projector/audio/AudioPorts.java src/org/lumen/projector/audio/AudioPortRules.java \
         'res/values*/strings_trial.xml'; do
  grep -qE "^audio-ports .*$(printf '%s' "$p" | sed 's/[*.]/\\&/g')" "$F" || fail "core.features: $p is not part of audio-ports"
done
grep -qE '^feature .*\baudio-ports\b' "$HB" && fail "HB recipe: takes the feature audio-ports"
[ $bad = 0 ] && echo "sound wiring: ok"
exit $bad
