// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;

/**
 * Constants and device facts (read-only system properties) of the Lumen OS updater. What differs between the
 * projector models comes from this model's {@link UpdateTarget} ({@code Target.SPEC}, the app's own file): the
 * property namespace, the wire id, the channels, the version scheme, the guide, the download folder.
 * The init services and runtime properties of the boot gates keep their z9x names on every model (sys.z9x.*,
 * REBOOT_REASON z9x-ota): BootReceiver and Outcome read them unchanged.
 */
public final class Ota {
    private Ota() {}

    /** This model's facts. */
    static final UpdateTarget T = Target.SPEC;

    public static final String TAG = "LumenUpdater";
    public static final String VERSION = "1.0";
    public static final String AUTHOR = "kmuradoff";
    public static final String REPO = UpdateTarget.REPO;
    public static final String PROJECT_URL = UpdateTarget.PROJECT_URL;
    /** Guide sections of the one-time vbmeta step and of "Rescue" (language picked at runtime). */
    public static final String GUIDE_VBMETA_EN = T.guide("en", "vbmeta");
    public static final String GUIDE_VBMETA_RU = T.guide("ru", "vbmeta");
    public static final String GUIDE_RESCUE_EN = T.guide("en", "rescue");
    public static final String GUIDE_RESCUE_RU = T.guide("ru", "rescue");
    public static final String USER_AGENT = "Lumen-OS-Updater/" + VERSION;
    public static final String OTACERTS = "/system/etc/security/otacerts.zip";

    public static final String PERM_STATE = "org.lumen.updater.permission.OTA_STATE";
    public static final String ACTION_STATE = "org.lumen.updater.action.OTA_STATE";
    public static final String REBOOT_REASON = "z9x-ota";
    /** org.lumen.projector power hook (receiver permission org.lumen.projector.permission.OTA_STATE). */
    public static final String PROJECTOR_PKG = "org.lumen.projector";
    public static final String PROJECTOR_ACTION_OTA_STATE = "org.lumen.projector.action.OTA_STATE";
    public static final String NOTIF_CHANNEL = "updates";

    /** update_engine status codes (system/update_engine/client_library/include/update_engine/update_status.h). */
    public static final int UE_IDLE = 0, UE_CHECKING = 1, UE_AVAILABLE = 2, UE_DOWNLOADING = 3,
            UE_VERIFYING = 4, UE_FINALIZING = 5, UE_NEED_REBOOT = 6, UE_REPORTING = 7,
            UE_ROLLBACK = 8, UE_DISABLED = 9, UE_CLEANUP = 11;
    /** update_engine error codes we explain (common/error_code.h). */
    public static final int ERR_SUCCESS = 0, ERR_NOT_ENOUGH_SPACE = 60, ERR_UPDATED_BUT_NOT_ACTIVE = 52,
            ERR_TIMESTAMP = 51, ERR_USER_CANCELED = 48;

    public static String prop(String key) {
        try {
            return SystemProperties.get(key, "");
        } catch (Throwable t) {
            return "";
        }
    }

    /** The wire id a manifest must name; empty = this image takes no update (fail closed). */
    public static String device() { return T.device(); }

    /** &lt;props&gt;.version_code, else derived from &lt;props&gt;.version. */
    public static int currentVersionCode() {
        String vc = prop(T.props + ".version_code");
        try {
            if (!vc.isEmpty()) return Integer.parseInt(vc.trim());
        } catch (NumberFormatException ignored) {
        }
        return versionCode(prop(T.props + ".version"));
    }

    /** The version code of a version name in this model's scheme (VersionScheme). */
    public static int versionCode(String v) { return T.versions.code(v); }

    /** The version name of a version code, as a refusal shows it. */
    public static String versionName(int code) { return T.versions.name(code); }

    public static String currentVersion() {
        String v = prop(T.props + ".version");
        return v.isEmpty() ? "?" : v;
    }

    public static String buildId() { return prop(T.props + ".build_id"); }

    /** The manifest this image was built to read (ChannelRule.image). */
    public static String imageManifestUrl() { return T.channels.image(T); }

    /** The user's "Beta updates" choice (Store, the app's own storage); null = not chosen = the image's channel. */
    private static volatile Boolean sBeta;

    static void setBeta(Boolean on) { sBeta = on; }

