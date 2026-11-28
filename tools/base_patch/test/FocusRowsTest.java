// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package com.android.tv.settings;

import android.view.View;
import android.view.ViewGroup;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * The remote on a TvSettings list (tools/base_patch/tvsettings_focus): the rows of a special app access page bound by
 * the adapter of SettingsPreferenceFragment (test/focus_extract.py), then the keys a user presses. The leanback grid
 * gives the page's focus to the row at its focus position (the first row) and DOWN goes to the next row that can take
 * focus; when the first row cannot, focus falls to the panel's scroll view, which does nothing with DOWN or OK.
 * Prints one line per page; run.sh compares the LineageOS 21 class and the fix with expected_focus_*.txt.
 */
public final class FocusRowsTest {
    static final class Row {
        final Preference pref;
        final ViewGroup itemView = new ViewGroup();
        final View title = new View(), widget = new View();

        Row(Preference pref) {
            this.pref = pref;
            itemView.addView(title);
            itemView.addView(widget);
        }
    }

    static Preference sw(String title, boolean on, boolean enabled) {
        Preference p = new Preference(title, true, on);
        p.setEnabled(enabled);
        return p;
    }

    static Preference plain(String title, boolean enabled, boolean selectable) {
        Preference p = new Preference(title, false, false);
        p.setEnabled(enabled);
        p.setSelectable(selectable);
        return p;
    }

    /** binds the page, presses the keys, prints what the user sees */
    static String page(String name, String keys, Preference... prefs) {
        PreferenceGroup screen = new PreferenceGroup();
        List<Row> rows = new ArrayList<>();
        for (Preference p : prefs) {
            screen.addPreference(p);
            rows.add(new Row(p));
        }
        FocusAdapter adapter = new FocusAdapter(screen);
        for (int i = 0; i < rows.size(); i++) adapter.onBindViewHolder(new PreferenceViewHolder(rows.get(i).itemView), i);
        int focus = rows.get(0).itemView.canTakeFocus() ? 0 : -1;
        StringBuilder out = new StringBuilder(name).append(": focus ").append(at(rows, focus));
        for (String k : keys.split(" ")) {
            if (k.isEmpty()) continue;
            if (k.equals("DOWN") && focus >= 0) {
                for (int j = focus + 1; j < rows.size(); j++) {
                    if (rows.get(j).itemView.canTakeFocus()) {
                        focus = j;
                        break;
                    }
                }
            } else if (k.equals("OK") && focus >= 0) {
                rows.get(focus).itemView.pressOk();
            }
            out.append(" | ").append(k).append(" focus ").append(at(rows, focus));
        }
        out.append(" ||");
        for (Row r : rows) {
            out.append(' ').append(r.pref.getTitle()).append('=').append(r.pref.isChecked() ? "on" : "off")
                    .append(r.itemView.isEnabled() ? "" : ",row-disabled")
                    .append(r.title.isEnabled() ? "" : ",grey")
                    .append(r.widget.isEnabled() ? "" : ",switch-grey");
        }
        return out.toString();
    }

    static String at(List<Row> rows, int i) {
        return i < 0 ? "nowhere" : rows.get(i).pref.getTitle();
    }

    public static void main(String[] args) {
        // S1: the TITAN tester's page: the ATV default listener first, on, "Required by system"
        System.out.println(page("S1 notification access, default listener first", "DOWN OK",
                sw("Android TV Core Services", true, false), sw("Test App", false, true)));
        System.out.println(page("S2 OK on the required row does nothing", "OK DOWN OK",
                sw("Android TV Core Services", true, false), sw("Test App", false, true)));
        // S3: Install unknown apps, the first app cannot change (setEnabled(canChange))
        System.out.println(page("S3 install unknown apps, first app cannot change", "DOWN OK",
                sw("Files", false, false), sw("Test App", false, true)));
        // S4: a list with no app: the "No apps" row only
        System.out.println(page("S4 empty list", "DOWN OK", plain("No apps", false, true)));
        // S5: every row enabled: as before
        System.out.println(page("S5 all rows enabled", "OK DOWN OK",
                sw("Test App", false, true), sw("Demo", false, true)));
        // S6: a disabled row in the middle is no longer skipped, and DOWN goes on past it
        System.out.println(page("S6 disabled row in the middle", "DOWN DOWN OK",
                sw("Test App", false, true), sw("Android TV Core Services", true, false), sw("Demo", false, true)));
        // S7: a row that is not selectable (a header, a text line) stays as it was
        System.out.println(page("S7 not selectable rows", "DOWN",
                sw("Test App", false, true), plain("Header", false, false), plain("Text", true, false)));
    }
}
