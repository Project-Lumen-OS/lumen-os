// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.lumen.cec.CecDefaults;
import org.lumen.cec.CecRow;
import org.lumen.cec.CecSetting;
import org.lumen.menu.MenuJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Once per unit, after the update that brought the stock HDMI-CEC defaults (devices/_image/cec_defaults.tsv): a
 * switch the device's reset lines name gets its stock value back unless the user switched its row in the Lumen menu
 * (CecSettings.userChose, recorded since this version). The table is the app asset {@link CecDefaults#ASSET}, the
 * device its menu profile names. A value nobody stored needs nothing: the image's framework RRO has the stock
 * default. Off the main thread (binder calls), at the start of the projector app. Never throws.
 */
public final class CecStockDefaults {
    private static final String TAG = "Z9xCec";
    static final String K_DONE = "cec_stock_defaults";
    /** The menu profile of the app (lumen-menu MenuController.PROFILE_ASSET): its "device" picks the table lines. */
    static final String MENU_ASSET = "lumen_menu/menu.json";

    private CecStockDefaults() {}

    public static void applyOnce(Context c) {
        CecDefaults table = null;
        String device = null;
        try {
            table = CecDefaults.parse(asset(c, CecDefaults.ASSET));
            device = device(c);
            SharedPreferences p = CecSettings.prefs(c);
            if (p.getInt(K_DONE, 0) < CecDefaults.VERSION) {
                List<String> log = new ArrayList<>();
                boolean done = table.apply(device, new FrameworkUnit(c), log);
                for (String l : log) Log.i(TAG, "stock defaults (" + device + "): " + l);
                if (done) p.edit().putInt(K_DONE, CecDefaults.VERSION).commit();
            }
        } catch (Throwable t) {
            Log.w(TAG, "stock defaults: " + t);
        }
        allOnOnce(c, table);
        stockAgainOnce(c, table, device);
    }

    /** The owner's all-ON step (CecDefaults.allOnOnce), once per unit, flag {@value #K_ALL_ON}. Never throws. */
    static void allOnOnce(Context c, CecDefaults table) {
        try {
            List<String> log = new ArrayList<>();
            CecDefaults.allOnOnce(new FrameworkRows(c), table == null ? CecDefaults.ALL_ON : table.allOn(), log);
            for (String l : log) Log.i(TAG, "all on: " + l);
        } catch (Throwable t) {
            Log.w(TAG, "all on: " + t);
        }
    }

    /** An owner value taken back to stock (CecDefaults.stockAgainOnce), once per unit, flag {@value #K_STOCK_AGAIN}. */
    static void stockAgainOnce(Context c, CecDefaults table, String device) {
        if (table == null) return;
        try {
            SharedPreferences p = CecSettings.prefs(c);
            if (p.getBoolean(K_STOCK_AGAIN, false)) return;
            boolean any = false;
            for (CecSetting s : CecSetting.values()) any |= table.stockAgain(s);
            if (!any) return;
            List<String> log = new ArrayList<>();
            boolean done = table.stockAgainOnce(device, new FrameworkUnit(c), log);
            for (String l : log) Log.i(TAG, "stock again: " + l);
            if (done) p.edit().putBoolean(K_STOCK_AGAIN, true).commit();
        } catch (Throwable t) {
            Log.w(TAG, "stock again: " + t);
        }
    }

    static final String K_STOCK_AGAIN = "cec_stock_again_v1_applied";

    static final String K_ALL_ON = "cec_defaults_v1_applied";

    /** The page's switches through their owners (CecSettings: framework, tvinput provider, our prefs). */
    private static final class FrameworkRows implements CecDefaults.Rows {
        private final Context c;
        private Bundle tv;

        FrameworkRows(Context c) { this.c = c; }

        private Bundle tv() {
            if (tv == null) tv = CecSettings.tvinputPrefs(c);
            return tv;
        }

        private static String tvKey(CecRow r) {
            return r == CecRow.SWITCH ? CecSettings.TV_OTP : r == CecRow.REMOTE ? CecSettings.TV_CONTROL : null;
        }

        @Override public boolean absent(CecRow r) {
            String k = tvKey(r);
            return k != null && (tv() == null || !tv().containsKey(k));
        }

        @Override public Boolean on(CecRow r) {
            switch (r) {
                case MASTER: return CecSettings.masterOn(c);
                case WAKE: return CecSettings.wakeOn(c);
                case SEND_STANDBY: return CecSettings.sendStandbyOn(c);
                case SOUNDBAR: return CecSettings.soundbarOn(c);
                case SOUNDBAR_ON: return CecSettings.avrOn(c);
                default: return tv() == null ? null : tv().getBoolean(tvKey(r));
            }
        }

        @Override public boolean set(CecRow r, boolean on) {
            switch (r) {
                case MASTER: return CecSettings.setMaster(c, on);
                case WAKE: return CecSettings.setWake(c, on);
                case SEND_STANDBY: return CecSettings.setSendStandby(c, on);
                case SOUNDBAR: return CecSettings.setSoundbar(c, on);
                case SOUNDBAR_ON: CecSettings.setAvrOn(c, on); return true;
                default: return CecSettings.setTvinputPref(c, tvKey(r), on);
            }
        }

        @Override public boolean done() { return CecSettings.prefs(c).getBoolean(K_ALL_ON, false); }

        @Override public void markDone() { CecSettings.prefs(c).edit().putBoolean(K_ALL_ON, true).commit(); }
    }

    /** The device codename of the app's menu profile (devices/&lt;c&gt;/menu.json "device"). */
    private static String device(Context c) throws IOException {
        Object j = MenuJson.parse(asset(c, MENU_ASSET));
        Object d = j instanceof Map ? ((Map<?, ?>) j).get("device") : null;
        if (!(d instanceof String)) throw new IOException("no device in " + MENU_ASSET);
        return (String) d;
    }

    private static String asset(Context c, String name) throws IOException {
        try (InputStream in = c.getApplicationContext().getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** The two switches through CecSettings (HdmiControlManager), the power policy's held wake through its pref. */
    private static final class FrameworkUnit implements CecDefaults.Unit {
        private final Context c;

        FrameworkUnit(Context c) { this.c = c; }

        @Override public Boolean on(CecSetting s) {
            return s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY ? CecSettings.wakeOn(c) : CecSettings.sendStandbyOn(c);
        }

        @Override public boolean set(CecSetting s, boolean on) {
            return s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY ? CecSettings.setWake(c, on) : CecSettings.setSendStandby(c, on);
        }

        @Override public boolean heldOff(CecSetting s) {
            return s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY && CecSettings.restoreOtpPending(c);
        }

        @Override public void dropHold(CecSetting s) {
            if (s == CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY) CecSettings.setRestoreOtpPending(c, false);
        }

        @Override public boolean userChose(CecRow row) { return CecSettings.userChose(c, row); }
    }
}
