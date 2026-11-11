// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.FrameLayout;

/**
 * The window of {@link ActivityPanelHost}: a full-screen, transparent activity whose content is one frame the host
 * puts the menu's panel into. The device app declares it in its manifest with a translucent theme, its own task and
 * no history (X50Projector: {@code org.lumen.projector.ui.PanelActivity}, MenuTheme). It starts on the host's first
 * show, takes the panels between onStart and onStop and is finished by the host when the last panel is gone; it
 * leaves with the menu on HOME, screen off or another app in front.
 */
public final class PanelActivity extends Activity {
    private FrameLayout frame;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (ActivityPanelHost.instance() == null) {
            finish();
            return;
        }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setDecorFitsSystemWindows(false);
        frame = new FrameLayout(this);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        setContentView(frame);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ActivityPanelHost h = ActivityPanelHost.instance();
        if (h == null || frame == null) finish();
        else h.attach(this, frame);
    }

    @Override
    protected void onStop() {
        ActivityPanelHost h = ActivityPanelHost.instance();
        if (h != null) h.detach(this);
        super.onStop();
        if (!isFinishing()) finish();
    }
}
