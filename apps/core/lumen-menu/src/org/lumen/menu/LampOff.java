// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.function.Consumer;

/**
 * The stock "Turn off screen" (power menu 关屏, PowerUtils.shutScreen), one rule for every projector that offers
 * lamp.off: the light goes off while the sound and the app in front keep running. While it is off (stock
 * XgimiWindowManagerDomestic): the power key gives the light back and does nothing else, the focus keys are eaten,
 * every other key works as usual and leaves the light off. Lumen on top: no power press (held or not) puts the
 * projector to sleep while the light is off, and a sleep, a reboot or a restart of the app never leaves it off.
 *
 * <p>A device brings its light calls ({@link Light}) and a mark that outlives its app process within one boot
 * ({@link Marker}). Plain Java, any thread.
 */
public final class LampOff {
    /** The device's light calls, as its stock sends them. */
    public interface Light {
        /** Light off, {@code done} gets true when the device reads it back off (main thread). */
        void off(Consumer<Boolean> done);

        /** Light on the stock way (eye-protection light first where it blanked it), read back before it sends. */
        void on();
    }

    /** The light-off mark: set in this boot, cleared, or left over from an earlier boot. */
    public interface Marker {
        boolean thisBoot();

        boolean earlierBoot();

        void mark(boolean off);
    }

    private final Light light;
    private final Marker marker;
    private boolean off;
    /** downTime of the last power press that gave the light back (its repeats and its UP are eaten too), -1 none. */
    private long relitPress = -1;

    public LampOff(Light light, Marker marker) {
        this.light = light;
        this.marker = marker;
    }

    /** At app start: an off light of this boot stays ours (the power key still gives it back), an older mark relights. */
    public synchronized void restore() {
        if (marker.thisBoot()) {
            off = true;
        } else if (marker.earlierBoot()) {
            marker.mark(false);
            light.on();
        }
    }

    public synchronized boolean isOff() { return off; }

    /** The menu row: light off, {@code done} gets the read-back (true = off). */
    public void turnOff(Consumer<Boolean> done) {
        light.off(ok -> {
            synchronized (this) {
                if (ok) {
                    off = true;
                    marker.mark(true);
                }
            }
            done.accept(ok);
        });
    }

    /** The menu action lamp.off of every adapter (ProjectorFeatures.run): done once the light reads off, else failed. */
    public void run(Consumer<ProjectorFeatures.Result> cb) {
        turnOff(ok -> cb.accept(ok ? ProjectorFeatures.Result.done(null) : ProjectorFeatures.Result.failed(null)));
    }

    /**
     * The brightness slider (MenuController, SliderRow edge): a fresh press towards the start while the level is at
     * its minimum turns the light off the way the "Turn off screen" row does, only where the profile offers lamp.off.
     * Every other press just steps the level, a press towards the end never turns the light off, and only the power
     * key gives the light back ({@link #eats}). {@code dir} -1 = towards the start, {@code value} null = not read yet.
     */
    public static boolean offFromSlider(boolean offered, Integer value, int min, int dir, int repeatCount) {
        return offered && dir < 0 && repeatCount == 0 && value != null && value <= min;
    }

    /** A remote key: true = eaten (the device does nothing else with it). */
    public synchronized boolean eats(LampOffKey key, boolean down, int repeatCount, long downTime) {
        if (key == LampOffKey.POWER && off && down && repeatCount == 0) {
            relitPress = downTime;
            lightOn();
            return true;
        }
        if (key == LampOffKey.POWER) return downTime == relitPress;     // the rest of that press: repeats and UP
        return off && key == LampOffKey.FOCUS;
    }

    /**
     * Android goes to sleep or the device's own standby starts: their path owns the light from here, and its wake
     * lights it (Z9X / HB PowerPolicy SCREEN_ON, Play 6 PowerKey SCREEN_ON), so the power key is the device's again.
     */
    public synchronized void onSleep() {
        if (!off) return;
        off = false;
        marker.mark(false);
    }

    private void lightOn() {
        off = false;
        marker.mark(false);
        light.on();
    }
}
