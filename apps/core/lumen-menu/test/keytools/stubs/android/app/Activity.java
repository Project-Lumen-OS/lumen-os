package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

/** Host stub (test/keytools): the lifecycle calls the test drives, the content view as the window root. */
public class Activity extends Context {
    private boolean finishing, destroyed;
    private View content;

    protected void onCreate(Bundle b) { }

    protected void onStart() { }

    protected void onResume() { }

    protected void onPause() { }

    protected void onStop() { }

    public void performCreate() { onCreate(null); }

    public void performStart() { onStart(); }

    public void performResume() { onResume(); }

    public void performPause() { onPause(); }

    public void performStop() { onStop(); }

    public Window getWindow() { return new Window(); }

    public void setContentView(View v) { content = v; }

    public View contentView() { return content; }

    public void finish() { finishing = true; }

    public boolean isFinishing() { return finishing; }

    public boolean isDestroyed() { return destroyed; }
}
