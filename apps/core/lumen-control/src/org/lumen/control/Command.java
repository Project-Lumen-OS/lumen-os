// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control;

/** One command for the host driver ({@link ControlSink}). */
public final class Command {
    public final CommandId id;
    /** {@link CommandId#REMOTE_KEY}: the key, else null. */
    public final RemoteKey key;
    /** {@link CommandId#ANDROID_KEY}: the key code; {@link CommandId#AUDIO_VOLUME}: the step. */
    public final int value;
    public final KeyAction action;

    private Command(CommandId id, RemoteKey key, int value, KeyAction action) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.action = action;
    }

    public static Command key(RemoteKey key, KeyAction action) {
        return new Command(CommandId.REMOTE_KEY, key, RemoteKey.NONE, action);
    }

    public static Command androidKey(int keyCode, KeyAction action) {
        return new Command(CommandId.ANDROID_KEY, null, keyCode, action);
    }

    public static Command volume(int step) {
        return new Command(CommandId.AUDIO_VOLUME, null, step, KeyAction.CLICK);
    }

    @Override public String toString() {
        return id + (key != null ? " " + key : " " + value) + " " + action;
    }
}
