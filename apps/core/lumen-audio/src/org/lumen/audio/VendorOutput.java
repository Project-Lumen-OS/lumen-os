// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

/** A model's vendor audio path call, on its HAL thread (Z9X / HB IGmpf 59 / 60, X50 AUDIO 2 / 3, Play 6 IGmpf 59 / 60). */
public interface VendorOutput {
    /** setAudioOutput(path): MediaPath values and MediaPath.SWITCHING. */
    void select(int path) throws Exception;

    /** getAudioOutput: the path the vendor holds. */
    int read() throws Exception;

    /** Runs before a path report on the HAL thread (Z9X / HB: a valid sound table before 59 re-applies it). */
    default void beforeReport() {}
}
