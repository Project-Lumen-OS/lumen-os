package org.lumen.projector.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.os.Handler;

import org.lumen.audio.PathSwitch;
import org.lumen.audio.VendorAudio;
import org.lumen.audio.VendorCall;
import org.lumen.audio.VendorOutput;
import org.lumen.projector.Hal;
import org.lumen.projector.hal.AudioPath;
import org.lumen.projector.hal.GmpfClient;

/**
 * The Z9X side of the shared vendor audio path switch and volume split (apps/core/lumen-audio PathSwitch, StockVolume;
 * the model data is devices/horse_popular/audio/chip_volume.tsv). Only how this model reaches its vendor:
 *  - 59 setAudioOutput / 60 getAudioOutput on the "z9x-hal" thread (Hal), after the boot handshake (237);
 *  - the audio device callback only while the audio policy answers (AudioPorts: a trial without the user's audio files);
 *  - the stored digital output format again with every switch (AudioOutputChoice.resendFormat);
 *  - at process start and boot SPDIF out of the media route (SpdifOff), and before every report a valid sound table in
 *    the vendor DB (SoundProfiles.bootCheckOnce: 59(0) re-applies the DB sound mode).
 * {@link #isActive} = path reporting is active this boot (SoundProfiles shows DTS only then: 27 / 44 apply only with
 * path 0).
 */
public final class AudioPathReporter implements VendorAudio {
    private static final AudioPathReporter VENDOR = new AudioPathReporter();
    private static Context sApp;

    /** Run on the HAL thread after a 59(P) of this process was confirmed (SoundEq / AiSoundEngine re-apply). */
    public interface PathListener { void onPathConfirmed(int path); }

    private AudioPathReporter() {}

    public static void addPathListener(PathListener l) {
        if (l != null) PathSwitch.addConfirmedListener(l::onPathConfirmed);
    }

    /** Application.onCreate. Idempotent, never throws, no HAL call here. */
    public static synchronized void install(Context ctx) {
        sApp = ctx.getApplicationContext();
        PathSwitch.install(sApp, VENDOR);
    }

    /** BOOT_COMPLETED receiver. */
    public static void onBootCompleted(Context ctx) {
        install(ctx);
        PathSwitch.onBootCompleted(ctx, VENDOR);
    }

    /** SCREEN_ON (main thread). */
    public static void onScreenOn() {
        PathSwitch.onScreenOn();
    }

    /** True when the path confirmed last is what the vendor holds (ready == 1 and 60 == it). HAL thread. */
    public static boolean isActive(GmpfClient g) {
        return PathSwitch.active(new Output(g));
    }

    /** The path confirmed last (0 speaker, 2 ARC, 3 BT, 4 wired, 6 USB), or -1. Any thread. */
    public static int confirmedPath() {
        return PathSwitch.confirmed();
    }

    @Override public boolean post(String what, VendorCall call, Runnable onSkipped) {
        return Hal.runDelayed((g, g2) -> call.run(new Output(g)), 0, onSkipped);
    }

    @Override public boolean ready() {
        return Hal.isHandshakeDone();
    }

    @Override public void onConnected(Runnable r) {
        Hal.addConnectedListener(r);
    }

    @Override public void registerDevices(Context app, AudioDeviceCallback cb, Handler h) {
        AudioPorts.registerDeviceCallback(app, cb, h, "AudioPathReporter");
    }

    @Override public void formatAgain(String why) {
        AudioOutputChoice.resendFormat(why);
    }

    @Override public void onBoot(String why) {
        SpdifOff.apply(sApp, why);
    }

    /** IGmpf 59 / 60 of one HAL thread call. */
    private static final class Output implements VendorOutput {
        private final GmpfClient g;

        Output(GmpfClient g) { this.g = g; }

        @Override public void select(int path) throws Exception { g.setAudioOutput(pathOf(path)); }

        @Override public int read() throws Exception { return g.getAudioOutputRaw(); }

        @Override public void beforeReport() { org.lumen.projector.panel.SoundProfiles.bootCheckOnce(sApp, g); }
    }

    private static AudioPath pathOf(int wire) {
        for (AudioPath p : AudioPath.values()) if (p.wire == wire) return p;
        throw new IllegalArgumentException("path " + wire);
    }
}
