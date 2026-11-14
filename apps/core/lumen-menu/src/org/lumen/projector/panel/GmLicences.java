package org.lumen.projector.panel;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.projector.R;

/**
 * "Licences" in Projector settings, Help (2026-11-11): the credits that CC BY 4.0 requires for the controller pictures
 * of the game menu (qp_design/models/README.md, CREDITS). The credit lines stay in English as published by the
 * authors; the line above them is translated. A device app without a Projector settings screen (the X50) shows the
 * same dialog from its menu: profile feature settings.licences, org.lumen.projector.ui.LicencesActivity.
 */
public final class GmLicences {
    private GmLicences() {}

    /** The CREDITS block of the models README, word for word (only the models whose frames ship). */
    static final String CREDITS =
            "\"PS5 Controller\" by Taohid Animation, https://sketchfab.com/3d-models/ps5-controller-b7bb9c5102a04cb0b1966c6d02bad7d6, CC BY 4.0. Changed: logos and symbols removed, reduced and converted to a real-time model, rendered to images.\n\n"
            + "\"Xbox One Controller Free\" by paxillop, https://sketchfab.com/3d-models/xbox-one-controller-free-97fb54c001f84a6896b6ce8eb7a1814d, CC BY 4.0. Changed: logo and letters removed, recoloured, reduced and converted to a real-time model, rendered to images.\n\n"
            + "\"Nintendo Switch OLED\" by Wille Evensson, https://sketchfab.com/3d-models/nintendo-switch-oled-aa2c7d815a104accae7709eb39ac2184, CC BY 4.0. Changed: only the controllers, recoloured, markings removed, reduced and converted to a real-time model, rendered to images.\n\n"
            + "Licence: https://creativecommons.org/licenses/by/4.0/. These are independent fan models, not official Sony, Microsoft or Nintendo material, and do not imply endorsement.\n\n"
            + "Inter typeface by Rasmus Andersson, SIL Open Font License 1.1.";

    /** The Licences dialog over {@code a} (Projector settings, Help on the Z9X, HB and Play 6). */
    public static void show(Activity a) {
        open(a);
    }

    /** Shows the Licences dialog over {@code a} and returns it, or null when it could not be shown. */
    public static AlertDialog open(Activity a) {
        try {
            TextView t = new TextView(a);
            int pad = Math.round(24 * a.getResources().getDisplayMetrics().density);
            t.setPadding(pad, pad / 2, pad, pad / 2);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT);
            t.setText(a.getString(R.string.lumen_gm_credits_intro) + "\n\n" + CREDITS);
            t.setFocusable(true);
            ScrollView sv = new ScrollView(a);
            sv.addView(t);
            return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.lumen_gm_licences)
                    .setView(sv)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Throwable e) {
            Log.w(GameMenu.TAG, "licences: " + e);
            return null;
        }
    }
}
