// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.DigitalOutputFormat;
import org.lumen.projector.audio.SoundEq;
import org.lumen.projector.cec.EarcLink;
import org.lumen.projector.hal.SoundEffect;

import java.util.List;
import java.util.function.Consumer;

/** Sound enhancement, sound mode, volume leveling, eARC, sound output and format. */
final class Z9xSoundRows extends Z9xRows {
    Z9xSoundRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        final boolean on = "on".equals(v);
        switch (f) {
            case SOUND_ENHANCEMENT:
                setSoundProfile(v, cb);
                return true;
            case SOUND_MODE:
                setSoundMode(v, cb);
                return true;
            case SOUND_LEVELING:                                          // QPC:1212
                prefs.edit().putBoolean(SoundProfiles.KEY_VOLUME_LEVELING, on).apply();
                write("42 volume leveling " + on, (g, g2) -> {
                    boolean ok = g.setVolumeBalance(on);
                    SoundEq.afterVendorCall(g, "42(" + on + ")");
                    if (!ok) throw new IllegalStateException("42 returned false");
                    return null;
                }, cb);
                return true;
            case SOUND_EARC:                                              // QPC:1222
                write("633 eARC " + on, (g, g2) -> {
                    if (!EarcLink.setUser(g, on)) throw new IllegalStateException("633 returned false");
                    return null;
                }, cb);
                return true;
            case SOUND_OUTPUT:
                setOutput(v, cb);
                return true;
            case SOUND_FORMAT: {                                          // QPC:1244
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.FORMAT, v);
                DigitalOutputFormat[] all = DigitalOutputFormat.values();
                if (i < 0 || i >= all.length) return false;
                AudioOutputChoice.chooseFormat(app, all[i]);
                Ui.main().post(() -> menu.read(st -> cb.accept(Result.done(st, Note.titled("sound_format_" + v)))));
                return true;
            }
            default:
                return false;
        }
    }

    /** QPC:1179 sound enhancement: SoundProfiles.apply and the stock note of what happened. */
    private void setSoundProfile(String v, Consumer<Result> cb) {
        int i = Z9xMenuRules.indexOf(Z9xMenuRules.ENH, v);
        if (i < 0 || i >= SoundProfiles.ORDER.length) { cb.accept(Result.failed(null)); return; }
        final SoundProfile prof = SoundProfiles.ORDER[i];
        final String label = text(i == 0 ? "panel_harman" : "panel_dts_vx");
        write("sound profile " + prof, (g, g2) -> {
            SoundResult r = SoundProfiles.apply(app, g, prof);
            if (r == SoundResult.APPLIED) return Result.done(null, Note.text(label, true));
            if (r == SoundResult.SPEAKER_ONLY) return Result.done(null, Note.text(text("sound_speaker_only", label), true));
            if (r == SoundResult.OUTPUT_NOT_READY) return Result.done(null, Note.text(text("sound_output_not_ready", label), true));
            Log.w(TAG, "sound profile " + prof + ": read-back differs");
            return Result.done(null, Note.titled("sound_not_confirmed"));
        }, cb);
    }

    /** QPC:1386 sound mode: AI follows the foreground app, the others are the amp tables; the curve is rewritten. */
    private void setSoundMode(String v, Consumer<Result> cb) {
        int w = Z9xMenuRules.soundWire(v);
        final SoundEffect e = w < 0 ? null : SoundEffect.fromWire(w);
        if (e == null) { cb.accept(Result.failed(null)); return; }
        final String title = text("panel_sound_mode");
        write("61 sound " + e, (g, g2) -> {
            if (e == SoundEffect.AI) {
                AiSoundEngine.userChoseAi(g);
            } else {
                g.setSoundEffect(e);
                AiSoundEngine.noteMode(e.wire, "user");
            }
            SoundProfiles.rememberMode(app, e);
            SoundEq.afterUserModeChange(g, "61(" + e.wire + ")");
            String label = e == SoundEffect.AI
                    ? text("sound_ai_sub", text(Z9xMenuRules.soundLabelRes(AiSoundEngine.shownSubMode() == null ? 1
                    : AiSoundEngine.shownSubMode().wire))) : text(Z9xMenuRules.soundLabelRes(e.wire));
            return Result.done(null, Note.text(title + ": " + label, false));
        }, cb);
    }

    /** QPC:1230 sound output: the option of the list that was shown, a preferred device of the media strategy. */
    private void setOutput(String token, Consumer<Result> cb) {
        Z9xMenuRules.In in = menu.last();
        List<AudioOutputChoice.Option> o = menu.outOptions();
        int i = in == null ? -1 : in.outTokens.indexOf(token);
        if (o == null || i < 0 || i >= o.size()) { cb.accept(Result.done(null)); return; }
        AudioOutputChoice.Option opt = o.get(i);
        AudioOutputChoice.choose(app, opt.kind, opt.addr);
        final CharSequence label = opt.label;
        Ui.main().post(() -> menu.read(st -> cb.accept(Result.done(st, Note.text(label, true)))));
    }
}
