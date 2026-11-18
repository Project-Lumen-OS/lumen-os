#!/usr/bin/env python3
"""Targeted mutants of the HDMI relink, the stock HDMI mode values (EdidModes) and the game profile gate: each one
change must turn test/run.sh red.
  python3 apps/core/lumen-hdmi/test/mutate.py [-k substring]
Every mutant runs on a scratch copy of the files run.sh reads (the repo itself is never written)."""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.normpath(os.path.join(HERE, '..', '..', '..', '..'))
L = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiLink.java'
R = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiRelink.java'
E = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/EdidModes.java'
ZH = 'apps/ProjectorBase/src/org/lumen/projector/source/GmpfRelinkHost.java'
XH = 'apps/X50Projector/src/org/lumen/x50/projector/X50RelinkHost.java'
X = 'apps/X50Projector/src/org/lumen/x50/projector/'
GP = 'apps/ProjectorBase/src/org/lumen/projector/game/GameProfile.java'
K = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudioKeys.java'
A = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudio.java'

MUTANTS = [
    # name, file, old (once in the file), new
    ('stable is 2', L, 'STABLE = 3;', 'STABLE = 2;'),
    ('an unread status counts as no picture', L, 'return status == NO_PICTURE || status == UNSTABLE;',
     'return status != STABLE;'),
    ('the clock survives a stable read', L, 'return since(since, noPicture(status), now);', 'return since(since, true, now);'),
    ('the clock restarts on every read', L, 'return since(since, noPicture(status), now);', 'return now;'),
    ('game profile on DVI', L, 'return !signalKnown || hdmiMode;', 'return true;'),
    ('game profile only on HDMI', L, 'return !signalKnown || hdmiMode;', 'return hdmiMode;'),
    ('game profile on an unstable link', L, 'if (status != UNKNOWN && !stable(status)) return false;',
     'if (status == 1) return false;'),
    ('an unread status blocks the game profile', L, 'if (status != UNKNOWN && !stable(status)) return false;',
     'if (!stable(status)) return false;'),
    ('relink asleep', L, 'return awake && streaming', 'return streaming'),
    ('relink without the port on screen', L, 'awake && streaming && sourceOnMs', 'awake && sourceOnMs'),
    ('relink for a source that is off', L, 'sourceOnMs >= NO_LOCK_MS && ', ''),
    ('relink right when the source turns on', L, 'sourceOnMs >= NO_LOCK_MS', 'sourceOnMs >= 0'),
    ('since restarts on every reading', L, '        return since > 0 ? since : now;\n    }\n\n    /**\n     * Game', '        return now;\n    }\n\n    /**\n     * Game'),
    ('relink one tick late', L, 'noPictureMs >= NO_LOCK_MS', 'noPictureMs > NO_LOCK_MS'),
    ('relink twice per session', L, 'sessionRelinks == 0', 'sessionRelinks <= 1'),
    ('one relink too many per boot', L, 'bootRelinks < MAX_PER_BOOT', 'bootRelinks <= MAX_PER_BOOT'),
    ('5 s without a picture', L, 'NO_LOCK_MS = 10_000;', 'NO_LOCK_MS = 5_000;'),
    ('flip held 500 ms', L, 'FLIP_HOLD_MS = 2_000;', 'FLIP_HOLD_MS = 500;'),
    ('flip from a version not written back', L, '        if (!contains(allowed, current)) return -1;\n', ''),
    ('flip to the same version', L, 'if (first != current && contains(allowed, first)) return first;',
     'if (contains(allowed, first)) return first;'),
    ('flip to a foreign version', L, 'if (second != current && contains(allowed, second)) return second;',
     'if (second != current) return second;'),
    # wiring
    ('relink not told about sessions', 'apps/ProjectorBase/src/org/lumen/projector/game/HdmiEventReceiver.java',
     'org.lumen.hdmi.HdmiRelink.onSessionEvent(', 'org.lumen.projector.source.HdmiSource.onSessionEvent('),
    ('relink not installed on the Z9X', 'apps/ProjectorBase/src/org/lumen/projector/App.java',
     'org.lumen.hdmi.HdmiRelink.install(this, new org.lumen.projector.source.GmpfRelinkHost());', ''),
    ('relink not installed on the HB', 'apps/HbProjector/src/org/lumen/projector/App.java',
     'org.lumen.hdmi.HdmiRelink.install(this, new org.lumen.projector.source.GmpfRelinkHost());', ''),
    ('relink not installed on the X50 family', X + 'App.java',
     'org.lumen.hdmi.HdmiRelink.install(this, new X50RelinkHost());', ''),
    ('X50 relink not told about the shown port', X + 'HdmiSource.java',
     '        MAIN.post(() -> org.lumen.hdmi.HdmiRelink.onShownPort(port));', ''),
    ('shown port never ends a session', R, '        else if (sPort != 0) onSessionEvent(sPort, false);\n', ''),
    ('shown port never starts a session', R, '        if (port > 0) onSessionEvent(port, true);\n', ''),
    ('relink in the lamp-only standby', R, 'return !sHost.standby();', 'return true;'),
    ('relink flips in the host order reversed', R, 'g.edidVersions(), sHost.edidFirst(), sHost.edidSecond());',
     'g.edidVersions(), sHost.edidSecond(), sHost.edidFirst());'),
    ('gmpf host reads the HDMI info as status', ZH, 'return g.getSignalStatus(); }', 'return g.getHdmiInfo().hashCode(); }'),
    ('gmpf host without the standby', ZH, 'return StandbyController.isActive(); }', 'return false; }'),
    ('gmpf host without the connect hook', ZH, 'Hal.addConnectedListener(() -> Hal.run((g, g2) -> j.run(vendor(g))));', ''),
    ('gmpf host with all versions', ZH, 'return g.edidVersions(); }', 'return new int[] {0, 1, 2, 3}; }'),
    ('X50 host reads the EDID as status', XH, 'return g.signalStatus(); }', 'return g.edidVersion(); }'),
    ('X50 host with the beta 7 values', XH, 'EDID_VERSIONS = HdmiHal.EDID.wires();', 'EDID_VERSIONS = new int[] {1, 2};'),
    ('X50 host flips to Enhanced first', XH, 'return HdmiHal.EDID.wire(EdidModes.COMPATIBLE); }',
     'return HdmiHal.EDID.wire(EdidModes.ENHANCED); }'),
    ('X50 model data HDMI 2.1', X + 'HdmiHal.java', 'EdidModes.of(EdidModes.FEATURE_HDMI_20);',
     'EdidModes.of(EdidModes.FEATURE_HDMI_21);'),
    ('X50 TV 40 guard off', X + 'Gmpf.java', 'if (!HdmiHal.EDID.writable(v)) {', 'if (false) {'),
    ('X50 row writes the shown copy again', X + 'HdmiSequences.java',
     'if (HdmiHal.EDID.needsWrite(h.edidVersion(), row)) h.edidVersionSet(HdmiHal.EDID.wire(row));',
     'h.edidVersionSet(row + 1);'),
    # EdidModes: the stock rule
    ('Enhanced without the gmuiapi step', E, '(edid2VersionVal == FEATURE_HDMI_21 ? SIGNAL_21 : SIGNAL_20) - GMUIAPI_STEP)',
     '(edid2VersionVal == FEATURE_HDMI_21 ? SIGNAL_21 : SIGNAL_20))'),
    ('Compatible without the gmuiapi step', E, 'if (row == COMPATIBLE) return SIGNAL_14 - GMUIAPI_STEP;',
     'if (row == COMPATIBLE) return SIGNAL_14;'),
    ('the 2.1 feature ignored', E, 'edid2VersionVal == FEATURE_HDMI_21 ? SIGNAL_21 : SIGNAL_20', 'SIGNAL_20'),
    ('an unknown feature value accepted', E,
     'if (edid2VersionVal != FEATURE_HDMI_20 && edid2VersionVal != FEATURE_HDMI_21) {', 'if (false) {'),
    ('an unknown row writes Enhanced', E, '        if (row == ENHANCED) return enhanced;\n', '        return enhanced;\n'),
    ('every non-zero read shows Enhanced', E, 'return raw == enhanced ? ENHANCED : COMPATIBLE;',
     'return raw != 0 ? ENHANCED : COMPATIBLE;'),
    ('the shown row is not written again', E, 'return raw != wire(row);', 'return row(raw) != row;'),
    ('any value writable', E, 'return raw == wire(COMPATIBLE) || raw == wire(ENHANCED);', 'return raw >= 0;'),
    ('the other row is the same one', E, 'return wire(row(raw) == ENHANCED ? COMPATIBLE : ENHANCED);',
     'return wire(row(raw));'),
    ('wires Enhanced first', E, 'return new int[] {wire(COMPATIBLE), wire(ENHANCED)};',
     'return new int[] {wire(ENHANCED), wire(COMPATIBLE)};'),
    ('X50 host never restores at start', XH, 'public void onConnect(Job j) { run(j); }', 'public void onConnect(Job j) { }'),
    ('X50 host read never answered', XH, '            MAIN.post(() -> onMain.accept(out));\n', ''),
    ('X50 status of another code', X + 'GmpfCall.java', 'HalCodes.TV_GET_SIGNAL_STATUS, "getSignalStatus"',
     'HalCodes.TV_GET_HDR_TYPE, "getSignalStatus"'),
    ('X50 table row of another code', 'devices/g0111/hal/codes.tsv', 'TV_GET_SIGNAL_STATUS\t106\t', 'TV_GET_SIGNAL_STATUS\t107\t'),
    ('X50 generated constant of another code', X + 'HalCodes.java', 'TV_GET_SIGNAL_STATUS = 106;', 'TV_GET_SIGNAL_STATUS = 107;'),
    ('X50 recipe without the module', 'devices/g0155/app/X50Projector.layers', 'module    lumen-hdmi\n', ''),
    ('a per-app relink copy again', 'apps/ProjectorBase/src/org/lumen/projector/source/GmpfRelinkHost.java',
     'public final class GmpfRelinkHost', 'final class HdmiRelinkCopy { }\npublic final class GmpfRelinkHost'),
    ('relink without the tvinput check', R, 'if (!HdmiLink.relinkNow(noPictureMs, onMs, awake(), streaming, sSessionRelinks, sBootRelinks)) {',
     'if (!HdmiLink.relinkNow(noPictureMs, onMs, awake(), true, sSessionRelinks, sBootRelinks)) {'),
    ('relink without the CEC clock', R, 'sSourceOnSince = HdmiLink.since(sSourceOnSince, on, now);',
     'sSourceOnSince = HdmiLink.since(sSourceOnSince, true, now);'),
    ('flip without saving the user version', R,
     'if (!prefs().edit().putBoolean(K_PENDING, true).putInt(K_USER, user).commit()) {', 'if (false) {'),
    ('the user version never written back', R,
     'boolean queued = sHost.runDelayed(v -> restorePending(v, "relink"), HdmiLink.FLIP_HOLD_MS,',
     'boolean queued = sHost.runDelayed(v -> { }, HdmiLink.FLIP_HOLD_MS,'),
    ('a saved foreign version written back', L, '        return contains(allowed, user);\n    }\n\n    private',
     '        return user >= 0;\n    }\n\n    private'),
    ('a saved foreign version kept pending', R, 'if (!HdmiLink.restorable(user, g.edidVersions())) {', 'if (false) {'),
    ('a flip left over by a crash stays', R, '        host.onConnect(v -> restorePending(v, "HAL connect"));\n', ''),
    ('game profile without the tick gate', GP, '        if (!gameReady(signalStatus, signal)) {',
     '        if (false) {'),
    ('game profile without the HAL gate', GP, '        if (!gameReady(st, now)) {', '        if (false) {'),
    ('game profile on DVI in the gmpf apps', GP, 's != null && s.plausible(), s != null && s.hdmiMode);',
     's != null && s.plausible(), true);'),
    ('no picture counted as a refusal', GP, '"session".equals(o.refusedKind) || "signal".equals(o.refusedKind)) {',
     '"session".equals(o.refusedKind)) {'),
    ('Z9X signal status of another code', 'apps/ProjectorBase/src/org/lumen/projector/hal/Z9xCodes.java',
     '.put(GET_SIGNAL_STATUS, HalCodes.GMPF_GET_SIGNAL_STATUS)', '.put(GET_SIGNAL_STATUS, HalCodes.GMPF_GET_HDMI_INFO)'),
    ('HB signal status of another call', 'apps/HbProjector/src/org/lumen/projector/hal/HbCodes.java',
     '.put(GET_SIGNAL_STATUS, GmpfCall.GET_SIGNAL_STATUS)', '.put(GET_SIGNAL_STATUS, GmpfCall.GET_HDMI_INFO)'),
    # HdmiAudioKeys: the stock audio HAL keys of the HDMI input
    ('audio key on for another status', K, 'STABLE = "3", UNPLUGGED = "0";', 'STABLE = "2", UNPLUGGED = "0";'),
    ('a cable in counts as a cable out', K, 'STABLE = "3", UNPLUGGED = "0";', 'STABLE = "3", UNPLUGGED = "1";'),
    ('no delayed key after a lost signal', K, '                out.later(OFF_DELAYED, DELAY_MS);\n', ''),
    ('the delayed key at once', K, 'DELAY_MS = 500;', 'DELAY_MS = 0;'),
    ('event 4 sends nothing', K, 'if (type == EVENT_OFF) { out.now(OFF); return; }', 'if (type == EVENT_OFF) return;'),
    ('a cable out of any port switches the key off', K, 'if (unplugged == current) out.now(OFF);', 'out.now(OFF);'),
    ('the on key for a lost signal', K, 'OFF = "xgimi_source_hdmi=0",', 'OFF = "xgimi_source_hdmi=1",'),
    ('a status of another name counts', K, 'if (p.length < 2 || !"status".equals(p[0])) return;',
     'if (p.length < 2) return;'),
    ('the picture mode event gets a hop', K, 'return type == EVENT_SIGNAL || type == EVENT_HOT_PLUG || type == EVENT_OFF;',
     'return true;'),
    ('the events never reach the keys', A, 'h.run(source -> HdmiAudioKeys.onTvEvent(type, msg, source, OUT));', ''),
    ('the Z9X app does not hand the events over', 'apps/ProjectorBase/src/org/lumen/projector/App.java',
     'AiScenePolicy.addTvEventSink(org.lumen.hdmi.HdmiAudio::onTvEvent);', ''),
    ('the vendor events stop at the AI picture', 'apps/ProjectorBase/src/org/lumen/projector/AiScenePolicy.java',
     'for (TvEventSink s : EXTRA) s.onTvEvent(type, msg);', ''),
]

