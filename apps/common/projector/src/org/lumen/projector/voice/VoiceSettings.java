// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import org.lumen.menu.GeneralChoices;
import org.lumen.projector.R;

/**
 * The voice assistant settings of the shared menu (All settings, General, Voice assistant): the speech language as
 * option tokens (lumen-menu GeneralChoices), and the dialog of the optional Gemini key for the voice (Speaker). Main
 * thread.
 */
public final class VoiceSettings {
    private VoiceSettings() {}

    /** The option token of the stored speech language ("system" when unset or unknown). */
    public static String languageToken(Context c) {
        return GeneralChoices.voiceToken(Assistant.prefs(c).getString(Assistant.K_LANG, ""));
    }

    /** The speech language of an option token ("" = the system language). */
    public static String tagOf(String token) {
        return GeneralChoices.voiceTag(token);
    }

    /** The voice key row's value: the device voice, Gemini, or why Gemini does not speak now. */
    public static String keyValue(Context c) {
        String e = Speaker.lastError();
        if (Speaker.key(c).isEmpty()) return c.getString(R.string.voice_key_off);
        if ("region".equals(e)) return c.getString(R.string.voice_key_region);
        if ("quota".equals(e)) return c.getString(R.string.voice_key_quota);
        if ("bad key".equals(e)) return c.getString(R.string.voice_key_bad);
        return c.getString(R.string.voice_key_on);
    }

    /** The key's text field; {@code done} runs when the dialog goes, whatever the answer. */
    public static void editKey(Activity a, Runnable done) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setText(Speaker.key(a));
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(R.string.voice_key).setMessage(R.string.voice_key_hint).setView(e)
                .setPositiveButton(android.R.string.ok, (d, w) -> Speaker.setKey(a, e.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> { if (done != null) done.run(); })
                .show();
    }
}
