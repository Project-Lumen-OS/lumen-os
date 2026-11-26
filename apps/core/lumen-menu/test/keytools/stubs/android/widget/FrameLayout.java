// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public FrameLayout(Context c) { super(c); }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int gravity, leftMargin, topMargin, rightMargin, bottomMargin;

        public LayoutParams(int w, int h) { super(w, h); }

        public LayoutParams(int w, int h, int gravity) { super(w, h); this.gravity = gravity; }
    }
}
