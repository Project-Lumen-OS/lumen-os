// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.open;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.lumen.files.net.NetFilesProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Where a file opens: videos and music in Lumen Play when it is installed, else in the built-in player; photos in
 * the Lumen viewer; anything else in the app the user picks. Files hand out read-only content addresses (local and
 * SMB) or the server's own http address (DLNA).
 */
public final class Opener {
    public static final String LUMEN_PLAY = "com.xgimiru.player";
    private static final String TAG = "LumenFiles";

    private Opener() {}

    /** An address the built-in player and viewer may read. */
    public static boolean readable(Uri u) {
        if (u == null) return false;
        String s = u.getScheme();
        return NetFilesProvider.isOurs(u) || "http".equals(s) || "https".equals(s);
    }

    /** The file name of an address Files handed out (shown by the player and the viewer). */
    public static String name(Uri u) {
        if (NetFilesProvider.isOurs(u)) return NetFilesProvider.name(u);
        String s = u.getLastPathSegment();
        return s == null ? "" : s;
    }

    public static InputStream open(Context c, Uri u) throws IOException {
        String s = u.getScheme();
        if ("http".equals(s) || "https".equals(s)) {
            HttpURLConnection h = (HttpURLConnection) new URL(u.toString()).openConnection();
            h.setConnectTimeout(5000);
            h.setReadTimeout(20000);
            return h.getInputStream();
        }
        InputStream in = c.getContentResolver().openInputStream(u);
        if (in == null) throw new IOException("cannot open " + u);
        return in;
    }

    public static boolean hasLumenPlay(Context c) {
        try {
            c.getPackageManager().getPackageInfo(LUMEN_PLAY, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** A video or a song; {@code local}: its file when it is on a drive (the player then finds the next song). */
    public static boolean play(Activity a, Uri u, String mime, File local) {
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(LUMEN_PLAY);
        if (a.getPackageManager().resolveActivity(v, 0) != null && start(a, v)) return true;
        Intent p = new Intent(a, PlayerActivity.class);
        if (local != null) p.putExtra(PlayerActivity.EXTRA_PATH, local.getAbsolutePath());
        else p.setData(u);
        return start(a, p);
    }

    /** A photo and the other photos of its folder (local) or of its list (network). */
    public static boolean photo(Activity a, File local, Uri u, ArrayList<Uri> all) {
        Intent p = new Intent(a, PhotoActivity.class);
        if (local != null) p.putExtra(PhotoActivity.EXTRA_PATH, local.getAbsolutePath());
        else p.setData(u).putParcelableArrayListExtra(PhotoActivity.EXTRA_URIS, all);
        return start(a, p);
    }

    /** Any app that opens the type (Android asks which one). */
    public static boolean with(Activity a, Uri u, String mime) {
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return start(a, Intent.createChooser(v, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    static boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.i(TAG, "open: " + e);
            return false;
        }
    }
}
