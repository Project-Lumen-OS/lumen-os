// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The only per-model part of the signal panel: the projector app's adapter over its own HAL client (no new vendor
 * client, no copy of one). The panel asks only for items of {@link #items()} and never more than SignalBudget allows.
 * A read that fails or answers out of range leaves its field empty, so its row stays hidden.
 */
public interface SignalReader {
    /** The HAL items this adapter can read on this model (the panel intersects them with the model's items.tsv). */
    Set<SignalItem> items();

    /** The HDMI port on screen now (1..n), 0 = none. Main thread, no HAL call. */
    int hdmiPort();

    /** Reads {@code items} (HAL items only) in ONE task on the app's HAL thread; {@code cb} runs on the main thread. */
    void read(List<SignalItem> items, int port, Consumer<SignalValues> cb);
}
