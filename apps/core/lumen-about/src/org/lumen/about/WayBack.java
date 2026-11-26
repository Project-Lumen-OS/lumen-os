// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

/**
 * What Back to the XGIMI system shows for this unit: the stick file name (the unit's own config first, else the
 * catalog's), the official file when the catalog knows it for this model and firmware, and the address the QR code
 * opens: that file, else the guide's Back to stock section. A catalog file whose name is not the name this unit's
 * boot loader looks for is never offered.
 */
public final class WayBack {
    public final String model, firmware, usbFile, link;
    /** null when the catalog knows no official file for this model and firmware */
    public final OfficialFile official;

    private WayBack(String model, String firmware, String usbFile, OfficialFile official, String link) {
        this.model = model;
        this.firmware = firmware;
        this.usbFile = usbFile;
        this.official = official;
        this.link = link;
    }

    /** catalog null: none read yet (offline); language: the ISO 639 code of the projector's language */
    public static WayBack of(XgimiFacts f, RestoreCatalog catalog, String language) {
        String file = f.usbFile.isEmpty() && catalog != null ? catalog.usbFile(f.model) : f.usbFile;
        OfficialFile o = catalog == null ? null : catalog.file(f.model, f.firmware);
        if (o != null && !o.name.equals(file)) o = null;
        return new WayBack(f.model, f.firmwareShown(), file, o, o != null ? o.url : Guide.backToStock(language));
    }
}
