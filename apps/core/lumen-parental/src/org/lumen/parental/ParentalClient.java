// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * The projector menu's side of Lumen Home's ParentalProvider (apps/LumenHome, it holds the state) and the contract
 * both sides read. Binder calls: worker thread.
 */
public final class ParentalClient {
    /** The contract of Lumen Home's ParentalProvider: its authority, call() methods and result keys. */
    public static final String AUTHORITY = "org.lumen.parental";
    public static final String STATE = "state", SET = "set", CHECK_PIN = "check_pin", SET_PIN = "set_pin";
    public static final String OK = "ok", VALUE = "value", HAS_PIN = "has_pin";

    private static final Uri URI = Uri.parse("content://" + AUTHORITY);

    private ParentalClient() {}

    /** Lumen Home answers the parental controls on this image. */
    public static boolean present(Context c) {
        return c.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null;
    }

    /** The state without the password, null when Home does not answer. */
    public static ParentalState read(Context c) {
        Bundle b = call(c, STATE, null, null);
        return b == null || !b.getBoolean(OK) ? null : ParentalSettings.read(b.getString(STATE));
    }

    public static boolean set(Context c, String key, String value) {
        Bundle v = new Bundle();
        v.putString(VALUE, value);
        return ok(call(c, SET, key, v));
    }

    public static boolean checkPin(Context c, String keys) {
        return ok(call(c, CHECK_PIN, keys, null));
    }

    /** A new password, the lock on. */
    public static boolean setPin(Context c, String keys) {
        return ok(call(c, SET_PIN, keys, null));
    }

    private static boolean ok(Bundle b) {
        return b != null && b.getBoolean(OK);
    }

    private static Bundle call(Context c, String method, String arg, Bundle extras) {
        try {
            return c.getContentResolver().call(URI, method, arg, extras);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
