#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
ZH = 'apps/LumenProjector/core/src/org/lumen/projector/source/GmpfRelinkHost.java'
XH = 'apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/X50RelinkHost.java'
X = 'apps/LumenProjector/host/activity/src/org/lumen/x50/projector/'
XA = 'apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/'
GP = 'apps/LumenProjector/core/src/org/lumen/projector/game/GameProfile.java'
K = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudioKeys.java'
A = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudio.java'
SO = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/SourceOff.java'
SF = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/SourceFollow.java'
SC_ = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/SleepCec.java'
SS = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/SleepSource.java'
IS = 'apps/core/lumen-hdmi/src/org/lumen/hdmi/InactiveSleep.java'
STC = 'apps/LumenProjector/core/src/org/lumen/projector/power/StandbyController.java'

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
    # signal reads only without a stable picture (LR-5WQ7M9, stock xhplayer)
    ('reads during a stable picture', L, 'return !stable(known);', 'return true;'),
    ('no read after a lost picture', L, 'return !stable(known);', 'return known == UNKNOWN;'),
    ('another event type counts as a signal report', L, 'if (type != EVENT_SIGNAL || msg == null) return known;',
     'if (msg == null) return known;'),
    ('a message of another name counts', L, 'if (p.length < 2 || !"status".equals(p[0])) return known;',
     'if (p.length < 2) return known;'),
    ('an unparsed status reads as no picture', L, '            return known;\n        }\n    }', '            return 1;\n        }\n    }'),
    ('relink reads on every tick', R, '        if (!HdmiLink.readDue(sKnown)) return;', '        if (false) return;'),
    ('relink forgets its reads', R, '        sKnown = status;\n', ''),
    ('relink deaf to the vendor events', R, 'final int s = HdmiLink.afterEvent(HdmiLink.UNKNOWN, type, msg);',
     'final int s = HdmiLink.UNKNOWN; if (s == 0) HdmiLink.afterEvent(0, type, msg);'),
    ('Z9X and HB do not hand the events to the relink', 'apps/LumenProjector/core/src/org/lumen/projector/App.java',
     'AiScenePolicy.addTvEventSink(org.lumen.hdmi.HdmiRelink::onTvEvent);', ''),
    ('the X50 family does not hand the events to the relink',
     'apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/X50AiPq.java',
     '            org.lumen.hdmi.HdmiRelink.onTvEvent(type, msg);\n', ''),
    ('relink ticks every 4 s', R, 'static final long TICK_MS = 2_000;', 'static final long TICK_MS = 4_000;'),
    # wiring
    ('relink not told about sessions', 'apps/LumenProjector/core/src/org/lumen/projector/game/HdmiEvents.java',
     'org.lumen.hdmi.HdmiRelink.onSessionEvent(', 'org.lumen.projector.source.HdmiSource.onSessionEvent('),
    ('relink not installed on the Z9X and HB', 'apps/LumenProjector/core/src/org/lumen/projector/App.java',
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
    ('X50 model data HDMI 2.1', XA + 'HdmiHal.java', 'EdidModes.of(EdidModes.FEATURE_HDMI_20);',
     'EdidModes.of(EdidModes.FEATURE_HDMI_21);'),
    ('X50 TV 40 guard off', XA + 'Gmpf.java', 'if (!HdmiHal.EDID.writable(v)) {', 'if (false) {'),
    ('X50 row writes the shown copy again', XA + 'HdmiSequences.java',
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
    ('X50 status of another code', XA + 'GmpfCall.java', 'HalCodes.TV_GET_SIGNAL_STATUS, "getSignalStatus"',
     'HalCodes.TV_GET_HDR_TYPE, "getSignalStatus"'),
    ('X50 table row of another code', 'devices/g0111/hal/codes.tsv', 'TV_GET_SIGNAL_STATUS\t106\t', 'TV_GET_SIGNAL_STATUS\t107\t'),
    ('X50 generated constant of another code', XA + 'HalCodes.java', 'TV_GET_SIGNAL_STATUS = 106;', 'TV_GET_SIGNAL_STATUS = 107;'),
    ('X50 recipe without the module', 'devices/_family/mt9681/app/X50Projector.layers', 'module    lumen-hdmi\n', ''),
    ('a per-app relink copy again', 'apps/LumenProjector/core/src/org/lumen/projector/source/GmpfRelinkHost.java',
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
    ('Z9X signal status of another code', 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/hal/Z9xCodes.java',
     '.put(GET_SIGNAL_STATUS, HalCodes.GMPF_GET_SIGNAL_STATUS)', '.put(GET_SIGNAL_STATUS, HalCodes.GMPF_GET_HDMI_INFO)'),
    ('HB signal status of another call', 'apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/hal/HbCodes.java',
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
    ('the events never reach the keys', A, 'lane.post(() -> HdmiAudioKeys.onTvEvent(type, msg, sSource, OUT));', ''),
    # HdmiAudio: the keys at the event, on their own thread (report LR-5WQ7M9), HdmiAudioReplayTest
    ('every key waits for an input source read', A, 'lane.post(() -> HdmiAudioKeys.onTvEvent(type, msg, sSource, OUT));',
     'lane.post(() -> { try { sSource.current(); } catch (Exception e) { } '
     'HdmiAudioKeys.onTvEvent(type, msg, sSource, OUT); });'),
    ('the delayed key at once on the keys thread', A, 'sLane.postDelayed(() -> send(keyValue), delayMs)',
     'sLane.post(() -> send(keyValue))'),
    ('the delayed key 100 ms late', A, 'sLane.postDelayed(() -> send(keyValue), delayMs)',
     'sLane.postDelayed(() -> send(keyValue), delayMs + 100)'),
    ('Z9X and HB read the source through the HAL queue', 'apps/LumenProjector/core/src/org/lumen/projector/App.java',
     'Hal.readNow((g, g2) -> g.getCurrentInputSource())', 'Hal.readQueued((g, g2) -> g.getCurrentInputSource())'),
    ('the direct read reads nothing', 'apps/LumenProjector/core/src/org/lumen/projector/Hal.java',
     'return q.run(c.client(), c.client2());', 'return null;'),
    ('the X50 family has no keys', X + 'App.java',
     'org.lumen.hdmi.HdmiAudio.install(this, X50RelinkHost.audioSource());', ''),
    ('the X50 events stop before the keys', XA + 'X50AiPq.java', '            org.lumen.hdmi.HdmiAudio.onTvEvent(type, msg);\n', ''),
    ('the Z9X and HB app does not hand the events over', 'apps/LumenProjector/core/src/org/lumen/projector/App.java',
     'AiScenePolicy.addTvEventSink(org.lumen.hdmi.HdmiAudio::onTvEvent);', ''),
    ('the vendor events stop at the AI picture', 'apps/LumenProjector/core/src/org/lumen/projector/AiScenePolicy.java',
     'for (TvEventSink s : EXTRA) s.onTvEvent(type, msg);', ''),
    # the projector follows the HDMI device being watched when it turns off (SourceOff, SourceFollow)
    ('follow a source never seen on', SO, 'if (!seenOn || !follow) return false;', 'if (!follow) return false;'),
    ('follow with the switch off', SO, 'if (!seenOn || !follow) return false;', 'if (!seenOn) return false;'),
    ('follow on the first standby read', SO, 'CONFIRM_READS = 2;', 'CONFIRM_READS = 1;'),
    ('follow reads once a minute', SO, 'READ_MS = 5_000;', 'READ_MS = 60_000;'),
    ('turning to standby is not off', SO, 'else if (p == STANDBY || p == TO_STANDBY) off = true;',
     'else if (p == STANDBY) off = true;'),
    ('turning on is not on', SO, 'if (p == ON || p == TO_ON) on = true;', 'if (p == ON) on = true;'),
    ('follow while another source behind the port is on', SO, '        if (on) {\n            seenOn = true;',
     '        if (on && !off) {\n            seenOn = true;'),
    ('a wake keeps the old viewing', SO, 'a wake is a new viewing\n            seenOn = false;\n',
     'a wake is a new viewing\n'),
    ('follow twice per viewing', SO, 'if (done) return false;', 'if (false) return false;'),
    ('an audio system counts as the source', SO, 'return type == 1 || type == 3 || type == 4;', 'return type != 0;'),
    ('a new viewing keeps the source seen on', SO, '        seenOn = false;\n        offReads = 0;\n        done = false;',
     '        offReads = 0;\n        done = false;'),
    ('the sessions do not reach the follow', R, '        SourceFollow.onSession(sApp, port, active);', ''),
    ('a session older than the process is not followed', R, '                SourceFollow.onSession(app, ports[0], true);\n',
     ''),
    ('follow sleeps as a power key', SF, 'PowerManager.GO_TO_SLEEP_REASON_HDMI', 'PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON'),
    ('follow ignores the switch', SF, 'm.getTvSendStandbyOnSleep() == HdmiControlManager.TV_SEND_STANDBY_ON_SLEEP_ENABLED',
     'true'),
    ('follow reads audio systems', SF, '            if (!SourceOff.source(d.getDeviceType())) continue;\n', ''),
    ('follow while asleep', SF, 'RULE.read(powers, follow, HdmiRelink.awake())', 'RULE.read(powers, follow, true)'),
    # the TV takes the active source back before the sleep (SleepCec, SleepSource), stock order
    ('sleep: Active Source with CEC off', SC_, 'return cecOn && hdmiShown;', 'return hdmiShown;'),
    ('sleep: Active Source from Home too', SC_, 'return cecOn && hdmiShown;', 'return cecOn;'),
    ('sleep: no Active Source', SC_, 'return cecOn && hdmiShown;', 'return false;'),
    ('sleep: SleepSource skips the select', SS, '        tvTakesSource(ctx);\n', ''),
    ('sleep: shown at standby forgotten', SS, 'SourceFollow.shownPort() > 0 || sShownAtStandby', 'SourceFollow.shownPort() > 0'),
    ('sleep: standby goes to sleep directly', STC,
     'org.lumen.hdmi.SleepSource.goToSleep(sApp, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);\n        } catch (Throwable t) {\n            Log.e(TAG, "goToSleep failed: lamp-only',
     'sPm.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);\n        } catch (Throwable t) {\n            Log.e(TAG, "goToSleep failed: lamp-only'),
    ('sleep: no mark at standby enter', STC, 'org.lumen.hdmi.SleepSource.standbyBegins();', ''),
    ('sleep: the X50 power key without the select', X + 'Standby.java', 'org.lumen.hdmi.SleepSource.tvTakesSource(sApp);', ''),
    ('sleep: the watched device turning off sleeps directly', SF,
     'SleepSource.goToSleep(sApp, PowerManager.GO_TO_SLEEP_REASON_HDMI, 0);',
     'sApp.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_HDMI, 0);'),
    # the <Inactive Source> rule behind the family flag (off everywhere)
    ('inactive: flag on by default (X50 family)', 'apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/projector/Family.java', 'INACTIVE_SOURCE_SLEEP = false;', 'INACTIVE_SOURCE_SLEEP = true;'),
    ('inactive: the history read without the flag', SF, 'if (port == 0 || !HdmiRelink.inactiveSourceSleep()) return;', 'if (port == 0) return;'),
    ('inactive: 2.5 s wait', IS, 'WAIT_MS = 5_000;', 'WAIT_MS = 2_500;'),
    ('inactive: a key sent to the device ignored', IS, 'if (e.opcode == KEY) target = -1;', ''),
    ('inactive: a projector key ignored', IS, '    public void key() {\n        target = -1;', '    public void key() {\n'),
    ('inactive: any device behind any port', IS, 'if (e.opcode == INACTIVE && has(behind, e.initiator)) {', 'if (e.opcode == INACTIVE) {'),
    ('inactive: the device back is ignored', IS, 'else if (e.opcode == ACTIVE\n                    || (', 'else if (('),
    ('inactive: Text View On ignored', IS, 'e.opcode == IMAGE_VIEW_ON || e.opcode == TEXT_VIEW_ON', 'e.opcode == IMAGE_VIEW_ON'),
    ('inactive: asleep counts', IS, 'if (!on || !awake) {', 'if (!on) {'),
    ('inactive: the switch ignored', IS, 'if (!on || !awake) {', 'if (!awake) {'),
    ('inactive: sleeps on every read after', IS, '        target = -1;\n        return true;', '        return true;'),
]

COPY = ['apps/core/lumen-hdmi', 'apps/sdk/framework-full-headers.jar'] + ['devices/%s.layers' % r for r in (
    'horse_popular/app/Z9xProjector', 'goat_elegant/app/HbProjector', '_family/mt9681/app/X50Projector',
    )]


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
