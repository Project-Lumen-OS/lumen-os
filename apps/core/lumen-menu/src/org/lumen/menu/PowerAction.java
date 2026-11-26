// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/** What one event of the remote power key asks the device for ({@link PowerPress}). */
public enum PowerAction {
    /** Nothing: a repeat, an UP, the rest of a press that woke the projector or gave the light back. */
    NONE,
    /** The press gave the light back ({@link LampOff}) and is used up. */
    RELIGHT,
    /** The power menu (a short press or the hold, {@link PowerKeyChoice}). */
    MENU,
    /** Sleep (a short press or the hold, {@link PowerKeyChoice}). */
    SLEEP
}
