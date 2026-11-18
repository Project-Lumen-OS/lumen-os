package org.lumen.home.proj;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;

/**
 * The screen mirroring receiver (org.lumen.miracast, apps/LumenMiracast) as Lumen Home sees it, on every device. Its
 * control provider content://org.lumen.miracast resolves only while the receiver has found Wi-Fi Direct on this unit
 * (LumenMiracast ControlGate switches it off without), the rule the projector menu builds its own tile by (lumen-menu
 * QpdMirror.installed). So the Home tile shows exactly where the menu tile shows, with no projector app in between.
 */
public final class MirrorReceiver {
    private MirrorReceiver() {}

    public static final String PKG = "org.lumen.miracast";
    static final String AUTHORITY = "org.lumen.miracast";

    /** The tile shows: the control provider resolves to the receiver's own system package. */
    public static boolean shown(String providerPackage, boolean system) {
        return PKG.equals(providerPackage) && system;
    }

    /** {@link #shown} for this unit; false when the provider is off, missing or the lookup fails. */
    public static boolean offered(Context c) {
        try {
            ProviderInfo pi = c.getPackageManager().resolveContentProvider(AUTHORITY, 0);
            return pi != null && shown(pi.packageName, pi.applicationInfo != null
                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
