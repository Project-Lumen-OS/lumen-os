package androidx.preference;

import android.content.Context;
import android.content.Intent;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image. Runnable for the host test of the Lumen rows: it keeps what is set.
 */
public class Preference {
    public static final int DEFAULT_ORDER = Integer.MAX_VALUE;
    private final Context context;
    private String key, fragment;
    private CharSequence title, summary;
    private Intent intent;
    private int order = DEFAULT_ORDER;
    private boolean persistent = true, enabled = true, visible = true;

    public Preference(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public CharSequence getTitle() { return title; }
    public void setTitle(CharSequence title) { this.title = title; }
    public CharSequence getSummary() { return summary; }
    public void setSummary(CharSequence summary) { this.summary = summary; }
    public void setSummary(int summaryResId) { this.summary = "@" + summaryResId; }
    public Intent getIntent() { return intent; }
    public void setIntent(Intent intent) { this.intent = intent; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean persistent) { this.persistent = persistent; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setFragment(String fragment) { this.fragment = fragment; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
