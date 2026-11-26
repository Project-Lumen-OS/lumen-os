// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * A repaint of the page on screen is not a showing of it (plain Java, host tested by sh test/sound/run.sh).
 *
 * Every render of the panel runs the shown page's shown hook, and most hooks read the device again (refresh). A
 * repaint that follows a read (apply(), or a device page repainting itself from its read listener) must not read
 * again: the read repaints, the repaint reads, for as long as the page is open. The Z9X equalizer page did exactly
 * that (read, applyEq, refreshPage, shown hook, read, ...): the page blinked and every preset step was put back by the
 * next read before its 300 ms commit, so the preset could not be changed.
 */
final class RepaintGate {
    private int depth;

    /** Runs the repaint {@code r} with the gate quiet (nested repaints too); the gate always opens again. */
    void repaint(Runnable r) {
        depth++;
        try {
            r.run();
        } finally {
            depth--;
        }
    }

    /** True while a repaint runs: a shown hook's read is dropped, the values on screen were just read. */
    boolean quiet() { return depth > 0; }
}
