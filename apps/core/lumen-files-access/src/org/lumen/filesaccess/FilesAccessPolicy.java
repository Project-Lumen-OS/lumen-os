// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.filesaccess;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Which user apps get all files access from the system, and when. Pure Java: the Android side (FilesAccess) only
 * reads the package facts and applies the answer.
 */
public final class FilesAccessPolicy {
    private FilesAccessPolicy() {}

    public static final String PERMISSION = "android.permission.MANAGE_EXTERNAL_STORAGE";
    public static final String OP = "android:manage_external_storage";

    /** Trusted user apps: package name, SHA-256 of its release signing certificate (lower case hex). */
    static final String[][] TRUSTED = {
        // Lumen Play, release key of github.com/kmuradoff/kino-player (the same pin as Lumen Store's catalog)
        {"com.xgimiru.player", "39ab3b9e6436929ef3663681a8e10e78b429cbac53e93bd9e904cb74be4772a0"},
    };

    /** The trusted packages, for the start check. */
    public static String[] packages() {
        String[] out = new String[TRUSTED.length];
        for (int i = 0; i < TRUSTED.length; i++) out[i] = TRUSTED[i][0];
        return out;
    }

    /** The pinned certificate digest of a package, null when it is not trusted. */
    static String pin(String pkg) {
        for (String[] t : TRUSTED) if (t[0].equals(pkg)) return t[1];
        return null;
    }

    /** True when every current signer is the pinned certificate (and there is at least one). */
    public static boolean signedByPin(String pkg, List<String> signerSha256) {
        String pin = pin(pkg);
        if (pin == null || signerSha256 == null || signerSha256.isEmpty()) return false;
        for (String s : signerSha256) if (s == null || !pin.equals(s.toLowerCase(Locale.ROOT))) return false;
        return true;
    }

    /**
     * Whether to set the app op to allowed now. stamp: the install's lastUpdateTime, handledStamp: the one handled
     * last time (-1 when none), allowed: the app op is allowed already.
     */
    public static boolean shouldGrant(String pkg, List<String> signerSha256, boolean requested, boolean allowed,
                                      long stamp, long handledStamp) {
        if (!signedByPin(pkg, signerSha256) || !requested) return false;
        if (allowed) return false;
        return stamp != handledStamp;
    }

    /** SHA-256 of a certificate in the form of the table. */
    public static String sha256(byte[] cert) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(cert);
            StringBuilder b = new StringBuilder(d.length * 2);
            for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x & 0xff));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
