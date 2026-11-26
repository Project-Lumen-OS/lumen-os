#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Sound page state and the audio port gate: each must turn test/sound red (scratch copies, never in
place: test/scratch.py).

  python3 apps/core/lumen-menu/test/sound/mutate.py [-k substring]
"""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import scratch  # noqa: E402

Z9X = 'apps/LumenProjector/core/'
PJ = Z9X + 'src/org/lumen/projector/'
SS = 'apps/core/lumen-menu/src/org/lumen/projector/panel/SoundState.java'
RULES = 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xMenuRules.java'
PAGES = 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xMenuPages.java'
PORT_RULES = PJ + 'audio/AudioPortRules.java'
AP = PJ + 'audio/AudioPorts.java'
APR = PJ + 'audio/AudioPathReporter.java'
APPJ = PJ + 'App.java'
DE = Z9X + 'res/values-de/strings_trial.xml'
HB = 'devices/goat_elegant/app/HbProjector.layers'
MC = 'apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java'
GATE = 'apps/core/lumen-menu/src/org/lumen/projector/panel/RepaintGate.java'
SEQ = PJ + 'audio/SoundEq.java'
EQW = 'apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/audio/EqWrite.java'
WR = 'apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/audio/WakeReapply.java'
AIS = PJ + 'audio/AiSoundEngine.java'
P6EQ = 'apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/audio/P6SoundEq.java'

MUTANTS = [
    ('wake re-applies five times again', WR, 'return watching ? new long[0] : new long[] {SCREEN_ON_MS};', 'return new long[] {SCREEN_ON_MS, 10_000, 20_000, 30_000, 60_000};'),
    ('wake timer next to the vendor watch', WR, 'return watching ? new long[0] : new long[] {SCREEN_ON_MS};', 'return new long[] {SCREEN_ON_MS};'),
    ('retry after every write', WR, 'return outcome == REFUSED && attempt == 0;', 'return attempt == 0;'),
    ('retry without end', WR, 'return outcome == REFUSED && attempt == 0;', 'return outcome == REFUSED;'),
    ('speaker setting written on ARC', WR, 'return confirmedPath == AudioPath.ARC.wire;', 'return false;'),
    ('everything but the speaker skipped', WR, 'return confirmedPath == AudioPath.ARC.wire;', 'return confirmedPath != AudioPath.SPEAKER.wire;'),
    ('SoundEq writes on ARC', SEQ, 'if (kind != KIND_FORCE && WakeReapply.skipOutput(AudioPathReporter.confirmedPath())) {', 'if (false) {'),
    ('SoundEq without the retry', SEQ, 'if (WakeReapply.retry(outcome, attempt)) reapply(', 'if (false) reapply('),
    ('AI sound wake re-send after a confirm', AIS, '            if (onlyUnconfirmed && sConfirmed) return;\n', ''),
    ('AI sound five re-sends after a HAL reconnect', AIS, 'resend("HAL reconnected", WakeReapply.SCREEN_ON_MS, false);', 'for (long d : new long[] {3_000, 10_000, 20_000, 30_000, 60_000}) resend("HAL reconnected", d, false);'),
    ('Play 6 EQ two wake writes', P6EQ, 'for (long d : WakeReapply.screenOn(false))', 'for (long d : new long[] {3_000, 20_000})'),
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
    ('AudioPathReporter registers directly', APR, 'Family.registerAudioDevices(app, cb, h, "AudioPathReporter");',
     'app.getSystemService(android.media.AudioManager.class).registerAudioDeviceCallback(cb, h);'),
    ('EarcLink installed directly', 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/Family.java', 'AudioPorts.whenUp(app, new Handler(android.os.Looper.getMainLooper()), tag, r);',
     'org.lumen.projector.cec.EarcLink.install(this); org.lumen.projector.audio.AudioPorts.whenUp(this, new android.os.Handler(android.os.Looper.getMainLooper()),'),
    ('no back-off between probes', AP, 'sProbe.postDelayed(AudioPorts::probe, next);', 'sProbe.postDelayed(AudioPorts::probe, 100);'),
    ('trial notice every boot', AP, '            if (p.getBoolean(key, false)) return;\n', ''),
    ('trial notice over the setup wizard', AP, '                if (!setupDone()) {', '                if (false) {'),
    ('a German trial string missing', DE, '<string name="trial_no_sound_text">', '<string name="trial_no_sound_textx">'),
    ('HB gets the gate', HB, 'patch     devices/goat_elegant/app/HbProjector.manifest.diff\n', 'feature   audio-ports\npatch     devices/goat_elegant/app/HbProjector.manifest.diff\n'),
    # ---- the equalizer page read loop and the lost preset step (owner 1.1.0-beta2)
    ('refresh reads during a repaint', MC, '        if (repaint.quiet()) return;', '        if (false) return;'),
    ('refreshPage(Page) outside the gate', MC, 'repaint.repaint(() -> p.refreshPage());', 'p.refreshPage();'),
    ('gate never quiet', GATE, 'boolean quiet() { return depth > 0; }', 'boolean quiet() { return false; }'),
    ('gate stays closed after a throw', GATE, '        } finally {\n            depth--;\n        }', '        } catch (RuntimeException e) {\n            throw e;\n        }\n        depth--;'),
    ('a read puts the saved preset back over a pending step', SS, 'return mode == shownMode && (bandsBusy || presetPending);', 'return mode == shownMode && bandsBusy;'),
    ('applyEq ignores the pending step', PAGES, 'eqBands.isBusy(), eqPreset.isCommitPending())', 'eqBands.isBusy(), false)'),
    # ---- a flat equalizer: stock calls only
    ('flat after a vendor call sends 51(false)', EQW, 'return kind != FORCE && !onByUs ? NOTHING : OFF;', 'return kind == TRIGGER && !onByUs ? NOTHING : OFF;'),
    ('a curve after a vendor call deduped', EQW, 'if (kind == TRIGGER && onByUs && sameRecent) return NOTHING;', 'if (kind != FORCE && onByUs && sameRecent) return NOTHING;'),
    ('42 forces the flat write again', SEQ, '    public static void afterVendorCall(GmpfClient g, String why) {\n        apply(g, why, KIND_VENDOR);', '    public static void afterVendorCall(GmpfClient g, String why) {\n        apply(g, why, KIND_FORCE);'),
]


if __name__ == '__main__':
    scratch.main('apps/core/lumen-menu/test/sound/run.sh', MUTANTS)
