package com.android.tv.settings.util;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class InstrumentationUtils {
    public static void logPageFocused(int pageId, Boolean forward) {}
}
