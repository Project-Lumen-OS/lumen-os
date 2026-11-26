// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/**
 * The way a model installs an update, as data: the facts the shared download, screen and manifest code read
 * (InstallWay.of gives the code of the install itself).
 */
public enum InstallKind {
    /** update_engine writes the other slot: the OTA zip plus its payload metadata file, the slot switch at restart */
    UPDATE_ENGINE(true, true, false, false),
    /** the recovery installs a whole package after a restart: no payload facts, nothing to press at READY */
    RECOVERY(false, false, true, true);

    /** A manifest package must carry the update_engine payload facts (range, metadata, properties). */
    final boolean payloadFacts;
    /** A finished download has its metadata file too (else it is downloaded again). */
    final boolean metadataCompletesDownload;
    /** A file that is complete already goes straight to the checks, without a request. */
    final boolean skipsCompleteFile;
    /** The update installs in the recovery after the restart: READY offers nothing to press, other consent texts. */
    final boolean installsAfterRestart;

    InstallKind(boolean payloadFacts, boolean metadataCompletesDownload, boolean skipsCompleteFile,
                boolean installsAfterRestart) {
        this.payloadFacts = payloadFacts;
        this.metadataCompletesDownload = metadataCompletesDownload;
        this.skipsCompleteFile = skipsCompleteFile;
        this.installsAfterRestart = installsAfterRestart;
    }

    /**
     * The payload metadata file belongs to this package (downloaded or copied beside it): always with update_engine,
     * with a whole package only when its manifest names one.
     */
    boolean needsMetadata(UpdateManifest.Pkg p) { return payloadFacts || !p.metadataUrl.isEmpty(); }
}
