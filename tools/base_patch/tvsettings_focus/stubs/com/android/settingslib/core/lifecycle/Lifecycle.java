package com.android.settingslib.core.lifecycle;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.preference.PreferenceScreen;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class Lifecycle extends LifecycleRegistry {
    public Lifecycle(LifecycleOwner provider) { super(provider); }
    public void onAttach(Context context) {}
    public void onCreate(Bundle savedInstanceState) {}
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {}
    public void onSaveInstanceState(Bundle outState) {}
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {}
    public void onPrepareOptionsMenu(Menu menu) {}
    public boolean onOptionsItemSelected(MenuItem menuItem) { return false; }
}
