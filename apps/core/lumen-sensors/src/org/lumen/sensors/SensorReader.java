// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The only per-model part of the sensor card: the projector app's adapter over its own HAL client (no new vendor
 * client). The card asks only for items of {@link #items()} that the model's items.tsv lists, and never more than
 * SensorBudget allows.
 */
public interface SensorReader {
    /** The HAL items this adapter can read on this model. */
    Set<SensorItem> items();

    /** Reads {@code items} (HAL items only) in ONE task on the app's HAL thread; {@code cb} runs on the main thread. */
    void read(List<SensorItem> items, Consumer<SensorValues> cb);
}
