// SPDX-License-Identifier: Apache-2.0
package org.lumen.source;

/** One press: switch to {@link #go} ({@link SourceCycle#NONE} = stay), the card names {@link #shown}. Pure. */
public final class SourceStep {
    public final int go, shown, from;

    SourceStep(int go, int shown, int from) {
        this.go = go;
        this.shown = shown;
        this.from = from;
    }

    @Override public String toString() { return "from " + from + " go " + go + " shown " + shown; }
}
