// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.android;

import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import org.lumen.control.Command;
import org.lumen.control.ControlSink;
import org.lumen.control.KeyAction;
import org.lumen.control.RemoteKey;
import org.lumen.control.ucontrol.UcontrolKeys;

/**
 * The Android driver of the command core. A remote key goes to the vendor keypad through ucontrolMDNS while that
 * service runs, so it means exactly what the same button means; other keys are injected as Android key codes
 * (INJECT_EVENTS, held by the platform-signed projector apps). Never call it on the main thread.
 */
public final class AndroidSink implements ControlSink {
    private static final String UCONTROL_STATE = "init.svc.ucontrolMDNS";
    private static final String RUNNING = "running";

    private final AudioManager audio;
    private final UcontrolKeys keypad = UcontrolKeys.local();

    public AndroidSink(Context c) {
        audio = c.getSystemService(AudioManager.class);
    }

    @Override public boolean run(Command c) {
        switch (c.id) {
            case REMOTE_KEY: return remoteKey(c.key, c.action);
            case ANDROID_KEY: return inject(c.value, c.action);
            case AUDIO_VOLUME: return setVolume(c.value);
            default: return false;
        }
    }

    @Override public int[] volume() {
        if (audio == null) return new int[] {0, 0};
        return new int[] {audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC)};
    }

    private boolean remoteKey(RemoteKey k, KeyAction a) {
        if (k.scancode != RemoteKey.NONE && RUNNING.equals(SysProp.get(UCONTROL_STATE)) && keypad.press(k.scancode, a)) {
            return true;
        }
        return k.keyCode != RemoteKey.NONE && inject(k.keyCode, a);
    }

    private boolean setVolume(int step) {
        if (audio == null) return false;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(step, max)), AudioManager.FLAG_SHOW_UI);
        return true;
    }

    private static boolean inject(int keyCode, KeyAction a) {
        if (keyCode <= KeyEvent.KEYCODE_UNKNOWN || keyCode > KeyEvent.getMaxKeyCode()) return false;
        try {
            Instrumentation in = new Instrumentation();
            long now = SystemClock.uptimeMillis();
            if (a != KeyAction.UP) in.sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            if (a != KeyAction.DOWN) in.sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
