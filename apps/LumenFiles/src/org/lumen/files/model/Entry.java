// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

/** One row: a folder or a file of a store ({@link Fs}), its path in that store, kind, size and date. */
public final class Entry {
    public final String name, path;
    public final Kind kind;
    public final long size, modified;
    /** Search and kind views: the place and folder it lies in. */
    public String where = "";
    /** DLNA: the item's http resource and the type the server gives it. */
    public String url = "", mime = "";

    public Entry(String name, String path, Kind kind, long size, long modified) {
        this.name = name;
        this.path = path;
        this.kind = kind;
        this.size = size;
        this.modified = modified;
    }

    public boolean dir() {
        return kind == Kind.FOLDER;
    }
}
