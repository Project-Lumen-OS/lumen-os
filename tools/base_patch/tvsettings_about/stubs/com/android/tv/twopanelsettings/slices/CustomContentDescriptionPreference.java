package com.android.tv.twopanelsettings.slices;

import android.content.Context;

import androidx.preference.Preference;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class CustomContentDescriptionPreference extends Preference {
    public CustomContentDescriptionPreference(Context context) { super(context); }
    public void setContentDescription(String contentDescription) {}
}
