package androidx.fragment.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class Fragment {
    public final FragmentActivity getActivity() { return null; }
    public Context getContext() { return null; }
    public final Resources getResources() { return null; }
    public void startActivity(Intent intent) {}
    public void startActivityForResult(Intent intent, int requestCode) {}
    public void onCreate(Bundle savedInstanceState) {}
    public void onStart() {}
    public void onResume() {}
    public void onStop() {}
}
