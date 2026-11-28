package androidx.fragment.app;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class Fragment implements LifecycleOwner {
    @Override
    public Lifecycle getLifecycle() { return null; }
    public final FragmentActivity getActivity() { return null; }
    public Context getContext() { return null; }
    public final Resources getResources() { return null; }
    public LifecycleOwner getViewLifecycleOwner() { return null; }
    public void onAttach(Context context) {}
    public void onCreate(Bundle savedInstanceState) {}
    public void onViewCreated(View view, Bundle savedInstanceState) {}
    public void onSaveInstanceState(Bundle outState) {}
    public void onStart() {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onDestroy() {}
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {}
    public void onPrepareOptionsMenu(Menu menu) {}
    public boolean onOptionsItemSelected(MenuItem item) { return false; }
}
