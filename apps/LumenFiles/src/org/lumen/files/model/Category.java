// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

/** The places strip's kind views: every file of one kind on the projector's storage and the USB drives. */
public enum Category {
    VIDEO(Kind.VIDEO), MUSIC(Kind.MUSIC), PHOTO(Kind.PHOTO), APK(Kind.APK), ARCHIVE(Kind.ARCHIVE);

    public final Kind kind;

    Category(Kind kind) {
        this.kind = kind;
    }
}
