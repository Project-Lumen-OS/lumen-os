// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.CustomMode;
import org.lumen.menu.CustomModes;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuState;
import org.lumen.menu.NameCheck;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.menu.ResultKind;
import org.lumen.projector.R;
import org.lumen.ui.TvFocus;

import java.util.function.Consumer;

/**
 * User picture modes, the stock flow of every model whose stock has them (newsettings PictureViewModel /
 * CustomizeDataManager): the names live in the stock Settings.System list, the vendor keeps each mode's values under
 * the id it gave the mode. The menu lists them after the stock modes with a "Custom" entry while fewer than ten exist;
 * that entry asks for a name, makes the mode from the one in use and opens its picture settings. "Based on" and
 * "Delete" show only while a user mode is in use. The device adapter sends the vendor calls.
 */
final class CustomPictures {
    private static final String TAG = "LumenCustomPicture";

    interface Host {
        boolean ready();
        void run(FeatureId f, java.util.function.Consumer<ProjectorFeatures.Result> cb);
        void confirm(CharSequence title, CharSequence msg, CharSequence ok, Runnable onOk);
        void toast(int res);
        /** The page of the picture settings of the mode in use (stock openImageMode4SettingInner). */
        void openSettings();
        /** Leaves the page on screen (stock: BACK after a delete). */
        void back();
        void refresh();
        MenuState state();
    }

    private final Context app;
    private final Host host;

    CustomPictures(Context app, Host host) {
        this.app = app;
        this.host = host;
    }

    static boolean on(MenuProfile p) { return p.has(FeatureId.PICTURE_CUSTOM_NEW); }

    static CustomModes load(Context c) {
        try {
            return CustomModes.parse(Settings.System.getString(c.getContentResolver(), CustomModes.SETTING));
        } catch (RuntimeException e) {
            Log.w(TAG, "read " + e);
            return CustomModes.NONE;
        }
    }

    /** Writes the list and reads it back; false when the setting did not take it. */
    static boolean save(Context c, CustomModes m) {
        try {
            Settings.System.putString(c.getContentResolver(), CustomModes.SETTING, m.json());
            return m.equals(load(c));
        } catch (RuntimeException e) {
            Log.w(TAG, "write " + e);
            return false;
        }
    }

    /**
     * {@code st} with the user modes as picture.mode options after the stock ones (their names as labels), and the
     * user mode rows shown only while one of them is in use (stock isCustomizeImageMode).
     */
    static MenuState merge(Context c, MenuProfile p, MenuState st) {
        if (st == null || !on(p)) return st;
        return st.with(load(c).rows(p.options(FeatureId.PICTURE_MODE), st));
    }

    /** Who waits for the name typed on the shared edit screen (one at a time, the screen is modal). */
    private static Consumer<String> naming;
    /** The user modes when the name was asked (the names a new one may not take). */
    private static CustomModes namingList = CustomModes.NONE;

    /**
     * The name field of the shared edit screen (TextFields.PICTURE_MODE_NAME): the stock PopWindowDialog type 254 title
     * picture_name_state_text, its ChinaInputFilter(24) with the input_max_tip_msg toast, the stock checks on OK.
     */
    static TextField nameField() {
        return new TextField() {
            @Override public int title() { return R.string.lumen_m_custom_name; }
            @Override public int hint() { return 0; }
            @Override public int inputType() { return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; }
            @Override public String value(Context c) { return ""; }
            @Override public int problem(String text) {
                NameCheck n = namingList.check(text);
                if (n == NameCheck.EMPTY) return R.string.lumen_m_custom_name_empty;
                if (!CustomModes.fits(text)) return R.string.lumen_m_custom_name_max;
                return n == NameCheck.TAKEN ? R.string.lumen_m_custom_name_taken : 0;
            }
            @Override public boolean save(Context c, String text) {
                Consumer<String> to = naming;
                naming = null;
                if (to == null) return false;
                new Handler(Looper.getMainLooper()).post(() -> to.accept(text));
                return true;
            }
            @Override public int saved() { return 0; }
            @Override public int failed() { return R.string.lumen_m_toast_failed; }
            @Override public InputFilter[] filters(Context c) {
                return new InputFilter[] {(src, start, end, dest, ds, de) -> {
                    String next = dest.subSequence(0, ds) + src.subSequence(start, end).toString() + dest.subSequence(de, dest.length());
                    if (CustomModes.fits(next)) return null;
                    TvFocus.toast(c, R.string.lumen_m_custom_name_max, false);
                    return "";
                }};
            }
        };
    }

    /** The mode list's "Custom" entry: the name, then the new mode, then its picture settings. */
    void create() {
        if (load(app).full()) { host.refresh(); return; }
        namingList = load(app);
        naming = this::make;
        if (!TextEditActivity.open(app, TextFields.PICTURE_MODE_NAME)) naming = null;
    }

    private void make(String name) {
        if (!host.ready()) { host.toast(R.string.lumen_m_toast_not_ready); return; }
        host.run(FeatureId.PICTURE_CUSTOM_NEW, r -> {
            Integer id = r == null || r.after == null ? null : CustomModes.value(r.after.get(FeatureId.PICTURE_MODE));
            // stock adds the name as soon as the vendor gave the id, before the mode is selected
            if (id != null && !save(app, load(app).plus(new CustomMode(name, id)))) Log.w(TAG, "name of " + id + " not stored");
            Log.i(TAG, "new mode " + name + " -> " + id + " " + (r == null ? null : r.kind));
            if (r == null || r.kind != ResultKind.DONE || id == null) {
                host.toast(R.string.lumen_m_toast_failed);
                host.refresh();
                return;
            }
            host.refresh();
            host.openSettings();
        });
    }

    /** "Delete" of the user mode in use: the stock question, the vendor delete, the name, then back. */
    void delete() {
        MenuState st = host.state();
        CustomMode m = st == null ? null : load(app).of(st.get(FeatureId.PICTURE_MODE));
        if (m == null) { host.refresh(); return; }
        host.confirm(app.getString(R.string.lumen_m_custom_delete_ask), null, app.getString(R.string.lumen_m_custom_confirm),
                () -> host.run(FeatureId.PICTURE_CUSTOM_DELETE, r -> {
                    boolean done = r != null && r.kind == ResultKind.DONE;
                    Log.i(TAG, "delete " + m + " " + (r == null ? null : r.kind));
                    if (!done) {
                        host.toast(R.string.lumen_m_toast_failed);
                        host.refresh();
                        return;
                    }
                    if (!save(app, load(app).minus(m.value))) host.toast(R.string.lumen_m_toast_failed);
                    host.refresh();
                    host.back();
                }));
    }
}
