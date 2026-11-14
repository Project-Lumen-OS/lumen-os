// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

/**
 * Where a host keeps its statistics state: the switch, the random install id and what the last successful send
 * reported. The updater keeps it in its Store (Stats), an app without an updater in PrefsState.
 */
public interface StatsState {
    /** The statistics switch. */
    boolean on();

    /** The random install id, made on first use (Ping.newId). */
    String id();

    /** UTC day, version code, version and boot count of the last successful send ("", 0, "", -1 before the first). */
    String sentDay();

    int sentVc();

    String sentVersion();

    int sentBoot();

    /** Time of the last try (ms). */
    long lastTry();

    void setTry(long t);

    void setSent(String day, int vc, String version, int boot);

    /** The build an update method was recorded for at its install, and the method (ota_full, ota_delta, usb). */
    String methodBuild();

    String method();
}
