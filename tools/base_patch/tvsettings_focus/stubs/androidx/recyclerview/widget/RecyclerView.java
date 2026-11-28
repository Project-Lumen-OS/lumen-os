package androidx.recyclerview.widget;

import android.view.View;
import android.view.ViewGroup;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class RecyclerView {
    public abstract static class ViewHolder {
        public final View itemView;

        public ViewHolder(View itemView) { this.itemView = itemView; }
    }

    public abstract static class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);

        public abstract void onBindViewHolder(VH holder, int position);
    }
}
