package com.android.tv.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class MainFragment {
    public static ResolveInfo systemIntentIsHandled(Context context, Intent intent) { return null; }
}
