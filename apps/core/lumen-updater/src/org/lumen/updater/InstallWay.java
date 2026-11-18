// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import java.io.File;
import java.io.IOException;

/**
 * How an update gets onto the projector. Two ways, each written once: update_engine writes the other slot
 * (ViaUpdateEngine: the Z9X, the HB, the X50 family), or the recovery installs a whole package after a restart
 * (ViaRecovery: the Play 6). A model names its way in its UpdateTarget (InstallKind); UpdateService and the update screen run
 * the shared steps, read the way's facts from InstallKind and ask the way only where the two installs differ.
 */
interface InstallWay {
    /** The code of a way (one instance each). */
    static InstallWay of(InstallKind k) { return k == InstallKind.RECOVERY ? Ways.RECOVERY : Ways.UPDATE_ENGINE; }

    /** The way of this model (UpdateTarget.install). */
    static InstallWay current() { return of(Ota.T.install); }

    /** Follows update_engine's progress and end (the recovery way has no callbacks). */
    void listen(Engine.Listener l, boolean on);

    /** update_engine's status (Ota.UE_*); the recovery way is always idle. */
    int status();

    void cancel();

    /** Undoes a staged slot switch (update_engine finished, the restart has not happened). */
    void revertReady();

    /**
     * The check of a downloaded package after its sha256 matched: false = refused, the error is set in the store
     * and the rejected files are deleted (a file on a USB stick is never deleted).
     */
    boolean verifyDownload(Store st, File src, UpdateManifest.Pkg p, File meta) throws IOException;

    /** An install request is dropped because the running jobs already do it. */
    boolean dropsInstall(ServiceHost h);

    /** The install of a downloaded package (worker thread), after the shared start of UpdateService.install. */
    void install(ServiceHost h, Store st, UpdateManifest m, UpdateManifest.Pkg p);

    /** Follows an install the store says is running (the screen sends WATCH at every resume). */
    void watch(ServiceHost h, Store st);

    /** The dry run of everything install() checks: one log line, nothing written. */
    String dryRun(ServiceHost h, Store st, UpdateManifest.Pkg p);
}
