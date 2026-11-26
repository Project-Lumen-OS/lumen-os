// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

/** The official XGIMI file that puts this unit's XGIMI system back: its version, size in bytes and address. */
public final class OfficialFile {
    public final String name, version, url;
    public final long size;

    OfficialFile(String name, String version, long size, String url) {
        this.name = name;
        this.version = version;
        this.size = size;
        this.url = url;
    }
}
