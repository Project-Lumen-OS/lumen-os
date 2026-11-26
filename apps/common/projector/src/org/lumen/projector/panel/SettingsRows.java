// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.lumen.audio.BootSound;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.R;
import org.lumen.projector.source.BootSource;
import org.lumen.projector.voice.Assistant;
import org.lumen.projector.voice.Speaker;
import org.lumen.projector.voice.VoiceKeyActivity;
import org.lumen.projector.voice.VoiceSettings;
import org.lumen.sensors.DiagPanel;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The rows of the old Projector settings page that live in the shared menu now (All settings, General
 * 2026-11-19): pairing a remote and Remote buttons, the source at power-on, the voice assistant, the interface
 * resolution, the statistics switch and diagnostics. One class for the Z9X, HB and Play 6 adapters with the page's
 * behaviour; what differs per model is its {@link Device}. The power-on melody row is Lumen's own sound now (lumen-menu
 * module row, lumen-audio BootSound): here the unit's stock switch only gives its first value and is turned off, so
 * the vendor music never plays next to it.
 */
public final class SettingsRows {
    public static final Set<FeatureId> FEATURES = Collections.unmodifiableSet(EnumSet.of(FeatureId.POWER_BOOT_SOURCE, FeatureId.VOICE_LANGUAGE, FeatureId.VOICE_SPEAK, FeatureId.VOICE_KEY,
            FeatureId.SETTINGS_UI_RESOLUTION, FeatureId.SETTINGS_STATS, FeatureId.SETTINGS_DIAGNOSTICS, FeatureId.SETTINGS_REMOTE,
            FeatureId.SETTINGS_REMOTE_KEYS));
    private static final String TAG = "SettingsRows", ON = "on";
    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> new Thread(r, "settings-rows"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** The per-model part. Callbacks run on the main thread. */
    public interface Device {
        /** The stock power-on music switch (IGmpf 420 or the model's getter) on the app's HAL thread, null = no answer. */
        void music(Consumer<Boolean> cb);

        /** Writes the stock switch (419) and reads it back. */
        void setMusic(boolean on, Consumer<Boolean> cb);

        /** The option token of each source.BootSource value on this model. */
        String[] bootTokens();

        /** The statistics switch, null = nobody owns it on this unit (no row). Any thread, may block. */
        Boolean stats();

        Boolean setStats(boolean on);

        /** True when this build shows diagnostics. */
        boolean diagnostics();

        /** The diagnostic text; may call back again with a longer text. */
        void readDiagnostics(Consumer<String> cb);

        /** True when the interface resolution row is there now. */
        boolean uiResolution();

        void openUiResolution();
    }

    private final Context app;
    private final Device dev;
    /** The rows' values of the last read, kept on top of every state a write gives back. */
    private volatile MenuState last;

    public SettingsRows(Context c, Device dev) {
        this.app = c.getApplicationContext();
        this.dev = dev;
        stockMusicOff();
    }

    /** The stock switch gives Lumen's sound its first value on this unit (BootSound.adopt), then goes off. */
    private void stockMusicOff() {
        dev.music(v -> {
            if (BootSound.adopt(app, v)) dev.setMusic(false, rb -> Log.i(TAG, "stock power-on music -> " + rb));
        });
    }

    public static boolean mine(FeatureId f) { return FEATURES.contains(f); }

    public boolean available(FeatureId f) {
        switch (f) {
            case VOICE_LANGUAGE: case VOICE_SPEAK: case VOICE_KEY: return Assistant.available(app);
            case SETTINGS_UI_RESOLUTION: return dev.uiResolution();
            case SETTINGS_DIAGNOSTICS: return dev.diagnostics();
            default: return true;
        }
    }

    /** {@code base} with these rows' values on top; the statistics hide when nothing answers. */
    public void read(MenuState base, Consumer<MenuState> cb) {
        BG.execute(() -> {
            Boolean stats = dev.stats();
            MAIN.post(() -> cb.accept(merge(base, stats)));
        });
    }

    private MenuState merge(MenuState base, Boolean stats) {
        MenuState.Builder b = MenuState.builder();
        String[] boot = dev.bootTokens();
        int src = BootSource.get(app);
        if (src >= 0 && src < boot.length) b.put(FeatureId.POWER_BOOT_SOURCE, boot[src]);
        b.put(FeatureId.VOICE_LANGUAGE, VoiceSettings.languageToken(app));
        b.on(FeatureId.VOICE_SPEAK, Speaker.speakReplies(app));
        b.put(FeatureId.VOICE_KEY, VoiceSettings.keyValue(app));
        b.on(FeatureId.SETTINGS_STATS, stats).hideIf(FeatureId.SETTINGS_STATS, stats == null);
        MenuState part = b.build();
        last = part;
        return base == null ? part : base.with(part);
    }

    /** {@code st} with the rows' values of the last read (a write's read-back of the device rows alone). */
    public MenuState with(MenuState st) {
        MenuState l = last;
        return st == null || l == null ? st : st.with(l);
    }

    /** Writes one of these rows, then the adapter's full read; false when {@code f} is not one of them. */
    public boolean set(FeatureId f, String v, ProjectorFeatures adapter, Consumer<ProjectorFeatures.Result> cb) {
        Runnable reread = () -> adapter.read(st -> cb.accept(ProjectorFeatures.Result.done(st)));
        switch (f) {
            case POWER_BOOT_SOURCE: {
                int i = Arrays.asList(dev.bootTokens()).indexOf(v);
                if (i < 0) { cb.accept(ProjectorFeatures.Result.failed(null)); return true; }
                BootSource.set(app, i);
                reread.run();
                return true;
            }
            case VOICE_LANGUAGE:
                Assistant.setLanguage(app, VoiceSettings.tagOf(v));
                reread.run();
                return true;
            case VOICE_SPEAK:
                Speaker.setSpeakReplies(app, ON.equals(v));
                reread.run();
                return true;
            case SETTINGS_STATS:
                BG.execute(() -> {
                    Boolean now = dev.setStats(ON.equals(v));
                    Log.i(TAG, "statistics -> " + now);
                    MAIN.post(reread);
                });
                return true;
            default:
                return false;
        }
    }

    /** Opens one of these screens; false when {@code f} is not one of them or it cannot open. */
    public boolean open(FeatureId f) {
        switch (f) {
            case VOICE_KEY: return VoiceKeyActivity.open(app);
            case SETTINGS_REMOTE:                                   // 120 s fast LE scan, as the old page's row
                org.lumen.remote.RemotePairing.startPairing(app);
                return true;
            case SETTINGS_REMOTE_KEYS:
                org.lumen.remote.keys.KeyBindActivity.open(app);
                return true;
            case SETTINGS_UI_RESOLUTION:
                dev.openUiResolution();
                return true;
            case SETTINGS_DIAGNOSTICS:
                DiagPanel.show(app, app.getString(R.string.lumen_m_diag), dev::readDiagnostics);
                return true;
            default:
                return false;
        }
    }

}
