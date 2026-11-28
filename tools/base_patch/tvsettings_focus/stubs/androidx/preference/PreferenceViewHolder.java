package androidx.preference;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class PreferenceViewHolder extends RecyclerView.ViewHolder {
    PreferenceViewHolder(View itemView) { super(itemView); }
}
