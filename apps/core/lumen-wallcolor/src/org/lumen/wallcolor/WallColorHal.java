// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

/**
 * A projector's part of the wall colour adaptation: one stock vendor call, run on the app's HAL thread (never the main
 * thread). {@code done} gets true when the vendor took the call, false when it refused it or the call failed; it is
 * called exactly once, from any thread.
 */
public interface WallColorHal {
    void call(WallColorStep c, Done done);

    /** The answer of one call. */
    interface Done {
        void done(boolean ok);
    }
}
