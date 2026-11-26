// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.voice;

import android.content.Context;

import org.lumen.projector.Family;
import org.lumen.projector.panel.QuickPanel;

/** The projector side of the voice assistant (voice/Assistant, shared): focus, keystone, quick panel pages. */
public final class AssistantHost implements Assistant.Host {
    @Override public void autofocus(Context c) { org.lumen.projector.Hal.requestAutofocus(c); }
    @Override public void keystone(Context c) { Family.assistKeystone(c); }
    @Override public void openPanel(Context c, String section) {
        if (section == null) QuickPanel.toggle(c); else QuickPanel.show(c, section);
    }
    @Override public boolean lowPower() { return Family.ASSIST_LOW_POWER; }
}
