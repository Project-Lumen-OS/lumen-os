#!/usr/bin/env python3
"""Mutants of the Sound page state and the audio port gate: each must turn test/sound red. python3 test/sound/mutate.py"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..'))
ROOT = os.path.normpath(os.path.join(APP, '..', '..'))
PJ = 'src/org/lumen/projector/'
SS = PJ + 'panel/SoundState.java'
QPC = PJ + 'panel/QuickPanelController.java'
RULES = PJ + 'audio/AudioPortRules.java'
AP = PJ + 'audio/AudioPorts.java'
APR = PJ + 'audio/AudioPathReporter.java'
APPJ = PJ + 'App.java'
DE = 'res/values-de/strings_trial.xml'
HB = '../../devices/goat_elegant/app/HbProjector.layers'

MUTANTS = [
    # ---- the Sound page in the stock mapping
    ('DTS shown as Harman (owner trial bug)', SS, 'if (process == DTS_VX) return ROW_DTS;', 'if (process == DTS_VX) return ROW_HARMAN;'),
    ('43 on shown as a choice', SS, 'if (dts43 == null || dts43 || process == null) return -1;', 'if (process == null) return -1;'),
    ('another process shown as Harman', SS, '        if (process == HARMAN) return ROW_HARMAN;\n        return -1;', '        return ROW_HARMAN;'),
    ('no hint while DTS waits for the output', SS, '            if (!pathActive) return HINT_NOT_READY;\n', ''),
    ('no speaker-only hint', SS, '            if (path != speakerPath) return HINT_SPEAKER_ONLY;\n', ''),
    ('equalizer on in DTS', SS, 'return Boolean.FALSE.equals(dts43) && process != null && process == HARMAN;', 'return process != null;'),
    ('the page maps DTS by the path again', QPC,
     'setChoice(soundEnh, ok ? SoundState.enhancementRow(sn.soundProcess, sn.dtsOn) : -1, ok && sn.soundProcess != null, true);',
     'setChoice(soundEnh, ok ? SoundProfiles.indexOf(SoundProfiles.fromState(sn.soundProcess, sn.dtsOn, sn.soundPathActive)) : -1, ok && sn.soundProcess != null, true);'),
    ('equalizer by its own rule', QPC, 'boolean amp = ok && SoundState.eqUsable(sn.soundProcess, sn.dtsOn);', 'boolean amp = ok;'),
    # ---- audio port gate
    ('probe every 100 ms', RULES, 'long d = FIRST_MS << Math.min(failures, 20);', 'long d = 100L;'),
    ('no cap on the back-off', RULES, 'return Math.min(MAX_MS, d);', 'return d;'),
    ('policy always up', RULES, 'static boolean policyUp(int outputs) { return outputs > 0; }', 'static boolean policyUp(int outputs) { return true; }'),
    ('trial notice with the blobs', RULES, '"1".equals(gsidRunning) && !"ok".equals(blobs) && !policyUp', '"1".equals(gsidRunning) && !policyUp'),
    ('trial notice on an installed system', RULES, '"1".equals(gsidRunning) && !"ok".equals(blobs)', '!"ok".equals(blobs)'),
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

COPY_DIRS = ['src', 'res', 'test/sound']


def tree(tmp):
    root = os.path.join(tmp, 'r')
    app = os.path.join(root, 'apps', 'Z9xProjector')
    for d in COPY_DIRS:
        shutil.copytree(os.path.join(APP, d), os.path.join(app, d))
    hb = os.path.join(root, 'devices', 'goat_elegant', 'app')
    os.makedirs(hb)
    shutil.copy(os.path.join(ROOT, 'devices', 'goat_elegant', 'app', 'HbProjector.layers'), hb)
    return app


def run(app):
    r = subprocess.run(['sh', os.path.join(app, 'test/sound/run.sh')], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    tmp = tempfile.mkdtemp(prefix='snd_mut_')
    try:
        rc, out = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-800:])
        sys.exit(1)
    killed = 0
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix='snd_mut_')
        try:
            app = tree(tmp)
            target = os.path.normpath(os.path.join(app, rel))
            s = open(target, encoding='utf-8').read()
            if s.count(old) != 1:
                print('ANCHOR %d: %s' % (s.count(old), name))
                continue
            open(target, 'w', encoding='utf-8').write(s.replace(old, new))
            rc, out = run(app)
            if rc != 0:
                killed += 1
                print('killed   ' + name)
            else:
                print('SURVIVED ' + name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('mutants: %d/%d killed' % (killed, len(MUTANTS)))
    sys.exit(0 if killed == len(MUTANTS) else 1)


if __name__ == '__main__':
    main()
