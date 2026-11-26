// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.lumen.files.net.NetServers;

/**
 * Once per install: the SMB servers the user saved in Home's old Files panel move here. Home hands them over through
 * its provider (it alone can read its keystore key, it checks that the caller is this app signed with its key) and
 * forgets them; Files stores them again with its own keystore key.
 */
public final class ServersMove {
    public static final String AUTHORITY = "org.lumen.home.netservers";
    public static final String METHOD = "take";
    public static final String[] KEYS = {"id", "name", "host", "login", "password", "share"};
    private static final String PREFS = "files", DONE = "servers_moved", TAG = "LumenFiles";

    private ServersMove() {}

    /** Worker thread. */
    public static void once(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(DONE, false)) return;
        int n = 0;
        try {
            Bundle b = c.getContentResolver().call(Uri.parse("content://" + AUTHORITY), METHOD, null, null);
            String[][] v = new String[KEYS.length][];
            for (int i = 0; b != null && i < KEYS.length; i++) v[i] = b.getStringArray(KEYS[i]);
            int[] ports = b == null ? null : b.getIntArray("port");
            for (int i = 0; ports != null && v[0] != null && i < v[0].length; i++) {
                NetServers.Server s = NetServers.create();
                s.id = v[0][i];
                s.name = v[1][i];
                s.host = v[2][i];
                s.login = v[3][i];
                s.password = v[4][i];
                s.share = v[5][i];
                s.port = ports[i];
                if (s.id != null && s.host != null && !s.host.isEmpty()) {
                    NetServers.save(c, s);
                    n++;
                }
            }
        } catch (IllegalArgumentException | SecurityException | NullPointerException e) {
            Log.i(TAG, "no servers from Home: " + e);
        }
        Log.i(TAG, "servers moved from Home: " + n);
        p.edit().putBoolean(DONE, true).apply();
    }
}
