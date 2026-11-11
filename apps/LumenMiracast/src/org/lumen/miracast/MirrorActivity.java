// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Full-screen picture of the mirrored device. Started by the service when the source begins to play;
 * finishes when the session ends. BACK (or leaving the screen) ends the session. No launcher entry,
 * not in recents.
 */
public final class MirrorActivity extends Activity implements MiracastService.UiListener {
    private AspectSurfaceView surface;
    private TextView status;
    private boolean stopping;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new AspectSurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        status = new TextView(this);
        status.setTextColor(0xCCFFFFFF);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                MiracastService s = MiracastService.instance();
                if (s != null) s.attachSurface(h.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder h) {
                MiracastService s = MiracastService.instance();
                if (s != null) s.attachSurface(null);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        MiracastService.addListener(this);
        onMiracastState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MiracastService.removeListener(this);
        // left the screen (HOME, another app): mirroring ends, as on stock
        if (!isChangingConfigurations() && !stopping) {
            MiracastService s = MiracastService.instance();
            if (s != null) s.stopMirroring("screen left");
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            stopping = true;
            MiracastService s = MiracastService.instance();
            if (s != null) s.stopMirroring("back");
            finish();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onMiracastState() {
        MiracastService s = MiracastService.instance();
        if (s == null || !s.mirroring()) {
            stopping = true;
            finish();
            return;
        }
        if (s.firstFrameShown()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(getString(R.string.connecting, s.peerName()));
        }
        surface.setVideoSize(s.videoWidth(), s.videoHeight());
    }
}
