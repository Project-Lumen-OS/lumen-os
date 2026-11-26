// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package com.android.tv.settings.about;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Lumen rows of the patched About page (tools/base_patch/tvsettings_about) on the JVM: which provider may give
 * them, what each row becomes, where the rows go. Host stand-ins of android.* in test/about_stubs.
 */
public final class AboutRowsTest {
    private static final String PKG = "org.lumen.projector";
    private static final String SCREEN = "org.lumen.about.XgimiSystemActivity";
    private static int failed;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failed++;
    }

    /** a projector whose provider org.lumen.about is given by pkg (null: none) with these flags and answer */
    static final class Unit extends Context {
        final String pkg;
        final int flags;
        final Bundle answer;
        final RuntimeException fails;
        int calls;
        String askedAuthority, askedMethod;

        Unit(String pkg, int flags, Bundle answer, RuntimeException fails) {
            this.pkg = pkg;
            this.flags = flags;
            this.answer = answer;
            this.fails = fails;
        }

        @Override
        public PackageManager getPackageManager() {
            return new PackageManager() {
                @Override
                public ProviderInfo resolveContentProvider(String authority, int f) {
                    if (pkg == null || !"org.lumen.about".equals(authority)) return null;
                    ProviderInfo pi = new ProviderInfo();
                    pi.packageName = pkg;
                    pi.applicationInfo = new ApplicationInfo();
                    pi.applicationInfo.flags = flags;
                    return pi;
                }
            };
        }

        @Override
        public ContentResolver getContentResolver() {
            return new ContentResolver() {
                @Override
                public Bundle call(String authority, String method, String arg, Bundle extras) {
                    calls++;
                    askedAuthority = authority;
                    askedMethod = method;
                    if (fails != null) throw fails;
                    return answer;
                }
            };
        }
    }

    static Bundle row(String key, String title, String summary, String screen) {
        Bundle b = new Bundle();
        b.putString("key", key);
        b.putString("title", title);
        b.putString("summary", summary);
        if (screen != null) b.putString("screen", screen);
        return b;
    }

    static Bundle answer(Bundle... rows) {
        Bundle a = new Bundle();
        a.putParcelableArrayList("rows", new ArrayList<>(Arrays.asList(rows)));
        return a;
    }

    static Bundle three() {
        return answer(row("lumen_model_code", "Model code", "G0082", null),
                row("lumen_xgimi_firmware", "XGIMI firmware", "V6.15.62", null),
                row("lumen_back_to_xgimi", "Back to the XGIMI system", "USB stick file: XGIMI_HORSEPOPULAR_XR03D.bin", SCREEN));
    }

    static String text(List<Preference> l) {
        StringBuilder b = new StringBuilder();
        for (Preference p : l) {
            b.append(p.getKey()).append('|').append(p.getTitle()).append('|').append(p.getSummary()).append('|')
                    .append(p.isPersistent()).append('|')
                    .append(p.getIntent() == null ? "-" : p.getIntent().pkg + "/" + p.getIntent().cls).append('\n');
        }
        return b.toString();
    }

    static Preference pref(Context c, String key, int order) {
        Preference p = new Preference(c);
        p.setKey(key);
        p.setOrder(order);
        return p;
    }

    public static void main(String[] a) {
        Unit u = new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, three(), null);
        List<Preference> rows = AboutFragment.LumenRows.of(u);
        check(u.calls == 1 && "org.lumen.about".equals(u.askedAuthority) && "rows".equals(u.askedMethod),
                "the page asks org.lumen.about for its rows once");
        check(text(rows).equals("lumen_model_code|Model code|G0082|false|-\n"
                        + "lumen_xgimi_firmware|XGIMI firmware|V6.15.62|false|-\n"
                        + "lumen_back_to_xgimi|Back to the XGIMI system|USB stick file: XGIMI_HORSEPOPULAR_XR03D.bin|false|"
                        + PKG + "/" + SCREEN + "\n"),
                "three plain rows, not saved, only the last opens the screen of the provider's own app");
        check(rows.get(0).getContext() == u, "the rows live in the page's context");

        u = new Unit(null, 0, three(), null);
        check(AboutFragment.LumenRows.of(u).isEmpty() && u.calls == 0, "no provider: no rows, nothing asked");
        u = new Unit("com.evil.app", 0, three(), null);
        check(AboutFragment.LumenRows.of(u).isEmpty() && u.calls == 0, "a provider of an app the user installed is never asked");
        u = new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, null, new SecurityException("denied"));
        check(AboutFragment.LumenRows.of(u).isEmpty() && u.calls == 1, "a provider that fails: no rows, the page stays");
        u = new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, null, new IllegalArgumentException("Unknown authority"));
        check(AboutFragment.LumenRows.of(u).isEmpty(), "an authority that went away: no rows");
        check(AboutFragment.LumenRows.of(new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, null, null)).isEmpty(), "no answer: no rows");
        check(AboutFragment.LumenRows.of(new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, new Bundle(), null)).isEmpty(),
                "an answer without rows: no rows");
        Bundle odd = answer(row("device_model", "Model", "spoofed", null), row("lumen_x", "", "empty title", null),
                row(null, "No key", "x", null), row("lumen_ok", "Kept", null, ""));
        check(text(AboutFragment.LumenRows.of(new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, odd, null)))
                        .equals("lumen_ok|Kept|null|false|-\n"),
                "only lumen_ rows with a title, an empty screen opens nothing");

        Context c = new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, null, null);
        PreferenceScreen screen = new PreferenceScreen(c);
        String[] keys = {"tutorials", "system_update_settings", "device_name", "device_model", "firmware_version",
                "build_number"};
        for (int i = 0; i < keys.length; i++) screen.addPreference(pref(c, keys[i], i == 0 ? 0 : i + 1));
        Preference model = screen.getPreference(3);
        List<Preference> mine = AboutFragment.LumenRows.of(new Unit(PKG, ApplicationInfo.FLAG_SYSTEM, three(), null));
        AboutFragment.LumenRows.insert(screen, model, mine);
        List<Preference> sorted = new ArrayList<>();
        for (int i = 0; i < screen.getPreferenceCount(); i++) sorted.add(screen.getPreference(i));
        sorted.sort((x, y) -> Integer.compare(x.getOrder(), y.getOrder()));
        StringBuilder order = new StringBuilder();
        for (Preference p : sorted) order.append(p.getKey()).append(' ');
        check(order.toString().equals("tutorials system_update_settings device_name device_model lumen_model_code "
                        + "lumen_xgimi_firmware lumen_back_to_xgimi firmware_version build_number "),
                "the rows come right after Model, the rest keeps its order after them");
        check(model.getOrder() == 4 && mine.get(0).getOrder() == 5 && mine.get(2).getOrder() == 7
                        && screen.getPreference(4).getOrder() == 8 && screen.getPreference(0).getOrder() == 0,
                "orders: Model and what is above it unchanged, the rows next, the rest moved by three");
        int before = screen.getPreferenceCount();
        AboutFragment.LumenRows.insert(screen, null, mine);
        AboutFragment.LumenRows.insert(screen, model, new ArrayList<>());
        check(screen.getPreferenceCount() == before, "no Model row or no rows: the page is left as it is");
        System.out.println("AboutRowsTest: " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