    /** {stable, beta} manifest URLs of the channel an image of manifest URL {@code image} reads, or null (no beta). */
    static String[] channels(String image) { return T.channels.pair(T, image); }

    /** True when this image has a beta channel: only then the updater shows the "Beta updates" switch. */
    public static boolean betaAvailable() { return channels(imageManifestUrl()) != null; }

    /** True when the beta channel is read: the user's choice, else whether the image itself reads it. */
    public static boolean betaOn() { return betaOn(imageManifestUrl(), sBeta); }

    static boolean betaOn(String image, Boolean choice) {
        String[] c = channels(image);
        if (c == null) return false;
        return choice != null ? choice : T.channels.betaByDefault(c, image);
    }

    /**
     * The main manifest of the chosen channel: the stable or the beta one (betaOn), else the image's own. A check
     * with beta on reads the stable one too (Channels.read). Turning beta off never downgrades: a manifest is
     * offered only with a version_code above the installed one (Preflight.applicable), so a beta stays until the
     * stable channel has a newer version.
     */
    public static String manifestUrl() { return manifestUrl(imageManifestUrl(), sBeta); }

    static String manifestUrl(String image, Boolean choice) {
        String[] c = channels(image);
        if (c == null) return image;
        return betaOn(image, choice) ? c[1] : c[0];
    }

    /**
     * The file name of the manifest this updater reads (update-&lt;channel&gt;.json): the USB file to look for, and the
     * channel a manifest must carry (an edition never installs another edition's update, and a beta manifest is
     * refused while beta updates are off).
     */
    public static String channelFile() { return channelFile(manifestUrl()); }

    static String channelFile(String url) {
        String f = url == null ? "" : url;
        int q = f.indexOf('?');
        if (q >= 0) f = f.substring(0, q);
        f = f.substring(f.lastIndexOf('/') + 1);
        return T.channels.file(T, f);
    }

    /** "_a"/"_b" of the running slot. */
    public static String slot() {
        String s = prop("ro.boot.slot_suffix");
        return s.isEmpty() ? prop("sys.z9x.slot") : s;
    }

    public static String slotLetter(String suffix) {
        return suffix != null && suffix.length() == 2 ? suffix.substring(1).toUpperCase() : "?";
    }

    public static String otherSlot() {
        String s = slot();
        return "_a".equals(s) ? "_b" : "_b".equals(s) ? "_a" : "";
    }

    /** AVB flags of vbmeta_<other> published by the boot gate's info service; -1 unknown. */
    public static int otherVbmetaFlags() {
        String f = prop("sys.z9x.vbmeta_other.flags");
        try {
            return Integer.parseInt(f.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** One section of this model's install guide (UpdateTarget.guide). */
    static String guide(String lang, String section) { return T.guide(lang, section); }

    /** The folder of the downloaded package (OtaDir of this model). */
    public static File dir(Context c) { return T.dir.get(c); }

    /** Best-effort property for org.lumen.projector (STR / power-off postponed while busy). */
    static void setBusy(Context c, boolean busy, String state) {
        try {
            SystemProperties.set("sys.z9x.ota.busy", busy ? "1" : "0");
        } catch (Throwable t) {
            Log.i(TAG, "sys.z9x.ota.busy not settable: " + t);
        }
        Intent i = new Intent(ACTION_STATE).putExtra("busy", busy).putExtra("state", state);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        c.sendBroadcast(i, PERM_STATE);
        // Contract: org.lumen.projector's power.OtaStateReceiver (explicit, guarded by the
        // projector's signature permission OTA_STATE) keeps the SoC out of STR while busy; the property
        // above is only a best-effort duplicate (it needs the property to be settable).
        try {
            Intent p = new Intent(PROJECTOR_ACTION_OTA_STATE).setPackage(PROJECTOR_PKG)
                    .putExtra("busy", busy)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            c.sendBroadcast(p);
        } catch (Throwable t) {
            Log.i(TAG, "projector OTA_STATE: " + t);
        }
    }

    /**
     * Sets a runtime property; false when it is refused (SELinux, or no property service). Never throws: the
     * caller turns false into a clear refusal.
     */
    static boolean setProp(String key, String val) {
        try {
            SystemProperties.set(key, val);
            return true;
        } catch (Throwable t) {
            try {
                Log.w(TAG, key + " not settable: " + t);
            } catch (Throwable ignored) {   // the host test has no android.util.Log
            }
            return false;
        }
    }
}
