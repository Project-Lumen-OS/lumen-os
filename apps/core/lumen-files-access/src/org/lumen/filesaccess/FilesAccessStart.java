// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.filesaccess;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Starts FilesAccess at every start of the projector app's process: Android creates the providers of a process
 * before anything else runs in it, also after the process died and came back in the same start. Not exported,
 * not direct boot aware (package facts and the app's own storage need the unlocked user). It serves no data.
 */
public final class FilesAccessStart extends ContentProvider {
    @Override
    public boolean onCreate() {
        FilesAccess.start(getContext());
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
