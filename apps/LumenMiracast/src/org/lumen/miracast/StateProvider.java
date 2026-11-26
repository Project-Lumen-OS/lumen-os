// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import org.lumen.miracast.core.Control;
import org.lumen.miracast.core.ReceiverStatus;

/**
 * content://org.lumen.miracast, the screen mirroring switches for other Lumen apps (the projector's quick menu tile).
 * Callers need org.lumen.miracast.permission.CONTROL (signature: only apps signed with the platform key).
 *   call("state")                 -> the state below
 *   call("set_enabled", "1"|"0")  -> on or off, then the state
 *   call("set_ask", "1"|"0")      -> ask before connecting or not, then the state
 *   call("set_quality", "1"|"-1") -> the next (or the one before) picture quality, then the state
 *   call("follow_name")           -> the name follows the system device name again, then the state
 * State keys: enabled, ask (boolean), name (what phones and computers list), status (the status line in the
 * user's language), quality (the picture quality in the user's language), code (ReceiverStatus name), detail (the cause behind STARTING or FAULT in the user's language,
 * empty when there is none), mirroring (boolean). A bad method or value changes nothing and
 * returns null. The casting screen (apps/LumenAirPlay CastActivity) shows and changes these: the sink has no screen
 * of its own.
 */
public final class StateProvider extends ContentProvider {
    static final String PERM_CONTROL = "org.lumen.miracast.permission.CONTROL";
    /** call("diag"): verdict, report and the last finished attempt of the stage diagnostics. */
    static final String DIAG = "diag";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        c.enforceCallingOrSelfPermission(PERM_CONTROL, "screen mirroring control");
        String key = Control.settingOf(method);
        if (key != null) {
            Boolean on = Control.parseSwitch(arg);
            if (on == null) return null;
            Prefs.get(c).edit().putBoolean(Control.ENABLED.equals(key) ? Prefs.ENABLED : Prefs.ASK, on).commit();
            Log.i(MiracastService.TAG, "set " + key + " " + on + " by uid " + Binder.getCallingUid());
            sync(c, method);
        } else if (Control.SET_QUALITY.equals(method) || Control.FOLLOW_NAME.equals(method)) {
            SharedPreferences.Editor e = Prefs.get(c).edit();
            if (Control.SET_QUALITY.equals(method)) e.putString(Prefs.QUALITY, Prefs.quality(c).step("-1".equals(arg) ? -1 : 1).name());
            else e.remove(Prefs.NAME);
            e.commit();
            Log.i(MiracastService.TAG, method + " by uid " + Binder.getCallingUid());
            sync(c, method);
        } else if (DIAG.equals(method)) {
            // the stage report (hidden status page, support): no switch changes
            Bundle d = new Bundle();
            d.putString("verdict", MiracastService.diag().verdict());
            d.putString("report", MiracastService.diag().report());
            d.putString("last", MiracastService.lastReport(c));
            return d;
        } else if (!Control.STATE.equals(method)) {
            return null;
        }
        ReceiverStatus st = StatusText.status(c);
        MiracastService s = MiracastService.instance();
        Bundle b = new Bundle();
        b.putBoolean("enabled", Prefs.enabled(c));
        b.putBoolean("ask", Prefs.ask(c));
        b.putString("name", Prefs.name(c));
        b.putString("code", st.name());
        b.putString("status", StatusText.of(c, st));
        String detail = StatusText.detail(c, st);
        b.putString("detail", detail != null ? detail : "");
        b.putBoolean("mirroring", s != null && s.mirroring());
        b.putString("quality", StatusText.quality(c, Prefs.quality(c)));
        return b;
    }

    /** The receiver takes the change at once, under the sink's own identity. */
    private static void sync(Context c, String method) {
        long id = Binder.clearCallingIdentity();
        try {
            MiracastService.sync(c);
        } catch (RuntimeException e) {
            Log.w(MiracastService.TAG, "sync after " + method + ": " + e);
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri u) {
        return null;
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        return 0;
    }
}
