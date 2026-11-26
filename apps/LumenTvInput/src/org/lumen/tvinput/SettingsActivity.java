// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * Copyright (C) 2026 Z9X project.
 */
package org.lumen.tvinput;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * The old address of the HDMI settings (org.lumen.tvinput/.SettingsActivity, kept for anything that still opens it):
 * the options live on the HDMI page of the projector menu (All settings, Input source, lumen-menu HdmiPage) since
 * 2026-11-24, so this only asks the projector app to show that page and closes. No screen of its own.
 */
public class SettingsActivity extends Activity {
    private static final String TAG = "Z9xHdmiSettings";
    /** The projector apps' menu request (Lumen Home's tiles send the same), with the menu place as "section". */
    static final String SHOW_PANEL = "org.lumen.projector.action.SHOW_PANEL";
    static final String PAGE = "settings.hdmi";
    private static final String[] PROJECTOR_APPS = {"org.lumen.projector", "org.lumen.x50.projector"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Safe.run(TAG, "redirect", () -> openMenuPage(this));
        finish();
    }

    /** Opens the menu's HDMI page in whichever projector app this image has. */
    static void openMenuPage(Context c) {
        for (String pkg : PROJECTOR_APPS) {
            c.sendBroadcast(new Intent(SHOW_PANEL).setPackage(pkg).putExtra("section", PAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
    }
}