COPY = ['apps/core/lumen-hdmi', 'apps/sdk/framework-full-headers.jar'] + ['devices/%s.layers' % r for r in (
    'horse_popular/app/Z9xProjector', 'goat_elegant/app/HbProjector', 'g0111/app/X50Projector', 'g0100/app/X50Projector',
    'g0155/app/X50Projector', 'h0062/app/X50Projector', 'h0063/app/X50Projector')]


def main():
    k = sys.argv[sys.argv.index('-k') + 1] if '-k' in sys.argv else ''
    killed = survived = 0
    for name, f, old, new in MUTANTS:
        if k and k not in name:
            continue
        with tempfile.TemporaryDirectory() as t:
            files = set(COPY) | {f} | {line.split('\t')[0] for line in open(os.path.join(HERE, 'wiring.tsv')) if line.strip()}
            for p in files:
                src, dst = os.path.join(G, p), os.path.join(t, p)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                if os.path.isdir(src):
                    shutil.copytree(src, dst, dirs_exist_ok=True)
                else:
                    shutil.copy(src, dst)
            target = os.path.join(t, f)
            text = open(target).read()
            if text.count(old) != 1:
                print('BAD   %s: the text is in %s %d times' % (name, f, text.count(old)))
                survived += 1
                continue
            open(target, 'w').write(text.replace(old, new))
            r = subprocess.run(['sh', os.path.join(t, 'apps/core/lumen-hdmi/test/run.sh')], capture_output=True,
                               text=True)
            if r.returncode != 0:
                killed += 1
                print('kill  ' + name)
            else:
                survived += 1
                print('ALIVE ' + name)
    print('mutants: %d killed, %d alive' % (killed, survived))
    sys.exit(1 if survived else 0)


if __name__ == '__main__':
    main()
