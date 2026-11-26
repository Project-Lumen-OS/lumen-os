// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.lumen.projector.R;

import java.util.ArrayList;

/**
 * The rows of the system Settings About page (authority org.lumen.about): TvSettings' About page (the base_patch fix
 * tvsettings_about) calls method "rows" and adds what it gets right after Model. Each row is a Bundle: key, title,
 * summary, and for the row that opens Back to the XGIMI system the activity class in this app ("screen"). Read only:
 * no other method, no query, no write.
 */
public final class AboutProvider extends ContentProvider {
    public static final String AUTHORITY = "org.lumen.about", METHOD_ROWS = "rows";
    public static final String ROWS = "rows", KEY = "key", TITLE = "title", SUMMARY = "summary", SCREEN = "screen";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_ROWS.equals(method)) return null;
        ArrayList<Bundle> out = new ArrayList<>();
        for (AboutRow r : AboutRows.of(UnitFacts.read(), t -> getContext().getString(text(t)))) {
            Bundle b = new Bundle();
            b.putString(KEY, r.key);
            b.putString(TITLE, r.title);
            b.putString(SUMMARY, r.summary);
            if (r.opensScreen) b.putString(SCREEN, XgimiSystemActivity.class.getName());
            out.add(b);
        }
        Bundle answer = new Bundle();
        answer.putParcelableArrayList(ROWS, out);
        return answer;
    }

    static int text(RowText t) {
        switch (t) {
            case MODEL_CODE: return R.string.lumen_about_model_code;
            case FIRMWARE: return R.string.lumen_about_firmware;
            case FIRMWARE_DATED: return R.string.lumen_about_firmware_dated;
            case ANDROID: return R.string.lumen_about_android;
            case ANDROID_PATCH: return R.string.lumen_about_android_patch;
            case KERNEL: return R.string.lumen_about_kernel;
            case BOOT_LOADER: return R.string.lumen_about_boot_loader;
            case GMUI: return R.string.lumen_about_gmui;
            case PLATFORM: return R.string.lumen_about_platform;
            case DLP: return R.string.lumen_about_dlp;
            case FPGA: return R.string.lumen_about_fpga;
            case REMOTE: return R.string.lumen_about_remote;
            case WIFI: return R.string.lumen_about_wifi;
            case PART_FIRMWARE: return R.string.lumen_about_part_firmware;
            case BACK: return R.string.lumen_about_back;
            case BACK_SUMMARY_FILE: return R.string.lumen_about_back_summary_file;
            case BACK_SUMMARY: return R.string.lumen_about_back_summary;
            default: throw new IllegalArgumentException(t.name());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
