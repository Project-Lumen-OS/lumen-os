// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Starts AliveWatch at every start of the projector app's process: Android creates the providers of a process
 * before anything else runs in it, also after the process died and came back in the same start, when no
 * BOOT_COMPLETED follows. Not exported, not direct boot aware (the files of the watch need the unlocked user). It
 * serves no data.
 */
public final class AliveStart extends ContentProvider {
    @Override
    public boolean onCreate() {
        AliveWatch.start(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
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
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
