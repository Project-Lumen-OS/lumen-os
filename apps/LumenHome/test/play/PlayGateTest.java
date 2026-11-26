// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home;

/**
 * PlayGate (Lumen Home): Google Play opened while no Google account is on the projector starts the Google sign-in
 * instead, because Play's TV build crashes without an account on a device that is not Play Protect certified.
 * Every other launch, and every case where Home cannot tell, stays as it was. Plain JDK (only the pure rule runs).
 * sh test/play/run.sh
 */
public final class PlayGateTest {
    static int fail, pass;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] a) {
        String play = "com.android.vending";
        check(PlayGate.signInFirst(play, true, 0, true), "Play, no Google account: the sign-in first");
        for (int n : new int[] {1, 2, 5})
            check(!PlayGate.signInFirst(play, true, n, true), n + " Google account(s): Play opens");
        check(!PlayGate.signInFirst(play, false, 0, true), "accounts not readable: Play opens as before");
        check(!PlayGate.signInFirst(play, true, 0, false), "no sign-in screen on the image: Play opens as before");
        for (String p : new String[] {"org.lumen.home", "com.google.android.youtube.tv", "com.google.android.gms",
                "com.android.vending.billing", "com.android.vendin", "", null})
            check(!PlayGate.signInFirst(p, true, 0, true), "'" + p + "': opens as before");

        check(play.equals(PlayGate.PLAY), "Play's package");
        check(PlayGate.PLAY_ACCOUNT_TYPES.length == 2 && "com.google".equals(PlayGate.PLAY_ACCOUNT_TYPES[0])
                && "com.google.work".equals(PlayGate.PLAY_ACCOUNT_TYPES[1]), "Play's own account types");
        check("com.google".equals(PlayGate.SIGN_IN_TYPE), "the sign-in adds a Google account");
        check("accountType".equals(PlayGate.EXTRA_TYPE), "TvSettings' extra for one account type (no picker)");
        check("android.permission.GET_ACCOUNTS_PRIVILEGED".equals(PlayGate.PERMISSION), "the permission that sees every account");

        System.out.println("play gate tests passed=" + pass + " failed=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
