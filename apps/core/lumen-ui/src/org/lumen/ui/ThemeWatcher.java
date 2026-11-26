// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Follows {@link LumenTheme} live while a screen is visible: the user's choice and the place (Settings observer), the
 * clock and time zone (system broadcasts) and the next Авто flip (a timer to the minute). The listener runs on the
 * main thread and only when the palette really changed. start() in onStart/onResume, stop() in onStop/onPause.
 */
public final class ThemeWatcher {
    public interface Listener {
        void onTheme(Palette p);
    }

    /** Re-check at least this often while started (a timer may be late after the device slept). */
    private static final long MAX_WAIT_MS = 30 * 60_000L;

    private final Context mCtx;
    private final Listener mListener;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::check;
    private final ContentObserver mObs;
    private final BroadcastReceiver mTime = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            check();
        }
    };
    private Palette mCurrent;
    private boolean mStarted;

    public ThemeWatcher(Context c, Listener l) {
        mCtx = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        mListener = l;
        mObs = new ContentObserver(mMain) {
            @Override
            public void onChange(boolean selfChange) {
                check();
            }
        };
        mCurrent = LumenTheme.palette(mCtx);
    }

    public Palette current() {
        return mCurrent;
    }

    public void start() {
        if (mStarted) return;
        mStarted = true;
        try {
            mCtx.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(LumenTheme.KEY_MODE), false, mObs);
            mCtx.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(LumenTheme.KEY_GEO), false, mObs);
        } catch (RuntimeException ignored) {
        }
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        mCtx.registerReceiver(mTime, f, Context.RECEIVER_EXPORTED);
        check();
    }

    public void stop() {
        if (!mStarted) return;
        mStarted = false;
        mMain.removeCallbacks(mTick);
        try {
            mCtx.getContentResolver().unregisterContentObserver(mObs);
            mCtx.unregisterReceiver(mTime);
        } catch (RuntimeException ignored) {
        }
    }

    private void check() {
        Palette p = LumenTheme.palette(mCtx);
        if (mStarted) {
            mMain.removeCallbacks(mTick);
            long next = LumenTheme.nextChange(mCtx);
            long wait = next == Long.MAX_VALUE ? MAX_WAIT_MS
                    : Math.max(1_000L, Math.min(MAX_WAIT_MS, next - System.currentTimeMillis() + 500L));
            mMain.postDelayed(mTick, wait);
        }
        if (p != mCurrent) {
            mCurrent = p;
            mListener.onTheme(p);
        }
    }
}
