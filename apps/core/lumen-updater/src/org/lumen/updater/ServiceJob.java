// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/** The long jobs of UpdateService. A start request that only repeats the running job starts nothing. */
enum ServiceJob {
    DOWNLOAD, DOWNLOAD_INSTALL, INSTALL, OTHER;

    /**
     * True when this request is already covered by {@code running}: a second "Install now" press while the install
     * job runs, an automatic download next to the user's one. Adding the install to a plain download is new work.
     */
    boolean repeats(ServiceJob running) {
        if (this == OTHER || running == null || running == OTHER) return false;
        return !(running == DOWNLOAD && this != DOWNLOAD);
    }
}
