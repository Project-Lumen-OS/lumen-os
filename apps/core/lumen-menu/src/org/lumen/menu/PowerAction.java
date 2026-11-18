// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/** What one event of the remote power key asks the device for ({@link PowerPress}). */
public enum PowerAction {
    /** Nothing: a repeat, an UP, the rest of a press that woke the projector or gave the light back. */
    NONE,
    /** The press gave the light back ({@link LampOff}) and is used up. */
    RELIGHT,
    /** The key held: the power menu. */
    MENU,
    /** A short press: sleep. */
    SLEEP
}
