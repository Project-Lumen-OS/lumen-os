// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.os.Handler;

/**
 * The per-model part of the one path switch (PathSwitch): how this model reaches its vendor audio path call and the
 * model's own hooks. Everything else (when to switch, the HAL sink, the levels) is shared.
 */
public interface VendorAudio {
    /** Runs {@code call} on the model's HAL thread; {@code onSkipped} when it was dropped (HAL not connected). False = not queued. */
    boolean post(String what, VendorCall call, Runnable onSkipped);

    /** The vendor takes a path report now (Z9X / HB: the boot handshake 237 is done). */
    boolean ready();

    /** {@code r} after every vendor HAL (re)connect, any thread. */
    void onConnected(Runnable r);

    /** Registers the audio device callback of the path switch on {@code h}. */
    void registerDevices(Context app, AudioDeviceCallback cb, Handler h);

    /** The stored digital output format again (stock sends it with every switch start). */
    void formatAgain(String why);

    /** Process start after the boot, or boot completed, on the path switch thread (Z9X / HB: SPDIF out of the route). */
    default void onBoot(String why) {}
}
