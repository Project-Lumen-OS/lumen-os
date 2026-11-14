package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Values;

import java.util.function.Consumer;

/** Sound mode, eARC and the sound output. */
final class Play6SoundRows extends Play6Rows {
    Play6SoundRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case SOUND_MODE: {
                Integer w = Play6MenuGates.SOUND_MODE.wire(value);
                if (w == null) throw new IllegalArgumentException("sound mode " + value);
                g.setSoundEffect(Play6Values.soundEffect(w));                        // 61
                org.lumen.projector.audio.P6SoundEq.afterSoundModeChange(app);
                return null;
            }
            case SOUND_EARC:
                if (!g.setEarc(on)) throw new IllegalStateException("633 returned false");
                return null;
            default:
                return OTHER;
        }
    }

    /** Sound output: only the outputs connected now (AudioOutputChoice, no HAL). Main thread. */
    void setOutput(String value, Consumer<Result> cb) {
        String kind = Play6MenuGates.outputKind(value);
        if (kind == null) { refuse(cb, null); return; }
        try {
            AudioOutputChoice.Option pick = null;
            for (AudioOutputChoice.Option o : AudioOutputChoice.options(app)) if (o.kind.name().equals(kind)) pick = o;
            if (pick == null) {
                Log.i(TAG, "output " + value + ": not connected now");
                refuse(cb, null);
                return;
            }
            AudioOutputChoice.choose(app, pick.kind, pick.addr);
        } catch (Throwable t) {
            Log.w(TAG, "output " + value + ": " + t);
            Ui.main().post(() -> cb.accept(Result.failed(menu.state())));
            return;
        }
        menu.read(st -> cb.accept(Result.done(st)));
    }
}
