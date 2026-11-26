// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.fakewn;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.util.Log;

/**
 * Puts N Continue watching rows of this app into TvProvider's Watch Next (its old rows go first), newest first:
 * am start -W -n org.lumen.home.fakewn/.FakeWnSeed --ei n 3. Each row opens FakeWnPlayer with its film number.
 */
public class FakeWnSeed extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int n = getIntent().getIntExtra("n", 3);
        getContentResolver().delete(TvContract.WatchNextPrograms.CONTENT_URI, null, null);
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            Intent play = new Intent(Intent.ACTION_VIEW).setComponent(new ComponentName(this, FakeWnPlayer.class))
                    .putExtra("film", i);
            ContentValues v = new ContentValues();
            v.put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.TYPE_MOVIE);
            v.put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
            v.put(TvContract.WatchNextPrograms.COLUMN_TITLE, "Fake film " + (char) ('A' + i));
            v.put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, "fake" + i);
            v.put(TvContract.WatchNextPrograms.COLUMN_CONTENT_ID, "fake" + i);
            v.put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, play.toUri(Intent.URI_INTENT_SCHEME));
            v.put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, 6_000_000);
            v.put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 1_000_000 + i * 100_000);
            v.put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, now - i * 60_000L);
            getContentResolver().insert(TvContract.WatchNextPrograms.CONTENT_URI, v);
        }
        Log.i("fakewn", "seeded " + n);
        finish();
    }
}
