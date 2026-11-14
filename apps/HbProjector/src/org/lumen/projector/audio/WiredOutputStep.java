package org.lumen.projector.audio;

/** What the HB app does with its wired headphone output on a sound output evaluation. */
public enum WiredOutputStep {
    /** "Headphones" is the choice and no wired output is listed: connect it (the next evaluation routes to it). */
    CONNECT,
    /** Another output is the choice and the listed wired output is the one this app connected: remove it. */
    DISCONNECT,
    /** Nothing to do. */
    NONE
}
