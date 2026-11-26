// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.os.RecoverySystem;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A whole recovery OTA package (ViaRecovery): its signature against /system/etc/security/otacerts.zip (the Lumen
 * certificate of the model) and the hand-over to the recovery. The package lives on /cache (not encrypted, mounted
 * by the recovery), so no uncrypt block map is needed.
 */
final class RecoveryPackage {
    private RecoveryPackage() {}

    /** Whole-file signature check of the package against the system OTA certificates. */
    static boolean verify(File zip) {
        try {
            RecoverySystem.verifyPackage(zip, null, null);
            return true;
        } catch (GeneralSecurityException | IOException e) {
            Log.w(Ota.TAG, "verifyPackage " + zip + ": " + e);
            return false;
        }
    }

    /**
     * BCB + restart into the recovery. Returns only on failure (RecoverySystem throws). The caller checked the
     * package with {@link #verify} right before, while the state still said "downloaded".
     */
    static void install(Context c, File zip) throws IOException {
        // a new install starts from scratch: the updater's resume markers of an earlier install
        // (last_command, *.UPDATED) would turn its steps into no-ops
        File[] old = new File("/cache/recovery").listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.getName().endsWith(".UPDATED") || f.getName().equals("last_command")) f.delete();
            }
        }
        Log.i(Ota.TAG, "installPackage " + zip + ": restarting into the recovery");
        RecoverySystem.installPackage(c, zip);
        throw new IOException("installPackage returned");
    }
}
