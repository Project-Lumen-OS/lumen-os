// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

/** A drive of the places strip: the projector's storage, a USB drive, an SD card; space -1 when unknown. */
public final class Place {
    public final PlaceKind kind;
    public final String label, root;
    public final long total, free;

    public Place(PlaceKind kind, String label, String root, long total, long free) {
        this.kind = kind;
        this.label = label;
        this.root = root;
        this.total = total;
        this.free = free;
    }

    /** Used share 0..1 for the card's bar. */
    public float used() {
        if (total <= 0 || free < 0) return 0f;
        return Math.max(0f, Math.min(1f, (total - free) / (float) total));
    }
}
