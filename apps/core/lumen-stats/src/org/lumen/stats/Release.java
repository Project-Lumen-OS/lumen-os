// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

/** The running Lumen OS release a ping reports. */
public final class Release {
    public final String version, build;
    public final int versionCode;

    public Release(String version, int versionCode, String build) {
        this.version = version == null ? "" : version;
        this.versionCode = versionCode;
        this.build = build == null ? "" : build;
    }
}
