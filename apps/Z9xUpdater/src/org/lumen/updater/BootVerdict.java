// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/** BootReceiver.evaluate, once per boot (and after an app update). */
enum BootVerdict {
    /** nothing was pending (or the update still waits for its restart) */
    NONE,
    /** a pending record from an install that never reached update_engine's end: dropped */
    STALE,
    /** the update runs; the gate's verdict comes later (z9x_ota.sh health, 90 s after boot) */
    OK,
    /** the update runs, and the gate already reports a problem */
    UNHEALTHY,
    /** the build the update started from runs again (gate rollback, bootloader fallback, undone switch) */
    ROLLED_BACK,
    /** neither the update nor the build it started from runs (reinstalled from a computer): no message */
    OTHER,
    /** the install was cut by a restart: update_engine starts over next time */
    INTERRUPTED
}
