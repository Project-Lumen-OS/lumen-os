// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.app.Activity;

import org.lumen.menu.KeyTools;

/**
 * A full-screen Lumen tool that owns the remote while it is in front (manual focus, the X50 corner and lens screens),
 * the one base of every such screen (org.lumen.menu.KeyTools): it comes in front at onResume, before it has the window
 * focus (a menu panel above it would keep the focus), and leaves at onPause. The remote's menu key reaches
 * {@link KeyTools.Tool#onMenuKey}.
 */
public abstract class ToolActivity extends Activity implements KeyTools.Tool {
    @Override
    protected void onResume() {
        super.onResume();
        KeyTools.shown(this);
    }

    @Override
    protected void onPause() {
        KeyTools.gone(this);
        super.onPause();
    }
}
