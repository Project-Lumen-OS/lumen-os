#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the shared media route rules and their wiring that test/run.sh must kill. Mutates the files IN PLACE
and restores them: run it on a frozen snapshot (a builder clone or a detached worktree), never on a tree someone edits.
    python3 apps/core/lumen-audio/test/mutate.py"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
G = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
MP = "apps/core/lumen-audio/src/org/lumen/audio/MediaPath.java"
Z_APR = "apps/LumenProjector/core/src/org/lumen/projector/audio/AudioPathReporter.java"
H_APR = "apps/LumenProjector/core/src/org/lumen/projector/audio/AudioPathReporter.java"
Z_AOC = "apps/LumenProjector/core/src/org/lumen/projector/audio/AudioOutputChoice.java"
H_AOC = "apps/LumenProjector/core/src/org/lumen/projector/audio/AudioOutputChoice.java"    # one output choice since the one projector app
H_FAM = "apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/Family.java"
P_BT = "apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/audio/BtAudio.java"
X_RULES = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/AudioRules.java"
X_OUT = "apps/LumenProjector/host/activity/src/org/lumen/x50/projector/AudioOutput.java"
X_PATH = "apps/LumenProjector/vendor/xgimi/mt9681-aidl/src/org/lumen/x50/projector/AudioPath.java"
SO = "apps/core/lumen-audio/src/org/lumen/audio/SinkOrder.java"
HS = "apps/core/lumen-audio/src/org/lumen/audio/HalSink.java"
AP = "apps/core/lumen-audio/src/org/lumen/audio/A2dpPick.java"
SM = "apps/core/lumen-audio/src/org/lumen/audio/SoundbarMode.java"
H_SAM = "apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/cec/HbSystemAudio.java"
H_RULES = "apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/audio/HbAudioRules.java"
DO = "apps/core/lumen-audio/src/org/lumen/audio/DspOwned.java"
CL = "apps/core/lumen-audio/src/org/lumen/audio/ChipLevels.java"
CK = "apps/core/lumen-audio/src/org/lumen/audio/ChipKey.java"
CT = "apps/core/lumen-audio/src/org/lumen/audio/ChipTable.java"
SV = "apps/core/lumen-audio/src/org/lumen/audio/StockVolume.java"
PS = "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java"
AK = "apps/core/lumen-audio/src/org/lumen/audio/AutoPick.java"
AO = "apps/core/lumen-audio/src/org/lumen/audio/AutoOutput.java"
JO = "apps/core/lumen-audio/src/org/lumen/audio/JackOutput.java"
H_JACK = "apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/audio/HbWiredJack.java"
AUDIO_LINE = "module    lumen-audio\n"
BS = "apps/core/lumen-audio/src/org/lumen/audio/BootSound.java"
BR = "apps/core/lumen-audio/src/org/lumen/audio/BootSoundRules.java"
BRC = "apps/core/lumen-audio/image/lumen_boot_sound.rc"
SR = "apps/common/projector/src/org/lumen/projector/panel/SettingsRows.java"
MS = "apps/core/lumen-menu/src/org/lumen/projector/panel/ModuleScreens.java"
MUTANTS = [
    # the optical jack (path 1): heard only alone, never over the speaker the vendor policy pairs it with
    (MP, "optical: heard before every output", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL};", "HEARD = {OPTICAL, BT, WIRED, USB, ARC, SPEAKER};"),
    (MP, "optical: never heard", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL};", "HEARD = {BT, WIRED, USB, ARC, SPEAKER};"),
    (MP, "optical: not a digital output", "return path == ARC || path == OPTICAL;", "return path == ARC;"),
    ("devices/g0100/audio/chip_volume.tsv", "optical: Ultra Max table loses the jack", "xgimi_pcm_volume  -       1 ", "xgimi_pcm_volume  -       - "),
    # the power-on sound switch (BootSound): every power-on sound follows it, the stock switch only on a user's change
    (BR, "boot sound: unset switch is off", "return !OFF.equals(stored);", "return ON.equals(stored);"),
    (BR, "boot sound: the boot animation gate ignores the switch", "return on && !muted && index > 0 ? ON : OFF;",
     "return !muted && index > 0 ? ON : OFF;"),
    (BR, "boot sound: a wake ignores the switch", "return on && booted && sinceBootMs", "return booted && sinceBootMs"),
    (BRC, "boot sound: the rc does not copy the off gate",
     "on property:persist.lumen.boot_sound.play=0\n    setprop persist.sys.bootanim.play_sound 0\n", ""),
    (BS, "boot sound: the row does not write the stock switch", "        if (s != null) s.set(on);\n", ""),
    (BS, "boot sound: the stock switch gets the other value", "if (s != null) s.set(on);", "if (s != null) s.set(!on);"),
    (BS, "boot sound: adopt writes the stock switch", "            Log.i(TAG, \"first value from the stock switch: \" + first);\n",
     "            Log.i(TAG, \"first value from the stock switch: \" + first);\n            if (sStock != null) sStock.set(false);\n"),
    (SR, "boot sound: the stock switch off at every start (old head)", "        dev.music(v -> BootSound.adopt(app, v));\n",
     "        dev.music(v -> { BootSound.adopt(app, v); dev.setMusic(false, rb -> { }); });\n"),
    (SR, "boot sound: no stock writer", "        BootSound.stock(on -> dev.setMusic(on, ", "        BootSound.stock(on -> dev.setMusic(true, "),
    (SR, "boot sound: the stock value not adopted", "dev.music(v -> BootSound.adopt(app, v));", "dev.music(v -> { });"),
    (MS, "boot sound: the row writes a fixed value", "BootSound.set(c, ON.equals(value));", "BootSound.set(c, true);"),
    (PS, "boot sound: an app changes the switch by itself", "BootSound.install(sApp);",
     "BootSound.install(sApp);\n        BootSound.set(sApp, true);"),
    (MP, "format at the ARC connect always (LR-5MVYWT double pcm)", "return confirmed == ARC && !sentSinceArc;", "return true;"),
    (MP, "format at the ARC connect ignores what was sent", "return confirmed == ARC && !sentSinceArc;", "return confirmed == ARC;"),
    (MP, "format at the ARC connect whatever the path", "return confirmed == ARC && !sentSinceArc;", "return !sentSinceArc;"),
    ("apps/LumenProjector/core/src/org/lumen/projector/audio/AudioOutputChoice.java", "Z9X/HB: ARC connect sends unconditionally",
     "if (arc && !sArcSeen && MediaPath.formatOnArc(PathSwitch.confirmed(), sFormatSinceArc)) sendFormat", "if (arc && !sArcSeen) sendFormat"),
    ("apps/LumenProjector/core/src/org/lumen/projector/audio/AudioOutputChoice.java", "Z9X/HB: a send does not mark it",
     "            sFormatSinceArc = true;\n", ""),
    ("apps/LumenProjector/host/activity/src/org/lumen/x50/projector/AudioOutput.java", "X50: ARC absent keeps the mark",
     "        if (!arc) sFormatSinceArc = false;\n", ""),
    (MP, "heard: list order (arc before bt)", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL}", "HEARD = {ARC, BT, WIRED, USB, SPEAKER, OPTICAL}"),
    (MP, "heard: speaker before arc", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL}", "HEARD = {BT, WIRED, USB, SPEAKER, ARC, OPTICAL}"),
    (MP, "heard: usb before wired", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL}", "HEARD = {BT, USB, WIRED, ARC, SPEAKER, OPTICAL}"),
    (MP, "heard: no usb", "HEARD = {BT, WIRED, USB, ARC, SPEAKER, OPTICAL}", "HEARD = {BT, WIRED, ARC, SPEAKER, OPTICAL}"),
    (MP, "heard: null list", "if (paths == null) return NONE;", "if (paths == null) return SPEAKER;"),
    (MP, "formatAgain: first report too", "return prev != NONE && next != NONE && prev != next;", "return next != NONE && prev != next;"),
    (MP, "formatAgain: same path too", "return prev != NONE && next != NONE && prev != next;", "return prev != NONE && next != NONE;"),
    ("devices/goat_elegant/app/HbProjector.layers", "HB recipe without the module", AUDIO_LINE, ""),
    ("devices/horse_popular/app/Z9xProjector.layers", "Z9X recipe without the module", AUDIO_LINE, ""),
    ("devices/dragon_healthy/app/Play6Projector.layers", "Play 6 recipe without the module", AUDIO_LINE, ""),
    ("devices/_family/mt9681/app/X50Projector.layers", "RS30 recipe without the module", AUDIO_LINE, ""),
    (SO, "order: an older ticket after a newer one goes", "if (ticket <= written) return false;", "if (false) return false;"),
    (SO, "order: the same ticket twice", "if (ticket <= written) return false;", "if (ticket < written) return false;"),
    (SO, "order: the route is ignored", "if (routeNow != ROUTE_UNKNOWN && forRoute != routeNow) return false;", "if (false) return false;"),
    (SO, "order: an unknown route blocks", "if (routeNow != ROUTE_UNKNOWN && forRoute != routeNow) return false;",
     "if (forRoute != routeNow) return false;"),
    (SO, "order: a sent ticket not recorded", "        written = ticket;\n", ""),
    (HS, "HalSink: no order check", "if (!ORDER.claim(ticket, forRoute, now)) {", "if (false) {"),
    (HS, "HalSink: speaker path without switch_start", "        if (start) am.setParameters(START + path);\n", ""),
    (HS, "HalSink: switch_start on every end", "        if (start) am.setParameters(START + path);\n", "        am.setParameters(START + path);\n"),
    (AP, "pick: the stored device ignored", "            for (String a : connected) if (stored.equalsIgnoreCase(a)) return a;\n", ""),
    (AP, "pick: a stale stored address kept", "        return connected.get(0);", "        return stored;"),
    (AP, "pick: address case matters", "for (String a : connected) if (stored.equalsIgnoreCase(a)) return a;",
     "for (String a : connected) if (stored.equals(a)) return a;"),
    (AP, "activate: also the active device", "            for (String a : active) if (pick.equalsIgnoreCase(a)) return false;\n", ""),
    (AP, "activate: never", "            for (String a : active) if (pick.equalsIgnoreCase(a)) return false;\n        }\n        return true;",
     "            for (String a : active) if (pick.equalsIgnoreCase(a)) return false;\n        }\n        return false;"),
    (H_AOC, "HB choice: no activation", "                if (BtA2dp.activateIfNeeded(pick, why)) return false;", "                if (false) return false;"),
    (Z_AOC, "Z9X choice: stale address kept", "            String pick = BtA2dp.pick(addr);", "            String pick = null;"),
    (Z_AOC, "choice: Bluetooth row from AudioManager only", "                Option bt = btOption(ctx, am, k == kind(ctx) ? addr : \"\");\n                if (bt != null) out.add(bt);\n                continue;\n",
     "                if (true) {}\n"),
    (X_OUT, "X50 choice: inactive device no row", "            if (!out.contains(AudioRules.BT) && !BtA2dp.connected().isEmpty()) out.add(AudioRules.BT);\n", ""),
    (H_AOC, "HB choice: the route with the stored address", "        AudioDeviceInfo d = k == AudioOutputKind.AUTO ? AutoOutput.device(am, Family.JACK_SENSED) : find(am, k, addr);",
     "        AudioDeviceInfo d = k == AudioOutputKind.AUTO ? AutoOutput.device(am, Family.JACK_SENSED) : find(am, k, prefs(app).getString(KEY_ADDR, \"\"));"),
    (DO, "owned: AudioService adjusts nothing", "music, Runnable::run, NONE, false);", "music, Runnable::run, NONE, true);"),
    (DO, "owned: an index in the registration", "new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build()",
     "new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).setVolumeIndex(0).build()"),
    (DO, "owned: every call registers again", "        if (sDone || types == null) return;\n", "        if (types == null) return;\n"),
    (DO, "owned: no policy index at once", "                    AudioSystem.setStreamVolumeIndexAS(stream, max, dev.getInternalType());\n", ""),
    (DO, "owned: a muted stream raised", "                    if (am.isStreamMute(stream)) continue;          // a muted stream stays as AudioService left it\n", ""),
    ("devices/g0111/image/x50_prop_pub.txt", "X50 image keeps the base's absolute volume", "persist.bluetooth.disableabsvol=true\n", ""),
    ("devices/h0063/image/h0063_prop_pub.txt", "Titan image absolute volume on", "persist.bluetooth.disableabsvol=true\n", "persist.bluetooth.disableabsvol=false\n"),
    (SM, "soundbar: not asked again while System Audio is on",
     "        return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;\n", "        return false;\n"),
    (SM, "soundbar: asked again also with CEC off", "return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;",
     "return SOUNDBAR.equals(kind) && controlOn && avrKnown;"),
    (SM, "soundbar: asked again without the soundbar switch", "return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;",
     "return SOUNDBAR.equals(kind) && cecOn && avrKnown;"),
    (SM, "soundbar: asked again with no audio system", "return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;",
     "return SOUNDBAR.equals(kind) && cecOn && controlOn;"),
    (SM, "Auto asked again too", "return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;",
     "return (SOUNDBAR.equals(kind) || AUTO.equals(kind)) && cecOn && controlOn && avrKnown;"),
    (SM, "every choice asked again", "return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;",
     "return cecOn && controlOn && avrKnown;"),
    (SM, "change: no mode check", "        return want != modeOn;\n", "        return true;\n"),
    (SM, "change: CEC ignored", "if (want == null || !cecOn || !controlOn || !avrKnown) return false;",
     "if (want == null || !controlOn || !avrKnown) return false;"),
    (SM, "Speaker ends System Audio (beta15)", "        if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return Boolean.TRUE;\n        return null;",
     "        if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return Boolean.TRUE;\n        if (SPEAKER.equals(kind)) return Boolean.FALSE;\n        return null;"),
    (SM, "Speaker turns System Audio on", "        if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return Boolean.TRUE;\n        return null;",
     "        if (SOUNDBAR.equals(kind) || AUTO.equals(kind) || SPEAKER.equals(kind)) return Boolean.TRUE;\n        return null;"),
    (SM, "Auto unchanged", "if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return Boolean.TRUE;",
     "if (SOUNDBAR.equals(kind)) return Boolean.TRUE;"),
    (SM, "CEC volume: Bluetooth keeps it", "if (SPEAKER.equals(kind) || BT.equals(kind) || WIRED.equals(kind) || USB.equals(kind)) return Boolean.FALSE;",
     "if (SPEAKER.equals(kind) || WIRED.equals(kind) || USB.equals(kind)) return Boolean.FALSE;"),
    (H_SAM, "HB choice: change rule only", "if (!SoundbarMode.requestOnChoice(kind, cecOn, controlOn, avr, modeOn)) {",
     "if (!HbAudioRules.systemAudioChange(want, cecOn, controlOn, avr, modeOn)) {"),
    (X_OUT, "X50 choice: change rule only", "if (!SoundbarMode.requestOnChoice(kind,", "if (!AudioRules.systemAudioChange(want,"),
    (H_RULES, "HB rules: own copy of the change rule", "return SoundbarMode.systemAudioChange(want, cecOn, controlOn, avrKnown, modeOn);",
     "if (want == null || !cecOn || !controlOn || !avrKnown) return false;\n        return want != modeOn;"),
    (X_RULES, "X50 rules: own Speaker off again", "        return SoundbarMode.systemAudioFor(kind);\n",
     "        return \"SPEAKER\".equals(kind) ? Boolean.FALSE : SoundbarMode.systemAudioFor(kind);\n"),
    (H_RULES, "HB rules: own Speaker off again", "        return SoundbarMode.systemAudioFor(kind);\n",
     "        return \"SPEAKER\".equals(kind) ? Boolean.FALSE : SoundbarMode.systemAudioFor(kind);\n"),
    # the stock Auto output (AutoPick, AutoOutput) and the HB jack kept connected (JackOutput)
    (AK, "auto: last accessed ignored", "        if (lastAccess != MediaPath.NONE && connected.contains(lastAccess)) return lastAccess;\n", ""),
    (AK, "auto: last accessed outside Auto too", "        if (!auto || !candidate(path)) return;", "        if (!candidate(path)) return;"),
    (AK, "auto: a connect keeps the manual remnant", "        lastAccess = path;\n        manual = MediaPath.NONE;\n    }",
     "        lastAccess = path;\n    }"),
    (AK, "auto: removal of any device clears", "        if (path == lastAccess) lastAccess = MediaPath.NONE;", "        lastAccess = MediaPath.NONE;"),
    (AK, "auto: a choice keeps the last accessed", "        lastAccess = MediaPath.NONE;\n        manual = path;", "        manual = path;"),
    (AK, "auto: no manual remnant", "            if (connected.contains(manual)) return manual;\n", ""),
    (AK, "auto: manual remnant never forgotten", "            manual = MediaPath.NONE;\n        }\n        for", "        }\n        for"),
    (AK, "auto: fallback ARC before Bluetooth", "FALLBACK = {MediaPath.BT, MediaPath.WIRED, MediaPath.USB, MediaPath.ARC, MediaPath.SPEAKER}",
     "FALLBACK = {MediaPath.ARC, MediaPath.BT, MediaPath.WIRED, MediaPath.USB, MediaPath.SPEAKER}"),
    (AK, "auto: unknown paths count", "        if (!auto || !candidate(path)) return;", "        if (!auto) return;"),
    (AO, "auto: the HB jack is a candidate", "return p == MediaPath.WIRED && !jackSensed ? MediaPath.NONE : p;", "return p;"),
    (JO, "jack: removed again", "am.setWiredDeviceConnectionState(jack(), 1);", "am.setWiredDeviceConnectionState(jack(), chosen ? 1 : 0);"),
    (JO, "jack: role after the first connection", "            aside(am, media, !chosen, why);\n            List<String> active = BtA2dp.active();\n            am.setWiredDeviceConnectionState(jack(), 1);\n",
     "            List<String> active = BtA2dp.active();\n            am.setWiredDeviceConnectionState(jack(), 1);\n            aside(am, media, !chosen, why);\n"),
    (JO, "jack: never settled", "            if (listed(am)) aside(am, media, !chosen, why);\n", ""),
    (H_FAM, "HB: the jack sensed", "public static final boolean JACK_SENSED = false;", "public static final boolean JACK_SENSED = true;"),
    (Z_AOC, "Z9X: Auto by AOSP again", "? AutoOutput.device(am, Family.JACK_SENSED) : find(am, k, addr);", "? null : find(am, k, addr);"),
    (H_AOC, "HB: Auto by AOSP again", "? AutoOutput.device(am, Family.JACK_SENSED) : find(am, k, addr);", "? null : find(am, k, addr);"),
    (X_OUT, "X50: Auto by AOSP again", "? AutoOutput.device(am, JACK_SENSED) : find(am, k, addr);", "? null : find(am, k, addr);"),
    (Z_AOC, "Z9X: removals not reported", "                        AutoOutput.removed(d);\n", ""),
    (H_AOC, "HB: user choice not reported", "        AutoOutput.chose(k.name());\n        install(ctx);", "        install(ctx);"),
    (X_OUT, "X50: additions not reported", "                        AutoOutput.added(d, AudioRules.AUTO.equals(kind(sApp)), JACK_SENSED);\n", ""),
    # the stock volume split: ChipTable, ChipLevels, StockVolume, DspOwned and the one path switch (volume-z9x-play6)
    (CL, "level: muted not silent", "        if (muted) return 0;\n", ""),
    (CL, "level: truncates", "int v = Math.round(index * 100f / Math.max(1, max));", "int v = (int) (index * 100f / Math.max(1, max));"),
    (CL, "level: no upper clamp", "return Math.max(0, Math.min(FULL, v));", "return Math.max(0, v);"),
    (CL, "level: no lower clamp", "return Math.max(0, Math.min(FULL, v));", "return Math.min(FULL, v);"),
    (CL, "a2dp: absolute volume ignored", "if (row.type != ChipTable.TYPE_A2DP || !avrcpAbsolute) return v;", "if (true) return v;"),
    (CL, "a2dp: absolute on every output", "if (row.type != ChipTable.TYPE_A2DP || !avrcpAbsolute) return v;", "if (!avrcpAbsolute) return v;"),
    (CL, "a2dp: absolute at 0 not silent", "return v == 0 ? 0 : FULL;", "return FULL;"),
    (CL, "due: the same level again", "if (!force && had != null && had == level) return null;", "if (false) return null;"),
    (CL, "due: force ignored", "if (!force && had != null && had == level) return null;", "if (had != null && had == level) return null;"),
    (CL, "due: no range check", "if (level < 0 || level > FULL) return null;", ""),
    (CL, "forget: levels kept after the audio server", "        sent.clear();\n", ""),
    (CL, "pick: always the first type", "for (ChipKey r : slot) if (connectedTypes.contains(r.type)) return r;", ""),
    (CK, "param: device id dropped", "key + \"=\" + level + \";device=\" + device", "key + \"=\" + level"),
    (CK, "slot: one slot for every A2DP id", "return device == NO_DEVICE ? key : key + \";device=\" + device;", "return key;"),
    (CT, "owned: A2DP also with absolute volume", "if (!r.hasKey() || (avrcpAbsolute && r.type == TYPE_A2DP)) continue;", "if (!r.hasKey()) continue;"),
    (CT, "owned: outputs without a key too", "if (!r.hasKey() || (avrcpAbsolute && r.type == TYPE_A2DP)) continue;",
     "if (avrcpAbsolute && r.type == TYPE_A2DP) continue;"),
    (CT, "pathOf: unlisted never the table path", "        return defaultPath;\n", "        return MediaPath.NONE;\n"),
    (CT, "ofPath: rows without a key", "if (r.hasKey() && r.path == path && path != MediaPath.NONE) out.add(r);", "if (r.path == path) out.add(r);"),
    (CT, "parse: comments kept", "            if (hash >= 0) line = line.substring(0, hash);\n", ""),
    (CT, "parse: after-end ignored", "                        after = true;\n", ""),
    (CT, "parse: any value count", "        if (f.length != n) throw", "        if (f.length < 2) throw"),
    (CT, "parse: fixed list dropped", "                        fixed.add(Integer.parseInt(f[1]));\n", ""),
    (MP, "switch start: also the first report", "return prev != NONE && next != NONE && prev != next;", "return next != NONE && prev != next;"),
    (MP, "switch start: also the same path", "return prev != NONE && next != NONE && prev != next;", "return prev != NONE && next != NONE;"),
    (MP, "delay: always 300 ms", "? STOCK_SWITCH_MS : SHORT_SWITCH_MS;", "? SHORT_SWITCH_MS : SHORT_SWITCH_MS;"),
    (MP, "delay: 1 s only to ARC", "(digital(prev) || digital(next))", "(digital(next))"),
    (DO, "owned: AudioService adjusts nothing", "music, Runnable::run, NONE, false);", "music, Runnable::run, NONE, true);"),
    (DO, "owned: an index in the registration", "new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build()",
     "new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).setVolumeIndex(0).build()"),
    (DO, "owned: every call registers again", "        if (sDone || types == null) return;\n", "        if (types == null) return;\n"),
    (DO, "owned: no policy index at once", "                    AudioSystem.setStreamVolumeIndexAS(stream, max, dev.getInternalType());\n", ""),
    (DO, "owned: a muted stream raised", "                    if (am.isStreamMute(stream)) continue;          // a muted stream stays as AudioService left it\n", ""),
    (DO, "owned: music and clicks only", "STREAMS = {AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM,\n            AudioManager.STREAM_RING, ",
     "STREAMS = {AudioManager.STREAM_SYSTEM,\n            "),
    (SV, "volume: no split", '        DspOwned.apply(app, t.owned(avrcpAbsolute()), "process start");\n', ""),
    (SV, "volume: the routed index for every output", "            int level = level(am, dvm, r, abs);", "            int level = level(am, null, r, abs);"),
    (SV, "volume: one row of a slot for all", "            ChipKey r = ChipLevels.pick(slot, connected);", "            ChipKey r = slot.get(0);"),
    (SV, "volume: every stream's broadcast", "if (ACTION_VOLUME.equals(a) && i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;", ""),
    (SV, "volume: no mute broadcast", "            f.addAction(ACTION_MUTE);\n", ""),
    (SV, "volume: levels kept when the audio server dies", "                    LEVELS.forget();\n", ""),
    (SV, "volume: nothing at boot", 'sH.post(() -> sync("boot completed", true));', "sH.post(() -> {});"),
    (SV, "volume: no fixed list", "                am.setDeviceVolumeBehavior(d, AudioManager.DEVICE_VOLUME_BEHAVIOR_FIXED);\n", ""),
    (SV, "volume: wrong asset", 'ASSET = "lumen_audio/chip_volume.tsv"', 'ASSET = "chip_volume.tsv"'),
    (PS, "switch: end before the level", '            String stored = StockVolume.store(path, "before switch_end=" + path);\n', '            String stored = "";\n'),
    (PS, "switch: no level after the end", "            if (sTable.levelsAfterEnd()) stored = StockVolume.store(path,", "            if (false) stored = StockVolume.store(path,"),
    (PS, "switch: no sink when the vendor holds P", '                sink(target, "path " + target + " held");\n', ""),
    (PS, "switch: no sink after 59(P)", '            sink(target, "after 59(" + target + ")");\n', ""),
    (PS, "switch: ready prop ignored", 'if (sSwitching || sForce || !"1".equals(prop(PROP_READY))) return false;', "if (sSwitching || sForce) return false;"),
    (PS, "switch: no switch start", "            if (MediaPath.switchStart(prev, p) && readyForHal()) {", "            if (false) {"),
    (PS, "switch: no format again", "            if (MediaPath.formatAgain(prev, p)) sVendor.formatAgain(", "            if (false) sVendor.formatAgain("),
    (PS, "switch: list order", "        int p = MediaPath.heard(paths);", "        int p = paths.isEmpty() ? MediaPath.NONE : paths.get(0);"),
    (PS, "switch: no route for HalSink", "            HalSink.routeFrom(() -> sTarget);\n", ""),
    (PS, "switch: no sink after the audio server", 'StockVolume.onAudioServerUp(() -> sH.post(() -> resink("audio server up")));', "StockVolume.onAudioServerUp(() -> {});"),
    (PS, "switch: no safety end", '                sink(sTarget, "safety");\n', ""),
    (PS, "switch: before the boot", "        return booted() && sVendor.ready();", "        return sVendor.ready();"),
    (PS, "switch: end past HalSink", "            if (!HalSink.end(am, ticket, path, path, why)) return;\n",
     '            am.setParameters("xgimi_audiopath_switch_end=" + path);\n'),
    (Z_APR, "Z9X: own volume key again", "        SpdifOff.apply(sApp, why);\n", '        SpdifOff.apply(sApp, why);\n        sApp.getSystemService(android.media.AudioManager.class).setParameters("spk_volume=100");\n'),
    (Z_APR, "Z9X: not the shared switch", "        PathSwitch.install(sApp, VENDOR);\n", ""),
    (H_APR, "HB: not the shared switch", "        PathSwitch.install(sApp, VENDOR);\n", ""),
    (X_PATH, "X50: not the shared switch", "        PathSwitch.install(ctx, VENDOR);\n", ""),
    (P_BT, "Play 6: not the shared switch", "        PathSwitch.install(", "        if (false) PathSwitch.install("),
    (PS, "switch: no boot step at a late install", '        if (booted()) sH.post(() -> boot("process start"));', "        if (false) sH.post(() -> {});"),
    ("devices/horse_popular/audio/chip_volume.tsv", "Z9X table: jack on the speaker key", "out     4     headphone_volume  -       4", "out     4     spk_volume        -       4"),
    ("devices/goat_elegant/audio/chip_volume.tsv", "HB table: no USB", "out     22    usb_volume        -       6     # USB headset 0x4000000\n", ""),
    ("devices/_family/rs30/audio/chip_volume.tsv", "RS30 table: A2DP without device ids", "out     8     bluetooth_volume  128     3", "out     8     bluetooth_volume  -       3"),
    ("devices/dragon_healthy/audio/chip_volume.tsv", "Play 6 table: levels only before the end", "levels  after-end\n", ""),
    ("devices/dragon_healthy/audio/chip_volume.tsv", "Play 6 table: the A14 device ids", "out     8     bluetooth_volume  -       3", "out     8     bluetooth_volume  128     3"),
    ("devices/h0062/audio/chip_volume.tsv", "Titan table: no dock fixed", "fixed   31 ", "# xxxx 31 "),
    ("devices/_family/mt9681/app/X50Projector.layers", "Ultra Max recipe without the table", "file      {device}/audio/chip_volume.tsv assets/lumen_audio/chip_volume.tsv\n", ""),
    ("overlay/v1/z9x_base.rc", "Z9X image: the user's Bluetooth absolute volume ON is lost", "on early-boot && property:persist.lumen.bt_absvol=1\n    setprop persist.bluetooth.disableabsvol false\n", ""),
    ("overlay/play6/play6_base.rc", "Play 6 image: the user's Bluetooth absolute volume ON is lost", "on early-boot && property:persist.lumen.bt_absvol=1\n    setprop persist.bluetooth.disableabsvol false\n", ""),
    ("devices/goat_elegant/image/hb_bt.rc", "HB image: the user's Bluetooth absolute volume ON is lost", "on early-boot && property:persist.lumen.bt_absvol=1\n    setprop persist.bluetooth.disableabsvol false\n", ""),
    ("overlay/v1/z9x_base.rc", "Z9X image: absolute volume on", "    setprop persist.bluetooth.disableabsvol true\n", "    setprop persist.bluetooth.disableabsvol false\n"),
    ("overlay/play6/play6_base.rc", "Play 6 image: absolute volume on", "    setprop persist.bluetooth.disableabsvol true\n", "    setprop persist.bluetooth.disableabsvol false\n"),
]

