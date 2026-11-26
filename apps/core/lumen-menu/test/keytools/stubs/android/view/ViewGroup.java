// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.view;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ViewGroup extends View implements ViewParent {
    public final List<View> children = new ArrayList<>();

    public ViewGroup(Context c) { super(c); }

    public void addView(View v, LayoutParams p) {
        v.setLayoutParams(p);
        v.parent = this;
        children.add(v);
        if (isAttachedToWindow()) v.dispatchAttached(true);
    }

    public void removeView(View v) {
        if (children.remove(v)) {
            if (isAttachedToWindow()) v.dispatchAttached(false);
            v.parent = null;
        }
    }

    @Override public void dispatchWindowFocusChanged(boolean has) {
        super.dispatchWindowFocusChanged(has);
        for (View c : new ArrayList<>(children)) c.dispatchWindowFocusChanged(has);
    }

    @Override public void dispatchAttached(boolean on) {
        super.dispatchAttached(on);
        for (View c : new ArrayList<>(children)) c.dispatchAttached(on);
    }

    /** A plain group hands a key to its first child (the panel content of a host root). */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        for (View c : children) if (c.dispatchKeyEvent(e)) return true;
        return false;
    }

    public void setClipChildren(boolean b) { }

    public void setClipToPadding(boolean b) { }

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;

        public LayoutParams(int w, int h) { width = w; height = h; }
    }
}
