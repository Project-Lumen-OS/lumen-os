// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.filesaccess;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Which user apps get all files access, the overlay app op and the media read permissions from the system, and when.
 * Pure Java: the Android side (FilesAccess) only reads the package facts and applies the answer.
 */
public final class FilesAccessPolicy {
    private FilesAccessPolicy() {}

    public static final String PERMISSION = "android.permission.MANAGE_EXTERNAL_STORAGE";
    public static final String OP = "android:manage_external_storage";
    public static final String OVERLAY_PERMISSION = "android.permission.SYSTEM_ALERT_WINDOW";
    public static final String OVERLAY_OP = "android:system_alert_window";

    /**
     * The app ops a trusted app gets, each one only when its manifest asks for the permission behind it:
     * all files access (subtitles next to the videos of a USB stick), and drawing over other apps, without which
     * Android 10 and later never let a video sent from a phone (DLNA) or a browser open while the app is in the
     * background. {permission, app op}.
     */
    static final String[][] GRANTS = {
        {PERMISSION, OP},
        {OVERLAY_PERMISSION, OVERLAY_OP},
    };

    /**
     * The runtime permissions a trusted app gets, each one only when its manifest asks for it: the media reads
     * (Android 13 and later), so Lumen Play never asks for "Music and audio" and "Photos and videos" on a projector that
     * already gives it all files access.
     */
    static final String[] RUNTIME = {
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    };

    /** The runtime permissions to check, in order. */
    public static String[] runtimeGrants() {
        return RUNTIME.clone();
    }

    /**
     * Whether to grant one runtime permission now: as {@link #shouldGrant} (trusted key, asked for, not granted yet,
     * a new install), and never over a fixed state (the user chose "don't ask again", or a policy or the system fixed
     * it).
     */
    public static boolean shouldGrantRuntime(String pkg, List<String> signerSha256, boolean requested, boolean granted,
                                             boolean fixed, long stamp, long handledStamp) {
        return !fixed && shouldGrant(pkg, signerSha256, requested, granted, stamp, handledStamp);
    }

    /** The app ops to check, in order. */
    public static String[][] grants() {
        String[][] out = new String[GRANTS.length][];
        for (int i = 0; i < GRANTS.length; i++) out[i] = GRANTS[i].clone();
        return out;
    }

    /** Where the handled install of pkg is kept for op (all files access keeps its first key, pkg alone). */
    public static String handledKey(String pkg, String op) {
        return OP.equals(op) ? pkg : pkg + "|" + op;
    }

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
     * Whether to set one app op to allowed now. requested: the manifest asks for its permission, stamp: the install's
     * lastUpdateTime, handledStamp: the one handled last time for this op (-1 when none), allowed: the app op is
     * allowed already.
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