def run():
    return subprocess.run(["bash", os.path.join(HERE, "test", "run.sh")], capture_output=True, text=True)


def main():
    files = {f: open(os.path.join(G, f), encoding="utf-8").read() for f in {m[0] for m in MUTANTS}}
    bad = [n for f, n, a, _ in MUTANTS if files[f].count(a) != 1]
    if bad:
        print("BROKEN anchors: %s" % ", ".join(bad))
        return 2
    only = os.environ.get("MUT_ONLY")
    mutants = [m for m in MUTANTS if not only or only in m[1]]
    base = run()
    if base.returncode != 0:
        print("the unmutated suite is red:\n" + base.stdout[-2000:] + base.stderr[-2000:])
        return 2
    survived = []
    try:
        for f, name, a, b in mutants:
            with open(os.path.join(G, f), "w", encoding="utf-8") as fh:
                fh.write(files[f].replace(a, b, 1))
            r = run()
            with open(os.path.join(G, f), "w", encoding="utf-8") as fh:
                fh.write(files[f])
            fails = [l for l in r.stdout.splitlines() if l.startswith("FAIL")]
            if r.returncode == 0:
                survived.append(name)
                print("SURVIVED %s" % name)
            else:
                print("killed   %s: %s" % (name, (fails[0] if fails else (r.stdout + r.stderr).strip().splitlines()[-1])[:150]))
    finally:
        for f, text in files.items():
            with open(os.path.join(G, f), "w", encoding="utf-8") as fh:
                fh.write(text)
    print("lumen-audio mutants: %d killed, %d survived" % (len(mutants) - len(survived), len(survived)))
    return 1 if survived else 0


if __name__ == "__main__":
    sys.exit(main())
