// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control;

/** The host's driver: runs core commands on this projector. The Android one is org.lumen.control.android.AndroidSink. */
public interface ControlSink {
    /** Runs the command; false when it did not happen (the protocol then reports a failure). */
    boolean run(Command command);

    /** Media volume as {max, current}; {0, 0} when unknown. */
    int[] volume();
}
