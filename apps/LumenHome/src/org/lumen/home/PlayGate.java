// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Google Play on Lumen OS with Google services but no Google account. Play's TV build (Tubesky) then opens
 * UnauthenticatedMainActivity, which Play blocks on a device that is not Play Protect certified (Lumen OS is not,
 * GMS shows "This device isn't Play Protect certified"), and the TV build has no block screen: Play crashes at once
 * ("GMS compliance not supported on this form factor"). With an account Play opens its TV main screen, which is not
 * blocked. So while no Google account is on the projector, Home opens the system Google sign-in (TvSettings
 * AddAccountWithTypeActivity, account type com.google) instead of Play.
 *
 * Without GET_ACCOUNTS_PRIVILEGED (signature|privileged, Home is platform-signed) Home cannot see the accounts
 * and opens Play as before. Plain rules host-tested: test/play/run.sh.
 */
public final class PlayGate {
    static final String PLAY = "com.android.vending";
    /** The account types Play signs in with (its own list). */
    static final String[] PLAY_ACCOUNT_TYPES = {"com.google", "com.google.work"};
    static final String SIGN_IN_TYPE = "com.google";
    /** AccountManager.KEY_ACCOUNT_TYPE: TvSettings starts the sign-in of exactly this type, no picker. */
    static final String EXTRA_TYPE = "accountType";
    static final String PERMISSION = "android.permission.GET_ACCOUNTS_PRIVILEGED";
    /** App.TAG (not referenced: the host test compiles this class alone). */
    private static final String TAG = "Z9xHome";

    private PlayGate() {}

    /** The sign-in instead of the launch: Play, accounts readable, none of Play's types, the sign-in screen there. */
    static boolean signInFirst(String pkg, boolean canRead, int accounts, boolean signInThere) {
        return PLAY.equals(pkg) && canRead && accounts == 0 && signInThere;
    }

    static Intent signInIntent() {
        return new Intent(Settings.ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_TYPE, SIGN_IN_TYPE)
                .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] {SIGN_IN_TYPE})
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** The Google sign-in to start instead of {@code launch}, or null to start {@code launch} as it is. */
    public static Intent signInInstead(Context c, Intent launch) {
        try {
            String pkg = launch.getComponent() != null ? launch.getComponent().getPackageName() : launch.getPackage();
            if (!PLAY.equals(pkg)) return null;
            boolean canRead = c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
            int n = 0;
            if (canRead) {
                AccountManager am = AccountManager.get(c);
                for (String t : PLAY_ACCOUNT_TYPES) n += am.getAccountsByType(t).length;
            }
            Intent s = signInIntent();
            boolean there = c.getPackageManager().resolveActivity(s, PackageManager.MATCH_DEFAULT_ONLY) != null;
            return signInFirst(pkg, canRead, n, there) ? s : null;
        } catch (Throwable t) {
            Log.w(TAG, "play gate: " + t);
            return null;
        }
    }
}
