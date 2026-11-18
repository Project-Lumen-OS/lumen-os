// SPDX-License-Identifier: Apache-2.0
package org.lumen.control;

/** The commands of the core every control input (adapter) translates its protocol into. */
public enum CommandId {
    /** A key of the remote ({@link RemoteKey}). */
    REMOTE_KEY,
    /** An Android key code a stock protocol passes through unchanged. */
    ANDROID_KEY,
    /** Media volume to an absolute step (clamped to 0..max). */
    AUDIO_VOLUME
}
