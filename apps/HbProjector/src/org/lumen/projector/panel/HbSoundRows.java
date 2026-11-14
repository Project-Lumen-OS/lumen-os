package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.DigitalOutputFormat;
import org.lumen.projector.audio.HbOutputOptions;
import org.lumen.projector.audio.SoundEq;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.SoundEffect;

import java.util.function.Consumer;

/** Sound mode, eARC, sound output (with CEC System Audio) and format. */
final class HbSoundRows extends HbRows {
    HbSoundRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case SOUND_MODE:
                return setSoundMode(value, g);
            case SOUND_EARC:
                if (!org.lumen.projector.cec.EarcLink.setUser(g, on)) throw new IllegalStateException("TV 65 false");
                return null;
            default:
                return OTHER;
        }
    }

    /** HAL thread: sound mode, AI through the engine, the equalizer curve rewritten after a user change. */
    private String setSoundMode(String value, GmpfClient g) throws Exception {
        Integer w = HbMenuGates.SOUND_MODE.wire(value);
        if (w == null) throw new IllegalArgumentException("sound mode " + value);
        SoundEffect e = SoundEffect.fromWire(w);
        if (e == SoundEffect.AI) {
            AiSoundEngine.userChoseAi(g);
        } else {
            g.setSoundEffect(e);                                                // AUDIO 6
            AiSoundEngine.noteMode(e.wire, "user");
        }
        SoundProfiles.rememberMode(app, e);
        SoundEq.afterUserModeChange(g, "6(" + e.wire + ")");
        return null;
    }

    /** Sound output: AudioOutputChoice + CEC System Audio (off for the speaker, on for the soundbar / Auto). Main thread. */
    void setOutput(String value, Consumer<Result> cb) {
        String kind = HbMenuGates.outputKind(value);
        if (kind == null) { refuse(cb, null); return; }
        try {
            AudioOutputChoice.Option pick = null;
            for (AudioOutputChoice.Option o : HbOutputOptions.rows(app).options) if (o.kind.name().equals(kind)) pick = o;
            if (pick == null) {                                         // not connected now: the HB Sound page lists none
                Log.i(TAG, "output " + value + ": not offered now");
                Ui.main().post(() -> cb.accept(Result.refused(null, menu.state(last()))));
                return;
            }
            AudioOutputChoice.choose(app, pick.kind, pick.addr);
            org.lumen.projector.cec.HbSystemAudio.onChoice(app, kind);
        } catch (Throwable t) {
            Log.w(TAG, "output " + value + ": " + t);
            Ui.main().post(() -> cb.accept(Result.failed(menu.state(last()))));
            return;
        }
        Ui.main().post(() -> cb.accept(Result.done(menu.state(last()))));
    }

    void setFormat(String value, Consumer<Result> cb) {
        Integer ord = HbMenuGates.FORMAT.wire(value);
        if (ord == null) { refuse(cb, null); return; }
        AudioOutputChoice.chooseFormat(app, DigitalOutputFormat.values()[ord]);
        Ui.main().post(() -> cb.accept(Result.done(menu.state(last()))));
    }
}
