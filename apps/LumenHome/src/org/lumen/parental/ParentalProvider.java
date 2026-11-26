// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * The parental controls of Lumen Home for the projector menu's Parental controls page (lumen-menu ParentalPage), the
 * same on every model: content://org.lumen.parental, permission org.lumen.parental.permission.SETTINGS (signature).
 * Methods: "state" (the state without the password, {@link ParentalSettings#shown}, and "has_pin"), "set" (arg a
 * {@link ParentalSettings} key, extra "value"), "check_pin" and "set_pin" (arg the arrow keys, PinCode letters;
 * set_pin also turns the lock on). Every answer carries "ok".
 */
public final class ParentalProvider extends ContentProvider {

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        boolean ok = false;
        synchronized (ParentalProvider.class) {
            ParentalState s = Parental.load(getContext());
            switch (method == null ? "" : method) {
                case ParentalClient.STATE:
                    out.putString(ParentalClient.STATE, ParentalSettings.shown(s));
                    out.putBoolean(ParentalClient.HAS_PIN, s.hasPin());
                    ok = true;
                    break;
                case ParentalClient.SET:
                    ok = ParentalSettings.apply(s, arg, extras == null ? null : extras.getString(ParentalClient.VALUE));
                    if (ok) saved(s);
                    break;
                case ParentalClient.CHECK_PIN:
                    ok = arg != null && s.pin(arg);
                    break;
                case ParentalClient.SET_PIN:
                    if (PinCode.complete(arg)) {
                        s.setPin(arg);
                        s.lockOn = true;
                        saved(s);
                        ok = true;
                    }
                    break;
                default:
                    break;
            }
        }
        out.putBoolean(ParentalClient.OK, ok);
        return out;
    }

    private void saved(ParentalState s) {
        Parental.save(getContext(), s);
        if (!ParentalRules.controlled(s)) return;
        try {
            UsageService.start(getContext());
        } catch (RuntimeException e) {
            Log.w(Parental.TAG, "time watch not started now (Home starts it when it is in front): " + e);
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }

    @Override public String getType(Uri u) { return null; }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }

    @Override public int delete(Uri u, String s, String[] a) { return 0; }

    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
