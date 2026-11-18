package com.android.settingslib;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class RestrictedLockUtils {
    public static class EnforcedAdmin {
    }

    public static void sendShowAdminSupportDetailsIntent(Context context, EnforcedAdmin admin) {}
}
