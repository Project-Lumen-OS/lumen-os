package androidx.preference;

import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class PreferenceGroupAdapter extends RecyclerView.Adapter<PreferenceViewHolder> {
    public PreferenceGroupAdapter(PreferenceGroup preferenceGroup) {}

    public Preference getItem(int position) { return null; }

    @Override
    public PreferenceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) { return null; }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder, int position) {}
}
