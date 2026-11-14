#!/usr/bin/env python3
"""Mutants of the Sound page state and the audio port gate: each must turn test/sound red (scratch copies, never in
place: test/scratch.py).

  python3 apps/core/lumen-menu/test/sound/mutate.py [-k substring]
"""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import scratch  # noqa: E402

Z9X = 'apps/Z9xProjector/'
PJ = Z9X + 'src/org/lumen/projector/'
SS = 'apps/core/lumen-menu/src/org/lumen/projector/panel/SoundState.java'
RULES = PJ + 'panel/Z9xMenuRules.java'
PAGES = PJ + 'panel/Z9xMenuPages.java'
PORT_RULES = PJ + 'audio/AudioPortRules.java'
AP = PJ + 'audio/AudioPorts.java'
APR = PJ + 'audio/AudioPathReporter.java'
APPJ = PJ + 'App.java'
DE = Z9X + 'res/values-de/strings_trial.xml'
HB = 'devices/goat_elegant/app/HbProjector.layers'

MUTANTS = [
    # ---- the Sound page in the stock mapping
    ('DTS shown as Harman (owner trial bug)', SS, 'if (process == DTS_VX) return ROW_DTS;', 'if (process == DTS_VX) return ROW_HARMAN;'),
    ('43 on shown as a choice', SS, 'if (dts43 == null || dts43 || process == null) return -1;', 'if (process == null) return -1;'),
    ('another process shown as Harman', SS, '        if (process == HARMAN) return ROW_HARMAN;\n        return -1;', '        return ROW_HARMAN;'),
    ('no hint while DTS waits for the output', SS, '            if (!pathActive) return HINT_NOT_READY;\n', ''),
    ('no speaker-only hint', SS, '            if (path != speakerPath) return HINT_SPEAKER_ONLY;\n', ''),
    ('equalizer on in DTS', SS, 'return Boolean.FALSE.equals(dts43) && process != null && process == HARMAN;', 'return process != null;'),
    ('the page maps DTS by the path again', RULES, 'int row = SoundState.enhancementRow(in.soundProcess, in.dtsOn);',
     'int row = SoundProfiles.indexOf(SoundProfiles.fromState(in.soundProcess, in.dtsOn, in.soundPathActive));'),
    ('equalizer by its own rule', PAGES, 'boolean amp = ok && SoundState.eqUsable(in.soundProcess, in.dtsOn);', 'boolean amp = ok;'),
    # ---- audio port gate
    ('probe every 100 ms', PORT_RULES, 'long d = FIRST_MS << Math.min(failures, 20);', 'long d = 100L;'),
    ('no cap on the back-off', PORT_RULES, 'return Math.min(MAX_MS, d);', 'return d;'),
    ('policy always up', PORT_RULES, 'static boolean policyUp(int outputs) { return outputs > 0; }', 'static boolean policyUp(int outputs) { return true; }'),
    ('trial notice with the blobs', PORT_RULES, '"1".equals(gsidRunning) && !"ok".equals(blobs) && !policyUp', '"1".equals(gsidRunning) && !policyUp'),
    ('trial notice on an installed system', PORT_RULES, '"1".equals(gsidRunning) && !"ok".equals(blobs)', '!"ok".equals(blobs)'),
    ('AudioPathReporter registers directly', APR, 'AudioPorts.registerDeviceCallback(sApp, new AudioDeviceCallback() {',
     'sApp.getSystemService(AudioManager.class).registerAudioDeviceCallback(new AudioDeviceCallback() {'),
    ('EarcLink installed directly', APPJ, 'org.lumen.projector.audio.AudioPorts.whenUp(this, new android.os.Handler(android.os.Looper.getMainLooper()),',
     'org.lumen.projector.cec.EarcLink.install(this); org.lumen.projector.audio.AudioPorts.whenUp(this, new android.os.Handler(android.os.Looper.getMainLooper()),'),
    ('no back-off between probes', AP, 'sProbe.postDelayed(AudioPorts::probe, next);', 'sProbe.postDelayed(AudioPorts::probe, 100);'),
    ('trial notice every boot', AP, '            if (p.getBoolean(key, false)) return;\n', ''),
    ('trial notice over the setup wizard', AP, '                if (!setupDone()) {', '                if (false) {'),
    ('a German trial string missing', DE, '<string name="trial_no_sound_text">', '<string name="trial_no_sound_textx">'),
    ('HB gets the gate', HB, 'drop      src/org/lumen/projector/audio/AudioPorts.java\n', ''),
]


if __name__ == '__main__':
    scratch.main('apps/core/lumen-menu/test/sound/run.sh', MUTANTS)
